package com.wasteland.deadair.client.sound

import com.wasteland.deadair.registry.ModSounds
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.sounds.SoundSource

/**
 * Radio static that plays from the listener's own radio. It is relative to the listener so it
 * moves with the player. A burst is cut off (with a short fade) after [durationTicks]; a "bed"
 * loops under incoming voice until [fadeOut] is called.
 */
class RadioStaticSound(
    private val durationTicks: Int,
    private val baseVolume: Float,
    loop: Boolean = false
) : AbstractTickableSoundInstance(ModSounds.RADIO_STATIC.get(), SoundSource.PLAYERS, SoundInstance.createUnseededRandom()) {

    companion object {
        private const val FADE_TICKS = 6

        private var currentBurst: RadioStaticSound? = null
        private var currentBed: RadioStaticSound? = null

        /** Plays a static burst, replacing any burst that is still playing so they never stack. */
        fun play(durationTicks: Int, volume: Float) {
            val soundManager = Minecraft.getInstance().soundManager
            currentBurst?.let { soundManager.stop(it) }
            val sound = RadioStaticSound(durationTicks, volume.coerceIn(0.0f, 1.0f))
            currentBurst = sound
            soundManager.play(sound)
        }

        /** Starts (or re-levels) the hiss under an incoming voice transmission. */
        fun startBed(volume: Float) {
            val soundManager = Minecraft.getInstance().soundManager
            currentBed?.let { soundManager.stop(it) }
            val sound = RadioStaticSound(Int.MAX_VALUE, volume.coerceIn(0.0f, 1.0f), loop = true)
            currentBed = sound
            soundManager.play(sound)
        }

        fun stopBed() {
            currentBed?.fadeOut()
            currentBed = null
        }
    }

    private var age = 0
    private var stopAt = durationTicks

    init {
        volume = baseVolume
        looping = loop
        relative = true
        attenuation = SoundInstance.Attenuation.NONE
        x = 0.0
        y = 0.0
        z = 0.0
    }

    fun fadeOut() {
        stopAt = minOf(stopAt, age + FADE_TICKS)
    }

    override fun tick() {
        age++
        val remaining = stopAt - age
        if (remaining <= 0) {
            volume = 0.0f
            stop()
            return
        }
        if (remaining < FADE_TICKS) {
            volume = baseVolume * remaining / FADE_TICKS
        }
    }
}
