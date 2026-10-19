package com.wasteland.deadair.network.packets

import com.wasteland.deadair.DeadAir
import com.wasteland.deadair.client.ClientRadioState
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext

/** Server -> client: the station this player is listening to and what's on (empty call sign = none). */
data class PacketStationInfo(
    val callSign: String,
    val title: String,
    val frequency: Float
) : CustomPacketPayload {

    companion object {
        val TYPE = CustomPacketPayload.Type<PacketStationInfo>(
            ResourceLocation.fromNamespaceAndPath(DeadAir.MOD_ID, "station_info")
        )

        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, PacketStationInfo> = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, PacketStationInfo::callSign,
            ByteBufCodecs.STRING_UTF8, PacketStationInfo::title,
            ByteBufCodecs.FLOAT, PacketStationInfo::frequency,
            ::PacketStationInfo
        )

        fun handle(payload: PacketStationInfo, context: IPayloadContext) {
            context.enqueueWork { ClientRadioState.updateStation(payload.callSign, payload.title, payload.frequency) }
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
