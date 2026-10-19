package com.wasteland.deadair.menu

import com.wasteland.deadair.block.entity.TowerType
import com.wasteland.deadair.item.BatteryCellItem
import com.wasteland.deadair.registry.ModMenus
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.world.Container
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerData
import net.minecraft.world.inventory.SimpleContainerData
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack

class RadioTowerMenu(
    containerId: Int,
    playerInventory: Inventory,
    val container: Container,
    val data: ContainerData
) : AbstractContainerMenu(ModMenus.RADIO_TOWER_MENU.get(), containerId) {

    constructor(containerId: Int, playerInventory: Inventory, buf: RegistryFriendlyByteBuf) : this(
        containerId,
        playerInventory,
        (playerInventory.player.level().getBlockEntity(buf.readBlockPos()) as? Container) ?: SimpleContainer(1),
        SimpleContainerData(13)
    )

    init {
        checkContainerSize(container, 1)
        checkContainerDataCount(data, 13)

        // Battery Slot (index 0) at x: 145, y: 36
        addSlot(object : Slot(container, 0, 145, 36) {
            override fun mayPlace(stack: ItemStack): Boolean {
                return stack.item is BatteryCellItem
            }
        })

        // Player Inventory
        for (row in 0..2) {
            for (col in 0..8) {
                addSlot(Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18))
            }
        }

        // Player Hotbar
        for (col in 0..8) {
            addSlot(Slot(playerInventory, col, 8 + col * 18, 142))
        }

        addDataSlots(data)
    }

    val energy: Int get() = data.get(0)
    val maxEnergy: Int get() = data.get(1)
    val layerCount: Int get() = data.get(2)
    val hasAntenna: Boolean get() = data.get(3) == 1
    val isStructureValid: Boolean get() = data.get(4) == 1
    val isActive: Boolean get() = data.get(5) == 1
    val radius: Int get() = data.get(6)
    val linkedTowers: Int get() = data.get(7)
    val towerType: TowerType? get() = TowerType.byOrdinal(data.get(8))
    val antennaBonus: Int get() = data.get(9)
    val partCount: Int get() = data.get(10)
    val missingFoundation: Boolean get() = data.get(11) == 1
    val needsFooting: Boolean get() = data.get(12) == 1

    override fun quickMoveStack(player: Player, index: Int): ItemStack {
        var itemstack = ItemStack.EMPTY
        val slot = slots[index]
        if (slot.hasItem()) {
            val itemstack1 = slot.item
            itemstack = itemstack1.copy()
            if (index == 0) {
                if (!moveItemStackTo(itemstack1, 1, 37, true)) {
                    return ItemStack.EMPTY
                }
            } else if (itemstack1.item is BatteryCellItem) {
                if (!moveItemStackTo(itemstack1, 0, 1, false)) {
                    return ItemStack.EMPTY
                }
            } else if (index in 1..27) {
                if (!moveItemStackTo(itemstack1, 28, 37, false)) {
                    return ItemStack.EMPTY
                }
            } else if (index in 28..36 && !moveItemStackTo(itemstack1, 1, 28, false)) {
                return ItemStack.EMPTY
            }

            if (itemstack1.isEmpty) {
                slot.setByPlayer(ItemStack.EMPTY)
            } else {
                slot.setChanged()
            }

            if (itemstack1.count == itemstack.count) {
                return ItemStack.EMPTY
            }

            slot.onTake(player, itemstack1)
        }
        return itemstack
    }

    override fun stillValid(player: Player): Boolean {
        return container.stillValid(player)
    }
}
