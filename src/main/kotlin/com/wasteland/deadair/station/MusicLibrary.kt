package com.wasteland.deadair.station

import com.wasteland.deadair.DeadAir
import net.neoforged.fml.loading.FMLPaths
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.nameWithoutExtension

/**
 * Songs a station can stream through Simple Voice Chat, from `config/dead_air/music/` on the server.
 *
 * A subfolder named after a station's call sign (`music/KIIS/`) is that station's own playlist;
 * otherwise a station plays the files in the music folder itself.
 */
object MusicLibrary {
    val SUPPORTED = setOf("mp3", "wav")
    private const val MAX_FILE_BYTES = 64L * 1024 * 1024

    private val root: Path by lazy {
        val dir = FMLPaths.CONFIGDIR.get().resolve("dead_air").resolve("music")
        try {
            Files.createDirectories(dir)
            val readme = dir.resolve("README.txt")
            if (!Files.exists(readme)) {
                Files.writeString(
                    readme,
                    "Dead Air radio station music\n" +
                        "============================\n" +
                        "Drop .mp3 or .wav files here and every Station Console with SONGS turned on can play them.\n" +
                        "Make a folder named after a station's call sign (for example music/KIIS) to give that station its own playlist.\n" +
                        "Songs stream through Simple Voice Chat, so it must be installed on the server and the clients.\n" +
                        "Only use music you have the rights to play.\n"
                )
            }
        } catch (e: Exception) {
            DeadAir.LOGGER.warn("Dead Air: couldn't create the music folder {}", dir, e)
        }
        dir
    }

    fun songsFor(callSign: String): List<Path> {
        val own = sanitize(callSign)?.let { root.resolve(it) }
        val dir = if (own != null && own.isDirectory()) own else root
        return try {
            Files.list(dir).use { stream ->
                stream.filter { it.isRegularFile() && it.extension.lowercase() in SUPPORTED && Files.size(it) <= MAX_FILE_BYTES }
                    .sorted()
                    .toList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** "Some_Artist - Cool-Song.mp3" -> "Some Artist - Cool-Song". */
    fun titleOf(file: Path): String = file.nameWithoutExtension.replace('_', ' ').trim()

    private fun sanitize(callSign: String): String? {
        val clean = callSign.trim().filter { it.isLetterOrDigit() || it == ' ' || it == '-' || it == '_' }
        return clean.ifEmpty { null }
    }
}
