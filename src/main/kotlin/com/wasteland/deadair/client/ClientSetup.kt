package com.wasteland.deadair.client

import com.wasteland.deadair.client.gui.RadioScreen
import com.wasteland.deadair.client.gui.RadioTowerScreen
import com.wasteland.deadair.client.gui.StationConsoleScreen
import com.wasteland.deadair.client.sound.StationDiscPlayer
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import com.wasteland.deadair.client.render.RadioBackpackLayer
import com.wasteland.deadair.client.render.WalkieClientExtensions
import com.wasteland.deadair.registry.ModItems
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent
import com.wasteland.deadair.item.RadioLocator
import com.wasteland.deadair.registry.ModMenus
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.player.PlayerRenderer
import net.minecraft.world.entity.player.Player
import net.neoforged.neoforge.client.event.EntityRenderersEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent

object ClientSetup {

    fun registerScreens(event: RegisterMenuScreensEvent) {
        event.register(ModMenus.RADIO_TOWER_MENU.get(), ::RadioTowerScreen)
        event.register(ModMenus.STATION_CONSOLE_MENU.get(), ::StationConsoleScreen)
    }

    fun onLoggingOut(event: ClientPlayerNetworkEvent.LoggingOut) {
        StationDiscPlayer.stopAll()
        ClientRadioState.clear()
    }

    fun registerKeyMappings(event: RegisterKeyMappingsEvent) {
        event.register(KeyBindings.KEY_RADIO_CHAT)
    }

    fun registerClientExtensions(event: RegisterClientExtensionsEvent) {
        event.registerItem(WalkieClientExtensions, ModItems.HANDHELD_RADIO.get())
    }

    fun addRenderLayers(event: EntityRenderersEvent.AddLayers) {
        for (skin in event.skins) {
            val renderer = event.getSkin<EntityRenderer<out Player>>(skin)
            if (renderer is PlayerRenderer) renderer.addLayer(RadioBackpackLayer(renderer))
        }
    }

    fun onClientTick(event: ClientTickEvent.Post) {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        ClientSongUploader.tick()

        while (KeyBindings.KEY_RADIO_CHAT.consumeClick()) {
            if (mc.screen != null) continue
            // Opens the held radio, then a belt radio, then any radio in the inventory.
            val slot = RadioLocator.findPreferredSlot(player) ?: continue
            mc.setScreen(RadioScreen(slot))
        }
    }
}
