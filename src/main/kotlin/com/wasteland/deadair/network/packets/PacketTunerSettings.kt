package com.wasteland.deadair.network.packets

import com.wasteland.deadair.DeadAir
import com.wasteland.deadair.block.entity.HomeRadioBlockEntity
import com.wasteland.deadair.item.BoomboxItem
import com.wasteland.deadair.station.StationManager
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.network.handling.IPayloadContext

/**
 * Client -> server: tune a Home Radio (block position) or a boombox (inventory slot), or with
 * [scan] jump to the next station that can be heard there.
 */
data class PacketTunerSettings(
    val isBlock: Boolean,
    val pos: BlockPos,
    val slot: Int,
    val frequency: Float,
    val on: Boolean,
    val scan: Boolean
) : CustomPacketPayload {

    companion object {
        val TYPE = CustomPacketPayload.Type<PacketTunerSettings>(
            ResourceLocation.fromNamespaceAndPath(DeadAir.MOD_ID, "tuner_settings")
        )

        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, PacketTunerSettings> = StreamCodec.composite(
            ByteBufCodecs.BOOL, PacketTunerSettings::isBlock,
            BlockPos.STREAM_CODEC, PacketTunerSettings::pos,
            ByteBufCodecs.VAR_INT, PacketTunerSettings::slot,
            ByteBufCodecs.FLOAT, PacketTunerSettings::frequency,
            ByteBufCodecs.BOOL, PacketTunerSettings::on,
            ByteBufCodecs.BOOL, PacketTunerSettings::scan,
            ::PacketTunerSettings
        )

        fun handle(payload: PacketTunerSettings, context: IPayloadContext) {
            context.enqueueWork {
                val player = context.player() as? ServerPlayer ?: return@enqueueWork
                val level = player.serverLevel()
                var frequency = payload.frequency
                if (payload.scan) {
                    val where = if (payload.isBlock) payload.pos else player.blockPosition()
                    val found = StationManager.scan(level, where, frequency)
                    if (found == null) {
                        player.displayClientMessage(Component.literal("No stations in range").withStyle(ChatFormatting.GRAY), true)
                        return@enqueueWork
                    }
                    frequency = found
                }
                if (payload.isBlock) {
                    if (player.distanceToSqr(Vec3.atCenterOf(payload.pos)) > 64.0) return@enqueueWork
                    val radio = level.getBlockEntity(payload.pos) as? HomeRadioBlockEntity ?: return@enqueueWork
                    radio.applySettings(frequency, payload.on)
                } else {
                    if (payload.slot !in 0 until player.inventory.containerSize) return@enqueueWork
                    val stack = player.inventory.getItem(payload.slot)
                    if (stack.item !is BoomboxItem) return@enqueueWork
                    BoomboxItem.setFrequency(stack, frequency)
                    BoomboxItem.setOn(stack, payload.on)
                }
            }
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
