package com.wasteland.deadair.registry

import com.wasteland.deadair.DeadAir
import com.wasteland.deadair.block.entity.HomeRadioBlockEntity
import com.wasteland.deadair.block.entity.RadioTowerControllerBlockEntity
import com.wasteland.deadair.block.entity.StationConsoleBlockEntity
import net.minecraft.core.registries.Registries
import net.minecraft.world.level.block.entity.BlockEntityType
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

object ModBlockEntities {
    val BLOCK_ENTITIES: DeferredRegister<BlockEntityType<*>> =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, DeadAir.MOD_ID)

    val RADIO_TOWER_CONTROLLER_BE: DeferredHolder<BlockEntityType<*>, BlockEntityType<RadioTowerControllerBlockEntity>> =
        BLOCK_ENTITIES.register("radio_tower_controller", Supplier {
            BlockEntityType.Builder.of(
                ::RadioTowerControllerBlockEntity,
                ModBlocks.RADIO_TOWER_CONTROLLER.get(),
                ModBlocks.BROADCAST_CONTROLLER.get()
            ).build(null)
        })

    val STATION_CONSOLE_BE: DeferredHolder<BlockEntityType<*>, BlockEntityType<StationConsoleBlockEntity>> =
        BLOCK_ENTITIES.register("station_console", Supplier {
            BlockEntityType.Builder.of(::StationConsoleBlockEntity, ModBlocks.STATION_CONSOLE.get()).build(null)
        })

    val HOME_RADIO_BE: DeferredHolder<BlockEntityType<*>, BlockEntityType<HomeRadioBlockEntity>> =
        BLOCK_ENTITIES.register("home_radio", Supplier {
            BlockEntityType.Builder.of(::HomeRadioBlockEntity, ModBlocks.HOME_RADIO.get()).build(null)
        })
}
