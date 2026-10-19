package com.wasteland.deadair.block

/** A block that adds range to a radio tower it's mounted on. */
interface RadioAntenna {
    /** Range added when mounted on top of the tower, centered over the controller. */
    val topBonus: Int
    /** Range added when mounted on the tower's sides (each, up to a limit). */
    val sideBonus: Int
    /** How many blocks tall the antenna is, so the signal origin sits at its tip. */
    val height: Int get() = 1
}
