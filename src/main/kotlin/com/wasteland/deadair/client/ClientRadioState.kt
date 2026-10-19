package com.wasteland.deadair.client

/** Last signal report from the server, shown on the walkie screen. */
object ClientRadioState {
    var quality: Float = 0.0f
        private set
    var obstructed: Boolean = false
        private set

    fun update(quality: Float, obstructed: Boolean) {
        this.quality = quality
        this.obstructed = obstructed
    }

    /** 0..4 bars, like a phone signal meter. */
    val bars: Int
        get() = when {
            quality <= 0.05f -> 0
            quality < 0.35f -> 1
            quality < 0.6f -> 2
            quality < 0.85f -> 3
            else -> 4
        }
}
