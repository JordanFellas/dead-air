package com.wasteland.deadair.registry

import com.wasteland.deadair.DeadAir
import com.wasteland.deadair.block.AviationLightBlock
import com.wasteland.deadair.block.BroadcastControllerBlock
import com.wasteland.deadair.block.DirectionalAntennaBlock
import com.wasteland.deadair.block.HomeRadioBlock
import com.wasteland.deadair.block.StationConsoleBlock
import com.wasteland.deadair.block.StudioMicBlock
import com.wasteland.deadair.block.RadioMastSectionBlock
import com.wasteland.deadair.block.RadioTowerAntennaBlock
import com.wasteland.deadair.block.RadioTowerControllerBlock
import com.wasteland.deadair.block.RadioTowerFrameBlock
import com.wasteland.deadair.block.RadioTowerLadderBlock
import com.wasteland.deadair.block.TowerDecorBlock
import com.wasteland.deadair.block.TowerPartBlock
import com.wasteland.deadair.block.TowerPlatformBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.material.MapColor
import net.neoforged.neoforge.registries.DeferredBlock
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

object ModBlocks {
    val BLOCKS: DeferredRegister.Blocks = DeferredRegister.createBlocks(DeadAir.MOD_ID)

    private fun metal(strength: Float, sound: SoundType): BlockBehaviour.Properties =
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(strength, strength * 2)
            .sound(sound)
            .requiresCorrectToolForDrops()
            .noOcclusion()

    val RADIO_TOWER_CONTROLLER: DeferredBlock<RadioTowerControllerBlock> =
        BLOCKS.register("radio_tower_controller", Supplier {
            RadioTowerControllerBlock(
                BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5f, 6.0f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
            )
        })

    val RADIO_TOWER_FRAME: DeferredBlock<RadioTowerFrameBlock> =
        BLOCKS.register("radio_tower_frame", Supplier {
            RadioTowerFrameBlock(
                BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0f, 6.0f)
                    .sound(SoundType.CHAIN)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
            )
        })

    /** 5x5 Mega Tower lattice. */
    val HEAVY_TOWER_FRAME: DeferredBlock<RadioTowerFrameBlock> =
        BLOCKS.register("heavy_tower_frame", Supplier { RadioTowerFrameBlock(metal(4.0f, SoundType.NETHERITE_BLOCK)) })

    /** Mega Tower controller: sits in the middle of a 5x5 Tower Foundation pad. */
    val BROADCAST_CONTROLLER: DeferredBlock<BroadcastControllerBlock> =
        BLOCKS.register("broadcast_controller", Supplier {
            BroadcastControllerBlock(
                BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(5.0f, 12.0f)
                    .sound(SoundType.NETHERITE_BLOCK)
                    .requiresCorrectToolForDrops()
            )
        })

    val TOWER_FOUNDATION: DeferredBlock<TowerPartBlock> =
        BLOCKS.register("tower_foundation", Supplier {
            TowerPartBlock(
                BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(4.0f, 9.0f)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops()
            )
        })

    /** Guyed Mast poles in three sizes; a bigger section adds more range. */
    val MAST_SECTION_LARGE: DeferredBlock<RadioMastSectionBlock> =
        BLOCKS.register("mast_section_large", Supplier {
            RadioMastSectionBlock(metal(2.5f, SoundType.CHAIN), RadioMastSectionBlock.Size.LARGE)
        })

    val RADIO_MAST_SECTION: DeferredBlock<RadioMastSectionBlock> =
        BLOCKS.register("radio_mast_section", Supplier {
            RadioMastSectionBlock(metal(2.0f, SoundType.CHAIN), RadioMastSectionBlock.Size.MEDIUM)
        })

    val MAST_SECTION_SMALL: DeferredBlock<RadioMastSectionBlock> =
        BLOCKS.register("mast_section_small", Supplier {
            RadioMastSectionBlock(metal(1.5f, SoundType.CHAIN), RadioMastSectionBlock.Size.SMALL)
        })

    val RADIO_TOWER_LADDER: DeferredBlock<RadioTowerLadderBlock> =
        BLOCKS.register("radio_tower_ladder", Supplier {
            RadioTowerLadderBlock(
                BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 4.0f)
                    .sound(SoundType.LADDER)
                    .noOcclusion()
            )
        })

    val RADIO_TOWER_ANTENNA: DeferredBlock<RadioTowerAntennaBlock> =
        BLOCKS.register("radio_tower_antenna", Supplier {
            RadioTowerAntennaBlock(metal(2.5f, SoundType.COPPER).lightLevel { state ->
                if (state.getValue(RadioTowerAntennaBlock.PART) == RadioTowerAntennaBlock.PARTS - 1) 7 else 0
            })
        })

    val PARABOLIC_DISH: DeferredBlock<DirectionalAntennaBlock> =
        BLOCKS.register("parabolic_dish", Supplier {
            DirectionalAntennaBlock(metal(2.5f, SoundType.COPPER), 300, 120, Block.box(1.0, 0.0, 1.0, 15.0, 16.0, 15.0))
        })

    val YAGI_ANTENNA: DeferredBlock<DirectionalAntennaBlock> =
        BLOCKS.register("yagi_antenna", Supplier {
            DirectionalAntennaBlock(metal(2.0f, SoundType.CHAIN), 200, 80, Block.box(2.0, 4.0, 2.0, 14.0, 12.0, 14.0))
        })

    /** Grated gallery deck with automatic railings. */
    val TOWER_PLATFORM: DeferredBlock<TowerPlatformBlock> =
        BLOCKS.register("tower_platform", Supplier { TowerPlatformBlock(metal(2.5f, SoundType.CHAIN)) })

    /** Lattice panels that lean in as they rise, for tapering, angled tower sides. */
    val SLOPED_LATTICE_PANEL: DeferredBlock<TowerDecorBlock> =
        BLOCKS.register("sloped_lattice_panel", Supplier { TowerDecorBlock(metal(2.5f, SoundType.CHAIN), Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 16.0)) })

    val SLOPED_HEAVY_PANEL: DeferredBlock<TowerDecorBlock> =
        BLOCKS.register("sloped_heavy_panel", Supplier { TowerDecorBlock(metal(3.0f, SoundType.NETHERITE_BLOCK), Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 16.0)) })

    /** Red warning light for tower tops and corners. */
    val AVIATION_LIGHT: DeferredBlock<AviationLightBlock> =
        BLOCKS.register("aviation_light", Supplier { AviationLightBlock(metal(1.0f, SoundType.GLASS).lightLevel { 13 }) })

    /** Flat cellular panel antenna (side mount). */
    val PANEL_ANTENNA: DeferredBlock<DirectionalAntennaBlock> =
        BLOCKS.register("panel_antenna", Supplier {
            DirectionalAntennaBlock(metal(2.0f, SoundType.METAL), 40, 60, Block.box(3.0, 1.0, 3.0, 13.0, 15.0, 13.0))
        })

    /** White radome "drum" microwave dish, like the clusters on big broadcast towers. */
    val DRUM_DISH: DeferredBlock<DirectionalAntennaBlock> =
        BLOCKS.register("drum_dish", Supplier {
            DirectionalAntennaBlock(metal(2.5f, SoundType.METAL), 150, 100, Block.box(2.0, 1.0, 2.0, 14.0, 15.0, 14.0))
        })

    // ---- Radio stations ----

    val STATION_CONSOLE: DeferredBlock<StationConsoleBlock> =
        BLOCKS.register("station_console", Supplier {
            StationConsoleBlock(metal(3.0f, SoundType.METAL).lightLevel { if (it.getValue(StationConsoleBlock.LIVE)) 6 else 0 })
        })

    val STUDIO_MIC: DeferredBlock<StudioMicBlock> =
        BLOCKS.register("studio_mic", Supplier { StudioMicBlock(metal(1.0f, SoundType.LANTERN)) })

    val HOME_RADIO: DeferredBlock<HomeRadioBlock> =
        BLOCKS.register("home_radio", Supplier {
            HomeRadioBlock(
                BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(1.0f)
                    .sound(SoundType.WOOD)
                    .noOcclusion()
                    .lightLevel { if (it.getValue(HomeRadioBlock.ON)) 4 else 0 }
            )
        })
}