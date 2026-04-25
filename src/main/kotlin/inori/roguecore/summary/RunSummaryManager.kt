package inori.roguecore.summary

import inori.roguecore.boon.BoonResonanceManager
import inori.roguecore.boon.PlayerBoonData
import inori.roguecore.curse.RunCurseManager
import inori.roguecore.dungeon.DungeonInstance
import inori.roguecore.data.PermanentMaterialManager
import inori.roguecore.dungeon.RunPersistenceManager
import inori.roguecore.dungeon.room.RoomType
import inori.roguecore.dungeon.route.NextFloorRoute
import inori.roguecore.milestone.RunMilestoneType
import inori.roguecore.modifier.RunModifierManager
import inori.roguecore.modifier.RunModifierType
import inori.roguecore.relic.PlayerRelicData
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 玩家单局结算报告。
 */
object RunSummaryManager {

    private val current = ConcurrentHashMap<UUID, RunSummaryProgress>()
    private val last = ConcurrentHashMap<UUID, RunSummary>()
    private val pendingEndReasons = ConcurrentHashMap<UUID, RunEndReason>()

    fun startRun(player: Player, floor: Int) {
        current[player.uniqueId] = RunSummaryProgress(
            startedAt = System.currentTimeMillis(),
            startFloor = floor.coerceAtLeast(1),
            highestFloor = floor.coerceAtLeast(1)
        )
        pendingEndReasons.remove(player.uniqueId)
        RunPersistenceManager.markDirty()
    }

    fun onFloorEntered(uuid: UUID, floor: Int) {
        val progress = current[uuid] ?: return
        progress.highestFloor = maxOf(progress.highestFloor, floor.coerceAtLeast(1))
        RunPersistenceManager.markDirty()
    }

    fun onRoomCleared(player: Player, roomType: RoomType) {
        val progress = current[player.uniqueId] ?: return
        progress.roomsCleared += 1
        when (roomType) {
            RoomType.COMBAT -> progress.combatRoomsCleared += 1
            RoomType.ELITE -> progress.eliteRoomsCleared += 1
            RoomType.BOSS -> progress.bossRoomsCleared += 1
            else -> Unit
        }
        RunPersistenceManager.markDirty()
    }

    fun onRouteSelected(uuid: UUID, route: NextFloorRoute) {
        val progress = current[uuid] ?: return
        progress.routeHistory += route.displayName
        RunPersistenceManager.markDirty()
    }

    fun onMilestone(uuid: UUID, type: RunMilestoneType) {
        val progress = current[uuid] ?: return
        progress.milestones += type
        RunPersistenceManager.markDirty()
    }

    fun onModifierApplied(uuid: UUID, type: RunModifierType) {
        val progress = current[uuid] ?: return
        progress.modifierCounts[type] = (progress.modifierCounts[type] ?: 0) + 1
        RunPersistenceManager.markDirty()
    }

    fun onSoulShardsSettled(uuid: UUID, amount: Int) {
        if (amount <= 0) return
        val progress = current[uuid] ?: return
        progress.settledSoulShards += amount
        RunPersistenceManager.markDirty()
    }

    fun onRunShardsChanged(uuid: UUID, currentAmount: Int) {
        val progress = current[uuid] ?: return
        progress.peakRunShards = maxOf(progress.peakRunShards, currentAmount)
        RunPersistenceManager.markDirty()
    }

    fun onLootGained(uuid: UUID, category: String, amount: Int = 1) {
        if (amount <= 0 || category.isBlank()) return
        val progress = current[uuid] ?: return
        progress.lootCounts[category] = (progress.lootCounts[category] ?: 0) + amount
        RunPersistenceManager.markDirty()
    }

    fun onSalvaged(uuid: UUID, count: Int, runShards: Int, soulShards: Int, materials: Map<PermanentMaterialManager.MaterialType, Int>) {
        if (count <= 0) return
        val progress = current[uuid] ?: return
        progress.salvagedCount += count
        progress.salvagedRunShards += runShards.coerceAtLeast(0)
        progress.salvagedSoulShards += soulShards.coerceAtLeast(0)
        for ((type, amount) in materials) {
            if (amount > 0) progress.salvagedMaterials[type.id] = (progress.salvagedMaterials[type.id] ?: 0) + amount
        }
        RunPersistenceManager.markDirty()
    }

    fun onCollectionUnlocked(uuid: UUID, name: String) {
        val progress = current[uuid] ?: return
        if (name.isNotBlank()) progress.collectionUnlocks += name
        RunPersistenceManager.markDirty()
    }

    fun onBossFirstKill(uuid: UUID, floor: Int) {
        val progress = current[uuid] ?: return
        progress.bossFirstKills += 1
        progress.collectionUnlocks += "第 ${floor.coerceIn(1, 100)} 层 Boss 首杀"
        RunPersistenceManager.markDirty()
    }

    fun markEndReason(uuid: UUID, reason: RunEndReason) {
        pendingEndReasons[uuid] = reason
    }

    fun finish(player: Player, instance: DungeonInstance?) {
        val uuid = player.uniqueId
        val progress = current.remove(uuid) ?: RunSummaryProgress(
            startedAt = System.currentTimeMillis(),
            startFloor = instance?.config?.floorNumber ?: 1,
            highestFloor = instance?.config?.floorNumber ?: 1
        )
        val reason = pendingEndReasons.remove(uuid)
            ?: if (instance?.completed == true) RunEndReason.CLEAR else RunEndReason.LEAVE
        progress.highestFloor = maxOf(progress.highestFloor, instance?.config?.floorNumber ?: progress.highestFloor)

        val summary = RunSummary(
            result = reason,
            startFloor = progress.startFloor,
            highestFloor = progress.highestFloor,
            roomsCleared = progress.roomsCleared,
            combatRoomsCleared = progress.combatRoomsCleared,
            eliteRoomsCleared = progress.eliteRoomsCleared,
            bossRoomsCleared = progress.bossRoomsCleared,
            settledSoulShards = progress.settledSoulShards,
            peakRunShards = progress.peakRunShards,
            boonCount = PlayerBoonData.getBoons(player).size,
            relicCount = PlayerRelicData.getRelics(player).size,
            curseCount = RunCurseManager.getCurses(player).size,
            modifierCount = RunModifierManager.getModifiers(player).size,
            resonanceLines = BoonResonanceManager.getActiveResonanceLines(player),
            routeHistory = progress.routeHistory.toList(),
            milestoneNames = progress.milestones.sortedBy { it.ordinal }.map { it.displayName },
            modifierCounts = progress.modifierCounts.entries
                .sortedBy { it.key.ordinal }
                .associate { it.key.displayName to it.value },
            lootCounts = progress.lootCounts.toMap(),
            salvagedCount = progress.salvagedCount,
            salvagedRunShards = progress.salvagedRunShards,
            salvagedSoulShards = progress.salvagedSoulShards,
            salvagedMaterials = progress.salvagedMaterials.toMap(),
            collectionUnlocks = progress.collectionUnlocks.toList(),
            bossFirstKills = progress.bossFirstKills,
            durationSeconds = ((System.currentTimeMillis() - progress.startedAt) / 1000L).coerceAtLeast(0L)
        )
        last[uuid] = summary
        RunPersistenceManager.markDirty()
    }

    fun getDisplaySummary(player: Player): RunSummary? {
        return last[player.uniqueId] ?: current[player.uniqueId]?.let { progress ->
            RunSummary(
                result = RunEndReason.ONGOING,
                startFloor = progress.startFloor,
                highestFloor = progress.highestFloor,
                roomsCleared = progress.roomsCleared,
                combatRoomsCleared = progress.combatRoomsCleared,
                eliteRoomsCleared = progress.eliteRoomsCleared,
                bossRoomsCleared = progress.bossRoomsCleared,
                settledSoulShards = progress.settledSoulShards,
                peakRunShards = progress.peakRunShards,
                boonCount = PlayerBoonData.getBoons(player).size,
                relicCount = PlayerRelicData.getRelics(player).size,
                curseCount = RunCurseManager.getCurses(player).size,
                modifierCount = RunModifierManager.getModifiers(player).size,
                resonanceLines = BoonResonanceManager.getActiveResonanceLines(player),
                routeHistory = progress.routeHistory.toList(),
                milestoneNames = progress.milestones.sortedBy { it.ordinal }.map { it.displayName },
                modifierCounts = progress.modifierCounts.entries.sortedBy { it.key.ordinal }.associate { it.key.displayName to it.value },
                lootCounts = progress.lootCounts.toMap(),
                salvagedCount = progress.salvagedCount,
                salvagedRunShards = progress.salvagedRunShards,
                salvagedSoulShards = progress.salvagedSoulShards,
                salvagedMaterials = progress.salvagedMaterials.toMap(),
                collectionUnlocks = progress.collectionUnlocks.toList(),
                bossFirstKills = progress.bossFirstKills,
                durationSeconds = ((System.currentTimeMillis() - progress.startedAt) / 1000L).coerceAtLeast(0L)
            )
        }
    }

    fun clear(uuid: UUID) {
        current.remove(uuid)
        pendingEndReasons.remove(uuid)
        RunPersistenceManager.markDirty()
    }

    fun getSnapshot(uuid: UUID): Map<String, Any>? {
        val progress = current[uuid] ?: return null
        return mapOf(
            "started-at" to progress.startedAt,
            "start-floor" to progress.startFloor,
            "highest-floor" to progress.highestFloor,
            "rooms-cleared" to progress.roomsCleared,
            "combat-rooms-cleared" to progress.combatRoomsCleared,
            "elite-rooms-cleared" to progress.eliteRoomsCleared,
            "boss-rooms-cleared" to progress.bossRoomsCleared,
            "settled-soul-shards" to progress.settledSoulShards,
            "peak-run-shards" to progress.peakRunShards,
            "routes" to progress.routeHistory,
            "milestones" to progress.milestones.map(RunMilestoneType::name),
            "modifiers" to progress.modifierCounts.mapKeys { it.key.name },
            "loot-counts" to progress.lootCounts,
            "salvaged-count" to progress.salvagedCount,
            "salvaged-run-shards" to progress.salvagedRunShards,
            "salvaged-soul-shards" to progress.salvagedSoulShards,
            "salvaged-materials" to progress.salvagedMaterials,
            "collection-unlocks" to progress.collectionUnlocks,
            "boss-first-kills" to progress.bossFirstKills
        )
    }

    fun restore(uuid: UUID, raw: Map<*, *>?) {
        if (raw == null || raw.isEmpty()) {
            current.remove(uuid)
            return
        }
        val progress = RunSummaryProgress(
            startedAt = raw["started-at"]?.toString()?.toLongOrNull() ?: System.currentTimeMillis(),
            startFloor = raw["start-floor"]?.toString()?.toIntOrNull() ?: 1,
            highestFloor = raw["highest-floor"]?.toString()?.toIntOrNull() ?: 1,
            roomsCleared = raw["rooms-cleared"]?.toString()?.toIntOrNull() ?: 0,
            combatRoomsCleared = raw["combat-rooms-cleared"]?.toString()?.toIntOrNull() ?: 0,
            eliteRoomsCleared = raw["elite-rooms-cleared"]?.toString()?.toIntOrNull() ?: 0,
            bossRoomsCleared = raw["boss-rooms-cleared"]?.toString()?.toIntOrNull() ?: 0,
            settledSoulShards = raw["settled-soul-shards"]?.toString()?.toIntOrNull() ?: 0,
            peakRunShards = raw["peak-run-shards"]?.toString()?.toIntOrNull() ?: 0,
            salvagedCount = raw["salvaged-count"]?.toString()?.toIntOrNull() ?: 0,
            salvagedRunShards = raw["salvaged-run-shards"]?.toString()?.toIntOrNull() ?: 0,
            salvagedSoulShards = raw["salvaged-soul-shards"]?.toString()?.toIntOrNull() ?: 0,
            bossFirstKills = raw["boss-first-kills"]?.toString()?.toIntOrNull() ?: 0
        )
        (raw["routes"] as? List<*>)?.mapNotNull { it?.toString() }?.let { progress.routeHistory += it }
        (raw["milestones"] as? List<*>)?.mapNotNull { id ->
            runCatching { RunMilestoneType.valueOf(id.toString()) }.getOrNull()
        }?.let { progress.milestones += it }
        (raw["modifiers"] as? Map<*, *>)?.forEach { (key, value) ->
            val type = runCatching { RunModifierType.valueOf(key.toString()) }.getOrNull() ?: return@forEach
            val count = value?.toString()?.toIntOrNull() ?: return@forEach
            if (count > 0) progress.modifierCounts[type] = count
        }
        (raw["loot-counts"] as? Map<*, *>)?.forEach { (key, value) ->
            val count = value?.toString()?.toIntOrNull() ?: return@forEach
            if (count > 0) progress.lootCounts[key.toString()] = count
        }
        (raw["salvaged-materials"] as? Map<*, *>)?.forEach { (key, value) ->
            val count = value?.toString()?.toIntOrNull() ?: return@forEach
            if (count > 0) progress.salvagedMaterials[key.toString()] = count
        }
        (raw["collection-unlocks"] as? List<*>)?.mapNotNull { it?.toString() }?.let { progress.collectionUnlocks += it }
        current[uuid] = progress
        RunPersistenceManager.markDirty()
    }
}
