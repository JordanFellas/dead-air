package com.wasteland.deadair.registry

import com.wasteland.deadair.DeadAir
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

object ModCreativeTabs {
    val CREATIVE_TABS: DeferredRegister<CreativeModeTab> =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, DeadAir.MOD_ID)

    val DEAD_AIR_TAB: DeferredHolder<CreativeModeTab, CreativeModeTab> =
        CREATIVE_TABS.register("dead_air_tab", Supplier {
            CreativeModeTab.builder()
                .icon { ItemStack(ModItems.HANDHELD_RADIO.get()) }
                .title(Component.translatable("itemGroup.dead_air"))
                .displayItems { _, output ->
                    ModItems.ITEMS.entries.forEach { output.accept(it.get()) }
                }
                .build()
        })
}
