package com.wasteland.deadair.compat.curios

import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import top.theillusivec4.curios.api.CuriosApi

/**
 * Only referenced after checking [com.wasteland.deadair.item.RadioLocator.curiosLoaded],
 * so Curios classes are never loaded when the mod is absent.
 */
object CuriosCompat {

    /** The first stack in the given Curios slot type (e.g. "belt", "back") that matches. */
    fun findInSlot(entity: LivingEntity, slotType: String, predicate: (ItemStack) -> Boolean): ItemStack? {
        val handler = CuriosApi.getCuriosInventory(entity).orElse(null) ?: return null
        val stacks = handler.getStacksHandler(slotType).orElse(null)?.stacks ?: return null
        for (i in 0 until stacks.slots) {
            val stack = stacks.getStackInSlot(i)
            if (!stack.isEmpty && predicate(stack)) return stack
        }
        return null
    }
}
