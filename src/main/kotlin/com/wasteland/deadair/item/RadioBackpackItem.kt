package com.wasteland.deadair.item

import com.wasteland.deadair.registry.ModDataComponents
import net.minecraft.ChatFormatting
import net.minecraft.core.Holder
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Equipable
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.level.Level
import kotlin.math.roundToInt

/**
 * A wearable relay. Worn in the Curios back slot (or the chest slot without Curios), it acts as a
 * small mobile radio tower centered on the wearer, so nearby walkies work without a fixed tower.
 */
class RadioBackpackItem(properties: Properties) : Item(properties), Equipable {

    companion object {
        const val RELAY_RADIUS = 200
        /** FE per second while worn and relaying. */
        const val DRAIN_PER_SECOND = 60
        val MAX_ENERGY: Int get() = BatteryCellItem.MAX_ENERGY

        fun getEnergy(stack: ItemStack): Int = stack.get(ModDataComponents.ENERGY.get()) ?: MAX_ENERGY

        fun setEnergy(stack: ItemStack, amount: Int) {
            stack.set(ModDataComponents.ENERGY.get(), amount.coerceIn(0, MAX_ENERGY))
        }
    }

    override fun getEquipmentSlot(): EquipmentSlot = EquipmentSlot.CHEST

    override fun getEquipSound(): Holder<SoundEvent> = SoundEvents.ARMOR_EQUIP_LEATHER

    /** Sneak + right-click swaps in a fuller battery; right-click equips it. */
    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResultHolder<ItemStack> {
        val stack = player.getItemInHand(hand)
        if (player.isShiftKeyDown) {
            if (player is ServerPlayer) BatterySwap.swap(player, stack, MAX_ENERGY, "Radio Backpack")
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide)
        }
        return swapWithEquipmentSlot(this, level, player, hand)
    }

    /** The backpack drains every second; don't replay the swap animation if it's held. */
    override fun shouldCauseReequipAnimation(oldStack: ItemStack, newStack: ItemStack, slotChanged: Boolean): Boolean =
        slotChanged || oldStack.item != newStack.item

    override fun isBarVisible(stack: ItemStack): Boolean = true

    override fun getBarWidth(stack: ItemStack): Int = (13.0f * getEnergy(stack) / MAX_ENERGY).roundToInt()

    override fun getBarColor(stack: ItemStack): Int = 0x55DDFF

    override fun appendHoverText(
        stack: ItemStack,
        context: TooltipContext,
        tooltipComponents: MutableList<Component>,
        tooltipFlag: TooltipFlag
    ) {
        tooltipComponents.add(
            Component.literal("Battery: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("${getEnergy(stack) * 100 / MAX_ENERGY}%").withStyle(ChatFormatting.AQUA))
        )
        tooltipComponents.add(
            Component.literal("Wear it to relay radio within $RELAY_RADIUS blocks of you").withStyle(ChatFormatting.DARK_GRAY)
        )
        tooltipComponents.add(Component.literal("Back slot (Curios) or chest slot").withStyle(ChatFormatting.DARK_GRAY))
        tooltipComponents.add(Component.literal("Sneak + right-click: swap battery").withStyle(ChatFormatting.DARK_GRAY))
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag)
    }
}
