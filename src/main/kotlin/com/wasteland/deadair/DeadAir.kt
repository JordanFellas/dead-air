package com.wasteland.deadair

import com.wasteland.deadair.client.ClientSetup
import com.wasteland.deadair.network.RadioNetworkManager
import com.wasteland.deadair.network.RadioVoiceRelay
import com.wasteland.deadair.registry.*
import com.wasteland.deadair.station.StationManager
import net.minecraft.world.item.CreativeModeTabs
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModLoadingContext
import net.neoforged.fml.common.Mod
import net.neoforged.fml.config.ModConfig
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent
import net.neoforged.neoforge.event.server.ServerStartingEvent
import net.neoforged.neoforge.event.server.ServerStoppedEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

@Mod(DeadAir.MOD_ID)
class DeadAir(modBus: IEventBus) {

    companion object {
        const val MOD_ID = "dead_air"
        val LOGGER: Logger = LogManager.getLogger(MOD_ID)
    }

    init {
        LOGGER.info("Initializing Dead Air: Post-Apocalyptic Radio Mod for NeoForge 1.21.1...")

        // Register Deferred Registers to Mod Event Bus
        ModBlocks.BLOCKS.register(modBus)
        ModItems.ITEMS.register(modBus)
        ModBlockEntities.BLOCK_ENTITIES.register(modBus)
        ModMenus.MENUS.register(modBus)
        ModSounds.SOUNDS.register(modBus)
        ModCreativeTabs.CREATIVE_TABS.register(modBus)
        ModDataComponents.DATA_COMPONENTS.register(modBus)

        ModLoadingContext.get().activeContainer.registerConfig(ModConfig.Type.COMMON, DeadAirConfig.SPEC)

        // Mod bus lifecycle listeners
        modBus.addListener(::onBuildCreativeTabs)
        modBus.addListener(::registerCapabilities)

        // NeoForge game event bus listeners
        val forgeBus = NeoForge.EVENT_BUS
        forgeBus.addListener(::onServerStarting)
        forgeBus.addListener(::onServerStopped)
        forgeBus.addListener(::onServerTick)

        // Client-side initialization
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modBus.addListener(ClientSetup::registerScreens)
            modBus.addListener(ClientSetup::registerKeyMappings)
            modBus.addListener(ClientSetup::addRenderLayers)
            modBus.addListener(ClientSetup::registerClientExtensions)
            forgeBus.addListener(ClientSetup::onClientTick)
            forgeBus.addListener(ClientSetup::onLoggingOut)
        }
    }

    private fun registerCapabilities(event: RegisterCapabilitiesEvent) {
        event.registerBlockEntity(
            Capabilities.EnergyStorage.BLOCK,
            ModBlockEntities.RADIO_TOWER_CONTROLLER_BE.get()
        ) { be, _ -> be.energyStorage }
    }

    private fun onBuildCreativeTabs(event: BuildCreativeModeTabContentsEvent) {
        if (event.tabKey == CreativeModeTabs.TOOLS_AND_UTILITIES || event.tabKey == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            ModItems.ITEMS.entries.forEach { event.accept(it.get()) }
        }
    }

    private fun onServerStarting(event: ServerStartingEvent) {
        RadioNetworkManager.init(event.server)
        LOGGER.info("Dead Air Radio Network Manager initialized on NeoForge 1.21.1.")
    }

    private fun onServerStopped(event: ServerStoppedEvent) {
        RadioNetworkManager.cleanup()
        RadioVoiceRelay.clear()
        StationManager.clear()
        LOGGER.info("Dead Air Radio Network Manager cleaned up.")
    }

    private fun onServerTick(event: ServerTickEvent.Post) {
        RadioNetworkManager.tick()
        RadioVoiceRelay.tick(event.server)
        StationManager.tick(event.server)
    }
}
