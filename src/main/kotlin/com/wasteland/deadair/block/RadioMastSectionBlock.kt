package com.wasteland.deadair.block

import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape

/**
 * Red/white lattice mast pole in three sizes. Stacked straight up from a controller they form a
 * Guyed Mast. A section with a smaller size directly above it tapers to meet it (TAPER), so a
 * Large -> Medium -> Small stack narrows toward the top like a real mast. Climbable.
 */
class RadioMastSectionBlock(properties: Properties, val size: Size) : Block(properties) {

    enum class Size(val rank: Int, val rangeBonus: Int, halfWidth: Double) {
        SMALL(0, 20, 4.0),
        MEDIUM(1, 30, 5.5),
        LARGE(2, 45, 7.0);

        val shape: VoxelShape = Block.box(8 - halfWidth, 0.0, 8 - halfWidth, 8 + halfWidth, 16.0, 8 + halfWidth)
    }

    companion object {
        val TAPER: BooleanProperty = BooleanProperty.create("taper")
    }

    override fun codec(): MapCodec<RadioMastSectionBlock> = RecordCodecBuilder.mapCodec { instance ->
        instance.group(propertiesCodec()).apply(instance) { props -> RadioMastSectionBlock(props, size) }
    }

    init {
        registerDefaultState(stateDefinition.any().setValue(TAPER, false))
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(TAPER)
    }

    private fun tapersInto(above: BlockState): Boolean {
        val block = above.block
        return block is RadioMastSectionBlock && block.size.rank < size.rank
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState =
        defaultBlockState().setValue(TAPER, tapersInto(context.level.getBlockState(context.clickedPos.above())))

    override fun updateShape(
        state: BlockState, direction: Direction, neighborState: BlockState,
        level: LevelAccessor, pos: BlockPos, neighborPos: BlockPos
    ): BlockState {
        if (direction == Direction.UP) return state.setValue(TAPER, tapersInto(neighborState))
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos)
    }

    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape = size.shape

    override fun propagatesSkylightDown(state: BlockState, level: BlockGetter, pos: BlockPos): Boolean = true

    override fun isLadder(state: BlockState, level: LevelReader, pos: BlockPos, entity: LivingEntity?): Boolean = true

    /** Empty hand: open the connected tower's control panel. */
    override fun useWithoutItem(
        state: net.minecraft.world.level.block.state.BlockState, level: net.minecraft.world.level.Level,
        pos: net.minecraft.core.BlockPos, player: net.minecraft.world.entity.player.Player,
        hitResult: net.minecraft.world.phys.BlockHitResult
    ): net.minecraft.world.InteractionResult = TowerAccess.open(level, pos, player)
}