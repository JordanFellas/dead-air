package com.wasteland.deadair.network.packets

import com.wasteland.deadair.DeadAir
import com.wasteland.deadair.client.sound.RadioStaticSound
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext

/**
 * Tells the client to play a short burst of static from its own radio.
 */
data class PacketRadioStatic(
    val durationTicks: Int,
    val volume: Float
) : CustomPacketPayload {

    companion object {
        /** Static played when a transmission finds no tower. */
        const val DEAD_AIR_TICKS = 20

        val TYPE = CustomPacketPayload.Type<PacketRadioStatic>(
            ResourceLocation.fromNamespaceAndPath(DeadAir.MOD_ID, "radio_static")
        )

        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, PacketRadioStatic> = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PacketRadioStatic::durationTicks,
            ByteBufCodecs.FLOAT, PacketRadioStatic::volume,
            ::PacketRadioStatic
        )

        fun handle(payload: PacketRadioStatic, context: IPayloadContext) {
            context.enqueueWork {
                RadioStaticSound.play(payload.durationTicks, payload.volume)
            }
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
