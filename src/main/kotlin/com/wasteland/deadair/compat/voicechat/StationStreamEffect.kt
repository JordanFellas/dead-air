package com.wasteland.deadair.compat.voicechat

import de.maxhenkel.voicechat.api.VoicechatApi
import de.maxhenkel.voicechat.api.opus.OpusEncoder
import de.maxhenkel.voicechat.api.opus.OpusEncoderMode
import kotlin.math.tanh
import kotlin.random.Random

/**
 * Makes station audio (music and DJ voice) sound like AM/FM radio: trimmed lows and highs, a touch
 * of warmth, and hiss and crackle that grow as the listener's signal weakens. Same signal buckets
 * as [RadioVoiceEffect]; one Opus encoder per bucket because encoders are stateful.
 *
 * Only used from the station audio thread, except [close].
 */
class StationStreamEffect(private val api: VoicechatApi) {

    companion object {
        private val HISS = floatArrayOf(0.003f, 0.012f, 0.035f, 0.08f)
        private val CRACKLE_CHANCE = floatArrayOf(0.0f, 0.0003f, 0.0015f, 0.005f)

        // One-pole filters at 48 kHz: high-pass ~120 Hz, low-pass ~6 kHz
        private const val HP_ALPHA = 0.9845f
        private const val LP_ALPHA = 0.44f
        private const val DRIVE = 1.1f
    }

    private val encoders = arrayOfNulls<OpusEncoder>(RadioVoiceEffect.BUCKETS)
    private var hpPrevIn = 0.0f
    private var hpPrevOut = 0.0f
    private var lpPrevOut = 0.0f
    private var filtered: FloatArray? = null

    /** Filters one 20 ms frame of mixed audio (-1..1). */
    @Synchronized
    fun load(mix: FloatArray) {
        val out = FloatArray(mix.size)
        for (i in mix.indices) {
            val input = mix[i]
            val hp = HP_ALPHA * (hpPrevOut + input - hpPrevIn)
            hpPrevIn = input
            hpPrevOut = hp
            lpPrevOut += LP_ALPHA * (hp - lpPrevOut)
            out[i] = lpPrevOut
        }
        filtered = out
    }

    /** The loaded frame with this bucket's static mixed in, Opus-encoded. */
    @Synchronized
    fun render(bucket: Int): ByteArray? {
        val clean = filtered ?: return null
        val hiss = HISS[bucket]
        val crackle = CRACKLE_CHANCE[bucket]
        val pcm = ShortArray(clean.size)
        for (i in clean.indices) {
            var sample = tanh(clean[i] * DRIVE)
            sample += (Random.nextFloat() * 2.0f - 1.0f) * hiss
            if (crackle > 0.0f && Random.nextFloat() < crackle) sample += (Random.nextFloat() - 0.5f) * 1.0f
            pcm[i] = (sample.coerceIn(-1.0f, 1.0f) * 32767.0f).toInt().toShort()
        }
        val encoder = encoders[bucket] ?: api.createEncoder(OpusEncoderMode.AUDIO).also { encoders[bucket] = it }
        return try {
            encoder.encode(pcm)
        } catch (e: Exception) {
            null
        }
    }

    @Synchronized
    fun close() {
        for (i in encoders.indices) {
            encoders[i]?.close()
            encoders[i] = null
        }
        filtered = null
    }
}
