package com.wasteland.deadair.block

import com.wasteland.deadair.block.entity.TowerScanner
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.LadderBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult

class RadioTowerLadderBlock(properties: Properties) : LadderBlock(properties) {

    // Always climbable, even if a datapack replaces the minecraft:climbable tag.
    override fun isLadder(state: BlockState, level: LevelReader, pos: BlockPos, entity: LivingEntity?): Boolean = true

    /**
     * Tower ladders hang on any tower part (lattice, masts, sloped panels, platforms), not just solid
     * blocks, and can continue straight up from the ladder below to climb through a platform hatch.
     */
    override fun canSurvive(state: BlockState, level: LevelReader, pos: BlockPos): Boolean {
        val behind = level.getBlockState(pos.relative(state.getValue(FACING).opposite))
        if (behind.`is`(TowerScanner.TOWER_PARTS) || behind.block is RadioTowerControllerBlock) return true
        if (level.getBlockState(pos.below()).`is`(this)) return true
        return super.canSurvive(state, level, pos)
    }

    /** Empty hand: open the connected tower's control panel. */
    override fun useWithoutItem(state: BlockState, level: Level, pos: BlockPos, player: Player, hitResult: BlockHitResult): InteractionResult =
        TowerAccess.open(level, pos, player)
}
