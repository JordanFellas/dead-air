package com.wasteland.deadair.station

import com.wasteland.deadair.DeadAir
import fr.delthas.javamp3.Sound
import java.io.BufferedInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection

/**
 * A stream URL (a direct MP3 link or an MP3 internet radio stream) decoded on its own thread into
 * a small buffer of 48 kHz mono samples that the station audio thread reads 20 ms at a time.
 *
 * Icecast/Shoutcast song titles ("StreamTitle") are picked up for the station's now playing.
 */
class LiveStream(private val url: String) {

    companion object {
        private const val SAMPLE_RATE = 48000
        private const val BUFFER_SAMPLES = SAMPLE_RATE * 8
        /** Wait for this much audio before starting (and after running dry) so it doesn't stutter. */
        private const val PREBUFFER_SAMPLES = SAMPLE_RATE
    }

    private val ring = ShortArray(BUFFER_SAMPLES)
    private var readPos = 0
    private var size = 0
    private var buffering = true
    private val lock = Object()

    @Volatile private var closed = false
    @Volatile private var ended = false
    @Volatile private var connection: HttpURLConnection? = null

    /** Current song title from the stream, or its station name. */
    @Volatile var title: String? = null
        private set
    /** Why the stream stopped, if it failed. */
    @Volatile var error: String? = null
        private set

    /** True once the stream has ended or failed and everything buffered has been played. */
    val isFinished: Boolean get() = ended && synchronized(lock) { size == 0 }

    private val thread = Thread(::run, "Dead Air Stream").apply { isDaemon = true }

    fun start(): LiveStream {
        thread.start()
        return this
    }

    fun close() {
        closed = true
        connection?.disconnect()
        thread.interrupt()
        synchronized(lock) { lock.notifyAll() }
    }

    /** Fills [frame] with the next 20 ms; false while buffering or once the stream is over. */
    fun read(frame: ShortArray): Boolean {
        synchronized(lock) {
            if (buffering) {
                if (size < PREBUFFER_SAMPLES && !ended) return false
                buffering = false
            }
            if (size < frame.size) {
                if (!ended) buffering = true
                if (size == 0) return false
                frame.fill(0)
            }
            val n = minOf(frame.size, size)
            for (i in 0 until n) {
                frame[i] = ring[readPos]
                readPos = (readPos + 1) % BUFFER_SAMPLES
            }
            size -= n
            lock.notifyAll()
            return true
        }
    }

    private fun run() {
        try {
            val conn = StreamUrls.open(url)
            connection = conn
            conn.getHeaderField("icy-name")?.takeIf { it.isNotBlank() }?.let { title = it.trim().take(80) }
            val metaInterval = conn.getHeaderField("icy-metaint")?.toIntOrNull() ?: 0
            var input: InputStream = BufferedInputStream(conn.inputStream, 16 * 1024)
            if (metaInterval > 0) input = IcyMetadataStream(input, metaInterval) { title = it }

            val sound = Sound(BufferedInputStream(Mp3FrameAligner(input), 16 * 1024))
            val channels = if (sound.isStereo) 2 else 1
            val bigEndian = sound.audioFormat.isBigEndian
            val resampler = Resampler(sound.samplingFrequency.toDouble())
            val bytes = ByteArray(4608 * channels)
            val mono = ShortArray(bytes.size / 2 / channels)
            while (!closed) {
                val read = readFully(sound, bytes)
                if (read <= 0) break
                val frames = read / 2 / channels
                for (i in 0 until frames) {
                    var sum = 0
                    for (c in 0 until channels) {
                        val at = (i * channels + c) * 2
                        val hi = if (bigEndian) bytes[at] else bytes[at + 1]
                        val lo = if (bigEndian) bytes[at + 1] else bytes[at]
                        sum += (hi.toInt() shl 8) or (lo.toInt() and 0xFF)
                    }
                    mono[i] = (sum / channels).toShort()
                }
                resampler.process(mono, frames, ::push)
            }
        } catch (e: StreamUrls.StreamUrlException) {
            error = e.message
        } catch (e: InterruptedException) {
            // closed
        } catch (e: Exception) {
            if (!closed) {
                error = if (e is IOException) "Stream stopped: ${e.message ?: "connection lost"}" else "Can't play this stream (MP3 only)"
                DeadAir.LOGGER.debug("Dead Air: stream {} stopped", url, e)
            }
        } finally {
            ended = true
            connection?.disconnect()
            synchronized(lock) { lock.notifyAll() }
        }
    }

    private fun readFully(input: InputStream, buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val n = input.read(buffer, total, buffer.size - total)
            if (n < 0) return if (total == 0) -1 else total
            total += n
        }
        return total
    }

    /** Adds one sample, waiting while the buffer is full (that paces direct MP3 downloads). */
    private fun push(sample: Short) {
        synchronized(lock) {
            while (size >= BUFFER_SAMPLES && !closed) lock.wait(250)
            if (closed) throw InterruptedException()
            ring[(readPos + size) % BUFFER_SAMPLES] = sample
            size++
        }
    }

    /** Linear resampler to 48 kHz that carries its position across chunks. */
    private class Resampler(inRate: Double) {
        private val step = inRate / SAMPLE_RATE
        private var t = 0.0
        private var prev: Short = 0

        fun process(input: ShortArray, n: Int, out: (Short) -> Unit) {
            if (n == 0) return
            if (step == 1.0) {
                for (i in 0 until n) out(input[i])
                return
            }
            // Index -1 is the last sample of the previous chunk.
            while (t < n - 1) {
                val i = kotlin.math.floor(t).toInt()
                val frac = t - i
                val a = if (i < 0) prev.toInt() else input[i].toInt()
                val b = input[i + 1].toInt()
                out((a + (b - a) * frac).toInt().toShort())
                t += step
            }
            t -= n
            prev = input[n - 1]
        }
    }

    /**
     * Passes MP3 data through whole frame by frame, starting at the first real frame (skipping ID3
     * tags and the partial frame you land in when joining a radio stream mid-broadcast).
     *
     * An MP3 frame's audio can start up to 511 bytes back, in earlier frames (the "bit reservoir").
     * Joining mid-stream, those bytes were never received and the decoder crashes reaching for
     * them, so each frame's back-pointer is clamped to what the decoder has actually seen. That
     * costs a few milliseconds of glitch at tune-in and after a dropout, never a crash.
     */
    private class Mp3FrameAligner(private val source: InputStream) : InputStream() {
        companion object {
            private const val MAX_RESERVOIR = 511
            private val BITRATE_V1 = intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320)
            private val BITRATE_V2 = intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160)
            private val RATE_V1 = intArrayOf(44100, 48000, 32000)
        }

        private val header = ByteArray(4)
        private var headerFill = 0
        private var frame = ByteArray(0)
        private var frameLength = 0
        private var framePos = 0
        /** Bytes of main data the decoder holds from frames already passed through. */
        private var reservoir = 0

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) <= 0) -1 else one[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            if (framePos >= frameLength && !nextFrame()) return -1
            val n = minOf(len, frameLength - framePos)
            System.arraycopy(frame, framePos, b, off, n)
            framePos += n
            return n
        }

        override fun close() = source.close()

        /** Reads the next whole frame into [frame], resyncing byte by byte on garbage. */
        private fun nextFrame(): Boolean {
            while (true) {
                while (headerFill < 4) {
                    val c = source.read()
                    if (c < 0) return false
                    header[headerFill++] = c.toByte()
                }
                val length = frameLength(header)
                if (length <= 0) {
                    System.arraycopy(header, 1, header, 0, 3)
                    headerFill = 3
                    continue
                }
                if (frame.size < length) frame = ByteArray(length)
                System.arraycopy(header, 0, frame, 0, 4)
                var got = 4
                while (got < length) {
                    val n = source.read(frame, got, length - got)
                    if (n < 0) return false
                    got += n
                }
                headerFill = 0
                clampReservoirPointer(frame, length)
                frameLength = length
                framePos = 0
                return true
            }
        }

        /** Length of a Layer III frame with this header, or 0 when it isn't one. */
        private fun frameLength(h: ByteArray): Int {
            val b1 = h[1].toInt() and 0xFF
            val b2 = h[2].toInt() and 0xFF
            if ((h[0].toInt() and 0xFF) != 0xFF || (b1 and 0xE0) != 0xE0) return 0
            val version = (b1 shr 3) and 3 // 3 = MPEG-1, 2 = MPEG-2, 0 = MPEG-2.5
            if (version == 1 || ((b1 shr 1) and 3) != 1) return 0
            val bitrateIndex = b2 shr 4
            val rateIndex = (b2 shr 2) and 3
            if (bitrateIndex == 0 || bitrateIndex == 15 || rateIndex == 3) return 0
            val padding = (b2 shr 1) and 1
            return if (version == 3) {
                144 * BITRATE_V1[bitrateIndex] * 1000 / RATE_V1[rateIndex] + padding
            } else {
                val rate = RATE_V1[rateIndex] / if (version == 2) 2 else 4
                72 * BITRATE_V2[bitrateIndex] * 1000 / rate + padding
            }
        }

        /**
         * Limits main_data_begin (the 9-bit back-pointer at the start of an MPEG-1 frame's side
         * info) to the bytes the decoder has, then records how many it will have after this frame.
         * The decoder keeps the pointed-to bytes plus this frame's main data.
         */
        private fun clampReservoirPointer(f: ByteArray, length: Int) {
            val b1 = f[1].toInt() and 0xFF
            if ((b1 shr 3) and 3 != 3) return // the decoder only handles MPEG-1 reservoirs
            val sideInfo = if ((b1 and 1) == 0) 6 else 4 // a CRC sits between header and side info
            val sideInfoSize = if ((f[3].toInt() and 0xFF) shr 6 == 3) 17 else 32 // mono : stereo
            var begin = ((f[sideInfo].toInt() and 0xFF) shl 1) or ((f[sideInfo + 1].toInt() and 0xFF) shr 7)
            if (begin > reservoir) {
                begin = reservoir
                f[sideInfo] = (begin shr 1).toByte()
                f[sideInfo + 1] = ((f[sideInfo + 1].toInt() and 0x7F) or ((begin and 1) shl 7)).toByte()
            }
            reservoir = minOf(MAX_RESERVOIR, begin + length - sideInfoSize - 4)
        }
    }

    /** Strips Icecast metadata blocks out of the audio and reports "StreamTitle" changes. */
    private class IcyMetadataStream(
        input: InputStream,
        private val interval: Int,
        private val onTitle: (String) -> Unit
    ) : FilterInputStream(input) {
        private var untilMeta = interval

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) <= 0) -1 else one[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (untilMeta == 0) readMetadata()
            val n = `in`.read(b, off, minOf(len, untilMeta))
            if (n > 0) untilMeta -= n
            return n
        }

        private fun readMetadata() {
            untilMeta = interval
            val length = `in`.read() * 16
            if (length <= 0) return
            val data = `in`.readNBytes(length)
            val text = String(data, Charsets.UTF_8).trimEnd('\u0000')
            val match = Regex("StreamTitle='(.*?)';").find(text) ?: return
            val title = match.groupValues[1].trim()
            if (title.isNotEmpty()) onTitle(title.take(80))
        }
    }
}
