package com.wasteland.deadair.registry

import com.wasteland.deadair.DeadAir
import com.wasteland.deadair.item.BatteryCellItem
import com.wasteland.deadair.item.HandheldRadioItem
import com.wasteland.deadair.item.RadioBackpackItem
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.neoforged.neoforge.registries.DeferredBlock
import net.neoforged.neoforge.registries.DeferredItem
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

object ModItems {
    val ITEMS: DeferredRegister.Items = DeferredRegister.createItems(DeadAir.MOD_ID)

    val HANDHELD_RADIO: DeferredItem<HandheldRadioItem> = ITEMS.register("handheld_radio", Supplier {
        HandheldRadioItem(Item.Properties().stacksTo(1))
    })

    // Charge is stored per stack, so batteries don't stack (a stack would share one charge)
    val BATTERY_CELL: DeferredItem<BatteryCellItem> = ITEMS.register("battery_cell", Supplier {
        BatteryCellItem(Item.Properties().stacksTo(1), BatteryCellItem.MAX_ENERGY, 0x55FF55)
    })

    val LITHIUM_BATTERY: DeferredItem<BatteryCellItem> = ITEMS.register("lithium_battery", Supplier {
        BatteryCellItem(Item.Properties().stacksTo(1), 250000, 0x55BBFF)
    })

    val FUSION_CORE: DeferredItem<BatteryCellItem> = ITEMS.register("fusion_core", Supplier {
        BatteryCellItem(Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.RARE), 1000000, 0xCC66FF)
    })

    val RADIO_BACKPACK: DeferredItem<RadioBackpackItem> = ITEMS.register("radio_backpack", Supplier {
        RadioBackpackItem(Item.Properties().stacksTo(1))
    })

    private fun blockItem(name: String, block: DeferredBlock<out Block>): DeferredItem<BlockItem> =
        ITEMS.register(name, Supplier { BlockItem(block.get(), Item.Properties()) })

    val RADIO_TOWER_CONTROLLER = blockItem("radio_tower_controller", ModBlocks.RADIO_TOWER_CONTROLLER)
    val BROADCAST_CONTROLLER = blockItem("broadcast_controller", ModBlocks.BROADCAST_CONTROLLER)
    val TOWER_FOUNDATION = blockItem("tower_foundation", ModBlocks.TOWER_FOUNDATION)
    val RADIO_TOWER_FRAME = blockItem("radio_tower_frame", ModBlocks.RADIO_TOWER_FRAME)
    val HEAVY_TOWER_FRAME = blockItem("heavy_tower_frame", ModBlocks.HEAVY_TOWER_FRAME)
    val MAST_SECTION_LARGE = blockItem("mast_section_large", ModBlocks.MAST_SECTION_LARGE)
    val RADIO_MAST_SECTION = blockItem("radio_mast_section", ModBlocks.RADIO_MAST_SECTION)
    val MAST_SECTION_SMALL = blockItem("mast_section_small", ModBlocks.MAST_SECTION_SMALL)
    val RADIO_TOWER_LADDER = blockItem("radio_tower_ladder", ModBlocks.RADIO_TOWER_LADDER)
    val RADIO_TOWER_ANTENNA = blockItem("radio_tower_antenna", ModBlocks.RADIO_TOWER_ANTENNA)
    val TOWER_PLATFORM = blockItem("tower_platform", ModBlocks.TOWER_PLATFORM)
    val SLOPED_LATTICE_PANEL = blockItem("sloped_lattice_panel", ModBlocks.SLOPED_LATTICE_PANEL)
    val SLOPED_HEAVY_PANEL = blockItem("sloped_heavy_panel", ModBlocks.SLOPED_HEAVY_PANEL)
    val AVIATION_LIGHT = blockItem("aviation_light", ModBlocks.AVIATION_LIGHT)
    val PARABOLIC_DISH = blockItem("parabolic_dish", ModBlocks.PARABOLIC_DISH)
    val DRUM_DISH = blockItem("drum_dish", ModBlocks.DRUM_DISH)
    val YAGI_ANTENNA = blockItem("yagi_antenna", ModBlocks.YAGI_ANTENNA)
    val PANEL_ANTENNA = blockItem("panel_antenna", ModBlocks.PANEL_ANTENNA)
}
