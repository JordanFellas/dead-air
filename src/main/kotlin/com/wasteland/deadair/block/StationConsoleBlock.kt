package com.wasteland.deadair.block

import com.mojang.serialization.MapCodec
import com.wasteland.deadair.block.entity.StationConsoleBlockEntity
import com.wasteland.deadair.registry.ModBlockEntities
import com.wasteland.deadair.station.StreamUrls
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerPlayer
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
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.level.block.state.properties.DirectionProperty
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape

/** Radio station desk: disc playlist, call sign, frequency and on-air switch. */
class StationConsoleBlock(properties: Properties) : BaseEntityBlock(properties) {

    companion object {
        val CODEC: MapCodec<StationConsoleBlock> = simpleCodec(::StationConsoleBlock)
        val FACING: DirectionProperty = HorizontalDirectionalBlock.FACING
        /** Lit "ON AIR" sign while broadcasting. */
        val LIVE: BooleanProperty = BooleanProperty.create("live")
        private val SHAPE: VoxelShape = box(0.0, 0.0, 0.0, 16.0, 12.0, 16.0)
    }

    init {
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIVE, false))
    }

    override fun codec(): MapCodec<out StationConsoleBlock> = CODEC

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(FACING, LIVE)
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState =
        defaultBlockState().setValue(FACING, context.horizontalDirection.opposite)

    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape = SHAPE

    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.MODEL

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = StationConsoleBlockEntity(pos, state)

    override fun <T : BlockEntity?> getTicker(
        level: Level,
        state: BlockState,
        blockEntityType: BlockEntityType<T>
    ): BlockEntityTicker<T>? {
        if (level.isClientSide) return null
        return createTickerHelper(blockEntityType, ModBlockEntities.STATION_CONSOLE_BE.get(), StationConsoleBlockEntity::tick)
    }

    override fun useWithoutItem(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hitResult: BlockHitResult
    ): InteractionResult {
        if (!level.isClientSide && player is ServerPlayer) {
            val be = level.getBlockEntity(pos) as? StationConsoleBlockEntity
            if (be != null) {
                player.openMenu(be) { buf ->
                    buf.writeBlockPos(pos)
                    buf.writeUtf(be.callSign, StationConsoleBlockEntity.MAX_CALL_SIGN * 4)
                    buf.writeUtf(be.streamUrl, StreamUrls.MAX_LENGTH * 4)
                }
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide)
    }

    override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, isMoving: Boolean) {
        if (!state.`is`(newState.block)) {
            (level.getBlockEntity(pos) as? StationConsoleBlockEntity)?.drops()
        }
        super.onRemove(state, level, pos, newState, isMoving)
    }
}
