package inori.roguecore.data

import inori.roguecore.dungeon.DungeonManager
import inori.roguecore.dungeon.RunPersistenceManager
import inori.roguecore.talent.TalentManager
import inori.roguecore.ui.DungeonHudManager
import org.bukkit.Bukkit
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.info
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 局内货币与结算管理。
 * `runShards` 表示本次 run 的临时货币，离开/死亡时才结算为永久灵魂碎片。
 */
object ShardRewardManager {

    @Config("config.yml")
    lateinit var config: Configuration
        private set

    private var perRoomClear = 5
    private var perDungeonClear = 50
    private var floorMultiplier = 0.2
    private var deathPenalty = 0.5

    /** 玩家本次运行积累的局内货币 */
    private val runShards = ConcurrentHashMap<UUID, Int>()

    @Awake(LifeCycle.ENABLE)
    fun load() {
        perRoomClear = config.getInt("soul-shards.per-room-clear", 5)
        perDungeonClear = config.getInt("soul-shards.per-dungeon-clear", 50)
        floorMultiplier = config.getDouble("soul-shards.floor-multiplier", 0.2)
        deathPenalty = config.getDouble("soul-shards.death-penalty", 0.5)
        info("[RogueCore] 灵魂碎片配置已加载")
    }

    /**
     * 房间通关时积累碎片
     */
    fun onRoomClear(uuid: UUID, floorNumber: Int, affixMultiplier: Double = 1.0) {
        val base = perRoomClear
        val multiplier = 1.0 + (floorNumber - 1) * floorMultiplier
        val shards = (base * multiplier * affixMultiplier).toInt()
        addRunShards(uuid, shards)
    }

    /**
     * 副本通关时积累额外碎片
     */
    fun onDungeonClear(uuid: UUID, floorNumber: Int, affixMultiplier: Double = 1.0) {
        val base = perDungeonClear
        val multiplier = 1.0 + (floorNumber - 1) * floorMultiplier
        val shards = (base * multiplier * affixMultiplier).toInt()
        addRunShards(uuid, shards)
    }

    /**
     * 直接增加局内货币，用于宝箱/事件等奖励。
     */
    fun addRunShards(uuid: UUID, amount: Int) {
        if (amount <= 0) {
            return
        }
        runShards[uuid] = (runShards[uuid] ?: 0) + amount
        RunPersistenceManager.markDirty()
        notifyHud(uuid, "§6本局碎片 +$amount")
    }

    /**
     * 消耗局内货币，用于商店等系统。
     */
    fun takeRunShards(uuid: UUID, amount: Int): Boolean {
        if (amount <= 0) {
            return true
        }
        val current = runShards[uuid] ?: 0
        if (current < amount) {
            return false
        }
        val remain = current - amount
        if (remain > 0) {
            runShards[uuid] = remain
        } else {
            runShards.remove(uuid)
        }
        RunPersistenceManager.markDirty()
        notifyHud(uuid, "§c本局碎片 -$amount")
        return true
    }

    /**
     * 结算碎片（正常离开/通关）
     * @return 实际获得的碎片数
     */
    fun settle(uuid: UUID): Int {
        val base = runShards.remove(uuid) ?: 0
        if (base <= 0) return 0
        RunPersistenceManager.markDirty()

        val bonus = TalentManager.getShardBonus(uuid)
        val total = (base * bonus).toInt()
        PlayerDataManager.addSoulShards(uuid, total)
        return total
    }

    /**
     * 死亡结算（打折）
     * @return 实际获得的碎片数
     */
    fun settleDeath(uuid: UUID): Int {
        val base = runShards.remove(uuid) ?: 0
        if (base <= 0) return 0
        RunPersistenceManager.markDirty()

        val bonus = TalentManager.getShardBonus(uuid)
        val total = (base * bonus * deathPenalty).toInt()
        PlayerDataManager.addSoulShards(uuid, total)
        return total
    }

    /**
     * 提现部分局内碎片为永久灵魂碎片，并保留剩余局内碎片继续冒险。
     * @return 实际获得的永久灵魂碎片
     */
    fun cashOut(uuid: UUID, ratio: Double): Int {
        val current = runShards[uuid] ?: 0
        if (current <= 0) {
            return 0
        }
        val clampedRatio = ratio.coerceIn(0.0, 1.0)
        val base = (current * clampedRatio).toInt().coerceAtLeast(0)
        if (base <= 0) {
            return 0
        }

        val remain = current - base
        if (remain > 0) {
            runShards[uuid] = remain
        } else {
            runShards.remove(uuid)
        }

        val bonus = TalentManager.getShardBonus(uuid)
        val total = (base * bonus).toInt()
        PlayerDataManager.addSoulShards(uuid, total)
        RunPersistenceManager.markDirty()
        notifyHud(uuid, "§b提前提现 +$total 灵魂碎片")
        return total
    }

    /**
     * 获取当前积累的碎片（未结算）
     */
    fun getRunShards(uuid: UUID): Int {
        return runShards[uuid] ?: 0
    }

    /**
     * 获取当前若立刻结算可获得的永久灵魂碎片预览。
     */
    fun getSettlementPreview(uuid: UUID, deathPenaltyApplied: Boolean = false): Int {
        val base = getRunShards(uuid)
        if (base <= 0) {
            return 0
        }
        val bonus = TalentManager.getShardBonus(uuid)
        val multiplier = if (deathPenaltyApplied) deathPenalty else 1.0
        return (base * bonus * multiplier).toInt()
    }

    /**
     * 获取部分提现预览值。
     */
    fun getCashOutPreview(uuid: UUID, ratio: Double): Int {
        val base = getRunShards(uuid)
        if (base <= 0) {
            return 0
        }
        val bonus = TalentManager.getShardBonus(uuid)
        return ((base * ratio.coerceIn(0.0, 1.0)).toInt() * bonus).toInt()
    }

    /**
     * 清除（不结算，用于异常情况）
     */
    fun clear(uuid: UUID) {
        if (runShards.remove(uuid) != null) {
            RunPersistenceManager.markDirty()
        }
    }

    fun getAllRunShards(): Map<UUID, Int> {
        return runShards.toMap()
    }

    fun restoreRunShards(values: Map<UUID, Int>) {
        runShards.clear()
        for ((uuid, amount) in values) {
            if (amount > 0) {
                runShards[uuid] = amount
            }
        }
        RunPersistenceManager.markDirty()
    }

    private fun notifyHud(uuid: UUID, message: String) {
        val player = Bukkit.getPlayer(uuid) ?: return
        if (!DungeonManager.isInDungeon(player)) {
            return
        }
        DungeonHudManager.pushActionBar(player, message)
    }
}
