package com.wasteland.deadair.network

import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.random.Random

object RadioNetworkManager {

    data class ActiveTower(
        val dimension: ResourceLocation,
        val pos: BlockPos,
        val radius: Int,
        /** Where the signal radiates from: the top of the mast or antenna. */
        val top: BlockPos = pos,
        /** True for a radio backpack relay that moves with its wearer. */
        val mobile: Boolean = false
    ) {
        val topCenter: Vec3 get() = Vec3.atCenterOf(top)
    }

    // Key: Dimension ResourceLocation -> Map of BlockPos to ActiveTower
    private val activeTowers = ConcurrentHashMap<ResourceLocation, ConcurrentHashMap<BlockPos, ActiveTower>>()

    // Radio backpack relays, keyed by wearer
    private val mobileTowers = ConcurrentHashMap<UUID, ActiveTower>()

    private var server: MinecraftServer? = null

    fun init(minecraftServer: MinecraftServer) {
        server = minecraftServer
        activeTowers.clear()
        mobileTowers.clear()
    }

    fun cleanup() {
        server = null
        activeTowers.clear()
        mobileTowers.clear()
    }

    fun tick() {
        // Routine maintenance or graph cache invalidation if needed
    }

    fun registerTower(level: Level, pos: BlockPos, radius: Int, top: BlockPos = pos) {
        val dim = level.dimension().location()
        val dimMap = activeTowers.computeIfAbsent(dim) { ConcurrentHashMap() }
        dimMap[pos] = ActiveTower(dim, pos, radius, top)
    }

    fun unregisterTower(level: Level, pos: BlockPos) {
        val dim = level.dimension().location()
        activeTowers[dim]?.remove(pos)
    }

    fun setMobileTower(owner: UUID, dim: ResourceLocation, pos: BlockPos, radius: Int) {
        mobileTowers[owner] = ActiveTower(dim, pos, radius, pos.above(), mobile = true)
    }

    fun removeMobileTower(owner: UUID) {
        mobileTowers.remove(owner)
    }

    fun retainMobileTowers(owners: Set<UUID>) {
        mobileTowers.keys.retainAll(owners)
    }

    private fun towersIn(dim: ResourceLocation): List<ActiveTower> {
        val fixed = activeTowers[dim]?.values ?: emptyList()
        val mobile = mobileTowers.values.filter { it.dimension == dim }
        return if (mobile.isEmpty()) fixed.toList() else fixed + mobile
    }

    /**
     * Checks if a position is in range of any active tower in that dimension.
     */
    fun isPositionCovered(level: Level, pos: BlockPos): Boolean {
        return getDirectCoveringTowers(level.dimension().location(), pos).isNotEmpty()
    }

    /**
     * Finds all active towers that directly cover the given position.
     */
    fun getDirectCoveringTowers(dim: ResourceLocation, pos: BlockPos): List<ActiveTower> {
        return towersIn(dim).filter { tower -> getDistance(pos, tower.pos) <= tower.radius }
    }

    /** The covering tower whose signal is strongest at [pos], or null when nothing covers it. */
    fun getBestTower(dim: ResourceLocation, pos: BlockPos): ActiveTower? {
        return getDirectCoveringTowers(dim, pos).maxByOrNull { towerQuality(pos, it) }
    }

    /**
     * Computes the entire connected daisy-chained network of towers starting from
     * the towers that cover the sender.
     */
    fun getConnectedNetwork(dim: ResourceLocation, senderPos: BlockPos): Set<ActiveTower> {
        val allTowers = towersIn(dim)
        val directTowers = allTowers.filter { getDistance(senderPos, it.pos) <= it.radius }
        if (directTowers.isEmpty()) return emptySet()

        val connected = mutableSetOf<ActiveTower>()
        val queue = ArrayDeque<ActiveTower>()

        for (t in directTowers) {
            connected.add(t)
            queue.add(t)
        }

        // BFS to traverse daisy-chained towers
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            for (other in allTowers) {
                if (!connected.contains(other)) {
                    val dist = getDistance(current.pos, other.pos)
                    // Daisy-chain linking condition: towers reach each other's signal boundary
                    if (dist <= (current.radius + other.radius)) {
                        connected.add(other)
                        queue.add(other)
                    }
                }
            }
        }

        return connected
    }

    /**
     * Gets how many other active towers are linked to the tower at pos.
     */
    fun getLinkedTowersCount(level: Level, pos: BlockPos): Int {
        val dim = level.dimension().location()
        val dimMap = activeTowers[dim] ?: return 0
        if (!dimMap.containsKey(pos)) return 0
        val network = getConnectedNetwork(dim, pos)
        return max(0, network.size - 1)
    }

    /**
     * Calculates signal quality for a receiver position given the broadcast network.
     * Returns: 0.0 (no signal) to 1.0 (crystal clear).
     */
    fun getSignalQuality(receiverPos: BlockPos, network: Set<ActiveTower>): Float {
        var maxQuality = 0.0f
        for (tower in network) {
            val quality = towerQuality(receiverPos, tower)
            if (quality > maxQuality) maxQuality = quality
        }
        return maxQuality.coerceIn(0.0f, 1.0f)
    }

    /** Inside the inner 50% of a tower's radius: 1.0; outer 50%: decays from 1.0 to 0.1; outside: 0. */
    fun towerQuality(pos: BlockPos, tower: ActiveTower): Float {
        val dist = getDistance(pos, tower.pos)
        if (dist > tower.radius) return 0.0f
        val ratio = dist.toFloat() / tower.radius.toFloat()
        return if (ratio <= 0.5f) 1.0f else 1.0f - ((ratio - 0.5f) / 0.5f) * 0.9f
    }

    /**
     * Applies post-apocalyptic static degradation to text messages.
     * When quality < 0.6, characters start glitching. At low quality, heavy dropouts occur.
     */
    fun degradeMessage(message: String, quality: Float): String {
        if (quality >= 0.75f) return message

        val glitchChars = charArrayOf('~', '#', '.', '?', '-', '_')
        val dropRate = (1.0f - quality).coerceIn(0.0f, 0.85f)
        val sb = StringBuilder()

        for (ch in message) {
            if (ch.isWhitespace()) {
                sb.append(ch)
            } else if (Random.nextFloat() < dropRate) {
                if (Random.nextFloat() < 0.35f) {
                    sb.append(glitchChars[Random.nextInt(glitchChars.size)])
                } else {
                    sb.append('.')
                }
            } else {
                sb.append(ch)
            }
        }

        return sb.toString()
    }

    private fun getDistance(a: BlockPos, b: BlockPos): Double {
        val dx = (a.x - b.x).toDouble()
        val dy = (a.y - b.y).toDouble()
        val dz = (a.z - b.z).toDouble()
        return sqrt(dx * dx + dy * dy + dz * dz)
    }
}
