package com.wasteland.deadair.network.packets

import com.wasteland.deadair.DeadAir
import com.wasteland.deadair.DeadAirConfig
import com.wasteland.deadair.block.entity.StationConsoleBlockEntity
import com.wasteland.deadair.station.StreamUrls
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.network.handling.IPayloadContext

/** Client -> server: Station Console settings changed (and optionally skip the current track). */
data class PacketStationSettings(
    val pos: BlockPos,
    val callSign: String,
    val frequency: Float,
    val flags: Int,
    val announceEvery: Int,
    val skip: Boolean,
    /** New stream URL, or null to leave it unchanged. */
    val streamUrl: String?
) : CustomPacketPayload {

    companion object {
        val TYPE = CustomPacketPayload.Type<PacketStationSettings>(
            ResourceLocation.fromNamespaceAndPath(DeadAir.MOD_ID, "station_settings")
        )

        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, PacketStationSettings> = StreamCodec.of(
            { buf, p ->
                buf.writeBlockPos(p.pos)
                buf.writeUtf(p.callSign, 64)
                buf.writeFloat(p.frequency)
                buf.writeVarInt(p.flags)
                buf.writeVarInt(p.announceEvery)
                buf.writeBoolean(p.skip)
                buf.writeBoolean(p.streamUrl != null)
                p.streamUrl?.let { buf.writeUtf(it, StreamUrls.MAX_LENGTH * 4) }
            },
            { buf ->
                PacketStationSettings(
                    buf.readBlockPos(),
                    buf.readUtf(64),
                    buf.readFloat(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readBoolean(),
                    if (buf.readBoolean()) buf.readUtf(StreamUrls.MAX_LENGTH * 4) else null
                )
            }
        )

        fun handle(payload: PacketStationSettings, context: IPayloadContext) {
            context.enqueueWork {
                val player = context.player() as? ServerPlayer ?: return@enqueueWork
                if (player.distanceToSqr(Vec3.atCenterOf(payload.pos)) > 64.0) return@enqueueWork
                val console = player.level().getBlockEntity(payload.pos) as? StationConsoleBlockEntity ?: return@enqueueWork
                val url = payload.streamUrl?.trim()?.takeIf { it != console.streamUrl }?.let { checkUrl(player, it) }
                console.applySettings(payload.callSign, payload.frequency, payload.flags, payload.announceEvery, url)
                if (payload.skip) console.skip()
            }
        }

        /** The URL if this player may set it, otherwise null (and tells them why). */
        private fun checkUrl(player: ServerPlayer, url: String): String? {
            val problem = when {
                url.isEmpty() -> null
                !DeadAirConfig.STREAM_URLS_ENABLED.get() -> "Stream URLs are turned off on this server"
                DeadAirConfig.STREAM_URLS_OPS_ONLY.get() && !player.hasPermissions(2) -> "Only operators can set stream URLs here"
                else -> StreamUrls.problemWith(url)
            }
            if (problem != null) {
                player.displayClientMessage(Component.literal(problem).withStyle(ChatFormatting.RED), true)
                return null
            }
            return url
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
