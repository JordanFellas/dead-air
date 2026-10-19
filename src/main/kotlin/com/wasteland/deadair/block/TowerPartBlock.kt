package com.wasteland.deadair.block

import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult

/** A plain full tower block (Tower Foundation) that also opens the connected control panel. */
class TowerPartBlock(properties: Properties) : Block(properties) {

    companion object {
        val CODEC: MapCodec<TowerPartBlock> = simpleCodec(::TowerPartBlock)
    }

    override fun codec(): MapCodec<TowerPartBlock> = CODEC

    override fun useWithoutItem(state: BlockState, level: Level, pos: BlockPos, player: Player, hitResult: BlockHitResult): InteractionResult =
        TowerAccess.open(level, pos, player)
}
