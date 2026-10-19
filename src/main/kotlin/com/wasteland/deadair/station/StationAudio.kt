package com.wasteland.deadair.station

import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import java.nio.file.Path
import java.util.UUID

/** Identifies a radio station (its console) across dimensions. */
data class StationKey(val dimension: ResourceLocation, val pos: BlockPos) {
    /** File-name-safe id, used for announcement recordings and audio channel ids. */
    val fileId: String get() = "${dimension.namespace}_${dimension.path.replace('/', '_')}_${pos.x}_${pos.y}_${pos.z}"
}

/** Who is tuned in to a station right now, and how clearly (0..1). Built on the server thread. */
data class StationListeners(
    val level: ServerLevel,
    /** Players listening on a walkie-talkie (private audio). */
    val walkies: Map<UUID, Float>,
    /** Home Radio blocks (audio plays from the block). */
    val radios: Map<BlockPos, Float>,
    /** Players carrying a playing boombox (audio follows them). */
    val boomboxes: Map<ServerPlayer, Float>
)

/**
 * Streams station audio (song files, the live DJ mic, recorded announcements) to listeners.
 * Implemented by the Simple Voice Chat integration; null when voice chat isn't installed, in which
 * case stations can still play music discs (those play on each listener's client).
 */
interface StationAudioBackend {
    /** Starts streaming an MP3/WAV file. Decoding happens off-thread. */
    fun play(station: StationKey, file: Path)
    /** Starts relaying a stream URL (direct MP3 link or MP3 internet radio). Connects off-thread. */
    fun playStream(station: StationKey, url: String)
    /** The stream's current song (or station) name, if it sent one. */
    fun streamTitle(station: StationKey): String?
    /** Why the stream stopped, if it failed. */
    fun streamError(station: StationKey): String?
    fun stop(station: StationKey)
    /** True when nothing is streaming (the last file ended, failed to load, or was stopped). */
    fun isFinished(station: StationKey): Boolean
    fun updateListeners(station: StationKey, listeners: StationListeners)
    /**
     * Start (file != null) or stop and save a recording of the station's DJ mic as a WAV file.
     * When stopping, returns the seconds of voice captured (nothing is saved for 0).
     */
    fun setRecording(station: StationKey, file: Path?): Int
    fun remove(station: StationKey)
    fun shutdown()

    /** Tries to decode a song file. Null when it plays, otherwise why not. Blocking: call off-thread. */
    fun validateSong(file: Path): String?
}

object StationAudio {
    @Volatile
    var backend: StationAudioBackend? = null

    /** Player -> the station whose Studio Mic they're standing at. Read by the voice chat thread. */
    @Volatile
    var djStations: Map<UUID, StationKey> = emptyMap()
}
