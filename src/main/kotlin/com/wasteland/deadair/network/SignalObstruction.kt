package com.wasteland.deadair.network

import com.wasteland.deadair.DeadAir
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.phys.Vec3
import kotlin.math.pow

/**
 * Blocks in the `dead_air:signal_blocking_blocks` tag weaken radio signals that pass through them.
 * The path is traced from a player toward their tower's top; only the first [MAX_TRACE] blocks
 * are checked (what's around the player matters most, and far chunks may not be loaded).
 */
object SignalObstruction {
    val SIGNAL_BLOCKING: TagKey<Block> = TagKey.create(
        Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(DeadAir.MOD_ID, "signal_blocking_blocks")
    )

    private const val MAX_TRACE = 96.0
    /** Each blocking block in the path keeps this fraction of the signal. */
    private const val PER_BLOCK_FACTOR = 0.8

    /** 1.0 = clear path, toward 0.0 = heavily blocked. Main thread only. */
    fun factor(level: Level, from: Vec3, to: Vec3): Float {
        val delta = to.subtract(from)
        val length = delta.length()
        if (length < 1.0e-3) return 1.0f
        val dir = delta.scale(1.0 / length)
        val steps = (minOf(length, MAX_TRACE) * 2).toInt()
        var last: BlockPos? = null
        var blocking = 0
        for (i in 0..steps) {
            val point = from.add(dir.scale(i * 0.5))
            val pos = BlockPos.containing(point)
            if (pos == last) continue
            last = pos
            if (!level.isLoaded(pos)) break
            if (level.getBlockState(pos).`is`(SIGNAL_BLOCKING)) blocking++
        }
        return PER_BLOCK_FACTOR.pow(blocking).toFloat()
    }
}
