package com.wasteland.deadair.block

import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.AttachFace
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.level.block.state.properties.EnumProperty
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape

/**
 * Red aviation warning light. Click the top of a block to stand it up, the side of a block to bolt
 * it to that wall, or the underside to hang it from the ceiling. For walls, FACING points away from
 * the wall it's mounted on.
 */
class AviationLightBlock(properties: Properties) : HorizontalDirectionalBlock(properties) {

    companion object {
        val CODEC: MapCodec<AviationLightBlock> = simpleCodec(::AviationLightBlock)
        val FACE: EnumProperty<AttachFace> = BlockStateProperties.ATTACH_FACE
        /** Standing on a platform's railing at the FACING edge (sits on the handrail). */
        val RAIL: BooleanProperty = BooleanProperty.create("rail")
        /** How close (0..1 of a block) to a railed edge a click must be to snap onto the railing. */
        private const val RAIL_SNAP = 0.35

        private val RAIL_NORTH: VoxelShape = box(5.0, 0.0, 0.0, 11.0, 6.0, 3.0)
        private val RAIL_SOUTH: VoxelShape = box(5.0, 0.0, 13.0, 11.0, 6.0, 16.0)
        private val RAIL_WEST: VoxelShape = box(0.0, 0.0, 5.0, 3.0, 6.0, 11.0)
        private val RAIL_EAST: VoxelShape = box(13.0, 0.0, 5.0, 16.0, 6.0, 11.0)

        private val FLOOR: VoxelShape = box(5.0, 0.0, 5.0, 11.0, 7.0, 11.0)
        private val CEILING: VoxelShape = box(5.0, 9.0, 5.0, 11.0, 16.0, 11.0)
        private val WALL_NORTH: VoxelShape = box(5.0, 4.0, 8.0, 11.0, 11.0, 16.0)
        private val WALL_SOUTH: VoxelShape = box(5.0, 4.0, 0.0, 11.0, 11.0, 8.0)
        private val WALL_WEST: VoxelShape = box(8.0, 4.0, 5.0, 16.0, 11.0, 11.0)
        private val WALL_EAST: VoxelShape = box(0.0, 4.0, 5.0, 8.0, 11.0, 11.0)
    }

    override fun codec(): MapCodec<AviationLightBlock> = CODEC

    init {
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FACE, AttachFace.FLOOR).setValue(RAIL, false))
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(FACING, FACE, RAIL)
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState? {
        val clicked = context.clickedFace
        return when (clicked) {
            Direction.UP -> railEdge(context)?.let { edge ->
                defaultBlockState().setValue(FACE, AttachFace.FLOOR).setValue(RAIL, true).setValue(FACING, edge)
            } ?: defaultBlockState().setValue(FACE, AttachFace.FLOOR).setValue(FACING, context.horizontalDirection.opposite)
            Direction.DOWN -> defaultBlockState().setValue(FACE, AttachFace.CEILING).setValue(FACING, context.horizontalDirection.opposite)
            else -> defaultBlockState().setValue(FACE, AttachFace.WALL).setValue(FACING, clicked)
        }
    }

    /** On top of a platform, the railed edge nearest the click, if the click was close enough to it. */
    private fun railEdge(context: BlockPlaceContext): Direction? {
        val below = context.level.getBlockState(context.clickedPos.below())
        if (below.block !is TowerPlatformBlock) return null
        val hit = context.clickLocation
        val fx = hit.x - context.clickedPos.x
        val fz = hit.z - context.clickedPos.z
        val distances = mapOf(
            Direction.NORTH to fz, Direction.SOUTH to 1.0 - fz,
            Direction.WEST to fx, Direction.EAST to 1.0 - fx
        )
        return distances
            .filter { (side, d) -> d <= RAIL_SNAP && !below.getValue(RadioTowerFrameBlock.propertyFor(side)!!) }
            .minByOrNull { it.value }?.key
    }

    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape =
        if (state.getValue(RAIL)) when (state.getValue(FACING)) {
            Direction.NORTH -> RAIL_NORTH
            Direction.SOUTH -> RAIL_SOUTH
            Direction.WEST -> RAIL_WEST
            else -> RAIL_EAST
        } else when (state.getValue(FACE)) {
            AttachFace.FLOOR -> FLOOR
            AttachFace.CEILING -> CEILING
            else -> when (state.getValue(FACING)) {
                Direction.NORTH -> WALL_NORTH
                Direction.SOUTH -> WALL_SOUTH
                Direction.WEST -> WALL_WEST
                else -> WALL_EAST
            }
        }

    override fun propagatesSkylightDown(state: BlockState, level: BlockGetter, pos: BlockPos): Boolean = true

    override fun rotate(state: BlockState, rotation: Rotation): BlockState =
        state.setValue(FACING, rotation.rotate(state.getValue(FACING)))

    override fun mirror(state: BlockState, mirror: Mirror): BlockState =
        state.rotate(mirror.getRotation(state.getValue(FACING)))

    override fun useWithoutItem(state: BlockState, level: Level, pos: BlockPos, player: Player, hitResult: BlockHitResult): InteractionResult =
        TowerAccess.open(level, pos, player)
}
