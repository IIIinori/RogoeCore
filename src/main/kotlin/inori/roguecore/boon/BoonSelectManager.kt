package inori.roguecore.boon

import inori.roguecore.relic.RelicEffectHandler
import inori.roguecore.talent.TalentManager
import org.bukkit.entity.Player
import kotlin.random.Random

/**
 * Boon 选择管理器 — 负责随机选取 Boon 供玩家选择
 */
object BoonSelectManager {

    /**
     * 房间通关后触发 Boon 选择
     */
    fun offerBoonSelection(player: Player, count: Int = 3) {
        val effectiveCount = (count + RelicEffectHandler.getBoonOfferBonus(player)).coerceIn(1, 4)
        val candidates = rollBoons(effectiveCount, player)

        if (candidates.isEmpty()) {
            player.sendMessage("§7没有可用的神恩了...")
            return
        }

        inori.roguecore.ui.BoonSelectUI.open(player, candidates)
    }

    /**
     * 按稀有度权重随机选取 Boon
     * @param count 需要的数量
     * @param player 目标玩家（用于检查已满级的 Boon）
     * @return 选中的 Boon 列表
     */
    fun rollBoons(count: Int, player: Player): List<Boon> {
        val owned = PlayerBoonData.getBoons(player)

        // 已满级的 Boon ID 集合
        val maxedIds = owned.filter { !it.canUpgrade }.map { it.boon.id }.toSet()

        // 候选池：排除已满级的
        val pool = BoonRegistry.getAll().filter { it.id !in maxedIds }
        if (pool.isEmpty()) return emptyList()

        // 加权随机选取（不重复）
        val selected = mutableListOf<Boon>()
        val remaining = pool.toMutableList()
        val dungeonLuck = inori.roguecore.dungeon.DungeonManager.getPlayerDungeon(player)?.let { inori.roguecore.affix.AffixManager.getBoonLuckBonus(it) } ?: 0.0
        val luckBonus = TalentManager.getBoonLuckBonus(player.uniqueId) + RelicEffectHandler.getBoonRarityLuckBonus(player) + dungeonLuck

        repeat(count.coerceAtMost(remaining.size)) {
            val boon = weightedRandom(remaining, luckBonus)
            if (boon != null) {
                selected.add(boon)
                remaining.remove(boon)
            }
        }

        return selected
    }

    /**
     * 加权随机
     */
    private fun weightedRandom(pool: List<Boon>, luckBonus: Double): Boon? {
        if (pool.isEmpty()) return null
        val weighted = pool.associateWith { boon ->
            adjustedWeight(boon, luckBonus)
        }
        val totalWeight = weighted.values.sum()
        var roll = Random.nextInt(totalWeight)

        for (boon in pool) {
            roll -= weighted[boon] ?: boon.rarity.weight
            if (roll < 0) return boon
        }
        return pool.last()
    }

    private fun adjustedWeight(boon: Boon, luckBonus: Double): Int {
        val base = boon.rarity.weight.toDouble()
        val luckFactor = 1.0 + (luckBonus / 100.0)
        val multiplier = when (boon.rarity) {
            BoonRarity.COMMON -> 1.0
            BoonRarity.RARE -> luckFactor
            BoonRarity.EPIC -> luckFactor * 1.35
            BoonRarity.LEGENDARY -> luckFactor * 1.75
        }
        return (base * multiplier).toInt().coerceAtLeast(1)
    }
}
