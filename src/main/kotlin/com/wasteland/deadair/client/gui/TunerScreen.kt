package com.wasteland.deadair.client.gui

import com.wasteland.deadair.block.entity.HomeRadioBlockEntity
import com.wasteland.deadair.client.ClientRadioState
import com.wasteland.deadair.client.sound.RadioSounds
import com.wasteland.deadair.item.BoomboxItem
import com.wasteland.deadair.network.packets.PacketTunerSettings
import com.wasteland.deadair.registry.ModSounds
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractButton
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.neoforged.neoforge.network.PacketDistributor
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Old-school sliding-dial tuner shared by the Home Radio (block) and the boombox (inventory slot).
 * Reads the frequency live from the block or item, so SCAN results show up as soon as they sync.
 */
class TunerScreen private constructor(
    title: String,
    private val pos: BlockPos?,
    private val slot: Int
) : Screen(Component.literal(title)) {

    companion object {
        private const val W = 220
        private const val H = 146

        private const val WOOD = 0xFF6B4226.toInt()
        private const val WOOD_DARK = 0xFF4A2C17.toInt()
        private const val WOOD_LIGHT = 0xFF8A5A36.toInt()
        private const val GRILLE = 0xFF2B1D12.toInt()
        private const val DIAL = 0xFFF1E3C0.toInt()
        private const val DIAL_GLOW = 0xFFFFD98A.toInt()
        private const val DIAL_OFF = 0xFF8E8571.toInt()
        private const val INK = 0xFF3A2A18.toInt()
        private const val NEEDLE = 0xFFD2321E.toInt()
        private const val KEY = 0xFF2E2420.toInt()
        private const val KEY_HOVER = 0xFF4A3A30.toInt()
        private const val KEY_TEXT = 0xFFF1E3C0.toInt()

        fun forHomeRadio(pos: BlockPos) = TunerScreen("Home Radio", pos, -1)
        fun forBoombox(slot: Int) = TunerScreen("Boombox", null, slot)
    }

    private var left = 0
    private var top = 0

    private val radio: HomeRadioBlockEntity?
        get() = pos?.let { minecraft?.level?.getBlockEntity(it) as? HomeRadioBlockEntity }

    private val frequency: Float
        get() = radio?.frequency ?: boombox()?.let { BoomboxItem.getFrequency(it) } ?: 99.9f

    private val isOn: Boolean
        get() = radio?.isOn ?: boombox()?.let { BoomboxItem.isOn(it) } ?: false

    private fun boombox() = minecraft?.player?.inventory?.let { inv ->
        if (slot in 0 until inv.containerSize) inv.getItem(slot).takeIf { it.item is BoomboxItem } else null
    }

    override fun init() {
        left = (width - W) / 2
        top = (height - H) / 2
        val steps = listOf("-1" to -1.0f, "-.1" to -0.1f, "+.1" to 0.1f, "+1" to 1.0f)
        steps.forEachIndexed { i, (label, delta) ->
            addRenderableWidget(KnobButton(left + 14 + i * 34, top + 112, 30, 18, label) { send(frequency + delta, isOn, false) })
        }
        addRenderableWidget(KnobButton(left + 152, top + 112, 26, 18, "SCAN") { send(frequency, true, true) })
        addRenderableWidget(KnobButton(left + 182, top + 112, 26, 18, "PWR") {
            val on = !isOn
            RadioSounds.playLocal(if (on) ModSounds.RADIO_CLICK_ON.get() else ModSounds.RADIO_CLICK_OFF.get(), 0.6f, 0.8f)
            send(frequency, on, false)
        })
    }

    override fun isPauseScreen(): Boolean = false

    override fun tick() {
        if (pos != null && radio == null) onClose()
        if (pos == null && boombox() == null) onClose()
    }

    private fun send(freq: Float, on: Boolean, scan: Boolean) {
        val clean = ((freq * 10).roundToInt() / 10.0f).coerceIn(0.1f, 999.9f)
        if (!scan) RadioSounds.playLocal(ModSounds.RADIO_BEEP.get(), 0.2f, 1.4f)
        PacketDistributor.sendToServer(PacketTunerSettings(pos != null, pos ?: BlockPos.ZERO, slot, clean, on, scan))
    }

    override fun renderBackground(g: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.renderBackground(g, mouseX, mouseY, partialTick)
        val x = left
        val y = top
        // Wooden cabinet
        g.fill(x - 3, y - 3, x + W + 3, y + H + 3, WOOD_DARK)
        g.fill(x, y, x + W, y + H, WOOD)
        for (i in 0 until H step 7) g.fill(x, y + i, x + W, y + i + 1, WOOD_LIGHT)
        g.drawString(font, title, x + 10, y + 7, DIAL, false)
        g.fill(x + W - 16, y + 8, x + W - 10, y + 14, if (isOn) 0xFF5BE05B.toInt() else 0xFF5A2A20.toInt())

        // Speaker cloth
        g.fill(x + 10, y + 82, x + W - 10, y + 106, GRILLE)
        for (i in 0 until (W - 24) / 4) g.fill(x + 12 + i * 4, y + 84, x + 13 + i * 4, y + 104, 0xFF3D2A1A.toInt())

        drawDial(g, x + 10, y + 20, W - 20, 58)
    }

    private fun drawDial(g: GuiGraphics, x: Int, y: Int, w: Int, h: Int) {
        val on = isOn
        val freq = frequency
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, WOOD_DARK)
        g.fill(x, y, x + w, y + h, if (on) DIAL_GLOW else DIAL_OFF)
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, if (on) DIAL else DIAL_OFF)

        // Sliding scale: one tick per 0.5 MHz, labels every 2 MHz, the needle stays in the middle
        val pxPerMhz = 18.0f
        val center = x + w / 2
        g.enableScissor(x + 2, y + 2, x + w - 2, y + h - 2)
        val first = floor(freq - w / 2 / pxPerMhz).toInt() - 1
        val last = first + (w / pxPerMhz).toInt() + 3
        for (mhz in first..last) {
            for (half in 0..1) {
                val f = mhz + half * 0.5f
                if (f < 0) continue
                val px = center + ((f - freq) * pxPerMhz).roundToInt()
                val tall = half == 0
                g.fill(px, y + 4, px + 1, y + if (tall) 13 else 9, INK)
                if (tall && mhz % 2 == 0) g.drawCenteredString(font, "$mhz", px + 1, y + 15, INK)
            }
        }
        g.disableScissor()
        g.fill(center, y + 2, center + 1, y + 28, NEEDLE)

        // Readout and station
        g.drawString(font, "%.1f MHz".format(freq), x + 6, y + 31, INK, false)
        val station = ClientRadioState.stationCallSign
        val tunedHere = station.isNotEmpty() && abs(ClientRadioState.stationFrequency - freq) < 0.05f
        val line1 = when {
            !on -> "OFF"
            tunedHere -> station
            else -> "~ static ~"
        }
        g.drawString(font, line1, x + w - 6 - font.width(line1), y + 31, if (tunedHere && on) NEEDLE else INK, false)
        if (on && tunedHere && ClientRadioState.stationTitle.isNotEmpty()) {
            val title = "♫ " + ClientRadioState.stationTitle
            g.drawString(font, font.plainSubstrByWidth(title, w - 12), x + 6, y + 44, INK, false)
        } else if (on && !tunedHere) {
            g.drawString(font, "Press SCAN to find a station", x + 6, y + 44, 0xFF7A6A50.toInt(), false)
        }
    }

    private inner class KnobButton(
        x: Int, y: Int, w: Int, h: Int, label: String,
        private val action: () -> Unit
    ) : AbstractButton(x, y, w, h, Component.literal(label)) {
        override fun onPress() = action()

        override fun renderWidget(g: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
            g.fill(x, y + 2, x + width, y + height, 0xFF120C08.toInt())
            g.fill(x, y, x + width, y + height - 2, if (isHoveredOrFocused) KEY_HOVER else KEY)
            g.drawCenteredString(font, message, x + width / 2, y + (height - 10) / 2 + 1, KEY_TEXT)
        }

        override fun updateWidgetNarration(output: NarrationElementOutput) = defaultButtonNarrationText(output)
    }
}
