package com.wasteland.deadair.network.packets

import com.wasteland.deadair.DeadAir
import com.wasteland.deadair.station.SongImports
import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.handling.IPayloadContext

/**
 * Client -> server: a song file from the player's own computer, sent in pieces.
 * START names the file and its size, CHUNK carries the bytes in order, END asks the server to keep it.
 */
data class PacketSongUpload(
    val stage: Int,
    val pos: BlockPos = BlockPos.ZERO,
    val fileName: String = "",
    val size: Long = 0,
    val offset: Int = 0,
    val bytes: ByteArray = ByteArray(0)
) : CustomPacketPayload {

    companion object {
        const val START = 0
        const val CHUNK = 1
        const val END = 2

        val TYPE = CustomPacketPayload.Type<PacketSongUpload>(
            ResourceLocation.fromNamespaceAndPath(DeadAir.MOD_ID, "song_upload")
        )

        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, PacketSongUpload> = StreamCodec.of(
            { buf, p ->
                buf.writeVarInt(p.stage)
                when (p.stage) {
                    START -> {
                        buf.writeBlockPos(p.pos)
                        buf.writeUtf(p.fileName, 256)
                        buf.writeVarLong(p.size)
                    }
                    CHUNK -> {
                        buf.writeVarInt(p.offset)
                        buf.writeByteArray(p.bytes)
                    }
                }
            },
            { buf ->
                when (val stage = buf.readVarInt()) {
                    START -> PacketSongUpload(START, buf.readBlockPos(), buf.readUtf(256), buf.readVarLong())
                    CHUNK -> PacketSongUpload(CHUNK, offset = buf.readVarInt(), bytes = buf.readByteArray(SongImports.CHUNK_SIZE))
                    else -> PacketSongUpload(stage)
                }
            }
        )

        fun handle(payload: PacketSongUpload, context: IPayloadContext) {
            context.enqueueWork {
                val player = context.player() as? ServerPlayer ?: return@enqueueWork
                when (payload.stage) {
                    START -> SongImports.startUpload(player, payload.pos, payload.fileName, payload.size)
                    CHUNK -> SongImports.receiveChunk(player, payload.offset, payload.bytes)
                    END -> SongImports.finishUpload(player)
                }
            }
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    // ByteArray content isn't compared by data classes; uploads are never compared anyway.
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}
