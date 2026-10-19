package com.wasteland.deadair.network.packets

import com.wasteland.deadair.DeadAir
import com.wasteland.deadair.client.ClientRadioState
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext

/** Server -> client: the signal strength where a radio carrier is standing, for the walkie screen. */
data class PacketRadioStatus(
    val quality: Float,
    val obstructed: Boolean
) : CustomPacketPayload {

    companion object {
        val TYPE = CustomPacketPayload.Type<PacketRadioStatus>(
            ResourceLocation.fromNamespaceAndPath(DeadAir.MOD_ID, "radio_status")
        )

        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, PacketRadioStatus> = StreamCodec.composite(
            ByteBufCodecs.FLOAT, PacketRadioStatus::quality,
            ByteBufCodecs.BOOL, PacketRadioStatus::obstructed,
            ::PacketRadioStatus
        )

        fun handle(payload: PacketRadioStatus, context: IPayloadContext) {
            context.enqueueWork { ClientRadioState.update(payload.quality, payload.obstructed) }
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
