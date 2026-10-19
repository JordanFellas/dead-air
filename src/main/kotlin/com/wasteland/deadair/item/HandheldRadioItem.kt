package com.wasteland.deadair.item

import com.wasteland.deadair.client.ClientHooks
import com.wasteland.deadair.registry.ModDataComponents
import com.wasteland.deadair.registry.ModSounds
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.item.UseAnim
import net.minecraft.world.level.Level
import net.neoforged.fml.ModList
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class HandheldRadioItem(properties: Properties) : Item(properties) {

    companion object {
        /** A walkie runs on one Scrap Battery Cell. */
        val MAX_ENERGY: Int get() = BatteryCellItem.MAX_ENERGY
        const val DEFAULT_FREQUENCY = 104.5f
        const val TEXT_MESSAGE_COST = 50

        fun getEnergy(stack: ItemStack): Int {
            return stack.get(ModDataComponents.ENERGY.get()) ?: MAX_ENERGY
        }

        fun setEnergy(stack: ItemStack, amount: Int) {
            stack.set(ModDataComponents.ENERGY.get(), max(0, min(amount, MAX_ENERGY)))
        }

        fun consumeEnergy(stack: ItemStack, amount: Int): Boolean {
            val current = getEnergy(stack)
            if (current >= amount) {
                setEnergy(stack, current - amount)
                return true
            }
            return false
        }

        fun getFrequency(stack: ItemStack): Float {
            return stack.get(ModDataComponents.FREQUENCY.get()) ?: DEFAULT_FREQUENCY
        }

        fun setFrequency(stack: ItemStack, freq: Float) {
            val clamped = (Math.round(freq * 10.0f) / 10.0f).coerceIn(0.1f, 999.9f)
            stack.set(ModDataComponents.FREQUENCY.get(), clamped)
        }

        fun isRadioOn(stack: ItemStack): Boolean {
            return stack.get(ModDataComponents.POWER.get()) ?: true
        }

        fun setRadioOn(stack: ItemStack, on: Boolean) {
            stack.set(ModDataComponents.POWER.get(), on)
        }

        fun getVolume(stack: ItemStack): Int {
            return stack.get(ModDataComponents.VOLUME.get()) ?: 100
        }

        fun setVolume(stack: ItemStack, volume: Int) {
            stack.set(ModDataComponents.VOLUME.get(), volume.coerceIn(0, 100))
        }
    }

    override fun isBarVisible(stack: ItemStack): Boolean = true

    override fun getBarWidth(stack: ItemStack): Int {
        return (13.0f * getEnergy(stack) / MAX_ENERGY).roundToInt()
    }

    override fun getBarColor(stack: ItemStack): Int {
        val ratio = getEnergy(stack).toFloat() / MAX_ENERGY
        return when {
            ratio > 0.5f -> 0x55FF55
            ratio > 0.2f -> 0xFFFF55
            else -> 0xFF5555
        }
    }

    /** Hold right-click to raise the walkie to your face (goat-horn pose); sneak + right-click opens the radio. */
    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResultHolder<ItemStack> {
        val stack = player.getItemInHand(hand)
        if (player.isShiftKeyDown) {
            if (level.isClientSide) {
                val slot = if (hand == InteractionHand.MAIN_HAND) player.inventory.selected else RadioLocator.SLOT_OFFHAND
                ClientHooks.openRadioScreen(slot)
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide)
        }
        player.startUsingItem(hand)
        if (!level.isClientSide) {
            level.playSound(null, player.x, player.y, player.z, ModSounds.RADIO_CLICK_ON.get(), SoundSource.PLAYERS, 0.5f, 1.0f)
        }
        return InteractionResultHolder.consume(stack)
    }

    /**
     * Talking drains the battery every few ticks, which changes the stack's data. Without this the
     * game treats each change as a new item and replays the swap animation (radio dips and comes back).
     */
    override fun shouldCauseReequipAnimation(oldStack: ItemStack, newStack: ItemStack, slotChanged: Boolean): Boolean =
        slotChanged || oldStack.item != newStack.item

    /** The raise-to-face lean is drawn by WalkieClientExtensions, so no vanilla animation. */
    override fun getUseAnimation(stack: ItemStack): UseAnim = UseAnim.NONE

    override fun getUseDuration(stack: ItemStack, entity: LivingEntity): Int = 72000

    override fun releaseUsing(stack: ItemStack, level: Level, entity: LivingEntity, timeLeft: Int) {
        if (!level.isClientSide) {
            level.playSound(null, entity.x, entity.y, entity.z, ModSounds.RADIO_CLICK_OFF.get(), SoundSource.PLAYERS, 0.5f, 1.0f)
        }
    }

    override fun appendHoverText(
        stack: ItemStack,
        context: TooltipContext,
        tooltipComponents: MutableList<Component>,
        tooltipFlag: TooltipFlag
    ) {
        val energy = getEnergy(stack)
        tooltipComponents.add(
            Component.literal("Frequency: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.format("%.1f MHz", getFrequency(stack))).withStyle(ChatFormatting.YELLOW))
        )
        tooltipComponents.add(
            Component.literal("Power: ").withStyle(ChatFormatting.GRAY)
                .append(
                    if (isRadioOn(stack)) Component.literal("ONLINE").withStyle(ChatFormatting.GREEN)
                    else Component.literal("STANDBY").withStyle(ChatFormatting.RED)
                )
        )
        tooltipComponents.add(
            Component.literal("Battery: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("${energy * 100 / MAX_ENERGY}%").withStyle(ChatFormatting.AQUA))
        )
        tooltipComponents.add(Component.literal("Sneak + right-click or R: open radio").withStyle(ChatFormatting.DARK_GRAY))
        tooltipComponents.add(Component.literal("Hold right-click: raise to your face").withStyle(ChatFormatting.DARK_GRAY))
        if (ModList.get().isLoaded("voicechat")) {
            tooltipComponents.add(
                Component.literal("Hold or wear on belt + talk (Voice Chat) to transmit").withStyle(ChatFormatting.DARK_GRAY)
            )
        }
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag)
    }
}
