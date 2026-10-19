package com.wasteland.deadair.block

import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
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
import net.minecraft.world.level.block.state.properties.EnumProperty
import net.minecraft.util.StringRepresentable
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape

/**
 * Directional tower decoration: Sloped Lattice Panels (lean in toward the tower as they rise, for
 * tapering, angled sides) and Aviation Lights. Placed facing away from the player.
 */
class TowerDecorBlock(properties: Properties, private val shape: VoxelShape) : HorizontalDirectionalBlock(properties) {

    override fun codec(): MapCodec<out HorizontalDirectionalBlock> = RecordCodecBuilder.mapCodec { instance ->
        instance.group(propertiesCodec()).apply(instance) { props -> TowerDecorBlock(props, shape) }
    }

    /** At an outer corner, a second panel leans in from the side the slope wraps around to. */
    enum class Corner(private val id: String) : StringRepresentable {
        NONE("none"), CW("cw"), CCW("ccw");

        override fun getSerializedName(): String = id
    }

    companion object {
        val CORNER: EnumProperty<Corner> = EnumProperty.create("corner", Corner::class.java)
    }

    init {
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(CORNER, Corner.NONE))
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(FACING, CORNER)
    }

    /** FACING is the outer side: a sloped panel placed against the tower leans back over it. */
    override fun getStateForPlacement(context: BlockPlaceContext): BlockState? {
        val state = defaultBlockState().setValue(FACING, context.horizontalDirection.opposite)
        return state.setValue(CORNER, cornerAt(context.level, context.clickedPos, state.getValue(FACING)))
    }

    override fun updateShape(
        state: BlockState, direction: Direction, neighborState: BlockState,
        level: LevelAccessor, pos: BlockPos, neighborPos: BlockPos
    ): BlockState {
        if (direction.axis.isVertical) return state
        return state.setValue(CORNER, cornerAt(level, pos, state.getValue(FACING)))
    }

    /**
     * This panel is an outer corner when the panel behind it (toward the tower) faces sideways,
     * i.e. the next side of the ring starts here, and nothing continues on that side.
     */
    private fun cornerAt(level: BlockGetter, pos: BlockPos, facing: Direction): Corner {
        val behind = level.getBlockState(pos.relative(facing.opposite))
        if (!behind.`is`(this)) return Corner.NONE
        val behindFacing = behind.getValue(FACING)
        for ((side, corner) in listOf(facing.clockWise to Corner.CW, facing.counterClockWise to Corner.CCW)) {
            if (behindFacing == side && !level.getBlockState(pos.relative(side)).`is`(this)) return corner
        }
        return Corner.NONE
    }

    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape = shape

    override fun propagatesSkylightDown(state: BlockState, level: BlockGetter, pos: BlockPos): Boolean = true

    override fun rotate(state: BlockState, rotation: Rotation): BlockState =
        state.setValue(FACING, rotation.rotate(state.getValue(FACING)))

    override fun mirror(state: BlockState, mirror: Mirror): BlockState =
        state.rotate(mirror.getRotation(state.getValue(FACING)))

    override fun useWithoutItem(state: BlockState, level: Level, pos: BlockPos, player: Player, hitResult: BlockHitResult): InteractionResult =
        TowerAccess.open(level, pos, player)
}
