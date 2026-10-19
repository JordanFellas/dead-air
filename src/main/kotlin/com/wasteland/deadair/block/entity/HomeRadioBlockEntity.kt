package com.wasteland.deadair.block.entity

import com.wasteland.deadair.block.HomeRadioBlock
import com.wasteland.deadair.registry.ModBlockEntities
import com.wasteland.deadair.station.StationManager
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import kotlin.math.roundToInt

/** A plugged-in tabletop radio that plays whatever station is on its frequency, out loud. */
class HomeRadioBlockEntity(pos: BlockPos, state: BlockState) :
    BlockEntity(ModBlockEntities.HOME_RADIO_BE.get(), pos, state) {

    var frequency: Float = 99.9f
        private set
    var isOn: Boolean = true
        private set

    fun applySettings(frequency: Float, on: Boolean) {
        this.frequency = ((frequency * 10).roundToInt() / 10.0f).coerceIn(0.1f, 999.9f)
        isOn = on
        setChanged()
        val state = blockState
        if (state.hasProperty(HomeRadioBlock.ON) && state.getValue(HomeRadioBlock.ON) != on) {
            level?.setBlock(worldPosition, state.setValue(HomeRadioBlock.ON, on), 3)
        } else {
            level?.sendBlockUpdated(worldPosition, state, state, 3)
        }
    }

    override fun onLoad() {
        super.onLoad()
        if (level?.isClientSide == false) StationManager.addHomeRadio(this)
    }

    override fun setRemoved() {
        super.setRemoved()
        val lvl = level
        if (lvl != null && !lvl.isClientSide) StationManager.removeHomeRadio(lvl.dimension().location(), worldPosition)
    }

    override fun saveAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.saveAdditional(tag, registries)
        tag.putFloat("Frequency", frequency)
        tag.putBoolean("On", isOn)
    }

    override fun loadAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.loadAdditional(tag, registries)
        if (tag.contains("Frequency")) frequency = tag.getFloat("Frequency")
        if (tag.contains("On")) isOn = tag.getBoolean("On")
    }

    override fun getUpdatePacket(): Packet<ClientGamePacketListener> = ClientboundBlockEntityDataPacket.create(this)

    override fun getUpdateTag(registries: HolderLookup.Provider): CompoundTag {
        val tag = CompoundTag()
        saveAdditional(tag, registries)
        return tag
    }
}
