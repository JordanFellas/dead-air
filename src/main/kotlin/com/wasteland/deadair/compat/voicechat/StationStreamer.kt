package com.wasteland.deadair.compat.voicechat

import com.wasteland.deadair.DeadAir
import com.wasteland.deadair.station.LiveStream
import com.wasteland.deadair.station.StationAudioBackend
import com.wasteland.deadair.station.StationKey
import com.wasteland.deadair.station.StationListeners
import de.maxhenkel.voicechat.api.VoicechatServerApi
import de.maxhenkel.voicechat.api.audiochannel.AudioChannel
import de.maxhenkel.voicechat.api.audiochannel.EntityAudioChannel
import de.maxhenkel.voicechat.api.opus.OpusDecoder
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import kotlin.io.path.extension
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

/**
 * Streams radio station audio through Simple Voice Chat: song files from the server's music
 * folder, the live DJ at a Studio Mic (music ducks under their voice) and recorded station IDs.
 *
 * Every 20 ms a dedicated thread takes the next frame of each station, mixes in the DJ, runs it
 * through [StationStreamEffect] once per signal bucket and sends it down each listener's channel:
 * a private static channel for walkies, a positional one for Home Radios, and one that follows
 * the player for boomboxes. Song files are decoded on a separate loader thread.
 */
class StationStreamer(private val api: VoicechatServerApi) : StationAudioBackend {

    companion object {
        /** Voice chat allows at most 16 characters, a-z and _. */
        const val CATEGORY = "dead_air_station"
        private const val FRAME = 960
        private const val SAMPLE_RATE = 48000
        private const val SPEAKER_DISTANCE = 24.0f
        private const val MAX_RECORDING_SAMPLES = SAMPLE_RATE * 65
        private const val DUCK_FRAMES = 25
        private const val DUCK_LEVEL = 0.3f
        private const val DJ_GAIN = 1.3f
        private const val MUSIC_GAIN = 0.85f
        /** Weakest-signal listeners lose a few frames, like the voice relay. */
        private const val WEAK_DROP_CHANCE = 0.08f
    }

    private class Sink(val channel: AudioChannel, @Volatile var bucket: Int)

    private class Track(val pcm: ShortArray) {
        var position = 0
    }

    private inner class Stream(val key: StationKey) {
        val track = AtomicReference<Track?>(null)
        @Volatile var live: LiveStream? = null
        /** Error from the last stream, kept after it ends so the console can show it. */
        @Volatile var liveError: String? = null
        @Volatile var loading = false
        @Volatile var generation = 0
        @Volatile var sinks: Map<String, Sink> = emptyMap()
        val djFrames = ConcurrentLinkedQueue<ShortArray>()
        var duckFrames = 0
        var sending = false
        @Volatile var recordingFile: Path? = null
        val recorded = ArrayList<ShortArray>()
        var recordedSamples = 0
        val effect = StationStreamEffect(api)
    }

    private val streams = ConcurrentHashMap<StationKey, Stream>()
    private val djDecoders = ConcurrentHashMap<UUID, OpusDecoder>()
    private val pump = Executors.newSingleThreadScheduledExecutor { Thread(it, "Dead Air Station Audio").apply { isDaemon = true } }
    private val loader = Executors.newSingleThreadExecutor { Thread(it, "Dead Air Song Loader").apply { isDaemon = true } }

    init {
        pump.scheduleAtFixedRate({
            try {
                pumpFrame()
            } catch (e: Throwable) {
                DeadAir.LOGGER.error("Dead Air: station audio frame failed", e)
            }
        }, 20, 20, TimeUnit.MILLISECONDS)
    }

    private fun stream(key: StationKey): Stream = streams.computeIfAbsent(key) { Stream(it) }

    override fun play(station: StationKey, file: Path) {
        val s = stream(station)
        val generation = ++s.generation
        s.track.set(null)
        s.live?.close()
        s.live = null
        s.loading = true
        loader.execute {
            val pcm = try {
                decode(file)
            } catch (e: Throwable) {
                DeadAir.LOGGER.warn("Dead Air: couldn't play {}: {}", file.fileName, e.toString())
                null
            }
            if (s.generation == generation) {
                s.track.set(pcm?.let { Track(it) })
                s.loading = false
            }
        }
    }

    override fun playStream(station: StationKey, url: String) {
        val s = stream(station)
        s.generation++
        s.track.set(null)
        s.loading = false
        s.live?.close()
        s.liveError = null
        s.live = LiveStream(url).start()
    }

    override fun streamTitle(station: StationKey): String? = streams[station]?.live?.title

    override fun streamError(station: StationKey): String? {
        val s = streams[station] ?: return null
        return s.live?.error ?: s.liveError
    }

    override fun stop(station: StationKey) {
        val s = streams[station] ?: return
        s.generation++
        s.track.set(null)
        s.loading = false
        s.live?.let {
            s.liveError = it.error
            it.close()
        }
        s.live = null
    }

    override fun isFinished(station: StationKey): Boolean {
        val s = streams[station] ?: return true
        s.live?.let { return it.isFinished }
        return !s.loading && s.track.get() == null
    }

    override fun updateListeners(station: StationKey, listeners: StationListeners) {
        val s = stream(station)
        val old = s.sinks
        if (old.isEmpty() && listeners.walkies.isEmpty() && listeners.radios.isEmpty() && listeners.boomboxes.isEmpty()) return
        val level = api.fromServerLevel(listeners.level)
        val next = HashMap<String, Sink>()

        for ((uuid, quality) in listeners.walkies) {
            val id = "w$uuid"
            val sink = old[id] ?: api.getConnectionOf(uuid)?.let { connection ->
                api.createStaticAudioChannel(channelId(station, id), level, connection)?.let { Sink(it, 0) }
            } ?: continue
            sink.bucket = RadioVoiceEffect.bucketOf(quality)
            next[id] = sink
        }
        for ((pos, quality) in listeners.radios) {
            val id = "r${pos.asLong()}"
            val sink = old[id] ?: api.createLocationalAudioChannel(
                channelId(station, id), level, api.createPosition(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5)
            )?.let {
                it.distance = SPEAKER_DISTANCE
                Sink(it, 0)
            } ?: continue
            sink.bucket = RadioVoiceEffect.bucketOf(quality)
            next[id] = sink
        }
        for ((player, quality) in listeners.boomboxes) {
            val id = "b${player.uuid}"
            val entity = api.fromEntity(player)
            val existing = old[id]
            val sink = if (existing != null) {
                (existing.channel as? EntityAudioChannel)?.updateEntity(entity)
                existing
            } else {
                api.createEntityAudioChannel(channelId(station, id), entity)?.let {
                    it.distance = SPEAKER_DISTANCE
                    Sink(it, 0)
                } ?: continue
            }
            sink.bucket = RadioVoiceEffect.bucketOf(quality)
            next[id] = sink
        }
        for (sink in next.values) sink.channel.category = CATEGORY
        for ((id, sink) in old) if (id !in next) sink.channel.flush()
        s.sinks = next
    }

    override fun setRecording(station: StationKey, file: Path?): Int {
        val s = stream(station)
        if (file != null) {
            synchronized(s.recorded) {
                s.recorded.clear()
                s.recordedSamples = 0
            }
            s.recordingFile = file
            return 0
        }
        val target = s.recordingFile ?: return 0
        s.recordingFile = null
        val frames = synchronized(s.recorded) {
            val copy = ArrayList(s.recorded)
            s.recorded.clear()
            s.recordedSamples = 0
            copy
        }
        if (frames.isEmpty()) return 0
        loader.execute {
            try {
                writeWav(target, frames)
            } catch (e: Throwable) {
                DeadAir.LOGGER.warn("Dead Air: couldn't save announcement {}", target, e)
            }
        }
        return maxOf(1, frames.sumOf { it.size } / SAMPLE_RATE)
    }

    override fun remove(station: StationKey) {
        val s = streams.remove(station) ?: return
        s.generation++
        s.track.set(null)
        s.live?.close()
        s.live = null
        s.sinks.values.forEach { it.channel.flush() }
        s.effect.close()
    }

    override fun shutdown() {
        pump.shutdownNow()
        loader.shutdownNow()
        streams.values.forEach {
            it.live?.close()
            it.effect.close()
        }
        streams.clear()
        djDecoders.values.forEach { it.close() }
        djDecoders.clear()
    }

    override fun validateSong(file: Path): String? {
        val pcm = try {
            decode(file)
        } catch (e: Throwable) {
            DeadAir.LOGGER.debug("Dead Air: song {} doesn't decode", file.fileName, e)
            null
        }
        return when {
            pcm == null -> "That file isn't a playable MP3 or WAV"
            pcm.size < SAMPLE_RATE / 2 -> "That song is empty or too short"
            else -> null
        }
    }

    /** Voice thread: a frame from a player standing at a Studio Mic. */
    fun pushDj(player: UUID, station: StationKey, opus: ByteArray) {
        if (opus.isEmpty()) return
        val decoder = djDecoders.computeIfAbsent(player) { api.createDecoder() }
        val pcm = try {
            synchronized(decoder) { decoder.decode(opus) }
        } catch (e: Exception) {
            null
        } ?: return
        val s = stream(station)
        s.djFrames.add(pcm)
        while (s.djFrames.size > 6) s.djFrames.poll()
    }

    /** Releases the decoder of a player who left the mic. */
    fun forgetDj(player: UUID) {
        djDecoders.remove(player)?.close()
    }

    private fun pumpFrame() {
        for (s in streams.values) {
            val music = nextMusicFrame(s)
            var dj = s.djFrames.poll()

            if (dj != null && s.recordingFile != null) {
                record(s, dj)
                dj = null // a recording isn't broadcast live
            }
            if (dj != null) s.duckFrames = DUCK_FRAMES else if (s.duckFrames > 0) s.duckFrames--

            val sinks = s.sinks
            if ((music == null && dj == null) || sinks.isEmpty()) {
                if (s.sending) {
                    sinks.values.forEach { it.channel.flush() }
                    s.sending = false
                }
                continue
            }
            s.sending = true

            val musicGain = MUSIC_GAIN * if (s.duckFrames > 0) DUCK_LEVEL else 1.0f
            val mix = FloatArray(FRAME)
            if (music != null) for (i in 0 until FRAME) mix[i] = music[i] / 32768.0f * musicGain
            if (dj != null) for (i in 0 until min(FRAME, dj.size)) mix[i] += dj[i] / 32768.0f * DJ_GAIN
            s.effect.load(mix)

            for ((bucket, targets) in sinks.values.groupBy { it.bucket }) {
                val opus = s.effect.render(bucket) ?: continue
                for (sink in targets) {
                    if (bucket == RadioVoiceEffect.BUCKETS - 1 && Random.nextFloat() < WEAK_DROP_CHANCE) continue
                    sink.channel.send(opus)
                }
            }
        }
    }

    private fun nextMusicFrame(s: Stream): ShortArray? {
        s.live?.let { live ->
            val frame = ShortArray(FRAME)
            return if (live.read(frame)) frame else null
        }
        val track = s.track.get() ?: return null
        val pcm = track.pcm
        val start = track.position
        if (start >= pcm.size) {
            s.track.compareAndSet(track, null)
            return null
        }
        val end = min(start + FRAME, pcm.size)
        val frame = ShortArray(FRAME)
        System.arraycopy(pcm, start, frame, 0, end - start)
        track.position = end
        if (end >= pcm.size) s.track.compareAndSet(track, null)
        return frame
    }

    private fun record(s: Stream, frame: ShortArray) {
        synchronized(s.recorded) {
            if (s.recordedSamples >= MAX_RECORDING_SAMPLES) return
            s.recorded.add(frame)
            s.recordedSamples += frame.size
        }
    }

    private fun channelId(station: StationKey, sink: String): UUID =
        UUID.nameUUIDFromBytes("${DeadAir.MOD_ID}:station:${station.fileId}:$sink".toByteArray())

    // ---- Decoding and saving audio files ----

    /** Any MP3 or WAV, as 48 kHz mono 16-bit samples. */
    private fun decode(file: Path): ShortArray? {
        val samples: ShortArray
        val rate: Float
        val channels: Int
        if (file.extension.lowercase() == "mp3") {
            BufferedInputStream(Files.newInputStream(file)).use { input ->
                val decoder = api.createMp3Decoder(input) ?: return null
                samples = decoder.decode() ?: return null
                val format = decoder.audioFormat
                rate = format.sampleRate
                channels = format.channels
            }
        } else {
            AudioSystem.getAudioInputStream(file.toFile()).use { source ->
                val base = source.format
                val target = AudioFormat(AudioFormat.Encoding.PCM_SIGNED, base.sampleRate, 16, base.channels, base.channels * 2, base.sampleRate, false)
                AudioSystem.getAudioInputStream(target, source).use { converted ->
                    val bytes = converted.readAllBytes()
                    val shorts = ShortArray(bytes.size / 2)
                    ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
                    samples = shorts
                }
                rate = base.sampleRate
                channels = base.channels
            }
        }
        return resample(toMono(samples, channels), rate)
    }

    private fun toMono(samples: ShortArray, channels: Int): ShortArray {
        if (channels <= 1) return samples
        val out = ShortArray(samples.size / channels)
        for (i in out.indices) {
            var sum = 0
            for (c in 0 until channels) sum += samples[i * channels + c]
            out[i] = (sum / channels).toShort()
        }
        return out
    }

    private fun resample(mono: ShortArray, rate: Float): ShortArray {
        if (rate <= 0 || abs(rate - SAMPLE_RATE) < 1.0f || mono.isEmpty()) return mono
        val step = rate / SAMPLE_RATE.toDouble()
        val out = ShortArray((mono.size / step).toInt())
        for (i in out.indices) {
            val pos = i * step
            val index = pos.toInt()
            val frac = pos - index
            val a = mono[min(index, mono.size - 1)]
            val b = mono[min(index + 1, mono.size - 1)]
            out[i] = (a + (b - a) * frac).toInt().toShort()
        }
        return out
    }

    private fun writeWav(file: Path, frames: List<ShortArray>) {
        if (frames.isEmpty()) return
        val total = frames.sumOf { it.size }
        val buffer = ByteBuffer.allocate(total * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (frame in frames) for (sample in frame) buffer.putShort(sample)
        val format = AudioFormat(SAMPLE_RATE.toFloat(), 16, 1, true, false)
        val temp = file.resolveSibling(file.fileName.toString() + ".tmp")
        AudioInputStream(ByteArrayInputStream(buffer.array()), format, total.toLong()).use {
            AudioSystem.write(it, AudioFileFormat.Type.WAVE, temp.toFile())
        }
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
    }
}
