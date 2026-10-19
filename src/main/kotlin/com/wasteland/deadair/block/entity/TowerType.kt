package com.wasteland.deadair.block.entity

/**
 * What a controller can drive. Towers are free-form: any connected build of tower parts counts.
 * Range = base + perHeight * height + perPart * parts (capped) + antenna bonuses.
 */
enum class TowerType(
    val displayName: String,
    val baseRadius: Int,
    val perHeight: Int,
    val perPart: Int,
    /** Parts beyond this stop adding range (height matters most). */
    val partCap: Int,
    val maxHeight: Int,
    /** How far sideways from the controller the tower may spread. */
    val maxSpread: Int,
    /** Hard limit on blocks scanned. */
    val maxParts: Int,
    val maxSideAntennas: Int,
    val drainBase: Int,
    /** One extra FE/tick per this many blocks of height. */
    val heightPerDrain: Int
) {
    /** Regular Radio Tower Controller. */
    REGULAR("Radio Tower", 120, 35, 2, 400, 48, 6, 2500, 6, 1, 14),

    /** Broadcast Controller on a 5x5 Tower Foundation pad. */
    BROADCAST("Broadcast Tower", 500, 90, 3, 3000, 160, 12, 12000, 12, 4, 8);

    fun drainPerTick(height: Int): Int = drainBase + height / heightPerDrain

    companion object {
        fun byOrdinal(i: Int): TowerType? = entries.getOrNull(i)
    }
}
