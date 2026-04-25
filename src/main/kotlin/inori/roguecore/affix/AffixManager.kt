package inori.roguecore.affix

import inori.roguecore.dungeon.DungeonInstance
import inori.roguecore.dungeon.route.NextFloorRoute
import kotlin.random.Random

/**
 * 词缀管理器 — 选取 + 查询
 */
object AffixManager {

    /**
     * 为副本随机选取词缀
     */
    fun rollAffixes(floorNumber: Int, route: NextFloorRoute? = null): List<DungeonAffix> {
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
            val picked = weightedRandom(advancedPool, route)
            if (picked != null) {
                result.add(picked)
                usedTypes.add(picked.type)
                diffPool.removeAll { it.type == picked.type }
                rewardPool.removeAll { it.type == picked.type }
            }
        }

        // 楼层 4+ 保证至少一个难度词缀
        if (floorNumber >= 4 && result.none { it.difficulty } && diffPool.isNotEmpty()) {
            val picked = weightedRandom(diffPool, route)
            if (picked != null) {
                result.add(picked)
                usedTypes.add(picked.type)
                diffPool.remove(picked)
            }
        }

        // 剩余名额从全池随机
        val allPool = (diffPool + rewardPool).filter { it.type !in usedTypes }.toMutableList()
        while (result.size < count && allPool.isNotEmpty()) {
            val picked = weightedRandom(allPool, route) ?: break
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

    fun getMobDamageMultiplier(instance: DungeonInstance): Double {
        return instance.affixes
            .filter { it.type == AffixType.MOB_DAMAGE_MULTIPLY }
            .fold(1.0) { acc, affix -> acc * affix.value.coerceAtLeast(0.0) }
    }

    fun getCombatShardFlat(instance: DungeonInstance): Int {
        return instance.affixes
            .filter { it.type == AffixType.COMBAT_SHARD_FLAT }
            .sumOf { it.value.toInt().coerceAtLeast(0) }
    }

    fun getEliteKeyChance(instance: DungeonInstance): Double {
        return instance.affixes
            .filter { it.type == AffixType.ELITE_KEY_CHANCE }
            .sumOf { it.value }
            .coerceAtLeast(0.0)
    }

    fun getBossEmberBonus(instance: DungeonInstance): Int {
        return instance.affixes
            .filter { it.type == AffixType.BOSS_EMBER_BONUS }
            .sumOf { it.value.toInt().coerceAtLeast(0) }
    }

    fun getLowHealthPressure(instance: DungeonInstance): Double {
        return instance.affixes
            .filter { it.type == AffixType.LOW_HEALTH_PRESSURE }
            .sumOf { it.value }
            .coerceAtLeast(0.0)
    }

    fun getMobRegenPercent(instance: DungeonInstance): Double = sum(AffixType.MOB_REGEN, instance)

    fun getMobSpawnShield(instance: DungeonInstance): Double = sum(AffixType.MOB_SPAWN_SHIELD, instance)

    fun getMobLowHealthRage(instance: DungeonInstance): Double = sum(AffixType.MOB_LOW_HEALTH_RAGE, instance)

    fun getMobLifesteal(instance: DungeonInstance): Double = sum(AffixType.MOB_LIFESTEAL, instance)

    fun getBossDamageMultiplier(instance: DungeonInstance): Double {
        return instance.affixes
            .filter { it.type == AffixType.BOSS_DAMAGE_MULTIPLY }
            .fold(1.0) { acc, affix -> acc * affix.value.coerceAtLeast(0.0) }
    }

    fun getCombatEmberFlat(instance: DungeonInstance): Int = intSum(AffixType.COMBAT_EMBER_FLAT, instance)

    fun getHiddenLootChance(instance: DungeonInstance): Double = sum(AffixType.HIDDEN_LOOT_CHANCE, instance)

    fun getBoonLuckBonus(instance: DungeonInstance): Double = sum(AffixType.BOON_LUCK, instance)

    fun getBossRelicChance(instance: DungeonInstance): Double = sum(AffixType.BOSS_RELIC_CHANCE, instance)

    fun getChestShardBonus(instance: DungeonInstance): Int = intSum(AffixType.CHEST_SHARD_BONUS, instance)

    fun getExtraEventRoomWeight(instance: DungeonInstance): Int = intSum(AffixType.EXTRA_EVENT_ROOM_WEIGHT, instance)

    fun getExtraChestWeight(instance: DungeonInstance): Int = intSum(AffixType.EXTRA_CHEST_WEIGHT, instance)

    fun getExtraShrineWeight(instance: DungeonInstance): Int = intSum(AffixType.EXTRA_SHRINE_WEIGHT, instance)

    fun getExtraForgeWeight(instance: DungeonInstance): Int = intSum(AffixType.EXTRA_FORGE_WEIGHT, instance)

    fun getExtraHiddenChance(instance: DungeonInstance): Double = sum(AffixType.EXTRA_HIDDEN_CHANCE, instance)

    fun getExtractionRatioModifier(instance: DungeonInstance): Double = sum(AffixType.EXTRACTION_RATIO_MODIFY, instance)

    fun getShopPriceModifier(instance: DungeonInstance): Double = sum(AffixType.SHOP_PRICE_MODIFY, instance)

    fun getSealedChestRewardModifier(instance: DungeonInstance): Int = intSum(AffixType.SEALED_CHEST_REWARD_MODIFY, instance)

    fun getEliteShardFlat(instance: DungeonInstance): Int = intSum(AffixType.ELITE_SHARD_FLAT, instance)

    fun getBossShardFlat(instance: DungeonInstance): Int = intSum(AffixType.BOSS_SHARD_FLAT, instance)

    fun getHiddenShardFlat(instance: DungeonInstance): Int = intSum(AffixType.HIDDEN_SHARD_FLAT, instance)

    fun getChestGearChance(instance: DungeonInstance): Double = sum(AffixType.CHEST_GEAR_CHANCE, instance)

    fun getRelicOfferBonus(instance: DungeonInstance): Int = intSum(AffixType.RELIC_OFFER_BONUS, instance)

    fun getBoonOfferBonus(instance: DungeonInstance): Int = intSum(AffixType.BOON_OFFER_BONUS, instance)

    fun getFloorProphecyPower(instance: DungeonInstance): Int = intSum(AffixType.FLOOR_PROPHECY, instance)

    fun getHealingMultiplier(instance: DungeonInstance): Double {
        val reduction = sum(AffixType.HEALING_REDUCE, instance).coerceIn(0.0, 0.9)
        return 1.0 - reduction
    }

    private fun sum(type: AffixType, instance: DungeonInstance): Double {
        return instance.affixes.filter { it.type == type }.sumOf { it.value }.coerceAtLeast(0.0)
    }

    private fun intSum(type: AffixType, instance: DungeonInstance): Int {
        return instance.affixes.filter { it.type == type }.sumOf { it.value.toInt().coerceAtLeast(0) }
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

    private fun weightedRandom(pool: List<DungeonAffix>, route: NextFloorRoute? = null): DungeonAffix? {
        if (pool.isEmpty()) return null
        val weighted = pool.map { affix ->
            affix to (affix.weight + (route?.affixWeightModifiers?.get(affix.type) ?: 0)).coerceAtLeast(1)
        }
        val totalWeight = weighted.sumOf { it.second }
        var roll = Random.nextInt(totalWeight)
        for ((affix, weight) in weighted) {
            roll -= weight
            if (roll < 0) return affix
        }
        return weighted.last().first
    }
}
