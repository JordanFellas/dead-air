package com.wasteland.deadair.client

/** Last signal and station report from the server, shown on the walkie and tuner screens. */
object ClientRadioState {
    var quality: Float = 0.0f
        private set
    var obstructed: Boolean = false
        private set

    /** Call sign of the station you're listening to, or "" when none. */
    var stationCallSign: String = ""
        private set
    var stationTitle: String = ""
        private set
    var stationFrequency: Float = 0.0f
        private set

    fun update(quality: Float, obstructed: Boolean) {
        this.quality = quality
        this.obstructed = obstructed
    }

    fun updateStation(callSign: String, title: String, frequency: Float) {
        stationCallSign = callSign
        stationTitle = title
        stationFrequency = frequency
    }

    fun clear() {
        quality = 0.0f
        obstructed = false
        updateStation("", "", 0.0f)
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
