package com.wasteland.deadair.station

import com.wasteland.deadair.DeadAir
import com.wasteland.deadair.block.StudioMicBlock
import com.wasteland.deadair.block.entity.HomeRadioBlockEntity
import com.wasteland.deadair.block.entity.StationConsoleBlockEntity
import com.wasteland.deadair.item.BoomboxItem
import com.wasteland.deadair.menu.StationConsoleMenu
import com.wasteland.deadair.network.RadioNetworkManager
import com.wasteland.deadair.network.RadioVoiceRelay
import com.wasteland.deadair.network.packets.PacketStationDisc
import com.wasteland.deadair.network.packets.PacketStationInfo
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.network.PacketDistributor
import java.nio.file.Files
import java.util.UUID
import kotlin.math.abs

/**
 * Server-side brain of the radio stations: works out who is tuned in to which station (walkies,
 * Home Radios, boomboxes), keeps every listener's music disc playback in step with the station,
 * tells listeners what's playing, and tracks DJs at Studio Mics and announcement recordings.
 */
object StationManager {

    private const val UPDATE_INTERVAL = 10
    private const val MIN_QUALITY = 0.05f
    /** Players this close to a Home Radio or boombox hear its disc music. */
    private const val SPEAKER_RANGE = 40.0
    /** Players this close to a Home Radio see what it's playing. */
    private const val NOW_PLAYING_RANGE = 10.0
    private const val MAX_RECORDING_TICKS = 20 * 60

    /** A place station audio comes out of. */
    sealed interface Sink
    data class WalkieSink(val player: UUID) : Sink
    data class RadioSink(val radio: StationKey) : Sink
    data class BoomboxSink(val player: UUID) : Sink

    private class Tuned(val station: StationConsoleBlockEntity, val quality: Float)
    private data class SentDisc(val tag: String, val type: Int, val id: Long)
    private class Recording(val player: UUID, val startTick: Int)

    private val consoles = HashMap<StationKey, StationConsoleBlockEntity>()
    private val homeRadios = HashMap<StationKey, HomeRadioBlockEntity>()

    private val sentDiscs = HashMap<Pair<Sink, UUID>, SentDisc>()
    private val lastInfo = HashMap<UUID, String>()
    private val lastAnnounced = HashMap<UUID, String>()
    private val recordings = HashMap<StationKey, Recording>()
    private var tickCounter = 0

    fun register(console: StationConsoleBlockEntity) {
        val key = console.key ?: return
        consoles[key] = console
    }

    fun unregister(key: StationKey) {
        consoles.remove(key)
        recordings.remove(key)?.let { StationAudio.backend?.setRecording(key, null) }
        StationAudio.backend?.remove(key)
    }

    fun addHomeRadio(radio: HomeRadioBlockEntity) {
        val level = radio.level ?: return
        homeRadios[StationKey(level.dimension().location(), radio.blockPos)] = radio
    }

    fun removeHomeRadio(dimension: ResourceLocation, pos: BlockPos) {
        homeRadios.remove(StationKey(dimension, pos))
    }

    fun clear() {
        consoles.clear()
        homeRadios.clear()
        sentDiscs.clear()
        lastInfo.clear()
        lastAnnounced.clear()
        recordings.clear()
        StationAudio.djStations = emptyMap()
        tickCounter = 0
    }

    fun tick(server: MinecraftServer) {
        tickCounter++
        if (tickCounter % UPDATE_INTERVAL != 0) return

        consoles.values.removeIf { it.isRemoved }
        homeRadios.values.removeIf { it.isRemoved }
        stopLongRecordings(server)

        val djs = findDjs(server)
        val tuned = findListeners(server, djs)
        updateStreams(tuned)
        syncDiscs(server, tuned)
        syncNowPlaying(server, tuned)

        val online = server.playerList.players.mapTo(HashSet()) { it.uuid }
        lastInfo.keys.retainAll(online)
        lastAnnounced.keys.retainAll(online)
    }

    /** Works out the best station each walkie, Home Radio and boombox can hear. */
    private fun findListeners(server: MinecraftServer, djs: Map<UUID, StationKey>): Map<Sink, Tuned> {
        // Stations on air, grouped by dimension and channel, with the tower network each one reaches.
        val byChannel = HashMap<Pair<ResourceLocation, Int>, MutableList<Pair<StationConsoleBlockEntity, Set<RadioNetworkManager.ActiveTower>>>>()
        for ((key, console) in consoles) {
            if (!console.broadcasting) continue
            val network = RadioNetworkManager.getConnectedNetwork(key.dimension, key.pos)
            if (network.isEmpty()) continue
            byChannel.getOrPut(key.dimension to RadioVoiceRelay.channelOf(console.frequency)) { ArrayList() }.add(console to network)
        }
        val tuned = HashMap<Sink, Tuned>()
        if (byChannel.isEmpty()) return tuned

        fun consider(sink: Sink, dimension: ResourceLocation, channel: Int, pos: BlockPos, obstruction: Float, skip: StationKey? = null) {
            for ((console, network) in byChannel[dimension to channel] ?: return) {
                if (console.key == skip) continue
                val quality = RadioNetworkManager.getSignalQuality(pos, network) * obstruction
                if (quality <= MIN_QUALITY) continue
                if ((tuned[sink]?.quality ?: 0.0f) < quality) tuned[sink] = Tuned(console, quality)
            }
        }

        for (player in server.playerList.players) {
            val dimension = player.serverLevel().dimension().location()
            val state = RadioVoiceRelay.getState(player.uuid)
            val obstruction = state?.obstruction ?: 1.0f
            // A DJ doesn't hear their own station on their walkie (no echo of their own voice).
            state?.listenChannels?.forEach { consider(WalkieSink(player.uuid), dimension, it, state.pos, obstruction, djs[player.uuid]) }
            BoomboxItem.findPlaying(player)?.let {
                consider(BoomboxSink(player.uuid), dimension, RadioVoiceRelay.channelOf(BoomboxItem.getFrequency(it)), player.blockPosition(), obstruction)
            }
        }
        for ((key, radio) in homeRadios) {
            if (!radio.isOn) continue
            consider(RadioSink(key), key.dimension, RadioVoiceRelay.channelOf(radio.frequency), key.pos, 1.0f)
        }
        return tuned
    }

    /** Hands each station's listeners to the voice chat streamer (songs, DJ, announcements). */
    private fun updateStreams(tuned: Map<Sink, Tuned>) {
        val byStation = tuned.entries.groupBy { it.value.station }
        val backend = StationAudio.backend
        for ((key, console) in consoles) {
            val entries = byStation[console].orEmpty()
            console.listenerCount = entries.size
            if (backend == null) continue
            val level = console.level as? ServerLevel ?: continue
            val walkies = HashMap<UUID, Float>()
            val radios = HashMap<BlockPos, Float>()
            val boomboxes = HashMap<ServerPlayer, Float>()
            for ((sink, t) in entries) {
                when (sink) {
                    is WalkieSink -> walkies[sink.player] = t.quality
                    is RadioSink -> radios[sink.radio.pos] = t.quality
                    is BoomboxSink -> level.server.playerList.getPlayer(sink.player)?.let { boomboxes[it] = t.quality }
                }
            }
            backend.updateListeners(key, StationListeners(level, walkies, radios, boomboxes))
        }
    }

    /** Keeps every listener's client playing the station's current music disc. */
    private fun syncDiscs(server: MinecraftServer, tuned: Map<Sink, Tuned>) {
        val desired = HashMap<Pair<Sink, UUID>, Pair<SentDisc, PacketStationDisc>>()
        for ((sink, t) in tuned) {
            val track = t.station.current as? StationConsoleBlockEntity.Track.Disc ?: continue
            val key = t.station.key ?: continue
            val level = t.station.level as? ServerLevel ?: continue
            val tag = "${key.fileId}#${t.station.trackSerial}"
            val volume = 0.35f + 0.65f * t.quality
            when (sink) {
                is WalkieSink -> {
                    val sent = SentDisc(tag, PacketStationDisc.SINK_WALKIE, 0L)
                    desired[sink to sink.player] = sent to PacketStationDisc(sent.type, sent.id, track.sound.toString(), volume)
                }
                is RadioSink -> {
                    val sent = SentDisc(tag, PacketStationDisc.SINK_RADIO, sink.radio.pos.asLong())
                    val packet = PacketStationDisc(sent.type, sent.id, track.sound.toString(), volume)
                    for (viewer in playersNear(level, Vec3.atCenterOf(sink.radio.pos), SPEAKER_RANGE)) desired[sink to viewer.uuid] = sent to packet
                }
                is BoomboxSink -> {
                    val carrier = server.playerList.getPlayer(sink.player) ?: continue
                    val sent = SentDisc(tag, PacketStationDisc.SINK_BOOMBOX, carrier.id.toLong())
                    val packet = PacketStationDisc(sent.type, sent.id, track.sound.toString(), volume)
                    for (viewer in playersNear(carrier.serverLevel(), carrier.position(), SPEAKER_RANGE)) desired[sink to viewer.uuid] = sent to packet
                }
            }
        }

        for ((target, play) in desired) {
            if (sentDiscs[target]?.tag == play.first.tag) continue
            server.playerList.getPlayer(target.second)?.let { PacketDistributor.sendToPlayer(it, play.second) }
            sentDiscs[target] = play.first
        }
        val iterator = sentDiscs.entries.iterator()
        while (iterator.hasNext()) {
            val (target, sent) = iterator.next()
            if (target in desired) continue
            iterator.remove()
            server.playerList.getPlayer(target.second)?.let { PacketDistributor.sendToPlayer(it, PacketStationDisc(sent.type, sent.id, "", 0.0f)) }
        }
    }

    /** Walkie LCD, tuner screens and the action bar show the station and track each player hears. */
    private fun syncNowPlaying(server: MinecraftServer, tuned: Map<Sink, Tuned>) {
        for (player in server.playerList.players) {
            val menu = player.containerMenu as? StationConsoleMenu
            val console = menu?.let { consoles[StationKey(player.serverLevel().dimension().location(), it.pos)] }
            val heard = tuned[WalkieSink(player.uuid)] ?: tuned[BoomboxSink(player.uuid)] ?: nearestRadio(player, tuned)
            val station = console ?: heard?.station

            val title = station?.let { it.current?.title ?: it.streamError?.let { error -> "⚠ $error" } } ?: ""
            val info = if (station == null) "" else "${station.callSign}|$title|${"%.1f".format(station.frequency)}"
            if (lastInfo.put(player.uuid, info) != info) {
                PacketDistributor.sendToPlayer(
                    player,
                    if (station == null) PacketStationInfo("", "", 0.0f)
                    else PacketStationInfo(station.callSign, title, station.frequency)
                )
            }

            // Pop the song name up when a new track starts on the station you're listening to.
            val track = heard?.station?.current
            val announceKey = if (heard == null || track == null) "" else "${heard.station.key?.fileId}#${heard.station.trackSerial}"
            if (lastAnnounced.put(player.uuid, announceKey) != announceKey && heard != null && track != null) {
                val text = when (track) {
                    is StationConsoleBlockEntity.Track.Announcement -> "♫ ${heard.station.callSign} %.1f".format(heard.station.frequency)
                    else -> "♫ ${heard.station.callSign}: ${track.title}"
                }
                player.displayClientMessage(Component.literal(text).withStyle(ChatFormatting.GOLD), true)
            }
        }
    }

    private fun nearestRadio(player: ServerPlayer, tuned: Map<Sink, Tuned>): Tuned? {
        val dimension = player.serverLevel().dimension().location()
        var best: Tuned? = null
        var bestDist = NOW_PLAYING_RANGE * NOW_PLAYING_RANGE
        for ((sink, t) in tuned) {
            if (sink !is RadioSink || sink.radio.dimension != dimension) continue
            val dist = player.distanceToSqr(Vec3.atCenterOf(sink.radio.pos))
            if (dist <= bestDist) {
                bestDist = dist
                best = t
            }
        }
        return best
    }

    /** Players standing at a Studio Mic: their voice goes out live on that mic's station. */
    private fun findDjs(server: MinecraftServer): Map<UUID, StationKey> {
        val djs = HashMap<UUID, StationKey>()
        for ((key, console) in consoles) {
            val level = console.level as? ServerLevel ?: continue
            for (mic in console.micPositions) {
                for (player in playersNear(level, Vec3.atCenterOf(mic), StudioMicBlock.PICKUP_RANGE)) {
                    djs.putIfAbsent(player.uuid, key)
                }
            }
        }
        val previous = StationAudio.djStations
        for ((uuid, key) in djs) {
            if (previous[uuid] == key) continue
            val console = consoles[key] ?: continue
            val player = server.playerList.getPlayer(uuid) ?: continue
            val text = when {
                StationAudio.backend == null -> "🎙 ${console.callSign}: the mic needs Simple Voice Chat"
                console.broadcasting -> "🎙 You're live on ${console.callSign} %.1f".format(console.frequency)
                else -> "🎙 ${console.callSign} is off the air"
            }
            player.displayClientMessage(Component.literal(text).withStyle(ChatFormatting.RED), true)
        }
        StationAudio.djStations = djs
        return djs
    }

    /** Right-clicking a Studio Mic starts or stops recording the station's announcement. */
    fun toggleRecording(player: ServerPlayer, micPos: BlockPos) {
        val level = player.serverLevel()
        val backend = StationAudio.backend
        if (backend == null) {
            player.displayClientMessage(Component.literal("Recording needs Simple Voice Chat").withStyle(ChatFormatting.RED), true)
            return
        }
        val console = consoles.values.firstOrNull {
            it.level == level &&
                abs(it.blockPos.x - micPos.x) <= StationConsoleBlockEntity.MIC_RANGE_XZ &&
                abs(it.blockPos.y - micPos.y) <= StationConsoleBlockEntity.MIC_RANGE_Y &&
                abs(it.blockPos.z - micPos.z) <= StationConsoleBlockEntity.MIC_RANGE_XZ
        }
        val key = console?.key
        if (console == null || key == null) {
            player.displayClientMessage(Component.literal("No Station Console within 8 blocks of this mic").withStyle(ChatFormatting.RED), true)
            return
        }

        if (recordings.containsKey(key)) {
            finishRecording(key, console, player)
            return
        }
        val file = StationConsoleBlockEntity.announcementFile(level, key)
        try {
            Files.createDirectories(file.parent)
        } catch (e: Exception) {
            DeadAir.LOGGER.warn("Dead Air: couldn't create {}", file.parent, e)
            return
        }
        backend.setRecording(key, file)
        recordings[key] = Recording(player.uuid, tickCounter)
        console.recording = true
        player.displayClientMessage(
            Component.literal("● REC  ${console.callSign} station ID: talk now, right-click the mic to stop (60s max)")
                .withStyle(ChatFormatting.RED),
            true
        )
    }

    private fun finishRecording(key: StationKey, console: StationConsoleBlockEntity, player: ServerPlayer?) {
        recordings.remove(key) ?: return
        val seconds = StationAudio.backend?.setRecording(key, null) ?: 0
        console.recording = false
        if (seconds == 0) {
            player?.displayClientMessage(
                Component.literal("Nothing recorded: talk into your voice chat mic while REC is on").withStyle(ChatFormatting.YELLOW), true
            )
            return
        }
        val text = if (console.announceEvery > 0) "Saved ${console.callSign} station ID (${seconds}s). It plays every ${console.announceEvery} tracks."
        else "Saved ${console.callSign} station ID (${seconds}s). Set \"ID every\" on the console to play it."
        player?.displayClientMessage(Component.literal(text).withStyle(ChatFormatting.GREEN), true)
    }

    private fun stopLongRecordings(server: MinecraftServer) {
        for ((key, recording) in recordings.entries.toList()) {
            if (tickCounter - recording.startTick < MAX_RECORDING_TICKS) continue
            val console = consoles[key] ?: continue
            finishRecording(key, console, server.playerList.getPlayer(recording.player))
        }
    }

    /** The next station up the dial that can be heard at [pos], for the tuners' SCAN button. */
    fun scan(level: ServerLevel, pos: BlockPos, from: Float): Float? {
        val dimension = level.dimension().location()
        val frequencies = consoles.filter { (key, console) ->
            key.dimension == dimension && console.broadcasting &&
                RadioNetworkManager.getSignalQuality(pos, RadioNetworkManager.getConnectedNetwork(key.dimension, key.pos)) > MIN_QUALITY
        }.values.map { it.frequency }.distinct().sorted()
        if (frequencies.isEmpty()) return null
        return frequencies.firstOrNull { it > from + 0.05f } ?: frequencies.first()
    }

    private fun playersNear(level: ServerLevel, center: Vec3, range: Double): List<ServerPlayer> =
        level.players().filter { it.distanceToSqr(center) <= range * range }
}
