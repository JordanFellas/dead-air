package com.wasteland.deadair.network.packets

import com.wasteland.deadair.DeadAir
import com.wasteland.deadair.client.sound.StationDiscPlayer
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext

/**
 * Server -> client: start (or with an empty [sound], stop) a station's music disc coming out of a
 * walkie (your own), a Home Radio (block position) or a boombox (carrier's entity id).
 */
data class PacketStationDisc(
    val sinkType: Int,
    val sinkId: Long,
    val sound: String,
    val volume: Float
) : CustomPacketPayload {

    companion object {
        const val SINK_WALKIE = 0
        const val SINK_RADIO = 1
        const val SINK_BOOMBOX = 2

        val TYPE = CustomPacketPayload.Type<PacketStationDisc>(
            ResourceLocation.fromNamespaceAndPath(DeadAir.MOD_ID, "station_disc")
        )

        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, PacketStationDisc> = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PacketStationDisc::sinkType,
            ByteBufCodecs.VAR_LONG, PacketStationDisc::sinkId,
            ByteBufCodecs.STRING_UTF8, PacketStationDisc::sound,
            ByteBufCodecs.FLOAT, PacketStationDisc::volume,
            ::PacketStationDisc
        )

        fun handle(payload: PacketStationDisc, context: IPayloadContext) {
            context.enqueueWork { StationDiscPlayer.handle(payload) }
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
