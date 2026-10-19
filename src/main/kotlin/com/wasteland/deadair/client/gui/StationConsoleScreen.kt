package com.wasteland.deadair.client.gui

import com.wasteland.deadair.block.entity.StationConsoleBlockEntity
import com.wasteland.deadair.client.ClientRadioState
import com.wasteland.deadair.client.sound.RadioSounds
import com.wasteland.deadair.menu.StationConsoleMenu
import com.wasteland.deadair.network.packets.PacketStationSettings
import com.wasteland.deadair.registry.ModSounds
import com.wasteland.deadair.station.StreamUrls
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractButton
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import net.neoforged.neoforge.network.PacketDistributor
import org.lwjgl.glfw.GLFW
import kotlin.math.roundToInt

/** Studio desk: call sign, frequency, playlist switches and the disc rack. */
class StationConsoleScreen(
    menu: StationConsoleMenu,
    playerInventory: Inventory,
    title: Component
) : AbstractContainerScreen<StationConsoleMenu>(menu, playerInventory, title) {

    companion object {
        private const val PANEL = 0xFF2A2D31.toInt()
        private const val PANEL_LIGHT = 0xFF3C4046.toInt()
        private const val PANEL_DARK = 0xFF17191B.toInt()
        private const val SLOT = 0xFF8B8B8B.toInt()
        private const val SLOT_DARK = 0xFF373737.toInt()
        private const val AMBER = 0xFFFFB43C.toInt()
        private const val GREEN = 0xFF7CFF6A.toInt()
        private const val RED = 0xFFFF4A3A.toInt()
        private const val DIM = 0xFF8A9096.toInt()
        private const val KEY = 0xFF1F2124.toInt()
        private const val KEY_HOVER = 0xFF33373C.toInt()
        private const val KEY_ON = 0xFF3B6E2F.toInt()
    }

    private lateinit var callSignBox: EditBox
    private lateinit var urlBox: EditBox
    /** The stream URL the server last got from us; the box is only sent when it differs. */
    private var sentUrl = menu.initialStreamUrl
    /** Local frequency so quick clicks add up before the server echoes it back. */
    private var frequency = -1.0f

    init {
        imageWidth = 176
        imageHeight = 236
        inventoryLabelY = StationConsoleMenu.INVENTORY_Y - 11
    }

    override fun init() {
        super.init()
        val x = leftPos
        val y = topPos

        callSignBox = EditBox(font, x + 34, y + 20, 64, 12, Component.literal("Call sign"))
        callSignBox.setMaxLength(StationConsoleBlockEntity.MAX_CALL_SIGN)
        callSignBox.value = menu.initialCallSign
        callSignBox.setTextColor(AMBER)
        callSignBox.setBordered(false)
        callSignBox.setResponder { send() }
        addRenderableWidget(callSignBox)

        addRenderableWidget(DeskButton(x + 120, y + 17, 48, 15, { if (onAir) "ON AIR" else "OFF AIR" }, { onAir }) {
            RadioSounds.playLocal(ModSounds.RADIO_CLICK_ON.get(), 0.5f, 1.0f)
            send(flags = menu.flags xor StationConsoleBlockEntity.FLAG_ON_AIR)
        })

        val steps = listOf(-1.0f to "-1", -0.1f to "-.1")
        steps.forEachIndexed { i, (delta, label) ->
            addRenderableWidget(DeskButton(x + 8 + i * 22, y + 36, 20, 14, { label }) { nudge(delta) })
        }
        listOf(0.1f to "+.1", 1.0f to "+1").forEachIndexed { i, (delta, label) ->
            addRenderableWidget(DeskButton(x + 126 + i * 22, y + 36, 20, 14, { label }) { nudge(delta) })
        }

        addRenderableWidget(DeskButton(x + 8, y + 54, 38, 14, { "DISCS" }, { flag(StationConsoleBlockEntity.FLAG_DISCS) }) {
            send(flags = menu.flags xor StationConsoleBlockEntity.FLAG_DISCS)
        })
        addRenderableWidget(DeskButton(x + 48, y + 54, 38, 14, { "SONGS" }, { flag(StationConsoleBlockEntity.FLAG_SONGS) }) {
            send(flags = menu.flags xor StationConsoleBlockEntity.FLAG_SONGS)
        })
        addRenderableWidget(DeskButton(x + 88, y + 54, 44, 14, { "SHUFFLE" }, { flag(StationConsoleBlockEntity.FLAG_SHUFFLE) }) {
            send(flags = menu.flags xor StationConsoleBlockEntity.FLAG_SHUFFLE)
        })
        addRenderableWidget(DeskButton(x + 134, y + 54, 34, 14, { "SKIP" }) {
            RadioSounds.playLocal(ModSounds.RADIO_BEEP.get(), 0.25f, 1.2f)
            send(skip = true)
        })
        addRenderableWidget(DeskButton(x + 8, y + 70, 62, 14, {
            if (menu.announceEvery == 0) "ID: OFF" else "ID: every ${menu.announceEvery}"
        }, { menu.announceEvery > 0 }) {
            send(announceEvery = (menu.announceEvery + 1) % 10)
        })
        addRenderableWidget(DeskButton(x + 72, y + 70, 44, 14, { "STREAM" }, { flag(StationConsoleBlockEntity.FLAG_STREAM) }) {
            RadioSounds.playLocal(ModSounds.RADIO_CLICK_ON.get(), 0.5f, 1.2f)
            send(flags = menu.flags xor StationConsoleBlockEntity.FLAG_STREAM)
        })

        urlBox = EditBox(font, x + 30, y + 89, 136, 12, Component.literal("Stream URL"))
        urlBox.setMaxLength(StreamUrls.MAX_LENGTH)
        urlBox.value = menu.initialStreamUrl
        urlBox.moveCursorToStart(false)
        urlBox.setTextColor(0xFF9DE0FF.toInt())
        urlBox.setBordered(false)
        urlBox.setHint(Component.literal("MP3 link or radio stream, Enter"))
        addRenderableWidget(urlBox)
    }

    override fun removed() {
        // Closing the console saves an edited URL too.
        if (::urlBox.isInitialized && urlBox.value.trim() != sentUrl) send()
        super.removed()
    }

    private val onAir: Boolean get() = flag(StationConsoleBlockEntity.FLAG_ON_AIR)

    private fun flag(bit: Int): Boolean = menu.flags and bit != 0

    private fun currentFrequency(): Float {
        if (frequency < 0 && menu.frequency > 0) frequency = menu.frequency
        return if (frequency < 0) menu.frequency else frequency
    }

    private fun nudge(delta: Float) {
        frequency = (((currentFrequency() + delta) * 10).roundToInt() / 10.0f).coerceIn(0.1f, 999.9f)
        RadioSounds.playLocal(ModSounds.RADIO_BEEP.get(), 0.2f, 1.6f)
        send()
    }

    private fun send(flags: Int = menu.flags, announceEvery: Int = menu.announceEvery, skip: Boolean = false) {
        if (!::callSignBox.isInitialized || !::urlBox.isInitialized) return
        val freq = currentFrequency()
        if (freq <= 0) return
        val url = urlBox.value.trim().takeIf { it != sentUrl }
        if (url != null) sentUrl = url
        PacketDistributor.sendToServer(PacketStationSettings(menu.pos, callSignBox.value, freq, flags, announceEvery, skip, url))
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) return super.keyPressed(keyCode, scanCode, modifiers)
        val box = listOf(callSignBox, urlBox).firstOrNull { it.isFocused }
        if (box != null) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                box.isFocused = false
                if (box == urlBox) {
                    RadioSounds.playLocal(ModSounds.RADIO_BEEP.get(), 0.25f, 1.0f)
                    // Saving a link switches the station to it.
                    val flags = if (urlBox.value.isNotBlank()) menu.flags or StationConsoleBlockEntity.FLAG_STREAM else menu.flags
                    send(flags = flags)
                }
                return true
            }
            // Typing owns the keyboard: no hotkey (inventory, drop...) steals a letter.
            box.keyPressed(keyCode, scanCode, modifiers)
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun render(g: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(g, mouseX, mouseY, partialTick)
        renderTooltip(g, mouseX, mouseY)
    }

    override fun renderBg(g: GuiGraphics, partialTick: Float, mouseX: Int, mouseY: Int) {
        val x = leftPos
        val y = topPos
        g.fill(x - 2, y - 2, x + imageWidth + 2, y + imageHeight + 2, PANEL_DARK)
        g.fill(x, y, x + imageWidth, y + imageHeight, PANEL)
        g.fill(x, y, x + imageWidth, y + 2, PANEL_LIGHT)

        // Call sign plate
        g.fill(x + 30, y + 17, x + 102, y + 33, PANEL_DARK)

        // Frequency LCD
        g.fill(x + 52, y + 35, x + 124, y + 51, PANEL_DARK)

        // Stream URL plate
        g.fill(x + 26, y + 86, x + 169, y + 100, PANEL_DARK)

        // Info screen
        g.fill(x + 7, y + 102, x + 169, y + 123, PANEL_DARK)

        // Disc rack and inventory slots
        for (i in 0 until StationConsoleBlockEntity.DISC_SLOTS) drawSlot(g, x + 7 + i * 18, y + StationConsoleMenu.DISC_ROW_Y - 1)
        for (row in 0..2) for (col in 0..8) drawSlot(g, x + 7 + col * 18, y + StationConsoleMenu.INVENTORY_Y - 1 + row * 18)
        for (col in 0..8) drawSlot(g, x + 7 + col * 18, y + StationConsoleMenu.INVENTORY_Y + 57)
    }

    private fun drawSlot(g: GuiGraphics, x: Int, y: Int) {
        g.fill(x, y, x + 18, y + 18, SLOT_DARK)
        g.fill(x + 1, y + 1, x + 18, y + 18, 0xFFFFFFFF.toInt())
        g.fill(x + 1, y + 1, x + 17, y + 17, SLOT)
    }

    override fun renderLabels(g: GuiGraphics, mouseX: Int, mouseY: Int) {
        g.drawString(font, "STATION CONSOLE", 8, 6, DIM, false)
        val (statusText, statusColor) = when (menu.status) {
            StationConsoleBlockEntity.STATUS_ON_AIR -> "● LIVE" to RED
            StationConsoleBlockEntity.STATUS_NO_TOWER -> "NO TOWER" to AMBER
            else -> "OFF AIR" to DIM
        }
        g.drawString(font, statusText, imageWidth - 8 - font.width(statusText), 6, statusColor, false)
        g.drawString(font, "CALL", 8, 21, DIM, false)

        g.drawCenteredString(font, "%.1f MHz".format(currentFrequency().coerceAtLeast(0.0f)), 88, 39, AMBER)

        // Mic / announcement status next to the STREAM switch
        val micText = when {
            menu.recording -> "● REC" to RED
            menu.hasAnnouncement -> "ID saved" to GREEN
            else -> "No ID" to DIM
        }
        g.drawString(font, micText.first, 120, 73, micText.second, false)
        g.drawString(font, "URL", 8, 89, if (menu.streamUrlsEnabled) DIM else 0xFF5A5F64.toInt(), false)

        // Now playing + listeners
        val nowPlaying = ClientRadioState.stationTitle.takeIf { menu.status == StationConsoleBlockEntity.STATUS_ON_AIR && it.isNotEmpty() }
        val line1 = if (nowPlaying != null) {
            if (nowPlaying.startsWith("⚠")) nowPlaying else "♫ $nowPlaying"
        } else when (menu.status) {
            StationConsoleBlockEntity.STATUS_ON_AIR -> "Load discs or songs to play"
            StationConsoleBlockEntity.STATUS_NO_TOWER -> "Build a powered tower nearby"
            else -> "Off the air"
        }
        g.drawString(font, font.plainSubstrByWidth(line1, 156), 10, 105, if (line1.startsWith("⚠")) AMBER else GREEN, false)
        val songs = when {
            !menu.voiceChat -> "songs need Voice Chat"
            flag(StationConsoleBlockEntity.FLAG_STREAM) && urlBox.value.isNotBlank() -> "streaming"
            menu.songCount == 1 -> "1 song"
            else -> "${menu.songCount} songs"
        }
        g.drawString(font, "${menu.listeners} listening · $songs", 10, 114, DIM, false)

        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, DIM, false)
    }

    /** Flat desk switch; lit green when its setting is on. */
    private inner class DeskButton(
        x: Int, y: Int, w: Int, h: Int,
        private val label: () -> String,
        private val lit: (() -> Boolean)? = null,
        private val action: () -> Unit
    ) : AbstractButton(x, y, w, h, Component.empty()) {
        override fun onPress() = action()

        override fun renderWidget(g: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
            val on = lit?.invoke() == true
            g.fill(x, y + 1, x + width, y + height, 0xFF0B0C0D.toInt())
            g.fill(x, y, x + width, y + height - 1, if (on) KEY_ON else if (isHoveredOrFocused) KEY_HOVER else KEY)
            g.drawCenteredString(font, label(), x + width / 2, y + (height - 8) / 2, if (on) 0xFFE8FFE0.toInt() else 0xFFC8CCD0.toInt())
        }

        override fun updateWidgetNarration(output: NarrationElementOutput) = defaultButtonNarrationText(output)
    }
}
