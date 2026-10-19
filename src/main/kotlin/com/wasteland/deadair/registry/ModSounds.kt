package com.wasteland.deadair.registry

import com.wasteland.deadair.DeadAir
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

object ModSounds {
    val SOUNDS: DeferredRegister<SoundEvent> = DeferredRegister.create(Registries.SOUND_EVENT, DeadAir.MOD_ID)

    val RADIO_CLICK_ON: DeferredHolder<SoundEvent, SoundEvent> = register("radio_click_on")
    val RADIO_CLICK_OFF: DeferredHolder<SoundEvent, SoundEvent> = register("radio_click_off")
    val RADIO_STATIC: DeferredHolder<SoundEvent, SoundEvent> = register("radio_static")
    val RADIO_BEEP: DeferredHolder<SoundEvent, SoundEvent> = register("radio_beep")

    private fun register(name: String): DeferredHolder<SoundEvent, SoundEvent> {
        val id = ResourceLocation.fromNamespaceAndPath(DeadAir.MOD_ID, name)
        return SOUNDS.register(name, Supplier { SoundEvent.createVariableRangeEvent(id) })
    }
}
