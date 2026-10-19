package com.wasteland.deadair.client.sound

import com.wasteland.deadair.network.packets.PacketStationDisc
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.Entity

/**
 * Plays radio station music discs on this client: in your ear for a walkie, from the block for a
 * Home Radio, and following the carrier for a boombox. One sound per source; a new track replaces it.
 */
object StationDiscPlayer {

    /** Speaker volume: a sound's range is 16 blocks per 1.0 of volume. */
    private const val SPEAKER_VOLUME = 2.0f
    private const val WALKIE_VOLUME = 0.6f

    private val playing = HashMap<Pair<Int, Long>, StationDiscSound>()

    fun handle(packet: PacketStationDisc) {
        val key = packet.sinkType to packet.sinkId
        val soundManager = Minecraft.getInstance().soundManager
        playing.remove(key)?.let { soundManager.stop(it) }
        if (packet.sound.isEmpty()) return

        val id = ResourceLocation.tryParse(packet.sound) ?: return
        val event = SoundEvent.createVariableRangeEvent(id)
        val sound = when (packet.sinkType) {
            PacketStationDisc.SINK_WALKIE -> StationDiscSound(event, WALKIE_VOLUME * packet.volume, null, null)
            PacketStationDisc.SINK_RADIO -> StationDiscSound(event, SPEAKER_VOLUME * packet.volume, BlockPos.of(packet.sinkId), null)
            PacketStationDisc.SINK_BOOMBOX -> {
                val entity = Minecraft.getInstance().level?.getEntity(packet.sinkId.toInt()) ?: return
                StationDiscSound(event, SPEAKER_VOLUME * packet.volume, null, entity)
            }
            else -> return
        }
        playing[key] = sound
        soundManager.play(sound)
    }

    fun stopAll() {
        val soundManager = Minecraft.getInstance().soundManager
        playing.values.forEach { soundManager.stop(it) }
        playing.clear()
    }
}

/** A disc track: relative to the listener (walkie), fixed at a block (radio), or following an entity (boombox). */
class StationDiscSound(
    event: SoundEvent,
    volume: Float,
    at: BlockPos?,
    private val follow: Entity?
) : AbstractTickableSoundInstance(event, SoundSource.RECORDS, SoundInstance.createUnseededRandom()) {

    init {
        this.volume = volume
        when {
            follow != null -> {
                x = follow.x
                y = follow.y + 1.0
                z = follow.z
            }
            at != null -> {
                x = at.x + 0.5
                y = at.y + 0.5
                z = at.z + 0.5
            }
            else -> {
                relative = true
                attenuation = SoundInstance.Attenuation.NONE
                x = 0.0
                y = 0.0
                z = 0.0
            }
        }
    }

    override fun tick() {
        val entity = follow ?: return
        if (entity.isRemoved) {
            stop()
            return
        }
        x = entity.x
        y = entity.y + 1.0
        z = entity.z
    }
}
