package com.wasteland.deadair.client.sound

import com.wasteland.deadair.registry.ModSounds
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource

/** Walkie-talkie sounds heard from your own radio (they follow the player). */
object RadioSounds {

    /** Incoming voice: squelch click, then hiss that gets louder as the signal weakens. */
    fun openSquelch(quality: Float) {
        playLocal(ModSounds.RADIO_CLICK_ON.get(), 0.6f, 1.0f)
        RadioStaticSound.startBed(0.05f + (1.0f - quality.coerceIn(0.0f, 1.0f)) * 0.45f)
    }

    /** Incoming voice ended: the classic roger beep, then the squelch closes. */
    fun closeSquelch() {
        RadioStaticSound.stopBed()
        playLocal(ModSounds.RADIO_BEEP.get(), 0.55f, 1.25f)
        playLocal(ModSounds.RADIO_CLICK_OFF.get(), 0.5f, 1.0f)
    }

    fun playLocal(event: SoundEvent, volume: Float, pitch: Float) {
        Minecraft.getInstance().soundManager.play(
            SimpleSoundInstance(
                event.location, SoundSource.PLAYERS, volume, pitch, SoundInstance.createUnseededRandom(),
                false, 0, SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true
            )
        )
    }
}
