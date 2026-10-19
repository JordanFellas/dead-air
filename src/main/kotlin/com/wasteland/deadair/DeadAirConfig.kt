package com.wasteland.deadair

import net.neoforged.neoforge.common.ModConfigSpec

/** Server owner settings, in `config/dead_air-common.toml`. */
object DeadAirConfig {
    private val builder = ModConfigSpec.Builder()

    val STREAM_URLS_ENABLED: ModConfigSpec.BooleanValue = builder
        .comment("Let Station Consoles play a stream URL (a direct MP3 link or an MP3 internet radio stream).")
        .define("stations.streamUrlsEnabled", true)

    val STREAM_URLS_OPS_ONLY: ModConfigSpec.BooleanValue = builder
        .comment("Only operators may set a station's stream URL.")
        .define("stations.streamUrlsOpsOnly", false)

    val STREAM_ALLOW_LOCAL: ModConfigSpec.BooleanValue = builder
        .comment(
            "Allow stream URLs that point at this machine or the local network (localhost, 192.168.x.x, ...).",
            "Off by default so players can't use the server to reach private addresses."
        )
        .define("stations.allowLocalAddresses", false)

    val SONG_IMPORTS_ENABLED: ModConfigSpec.BooleanValue = builder
        .comment("Let players add songs at a Station Console, from a file on their own computer or a web link.")
        .define("stations.songImportsEnabled", true)

    val SONG_IMPORTS_OPS_ONLY: ModConfigSpec.BooleanValue = builder
        .comment("Only operators may add or remove station songs.")
        .define("stations.songImportsOpsOnly", false)

    val MAX_SONG_MB: ModConfigSpec.IntValue = builder
        .comment("Largest song file players can add, in megabytes.")
        .defineInRange("stations.maxSongMegabytes", 25, 1, 200)

    val MAX_MUSIC_FOLDER_MB: ModConfigSpec.IntValue = builder
        .comment("Stop accepting new songs once config/dead_air/music holds this many megabytes.")
        .defineInRange("stations.maxMusicFolderMegabytes", 1000, 10, 100000)

    val SPEC: ModConfigSpec = builder.build()
}
