package com.wasteland.deadair.network.packets

import com.wasteland.deadair.DeadAir
import com.wasteland.deadair.client.sound.RadioSounds
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext

/**
 * Server -> client: an incoming voice transmission opened (click + static bed) or closed (roger beep).
 * [quality] sets how loud the static is: weaker signal, more hiss.
 */
data class PacketRadioSquelch(
    val open: Boolean,
    val quality: Float
) : CustomPacketPayload {

    companion object {
        val TYPE = CustomPacketPayload.Type<PacketRadioSquelch>(
            ResourceLocation.fromNamespaceAndPath(DeadAir.MOD_ID, "radio_squelch")
        )

        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, PacketRadioSquelch> = StreamCodec.composite(
            ByteBufCodecs.BOOL, PacketRadioSquelch::open,
            ByteBufCodecs.FLOAT, PacketRadioSquelch::quality,
            ::PacketRadioSquelch
        )

        fun handle(payload: PacketRadioSquelch, context: IPayloadContext) {
            context.enqueueWork {
                if (payload.open) RadioSounds.openSquelch(payload.quality) else RadioSounds.closeSquelch()
            }
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
