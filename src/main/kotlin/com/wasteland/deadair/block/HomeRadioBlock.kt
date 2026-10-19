package com.wasteland.deadair.block

import com.mojang.serialization.MapCodec
import com.wasteland.deadair.block.entity.HomeRadioBlockEntity
import com.wasteland.deadair.client.ClientHooks
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.level.block.state.properties.DirectionProperty
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape

/** Tabletop radio: tune it to a station and it plays out loud for everyone nearby. */
class HomeRadioBlock(properties: Properties) : BaseEntityBlock(properties) {

    companion object {
        val CODEC: MapCodec<HomeRadioBlock> = simpleCodec(::HomeRadioBlock)
        val FACING: DirectionProperty = HorizontalDirectionalBlock.FACING
        val ON: BooleanProperty = BooleanProperty.create("on")
        private val SHAPE_NS: VoxelShape = box(1.0, 0.0, 4.0, 15.0, 10.0, 12.0)
        private val SHAPE_EW: VoxelShape = box(4.0, 0.0, 1.0, 12.0, 10.0, 15.0)
    }

    init {
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ON, true))
    }

    override fun codec(): MapCodec<out HomeRadioBlock> = CODEC

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(FACING, ON)
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState =
        defaultBlockState().setValue(FACING, context.horizontalDirection.opposite)

    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape =
        if (state.getValue(FACING).axis == Direction.Axis.Z) SHAPE_NS else SHAPE_EW

    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.MODEL

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = HomeRadioBlockEntity(pos, state)

    override fun useWithoutItem(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hitResult: BlockHitResult
    ): InteractionResult {
        if (level.isClientSide) ClientHooks.openHomeRadio(pos)
        return InteractionResult.sidedSuccess(level.isClientSide)
    }
}
