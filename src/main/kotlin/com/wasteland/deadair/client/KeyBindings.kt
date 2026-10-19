package com.wasteland.deadair.client

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.KeyMapping
import org.lwjgl.glfw.GLFW

object KeyBindings {
    const val KEY_CATEGORY_DEAD_AIR = "key.categories.dead_air"

    val KEY_RADIO_CHAT = KeyMapping(
        "key.dead_air.radio_chat",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_R,
        KEY_CATEGORY_DEAD_AIR
    )
}
