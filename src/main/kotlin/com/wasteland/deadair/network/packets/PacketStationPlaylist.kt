package com.wasteland.deadair.network.packets

import com.wasteland.deadair.DeadAir
import com.wasteland.deadair.block.entity.StationConsoleBlockEntity.PlaylistEntry
import com.wasteland.deadair.client.ClientRadioState
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext

/** Server -> client: the playlist of the Station Console the player has open, for its MUSIC tab. */
data class PacketStationPlaylist(
    val entries: List<PlaylistEntry>,
    val currentId: String
) : CustomPacketPayload {

    companion object {
        private const val MAX_ENTRIES = 500

        val TYPE = CustomPacketPayload.Type<PacketStationPlaylist>(
            ResourceLocation.fromNamespaceAndPath(DeadAir.MOD_ID, "station_playlist")
        )

        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, PacketStationPlaylist> = StreamCodec.of(
            { buf, p ->
                val entries = p.entries.take(MAX_ENTRIES)
                buf.writeVarInt(entries.size)
                for (entry in entries) {
                    buf.writeUtf(entry.id, 512)
                    buf.writeUtf(entry.title, 512)
                    buf.writeBoolean(entry.removable)
                }
                buf.writeUtf(p.currentId, 512)
            },
            { buf ->
                val count = buf.readVarInt().coerceIn(0, MAX_ENTRIES)
                val entries = List(count) { PlaylistEntry(buf.readUtf(512), buf.readUtf(512), buf.readBoolean()) }
                PacketStationPlaylist(entries, buf.readUtf(512))
            }
        )

        fun handle(payload: PacketStationPlaylist, context: IPayloadContext) {
            context.enqueueWork { ClientRadioState.updatePlaylist(payload.entries, payload.currentId) }
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
