package com.wasteland.deadair.station

import com.wasteland.deadair.DeadAir
import com.wasteland.deadair.DeadAirConfig
import com.wasteland.deadair.block.entity.StationConsoleBlockEntity
import com.wasteland.deadair.network.packets.PacketStationNotice
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.network.PacketDistributor
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.URLDecoder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.Executors

/**
 * Adds songs to a station from where the player is, so nobody has to touch the server's files:
 * a file sent from the player's own computer, or a web link (direct MP3, Google Drive or Dropbox
 * share link) the server downloads. A link to a live radio stream switches the station to it.
 *
 * Every song is test-decoded before it's kept, saved in the station's own music folder, and
 * played right away.
 */
object SongImports {
    /** Bytes per upload packet; serverbound custom payloads are capped near 32 KB. */
    const val CHUNK_SIZE = 30_000
    private const val UPLOAD_TIMEOUT_MS = 60_000L
    private const val CONSOLE_REACH_SQ = 64.0

    private class Upload(
        val dimension: ResourceKey<Level>,
        val pos: BlockPos,
        val name: String,
        val data: ByteArray
    ) {
        var received = 0
        var lastActivity = System.currentTimeMillis()
    }

    private val uploads = HashMap<UUID, Upload>()
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "Dead Air Song Import").apply { isDaemon = true } }

    private val maxBytes: Long get() = DeadAirConfig.MAX_SONG_MB.get() * 1024L * 1024L

    // ---- Uploads from the player's computer (server thread) ----

    fun startUpload(player: ServerPlayer, pos: BlockPos, fileName: String, size: Long) {
        uploads.remove(player.uuid)
        val console = consoleNear(player, pos) ?: return
        val problem = importProblem(player) ?: when {
            fileName.substringAfterLast('.', "").lowercase() !in MusicLibrary.SUPPORTED -> "Only MP3 and WAV files can be added"
            size <= 0 -> "That file is empty"
            size > maxBytes -> "That song is too big (the limit is ${DeadAirConfig.MAX_SONG_MB.get()} MB)"
            else -> null
        }
        if (problem != null) {
            notify(player, problem, error = true, uploadOver = true)
            return
        }
        uploads[player.uuid] = Upload(player.level().dimension(), console.blockPos, fileName, ByteArray(size.toInt()))
    }

    fun receiveChunk(player: ServerPlayer, offset: Int, bytes: ByteArray) {
        val upload = uploads[player.uuid] ?: return
        if (offset != upload.received || offset + bytes.size > upload.data.size) {
            uploads.remove(player.uuid)
            notify(player, "The upload got mixed up, try again", error = true, uploadOver = true)
            return
        }
        System.arraycopy(bytes, 0, upload.data, offset, bytes.size)
        upload.received += bytes.size
        upload.lastActivity = System.currentTimeMillis()
    }

    fun finishUpload(player: ServerPlayer) {
        val upload = uploads.remove(player.uuid) ?: return
        if (upload.received != upload.data.size) {
            notify(player, "The upload didn't finish, try again", error = true, uploadOver = true)
            return
        }
        val server = player.server
        val console = server.getLevel(upload.dimension)?.getBlockEntity(upload.pos) as? StationConsoleBlockEntity
        if (console == null) {
            notify(player, "The Station Console is gone", error = true, uploadOver = true)
            return
        }
        val folder = MusicLibrary.stationFolder(console.callSign) ?: MusicLibrary.root
        val name = MusicLibrary.safeFileName(upload.name)
        worker.execute { keep(server, player.uuid, upload.dimension, upload.pos, folder, name, upload.data) }
    }

    /** Drops uploads that stalled (the player left or their game froze). */
    fun tick(server: MinecraftServer) {
        if (uploads.isEmpty()) return
        val now = System.currentTimeMillis()
        val iterator = uploads.entries.iterator()
        while (iterator.hasNext()) {
            val (uuid, upload) = iterator.next()
            if (now - upload.lastActivity < UPLOAD_TIMEOUT_MS) continue
            iterator.remove()
            server.playerList.getPlayer(uuid)?.let { notify(it, "The upload timed out", error = true, uploadOver = true) }
        }
    }

    fun clear() {
        uploads.clear()
    }

    // ---- Web links (downloaded by the server) ----

    fun addLink(player: ServerPlayer, console: StationConsoleBlockEntity, rawUrl: String) {
        val problem = importProblem(player) ?: StreamUrls.problemWith(rawUrl)
        if (problem != null) {
            notify(player, problem, error = true)
            return
        }
        val url = fixShareLink(rawUrl.trim())
        val server = player.server
        val dimension = player.level().dimension()
        val pos = console.blockPos
        val folder = MusicLibrary.stationFolder(console.callSign) ?: MusicLibrary.root
        notify(player, "Getting that link...", error = false)
        worker.execute { download(server, player.uuid, dimension, pos, folder, url, rawUrl.trim()) }
    }

    private fun download(
        server: MinecraftServer, playerId: UUID, dimension: ResourceKey<Level>, pos: BlockPos,
        folder: Path, url: String, originalUrl: String
    ) {
        try {
            val connection = StreamUrls.open(url)
            try {
                val type = connection.contentType?.lowercase().orEmpty()
                val length = connection.contentLengthLong
                val icy = connection.getHeaderField("icy-metaint") != null || connection.getHeaderField("icy-name") != null ||
                    connection.getHeaderField("icy-br") != null
                // Live radio never ends, so it has no length; a song file does.
                if (icy || (length < 0 && (type.startsWith("audio/") || type.startsWith("application/ogg")))) {
                    server.execute {
                        val console = console(server, dimension, pos) ?: return@execute
                        console.useStream(originalUrl)
                        player(server, playerId)?.let { notify(it, "That's a live radio stream, it's on the air now", error = false) }
                    }
                    return
                }
                if (type.startsWith("text/") || type.contains("html")) {
                    fail(server, playerId, "That link opens a web page, not a song. Use a direct download link to the MP3")
                    return
                }
                if (length > maxBytes) {
                    fail(server, playerId, "That song is too big (the limit is ${DeadAirConfig.MAX_SONG_MB.get()} MB)")
                    return
                }
                val buffer = ByteArrayOutputStream(if (length > 0) length.toInt() else 1 shl 20)
                val chunk = ByteArray(64 * 1024)
                connection.inputStream.use { input ->
                    while (true) {
                        val n = input.read(chunk)
                        if (n < 0) break
                        buffer.write(chunk, 0, n)
                        if (buffer.size() > maxBytes) {
                            fail(server, playerId, "That song is too big (the limit is ${DeadAirConfig.MAX_SONG_MB.get()} MB)")
                            return
                        }
                    }
                }
                val ext = if (type.contains("wav")) "wav" else "mp3"
                val name = MusicLibrary.safeFileName(fileNameOf(connection.getHeaderField("Content-Disposition"), url) ?: "Song", ext)
                keep(server, playerId, dimension, pos, folder, name, buffer.toByteArray())
            } finally {
                connection.disconnect()
            }
        } catch (e: StreamUrls.StreamUrlException) {
            fail(server, playerId, e.message ?: "Can't get that link")
        } catch (e: Exception) {
            DeadAir.LOGGER.debug("Dead Air: couldn't download {}", url, e)
            fail(server, playerId, "Couldn't download that link")
        }
    }

    // ---- Shared (worker thread) ----

    /** Checks the song plays, saves it in the station's folder, and starts it on the air. */
    private fun keep(
        server: MinecraftServer, playerId: UUID, dimension: ResourceKey<Level>, pos: BlockPos,
        folder: Path, name: String, data: ByteArray
    ) {
        val backend = StationAudio.backend
        if (backend == null) {
            fail(server, playerId, "Songs need Simple Voice Chat on the server", uploadOver = true)
            return
        }
        var temp: Path? = null
        try {
            Files.createDirectories(folder)
            temp = Files.createTempFile(folder, "importing-", ".${name.substringAfterLast('.')}")
            Files.write(temp, data)
            backend.validateSong(temp)?.let {
                fail(server, playerId, it, uploadOver = true)
                return
            }
            val target = MusicLibrary.uniqueTarget(folder, name)
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE)
            temp = null
            server.execute {
                val title = MusicLibrary.titleOf(target)
                val notPlaying = console(server, dimension, pos)?.onSongAdded(target)
                val message = if (notPlaying == null) "Added \"$title\", playing it now" else "Added \"$title\". $notPlaying to play it"
                player(server, playerId)?.let { notify(it, message, error = false, uploadOver = true) }
            }
        } catch (e: Exception) {
            DeadAir.LOGGER.warn("Dead Air: couldn't save song {}", name, e)
            fail(server, playerId, "Couldn't save that song on the server", uploadOver = true)
        } finally {
            temp?.let { runCatching { Files.deleteIfExists(it) } }
        }
    }

    /** Why this player can't add songs right now, or null. */
    fun importProblem(player: ServerPlayer): String? = when {
        !DeadAirConfig.SONG_IMPORTS_ENABLED.get() -> "Adding songs is turned off on this server"
        DeadAirConfig.SONG_IMPORTS_OPS_ONLY.get() && !player.hasPermissions(2) -> "Only operators can add songs here"
        StationAudio.backend == null -> "Songs need Simple Voice Chat on the server"
        MusicLibrary.totalBytes() > DeadAirConfig.MAX_MUSIC_FOLDER_MB.get() * 1024L * 1024L -> "The server's music folder is full"
        else -> null
    }

    /** Turns Google Drive and Dropbox share links into direct downloads. */
    private fun fixShareLink(url: String): String {
        Regex("drive\\.google\\.com/(?:file/d/|open\\?id=|uc\\?(?:.*&)?id=)([\\w-]+)").find(url)?.let {
            return "https://drive.google.com/uc?export=download&id=${it.groupValues[1]}"
        }
        if (url.contains("dropbox.com/")) {
            return when {
                url.contains("dl=0") -> url.replace("dl=0", "dl=1")
                url.contains("dl=1") -> url
                url.contains('?') -> "$url&dl=1"
                else -> "$url?dl=1"
            }
        }
        return url
    }

    private fun fileNameOf(disposition: String?, url: String): String? {
        disposition?.let { header ->
            Regex("filename\\*=UTF-8''([^;]+)", RegexOption.IGNORE_CASE).find(header)?.let {
                return URLDecoder.decode(it.groupValues[1].trim('"'), Charsets.UTF_8)
            }
            Regex("filename=\"?([^\";]+)\"?", RegexOption.IGNORE_CASE).find(header)?.let { return it.groupValues[1] }
        }
        val path = try {
            URI(url).path
        } catch (e: Exception) {
            null
        } ?: return null
        return path.substringAfterLast('/').takeIf { it.contains('.') }?.let { URLDecoder.decode(it, Charsets.UTF_8) }
    }

    private fun consoleNear(player: ServerPlayer, pos: BlockPos): StationConsoleBlockEntity? {
        if (player.distanceToSqr(Vec3.atCenterOf(pos)) > CONSOLE_REACH_SQ) return null
        return player.level().getBlockEntity(pos) as? StationConsoleBlockEntity
    }

    private fun console(server: MinecraftServer, dimension: ResourceKey<Level>, pos: BlockPos) =
        server.getLevel(dimension)?.getBlockEntity(pos) as? StationConsoleBlockEntity

    private fun player(server: MinecraftServer, id: UUID): ServerPlayer? = server.playerList.getPlayer(id)

    private fun fail(server: MinecraftServer, playerId: UUID, message: String, uploadOver: Boolean = false) {
        server.execute { player(server, playerId)?.let { notify(it, message, error = true, uploadOver = uploadOver) } }
    }

    fun notify(player: ServerPlayer, message: String, error: Boolean, uploadOver: Boolean = false) {
        PacketDistributor.sendToPlayer(player, PacketStationNotice(message, error, uploadOver))
    }
}
