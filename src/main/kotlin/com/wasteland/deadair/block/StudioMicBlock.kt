package com.wasteland.deadair.block

import com.mojang.serialization.MapCodec
import com.wasteland.deadair.station.StationManager
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape

/**
 * Broadcast microphone. Stand next to it (within 3 blocks) and talk with Simple Voice Chat to go
 * live on the station console it's near. Right-click to record (and right-click again to save)
 * the station's looping announcement.
 */
class StudioMicBlock(properties: Properties) : HorizontalDirectionalBlock(properties) {

    companion object {
        val CODEC: MapCodec<StudioMicBlock> = simpleCodec(::StudioMicBlock)
        /** How close a DJ has to stand to be picked up (a bit past arm's reach, so right-click to record works). */
        const val PICKUP_RANGE = 4.5
        private val SHAPE: VoxelShape = box(5.0, 0.0, 5.0, 11.0, 15.0, 11.0)
    }

    init {
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH))
    }

    override fun codec(): MapCodec<out HorizontalDirectionalBlock> = CODEC

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(FACING)
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState =
        defaultBlockState().setValue(FACING, context.horizontalDirection.opposite)

    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape = SHAPE

    override fun useWithoutItem(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hitResult: BlockHitResult
    ): InteractionResult {
        if (!level.isClientSide && player is ServerPlayer) StationManager.toggleRecording(player, pos)
        return InteractionResult.sidedSuccess(level.isClientSide)
    }
}
