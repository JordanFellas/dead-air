package com.wasteland.deadair.block

import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

/**
 * Lattice tower frame. Connects to neighboring frames of the same kind (like a fence), and only
 * draws lattice on its open sides with legs at outer corners, so a ring of frames reads as one
 * tower wall instead of a stack of cubes.
 */
open class RadioTowerFrameBlock(properties: Properties) : Block(properties) {

    companion object {
        val CODEC: MapCodec<RadioTowerFrameBlock> = simpleCodec(::RadioTowerFrameBlock)
        val NORTH: BooleanProperty = BlockStateProperties.NORTH
        val EAST: BooleanProperty = BlockStateProperties.EAST
        val SOUTH: BooleanProperty = BlockStateProperties.SOUTH
        val WEST: BooleanProperty = BlockStateProperties.WEST

        fun propertyFor(direction: Direction): BooleanProperty? = when (direction) {
            Direction.NORTH -> NORTH
            Direction.EAST -> EAST
            Direction.SOUTH -> SOUTH
            Direction.WEST -> WEST
            else -> null
        }
    }

    override fun codec(): MapCodec<out RadioTowerFrameBlock> = CODEC

    init {
        registerDefaultState(
            stateDefinition.any().setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false).setValue(WEST, false)
        )
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(NORTH, EAST, SOUTH, WEST)
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState {
        var state = defaultBlockState()
        for (direction in Direction.Plane.HORIZONTAL) {
            val neighbor = context.level.getBlockState(context.clickedPos.relative(direction))
            state = state.setValue(propertyFor(direction)!!, neighbor.`is`(this))
        }
        return state
    }

    override fun updateShape(
        state: BlockState, direction: Direction, neighborState: BlockState,
        level: LevelAccessor, pos: BlockPos, neighborPos: BlockPos
    ): BlockState {
        val property = propertyFor(direction) ?: return state
        return state.setValue(property, neighborState.`is`(this))
    }

    override fun propagatesSkylightDown(state: BlockState, level: BlockGetter, pos: BlockPos): Boolean = true

    override fun skipRendering(state: BlockState, adjacentState: BlockState, direction: Direction): Boolean {
        return adjacentState.`is`(this) || super.skipRendering(state, adjacentState, direction)
    }

    override fun getVisualShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape {
        return Shapes.empty()
    }

    /** Empty hand: open the connected tower's control panel. */
    override fun useWithoutItem(
        state: net.minecraft.world.level.block.state.BlockState, level: net.minecraft.world.level.Level,
        pos: net.minecraft.core.BlockPos, player: net.minecraft.world.entity.player.Player,
        hitResult: net.minecraft.world.phys.BlockHitResult
    ): net.minecraft.world.InteractionResult = TowerAccess.open(level, pos, player)
}