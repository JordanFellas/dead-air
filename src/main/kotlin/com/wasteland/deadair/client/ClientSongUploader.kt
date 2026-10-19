package com.wasteland.deadair.client

import com.wasteland.deadair.network.packets.PacketSongUpload
import com.wasteland.deadair.station.SongImports
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.neoforged.neoforge.network.PacketDistributor
import org.lwjgl.system.MemoryStack
import org.lwjgl.util.tinyfd.TinyFileDialogs
import java.net.URI
import java.net.URLDecoder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

/**
 * Sends a song from this computer to the server, a few pieces every tick so the game keeps
 * running smoothly. The server checks the file, keeps it and puts it on the air.
 */
object ClientSongUploader {
    /** About 3.6 MB per second. */
    private const val CHUNKS_PER_TICK = 6
    private const val MAX_BYTES = 200L * 1024 * 1024

    private var data: ByteArray? = null
    private var sent = 0
    var fileName: String = ""
        private set

    val isUploading: Boolean get() = data != null

    /** 0..1 while uploading. */
    val progress: Float get() = data?.let { if (it.isEmpty()) 1f else sent.toFloat() / it.size } ?: 0f

    /**
     * If [text] is a path to a file on this computer (C:\..., "C:\..." from Copy as path, or
     * file:///...), returns it; otherwise null (it's a web link or nonsense).
     */
    fun localPath(text: String): Path? {
        val t = text.trim().trim('"')
        if (t.isEmpty()) return null
        return try {
            when {
                t.startsWith("file:", ignoreCase = true) -> Paths.get(URI(t.replace(" ", "%20")))
                Regex("^[A-Za-z]:[\\\\/]").containsMatchIn(t) || t.startsWith("\\\\") -> {
                    val decoded = if (t.contains("%20")) runCatching { URLDecoder.decode(t.replace("+", "%2B"), Charsets.UTF_8) }.getOrNull() else null
                    Paths.get(decoded ?: t)
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Starts sending [file] to the console at [pos]. Returns why it can't, or null. */
    fun start(pos: BlockPos, file: Path): String? {
        if (isUploading) return "Already sending a song, wait for it to finish"
        if (!file.isRegularFile()) return "Can't find that file on your computer"
        if (file.extension.lowercase() !in setOf("mp3", "wav")) return "Only MP3 and WAV files can be added"
        val size = Files.size(file)
        if (size <= 0) return "That file is empty"
        if (size > MAX_BYTES) return "That file is too big"
        val bytes = try {
            Files.readAllBytes(file)
        } catch (e: Exception) {
            return "Can't read that file"
        }
        data = bytes
        sent = 0
        fileName = file.name
        PacketDistributor.sendToServer(PacketSongUpload(PacketSongUpload.START, pos, file.name, bytes.size.toLong()))
        return null
    }

    fun cancel() {
        data = null
        sent = 0
    }

    /** Client tick: send the next few pieces, then tell the server we're done. */
    fun tick() {
        val bytes = data ?: return
        if (Minecraft.getInstance().connection == null) {
            cancel()
            return
        }
        repeat(CHUNKS_PER_TICK) {
            if (sent >= bytes.size) return@repeat
            val end = minOf(bytes.size, sent + SongImports.CHUNK_SIZE)
            PacketDistributor.sendToServer(PacketSongUpload(PacketSongUpload.CHUNK, offset = sent, bytes = bytes.copyOfRange(sent, end)))
            sent = end
        }
        if (sent >= bytes.size) {
            PacketDistributor.sendToServer(PacketSongUpload(PacketSongUpload.END))
            data = null // the server answers with a notice once it has checked and kept the song
        }
    }

    /**
     * Opens the computer's "pick a file" window (on its own thread so the game doesn't freeze)
     * and calls [onPicked] on the game thread with the chosen file.
     */
    fun browse(onPicked: (Path) -> Unit) {
        Thread({
            val chosen = MemoryStack.stackPush().use { stack ->
                val filters = stack.mallocPointer(2)
                filters.put(stack.UTF8("*.mp3"))
                filters.put(stack.UTF8("*.wav"))
                filters.flip()
                TinyFileDialogs.tinyfd_openFileDialog("Pick a song for your station", null, filters, "Songs (MP3, WAV)", false)
            } ?: return@Thread
            Minecraft.getInstance().execute { onPicked(Paths.get(chosen)) }
        }, "Dead Air Song Picker").apply { isDaemon = true }.start()
    }
}
