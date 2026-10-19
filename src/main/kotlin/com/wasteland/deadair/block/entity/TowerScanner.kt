package com.wasteland.deadair.block.entity

import com.wasteland.deadair.DeadAir
import com.wasteland.deadair.block.RadioAntenna
import com.wasteland.deadair.block.RadioTowerAntennaBlock
import com.wasteland.deadair.block.RadioTowerControllerBlock
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import kotlin.math.abs

/**
 * Free-form towers: everything in the `dead_air:tower_parts` tag that is connected to a controller
 * (including diagonally, so tapered and angled builds count) is one tower.
 */
object TowerScanner {
    val TOWER_PARTS: TagKey<Block> = TagKey.create(
        Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(DeadAir.MOD_ID, "tower_parts")
    )

    data class Result(
        val height: Int,
        val parts: Int,
        /** Where the signal radiates from: the tip of the highest antenna, or the highest part. */
        val top: BlockPos,
        val antennaBonus: Int,
        val hasTopAntenna: Boolean,
        /** Parts standing at the controller's own level, i.e. on the ground beside it. */
        val groundParts: Int
    )

    private val NEIGHBORS: List<BlockPos> = buildList {
        for (dx in -1..1) for (dy in -1..1) for (dz in -1..1) {
            if (dx != 0 || dy != 0 || dz != 0) add(BlockPos(dx, dy, dz))
        }
    }

    fun scan(level: Level, controller: BlockPos, type: TowerType): Result {
        val visited = HashSet<BlockPos>()
        val queue = ArrayDeque<BlockPos>()
        visited.add(controller)
        queue.add(controller)

        var top = controller
        var bestAntennaTop = Int.MIN_VALUE
        var bestAntennaTopBonus = 0
        var bestAntennaTip = controller
        val sideBonuses = ArrayList<Int>()
        var parts = 0
        var groundParts = 0

        while (queue.isNotEmpty() && parts < type.maxParts) {
            val pos = queue.removeFirst()
            for (offset in NEIGHBORS) {
                val next = pos.offset(offset)
                if (next.y < controller.y || next.y > controller.y + type.maxHeight) continue
                if (abs(next.x - controller.x) > type.maxSpread || abs(next.z - controller.z) > type.maxSpread) continue
                if (!visited.add(next)) continue
                if (!level.isLoaded(next)) continue
                val state = level.getBlockState(next)
                if (!state.`is`(TOWER_PARTS)) continue

                parts++
                if (next.y == controller.y) groundParts++
                queue.add(next)
                if (next.y > top.y) top = next

                val block = state.block
                if (block is RadioAntenna) {
                    // A tall antenna counts once, from its base part
                    if (block is RadioTowerAntennaBlock && state.getValue(RadioTowerAntennaBlock.PART) != 0) continue
                    val tipY = next.y + block.height - 1
                    if (tipY > bestAntennaTop) {
                        if (bestAntennaTop != Int.MIN_VALUE) sideBonuses.add(sideBonusOf(level, bestAntennaTip))
                        bestAntennaTop = tipY
                        bestAntennaTopBonus = block.topBonus
                        bestAntennaTip = next
                    } else {
                        sideBonuses.add(block.sideBonus)
                    }
                }
            }
        }

        val hasTopAntenna = bestAntennaTop != Int.MIN_VALUE && bestAntennaTop >= top.y
        var antennaBonus = sideBonuses.sortedDescending().take(type.maxSideAntennas).sum()
        val signalTop: BlockPos
        if (hasTopAntenna) {
            antennaBonus += bestAntennaTopBonus
            signalTop = BlockPos(bestAntennaTip.x, bestAntennaTop, bestAntennaTip.z)
        } else {
            if (bestAntennaTop != Int.MIN_VALUE) antennaBonus += sideBonusOf(level, bestAntennaTip)
            signalTop = top
        }
        return Result(top.y - controller.y, parts, signalTop, antennaBonus, hasTopAntenna, groundParts)
    }

    private fun sideBonusOf(level: Level, pos: BlockPos): Int = (level.getBlockState(pos).block as? RadioAntenna)?.sideBonus ?: 0

    /** From any tower part, finds the controller it's connected to (for opening the control panel). */
    fun findController(level: Level, from: BlockPos): BlockPos? {
        val visited = HashSet<BlockPos>()
        val queue = ArrayDeque<BlockPos>()
        visited.add(from)
        queue.add(from)
        while (queue.isNotEmpty() && visited.size < 15000) {
            val pos = queue.removeFirst()
            for (offset in NEIGHBORS) {
                val next = pos.offset(offset)
                if (!visited.add(next) || !level.isLoaded(next)) continue
                val state = level.getBlockState(next)
                if (state.block is RadioTowerControllerBlock) return next
                if (state.`is`(TOWER_PARTS)) queue.add(next)
            }
        }
        return null
    }
}
