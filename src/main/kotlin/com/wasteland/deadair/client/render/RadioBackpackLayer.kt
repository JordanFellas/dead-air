package com.wasteland.deadair.client.render

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import com.wasteland.deadair.item.RadioLocator
import net.minecraft.client.Minecraft
import net.minecraft.client.model.PlayerModel
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.entity.RenderLayerParent
import net.minecraft.client.renderer.entity.layers.RenderLayer
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.world.item.ItemDisplayContext

/** Draws a worn Radio Backpack (chest slot or Curios back slot) on the player's back. */
class RadioBackpackLayer(
    parent: RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>
) : RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>(parent) {

    override fun render(
        poseStack: PoseStack, buffer: MultiBufferSource, packedLight: Int, player: AbstractClientPlayer,
        limbSwing: Float, limbSwingAmount: Float, partialTick: Float, ageInTicks: Float, netHeadYaw: Float, headPitch: Float
    ) {
        if (player.isInvisible) return
        val backpack = RadioLocator.wornBackpack(player) ?: return

        poseStack.pushPose()
        parentModel.body.translateAndRotate(poseStack)
        // Body space: y points down, +z is the player's back. Sit the pack between the shoulders.
        poseStack.translate(0.0, 0.38, 0.27)
        poseStack.mulPose(Axis.ZP.rotationDegrees(180.0f))
        poseStack.scale(0.62f, 0.62f, 0.62f)
        Minecraft.getInstance().itemRenderer.renderStatic(
            backpack, ItemDisplayContext.FIXED, packedLight, OverlayTexture.NO_OVERLAY,
            poseStack, buffer, player.level(), player.id
        )
        poseStack.popPose()
    }
}
