package inori.roguecore.item

import inori.roguecore.data.DatabaseManager
import inori.roguecore.data.PermanentMaterialManager
import inori.roguecore.data.PlayerDataManager
import inori.roguecore.unlock.UnlockManager
import org.bukkit.entity.Player
import java.util.UUID
import kotlin.math.roundToLong

/**
 * 锻造书任务管理器。使用真实时间戳，离线期间也会继续计时。
 */
object ForgeBookTaskManager {

    private const val TASKS_KEY = "forge_book.tasks"

    data class ForgeTask(
        val id: String,
        val lootId: String,
        val qualityId: String,
        val source: DungeonLootSource,
        val floor: Int,
        val startedAt: Long,
        val finishAt: Long
    ) {
        fun isDone(now: Long = System.currentTimeMillis()): Boolean = now >= finishAt
        fun remainingMillis(now: Long = System.currentTimeMillis()): Long = (finishAt - now).coerceAtLeast(0L)
    }

    fun getTasks(uuid: UUID): List<ForgeTask> {
        val raw = DatabaseManager.getOrCreateContainer(uuid)[TASKS_KEY] ?: return emptyList()
        return raw.split(";").filter { it.isNotBlank() }.mapNotNull { decode(it) }
    }

    fun getQueueLimit(): Int {
        return DungeonLootManager.config.getInt("forge-books.queue-size", 2).coerceAtLeast(1)
    }

    fun getQueueLimit(player: Player): Int {
        return (getQueueLimit() + UnlockManager.getForgeBookQueueBonus(player)).coerceAtLeast(1)
    }

    fun getForgeTimeMillis(player: Player, quality: DungeonLootManager.ForgeBookQuality): Long {
        return (quality.timeMillis * UnlockManager.getForgeBookTimeMultiplier(player))
            .roundToLong()
            .coerceAtLeast(1000L)
    }

    fun getCompletedCount(uuid: UUID): Int {
        return getTasks(uuid).count { it.isDone() }
    }

    fun isAccelerationEnabled(): Boolean {
        return DungeonLootManager.config.getBoolean("forge-books.acceleration.enabled", true)
    }

    fun getAccelerationSoulShards(): Int {
        return DungeonLootManager.config.getInt("forge-books.acceleration.soul-shards", 160).coerceAtLeast(0)
    }

    fun getAccelerationMaterials(): Map<PermanentMaterialManager.MaterialType, Int> {
        return PermanentMaterialManager.parseCost(
            DungeonLootManager.config.getConfigurationSection("forge-books.acceleration.materials")
        )
    }

    fun getAccelerationReduceMillis(task: ForgeTask): Long {
        val remaining = task.remainingMillis()
        if (remaining <= 0L) return 0L
        val percent = DungeonLootManager.config.getDouble("forge-books.acceleration.reduce-percent-of-remaining", 0.25)
            .coerceIn(0.0, 1.0)
        val minMillis = DungeonLootManager.config.getInt("forge-books.acceleration.min-reduce-seconds", 60)
            .coerceAtLeast(0) * 1000L
        return maxOf((remaining * percent).roundToLong(), minMillis).coerceIn(1L, remaining)
    }

    fun start(player: Player, inventorySlot: Int): DungeonLootManager.LootActionResult {
        val tasks = getTasks(player.uniqueId)
        if (tasks.size >= getQueueLimit(player)) {
            return DungeonLootManager.LootActionResult(false, "§c锻造队列已满，请先领取已完成的装备。")
        }
        val item = player.inventory.getItem(inventorySlot)
            ?: return DungeonLootManager.LootActionResult(false, "§c该位置没有锻造书。")
        val info = DungeonLootManager.getForgeBookInfo(item)
            ?: return DungeonLootManager.LootActionResult(false, "§c这不是有效的锻造书。")
        val now = System.currentTimeMillis()
        val task = ForgeTask(
            id = "${now}_${inventorySlot}",
            lootId = info.lootId,
            qualityId = info.quality.id,
            source = info.source,
            floor = info.floor,
            startedAt = now,
            finishAt = now + getForgeTimeMillis(player, info.quality)
        )
        player.inventory.setItem(inventorySlot, null)
        saveTasks(player.uniqueId, tasks + task)
        return DungeonLootManager.LootActionResult(true, "§6已开始锻造，预计 ${formatDuration(task.remainingMillis())} 后完成。")
    }

    fun claim(player: Player, taskId: String): DungeonLootManager.LootActionResult {
        val tasks = getTasks(player.uniqueId)
        val task = tasks.firstOrNull { it.id == taskId }
            ?: return DungeonLootManager.LootActionResult(false, "§c锻造任务不存在。")
        if (!task.isDone()) {
            return DungeonLootManager.LootActionResult(false, "§c锻造尚未完成，剩余 ${formatDuration(task.remainingMillis())}。")
        }
        val result = DungeonLootManager.buildForgeBookResultForPlayer(player, task.lootId, task.source, task.floor, task.qualityId)
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
            return DungeonLootManager.LootActionResult(false, "§c当前服务器未启用打造加速。")
        }
        val tasks = getTasks(player.uniqueId)
        val task = tasks.firstOrNull { it.id == taskId }
            ?: return DungeonLootManager.LootActionResult(false, "§c锻造任务不存在。")
        if (task.isDone()) {
            return DungeonLootManager.LootActionResult(false, "§a该打造已经完成，可直接领取。")
        }
        val materials = getAccelerationMaterials()
        val shards = getAccelerationSoulShards()
        if (!PermanentMaterialManager.takeCost(player, materials)) {
            return DungeonLootManager.LootActionResult(false, "§c材料不足，加速打造需要 ${PermanentMaterialManager.formatCost(materials)}")
        }
        if (shards > 0 && !PlayerDataManager.takeSoulShards(player.uniqueId, shards)) {
            PermanentMaterialManager.addAll(player, materials)
            return DungeonLootManager.LootActionResult(false, "§c灵魂碎片不足，加速打造需要 §e$shards §c碎片。")
        }

        val reduced = getAccelerationReduceMillis(task)
        val updated = task.copy(finishAt = (task.finishAt - reduced).coerceAtLeast(System.currentTimeMillis()))
        saveTasks(player.uniqueId, tasks.map { if (it.id == taskId) updated else it })
        return DungeonLootManager.LootActionResult(true, "§a已加速打造，减少 §e${formatDuration(reduced)}§a。")
    }

    fun claimAll(player: Player): DungeonLootManager.LootActionResult {
        val tasks = getTasks(player.uniqueId)
        val completed = tasks.filter { it.isDone() }
        if (completed.isEmpty()) {
            return DungeonLootManager.LootActionResult(false, "§7没有已完成的打造任务。")
        }

        val failed = mutableListOf<ForgeTask>()
        var claimed = 0
        for (task in completed) {
            val result = DungeonLootManager.buildForgeBookResultForPlayer(player, task.lootId, task.source, task.floor, task.qualityId)
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
            DungeonLootManager.LootActionResult(true, "§a已领取 §e$claimed §a件打造完成的装备。$failedText")
        } else {
            DungeonLootManager.LootActionResult(false, "§c已完成的打造任务暂时无法生成装备，任务已保留。")
        }
    }

    private fun saveTasks(uuid: UUID, tasks: List<ForgeTask>) {
        DatabaseManager.getOrCreateContainer(uuid)[TASKS_KEY] = tasks.joinToString(";") { encode(it) }
    }

    private fun encode(task: ForgeTask): String {
        return listOf(task.id, task.lootId, task.qualityId, task.source.name, task.floor, task.startedAt, task.finishAt).joinToString(",")
    }

    private fun decode(raw: String): ForgeTask? {
        val parts = raw.split(",")
        if (parts.size < 7) return null
        val source = runCatching { DungeonLootSource.valueOf(parts[3]) }.getOrNull() ?: return null
        return ForgeTask(
            id = parts[0],
            lootId = parts[1],
            qualityId = parts[2],
            source = source,
            floor = parts[4].toIntOrNull() ?: 1,
            startedAt = parts[5].toLongOrNull() ?: 0L,
            finishAt = parts[6].toLongOrNull() ?: 0L
        )
    }

    fun formatDuration(millis: Long): String {
        val seconds = (millis / 1000L).coerceAtLeast(0L)
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val rest = seconds % 60
        return when {
            hours > 0 -> "${hours}时${minutes}分${rest}秒"
            minutes > 0 -> "${minutes}分${rest}秒"
            else -> "${rest}秒"
        }
    }
}
