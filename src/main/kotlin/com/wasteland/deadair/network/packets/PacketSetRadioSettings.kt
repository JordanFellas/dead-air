package com.wasteland.deadair.network.packets

import com.wasteland.deadair.DeadAir
import com.wasteland.deadair.item.HandheldRadioItem
import com.wasteland.deadair.item.RadioLocator
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.handling.IPayloadContext

data class PacketSetRadioSettings(
    /** Inventory slot of the radio, or [RadioLocator.SLOT_BELT]. */
    val slot: Int,
    val frequency: Float,
    val powerOn: Boolean,
    val volume: Int
) : CustomPacketPayload {

    companion object {
        val TYPE = CustomPacketPayload.Type<PacketSetRadioSettings>(
            ResourceLocation.fromNamespaceAndPath(DeadAir.MOD_ID, "set_radio_settings")
        )

        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, PacketSetRadioSettings> = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PacketSetRadioSettings::slot,
            ByteBufCodecs.FLOAT, PacketSetRadioSettings::frequency,
            ByteBufCodecs.BOOL, PacketSetRadioSettings::powerOn,
            ByteBufCodecs.VAR_INT, PacketSetRadioSettings::volume,
            ::PacketSetRadioSettings
        )

        fun handle(payload: PacketSetRadioSettings, context: IPayloadContext) {
            context.enqueueWork {
                val player = context.player() as? ServerPlayer ?: return@enqueueWork
                val stack = RadioLocator.getRadio(player, payload.slot)
                if (stack.isEmpty) return@enqueueWork
                HandheldRadioItem.setFrequency(stack, payload.frequency)
                HandheldRadioItem.setRadioOn(stack, payload.powerOn)
                HandheldRadioItem.setVolume(stack, payload.volume)
            }
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
