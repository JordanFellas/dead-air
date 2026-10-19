package com.wasteland.deadair.menu

import com.wasteland.deadair.block.entity.StationConsoleBlockEntity
import com.wasteland.deadair.registry.ModMenus
import com.wasteland.deadair.station.StreamUrls
import net.minecraft.core.BlockPos
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

class StationConsoleMenu(
    containerId: Int,
    playerInventory: Inventory,
    val container: Container,
    val data: ContainerData,
    val pos: BlockPos,
    /** Call sign and stream URL when the menu opened; the screen edits its own copies. */
    val initialCallSign: String,
    val initialStreamUrl: String
) : AbstractContainerMenu(ModMenus.STATION_CONSOLE_MENU.get(), containerId) {

    companion object {
        const val DISC_ROW_Y = 142
        const val INVENTORY_Y = 172
    }

    constructor(containerId: Int, playerInventory: Inventory, buf: RegistryFriendlyByteBuf) : this(
        containerId,
        playerInventory,
        SimpleContainer(StationConsoleBlockEntity.DISC_SLOTS),
        SimpleContainerData(StationConsoleBlockEntity.DATA_COUNT),
        buf.readBlockPos(),
        buf.readUtf(StationConsoleBlockEntity.MAX_CALL_SIGN * 4),
        buf.readUtf(StreamUrls.MAX_LENGTH * 4)
    )

    init {
        checkContainerSize(container, StationConsoleBlockEntity.DISC_SLOTS)
        checkContainerDataCount(data, StationConsoleBlockEntity.DATA_COUNT)

        for (i in 0 until StationConsoleBlockEntity.DISC_SLOTS) {
            addSlot(object : Slot(container, i, 8 + i * 18, DISC_ROW_Y) {
                override fun mayPlace(stack: ItemStack): Boolean = StationConsoleBlockEntity.isDisc(stack)
                override fun getMaxStackSize(): Int = 1
            })
        }
        for (row in 0..2) {
            for (col in 0..8) {
                addSlot(Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, INVENTORY_Y + row * 18))
            }
        }
        for (col in 0..8) {
            addSlot(Slot(playerInventory, col, 8 + col * 18, INVENTORY_Y + 58))
        }
        addDataSlots(data)
    }

    val frequency: Float get() = data.get(0) / 10.0f
    val flags: Int get() = data.get(1)
    val announceEvery: Int get() = data.get(2)
    val status: Int get() = data.get(3)
    val listeners: Int get() = data.get(4)
    val songCount: Int get() = data.get(5)
    val hasAnnouncement: Boolean get() = data.get(6) == 1
    val recording: Boolean get() = data.get(7) == 1
    val voiceChat: Boolean get() = data.get(8) == 1
    val streamUrlsEnabled: Boolean get() = data.get(9) == 1
    val discCount: Int get() = data.get(10)
    val hasStreamUrl: Boolean get() = data.get(11) == 1
    val hasTower: Boolean get() = data.get(12) == 1

    override fun quickMoveStack(player: Player, index: Int): ItemStack {
        val discs = StationConsoleBlockEntity.DISC_SLOTS
        val slot = slots[index]
        if (!slot.hasItem()) return ItemStack.EMPTY
        val stack = slot.item
        val original = stack.copy()
        if (index < discs) {
            if (!moveItemStackTo(stack, discs, discs + 36, true)) return ItemStack.EMPTY
        } else if (StationConsoleBlockEntity.isDisc(stack)) {
            if (!moveItemStackTo(stack, 0, discs, false)) return ItemStack.EMPTY
        } else if (index < discs + 27) {
            if (!moveItemStackTo(stack, discs + 27, discs + 36, false)) return ItemStack.EMPTY
        } else if (!moveItemStackTo(stack, discs, discs + 27, false)) {
            return ItemStack.EMPTY
        }
        if (stack.isEmpty) slot.setByPlayer(ItemStack.EMPTY) else slot.setChanged()
        if (stack.count == original.count) return ItemStack.EMPTY
        slot.onTake(player, stack)
        return original
    }

    override fun stillValid(player: Player): Boolean = container.stillValid(player)
}
