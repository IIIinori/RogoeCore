package inori.roguecore.accessory

import inori.roguecore.item.DungeonLootAttributeDefinition
import inori.roguecore.item.DungeonLootSource
import taboolib.library.xseries.XMaterial

data class AccessoryRarity(
    val id: String,
    val displayName: String,
    val color: String,
    val weight: Int,
    val multiplier: Double
)

data class AccessoryEffect(
    val type: AccessoryEffectType,
    val value: Double,
    val chance: Double = 1.0,
    val tag: String = ""
) {
    fun describe(): String {
        val valueText = format(value)
        return when (type) {
            AccessoryEffectType.SOUL_DEBT_INTEREST_REDUCTION -> "§a债务: §7每房利息 §a-$valueText"
            AccessoryEffectType.SOUL_DEBT_DEADLINE_BONUS -> "§a债务: §7新债务期限 §b+$valueText §7房"
            AccessoryEffectType.SOUL_DEBT_PENALTY_SHIELD_CHANCE -> "§d担保: §7逾期时 §d$valueText% §7抵消诅咒"
            AccessoryEffectType.DELAYED_REWARD_BONUS_PERCENT -> "§d托管: §7兑现奖励 §a+$valueText%"
            AccessoryEffectType.DELAYED_REWARD_ROOM_REDUCTION -> "§d托管: §7等待房间 §a-$valueText"
            AccessoryEffectType.DELAYED_REWARD_EARLY_KEEP_PERCENT -> "§d托管: §7提前结算保留 §a+$valueText%"
            AccessoryEffectType.PROPHECY_REWARD_BONUS_PERCENT -> "§d预言: §7完成奖励 §a+$valueText%"
            AccessoryEffectType.PROPHECY_MISS_REDUCTION -> "§d预言: §7失败损失 §a-$valueText"
            AccessoryEffectType.ROUTE_CHAIN_TOLERANCE_BONUS -> "§a路线: §7连锁容错 §b+$valueText"
            AccessoryEffectType.ROUTE_CHAIN_REWARD_BONUS -> "§a路线: §7完成奖励 §a+$valueText"
            AccessoryEffectType.BOON_OFFER_BONUS -> "§d神恩: §7候选数量 §b+$valueText"
            AccessoryEffectType.BOON_ECHO_CHANCE -> "§d神恩: §7获得时 §d$valueText% §7生成回响"
            AccessoryEffectType.BOON_MUTATION_CHANCE -> "§5神恩: §7获得时 §d$valueText% §7生成变质"
            AccessoryEffectType.ACCESSORY_DROP_CHANCE -> "§6掉落: §7饰品掉率 §a+$valueText%"
            AccessoryEffectType.ACCESSORY_RARITY_LUCK -> "§6掉落: §7饰品升品概率 §a+$valueText%"
            AccessoryEffectType.HIDDEN_ACCESSORY_BONUS -> "§9隐藏: §7隐藏房饰品额外判定 §a+$valueText%"
        }
    }

    private fun format(number: Double): String {
        return if (number == number.toLong().toDouble()) number.toLong().toString() else String.format("%.1f", number)
    }
}

data class AccessoryDefinition(
    val id: String,
    val name: String,
    val material: XMaterial,
    val slot: AccessorySlot,
    val tags: Set<String>,
    val sources: Set<DungeonLootSource>,
    val minFloor: Int,
    val maxFloor: Int,
    val weight: Int,
    val lore: List<String>,
    val attributes: List<DungeonLootAttributeDefinition>,
    val effects: List<AccessoryEffect>
) {
    fun matches(source: DungeonLootSource, floor: Int): Boolean {
        return source in sources && floor in minFloor..maxFloor
    }
}

data class AccessoryInstance(
    val definition: AccessoryDefinition,
    val rarity: AccessoryRarity,
    val source: DungeonLootSource,
    val floor: Int,
    val rolledAttributes: Map<String, Double>,
    val effects: List<AccessoryEffect>,
    val score: Double
)
