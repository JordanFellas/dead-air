package com.wasteland.deadair.item

import com.wasteland.deadair.registry.ModDataComponents
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import kotlin.math.min

/**
 * Battery-powered items (walkie-talkies, radio backpacks) store their charge in the ENERGY component.
 * Charging tops the device up from the fullest battery cell in the inventory (any tier); the cell
 * keeps whatever the device couldn't hold, so no energy is ever lost.
 */
object BatterySwap {

    fun swap(player: ServerPlayer, device: ItemStack, deviceMax: Int, deviceName: String) {
        val deviceEnergy = device.get(ModDataComponents.ENERGY.get()) ?: deviceMax
        val room = deviceMax - deviceEnergy
        if (room <= 0) {
            player.displayClientMessage(Component.literal("[$deviceName] Already fully charged.").withStyle(ChatFormatting.YELLOW), true)
            return
        }
        var best: ItemStack? = null
        for (i in 0 until player.inventory.containerSize) {
            val stack = player.inventory.getItem(i)
            if (stack.item is BatteryCellItem && BatteryCellItem.getEnergy(stack) > 0 &&
                (best == null || BatteryCellItem.getEnergy(stack) > BatteryCellItem.getEnergy(best))
            ) {
                best = stack
            }
        }
        if (best == null) {
            player.displayClientMessage(
                Component.literal("[$deviceName] No charged battery in your inventory.").withStyle(ChatFormatting.RED), true
            )
            return
        }
        val moved = min(room, BatteryCellItem.getEnergy(best))
        BatteryCellItem.setEnergy(best, BatteryCellItem.getEnergy(best) - moved)
        device.set(ModDataComponents.ENERGY.get(), deviceEnergy + moved)
        player.displayClientMessage(
            Component.literal("[$deviceName] Charged to ${(deviceEnergy + moved) * 100L / deviceMax}% from ")
                .append(best.hoverName).withStyle(ChatFormatting.GREEN),
            true
        )
    }
}
