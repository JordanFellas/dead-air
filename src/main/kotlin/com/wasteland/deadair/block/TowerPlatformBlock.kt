package com.wasteland.deadair.block

import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

/**
 * Grated catwalk deck for tower galleries. Connects to neighboring platforms and puts a railing
 * on every open edge, so a ring of platforms makes a walkable gallery like on real towers.
 */
class TowerPlatformBlock(properties: Properties) : RadioTowerFrameBlock(properties) {

    companion object {
        val PLATFORM_CODEC: MapCodec<TowerPlatformBlock> = simpleCodec(::TowerPlatformBlock)
        private val DECK: VoxelShape = box(0.0, 0.0, 0.0, 16.0, 2.0, 16.0)
        private val RAIL_NORTH: VoxelShape = box(0.0, 2.0, 0.0, 16.0, 14.0, 1.0)
        private val RAIL_SOUTH: VoxelShape = box(0.0, 2.0, 15.0, 16.0, 14.0, 16.0)
        private val RAIL_WEST: VoxelShape = box(0.0, 2.0, 0.0, 1.0, 14.0, 16.0)
        private val RAIL_EAST: VoxelShape = box(15.0, 2.0, 0.0, 16.0, 14.0, 16.0)
    }

    override fun codec(): MapCodec<TowerPlatformBlock> = PLATFORM_CODEC

    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape {
        var shape = DECK
        if (!state.getValue(NORTH)) shape = Shapes.or(shape, RAIL_NORTH)
        if (!state.getValue(SOUTH)) shape = Shapes.or(shape, RAIL_SOUTH)
        if (!state.getValue(WEST)) shape = Shapes.or(shape, RAIL_WEST)
        if (!state.getValue(EAST)) shape = Shapes.or(shape, RAIL_EAST)
        return shape
    }
}
