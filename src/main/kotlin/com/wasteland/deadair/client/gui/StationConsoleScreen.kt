package com.wasteland.deadair.client.gui

import com.wasteland.deadair.block.entity.StationConsoleBlockEntity
import com.wasteland.deadair.client.ClientRadioState
import com.wasteland.deadair.client.ClientSongUploader
import com.wasteland.deadair.client.sound.RadioSounds
import com.wasteland.deadair.menu.StationConsoleMenu
import com.wasteland.deadair.network.packets.PacketStationSettings
import com.wasteland.deadair.registry.ModSounds
import com.wasteland.deadair.station.StreamUrls
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractButton
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import net.neoforged.neoforge.network.PacketDistributor
import org.lwjgl.glfw.GLFW
import kotlin.math.roundToInt

/**
 * The studio desk, in three tabs:
 * - STUDIO: what's happening, a getting-started checklist, START/STOP, NEXT, PLAY ID NOW and
 *   where the music comes from (playlist or live stream).
 * - MUSIC: add songs (a file on this computer, a web link, or BROWSE), and the playlist.
 * - SETUP: station name, frequency, station ID and the live stream link.
 * The disc rack and your inventory stay below every tab.
 */
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
        private const val TEXT = 0xFFD8DCE0.toInt()
        private const val BLUE = 0xFF9DE0FF.toInt()
        private const val KEY = 0xFF1F2124.toInt()
        private const val KEY_HOVER = 0xFF33373C.toInt()
        private const val KEY_ON = 0xFF3B6E2F.toInt()
        private const val KEY_GO = 0xFF2F7A2F.toInt()
        private const val KEY_STOP = 0xFF8A2A22.toInt()

        private const val TAB_STUDIO = 0
        private const val TAB_MUSIC = 1
        private const val TAB_SETUP = 2

        private const val CONTENT_TOP = 30
        private const val CONTENT_BOTTOM = 131
        private const val LIST_TOP = 66
        private const val LIST_ROWS = 5
        private const val ROW_H = 10
        private const val NOTICE_MS = 6000L
        private const val DEFAULT_CALL_SIGN = "KDAR"

        /** The tab you were last on, so reopening the console feels the same. */
        private var lastTab = TAB_STUDIO
    }

    private var tab = lastTab
    private val tabWidgets = HashMap<Int, MutableList<AbstractWidget>>()

    private lateinit var callSignBox: EditBox
    private lateinit var streamBox: EditBox
    private lateinit var addBox: EditBox

    /** The stream link the server last got from us; the box is only sent when it differs. */
    private var sentUrl = menu.initialStreamUrl
    /** Local frequency so quick clicks add up before the server echoes it back. */
    private var frequency = -1.0f
    private var scroll = 0
    /** A song whose ✕ was clicked once; a second click removes it. */
    private var pendingRemove: String? = null

    init {
        imageWidth = 176
        imageHeight = 254
        inventoryLabelY = StationConsoleMenu.INVENTORY_Y - 10
    }

    // ---------------------------------------------------------------- layout

    override fun init() {
        super.init()
        tabWidgets.clear()
        val x = leftPos
        val y = topPos

        // Tabs (always visible)
        listOf("STUDIO" to TAB_STUDIO, "MUSIC" to TAB_MUSIC, "SETUP" to TAB_SETUP).forEachIndexed { i, (label, id) ->
            addRenderableWidget(TabButton(x + 5 + i * 56, y + 16, 54, 14, label, id))
        }

        // ---- STUDIO ----
        add(TAB_STUDIO, DeskButton(x + 8, y + 78, 160, 18, {
            if (onAir) "\u25A0  STOP BROADCAST" else "\u25B6  START BROADCAST"
        }, color = { if (onAir) KEY_STOP else KEY_GO }, tip = {
            if (onAir) "Take the station off the air" else "Put the station on the air so radios can hear it"
        }) {
            RadioSounds.playLocal(if (onAir) ModSounds.RADIO_CLICK_OFF.get() else ModSounds.RADIO_CLICK_ON.get(), 0.6f, 1.0f)
            send(flags = menu.flags xor StationConsoleBlockEntity.FLAG_ON_AIR)
        })
        add(TAB_STUDIO, DeskButton(x + 8, y + 99, 78, 15, { "\u25B6\u25B6 NEXT SONG" }, tip = { "Skip to the next song in the playlist" }) {
            RadioSounds.playLocal(ModSounds.RADIO_BEEP.get(), 0.25f, 1.2f)
            send(action = PacketStationSettings.ACTION_SKIP)
        })
        add(TAB_STUDIO, DeskButton(x + 90, y + 99, 78, 15, { "PLAY ID NOW" }, tip = {
            if (menu.hasAnnouncement) "Play your recorded station ID right now"
            else "No station ID yet: right-click a Studio Mic near this console to record one"
        }) {
            RadioSounds.playLocal(ModSounds.RADIO_BEEP.get(), 0.25f, 0.9f)
            send(action = PacketStationSettings.ACTION_PLAY_ID)
        })
        add(TAB_STUDIO, DeskButton(x + 62, y + 116, 52, 13, { "PLAYLIST" }, lit = { !streamMode },
            tip = { "Play your songs and discs (MUSIC tab)" }) {
            send(flags = menu.flags and StationConsoleBlockEntity.FLAG_STREAM.inv())
        })
        add(TAB_STUDIO, DeskButton(x + 116, y + 116, 52, 13, { "LIVE STREAM" }, lit = { streamMode },
            tip = { "Play an internet radio stream (set the link in SETUP)" }) {
            if (!menu.hasStreamUrl && streamBox.value.isBlank()) {
                ClientRadioState.showNotice("Add a live stream link in SETUP first", true)
                switchTab(TAB_SETUP)
                setFocused(streamBox)
            } else {
                send(flags = menu.flags or StationConsoleBlockEntity.FLAG_STREAM)
            }
        })

        // ---- MUSIC ----
        addBox = EditBox(font, x + 11, y + 37, 112, 10, Component.literal("Add a song"))
        addBox.setMaxLength(1024)
        addBox.setBordered(false)
        addBox.setTextColor(BLUE)
        addBox.setHint(Component.literal("Paste MP3 file or link"))
        addBox.setTooltip(Tooltip.create(Component.literal(
            "Paste a song file from your computer (C:\\...\\song.mp3), a web link to an MP3 " +
                "(Google Drive and Dropbox links work), or a live radio link, then press ADD"
        )))
        add(TAB_MUSIC, addBox)
        add(TAB_MUSIC, DeskButton(x + 130, y + 33, 38, 15, { "ADD" }, color = { KEY_GO }, tip = { "Add what you pasted" }) { addSong() })
        add(TAB_MUSIC, DeskButton(x + 8, y + 50, 62, 13, { "BROWSE PC..." }, tip = { "Pick a song file from your computer" }) {
            ClientSongUploader.browse { startUpload(it) }
        })
        add(TAB_MUSIC, DeskButton(x + 72, y + 50, 46, 13, { if (flag(StationConsoleBlockEntity.FLAG_DISCS)) "DISCS: ON" else "DISCS: OFF" },
            lit = { flag(StationConsoleBlockEntity.FLAG_DISCS) }, tip = { "Play the music discs in the rack below" }) {
            send(flags = menu.flags xor StationConsoleBlockEntity.FLAG_DISCS)
        })
        add(TAB_MUSIC, DeskButton(x + 120, y + 50, 48, 13, { "SHUFFLE" }, lit = { flag(StationConsoleBlockEntity.FLAG_SHUFFLE) },
            tip = { "Play songs in a random order" }) {
            send(flags = menu.flags xor StationConsoleBlockEntity.FLAG_SHUFFLE)
        })

        // ---- SETUP ----
        callSignBox = EditBox(font, x + 86, y + 36, 80, 10, Component.literal("Station name"))
        callSignBox.setMaxLength(StationConsoleBlockEntity.MAX_CALL_SIGN)
        callSignBox.value = menu.initialCallSign
        callSignBox.setTextColor(AMBER)
        callSignBox.setBordered(false)
        callSignBox.setResponder { send() }
        callSignBox.setTooltip(Tooltip.create(Component.literal("Your station's name, like KIIS. Radios show it when tuned in.")))
        add(TAB_SETUP, callSignBox)

        add(TAB_SETUP, DeskButton(x + 84, y + 50, 16, 13, { "-" }, tip = { "Lower the frequency by 0.1 (hold Shift: by 1)" }) {
            nudge(if (Screen.hasShiftDown()) -1.0f else -0.1f)
        })
        add(TAB_SETUP, DeskButton(x + 152, y + 50, 16, 13, { "+" }, tip = { "Raise the frequency by 0.1 (hold Shift: by 1)" }) {
            nudge(if (Screen.hasShiftDown()) 1.0f else 0.1f)
        })
        add(TAB_SETUP, DeskButton(x + 84, y + 66, 16, 13, { "-" }, tip = { "Play the station ID less often" }) {
            send(announceEvery = (menu.announceEvery - 1).coerceAtLeast(0))
        })
        add(TAB_SETUP, DeskButton(x + 152, y + 66, 16, 13, { "+" }, tip = { "Play the station ID more often" }) {
            send(announceEvery = if (menu.announceEvery == 0) 3 else (menu.announceEvery + 1).coerceAtMost(9))
        })

        streamBox = EditBox(font, x + 11, y + 109, 154, 10, Component.literal("Live stream link"))
        streamBox.setMaxLength(StreamUrls.MAX_LENGTH)
        streamBox.value = menu.initialStreamUrl
        streamBox.moveCursorToStart(false)
        streamBox.setTextColor(BLUE)
        streamBox.setBordered(false)
        streamBox.setHint(Component.literal("https://... internet radio (MP3)"))
        streamBox.setTooltip(Tooltip.create(Component.literal(
            "An internet radio stream link. Press Enter to save it and play it live. To add single songs, use the MUSIC tab instead."
        )))
        add(TAB_SETUP, streamBox)

        switchTab(tab)
    }

    private fun add(tabId: Int, widget: AbstractWidget) {
        tabWidgets.getOrPut(tabId) { ArrayList() }.add(widget)
        addRenderableWidget(widget)
    }

    private fun switchTab(id: Int) {
        tab = id
        lastTab = id
        pendingRemove = null
        for ((tabId, widgets) in tabWidgets) for (w in widgets) {
            w.visible = tabId == id
            w.active = tabId == id
            if (tabId != id && w is EditBox) w.isFocused = false
        }
    }

    // ---------------------------------------------------------------- state

    private val onAir: Boolean get() = flag(StationConsoleBlockEntity.FLAG_ON_AIR)
    private val streamMode: Boolean get() = flag(StationConsoleBlockEntity.FLAG_STREAM)
    private val isLive: Boolean get() = menu.status == StationConsoleBlockEntity.STATUS_ON_AIR
    private val hasMusic: Boolean get() = menu.discCount > 0 || menu.songCount > 0 || (streamMode && menu.hasStreamUrl)
    private val nowPlaying: String get() = ClientRadioState.stationTitle.takeIf { isLive }.orEmpty()

    private fun flag(bit: Int): Boolean = menu.flags and bit != 0

    private fun currentFrequency(): Float {
        if (frequency < 0 && menu.frequency > 0) frequency = menu.frequency
        return if (frequency < 0) menu.frequency else frequency
    }

    // ---------------------------------------------------------------- actions

    private fun nudge(delta: Float) {
        frequency = (((currentFrequency() + delta) * 10).roundToInt() / 10.0f).coerceIn(0.1f, 999.9f)
        RadioSounds.playLocal(ModSounds.RADIO_BEEP.get(), 0.2f, 1.6f)
        send()
    }

    private fun send(
        flags: Int = menu.flags,
        announceEvery: Int = menu.announceEvery,
        action: Int = PacketStationSettings.ACTION_NONE,
        arg: String = ""
    ) {
        if (!::callSignBox.isInitialized || !::streamBox.isInitialized) return
        val freq = currentFrequency()
        if (freq <= 0) return
        val url = streamBox.value.trim().takeIf { it != sentUrl }
        if (url != null) sentUrl = url
        // Songs are always part of the playlist in this layout.
        val allFlags = flags or StationConsoleBlockEntity.FLAG_SONGS
        PacketDistributor.sendToServer(PacketStationSettings(menu.pos, callSignBox.value, freq, allFlags, announceEvery, url, action, arg))
    }

    /** ADD: a file on this computer gets uploaded; anything else is a web link for the server. */
    private fun addSong() {
        val text = addBox.value.trim()
        if (text.isEmpty()) {
            ClientRadioState.showNotice("Paste a song file path or a link first, or press BROWSE PC", true)
            return
        }
        val local = ClientSongUploader.localPath(text)
        when {
            local != null -> startUpload(local)
            text.startsWith("http://", true) || text.startsWith("https://", true) -> {
                send(action = PacketStationSettings.ACTION_ADD_LINK, arg = text)
                addBox.value = ""
            }
            else -> ClientRadioState.showNotice("That isn't a file on your computer or a web link", true)
        }
    }

    private fun startUpload(file: java.nio.file.Path) {
        val problem = ClientSongUploader.start(menu.pos, file)
        if (problem != null) {
            ClientRadioState.showNotice(problem, true)
        } else {
            addBox.value = ""
            ClientRadioState.showNotice("Sending ${file.fileName}...", false)
        }
    }

    private fun saveStreamLink() {
        val url = streamBox.value.trim()
        streamBox.isFocused = false
        if (url.isEmpty()) {
            send(flags = menu.flags and StationConsoleBlockEntity.FLAG_STREAM.inv())
            ClientRadioState.showNotice("Stream link cleared, back to the playlist", false)
        } else {
            send(flags = menu.flags or StationConsoleBlockEntity.FLAG_STREAM)
            ClientRadioState.showNotice("Stream link saved, playing it live", false)
        }
    }

    override fun removed() {
        // Closing the console saves an edited stream link too.
        if (::streamBox.isInitialized && streamBox.value.trim() != sentUrl) send()
        super.removed()
    }

    // ---------------------------------------------------------------- input

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) return super.keyPressed(keyCode, scanCode, modifiers)
        val box = listOf(callSignBox, streamBox, addBox).firstOrNull { it.isFocused && it.visible }
        if (box != null) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                when (box) {
                    addBox -> addSong()
                    streamBox -> saveStreamLink()
                    else -> box.isFocused = false
                }
                return true
            }
            // Typing owns the keyboard: no hotkey (inventory, drop...) steals a letter.
            box.keyPressed(keyCode, scanCode, modifiers)
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (tab == TAB_MUSIC && button == 0) {
            val row = rowAt(mouseX, mouseY)
            if (row != null) {
                val entry = ClientRadioState.playlist[row]
                if (entry.removable && mouseX >= leftPos + 158) {
                    if (pendingRemove == entry.id) {
                        send(action = PacketStationSettings.ACTION_REMOVE_ENTRY, arg = entry.id)
                        pendingRemove = null
                    } else {
                        pendingRemove = entry.id
                        ClientRadioState.showNotice("Click \u2715 again to remove \"${entry.title}\"", true)
                    }
                } else {
                    pendingRemove = null
                    RadioSounds.playLocal(ModSounds.RADIO_BEEP.get(), 0.2f, 1.3f)
                    send(action = PacketStationSettings.ACTION_PLAY_ENTRY, arg = entry.id)
                }
                return true
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (tab == TAB_MUSIC && mouseY >= topPos + LIST_TOP && mouseY < topPos + LIST_TOP + LIST_ROWS * ROW_H) {
            val max = (ClientRadioState.playlist.size - LIST_ROWS).coerceAtLeast(0)
            scroll = (scroll - scrollY.toInt()).coerceIn(0, max)
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    private fun rowAt(mouseX: Double, mouseY: Double): Int? {
        val listX = leftPos + 8
        val listY = topPos + LIST_TOP
        if (mouseX < listX || mouseX >= listX + 160 || mouseY < listY || mouseY >= listY + LIST_ROWS * ROW_H) return null
        val index = scroll + ((mouseY - listY) / ROW_H).toInt()
        return index.takeIf { it < ClientRadioState.playlist.size }
    }

    // ---------------------------------------------------------------- drawing

    override fun render(g: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(g, mouseX, mouseY, partialTick)
        if (tab == TAB_MUSIC) rowAt(mouseX.toDouble(), mouseY.toDouble())?.let { row ->
            val entry = ClientRadioState.playlist[row]
            val tip = if (entry.removable && mouseX >= leftPos + 158) "Remove this song" else "Play \"${entry.title}\" now"
            g.renderTooltip(font, Component.literal(tip), mouseX, mouseY)
        }
        renderTooltip(g, mouseX, mouseY)
    }

    override fun renderBg(g: GuiGraphics, partialTick: Float, mouseX: Int, mouseY: Int) {
        val x = leftPos
        val y = topPos
        g.fill(x - 2, y - 2, x + imageWidth + 2, y + imageHeight + 2, PANEL_DARK)
        g.fill(x, y, x + imageWidth, y + imageHeight, PANEL)
        g.fill(x, y, x + imageWidth, y + 2, PANEL_LIGHT)
        // Tab page
        g.fill(x + 4, y + CONTENT_TOP, x + 172, y + CONTENT_BOTTOM, PANEL_LIGHT)

        when (tab) {
            TAB_STUDIO -> g.fill(x + 8, y + 34, x + 168, y + 75, PANEL_DARK)
            TAB_MUSIC -> {
                g.fill(x + 8, y + 33, x + 127, y + 48, PANEL_DARK)
                g.fill(x + 8, y + LIST_TOP - 1, x + 168, y + LIST_TOP + LIST_ROWS * ROW_H + 1, PANEL_DARK)
            }
            TAB_SETUP -> {
                g.fill(x + 82, y + 33, x + 168, y + 48, PANEL_DARK)
                g.fill(x + 102, y + 50, x + 150, y + 63, PANEL_DARK)
                g.fill(x + 102, y + 66, x + 150, y + 79, PANEL_DARK)
                g.fill(x + 8, y + 106, x + 168, y + 121, PANEL_DARK)
            }
        }

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
        // Header: station name, frequency, and a status light
        val name = callSignBox.value.ifBlank { DEFAULT_CALL_SIGN }
        g.drawString(font, name, 8, 5, AMBER, false)
        g.drawString(font, "%.1f MHz".format(currentFrequency().coerceAtLeast(0f)), 12 + font.width(name), 5, DIM, false)
        val (statusText, statusColor) = when {
            isLive -> "\u25CF LIVE" to RED
            onAir -> "NO TOWER" to AMBER
            else -> "OFF AIR" to DIM
        }
        g.drawString(font, statusText, imageWidth - 8 - font.width(statusText), 5, statusColor, false)

        when (tab) {
            TAB_STUDIO -> drawStudio(g)
            TAB_MUSIC -> drawMusic(g)
            TAB_SETUP -> drawSetup(g)
        }
        drawNotice(g)

        g.drawString(font, "DISC RACK", 8, StationConsoleMenu.DISC_ROW_Y - 9, DIM, false)
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, DIM, false)
    }

    private fun drawStudio(g: GuiGraphics) {
        val playing = nowPlaying
        if (isLive && playing.isNotEmpty() && !playing.startsWith("\u26A0")) {
            // On the air with something playing
            g.drawString(font, "\u25CF ON THE AIR \u00B7 ${menu.listeners} listening", 12, 38, RED, false)
            g.drawString(font, font.plainSubstrByWidth("\u266B $playing", 152), 12, 50, GREEN, false)
            g.drawString(font, if (streamMode) "Playing your live stream" else "Playing your playlist", 12, 62, DIM, false)
        } else if (isLive) {
            // On the air but nothing coming out: say why
            g.drawString(font, "\u25CF ON THE AIR \u00B7 ${menu.listeners} listening", 12, 38, RED, false)
            val why = when {
                playing.startsWith("\u26A0") -> playing
                streamMode && !menu.hasStreamUrl -> "No stream link: add one in SETUP"
                !hasMusic && !menu.voiceChat -> "Nothing to play: put discs in the rack"
                !hasMusic -> "Nothing to play: add songs in MUSIC"
                else -> "Starting the next song..."
            }
            drawWrapped(g, why, 12, 50, 152, AMBER, 2)
        } else {
            // Getting started checklist
            g.drawString(font, "GETTING STARTED", 12, 37, TEXT, false)
            checklistLine(g, 47, menu.hasTower, "Powered radio tower nearby")
            checklistLine(g, 56, hasMusic, "Music added (MUSIC tab)")
            checklistLine(g, 65, onAir, "Press START BROADCAST")
        }
        g.drawString(font, "PLAY FROM", 8, 119, DIM, false)
    }

    private fun checklistLine(g: GuiGraphics, y: Int, done: Boolean, text: String) {
        g.drawString(font, if (done) "\u2714" else "\u2716", 14, y, if (done) GREEN else RED, false)
        g.drawString(font, text, 24, y, if (done) DIM else TEXT, false)
    }

    private fun drawMusic(g: GuiGraphics) {
        val list = ClientRadioState.playlist
        scroll = scroll.coerceIn(0, (list.size - LIST_ROWS).coerceAtLeast(0))
        if (list.isEmpty()) {
            drawWrapped(g, "No music yet. Paste a song file or link above and press ADD, or press BROWSE PC. Music discs go in the rack below.", 12, LIST_TOP + 1, 152, DIM, 5)
        }
        for (i in 0 until LIST_ROWS) {
            val index = scroll + i
            val entry = list.getOrNull(index) ?: break
            val rowY = LIST_TOP + i * ROW_H
            val current = entry.id == ClientRadioState.playlistCurrent && isLive
            if (current) g.fill(8, rowY, 168, rowY + ROW_H, 0xFF22381E.toInt())
            val color = when {
                current -> GREEN
                entry.id.startsWith("disc:") -> AMBER
                else -> TEXT
            }
            val prefix = if (current) "\u25B6 " else ""
            g.drawString(font, font.plainSubstrByWidth(prefix + entry.title, if (entry.removable) 142 else 152), 11, rowY + 1, color, false)
            if (entry.removable) {
                g.drawString(font, "\u2715", 160, rowY + 1, if (pendingRemove == entry.id) RED else DIM, false)
            }
        }
        if (list.size > LIST_ROWS) {
            // Scroll bar
            val trackH = LIST_ROWS * ROW_H
            val barH = (trackH * LIST_ROWS / list.size).coerceAtLeast(6)
            val barY = LIST_TOP + (trackH - barH) * scroll / (list.size - LIST_ROWS)
            g.fill(166, barY, 168, barY + barH, DIM)
        }

        val status = when {
            ClientSongUploader.isUploading -> "Sending ${ClientSongUploader.fileName}... ${(ClientSongUploader.progress * 100).toInt()}%"
            else -> {
                val songs = if (menu.songCount == 1) "1 song" else "${menu.songCount} songs"
                val discs = if (menu.discCount == 1) "1 disc" else "${menu.discCount} discs"
                "$songs \u00B7 $discs \u00B7 click a song to play it"
            }
        }
        g.drawString(font, font.plainSubstrByWidth(status, 160), 8, 120, if (ClientSongUploader.isUploading) BLUE else DIM, false)
        if (ClientSongUploader.isUploading) {
            g.fill(8, 129, 168, 130, PANEL_DARK)
            g.fill(8, 129, 8 + (160 * ClientSongUploader.progress).toInt(), 130, BLUE)
        }
    }

    private fun drawSetup(g: GuiGraphics) {
        g.drawString(font, "Station name", 8, 37, TEXT, false)
        g.drawString(font, "Frequency", 8, 53, TEXT, false)
        g.drawCenteredString(font, "%.1f".format(currentFrequency().coerceAtLeast(0f)), 126, 53, AMBER)
        g.drawString(font, "Station ID", 8, 69, TEXT, false)
        g.drawCenteredString(font, if (menu.announceEvery == 0) "off" else "every ${menu.announceEvery}", 126, 69, AMBER)
        val idStatus = when {
            menu.recording -> "\u25CF Recording at the Studio Mic..." to RED
            menu.hasAnnouncement -> "\u2714 ID recorded. Right-click a Studio Mic to redo it" to DIM
            else -> "No ID yet. Right-click a Studio Mic to record one" to DIM
        }
        drawWrapped(g, idStatus.first, 8, 83, 160, idStatus.second, 1)
        g.drawString(font, "Live stream link", 8, 96, TEXT, false)
        g.drawString(font, "Press Enter to play it live", 8, 123, DIM, false)
    }

    /** A message from the server (song added, link failed...) for a few seconds, over the top of the page. */
    private fun drawNotice(g: GuiGraphics) {
        val message = ClientRadioState.notice
        if (message.isEmpty() || System.currentTimeMillis() - ClientRadioState.noticeTime > NOTICE_MS) return
        val lines = font.split(Component.literal(message), 156).take(2)
        val top = CONTENT_BOTTOM - 4 - lines.size * 9
        g.pose().pushPose()
        g.pose().translate(0f, 0f, 300f)
        g.fill(6, top - 2, 170, CONTENT_BOTTOM - 2, 0xF0101214.toInt())
        g.fill(6, top - 2, 7, CONTENT_BOTTOM - 2, if (ClientRadioState.noticeError) RED else GREEN)
        lines.forEachIndexed { i, line ->
            g.drawString(font, line, 10, top + i * 9, if (ClientRadioState.noticeError) 0xFFFFB0A8.toInt() else 0xFFC8FFC0.toInt(), false)
        }
        g.pose().popPose()
    }

    private fun drawWrapped(g: GuiGraphics, text: String, x: Int, y: Int, width: Int, color: Int, maxLines: Int) {
        font.split(Component.literal(text), width).take(maxLines).forEachIndexed { i, line ->
            g.drawString(font, line, x, y + i * 9, color, false)
        }
    }

    // ---------------------------------------------------------------- widgets

    /** Flat desk switch; lit green when its setting is on. */
    private inner class DeskButton(
        x: Int, y: Int, w: Int, h: Int,
        private val label: () -> String,
        private val lit: (() -> Boolean)? = null,
        private val color: (() -> Int)? = null,
        private val tip: (() -> String)? = null,
        private val action: () -> Unit
    ) : AbstractButton(x, y, w, h, Component.empty()) {
        override fun onPress() = action()

        override fun renderWidget(g: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
            tip?.let { setTooltip(Tooltip.create(Component.literal(it()))) }
            val on = lit?.invoke() == true
            val base = color?.invoke() ?: if (on) KEY_ON else KEY
            g.fill(x, y + 1, x + width, y + height, 0xFF0B0C0D.toInt())
            g.fill(x, y, x + width, y + height - 1, if (isHoveredOrFocused) brighten(base) else base)
            val textColor = if (on || color != null) 0xFFF0FFF0.toInt() else 0xFFC8CCD0.toInt()
            g.drawCenteredString(font, label(), x + width / 2, y + (height - 8) / 2, textColor)
        }

        private fun brighten(c: Int): Int {
            val r = minOf(255, ((c shr 16) and 0xFF) + 24)
            val gr = minOf(255, ((c shr 8) and 0xFF) + 24)
            val b = minOf(255, (c and 0xFF) + 24)
            return (0xFF shl 24) or (r shl 16) or (gr shl 8) or b
        }

        override fun updateWidgetNarration(output: NarrationElementOutput) = defaultButtonNarrationText(output)
    }

    private inner class TabButton(x: Int, y: Int, w: Int, h: Int, label: String, private val id: Int) :
        AbstractButton(x, y, w, h, Component.literal(label)) {
        override fun onPress() {
            RadioSounds.playLocal(ModSounds.RADIO_BEEP.get(), 0.15f, 1.8f)
            switchTab(id)
        }

        override fun renderWidget(g: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
            val selected = tab == id
            g.fill(x, y, x + width, y + height, if (selected) PANEL_LIGHT else if (isHovered) KEY_HOVER else KEY)
            g.drawCenteredString(font, message, x + width / 2, y + 3, if (selected) AMBER else DIM)
        }

        override fun updateWidgetNarration(output: NarrationElementOutput) = defaultButtonNarrationText(output)
    }
}
