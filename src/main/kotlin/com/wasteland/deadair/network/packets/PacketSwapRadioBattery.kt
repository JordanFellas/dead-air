package com.wasteland.deadair.network.packets

import com.wasteland.deadair.DeadAir
import com.wasteland.deadair.item.BatterySwap
import com.wasteland.deadair.item.HandheldRadioItem
import com.wasteland.deadair.item.RadioLocator
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.handling.IPayloadContext

/** Client -> server: swap the fullest Scrap Battery Cell into the walkie in [slot]. */
data class PacketSwapRadioBattery(val slot: Int) : CustomPacketPayload {

    companion object {
        val TYPE = CustomPacketPayload.Type<PacketSwapRadioBattery>(
            ResourceLocation.fromNamespaceAndPath(DeadAir.MOD_ID, "swap_radio_battery")
        )

        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, PacketSwapRadioBattery> = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PacketSwapRadioBattery::slot,
            ::PacketSwapRadioBattery
        )

        fun handle(payload: PacketSwapRadioBattery, context: IPayloadContext) {
            context.enqueueWork {
                val player = context.player() as? ServerPlayer ?: return@enqueueWork
                val radio = RadioLocator.getRadio(player, payload.slot)
                if (radio.isEmpty) return@enqueueWork
                BatterySwap.swap(player, radio, HandheldRadioItem.MAX_ENERGY, "Radio")
            }
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
