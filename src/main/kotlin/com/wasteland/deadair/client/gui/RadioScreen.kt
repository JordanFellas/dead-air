package com.wasteland.deadair.client.gui

import com.wasteland.deadair.client.ClientRadioState
import com.wasteland.deadair.client.sound.RadioSounds
import com.wasteland.deadair.item.BatteryCellItem
import com.wasteland.deadair.item.HandheldRadioItem
import com.wasteland.deadair.item.RadioLocator
import com.wasteland.deadair.network.packets.PacketRadioTransmit
import com.wasteland.deadair.network.packets.PacketSetRadioSettings
import com.wasteland.deadair.network.packets.PacketSwapRadioBattery
import com.wasteland.deadair.registry.ModSounds
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractButton
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.network.PacketDistributor
import org.lwjgl.glfw.GLFW
import kotlin.math.roundToInt

/**
 * Walkie-talkie faceplate: LCD (frequency, signal, battery), keypad tuning, channel presets,
 * battery swap and a text transmit box. A plain Screen, so letter keys (like E) never close it.
 */
class RadioScreen(private val slot: Int) : Screen(Component.literal("Walkie-Talkie")) {

    companion object {
        private const val W = 230
        private const val H = 196

        private val PRESETS = floatArrayOf(99.9f, 104.5f, 107.7f, 121.5f)

        // Palette
        private const val BODY = 0xFFE07B26.toInt()
        private const val BODY_DARK = 0xFFB35A14.toInt()
        private const val BODY_LIGHT = 0xFFF29A47.toInt()
        private const val RUBBER = 0xFF26221F.toInt()
        private const val RUBBER_LIGHT = 0xFF3B3631.toInt()
        private const val LCD_ON = 0xFF9DB37A.toInt()
        private const val LCD_OFF = 0xFF3C4434.toInt()
        private const val LCD_INK = 0xFF1F2A17.toInt()
        private const val LCD_FAINT = 0xFF83996A.toInt()
        private const val KEY = 0xFF2E2A27.toInt()
        private const val KEY_HOVER = 0xFF47413B.toInt()
        private const val KEY_TEXT = 0xFFE9E2D6.toInt()
        private const val LED_RED = 0xFFE23B2E.toInt()
        private const val LED_GREEN = 0xFF5BE05B.toInt()
    }

    private var frequency = HandheldRadioItem.DEFAULT_FREQUENCY
    private var poweredOn = true
    private var volume = 100

    private lateinit var messageBox: EditBox
    private var left = 0
    private var top = 0

    private val radio: ItemStack
        get() = minecraft?.player?.let { RadioLocator.getRadio(it, slot) } ?: ItemStack.EMPTY

    override fun init() {
        val stack = radio
        if (stack.isEmpty) {
            onClose()
            return
        }
        frequency = HandheldRadioItem.getFrequency(stack)
        poweredOn = HandheldRadioItem.isRadioOn(stack)
        volume = HandheldRadioItem.getVolume(stack)

        left = (width - W) / 2
        top = (height - H) / 2

        // Keypad tuning row
        val tuning = listOf("-1.0" to -1.0f, "-0.1" to -0.1f, "+0.1" to 0.1f, "+1.0" to 1.0f)
        tuning.forEachIndexed { i, (label, delta) ->
            addRenderableWidget(RadioButton(left + 14 + i * 41, top + 92, 38, 18, label) { nudge(delta) })
        }
        addRenderableWidget(RadioButton(left + 178, top + 92, 38, 18, "PWR", accent = true) { togglePower() })

        // Channel presets
        PRESETS.forEachIndexed { i, preset ->
            addRenderableWidget(RadioButton(left + 14 + i * 41, top + 114, 38, 16, "CH${i + 1}") { tuneTo(preset) })
        }
        addRenderableWidget(RadioButton(left + 178, top + 114, 38, 16, "BATT") {
            PacketDistributor.sendToServer(PacketSwapRadioBattery(slot))
        })

        // Message box + send
        messageBox = EditBox(font, left + 18, top + 146, 150, 12, Component.literal("Message"))
        messageBox.setMaxLength(128)
        messageBox.setBordered(false)
        messageBox.setTextColor(0xFF9DE07A.toInt())
        messageBox.setHint(Component.literal("Type to transmit..."))
        addRenderableWidget(messageBox)
        addRenderableWidget(RadioButton(left + 178, top + 140, 38, 22, "SEND", accent = true) { transmit() })

        setInitialFocus(messageBox)
    }

    override fun isPauseScreen(): Boolean = false

    override fun tick() {
        if (radio.isEmpty) onClose()
    }

    private fun nudge(delta: Float) = tuneTo(frequency + delta)

    private fun tuneTo(freq: Float) {
        frequency = ((freq * 10.0f).roundToInt() / 10.0f).coerceIn(0.1f, 999.9f)
        RadioSounds.playLocal(ModSounds.RADIO_BEEP.get(), 0.25f, 1.6f)
        sync()
    }

    private fun togglePower() {
        poweredOn = !poweredOn
        RadioSounds.playLocal(if (poweredOn) ModSounds.RADIO_CLICK_ON.get() else ModSounds.RADIO_CLICK_OFF.get(), 0.6f, 1.0f)
        sync()
    }

    private fun sync() {
        PacketDistributor.sendToServer(PacketSetRadioSettings(slot, frequency, poweredOn, volume))
    }

    private fun transmit() {
        val text = messageBox.value.trim()
        if (text.isNotEmpty()) {
            PacketDistributor.sendToServer(PacketRadioTransmit(text, slot))
            messageBox.value = ""
        }
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) return super.keyPressed(keyCode, scanCode, modifiers)
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            transmit()
            return true
        }
        if (messageBox.isFocused) {
            // Typing owns the keyboard: no hotkey (inventory, drop, chat...) can steal a letter.
            messageBox.keyPressed(keyCode, scanCode, modifiers)
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun renderBackground(g: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.renderBackground(g, mouseX, mouseY, partialTick)
        drawBody(g)
        drawLcd(g)
        drawMessageWell(g)
    }

    private fun drawBody(g: GuiGraphics) {
        val x = left
        val y = top
        // Antenna and knob sticking out of the top
        g.fill(x + 22, y - 34, x + 30, y + 4, RUBBER)
        g.fill(x + 24, y - 38, x + 28, y - 34, RUBBER_LIGHT)
        g.fill(x + 190, y - 10, x + 208, y + 4, RUBBER)
        for (i in 0 until 4) g.fill(x + 192 + i * 4, y - 9, x + 194 + i * 4, y + 2, RUBBER_LIGHT)

        // Rubber bumper + orange shell
        g.fill(x - 4, y - 2, x + W + 4, y + H + 4, RUBBER)
        g.fill(x, y, x + W, y + H, BODY)
        g.fill(x, y, x + W, y + 3, BODY_LIGHT)
        g.fill(x, y + H - 4, x + W, y + H, BODY_DARK)
        g.fill(x, y, x + 3, y + H, BODY_LIGHT)
        g.fill(x + W - 3, y, x + W, y + H, BODY_DARK)

        // Speaker grille along the bottom
        for (i in 0 until 12) {
            g.fill(x + 16 + i * 17, y + 172, x + 28 + i * 17, y + 176, RUBBER)
            g.fill(x + 16 + i * 17, y + 180, x + 28 + i * 17, y + 184, RUBBER)
        }

        // Brand plate and power LED
        g.drawString(font, "DEAD AIR", x + 14, y + 6, RUBBER, false)
        g.drawString(font, "DA-7", x + W - 40, y + 6, BODY_DARK, false)
        g.fill(x + W - 14, y + 7, x + W - 9, y + 12, if (poweredOn) LED_GREEN else LED_RED)
    }

    private fun drawLcd(g: GuiGraphics) {
        val x = left + 14
        val y = top + 20
        val w = W - 28
        val h = 64
        g.fill(x - 3, y - 3, x + w + 3, y + h + 3, RUBBER)
        if (!poweredOn) {
            g.fill(x, y, x + w, y + h, LCD_OFF)
            g.drawCenteredString(font, "- STANDBY -", x + w / 2, y + h / 2 - 4, 0xFF5A6650.toInt())
            return
        }
        g.fill(x, y, x + w, y + h, LCD_ON)
        // Faint pixel grid for that LCD look
        for (gx in x until x + w step 3) g.fill(gx, y, gx + 1, y + h, 0x10000000)

        val stack = radio
        val energy = if (stack.isEmpty) 0 else HandheldRadioItem.getEnergy(stack)
        val pct = energy * 100 / HandheldRadioItem.MAX_ENERGY

        // Top row: signal bars, status, battery
        drawSignalBars(g, x + 6, y + 5)
        val status = when {
            ClientRadioState.bars == 0 -> "NO SIGNAL"
            ClientRadioState.obstructed -> "BLOCKED"
            else -> "LINKED"
        }
        g.drawString(font, status, x + 34, y + 5, LCD_INK, false)
        drawBattery(g, x + w - 52, y + 4, pct)
        g.drawString(font, "$pct%", x + w - 26, y + 5, LCD_INK, false)

        // Big frequency readout
        val freqText = String.format("%05.1f", frequency)
        g.pose().pushPose()
        g.pose().translate((x + 10).toFloat(), (y + 20).toFloat(), 0f)
        g.pose().scale(3.0f, 3.0f, 1.0f)
        g.drawString(font, "888.8", 0, 0, LCD_FAINT, false)
        g.drawString(font, freqText, 0, 0, LCD_INK, false)
        g.pose().popPose()
        g.drawString(font, "MHz", x + 106, y + 38, LCD_INK, false)

        // Bottom row: channel (or the radio station on this frequency) + batteries available
        val preset = PRESETS.indexOfFirst { kotlin.math.abs(it - frequency) < 0.05f }
        val station = ClientRadioState.stationCallSign
        val channel = if (station.isNotEmpty() && kotlin.math.abs(ClientRadioState.stationFrequency - frequency) < 0.05f) {
            val title = ClientRadioState.stationTitle
            font.plainSubstrByWidth(if (title.isEmpty()) "♫ $station" else "♫ $station: $title", w - 64)
        } else if (preset >= 0) "CH${preset + 1}" else "MANUAL"
        g.drawString(font, channel, x + 6, y + h - 11, LCD_INK, false)
        val cells = countBatteries()
        g.drawString(font, "CELLS:$cells", x + w - 50, y + h - 11, LCD_INK, false)
    }

    private fun drawSignalBars(g: GuiGraphics, x: Int, y: Int) {
        val bars = ClientRadioState.bars
        for (i in 0 until 4) {
            val barHeight = 2 + i * 2
            val color = if (i < bars) LCD_INK else LCD_FAINT
            g.fill(x + i * 5, y + 8 - barHeight, x + i * 5 + 3, y + 8, color)
        }
    }

    private fun drawBattery(g: GuiGraphics, x: Int, y: Int, pct: Int) {
        g.fill(x, y, x + 22, y + 10, LCD_INK)
        g.fill(x + 22, y + 3, x + 24, y + 7, LCD_INK)
        g.fill(x + 1, y + 1, x + 21, y + 9, LCD_ON)
        val fill = (19 * pct / 100).coerceIn(0, 19)
        if (fill > 0) g.fill(x + 2, y + 2, x + 2 + fill, y + 8, LCD_INK)
    }

    private fun drawMessageWell(g: GuiGraphics) {
        val x = left + 14
        val y = top + 140
        g.fill(x, y, x + 158, y + 22, RUBBER)
        g.fill(x + 1, y + 1, x + 157, y + 21, 0xFF141210.toInt())
    }

    private fun countBatteries(): Int {
        val inv = minecraft?.player?.inventory ?: return 0
        var count = 0
        for (i in 0 until inv.containerSize) {
            val stack = inv.getItem(i)
            if (stack.item is BatteryCellItem && BatteryCellItem.getEnergy(stack) > 0) count++
        }
        return count
    }

    /** Chunky keypad button drawn to match the radio faceplate. */
    private inner class RadioButton(
        x: Int, y: Int, w: Int, h: Int, label: String,
        private val accent: Boolean = false,
        private val action: () -> Unit
    ) : AbstractButton(x, y, w, h, Component.literal(label)) {

        override fun onPress() = action()

        override fun renderWidget(g: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
            val face = if (isHoveredOrFocused) KEY_HOVER else KEY
            g.fill(x, y + 2, x + width, y + height, 0xFF120F0D.toInt())
            g.fill(x, y, x + width, y + height - 2, face)
            if (accent) g.fill(x, y, x + width, y + 2, BODY_LIGHT)
            g.drawCenteredString(font, message, x + width / 2, y + (height - 2 - 8) / 2 + 1, KEY_TEXT)
        }

        override fun updateWidgetNarration(output: NarrationElementOutput) = defaultButtonNarrationText(output)
    }
}
