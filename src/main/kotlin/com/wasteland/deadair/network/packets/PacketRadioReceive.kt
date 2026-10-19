package com.wasteland.deadair.network.packets

import com.wasteland.deadair.DeadAir
import com.wasteland.deadair.client.sound.RadioStaticSound
import com.wasteland.deadair.registry.ModSounds
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundSource
import net.neoforged.neoforge.network.handling.IPayloadContext

data class PacketRadioReceive(
    val senderName: String,
    val frequency: Float,
    val message: String,
    val quality: Float
) : CustomPacketPayload {

    companion object {
        private const val RECEIVE_STATIC_TICKS = 30

        val TYPE = CustomPacketPayload.Type<PacketRadioReceive>(
            ResourceLocation.fromNamespaceAndPath(DeadAir.MOD_ID, "radio_receive")
        )

        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, PacketRadioReceive> = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, PacketRadioReceive::senderName,
            ByteBufCodecs.FLOAT, PacketRadioReceive::frequency,
            ByteBufCodecs.STRING_UTF8, PacketRadioReceive::message,
            ByteBufCodecs.FLOAT, PacketRadioReceive::quality,
            ::PacketRadioReceive
        )

        fun handle(payload: PacketRadioReceive, context: IPayloadContext) {
            context.enqueueWork {
                handleClient(payload)
            }
        }

        private fun handleClient(packet: PacketRadioReceive) {
            val mc = Minecraft.getInstance()
            val player = mc.player ?: return

            val bars = when {
                packet.quality >= 0.8f -> "§a[||||]"
                packet.quality >= 0.6f -> "§e[|||.]"
                packet.quality >= 0.35f -> "§6[||..]"
                else -> "§c[|...]"
            }

            val chatComponent = Component.empty()
                .append(Component.literal("📻 ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(String.format("[%.1f MHz] ", packet.frequency)).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("$bars ").withStyle(ChatFormatting.RESET))
                .append(Component.literal("<${packet.senderName}> ").withStyle(ChatFormatting.AQUA))
                .append(Component.literal(packet.message).withStyle(ChatFormatting.WHITE))

            mc.gui.chat.addMessage(chatComponent)

            val level = player.level()
            val pos = player.blockPosition()

            level.playLocalSound(
                pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(),
                ModSounds.RADIO_CLICK_ON.get(), SoundSource.PLAYERS,
                0.7f, 1.0f, false
            )

            if (packet.quality < 0.7f) {
                val staticVolume = ((1.0f - packet.quality) * 0.8f).coerceIn(0.1f, 1.0f)
                RadioStaticSound.play(RECEIVE_STATIC_TICKS, staticVolume)
            }

            level.playLocalSound(
                pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(),
                ModSounds.RADIO_CLICK_OFF.get(), SoundSource.PLAYERS,
                0.6f, 1.0f, false
            )
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
