package com.wasteland.deadair.compat.voicechat

import com.wasteland.deadair.DeadAir
import com.wasteland.deadair.network.RadioNetworkManager
import com.wasteland.deadair.network.RadioVoiceRelay
import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin
import de.maxhenkel.voicechat.api.VoicechatApi
import de.maxhenkel.voicechat.api.VoicechatConnection
import de.maxhenkel.voicechat.api.VoicechatPlugin
import de.maxhenkel.voicechat.api.events.EventRegistration
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent
import net.minecraft.core.BlockPos
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * Simple Voice Chat integration: a player holding (or belt-wearing) a powered radio who speaks is
 * relayed over the radio tower network to every player carrying a radio tuned to the same
 * frequency, no matter how far away, as long as both ends are covered by the same tower network.
 * The relayed voice gets walkie-talkie filtering and static that grows as the signal weakens.
 */
@ForgeVoicechatPlugin
class DeadAirVoicePlugin : VoicechatPlugin {

    companion object {
        const val VOLUME_CATEGORY = "dead_air_radio"

        /** Same floor the text radio uses before a signal is considered lost. */
        private const val MIN_QUALITY = 0.05f
        /** Below this quality, voice starts breaking up. */
        private const val CHOPPY_QUALITY = 0.35f
        private const val MAX_DROP_CHANCE = 0.3f

        var voicechatApi: VoicechatApi? = null
    }

    /** Separate audio channel per speaker so radio audio never collides with their proximity voice. */
    private val radioChannelIds = ConcurrentHashMap<UUID, UUID>()
    private val effects = ConcurrentHashMap<UUID, RadioVoiceEffect>()

    override fun getPluginId(): String = DeadAir.MOD_ID

    override fun initialize(api: VoicechatApi) {
        voicechatApi = api
        RadioVoiceRelay.onTransmissionEnded = { sender -> effects.remove(sender)?.close() }
        DeadAir.LOGGER.info("Dead Air: Simple Voice Chat integration hooked successfully.")
    }

    override fun registerEvents(registration: EventRegistration) {
        registration.registerEvent(VoicechatServerStartedEvent::class.java, ::onServerStarted)
        registration.registerEvent(MicrophonePacketEvent::class.java, ::onMicrophone)
    }

    private fun onServerStarted(event: VoicechatServerStartedEvent) {
        val api = event.voicechat
        api.registerVolumeCategory(
            api.volumeCategoryBuilder()
                .setId(VOLUME_CATEGORY)
                .setName("Radio")
                .setDescription("Voices received over Dead Air handheld radios")
                .build()
        )
    }

    private fun onMicrophone(event: MicrophonePacketEvent) {
        val api = event.voicechat
        val senderId = event.senderConnection?.player?.uuid ?: return
        val sender = RadioVoiceRelay.getState(senderId) ?: return
        val channel = sender.transmitChannel ?: return

        val newTransmission = RadioVoiceRelay.markVoiceActivity(senderId)

        val network = RadioNetworkManager.getConnectedNetwork(sender.dimension, sender.pos)
        if (network.isEmpty()) {
            if (newTransmission) RadioVoiceRelay.queueDeadAir(senderId)
            return
        }
        RadioVoiceRelay.recordTransmittedPacket(senderId)

        // Pick who hears this frame, and how clearly.
        val proximityDistanceSq = api.voiceChatDistance * api.voiceChatDistance
        val byBucket = Array(RadioVoiceEffect.BUCKETS) { ArrayList<Pair<VoicechatConnection, UUID>>() }
        for (listener in RadioVoiceRelay.getListeners(channel)) {
            if (listener.uuid == senderId || listener.dimension != sender.dimension) continue
            // Players within normal voice range already hear the speaker directly.
            if (distanceSq(listener.pos, sender.pos) <= proximityDistanceSq) continue

            val quality = RadioNetworkManager.getSignalQuality(listener.pos, network) *
                listener.obstruction * sender.obstruction
            if (quality <= MIN_QUALITY) continue
            RadioVoiceRelay.recordListener(senderId, listener.uuid, quality)
            if (quality < CHOPPY_QUALITY && Random.nextFloat() < dropChance(quality)) continue

            val connection = api.getConnectionOf(listener.uuid) ?: continue
            if (!connection.isConnected || connection.isDisabled) continue
            byBucket[RadioVoiceEffect.bucketOf(quality)].add(connection to listener.uuid)
        }
        if (byBucket.all { it.isEmpty() }) return

        val effect = effects.computeIfAbsent(senderId) { RadioVoiceEffect(api) }
        val decoded = effect.load(event.packet.opusEncodedData)
        val channelId = radioChannelIds.computeIfAbsent(senderId, ::radioChannelFor)

        for (bucket in byBucket.indices) {
            val targets = byBucket[bucket]
            if (targets.isEmpty()) continue
            val audio = (if (decoded) effect.render(bucket) else null) ?: event.packet.opusEncodedData
            val packet = event.packet.staticSoundPacketBuilder()
                .channelId(channelId)
                .opusEncodedData(audio)
                .category(VOLUME_CATEGORY)
                .build()
            for ((connection, _) in targets) api.sendStaticSoundPacketTo(connection, packet)
        }
    }

    /** Very weak signals drop some voice frames, like the text radio's garbling. */
    private fun dropChance(quality: Float): Float =
        ((CHOPPY_QUALITY - quality) / CHOPPY_QUALITY * MAX_DROP_CHANCE).coerceIn(0.0f, MAX_DROP_CHANCE)

    private fun radioChannelFor(playerId: UUID): UUID =
        UUID.nameUUIDFromBytes("${DeadAir.MOD_ID}:radio:$playerId".toByteArray())

    private fun distanceSq(a: BlockPos, b: BlockPos): Double {
        val dx = (a.x - b.x).toDouble()
        val dy = (a.y - b.y).toDouble()
        val dz = (a.z - b.z).toDouble()
        return dx * dx + dy * dy + dz * dz
    }
}
