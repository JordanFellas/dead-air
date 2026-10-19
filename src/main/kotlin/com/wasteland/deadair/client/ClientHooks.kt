package com.wasteland.deadair.client

import com.wasteland.deadair.client.gui.RadioScreen
import net.minecraft.client.Minecraft

/** Client-only entry points called from common code behind an isClientSide check. */
object ClientHooks {
    fun openRadioScreen(slot: Int) {
        Minecraft.getInstance().setScreen(RadioScreen(slot))
    }
}
