package inori.roguecore.milestone

import inori.roguecore.boon.BoonResonanceManager
import inori.roguecore.boon.PlayerBoonData
import inori.roguecore.data.ShardRewardManager
import inori.roguecore.dungeon.DungeonManager
import inori.roguecore.dungeon.RunPersistenceManager
import inori.roguecore.dungeon.room.RoomType
import inori.roguecore.dungeon.route.NextFloorRoute
import inori.roguecore.relic.PlayerRelicData
import inori.roguecore.stats.BalanceStatsManager
import inori.roguecore.summary.RunSummaryManager
import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 单局里程碑管理器。
 */
object RunMilestoneManager {

    private val resonanceTags = listOf("狩猎", "壁垒", "鲜血", "风暴", "霜寒", "深渊", "圣辉", "财宝")
    private val achieved = ConcurrentHashMap<UUID, MutableSet<RunMilestoneType>>()
    private val combatStreak = ConcurrentHashMap<UUID, Int>()

    fun getAchieved(uuid: UUID): Set<RunMilestoneType> {
        return achieved[uuid]?.toSet() ?: emptySet()
    }

    fun hasAchieved(uuid: UUID, type: RunMilestoneType): Boolean {
        return achieved[uuid]?.contains(type) == true
    }

    fun getCombatStreak(uuid: UUID): Int {
        return combatStreak[uuid] ?: 0
    }

    fun onBoonChanged(player: Player) {
        if (!DungeonManager.isInDungeon(player)) {
            return
        }
        val boonCount = PlayerBoonData.getBoons(player).size
        if (boonCount >= 10) {
            trigger(player, RunMilestoneType.BOONS_10)
        }
        val maxResonance = resonanceTags.maxOfOrNull { BoonResonanceManager.getLevel(player, it) } ?: 0
        if (maxResonance >= 1) trigger(player, RunMilestoneType.RESONANCE_I)
        if (maxResonance >= 2) trigger(player, RunMilestoneType.RESONANCE_II)
        if (maxResonance >= 3) trigger(player, RunMilestoneType.RESONANCE_III)
    }

    fun onRelicChanged(player: Player) {
        if (!DungeonManager.isInDungeon(player)) {
            return
        }
        if (PlayerRelicData.getRelics(player).size >= 5) {
            trigger(player, RunMilestoneType.RELICS_5)
        }
    }

    fun onRunShardsChanged(uuid: UUID) {
        val player = Bukkit.getPlayer(uuid) ?: return
        if (!DungeonManager.isInDungeon(player)) {
            return
        }
        val amount = ShardRewardManager.getRunShards(uuid)
        if (amount >= 100) trigger(player, RunMilestoneType.SHARDS_100)
        if (amount >= 250) trigger(player, RunMilestoneType.SHARDS_250)
        if (amount >= 500) trigger(player, RunMilestoneType.SHARDS_500)
    }

    fun onRoomCleared(player: Player, roomType: RoomType) {
        if (!DungeonManager.isInDungeon(player)) {
            return
        }
        when (roomType) {
            RoomType.COMBAT -> {
                val next = (combatStreak[player.uniqueId] ?: 0) + 1
                combatStreak[player.uniqueId] = next
                if (next >= 3) {
                    trigger(player, RunMilestoneType.COMBAT_STREAK_3)
                }
            }
            RoomType.ELITE -> {
                combatStreak[player.uniqueId] = 0
                trigger(player, RunMilestoneType.ELITE_CLEAR)
            }
            RoomType.BOSS -> {
                combatStreak[player.uniqueId] = 0
                trigger(player, RunMilestoneType.BOSS_CLEAR)
            }
            else -> combatStreak[player.uniqueId] = 0
        }
        RunPersistenceManager.markDirty()
    }

    fun onHiddenRoomOpened(player: Player) {
        trigger(player, RunMilestoneType.HIDDEN_ROOM)
    }

    fun onRouteSelected(player: Player, route: NextFloorRoute) {
        if (route == NextFloorRoute.EXTREME) {
            trigger(player, RunMilestoneType.EXTREME_ROUTE)
        }
    }

    fun trigger(player: Player, type: RunMilestoneType): Boolean {
        if (!DungeonManager.isInDungeon(player)) {
            return false
        }
        val set = achieved.getOrPut(player.uniqueId) { mutableSetOf() }
        if (!set.add(type)) {
            return false
        }

        RunPersistenceManager.markDirty()
        BalanceStatsManager.recordMilestone(type)
        RunSummaryManager.onMilestone(player.uniqueId, type)
        player.sendMessage("§6§l里程碑达成: §e${type.displayName}")
        player.sendMessage("§7${type.description}")
        if (type.rewardShards > 0) {
            player.sendMessage("§7奖励: §6+${type.rewardShards} §7本局碎片")
            ShardRewardManager.addRunShards(player.uniqueId, type.rewardShards)
        }
        player.playSound(player.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f)
        player.sendTitle("§6${type.displayName}", "§7${type.description}", 10, 50, 10)
        return true
    }

    fun clear(uuid: UUID) {
        var changed = false
        if (achieved.remove(uuid) != null) changed = true
        if (combatStreak.remove(uuid) != null) changed = true
        if (changed) {
            RunPersistenceManager.markDirty()
        }
    }

    fun restore(uuid: UUID, milestones: Set<RunMilestoneType>, streak: Int) {
        if (milestones.isEmpty()) {
            achieved.remove(uuid)
        } else {
            achieved[uuid] = milestones.toMutableSet()
        }
        if (streak > 0) {
            combatStreak[uuid] = streak
        } else {
            combatStreak.remove(uuid)
        }
        RunPersistenceManager.markDirty()
    }

    fun getSnapshot(uuid: UUID): Snapshot {
        return Snapshot(getAchieved(uuid), getCombatStreak(uuid))
    }

    data class Snapshot(
        val milestones: Set<RunMilestoneType>,
        val combatStreak: Int
    )
}
