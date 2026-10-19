package com.wasteland.deadair.compat.voicechat

import de.maxhenkel.voicechat.api.VoicechatApi
import de.maxhenkel.voicechat.api.opus.OpusDecoder
import de.maxhenkel.voicechat.api.opus.OpusEncoder
import de.maxhenkel.voicechat.api.opus.OpusEncoderMode
import kotlin.math.tanh
import kotlin.random.Random

/**
 * Makes one speaker's voice sound like it came through a walkie-talkie: band-limited, a little
 * overdriven, with hiss and crackle that grow as the signal weakens.
 *
 * Opus codecs are stateful, so each speaker gets one decoder and one encoder per signal bucket.
 * Calls are synchronized because transmissions end (and [close] runs) on the server thread.
 */
class RadioVoiceEffect(private val api: VoicechatApi) {

    companion object {
        const val BUCKETS = 4

        /** 0 = strong signal ... 3 = barely there. */
        fun bucketOf(quality: Float): Int = when {
            quality >= 0.85f -> 0
            quality >= 0.6f -> 1
            quality >= 0.35f -> 2
            else -> 3
        }

        private val HISS = floatArrayOf(0.012f, 0.035f, 0.08f, 0.15f)
        private val CRACKLE_CHANCE = floatArrayOf(0.0f, 0.0005f, 0.002f, 0.006f)

        // One-pole filters at 48 kHz: high-pass ~300 Hz, low-pass ~3 kHz
        private const val HP_ALPHA = 0.962f
        private const val LP_ALPHA = 0.282f
        private const val DRIVE = 1.4f
    }

    private var decoder: OpusDecoder? = null
    private val encoders = arrayOfNulls<OpusEncoder>(BUCKETS)
    private var hpPrevIn = 0.0f
    private var hpPrevOut = 0.0f
    private var lpPrevOut = 0.0f
    private var filtered: FloatArray? = null

    /** Decodes and filters one microphone frame; returns false if it couldn't be decoded. */
    @Synchronized
    fun load(opus: ByteArray): Boolean {
        val dec = decoder ?: api.createDecoder().also { decoder = it }
        val pcm = try {
            dec.decode(opus)
        } catch (e: Exception) {
            null
        }
        if (pcm == null || pcm.isEmpty()) {
            filtered = null
            return false
        }
        val out = FloatArray(pcm.size)
        for (i in pcm.indices) {
            val input = pcm[i] / 32768.0f
            val hp = HP_ALPHA * (hpPrevOut + input - hpPrevIn)
            hpPrevIn = input
            hpPrevOut = hp
            lpPrevOut += LP_ALPHA * (hp - lpPrevOut)
            out[i] = lpPrevOut
        }
        filtered = out
        return true
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
            if (crackle > 0.0f && Random.nextFloat() < crackle) sample += (Random.nextFloat() - 0.5f) * 1.2f
            pcm[i] = (sample.coerceIn(-1.0f, 1.0f) * 32767.0f).toInt().toShort()
        }
        val encoder = encoders[bucket] ?: api.createEncoder(OpusEncoderMode.VOIP).also { encoders[bucket] = it }
        return try {
            encoder.encode(pcm)
        } catch (e: Exception) {
            null
        }
    }

    @Synchronized
    fun close() {
        decoder?.close()
        decoder = null
        for (i in encoders.indices) {
            encoders[i]?.close()
            encoders[i] = null
        }
        filtered = null
        hpPrevIn = 0.0f
        hpPrevOut = 0.0f
        lpPrevOut = 0.0f
    }
}
