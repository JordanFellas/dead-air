package com.wasteland.deadair.energy

import net.minecraft.nbt.IntTag
import net.minecraft.nbt.Tag
import net.neoforged.neoforge.common.util.INBTSerializable
import net.neoforged.neoforge.energy.IEnergyStorage
import kotlin.math.max
import kotlin.math.min

open class CustomEnergyStorage(
    private var capacity: Int,
    private var maxReceive: Int = capacity,
    private var maxExtract: Int = capacity,
    energy: Int = 0,
    private val onEnergyChanged: (() -> Unit)? = null
) : IEnergyStorage, INBTSerializable<Tag> {

    protected var storedEnergy: Int = max(0, min(energy, capacity))

    override fun receiveEnergy(maxReceive: Int, simulate: Boolean): Int {
        if (!canReceive()) return 0
        val energyReceived = min(capacity - storedEnergy, min(this.maxReceive, maxReceive))
        if (!simulate && energyReceived > 0) {
            storedEnergy += energyReceived
            onEnergyChanged?.invoke()
        }
        return energyReceived
    }

    override fun extractEnergy(maxExtract: Int, simulate: Boolean): Int {
        if (!canExtract()) return 0
        val energyExtracted = min(storedEnergy, min(this.maxExtract, maxExtract))
        if (!simulate && energyExtracted > 0) {
            storedEnergy -= energyExtracted
            onEnergyChanged?.invoke()
        }
        return energyExtracted
    }

    override fun getEnergyStored(): Int = storedEnergy

    override fun getMaxEnergyStored(): Int = capacity

    override fun canExtract(): Boolean = maxExtract > 0

    override fun canReceive(): Boolean = maxReceive > 0

    fun setEnergy(amount: Int) {
        this.storedEnergy = max(0, min(amount, capacity))
        onEnergyChanged?.invoke()
    }

    fun consumeEnergy(amount: Int): Boolean {
        if (storedEnergy >= amount) {
            storedEnergy -= amount
            onEnergyChanged?.invoke()
            return true
        }
        return false
    }

    override fun serializeNBT(provider: net.minecraft.core.HolderLookup.Provider): Tag = IntTag.valueOf(storedEnergy)

    override fun deserializeNBT(provider: net.minecraft.core.HolderLookup.Provider, nbt: Tag) {
        if (nbt is IntTag) {
            this.storedEnergy = max(0, min(nbt.asInt, capacity))
        }
    }
}
