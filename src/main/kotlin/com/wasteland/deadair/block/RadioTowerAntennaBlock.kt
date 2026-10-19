package com.wasteland.deadair.block

import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.IntegerProperty
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape

/**
 * The 4-block-tall red/white mast antenna that crowns a radio tower. Placing the item builds all
 * four parts; breaking any part removes the whole antenna (dropping one item).
 */
class RadioTowerAntennaBlock(properties: Properties) : Block(properties), RadioAntenna {

    companion object {
        val CODEC: MapCodec<RadioTowerAntennaBlock> = simpleCodec(::RadioTowerAntennaBlock)
        const val PARTS = 4
        val PART: IntegerProperty = IntegerProperty.create("part", 0, PARTS - 1)

        private val SHAPES: Array<VoxelShape> = arrayOf(
            box(3.0, 0.0, 3.0, 13.0, 16.0, 13.0),
            box(4.5, 0.0, 4.5, 11.5, 16.0, 11.5),
            box(5.5, 0.0, 5.5, 10.5, 16.0, 10.5),
            box(6.5, 0.0, 6.5, 9.5, 16.0, 9.5)
        )
    }

    override val topBonus: Int = 600
    override val sideBonus: Int = 0
    override val height: Int = PARTS

    override fun codec(): MapCodec<RadioTowerAntennaBlock> = CODEC

    init {
        registerDefaultState(stateDefinition.any().setValue(PART, 0))
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(PART)
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState? {
        val pos = context.clickedPos
        val level = context.level
        for (i in 1 until PARTS) {
            val above = pos.above(i)
            if (above.y >= level.maxBuildHeight || !level.getBlockState(above).canBeReplaced(context)) return null
        }
        return defaultBlockState()
    }

    override fun setPlacedBy(level: Level, pos: BlockPos, state: BlockState, placer: LivingEntity?, stack: ItemStack) {
        for (i in 1 until PARTS) {
            level.setBlock(pos.above(i), state.setValue(PART, i), 3)
        }
    }

    /** Any missing neighbor part collapses the rest of the antenna. */
    override fun updateShape(
        state: BlockState, direction: Direction, neighborState: BlockState,
        level: LevelAccessor, pos: BlockPos, neighborPos: BlockPos
    ): BlockState {
        val part = state.getValue(PART)
        val broken = when (direction) {
            Direction.UP -> part < PARTS - 1 && !(neighborState.`is`(this) && neighborState.getValue(PART) == part + 1)
            Direction.DOWN -> part > 0 && !(neighborState.`is`(this) && neighborState.getValue(PART) == part - 1)
            else -> false
        }
        return if (broken) Blocks.AIR.defaultBlockState()
        else super.updateShape(state, direction, neighborState, level, pos, neighborPos)
    }

    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape =
        SHAPES[state.getValue(PART)]

    override fun propagatesSkylightDown(state: BlockState, level: BlockGetter, pos: BlockPos): Boolean = true

    /** Empty hand: open the connected tower's control panel. */
    override fun useWithoutItem(
        state: net.minecraft.world.level.block.state.BlockState, level: net.minecraft.world.level.Level,
        pos: net.minecraft.core.BlockPos, player: net.minecraft.world.entity.player.Player,
        hitResult: net.minecraft.world.phys.BlockHitResult
    ): net.minecraft.world.InteractionResult = TowerAccess.open(level, pos, player)
}