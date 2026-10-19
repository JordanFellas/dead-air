package com.wasteland.deadair.network

import com.wasteland.deadair.item.HandheldRadioItem
import com.wasteland.deadair.item.RadioBackpackItem
import com.wasteland.deadair.item.RadioLocator
import com.wasteland.deadair.network.packets.PacketRadioSquelch
import com.wasteland.deadair.network.packets.PacketRadioStatic
import com.wasteland.deadair.network.packets.PacketRadioStatus
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.PacketDistributor
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

/**
 * Tracks every player's radio gear on the server thread and bridges it to Simple Voice Chat's
 * audio thread.
 *
 * Voice packets arrive off the main thread, so they must not touch players or item stacks.
 * Instead, every few ticks the server thread publishes an immutable snapshot of who can
 * transmit and listen on which channel. The voice thread only reads that snapshot and queues
 * work (battery drain, squelch beeps, dead-air static) that is applied back on the server thread.
 *
 * This class has no Simple Voice Chat imports, so it is safe to load when the mod is absent.
 */
object RadioVoiceRelay {

    private const val REFRESH_INTERVAL_TICKS = 4
    private const val SLOW_INTERVAL_TICKS = 20
    /** Voice packets are 20ms each, so 2 packets = 1 FE is 25 FE per second of talking. */
    private const val PACKETS_PER_FE = 2
    /** A gap longer than this between voice packets ends a transmission. */
    private const val TRANSMISSION_GAP_MS = 350L
    private const val DEAD_AIR_VOLUME = 0.35f

    data class RadioPlayerState(
        val uuid: UUID,
        val dimension: ResourceLocation,
        val pos: BlockPos,
        /** Channel of the powered radio held in hand or on the belt, or null when this player isn't keyed up. */
        val transmitChannel: Int?,
        /** Channels of every powered radio this player carries. */
        val listenChannels: Set<Int>,
        /** Signal kept after blocking blocks between this player and their tower (1.0 = clear). */
        val obstruction: Float
    )

    private class Snapshot(
        val players: Map<UUID, RadioPlayerState>,
        val listenersByChannel: Map<Int, List<RadioPlayerState>>
    )

    private class Transmission {
        @Volatile var lastPacketMs: Long = System.currentTimeMillis()
        /** Listener -> quality they heard this transmission at. */
        val listeners = ConcurrentHashMap<UUID, Float>()
    }

    @Volatile
    private var snapshot = Snapshot(emptyMap(), emptyMap())

    private val pendingVoicePackets = ConcurrentHashMap<UUID, AtomicInteger>()
    private val transmissions = ConcurrentHashMap<UUID, Transmission>()
    private val deadAirQueue = ConcurrentLinkedQueue<UUID>()
    private val squelchQueue = ConcurrentLinkedQueue<Pair<UUID, PacketRadioSquelch>>()
    private val obstructionCache = HashMap<UUID, Float>()
    private val lastStatus = HashMap<UUID, Int>()

    /** Set by the voice chat plugin to release per-speaker audio state when a transmission ends. */
    @Volatile
    var onTransmissionEnded: ((UUID) -> Unit)? = null

    private var tickCounter = 0

    fun channelOf(frequency: Float): Int = (frequency * 10.0f).roundToInt()

    fun getState(uuid: UUID): RadioPlayerState? = snapshot.players[uuid]

    fun getListeners(channel: Int): List<RadioPlayerState> = snapshot.listenersByChannel[channel] ?: emptyList()

    /**
     * Called from the voice thread for every microphone packet from a keyed-up radio.
     * Returns true when this packet starts a new transmission.
     */
    fun markVoiceActivity(uuid: UUID): Boolean {
        var isNew = false
        val transmission = transmissions.computeIfAbsent(uuid) { isNew = true; Transmission() }
        transmission.lastPacketMs = System.currentTimeMillis()
        return isNew
    }

    /** Voice thread: [listener] heard [sender] at [quality]; the first time per transmission opens their squelch. */
    fun recordListener(sender: UUID, listener: UUID, quality: Float) {
        val transmission = transmissions[sender] ?: return
        if (transmission.listeners.put(listener, quality) == null) {
            squelchQueue.add(listener to PacketRadioSquelch(true, quality))
        }
    }

    /** Called from the voice thread for every packet that went out over the network. */
    fun recordTransmittedPacket(uuid: UUID) {
        pendingVoicePackets.computeIfAbsent(uuid) { AtomicInteger() }.incrementAndGet()
    }

    /** Called from the voice thread when a keyed-up radio has no tower in range. */
    fun queueDeadAir(uuid: UUID) {
        deadAirQueue.add(uuid)
    }

    fun tick(server: MinecraftServer) {
        while (true) {
            val uuid = deadAirQueue.poll() ?: break
            val player = server.playerList.getPlayer(uuid) ?: continue
            PacketDistributor.sendToPlayer(player, PacketRadioStatic(PacketRadioStatic.DEAD_AIR_TICKS, DEAD_AIR_VOLUME))
        }
        while (true) {
            val (uuid, packet) = squelchQueue.poll() ?: break
            server.playerList.getPlayer(uuid)?.let { PacketDistributor.sendToPlayer(it, packet) }
        }
        endQuietTransmissions(server)

        tickCounter++
        if (tickCounter % REFRESH_INTERVAL_TICKS != 0) return
        val slowTick = tickCounter % SLOW_INTERVAL_TICKS == 0

        val players = HashMap<UUID, RadioPlayerState>()
        val listeners = HashMap<Int, MutableList<RadioPlayerState>>()
        val backpackOwners = HashSet<UUID>()

        for (player in server.playerList.players) {
            drainVoiceEnergy(player)
            if (updateBackpack(player, slowTick)) backpackOwners.add(player.uuid)

            val transmitStack = RadioLocator.findTransmitter(player)
            val listenChannels = RadioLocator.allPoweredRadios(player)
                .map { channelOf(HandheldRadioItem.getFrequency(it)) }
                .toSet()
            if (transmitStack == null && listenChannels.isEmpty()) {
                lastStatus.remove(player.uuid)
                continue
            }

            if (slowTick || player.uuid !in obstructionCache) {
                obstructionCache[player.uuid] = measureObstruction(player)
            }
            val state = RadioPlayerState(
                player.uuid,
                player.serverLevel().dimension().location(),
                player.blockPosition(),
                transmitStack?.let { channelOf(HandheldRadioItem.getFrequency(it)) },
                listenChannels,
                obstructionCache[player.uuid] ?: 1.0f
            )
            players[player.uuid] = state
            for (channel in listenChannels) {
                listeners.getOrPut(channel) { ArrayList() }.add(state)
            }
            if (slowTick) sendStatus(player, state)
        }

        RadioNetworkManager.retainMobileTowers(backpackOwners)
        snapshot = Snapshot(players, listeners)
        pendingVoicePackets.keys.retainAll(players.keys)
        obstructionCache.keys.retainAll(players.keys)
        lastStatus.keys.retainAll(players.keys)
    }

    fun clear() {
        snapshot = Snapshot(emptyMap(), emptyMap())
        pendingVoicePackets.clear()
        transmissions.clear()
        deadAirQueue.clear()
        squelchQueue.clear()
        obstructionCache.clear()
        lastStatus.clear()
        tickCounter = 0
    }

    /** Signal strength at a player's position, after blocking blocks. Main thread only. */
    fun coverageQuality(player: ServerPlayer): Float {
        val tower = RadioNetworkManager.getBestTower(player.serverLevel().dimension().location(), player.blockPosition())
            ?: return 0.0f
        return RadioNetworkManager.towerQuality(player.blockPosition(), tower) * measureObstruction(player)
    }

    fun measureObstruction(player: ServerPlayer): Float {
        val tower = RadioNetworkManager.getBestTower(player.serverLevel().dimension().location(), player.blockPosition())
            ?: return 1.0f
        // Your own backpack relay is on your back: nothing in between.
        if (tower.mobile && tower.pos.distSqr(player.blockPosition()) <= 9.0) return 1.0f
        return SignalObstruction.factor(player.serverLevel(), player.eyePosition, tower.topCenter)
    }

    private fun sendStatus(player: ServerPlayer, state: RadioPlayerState) {
        val tower = RadioNetworkManager.getBestTower(state.dimension, state.pos)
        val quality = if (tower == null) 0.0f else RadioNetworkManager.towerQuality(state.pos, tower) * state.obstruction
        val key = (quality * 100).roundToInt()
        if (lastStatus.put(player.uuid, key) != key) {
            PacketDistributor.sendToPlayer(player, PacketRadioStatus(quality, state.obstruction < 0.95f))
        }
    }

    private fun endQuietTransmissions(server: MinecraftServer) {
        val now = System.currentTimeMillis()
        val iterator = transmissions.entries.iterator()
        while (iterator.hasNext()) {
            val (sender, transmission) = iterator.next()
            if (now - transmission.lastPacketMs <= TRANSMISSION_GAP_MS) continue
            iterator.remove()
            for ((listener, quality) in transmission.listeners) {
                server.playerList.getPlayer(listener)?.let {
                    PacketDistributor.sendToPlayer(it, PacketRadioSquelch(false, quality))
                }
            }
            onTransmissionEnded?.invoke(sender)
        }
    }

    /** Keeps a worn, charged radio backpack registered as a mobile relay. Returns true while it's relaying. */
    private fun updateBackpack(player: ServerPlayer, slowTick: Boolean): Boolean {
        val backpack = RadioLocator.wornBackpack(player)
        if (backpack == null || RadioBackpackItem.getEnergy(backpack) <= 0) {
            RadioNetworkManager.removeMobileTower(player.uuid)
            return false
        }
        if (slowTick) {
            RadioBackpackItem.setEnergy(backpack, RadioBackpackItem.getEnergy(backpack) - RadioBackpackItem.DRAIN_PER_SECOND)
        }
        RadioNetworkManager.setMobileTower(
            player.uuid, player.serverLevel().dimension().location(), player.blockPosition(), RadioBackpackItem.RELAY_RADIUS
        )
        return true
    }

    private fun drainVoiceEnergy(player: ServerPlayer) {
        val counter = pendingVoicePackets[player.uuid] ?: return
        val fe = counter.get() / PACKETS_PER_FE
        if (fe <= 0) return
        counter.addAndGet(-fe * PACKETS_PER_FE)
        val stack = RadioLocator.findTransmitter(player) ?: return
        HandheldRadioItem.setEnergy(stack, HandheldRadioItem.getEnergy(stack) - fe)
    }
}
