package com.wasteland.deadair.block

import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape

/**
 * Parabolic dishes and Yagi antennas: mostly decoration, but they add range when mounted on a
 * tower's top or sides. They face away from the player who places them.
 */
class DirectionalAntennaBlock(
    properties: Properties,
    override val topBonus: Int,
    override val sideBonus: Int,
    private val shape: VoxelShape
) : HorizontalDirectionalBlock(properties), RadioAntenna {

    override fun codec(): MapCodec<out HorizontalDirectionalBlock> = RecordCodecBuilder.mapCodec { instance ->
        instance.group(propertiesCodec()).apply(instance) { props -> DirectionalAntennaBlock(props, topBonus, sideBonus, shape) }
    }

    init {
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH))
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(FACING)
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState? =
        defaultBlockState().setValue(FACING, context.horizontalDirection.opposite)

    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape = shape

    override fun propagatesSkylightDown(state: BlockState, level: BlockGetter, pos: BlockPos): Boolean = true

    override fun rotate(state: BlockState, rotation: Rotation): BlockState =
        state.setValue(FACING, rotation.rotate(state.getValue(FACING)))

    override fun mirror(state: BlockState, mirror: Mirror): BlockState =
        state.rotate(mirror.getRotation(state.getValue(FACING)))

    /** Empty hand: open the connected tower's control panel. */
    override fun useWithoutItem(
        state: net.minecraft.world.level.block.state.BlockState, level: net.minecraft.world.level.Level,
        pos: net.minecraft.core.BlockPos, player: net.minecraft.world.entity.player.Player,
        hitResult: net.minecraft.world.phys.BlockHitResult
    ): net.minecraft.world.InteractionResult = TowerAccess.open(level, pos, player)
}