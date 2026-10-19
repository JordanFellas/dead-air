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

    val SPEC: ModConfigSpec = builder.build()
}
