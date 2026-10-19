package com.wasteland.deadair.registry

import com.wasteland.deadair.DeadAir
import com.wasteland.deadair.menu.RadioTowerMenu
import com.wasteland.deadair.menu.StationConsoleMenu
import net.minecraft.core.registries.Registries
import net.minecraft.world.inventory.MenuType
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

object ModMenus {
    val MENUS: DeferredRegister<MenuType<*>> = DeferredRegister.create(Registries.MENU, DeadAir.MOD_ID)

    val RADIO_TOWER_MENU: DeferredHolder<MenuType<*>, MenuType<RadioTowerMenu>> =
        MENUS.register("radio_tower_menu", Supplier {
            IMenuTypeExtension.create { windowId, inv, data -> RadioTowerMenu(windowId, inv, data) }
        })

    val STATION_CONSOLE_MENU: DeferredHolder<MenuType<*>, MenuType<StationConsoleMenu>> =
        MENUS.register("station_console", Supplier {
            IMenuTypeExtension.create { windowId, inv, data -> StationConsoleMenu(windowId, inv, data) }
        })
}
