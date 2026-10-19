package com.wasteland.deadair.item

import com.wasteland.deadair.client.ClientHooks
import com.wasteland.deadair.registry.ModDataComponents
import com.wasteland.deadair.registry.ModSounds
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.level.Level
import kotlin.math.roundToInt

/** Portable radio: plays a station out loud around whoever carries it. */
class BoomboxItem(properties: Properties) : Item(properties) {

    companion object {
        const val DEFAULT_FREQUENCY = 99.9f

        fun getFrequency(stack: ItemStack): Float = stack.get(ModDataComponents.FREQUENCY.get()) ?: DEFAULT_FREQUENCY

        fun setFrequency(stack: ItemStack, freq: Float) {
            stack.set(ModDataComponents.FREQUENCY.get(), ((freq * 10).roundToInt() / 10.0f).coerceIn(0.1f, 999.9f))
        }

        fun isOn(stack: ItemStack): Boolean = stack.get(ModDataComponents.POWER.get()) ?: false

        fun setOn(stack: ItemStack, on: Boolean) {
            stack.set(ModDataComponents.POWER.get(), on)
        }

        /** The first switched-on boombox a player carries, if any. */
        fun findPlaying(player: Player): ItemStack? {
            for (i in 0 until player.inventory.containerSize) {
                val stack = player.inventory.getItem(i)
                if (stack.item is BoomboxItem && isOn(stack)) return stack
            }
            return null
        }

        /** Inventory slot of a held boombox (40 = offhand). */
        fun heldSlot(player: Player, hand: InteractionHand): Int =
            if (hand == InteractionHand.MAIN_HAND) player.inventory.selected else RadioLocator.SLOT_OFFHAND
    }

    /** Right-click: switch on/off. Sneak + right-click: tune. */
    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResultHolder<ItemStack> {
        val stack = player.getItemInHand(hand)
        if (player.isShiftKeyDown) {
            if (level.isClientSide) ClientHooks.openBoombox(heldSlot(player, hand))
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide)
        }
        if (!level.isClientSide) {
            val on = !isOn(stack)
            setOn(stack, on)
            level.playSound(
                null, player.x, player.y, player.z,
                if (on) ModSounds.RADIO_CLICK_ON.get() else ModSounds.RADIO_CLICK_OFF.get(),
                SoundSource.PLAYERS, 0.7f, 0.8f
            )
            player.displayClientMessage(
                Component.literal(if (on) "Boombox on: %.1f MHz".format(getFrequency(stack)) else "Boombox off")
                    .withStyle(if (on) ChatFormatting.GREEN else ChatFormatting.GRAY),
                true
            )
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide)
    }

    override fun shouldCauseReequipAnimation(oldStack: ItemStack, newStack: ItemStack, slotChanged: Boolean): Boolean =
        slotChanged || oldStack.item != newStack.item

    override fun appendHoverText(
        stack: ItemStack,
        context: TooltipContext,
        tooltipComponents: MutableList<Component>,
        tooltipFlag: TooltipFlag
    ) {
        tooltipComponents.add(
            Component.literal("Station: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("%.1f MHz".format(getFrequency(stack))).withStyle(ChatFormatting.YELLOW))
        )
        tooltipComponents.add(
            if (isOn(stack)) Component.literal("PLAYING").withStyle(ChatFormatting.GREEN)
            else Component.literal("OFF").withStyle(ChatFormatting.RED)
        )
        tooltipComponents.add(Component.literal("Right-click: on/off").withStyle(ChatFormatting.DARK_GRAY))
        tooltipComponents.add(Component.literal("Sneak + right-click: tune").withStyle(ChatFormatting.DARK_GRAY))
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag)
    }
}
