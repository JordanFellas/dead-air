package com.wasteland.deadair.network.packets

import com.wasteland.deadair.DeadAir
import com.wasteland.deadair.client.ClientRadioState
import com.wasteland.deadair.client.ClientSongUploader
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext

/** Server -> client: a message for the Station Console (song added, link failed...). */
data class PacketStationNotice(
    val message: String,
    val error: Boolean,
    /** The player's song upload is over (kept or refused), so stop sending it. */
    val uploadOver: Boolean
) : CustomPacketPayload {

    companion object {
        val TYPE = CustomPacketPayload.Type<PacketStationNotice>(
            ResourceLocation.fromNamespaceAndPath(DeadAir.MOD_ID, "station_notice")
        )

        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, PacketStationNotice> = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, PacketStationNotice::message,
            ByteBufCodecs.BOOL, PacketStationNotice::error,
            ByteBufCodecs.BOOL, PacketStationNotice::uploadOver,
            ::PacketStationNotice
        )

        fun handle(payload: PacketStationNotice, context: IPayloadContext) {
            context.enqueueWork {
                if (payload.uploadOver) ClientSongUploader.cancel()
                ClientRadioState.showNotice(payload.message, payload.error)
            }
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
