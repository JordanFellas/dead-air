package com.wasteland.deadair.item

import com.wasteland.deadair.compat.curios.CuriosCompat
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.neoforged.fml.ModList

/**
 * Finds walkie-talkies and radio backpacks wherever a player carries them.
 *
 * Radios are addressed by "slot": an index into the player's inventory (40 is the offhand),
 * or [SLOT_BELT] for a walkie worn in the Curios belt slot.
 */
object RadioLocator {
    const val SLOT_BELT = -2
    const val SLOT_OFFHAND = 40

    val curiosLoaded: Boolean by lazy { ModList.get().isLoaded("curios") }

    fun getRadio(player: Player, slot: Int): ItemStack {
        val stack = when (slot) {
            SLOT_BELT -> beltRadio(player) ?: ItemStack.EMPTY
            in 0 until player.inventory.containerSize -> player.inventory.getItem(slot)
            else -> ItemStack.EMPTY
        }
        return if (stack.item is HandheldRadioItem) stack else ItemStack.EMPTY
    }

    /** Slot of the radio a player is holding or wearing on their belt, for opening the UI. */
    fun findPreferredSlot(player: Player): Int? {
        if (player.mainHandItem.item is HandheldRadioItem) return player.inventory.selected
        if (player.offhandItem.item is HandheldRadioItem) return SLOT_OFFHAND
        if (beltRadio(player) != null) return SLOT_BELT
        for (i in 0 until player.inventory.containerSize) {
            if (player.inventory.getItem(i).item is HandheldRadioItem) return i
        }
        return null
    }

    /** A powered, charged radio that can send voice: held in a hand or clipped to the belt. */
    fun findTransmitter(player: Player): ItemStack? {
        val candidates = listOfNotNull(player.mainHandItem, player.offhandItem, beltRadio(player))
        return candidates.firstOrNull {
            it.item is HandheldRadioItem && HandheldRadioItem.isRadioOn(it) && HandheldRadioItem.getEnergy(it) > 0
        }
    }

    /** Every powered radio the player carries, including one on the belt. */
    fun allPoweredRadios(player: Player): List<ItemStack> {
        val result = ArrayList<ItemStack>()
        for (i in 0 until player.inventory.containerSize) {
            val stack = player.inventory.getItem(i)
            if (stack.item is HandheldRadioItem && HandheldRadioItem.isRadioOn(stack)) result.add(stack)
        }
        beltRadio(player)?.let { if (HandheldRadioItem.isRadioOn(it)) result.add(it) }
        return result
    }

    /** A charged radio backpack worn in the Curios back slot, or in the chest armor slot without Curios. */
    fun wornBackpack(player: Player): ItemStack? {
        val chest = player.getItemBySlot(EquipmentSlot.CHEST)
        if (chest.item is RadioBackpackItem) return chest
        if (curiosLoaded) return CuriosCompat.findInSlot(player, "back") { it.item is RadioBackpackItem }
        return null
    }

    private fun beltRadio(player: Player): ItemStack? {
        if (!curiosLoaded) return null
        return CuriosCompat.findInSlot(player, "belt") { it.item is HandheldRadioItem }
    }
}
