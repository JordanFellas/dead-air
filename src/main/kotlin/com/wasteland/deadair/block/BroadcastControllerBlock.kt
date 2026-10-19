package com.wasteland.deadair.block

import com.mojang.serialization.MapCodec

/**
 * The heavy-duty controller for Mega Towers: ten times the energy buffer and faster battery
 * charging. It sits in the center of a 5x5 pad of Tower Foundation blocks.
 */
class BroadcastControllerBlock(properties: Properties) : RadioTowerControllerBlock(properties) {

    companion object {
        val CODEC: MapCodec<BroadcastControllerBlock> = simpleCodec(::BroadcastControllerBlock)
    }

    override fun codec(): MapCodec<BroadcastControllerBlock> = CODEC
}
