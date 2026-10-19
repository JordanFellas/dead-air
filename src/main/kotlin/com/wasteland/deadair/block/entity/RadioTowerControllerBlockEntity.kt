package com.wasteland.deadair.block.entity

import com.wasteland.deadair.block.BroadcastControllerBlock
import com.wasteland.deadair.block.RadioAntenna
import com.wasteland.deadair.block.RadioMastSectionBlock
import com.wasteland.deadair.block.RadioTowerControllerBlock
import com.wasteland.deadair.energy.CustomEnergyStorage
import com.wasteland.deadair.item.BatteryCellItem
import com.wasteland.deadair.menu.RadioTowerMenu
import com.wasteland.deadair.network.RadioNetworkManager
import com.wasteland.deadair.registry.ModBlockEntities
import com.wasteland.deadair.registry.ModBlocks
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.HolderLookup
import net.minecraft.core.NonNullList
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.world.ContainerHelper
import net.minecraft.world.Containers
import net.minecraft.world.MenuProvider
import net.minecraft.world.WorldlyContainer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerData
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import kotlin.math.min

class RadioTowerControllerBlockEntity(pos: BlockPos, state: BlockState) :
    BlockEntity(ModBlockEntities.RADIO_TOWER_CONTROLLER_BE.get(), pos, state),
    MenuProvider,
    WorldlyContainer {

    companion object {
        const val CAPACITY = 50000
        const val MAX_RECEIVE = 1000
        const val BROADCAST_CAPACITY = 500000
        const val BROADCAST_MAX_RECEIVE = 5000
        /** Tower Foundation pad around a Broadcast Controller: the 5x5 minus the controller. */
        const val FOUNDATION_RADIUS = 2

        fun tick(level: Level, pos: BlockPos, state: BlockState, entity: RadioTowerControllerBlockEntity) {
            entity.serverTick()
        }
    }

    /** True for the Mega Tower's Broadcast Controller. */
    val isBroadcast: Boolean = state.block is BroadcastControllerBlock

    // Inventory: 1 slot for battery charging
    val items: NonNullList<ItemStack> = NonNullList.withSize(1, ItemStack.EMPTY)

    // Energy storage (50,000 FE, or 500,000 FE for a Broadcast Controller)
    val energyStorage = CustomEnergyStorage(
        if (isBroadcast) BROADCAST_CAPACITY else CAPACITY,
        if (isBroadcast) BROADCAST_MAX_RECEIVE else MAX_RECEIVE, 0, 0
    ) {
        setChanged()
    }

    // Tower status (from the last structure scan)
    /** Height of the tower above the controller, in blocks. */
    var layerCount: Int = 0
        private set
    var partCount: Int = 0
        private set
    var hasAntenna: Boolean = false
        private set
    var isStructureValid: Boolean = false
        private set
    var isActive: Boolean = false
        private set
    var linkedTowersCount: Int = 0
    val towerType: TowerType get() = if (isBroadcast) TowerType.BROADCAST else TowerType.REGULAR
    /** Range from antennas (top + sides). */
    var antennaBonus: Int = 0
        private set
    /** A Broadcast Controller without its 5x5 foundation pad won't run. */
    var missingFoundation: Boolean = false
        private set
    /** The tower has no parts standing on the ground beside the controller (it's perched on it). */
    var needsFooting: Boolean = false
        private set
    /** Where the signal radiates from (tip of the top antenna, or the top of the structure). */
    private var signalTop: BlockPos = BlockPos.ZERO

    private var tickCounter: Int = 0

    val dataAccess: ContainerData = object : ContainerData {
        override fun get(index: Int): Int {
            return when (index) {
                0 -> energyStorage.energyStored
                1 -> energyStorage.maxEnergyStored
                2 -> layerCount
                3 -> if (hasAntenna) 1 else 0
                4 -> if (isStructureValid) 1 else 0
                5 -> if (isActive) 1 else 0
                6 -> getCoverageRadius()
                7 -> linkedTowersCount
                8 -> towerType.ordinal
                9 -> antennaBonus
                10 -> partCount
                11 -> if (missingFoundation) 1 else 0
                12 -> if (needsFooting) 1 else 0
                else -> 0
            }
        }

        override fun set(index: Int, value: Int) {
            when (index) {
                0 -> energyStorage.setEnergy(value)
                2 -> layerCount = value
                3 -> hasAntenna = (value == 1)
                4 -> isStructureValid = (value == 1)
                5 -> isActive = (value == 1)
                7 -> linkedTowersCount = value
            }
        }

        override fun getCount(): Int = 13
    }

    private fun serverTick() {
        if (level == null || level!!.isClientSide) return

        tickCounter++

        // Handle battery discharging in slot 0
        val batteryStack = items[0]
        if (!batteryStack.isEmpty && batteryStack.item is BatteryCellItem) {
            val space = energyStorage.maxEnergyStored - energyStorage.energyStored
            if (space > 0) {
                val availableInBattery = BatteryCellItem.getEnergy(batteryStack)
                val transfer = min(space, min(availableInBattery, if (isBroadcast) 500 else 100))
                if (transfer > 0 && BatteryCellItem.consumeEnergy(batteryStack, transfer)) {
                    energyStorage.receiveEnergy(transfer, false)
                }
            }
        }

        // Rescan the (possibly large) tower every 3 seconds, starting on the first second
        if (tickCounter % 60 == 20) scanMultiBlockStructure()

        // Power and network update every second (20 ticks)
        if (tickCounter % 20 == 0) {
            val drain = if (isStructureValid) towerType.drainPerTick(layerCount) * 20 else 0
            val canRun = isStructureValid && energyStorage.energyStored >= drain

            if (canRun) {
                energyStorage.consumeEnergy(drain)
                if (!isActive) {
                    isActive = true
                    updateBlockState(true)
                }
                RadioNetworkManager.registerTower(level!!, worldPosition, getCoverageRadius(), signalTop)
                linkedTowersCount = RadioNetworkManager.getLinkedTowersCount(level!!, worldPosition)
            } else {
                if (isActive) {
                    isActive = false
                    updateBlockState(false)
                    RadioNetworkManager.unregisterTower(level!!, worldPosition)
                }
                linkedTowersCount = 0
            }

            setChanged()
        }
    }

    /** Scans the free-form tower connected to this controller. */
    fun scanMultiBlockStructure() {
        val lvl = level ?: return
        missingFoundation = isBroadcast && !hasFoundation(lvl)
        if (missingFoundation) {
            isStructureValid = false
            needsFooting = false
            layerCount = 0
            partCount = 0
            hasAntenna = false
            antennaBonus = 0
            return
        }
        val result = TowerScanner.scan(lvl, worldPosition, towerType)
        layerCount = result.height
        partCount = result.parts
        hasAntenna = result.hasTopAntenna
        antennaBonus = result.antennaBonus
        signalTop = result.top
        // A tower has to stand on the ground: some of its parts must sit level with the controller.
        needsFooting = result.height >= 2 && result.groundParts == 0
        isStructureValid = result.height >= 2 && !needsFooting
    }

    /** Every block of the 5x5 around a Broadcast Controller (at its own level) is a Tower Foundation. */
    private fun hasFoundation(lvl: Level): Boolean {
        val r = FOUNDATION_RADIUS
        for (dx in -r..r) for (dz in -r..r) {
            if (dx == 0 && dz == 0) continue
            if (!lvl.getBlockState(worldPosition.offset(dx, 0, dz)).`is`(ModBlocks.TOWER_FOUNDATION.get())) return false
        }
        return true
    }

    fun getCoverageRadius(): Int {
        if (!isStructureValid) return 0
        val type = towerType
        return type.baseRadius + type.perHeight * layerCount + type.perPart * minOf(partCount, type.partCap) + antennaBonus
    }
    private fun updateBlockState(active: Boolean) {
        val state = blockState
        if (state.hasProperty(RadioTowerControllerBlock.ACTIVE) && state.getValue(RadioTowerControllerBlock.ACTIVE) != active) {
            level?.setBlock(worldPosition, state.setValue(RadioTowerControllerBlock.ACTIVE, active), 3)
        }
    }

    override fun setRemoved() {
        super.setRemoved()
        if (level != null && !level!!.isClientSide) {
            RadioNetworkManager.unregisterTower(level!!, worldPosition)
        }
    }

    fun drops() {
        val lvl = level ?: return
        Containers.dropContents(lvl, worldPosition, items)
    }

    override fun saveAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.saveAdditional(tag, registries)
        tag.put("Energy", energyStorage.serializeNBT(registries))
        tag.putInt("LayerCount", layerCount)
        tag.putBoolean("HasAntenna", hasAntenna)
        tag.putBoolean("IsActive", isActive)
        ContainerHelper.saveAllItems(tag, items, registries)
    }

    override fun loadAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.loadAdditional(tag, registries)
        tag.get("Energy")?.let { energyStorage.deserializeNBT(registries, it) }
        layerCount = tag.getInt("LayerCount")
        hasAntenna = tag.getBoolean("HasAntenna")
        isActive = tag.getBoolean("IsActive")
        ContainerHelper.loadAllItems(tag, items, registries)
    }

    override fun getUpdatePacket(): Packet<ClientGamePacketListener>? {
        return ClientboundBlockEntityDataPacket.create(this)
    }

    override fun getUpdateTag(registries: HolderLookup.Provider): CompoundTag {
        val tag = CompoundTag()
        saveAdditional(tag, registries)
        return tag
    }

    override fun getDisplayName(): Component = Component.literal("Radio Tower Terminal")

    override fun createMenu(containerId: Int, playerInventory: Inventory, player: Player): AbstractContainerMenu {
        return RadioTowerMenu(containerId, playerInventory, this, dataAccess)
    }

    // WorldlyContainer
    override fun getContainerSize(): Int = items.size
    override fun isEmpty(): Boolean = items.all { it.isEmpty }
    override fun getItem(slot: Int): ItemStack = items[slot]
    override fun removeItem(slot: Int, amount: Int): ItemStack = ContainerHelper.removeItem(items, slot, amount)
    override fun removeItemNoUpdate(slot: Int): ItemStack = ContainerHelper.takeItem(items, slot)
    override fun setItem(slot: Int, stack: ItemStack) { items[slot] = stack }
    override fun stillValid(player: Player): Boolean = true
    override fun clearContent() = items.clear()

    override fun getSlotsForFace(side: Direction): IntArray = intArrayOf(0)
    override fun canPlaceItemThroughFace(index: Int, itemStack: ItemStack, direction: Direction?): Boolean =
        itemStack.item is BatteryCellItem
    override fun canTakeItemThroughFace(index: Int, stack: ItemStack, direction: Direction): Boolean = true
}
