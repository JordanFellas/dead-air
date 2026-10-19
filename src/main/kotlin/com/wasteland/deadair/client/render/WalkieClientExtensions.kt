package com.wasteland.deadair.client.render

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.model.HumanoidModel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions

/**
 * While the walkie is "in use" (right-click held), lean it a little toward the player's face,
 * like keying up to talk, instead of vanilla's goat-horn pose that fills the screen.
 */
object WalkieClientExtensions : IClientItemExtensions {

    private const val RAISE_TICKS = 4.0f

    override fun getArmPose(entity: LivingEntity, hand: InteractionHand, stack: ItemStack): HumanoidModel.ArmPose? {
        return if (entity.isUsingItem && entity.usedItemHand == hand) HumanoidModel.ArmPose.TOOT_HORN else null
    }

    override fun applyForgeHandTransform(
        poseStack: PoseStack, player: LocalPlayer, arm: HumanoidArm, itemInHand: ItemStack,
        partialTick: Float, equipProcess: Float, swingProcess: Float
    ): Boolean {
        val side = if (arm == HumanoidArm.RIGHT) 1.0f else -1.0f
        // Vanilla's resting hand position
        poseStack.translate(side * 0.56f, -0.52f + equipProcess * -0.6f, -0.72f)

        val using = player.isUsingItem && player.useItem == itemInHand
        if (using) {
            val raise = ((player.ticksUsingItem + partialTick) / RAISE_TICKS).coerceIn(0.0f, 1.0f)
            // A small lean: up, in toward the middle, and closer to the camera, tilted to face you
            poseStack.translate(-side * 0.16f * raise, 0.14f * raise, 0.16f * raise)
            poseStack.mulPose(Axis.YP.rotationDegrees(-side * 12.0f * raise))
            poseStack.mulPose(Axis.XP.rotationDegrees(6.0f * raise))
        }
        return true
    }
}
