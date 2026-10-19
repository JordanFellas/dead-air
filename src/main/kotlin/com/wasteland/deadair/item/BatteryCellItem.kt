package com.wasteland.deadair.item

import com.wasteland.deadair.registry.ModDataComponents
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A rechargeable power cell. Three tiers share this class and differ in [capacity]:
 * Scrap Battery Cell, Lithium Battery Pack and Fusion Power Core.
 */
class BatteryCellItem(properties: Properties, val capacity: Int, private val barColor: Int) : Item(properties) {

    companion object {
        /** Capacity of the basic Scrap Battery Cell; walkies and backpacks hold this much. */
        const val MAX_ENERGY = 50000

        fun capacityOf(stack: ItemStack): Int = (stack.item as? BatteryCellItem)?.capacity ?: MAX_ENERGY

        fun getEnergy(stack: ItemStack): Int {
            return stack.get(ModDataComponents.ENERGY.get()) ?: capacityOf(stack)
        }

        fun setEnergy(stack: ItemStack, amount: Int) {
            stack.set(ModDataComponents.ENERGY.get(), max(0, min(amount, capacityOf(stack))))
        }

        fun consumeEnergy(stack: ItemStack, amount: Int): Boolean {
            val current = getEnergy(stack)
            if (current >= amount) {
                setEnergy(stack, current - amount)
                return true
            }
            return false
        }
    }

    override fun isBarVisible(stack: ItemStack): Boolean = true

    override fun getBarWidth(stack: ItemStack): Int = (13.0f * getEnergy(stack) / capacity).roundToInt()

    override fun getBarColor(stack: ItemStack): Int = barColor

    override fun appendHoverText(
        stack: ItemStack,
        context: TooltipContext,
        tooltipComponents: MutableList<Component>,
        tooltipFlag: TooltipFlag
    ) {
        tooltipComponents.add(
            Component.literal("Charge: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("${getEnergy(stack)} / $capacity FE").withStyle(ChatFormatting.AQUA))
        )
        tooltipComponents.add(
            Component.literal("Powers radio towers, walkie-talkies and radio backpacks.").withStyle(ChatFormatting.DARK_GRAY)
        )
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag)
    }
}
