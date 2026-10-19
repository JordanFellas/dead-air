package com.wasteland.deadair.station

import com.wasteland.deadair.DeadAir
import net.neoforged.fml.loading.FMLPaths
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension

/**
 * Songs a station can stream through Simple Voice Chat, from `config/dead_air/music/` on the server.
 *
 * Files in the music folder itself are shared by every station. A subfolder named after a call
 * sign (`music/KIIS/`) holds that station's own songs; songs players add at a console go there.
 */
object MusicLibrary {
    val SUPPORTED = setOf("mp3", "wav")
    private const val MAX_FILE_BYTES = 200L * 1024 * 1024

    val root: Path by lazy {
        val dir = FMLPaths.CONFIGDIR.get().resolve("dead_air").resolve("music")
        try {
            Files.createDirectories(dir)
            val readme = dir.resolve("README.txt")
            if (!Files.exists(readme)) {
                Files.writeString(
                    readme,
                    "Dead Air radio station music\n" +
                        "============================\n" +
                        "Songs here (.mp3 or .wav) play on every Station Console with music turned on.\n" +
                        "A folder named after a station's call sign (for example music/KIIS) holds songs for that station only.\n" +
                        "Players can also add songs from the console's MUSIC tab; those are saved in the station's folder.\n" +
                        "Songs stream through Simple Voice Chat, so it must be installed on the server and the clients.\n" +
                        "Only use music you have the rights to play.\n"
                )
            }
        } catch (e: Exception) {
            DeadAir.LOGGER.warn("Dead Air: couldn't create the music folder {}", dir, e)
        }
        dir
    }

    /** The station's own folder (may not exist yet). */
    fun stationFolder(callSign: String): Path? = sanitize(callSign)?.let { root.resolve(it) }

    /** The station's own songs first, then the shared ones. */
    fun songsFor(callSign: String): List<Path> {
        val own = stationFolder(callSign)?.takeIf { it.isDirectory() }?.let { list(it) }.orEmpty()
        val names = own.mapTo(HashSet()) { it.name.lowercase() }
        return own + list(root).filter { it.name.lowercase() !in names }
    }

    /** True when [file] is in this station's own folder (so the station may remove it). */
    fun isOwnSong(callSign: String, file: Path): Boolean {
        val folder = stationFolder(callSign) ?: return false
        return file.toAbsolutePath().normalize().parent == folder.toAbsolutePath().normalize()
    }

    /** Total size of everything in the music folder, in bytes. */
    fun totalBytes(): Long = try {
        Files.walk(root).use { stream -> stream.filter { it.isRegularFile() }.mapToLong { Files.size(it) }.sum() }
    } catch (e: Exception) {
        0L
    }

    /** "Some_Artist - Cool-Song.mp3" -> "Some Artist - Cool-Song". */
    fun titleOf(file: Path): String = file.nameWithoutExtension.replace('_', ' ').trim()

    /** A safe file name for a song: no folders, odd characters or hidden names, and a supported extension. */
    fun safeFileName(raw: String, fallbackExtension: String = "mp3"): String {
        val base = raw.substringAfterLast('/').substringAfterLast('\\')
        val dot = base.lastIndexOf('.')
        val ext = if (dot > 0) base.substring(dot + 1).lowercase() else ""
        var stem = (if (dot > 0) base.substring(0, dot) else base)
            .filter { it.isLetterOrDigit() || it in " -_()&',!." }
            .trim(' ', '.')
            .take(80)
        if (stem.isEmpty()) stem = "Song"
        return "$stem.${if (ext in SUPPORTED) ext else fallbackExtension}"
    }

    /** [name] in [folder], numbered "(2)", "(3)"... if it's taken. */
    fun uniqueTarget(folder: Path, name: String): Path {
        var target = folder.resolve(name)
        val dot = name.lastIndexOf('.')
        var n = 2
        while (Files.exists(target)) {
            target = folder.resolve("${name.substring(0, dot)} ($n)${name.substring(dot)}")
            n++
        }
        return target
    }

    private fun list(dir: Path): List<Path> = try {
        Files.list(dir).use { stream ->
            stream.filter {
                it.isRegularFile() && it.extension.lowercase() in SUPPORTED && !it.name.startsWith("importing-") &&
                    Files.size(it) <= MAX_FILE_BYTES
            }
                .sorted()
                .toList()
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun sanitize(callSign: String): String? {
        val clean = callSign.trim().filter { it.isLetterOrDigit() || it == ' ' || it == '-' || it == '_' }.trim()
        return clean.ifEmpty { null }
    }
}
