package com.wasteland.deadair.block

import com.wasteland.deadair.block.entity.RadioTowerControllerBlockEntity
import com.wasteland.deadair.block.entity.TowerScanner
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level

/**
 * Right-clicking any tower part with an empty hand opens the control panel of the controller it's
 * connected to, so a controller buried under a big tower is still easy to reach.
 */
object TowerAccess {
    fun open(level: Level, pos: BlockPos, player: Player): InteractionResult {
        if (!player.mainHandItem.isEmpty) return InteractionResult.PASS
        if (level.isClientSide) return InteractionResult.SUCCESS
        val controllerPos = TowerScanner.findController(level, pos) ?: return InteractionResult.PASS
        val be = level.getBlockEntity(controllerPos) as? RadioTowerControllerBlockEntity ?: return InteractionResult.PASS
        if (player is ServerPlayer) player.openMenu(be) { buf -> buf.writeBlockPos(controllerPos) }
        return InteractionResult.CONSUME
    }
}
