package com.wasteland.deadair.network.packets

import com.wasteland.deadair.DeadAir
import com.wasteland.deadair.item.HandheldRadioItem
import com.wasteland.deadair.item.RadioLocator
import com.wasteland.deadair.network.RadioNetworkManager
import com.wasteland.deadair.network.RadioVoiceRelay
import com.wasteland.deadair.registry.ModSounds
import net.minecraft.ChatFormatting
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundSource
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.handling.IPayloadContext
import kotlin.math.abs

data class PacketRadioTransmit(
    val message: String,
    /** Inventory slot of the radio, or [RadioLocator.SLOT_BELT]. */
    val slot: Int
) : CustomPacketPayload {

    companion object {
        val TYPE = CustomPacketPayload.Type<PacketRadioTransmit>(
            ResourceLocation.fromNamespaceAndPath(DeadAir.MOD_ID, "radio_transmit")
        )

        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, PacketRadioTransmit> = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, PacketRadioTransmit::message,
            ByteBufCodecs.VAR_INT, PacketRadioTransmit::slot,
            ::PacketRadioTransmit
        )

        fun handle(payload: PacketRadioTransmit, context: IPayloadContext) {
            context.enqueueWork {
                val player = context.player() as? ServerPlayer ?: return@enqueueWork
                handleTransmission(player, payload.message.take(128), payload.slot)
            }
        }

        private fun handleTransmission(sender: ServerPlayer, message: String, slot: Int) {
            val radioStack = RadioLocator.getRadio(sender, slot)
            if (radioStack.isEmpty) return

            if (!HandheldRadioItem.isRadioOn(radioStack)) {
                sender.sendSystemMessage(
                    Component.literal("[Radio] Walkie-Talkie is powered OFF.")
                        .withStyle(ChatFormatting.RED)
                )
                return
            }

            if (HandheldRadioItem.getEnergy(radioStack) < HandheldRadioItem.TEXT_MESSAGE_COST) {
                sender.sendSystemMessage(
                    Component.literal("[Radio] Battery depleted. Swap in a Scrap Battery Cell.")
                        .withStyle(ChatFormatting.RED)
                )
                sender.playNotifySound(ModSounds.RADIO_BEEP.get(), SoundSource.PLAYERS, 0.8f, 0.7f)
                return
            }

            val dim = sender.serverLevel().dimension().location()
            val senderPos = sender.blockPosition()

            val directTowers = RadioNetworkManager.getDirectCoveringTowers(dim, senderPos)
            if (directTowers.isEmpty()) {
                sender.sendSystemMessage(
                    Component.literal("[Radio] Dead Air... No active radio tower in range.")
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC)
                )
                PacketDistributor.sendToPlayer(sender, PacketRadioStatic(PacketRadioStatic.DEAD_AIR_TICKS, 0.5f))
                return
            }

            HandheldRadioItem.consumeEnergy(radioStack, HandheldRadioItem.TEXT_MESSAGE_COST)

            val frequency = HandheldRadioItem.getFrequency(radioStack)
            val network = RadioNetworkManager.getConnectedNetwork(dim, senderPos)
            val senderObstruction = RadioVoiceRelay.measureObstruction(sender)

            for (target in sender.server.playerList.players) {
                if (target.serverLevel().dimension().location() != dim) continue
                val tuned = RadioLocator.allPoweredRadios(target).any {
                    abs(HandheldRadioItem.getFrequency(it) - frequency) < 0.05f
                }
                if (!tuned) continue

                val quality = RadioNetworkManager.getSignalQuality(target.blockPosition(), network) *
                    senderObstruction * RadioVoiceRelay.measureObstruction(target)
                if (quality > 0.05f) {
                    val degraded = RadioNetworkManager.degradeMessage(message, quality)
                    PacketDistributor.sendToPlayer(
                        target,
                        PacketRadioReceive(sender.name.string, frequency, degraded, quality)
                    )
                }
            }

            sender.playNotifySound(ModSounds.RADIO_CLICK_ON.get(), SoundSource.PLAYERS, 0.6f, 1.0f)
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
