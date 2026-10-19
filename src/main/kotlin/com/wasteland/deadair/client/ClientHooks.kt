package com.wasteland.deadair.client

import com.wasteland.deadair.client.gui.RadioScreen
import com.wasteland.deadair.client.gui.TunerScreen
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos

/** Client-only entry points called from common code behind an isClientSide check. */
object ClientHooks {
    fun openRadioScreen(slot: Int) {
        Minecraft.getInstance().setScreen(RadioScreen(slot))
    }

    fun openHomeRadio(pos: BlockPos) {
        Minecraft.getInstance().setScreen(TunerScreen.forHomeRadio(pos))
    }

    fun openBoombox(slot: Int) {
        Minecraft.getInstance().setScreen(TunerScreen.forBoombox(slot))
    }
}
