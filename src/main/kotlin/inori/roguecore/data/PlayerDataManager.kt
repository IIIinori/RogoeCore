package inori.roguecore.data

import taboolib.expansion.DataContainer
import java.util.UUID

/**
 * 玩家永久数据管理。
 *
 * 由 TabooLib DatabasePlayer 容器负责缓存与异步写回。
 */
object PlayerDataManager {

    const val KEY_SOUL_SHARDS = "soul_shards"
    const val KEY_TOTAL_RUNS = "total_runs"
    const val KEY_TOTAL_CLEARS = "total_clears"
    const val KEY_BEST_FLOOR = "best_floor"
    const val KEY_TOTAL_KILLS = "total_kills"

    fun get(uuid: UUID): PlayerData {
        val container = DatabaseManager.getOrCreateContainer(uuid)
        return PlayerData(
            uuid = uuid,
            soulShards = container.getInt(KEY_SOUL_SHARDS),
            totalRuns = container.getInt(KEY_TOTAL_RUNS),
            totalClears = container.getInt(KEY_TOTAL_CLEARS),
            bestFloor = container.getInt(KEY_BEST_FLOOR),
            totalKills = container.getInt(KEY_TOTAL_KILLS)
        )
    }

    fun save(data: PlayerData) {
        val container = DatabaseManager.getOrCreateContainer(data.uuid)
        container.setInt(KEY_SOUL_SHARDS, data.soulShards)
        container.setInt(KEY_TOTAL_RUNS, data.totalRuns)
        container.setInt(KEY_TOTAL_CLEARS, data.totalClears)
        container.setInt(KEY_BEST_FLOOR, data.bestFloor)
        container.setInt(KEY_TOTAL_KILLS, data.totalKills)
    }

    fun addSoulShards(uuid: UUID, amount: Int) {
        val container = DatabaseManager.getOrCreateContainer(uuid)
        container.setInt(KEY_SOUL_SHARDS, container.getInt(KEY_SOUL_SHARDS) + amount)
    }

    fun setSoulShards(uuid: UUID, amount: Int) {
        val container = DatabaseManager.getOrCreateContainer(uuid)
        container.setInt(KEY_SOUL_SHARDS, amount.coerceAtLeast(0))
    }

    fun takeSoulShards(uuid: UUID, amount: Int): Boolean {
        val container = DatabaseManager.getOrCreateContainer(uuid)
        val current = container.getInt(KEY_SOUL_SHARDS)
        if (current < amount) return false
        container.setInt(KEY_SOUL_SHARDS, current - amount)
        return true
    }

    fun reset(uuid: UUID) {
        val container = DatabaseManager.getOrCreateContainer(uuid)
        container.setInt(KEY_SOUL_SHARDS, 0)
        container.setInt(KEY_TOTAL_RUNS, 0)
        container.setInt(KEY_TOTAL_CLEARS, 0)
        container.setInt(KEY_BEST_FLOOR, 0)
        container.setInt(KEY_TOTAL_KILLS, 0)
    }

    fun recordRunStart(uuid: UUID) {
        val container = DatabaseManager.getOrCreateContainer(uuid)
        container.setInt(KEY_TOTAL_RUNS, container.getInt(KEY_TOTAL_RUNS) + 1)
    }

    fun recordClear(uuid: UUID, floor: Int) {
        val container = DatabaseManager.getOrCreateContainer(uuid)
        container.setInt(KEY_TOTAL_CLEARS, container.getInt(KEY_TOTAL_CLEARS) + 1)
        updateBestFloor(uuid, floor)
    }

    fun updateBestFloor(uuid: UUID, floor: Int) {
        val container = DatabaseManager.getOrCreateContainer(uuid)
        if (floor > container.getInt(KEY_BEST_FLOOR)) {
            container.setInt(KEY_BEST_FLOOR, floor)
        }
    }

    fun addKills(uuid: UUID, count: Int) {
        val container = DatabaseManager.getOrCreateContainer(uuid)
        container.setInt(KEY_TOTAL_KILLS, container.getInt(KEY_TOTAL_KILLS) + count)
    }

    fun preload(uuid: UUID) {
        DatabaseManager.preload(uuid)
    }

    fun unload(uuid: UUID) {
        DatabaseManager.release(uuid)
    }

    private fun taboolib.expansion.DataContainer.getInt(key: String): Int {
        return this[key]?.toIntOrNull() ?: 0
    }

    private fun DataContainer.setInt(key: String, value: Int) {
        if (DatabaseManager.isShuttingDown()) {
            forcedSet(user, key, value, sync = true)
        } else {
            this[key] = value
        }
    }
}
