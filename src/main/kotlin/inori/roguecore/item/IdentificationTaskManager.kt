package inori.roguecore.item

import inori.roguecore.data.DatabaseManager
import inori.roguecore.data.PermanentMaterialManager
import inori.roguecore.data.PlayerDataManager
import inori.roguecore.relic.RelicEffectHandler
import inori.roguecore.unlock.UnlockManager
import org.bukkit.entity.Player
import java.util.UUID
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * 鉴定任务管理器。使用真实时间戳，离线期间也会继续计时。
 */
object IdentificationTaskManager {

    private const val TASKS_KEY = "identify.tasks"

    data class IdentifyTask(
        val id: String,
        val lootId: String,
        val source: DungeonLootSource,
        val floor: Int,
        val startedAt: Long,
        val finishAt: Long
    ) {
        fun isDone(now: Long = System.currentTimeMillis()): Boolean = now >= finishAt
        fun remainingMillis(now: Long = System.currentTimeMillis()): Long = (finishAt - now).coerceAtLeast(0L)
    }

    fun getTasks(uuid: UUID): List<IdentifyTask> {
        val raw = DatabaseManager.getOrCreateContainer(uuid)[TASKS_KEY] ?: return emptyList()
        return raw.split(";")
            .filter { it.isNotBlank() }
            .mapNotNull { decode(it) }
    }

    fun getCompletedCount(uuid: UUID): Int {
        return getTasks(uuid).count { it.isDone() }
    }

    fun getQueueSize(uuid: UUID): Int {
        return getTasks(uuid).size
    }

    fun getQueueLimit(): Int {
        return DungeonLootManager.config.getInt("identification.queue-size", 3).coerceAtLeast(1)
    }

    fun getQueueLimit(player: Player): Int {
        return (getQueueLimit() + UnlockManager.getIdentificationQueueBonus(player)).coerceAtLeast(1)
    }

    fun getIdentifyTimeMillis(source: DungeonLootSource): Long {
        val seconds = DungeonLootManager.config.getInt("identification.time-by-source.${source.name}", when (source) {
            DungeonLootSource.CHEST -> 15
            DungeonLootSource.ELITE -> 30
            DungeonLootSource.BOSS -> 60
            DungeonLootSource.HIDDEN -> 45
        }).coerceAtLeast(1)
        return seconds * 1000L
    }

    fun getIdentifyTimeMillis(player: Player, source: DungeonLootSource): Long {
        return (getIdentifyTimeMillis(source) * UnlockManager.getIdentificationTimeMultiplier(player))
            .roundToLong()
            .coerceAtLeast(1000L)
    }

    fun isAccelerationEnabled(): Boolean {
        return DungeonLootManager.config.getBoolean("identification.acceleration.enabled", true)
    }

    fun getAccelerationSoulShards(): Int {
        return DungeonLootManager.config.getInt("identification.acceleration.soul-shards", 45).coerceAtLeast(0)
    }

    fun getAccelerationSoulShards(player: Player): Int {
        val discount = (RelicEffectHandler.getIdentifyAccelerationDiscountPercent(player) / 100.0).coerceIn(0.0, 0.9)
        return (getAccelerationSoulShards() * (1.0 - discount)).roundToInt().coerceAtLeast(0)
    }

    fun getAccelerationMaterials(): Map<PermanentMaterialManager.MaterialType, Int> {
        return PermanentMaterialManager.parseCost(
            DungeonLootManager.config.getConfigurationSection("identification.acceleration.materials")
        )
    }

    fun getAccelerationReduceMillis(task: IdentifyTask): Long {
        val remaining = task.remainingMillis()
        if (remaining <= 0L) return 0L
        val percent = DungeonLootManager.config.getDouble("identification.acceleration.reduce-percent-of-remaining", 0.35)
            .coerceIn(0.0, 1.0)
        val minMillis = DungeonLootManager.config.getInt("identification.acceleration.min-reduce-seconds", 5)
            .coerceAtLeast(0) * 1000L
        return maxOf((remaining * percent).roundToLong(), minMillis).coerceIn(1L, remaining)
    }

    fun start(player: Player, inventorySlot: Int): DungeonLootManager.LootActionResult {
        val tasks = getTasks(player.uniqueId)
        if (tasks.size >= getQueueLimit(player)) {
            return DungeonLootManager.LootActionResult(false, "§c鉴定队列已满，请先领取已完成的鉴定。")
        }
        val item = player.inventory.getItem(inventorySlot)
            ?: return DungeonLootManager.LootActionResult(false, "§c该位置没有未鉴定装备。")
        val info = DungeonLootManager.getUnidentifiedLootInfo(item)
            ?: return DungeonLootManager.LootActionResult(false, "§c这不是未鉴定装备。")
        val now = System.currentTimeMillis()
        val task = IdentifyTask(
            id = "${now}_${inventorySlot}",
            lootId = info.lootId,
            source = info.source,
            floor = info.floor,
            startedAt = now,
            finishAt = now + getIdentifyTimeMillis(player, info.source)
        )
        player.inventory.setItem(inventorySlot, null)
        saveTasks(player.uniqueId, tasks + task)
        return DungeonLootManager.LootActionResult(true, "§6已开始鉴定，预计 ${formatDuration(task.remainingMillis())} 后完成。")
    }

    fun claim(player: Player, taskId: String): DungeonLootManager.LootActionResult {
        val tasks = getTasks(player.uniqueId)
        val task = tasks.firstOrNull { it.id == taskId }
            ?: return DungeonLootManager.LootActionResult(false, "§c鉴定任务不存在。")
        if (!task.isDone()) {
            return DungeonLootManager.LootActionResult(false, "§c鉴定尚未完成，剩余 ${formatDuration(task.remainingMillis())}。")
        }
        val result = DungeonLootManager.buildIdentifiedLootForPlayer(player, task.lootId, task.source, task.floor)
        if (!result.success || result.item == null) {
            return DungeonLootManager.LootActionResult(false, result.message)
        }
        val leftovers = player.inventory.addItem(result.item)
        leftovers.values.forEach { leftover -> player.world.dropItemNaturally(player.location, leftover) }
        saveTasks(player.uniqueId, tasks.filterNot { it.id == taskId })
        return DungeonLootManager.LootActionResult(true, result.message)
    }

    fun accelerate(player: Player, taskId: String): DungeonLootManager.LootActionResult {
        if (!isAccelerationEnabled()) {
            return DungeonLootManager.LootActionResult(false, "§c当前服务器未启用鉴定加速。")
        }
        val tasks = getTasks(player.uniqueId)
        val task = tasks.firstOrNull { it.id == taskId }
            ?: return DungeonLootManager.LootActionResult(false, "§c鉴定任务不存在。")
        if (task.isDone()) {
            return DungeonLootManager.LootActionResult(false, "§a该鉴定已经完成，可直接领取。")
        }
        val materials = getAccelerationMaterials()
        val shards = getAccelerationSoulShards(player)
        if (!PermanentMaterialManager.takeCost(player, materials)) {
            return DungeonLootManager.LootActionResult(false, "§c材料不足，加速鉴定需要 ${PermanentMaterialManager.formatCost(materials)}")
        }
        if (shards > 0 && !PlayerDataManager.takeSoulShards(player.uniqueId, shards)) {
            PermanentMaterialManager.addAll(player, materials)
            return DungeonLootManager.LootActionResult(false, "§c灵魂碎片不足，加速鉴定需要 §e$shards §c碎片。")
        }

        val reduced = getAccelerationReduceMillis(task)
        val updated = task.copy(finishAt = (task.finishAt - reduced).coerceAtLeast(System.currentTimeMillis()))
        saveTasks(player.uniqueId, tasks.map { if (it.id == taskId) updated else it })
        return DungeonLootManager.LootActionResult(true, "§a已加速鉴定，减少 §e${formatDuration(reduced)}§a。")
    }

    fun claimAll(player: Player): DungeonLootManager.LootActionResult {
        val tasks = getTasks(player.uniqueId)
        val completed = tasks.filter { it.isDone() }
        if (completed.isEmpty()) {
            return DungeonLootManager.LootActionResult(false, "§7没有已完成的鉴定任务。")
        }

        val failed = mutableListOf<IdentifyTask>()
        var claimed = 0
        for (task in completed) {
            val result = DungeonLootManager.buildIdentifiedLootForPlayer(player, task.lootId, task.source, task.floor)
            if (!result.success || result.item == null) {
                failed += task
                continue
            }
            val leftovers = player.inventory.addItem(result.item)
            leftovers.values.forEach { leftover -> player.world.dropItemNaturally(player.location, leftover) }
            claimed += 1
        }

        val remaining = tasks.filter { !it.isDone() } + failed
        saveTasks(player.uniqueId, remaining)

        return if (claimed > 0) {
            val failedText = if (failed.isNotEmpty()) " §7(${failed.size} 个任务生成失败，已保留)" else ""
            DungeonLootManager.LootActionResult(true, "§a已领取 §e$claimed §a件鉴定完成的装备。$failedText")
        } else {
            DungeonLootManager.LootActionResult(false, "§c已完成的鉴定任务暂时无法生成装备，任务已保留。")
        }
    }

    private fun saveTasks(uuid: UUID, tasks: List<IdentifyTask>) {
        DatabaseManager.getOrCreateContainer(uuid)[TASKS_KEY] = tasks.joinToString(";") { encode(it) }
    }

    private fun encode(task: IdentifyTask): String {
        return listOf(task.id, task.lootId, task.source.name, task.floor, task.startedAt, task.finishAt).joinToString(",")
    }

    private fun decode(raw: String): IdentifyTask? {
        val parts = raw.split(",")
        if (parts.size < 6) return null
        val source = runCatching { DungeonLootSource.valueOf(parts[2]) }.getOrNull() ?: return null
        return IdentifyTask(
            id = parts[0],
            lootId = parts[1],
            source = source,
            floor = parts[3].toIntOrNull() ?: 1,
            startedAt = parts[4].toLongOrNull() ?: 0L,
            finishAt = parts[5].toLongOrNull() ?: 0L
        )
    }

    fun formatDuration(millis: Long): String {
        val seconds = (millis / 1000L).coerceAtLeast(0L)
        val minutes = seconds / 60
        val rest = seconds % 60
        return if (minutes > 0) "${minutes}分${rest}秒" else "${rest}秒"
    }
}
