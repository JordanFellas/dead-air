package com.wasteland.deadair.block.entity

import com.wasteland.deadair.DeadAirConfig
import com.wasteland.deadair.block.StationConsoleBlock
import com.wasteland.deadair.station.StreamUrls
import com.wasteland.deadair.block.StudioMicBlock
import com.wasteland.deadair.menu.StationConsoleMenu
import com.wasteland.deadair.network.RadioNetworkManager
import com.wasteland.deadair.registry.ModBlockEntities
import com.wasteland.deadair.station.MusicLibrary
import com.wasteland.deadair.station.StationAudio
import com.wasteland.deadair.station.StationKey
import com.wasteland.deadair.station.StationManager
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.core.NonNullList
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.Container
import net.minecraft.world.ContainerHelper
import net.minecraft.world.Containers
import net.minecraft.world.MenuProvider
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerData
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.JukeboxSong
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.LevelResource
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.roundToInt

/**
 * A radio station: plays a playlist of music discs and server song files, a looping recorded
 * station ID, and whoever is talking at a nearby Studio Mic, out over the tower network on its
 * frequency. It only broadcasts while a powered tower covers it.
 */
class StationConsoleBlockEntity(pos: BlockPos, state: BlockState) :
    BlockEntity(ModBlockEntities.STATION_CONSOLE_BE.get(), pos, state),
    MenuProvider,
    Container {

    companion object {
        const val DISC_SLOTS = 9
        const val DATA_COUNT = 13
        const val MAX_CALL_SIGN = 12
        const val MIC_RANGE_XZ = 8
        const val MIC_RANGE_Y = 4
        /** Ticks of silence between tracks. */
        private const val TRACK_GAP = 30
        /** Pause between repeats when the station ID is the only thing to play. */
        private const val ID_LOOP_GAP = 20 * 15

        const val FLAG_ON_AIR = 1
        const val FLAG_DISCS = 2
        const val FLAG_SONGS = 4
        const val FLAG_SHUFFLE = 8
        /** Play the stream URL instead of the playlist. */
        const val FLAG_STREAM = 16
        /** Wait before reconnecting a stream that dropped or failed. */
        private const val STREAM_RETRY_GAP = 20 * 10

        const val STATUS_OFF_AIR = 0
        const val STATUS_NO_TOWER = 1
        const val STATUS_ON_AIR = 2

        fun tick(level: Level, pos: BlockPos, state: BlockState, entity: StationConsoleBlockEntity) {
            entity.serverTick()
        }

        fun isDisc(stack: ItemStack): Boolean = stack.has(DataComponents.JUKEBOX_PLAYABLE)

        fun announcementFile(level: ServerLevel, key: StationKey): Path =
            level.server.getWorldPath(LevelResource.ROOT).resolve("dead_air").resolve("announcements").resolve("${key.fileId}.wav")
    }

    sealed class Track(open val title: String) {
        class Disc(val slot: Int, val sound: ResourceLocation, val lengthTicks: Int, title: String) : Track(title)
        class Song(val file: Path, title: String) : Track(title)
        class Announcement(val file: Path) : Track("Station ID")
        /** A live stream URL; the title follows the stream's own song titles. */
        class Stream(val url: String, private val key: StationKey) : Track("Live stream") {
            override val title: String get() = StationAudio.backend?.streamTitle(key) ?: "Live stream"
        }
    }

    val items: NonNullList<ItemStack> = NonNullList.withSize(DISC_SLOTS, ItemStack.EMPTY)

    // Settings (saved)
    var callSign: String = "KDAR"
        private set
    var frequency: Float = 99.9f
        private set
    /** New consoles start off the air; START BROADCAST turns them on. */
    var onAir = false
    var playDiscs = true
    var playSongs = true
    var shuffle = false
    /** Play the recorded station ID after every N tracks; 0 = never. */
    var announceEvery = 3
    var playStream = false
    /** Direct MP3 link or MP3 internet radio stream. */
    var streamUrl: String = ""
        private set

    // Runtime state
    val key: StationKey? get() = level?.let { StationKey(it.dimension().location(), worldPosition) }
    var broadcasting = false
        private set
    var hasTower = false
        private set
    var current: Track? = null
        private set
    /** Bumped every time a new track starts, so listeners can tell tracks apart. */
    var trackSerial = 0
        private set
    var listenerCount = 0
    var songCount = 0
        private set
    var micPositions: List<BlockPos> = emptyList()
        private set
    var recording = false

    private var trackEndTick = 0L
    private var nextStartTick = 0L
    private var tracksSinceId = 0
    /** Playing the station ID on its own because the playlist is empty. */
    private var idOnlyLoop = false
    private val queue = ArrayDeque<Track>()
    private var tickCounter = 0
    private var lastStreamTitle: String? = null
    /** Why the stream last failed, shown on the console while it waits to reconnect. */
    var streamError: String? = null
        private set

    /** One line in the console's MUSIC tab. [id] is "disc:<slot>" or "song:<file name>". */
    data class PlaylistEntry(val id: String, val title: String, val removable: Boolean)

    /** Everything this station can play, for the MUSIC tab. Bumps [playlistVersion] when it changes. */
    var playlist: List<PlaylistEntry> = emptyList()
        private set
    var playlistVersion = 0
        private set

    /** Playlist id of what's on the air now, or "". */
    val currentEntryId: String
        get() = when (val track = current) {
            is Track.Disc -> "disc:${track.slot}"
            is Track.Song -> "song:${track.file.fileName}"
            else -> ""
        }

    private val discCount: Int get() = items.count { isDisc(it) }

    val dataAccess: ContainerData = object : ContainerData {
        override fun get(index: Int): Int = when (index) {
            0 -> (frequency * 10).roundToInt()
            1 -> flags()
            2 -> announceEvery
            3 -> when {
                !onAir -> STATUS_OFF_AIR
                !hasTower -> STATUS_NO_TOWER
                else -> STATUS_ON_AIR
            }
            4 -> listenerCount
            5 -> songCount
            6 -> if (hasAnnouncement()) 1 else 0
            7 -> if (recording) 1 else 0
            8 -> if (StationAudio.backend != null) 1 else 0
            9 -> if (DeadAirConfig.STREAM_URLS_ENABLED.get()) 1 else 0
            10 -> discCount
            11 -> if (streamUrl.isNotBlank()) 1 else 0
            12 -> if (hasTower) 1 else 0
            else -> 0
        }

        override fun set(index: Int, value: Int) {}
        override fun getCount(): Int = DATA_COUNT
    }

    private fun flags(): Int =
        (if (onAir) FLAG_ON_AIR else 0) or (if (playDiscs) FLAG_DISCS else 0) or
            (if (playSongs) FLAG_SONGS else 0) or (if (shuffle) FLAG_SHUFFLE else 0) or
            (if (playStream) FLAG_STREAM else 0)

    /** True when the station should be relaying its stream URL instead of the playlist. */
    private val streaming: Boolean
        get() = playStream && streamUrl.isNotBlank() && StationAudio.backend != null && DeadAirConfig.STREAM_URLS_ENABLED.get()

    /** [streamUrl] null = leave the URL as it is. */
    fun applySettings(callSign: String, frequency: Float, flags: Int, announceEvery: Int, streamUrl: String? = null) {
        val cleanSign = callSign.filter { it.code in 32..126 }.trim().take(MAX_CALL_SIGN).ifEmpty { "KDAR" }
        val cleanFreq = ((frequency * 10).roundToInt() / 10.0f).coerceIn(0.1f, 999.9f)
        val cleanUrl = streamUrl?.trim()?.take(StreamUrls.MAX_LENGTH) ?: this.streamUrl
        val playlistChanged = cleanSign != this.callSign || (flags and FLAG_DISCS != 0) != playDiscs ||
            (flags and FLAG_SONGS != 0) != playSongs || (flags and FLAG_SHUFFLE != 0) != shuffle
        val streamChanged = cleanUrl != this.streamUrl || (flags and FLAG_STREAM != 0) != playStream
        this.callSign = cleanSign
        this.frequency = cleanFreq
        onAir = flags and FLAG_ON_AIR != 0
        playDiscs = flags and FLAG_DISCS != 0
        playSongs = flags and FLAG_SONGS != 0
        shuffle = flags and FLAG_SHUFFLE != 0
        playStream = flags and FLAG_STREAM != 0
        this.streamUrl = cleanUrl
        this.announceEvery = announceEvery.coerceIn(0, 9)
        if (playlistChanged) {
            queue.clear()
            refreshPlaylist()
        }
        if (streamChanged) {
            // Switch between stream and playlist (or to the new URL) right away.
            stopTrack()
            streamError = null
            nextStartTick = 0
        }
        setChanged()
        level?.sendBlockUpdated(worldPosition, blockState, blockState, 3)
    }

    fun skip() {
        stopTrack()
        nextStartTick = 0
    }

    /** Plays the recorded station ID right now. Returns why not, or null. */
    fun playIdNow(): String? {
        val lvl = level as? ServerLevel ?: return null
        val k = key ?: return null
        val file = announcementFile(lvl, k)
        return when {
            !broadcasting -> notLiveReason()
            StationAudio.backend == null -> "The station ID needs Simple Voice Chat on the server"
            !Files.exists(file) -> "No station ID yet: right-click a Studio Mic to record one"
            else -> {
                stopTrack()
                playTrack(Track.Announcement(file), lvl.gameTime)
                tracksSinceId = 0
                null
            }
        }
    }

    /** Plays one playlist entry right now (switching off the live stream). Returns why not, or null. */
    fun playEntryNow(id: String): String? {
        val lvl = level as? ServerLevel ?: return null
        if (!broadcasting) return notLiveReason()
        val track = trackFor(id) ?: return "That song isn't in the playlist anymore"
        if (track is Track.Song && StationAudio.backend == null) return "Songs need Simple Voice Chat on the server"
        if (playStream) {
            playStream = false
            setChanged()
        }
        stopTrack()
        streamError = null
        playTrack(track, lvl.gameTime)
        return null
    }

    /** Deletes one of this station's own songs. Returns why not, or null. */
    fun removeSong(id: String): String? {
        val file = (trackFor(id) as? Track.Song)?.file ?: return "That song isn't in the playlist anymore"
        if (!MusicLibrary.isOwnSong(callSign, file)) return "That song is shared by every station; remove it from the server's music folder"
        if ((current as? Track.Song)?.file == file) skip()
        return try {
            Files.deleteIfExists(file)
            queue.clear()
            refreshPlaylist()
            null
        } catch (e: Exception) {
            "Couldn't remove that song"
        }
    }

    /** A song was just added from the MUSIC tab: put it on the air. Returns why it couldn't play yet, or null. */
    fun onSongAdded(file: java.nio.file.Path): String? {
        queue.clear()
        playSongs = true
        refreshPlaylist()
        setChanged()
        return playEntryNow("song:${file.fileName}")
    }

    /** A pasted link turned out to be live radio: relay it. */
    fun useStream(url: String) {
        applySettings(callSign, frequency, flags() or FLAG_STREAM, announceEvery, url)
    }

    /** What's stopping the station from broadcasting, in plain words. */
    fun notLiveReason(): String = when {
        !onAir -> "Press START BROADCAST first"
        !hasTower -> "No powered radio tower in range"
        else -> "The station isn't on the air"
    }

    private fun trackFor(id: String): Track? {
        if (id.startsWith("disc:")) {
            val slot = id.removePrefix("disc:").toIntOrNull() ?: return null
            if (slot !in 0 until DISC_SLOTS) return null
            val (sound, song) = discIn(slot) ?: return null
            return Track.Disc(slot, sound, song.lengthInTicks(), song.description().string)
        }
        if (id.startsWith("song:")) {
            val name = id.removePrefix("song:")
            val file = MusicLibrary.songsFor(callSign).firstOrNull { it.fileName.toString() == name } ?: return null
            return Track.Song(file, MusicLibrary.titleOf(file))
        }
        return null
    }

    /** Rebuilds the MUSIC tab list from the disc rack and the song folders. */
    fun refreshPlaylist() {
        val entries = ArrayList<PlaylistEntry>()
        for (slot in 0 until DISC_SLOTS) {
            val (_, song) = discIn(slot) ?: continue
            entries.add(PlaylistEntry("disc:$slot", "Disc: ${song.description().string}", false))
        }
        val songs = MusicLibrary.songsFor(callSign)
        songCount = songs.size
        for (file in songs) {
            entries.add(PlaylistEntry("song:${file.fileName}", MusicLibrary.titleOf(file), MusicLibrary.isOwnSong(callSign, file)))
        }
        if (entries != playlist) {
            playlist = entries
            playlistVersion++
        }
    }

    fun hasAnnouncement(): Boolean {
        val lvl = level as? ServerLevel ?: return false
        val k = key ?: return false
        return Files.exists(announcementFile(lvl, k))
    }

    private fun serverTick() {
        val lvl = level as? ServerLevel ?: return
        tickCounter++

        if (tickCounter % 60 == 1) {
            scanForMics(lvl)
            refreshPlaylist()
        }
        if (tickCounter % 10 == 0) {
            hasTower = RadioNetworkManager.getConnectedNetwork(lvl.dimension().location(), worldPosition).isNotEmpty()
            val wasBroadcasting = broadcasting
            broadcasting = onAir && hasTower
            if (wasBroadcasting != broadcasting) updateBlockState(broadcasting)
            StationManager.register(this)
        }

        if (!broadcasting) {
            if (current != null) stopTrack()
            return
        }
        val now = lvl.gameTime
        val playing = current
        if (playing != null && isTrackFinished(now)) {
            if (playing is Track.Stream) key?.let { streamError = StationAudio.backend?.streamError(it) ?: "Stream ended" }
            stopTrack()
            nextStartTick = now + when {
                playing is Track.Stream -> STREAM_RETRY_GAP
                idOnlyLoop -> ID_LOOP_GAP
                else -> TRACK_GAP
            }
        }
        // A new song title on the stream counts as a new track (listeners see it pop up).
        if (current is Track.Stream && tickCounter % 20 == 0) {
            val title = key?.let { StationAudio.backend?.streamTitle(it) }
            if (title != null && title != lastStreamTitle) {
                lastStreamTitle = title
                streamError = null
                trackSerial++
            }
        }
        if (current == null && now >= nextStartTick) startNextTrack(lvl, now)
    }

    private fun isTrackFinished(now: Long): Boolean {
        return when (val track = current) {
            is Track.Disc -> now >= trackEndTick || discIn(track.slot)?.let { it.first != track.sound } ?: true
            is Track.Song, is Track.Announcement, is Track.Stream -> StationAudio.backend?.isFinished(key ?: return true) ?: true
            null -> true
        }
    }

    private fun startNextTrack(lvl: ServerLevel, now: Long) {
        val k = key ?: return
        if (streaming) {
            lastStreamTitle = null
            playTrack(Track.Stream(streamUrl, k), now)
            return
        }
        val idFile = announcementFile(lvl, k)
        val backend = StationAudio.backend
        val canPlayId = backend != null && announceEvery > 0 && Files.exists(idFile)

        idOnlyLoop = false
        if (canPlayId && tracksSinceId >= announceEvery) {
            playTrack(Track.Announcement(idFile), now)
            tracksSinceId = 0
            return
        }
        if (queue.isEmpty()) refillQueue(lvl)
        val next = queue.removeFirstOrNull()
        if (next == null) {
            // Nothing in the playlist: loop the station ID on its own, or wait for something to play.
            if (canPlayId) {
                idOnlyLoop = true
                playTrack(Track.Announcement(idFile), now)
            } else {
                nextStartTick = now + 40
            }
            return
        }
        playTrack(next, now)
        tracksSinceId++
    }

    private fun refillQueue(lvl: ServerLevel) {
        val tracks = ArrayList<Track>()
        if (playDiscs) {
            for (slot in 0 until DISC_SLOTS) {
                val (sound, song) = discIn(slot) ?: continue
                tracks.add(Track.Disc(slot, sound, song.lengthInTicks(), song.description().string))
            }
        }
        if (playSongs && StationAudio.backend != null) {
            for (file in MusicLibrary.songsFor(callSign)) tracks.add(Track.Song(file, MusicLibrary.titleOf(file)))
        }
        if (shuffle) tracks.shuffle()
        queue.addAll(tracks)
    }

    private fun discIn(slot: Int): Pair<ResourceLocation, JukeboxSong>? {
        val lvl = level ?: return null
        val stack = items[slot]
        if (stack.isEmpty) return null
        val song = JukeboxSong.fromStack(lvl.registryAccess(), stack).orElse(null)?.value() ?: return null
        return song.soundEvent().value().location to song
    }

    private fun playTrack(track: Track, now: Long) {
        val k = key ?: return
        when (track) {
            is Track.Disc -> trackEndTick = now + track.lengthTicks
            is Track.Song -> StationAudio.backend?.play(k, track.file)
            is Track.Announcement -> StationAudio.backend?.play(k, track.file)
            is Track.Stream -> StationAudio.backend?.playStream(k, track.url)
        }
        current = track
        trackSerial++
    }

    private fun stopTrack() {
        val track = current ?: return
        if (track !is Track.Disc) key?.let { StationAudio.backend?.stop(it) }
        current = null
    }

    private fun scanForMics(lvl: Level) {
        val found = ArrayList<BlockPos>()
        for (pos in BlockPos.betweenClosed(
            worldPosition.offset(-MIC_RANGE_XZ, -MIC_RANGE_Y, -MIC_RANGE_XZ),
            worldPosition.offset(MIC_RANGE_XZ, MIC_RANGE_Y, MIC_RANGE_XZ)
        )) {
            if (lvl.getBlockState(pos).block is StudioMicBlock) found.add(pos.immutable())
        }
        micPositions = found
    }

    private fun updateBlockState(live: Boolean) {
        val state = blockState
        if (state.hasProperty(StationConsoleBlock.LIVE) && state.getValue(StationConsoleBlock.LIVE) != live) {
            level?.setBlock(worldPosition, state.setValue(StationConsoleBlock.LIVE, live), 3)
        }
    }

    override fun setRemoved() {
        super.setRemoved()
        val lvl = level
        if (lvl != null && !lvl.isClientSide) {
            stopTrack()
            key?.let { StationManager.unregister(it) }
        }
    }

    fun drops() {
        val lvl = level ?: return
        Containers.dropContents(lvl, worldPosition, items)
    }

    override fun saveAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.saveAdditional(tag, registries)
        tag.putString("CallSign", callSign)
        tag.putFloat("Frequency", frequency)
        tag.putInt("Flags", flags())
        tag.putInt("AnnounceEvery", announceEvery)
        tag.putString("StreamUrl", streamUrl)
        ContainerHelper.saveAllItems(tag, items, registries)
    }

    override fun loadAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.loadAdditional(tag, registries)
        if (tag.contains("CallSign")) callSign = tag.getString("CallSign")
        if (tag.contains("Frequency")) frequency = tag.getFloat("Frequency")
        if (tag.contains("Flags")) {
            val flags = tag.getInt("Flags")
            onAir = flags and FLAG_ON_AIR != 0
            playDiscs = flags and FLAG_DISCS != 0
            playSongs = flags and FLAG_SONGS != 0
            shuffle = flags and FLAG_SHUFFLE != 0
            playStream = flags and FLAG_STREAM != 0
        }
        if (tag.contains("AnnounceEvery")) announceEvery = tag.getInt("AnnounceEvery")
        if (tag.contains("StreamUrl")) streamUrl = tag.getString("StreamUrl")
        items.clear()
        ContainerHelper.loadAllItems(tag, items, registries)
    }

    override fun getUpdateTag(registries: HolderLookup.Provider): CompoundTag {
        val tag = CompoundTag()
        tag.putString("CallSign", callSign)
        tag.putFloat("Frequency", frequency)
        return tag
    }

    override fun getDisplayName(): Component = Component.literal("Station Console")

    override fun createMenu(containerId: Int, playerInventory: Inventory, player: Player): AbstractContainerMenu =
        StationConsoleMenu(containerId, playerInventory, this, dataAccess, worldPosition, callSign, streamUrl)

    // Container: the disc playlist
    override fun getContainerSize(): Int = DISC_SLOTS
    override fun isEmpty(): Boolean = items.all { it.isEmpty }
    override fun getItem(slot: Int): ItemStack = items[slot]
    override fun removeItem(slot: Int, amount: Int): ItemStack = ContainerHelper.removeItem(items, slot, amount).also { if (!it.isEmpty) onDiscsChanged() }
    override fun removeItemNoUpdate(slot: Int): ItemStack = ContainerHelper.takeItem(items, slot).also { onDiscsChanged() }
    override fun setItem(slot: Int, stack: ItemStack) {
        items[slot] = stack
        onDiscsChanged()
    }
    override fun getMaxStackSize(): Int = 1
    override fun canPlaceItem(slot: Int, stack: ItemStack): Boolean = isDisc(stack)
    override fun stillValid(player: Player): Boolean = Container.stillValidBlockEntity(this, player)
    override fun clearContent() = items.clear()

    private fun onDiscsChanged() {
        queue.clear()
        if (level?.isClientSide == false) refreshPlaylist()
        setChanged()
    }
}
