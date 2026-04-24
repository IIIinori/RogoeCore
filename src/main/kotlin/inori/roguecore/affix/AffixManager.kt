package inori.roguecore.affix

import inori.roguecore.dungeon.DungeonInstance
import kotlin.random.Random

/**
 * 词缀管理器 — 选取 + 查询
 */
object AffixManager {

    /**
     * 为副本随机选取词缀
     */
    fun rollAffixes(floorNumber: Int): List<DungeonAffix> {
        val range = AffixRegistry.getAffixCountRange(floorNumber)
        val minCount = range[0]
        val maxCount = range[1]

        if (maxCount <= 0) return emptyList()

        val count = if (minCount >= maxCount) minCount else Random.nextInt(minCount, maxCount + 1)
        if (count <= 0) return emptyList()

        val result = mutableListOf<DungeonAffix>()
        val usedTypes = mutableSetOf<AffixType>()

        val diffPool = AffixRegistry.getDifficultyAffixes(floorNumber).toMutableList()
        val rewardPool = AffixRegistry.getRewardAffixes(floorNumber).toMutableList()
        val advancedPool = AffixRegistry.getAdvancedAffixes(floorNumber).toMutableList()

        // 楼层 10+ 保证至少一个高级词缀，让后期战斗层气质明显拉开。
        if (floorNumber >= 10 && advancedPool.isNotEmpty()) {
            val picked = weightedRandom(advancedPool)
            if (picked != null) {
                result.add(picked)
                usedTypes.add(picked.type)
                diffPool.removeAll { it.type == picked.type }
                rewardPool.removeAll { it.type == picked.type }
            }
        }

        // 楼层 4+ 保证至少一个难度词缀
        if (floorNumber >= 4 && result.none { it.difficulty } && diffPool.isNotEmpty()) {
            val picked = weightedRandom(diffPool)
            if (picked != null) {
                result.add(picked)
                usedTypes.add(picked.type)
                diffPool.remove(picked)
            }
        }

        // 剩余名额从全池随机
        val allPool = (diffPool + rewardPool).filter { it.type !in usedTypes }.toMutableList()
        while (result.size < count && allPool.isNotEmpty()) {
            val picked = weightedRandom(allPool) ?: break
            result.add(picked)
            usedTypes.add(picked.type)
            allPool.removeAll { it.type == picked.type }
        }

        return result
    }

    // ========== 查询方法 ==========

    /**
     * 获取怪物血量倍率
     */
    fun getMobHpMultiplier(instance: DungeonInstance): Double {
        return instance.affixes
            .filter { it.type == AffixType.MOB_HP_MULTIPLY }
            .fold(1.0) { acc, affix -> acc * affix.value }
    }

    /**
     * 获取怪物数量倍率
     */
    fun getMobCountMultiplier(instance: DungeonInstance): Double {
        return instance.affixes
            .filter { it.type == AffixType.MOB_COUNT_MULTIPLY }
            .fold(1.0) { acc, affix -> acc * affix.value }
    }

    /**
     * 获取怪物移速加成
     */
    fun getMobSpeedBonus(instance: DungeonInstance): Double {
        return instance.affixes
            .filter { it.type == AffixType.MOB_SPEED }
            .sumOf { it.value }
    }

    /**
     * 获取碎片倍率
     */
    fun getShardMultiplier(instance: DungeonInstance): Double {
        return instance.affixes
            .filter { it.type == AffixType.SHARD_MULTIPLY }
            .fold(1.0) { acc, affix -> acc * affix.value }
    }

    /**
     * 获取武器掉率倍率
     */
    fun getWeaponLuckMultiplier(instance: DungeonInstance): Double {
        return instance.affixes
            .filter { it.type == AffixType.WEAPON_LUCK }
            .fold(1.0) { acc, affix -> acc * affix.value }
    }

    /**
     * 获取额外神恩次数
     */
    fun getExtraBoonCount(instance: DungeonInstance): Int {
        return instance.affixes
            .filter { it.type == AffixType.EXTRA_BOON }
            .sumOf { it.value.toInt().coerceAtLeast(0) }
    }

    /**
     * 是否有某种词缀
     */
    fun hasAffix(instance: DungeonInstance, type: AffixType): Boolean {
        return instance.affixes.any { it.type == type }
    }

    /**
     * 获取某种词缀的数值（多个同类型取第一个）
     */
    fun getAffixValue(instance: DungeonInstance, type: AffixType): Double {
        return instance.affixes.firstOrNull { it.type == type }?.value ?: 0.0
    }

    // ========== 工具方法 ==========

    private fun weightedRandom(pool: List<DungeonAffix>): DungeonAffix? {
        if (pool.isEmpty()) return null
        val totalWeight = pool.sumOf { it.weight }
        var roll = Random.nextInt(totalWeight)
        for (affix in pool) {
            roll -= affix.weight
            if (roll < 0) return affix
        }
        return pool.last()
    }
}
