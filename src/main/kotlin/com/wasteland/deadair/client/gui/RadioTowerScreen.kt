package com.wasteland.deadair.client.gui

import com.wasteland.deadair.DeadAir
import com.wasteland.deadair.menu.RadioTowerMenu
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Inventory

class RadioTowerScreen(
    menu: RadioTowerMenu,
    playerInventory: Inventory,
    title: Component
) : AbstractContainerScreen<RadioTowerMenu>(menu, playerInventory, title) {

    companion object {
        val TEXTURE: ResourceLocation = ResourceLocation.fromNamespaceAndPath(DeadAir.MOD_ID, "textures/gui/radio_tower_gui.png")
    }

    init {
        imageWidth = 176
        imageHeight = 166
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick)
        super.render(guiGraphics, mouseX, mouseY, partialTick)
        renderTooltip(guiGraphics, mouseX, mouseY)

        // Energy Bar Tooltip
        val left = leftPos + 14
        val top = topPos + 16
        if (mouseX in left..(left + 16) && mouseY in top..(top + 56)) {
            guiGraphics.renderTooltip(
                font,
                Component.literal("Energy: ${menu.energy} / ${menu.maxEnergy} FE"),
                mouseX,
                mouseY
            )
        }
    }

    override fun renderBg(guiGraphics: GuiGraphics, partialTick: Float, mouseX: Int, mouseY: Int) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader)
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f)
        RenderSystem.setShaderTexture(0, TEXTURE)

        val x = leftPos
        val y = topPos
        guiGraphics.blit(TEXTURE, x, y, 0, 0, imageWidth, imageHeight)

        // Draw glowing cyan energy fill
        if (menu.maxEnergy > 0) {
            val energyHeight = (54.0f * menu.energy / menu.maxEnergy).toInt()
            if (energyHeight > 0) {
                guiGraphics.fill(x + 15, y + 17 + (54 - energyHeight), x + 29, y + 71, 0xFF00DDFF.toInt())
            }
        }
    }

    override fun renderLabels(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        guiGraphics.drawString(font, title, 8, 6, 0x404040, false)

        // Text inside Diagnostic Monitor: (x: 40, y: 20)
        val statusColor = if (menu.isActive) 0x55FF55 else 0xFF5555
        val statusText = if (menu.isActive) "ONLINE" else "STANDBY"
        guiGraphics.drawString(font, "[$statusText]", 40, 20, statusColor, false)

        val structText = when {
            menu.missingFoundation -> "Needs 5x5 Foundation"
            menu.needsFooting -> "Needs footing on ground"
            menu.isStructureValid -> "${menu.layerCount}m tall, ${menu.partCount} parts"
            else -> "No tower built"
        }
        val structColor = if (menu.isStructureValid) 0x66DD66 else 0xDD6666
        guiGraphics.drawString(font, structText, 40, 31, structColor, false)

        val antText = if (menu.antennaBonus > 0) "Antennas: +${menu.antennaBonus}m" else "Antennas: None"
        guiGraphics.drawString(font, antText, 40, 42, 0x88CC88, false)

        guiGraphics.drawString(font, "Range: ${menu.radius}m", 40, 53, 0x55DDFF, false)
        guiGraphics.drawString(font, "Relays: ${menu.linkedTowers}", 40, 62, 0xDDDD55, false)

        // Slot Label
        guiGraphics.drawString(font, "BAT", 146, 24, 0x606060, false)

        guiGraphics.drawString(font, playerInventoryTitle, 8, imageHeight - 94, 0x404040, false)
    }
}
