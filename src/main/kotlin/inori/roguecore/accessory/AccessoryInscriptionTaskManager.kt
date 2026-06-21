package inori.roguecore.accessory

import inori.roguecore.data.DatabaseManager
import inori.roguecore.data.PermanentMaterialManager
import inori.roguecore.data.PlayerDataManager
import inori.roguecore.display.ContentDisplayNameResolver
import inori.roguecore.item.DungeonLootManager
import inori.roguecore.item.DungeonLootSource
import org.bukkit.entity.Player
import java.util.UUID
import kotlin.math.roundToLong

object AccessoryInscriptionTaskManager {

    private const val TASKS_KEY = "accessory_inscription.tasks"

    data class InscriptionTask(
        val id: String,
        val accessoryId: String,
        val qualityId: String,
        val source: DungeonLootSource,
        val floor: Int,
        val startedAt: Long,
        val finishAt: Long
    ) {
        fun isDone(now: Long = System.currentTimeMillis()): Boolean = now >= finishAt
        fun remainingMillis(now: Long = System.currentTimeMillis()): Long = (finishAt - now).coerceAtLeast(0L)
    }

    fun getTasks(uuid: UUID): List<InscriptionTask> {
        val raw = DatabaseManager.getOrCreateContainer(uuid)[TASKS_KEY] ?: return emptyList()
        return raw.split(";").filter { it.isNotBlank() }.mapNotNull { decode(it) }
    }

    fun getCompletedCount(uuid: UUID): Int = getTasks(uuid).count { it.isDone() }

    fun getQueueLimit(): Int = AccessoryRegistry.inscriptionQueueSize.coerceAtLeast(1)

    fun getInscriptionTimeMillis(quality: AccessoryInscriptionQuality): Long = quality.timeMillis.coerceAtLeast(1000L)

    fun isAccelerationEnabled(): Boolean = AccessoryRegistry.inscriptionAccelerationEnabled

    fun getAccelerationSoulShards(): Int = AccessoryRegistry.inscriptionAccelerationSoulShards.coerceAtLeast(0)

    fun getAccelerationMaterials(): Map<PermanentMaterialManager.MaterialType, Int> = AccessoryRegistry.getInscriptionAccelerationMaterials()

    fun getAccelerationReduceMillis(task: InscriptionTask): Long {
        val remaining = task.remainingMillis()
        if (remaining <= 0L) return 0L
        val minMillis = AccessoryRegistry.inscriptionAccelerationMinReduceSeconds.coerceAtLeast(0) * 1000L
        return maxOf((remaining * AccessoryRegistry.inscriptionAccelerationReducePercent).roundToLong(), minMillis)
            .coerceIn(1L, remaining)
    }

    fun start(player: Player, inventorySlot: Int): DungeonLootManager.LootActionResult {
        if (!AccessoryRegistry.inscriptionEnabled) {
            return DungeonLootManager.LootActionResult(false, "§c当前未启用饰品刻印。")
        }
        val tasks = getTasks(player.uniqueId)
        if (tasks.size >= getQueueLimit()) {
            return DungeonLootManager.LootActionResult(false, "§c饰品刻印队列已满，请先领取已完成任务。")
        }
        val item = player.inventory.getItem(inventorySlot)
            ?: return DungeonLootManager.LootActionResult(false, "§c该位置没有饰品刻印书。")
        val info = AccessoryItemCodec.parseInscriptionBook(item)
            ?: return DungeonLootManager.LootActionResult(false, "§c这不是有效的饰品刻印书。")
        val now = System.currentTimeMillis()
        val task = InscriptionTask(
            id = "${now}_${inventorySlot}",
            accessoryId = info.definition.id,
            qualityId = info.quality.id,
            source = info.source,
            floor = info.floor,
            startedAt = now,
            finishAt = now + getInscriptionTimeMillis(info.quality)
        )
        player.inventory.setItem(inventorySlot, null)
        saveTasks(player.uniqueId, tasks + task)
        return DungeonLootManager.LootActionResult(true, "§6已开始饰品刻印，预计 ${formatDuration(task.remainingMillis())} 后完成。")
    }

    fun claim(player: Player, taskId: String): DungeonLootManager.LootActionResult {
        val tasks = getTasks(player.uniqueId)
        val task = tasks.firstOrNull { it.id == taskId }
            ?: return DungeonLootManager.LootActionResult(false, "§c饰品刻印任务不存在。")
        if (!task.isDone()) {
            return DungeonLootManager.LootActionResult(false, "§c饰品刻印尚未完成，剩余 ${formatDuration(task.remainingMillis())}。")
        }
        val quality = AccessoryRegistry.getInscriptionQuality(task.qualityId)
            ?: return DungeonLootManager.LootActionResult(false, "§c饰品刻印品质配置缺失，请联系管理员。")
        val result = AccessoryDropManager.buildIdentifiedItemForPlayer(
            player = player,
            accessoryId = task.accessoryId,
            source = task.source,
            floor = task.floor,
            extraRarityLuck = quality.rarityLuck,
            valueMultiplier = quality.valueMultiplier
        )
        if (!result.success || result.item == null) {
            return DungeonLootManager.LootActionResult(false, result.message)
        }
        val leftovers = player.inventory.addItem(result.item)
        leftovers.values.forEach { player.world.dropItemNaturally(player.location, it) }
        saveTasks(player.uniqueId, tasks.filterNot { it.id == taskId })
        return DungeonLootManager.LootActionResult(true, result.message)
    }

    fun claimAll(player: Player): DungeonLootManager.LootActionResult {
        val tasks = getTasks(player.uniqueId)
        val completed = tasks.filter { it.isDone() }
        if (completed.isEmpty()) {
            return DungeonLootManager.LootActionResult(false, "§7没有已完成的饰品刻印任务。")
        }
        val failed = mutableListOf<InscriptionTask>()
        var claimed = 0
        for (task in completed) {
            val quality = AccessoryRegistry.getInscriptionQuality(task.qualityId)
            if (quality == null) {
                failed += task
                continue
            }
            val result = AccessoryDropManager.buildIdentifiedItemForPlayer(
                player = player,
                accessoryId = task.accessoryId,
                source = task.source,
                floor = task.floor,
                extraRarityLuck = quality.rarityLuck,
                valueMultiplier = quality.valueMultiplier
            )
            if (!result.success || result.item == null) {
                failed += task
                continue
            }
            val leftovers = player.inventory.addItem(result.item)
            leftovers.values.forEach { player.world.dropItemNaturally(player.location, it) }
            claimed += 1
        }
        saveTasks(player.uniqueId, tasks.filter { !it.isDone() } + failed)
        return if (claimed > 0) {
            val failedText = if (failed.isNotEmpty()) " §7(${failed.size} 个任务生成失败，已保留)" else ""
            DungeonLootManager.LootActionResult(true, "§a已领取 §e$claimed §a件刻印完成的饰品。$failedText")
        } else {
            DungeonLootManager.LootActionResult(false, "§c已完成的饰品刻印任务暂时无法生成，任务已保留。")
        }
    }

    fun accelerate(player: Player, taskId: String): DungeonLootManager.LootActionResult {
        if (!isAccelerationEnabled()) {
            return DungeonLootManager.LootActionResult(false, "§c当前未启用饰品刻印加速。")
        }
        val tasks = getTasks(player.uniqueId)
        val task = tasks.firstOrNull { it.id == taskId }
            ?: return DungeonLootManager.LootActionResult(false, "§c饰品刻印任务不存在。")
        if (task.isDone()) {
            return DungeonLootManager.LootActionResult(false, "§a该饰品刻印已经完成，可直接领取。")
        }
        val materials = getAccelerationMaterials()
        val shards = getAccelerationSoulShards()
        if (!PermanentMaterialManager.takeCost(player, materials)) {
            return DungeonLootManager.LootActionResult(false, "§c材料不足，加速饰品刻印需要 ${PermanentMaterialManager.formatCost(materials)}")
        }
        if (shards > 0 && !PlayerDataManager.takeSoulShards(player.uniqueId, shards)) {
            PermanentMaterialManager.addAll(player, materials)
            return DungeonLootManager.LootActionResult(false, "§c灵魂碎片不足，加速饰品刻印需要 §e$shards §c碎片。")
        }
        val reduced = getAccelerationReduceMillis(task)
        val updated = task.copy(finishAt = (task.finishAt - reduced).coerceAtLeast(System.currentTimeMillis()))
        saveTasks(player.uniqueId, tasks.map { if (it.id == taskId) updated else it })
        return DungeonLootManager.LootActionResult(true, "§a已加速饰品刻印，减少 §e${formatDuration(reduced)}§a。")
    }

    private fun saveTasks(uuid: UUID, tasks: List<InscriptionTask>) {
        DatabaseManager.getOrCreateContainer(uuid)[TASKS_KEY] = tasks.joinToString(";") { encode(it) }
    }

    private fun encode(task: InscriptionTask): String {
        return listOf(task.id, task.accessoryId, task.qualityId, task.source.name, task.floor, task.startedAt, task.finishAt).joinToString(",")
    }

    private fun decode(raw: String): InscriptionTask? {
        val parts = raw.split(",")
        if (parts.size < 7) return null
        val source = runCatching { DungeonLootSource.valueOf(parts[3]) }.getOrNull() ?: return null
        return InscriptionTask(
            id = parts[0],
            accessoryId = parts[1],
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

    fun taskDisplayName(task: InscriptionTask): String {
        val name = AccessoryRegistry.get(task.accessoryId)?.name
        if (!name.isNullOrBlank()) {
            return name
        }
        return ContentDisplayNameResolver.safeText(task.accessoryId, "未知饰品")
    }
}
