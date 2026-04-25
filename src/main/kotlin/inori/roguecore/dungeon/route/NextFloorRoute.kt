package inori.roguecore.dungeon.route

import inori.roguecore.affix.AffixType
import inori.roguecore.dungeon.room.RoomType
import taboolib.library.xseries.XMaterial

/**
 * 通关后选择的下一层路线。
 *
 * 路线同时影响：房间权重、隐藏房概率、副本词缀倾向、事件词缀 family 倾向。
 */
enum class NextFloorRoute(
    val displayName: String,
    val icon: XMaterial,
    val description: List<String>,
    val roomWeightModifiers: Map<RoomType, Int>,
    val hiddenRoomChanceBonus: Double = 0.0,
    val affixWeightModifiers: Map<AffixType, Int> = emptyMap(),
    val eventFamilyModifiers: Map<String, Int> = emptyMap(),
    val riskLevel: Int = 1,
    val rewardLevel: Int = 1,
    val requiredUnlockId: String? = null
) {
    STABLE(
        displayName = "稳定推进",
        icon = XMaterial.COMPASS,
        description = listOf(
            "§7均衡房间分布，低风险推进",
            "§7适合构筑尚未成型或想稳住节奏"
        ),
        roomWeightModifiers = mapOf(
            RoomType.COMBAT to 4,
            RoomType.REST to 4,
            RoomType.SHOP to 2,
            RoomType.CHEST to 2,
            RoomType.CONTRACT to -3,
            RoomType.ELITE to -2
        ),
        affixWeightModifiers = mapOf(
            AffixType.HEALING_REDUCE to -8,
            AffixType.VOID_FIELD to -8,
            AffixType.MOB_DAMAGE_MULTIPLY to -6,
            AffixType.MOB_HP_MULTIPLY to -4
        ),
        eventFamilyModifiers = mapOf("REST_TRAINING" to 6, "SHOP_DISCOUNT" to 4),
        riskLevel = 1,
        rewardLevel = 1
    ),
    BATTLE(
        displayName = "精英征伐",
        icon = XMaterial.CROSSBOW,
        description = listOf(
            "§7战斗房与精英房更多",
            "§7更适合狩猎、风暴和高爆发装备"
        ),
        roomWeightModifiers = mapOf(
            RoomType.COMBAT to 16,
            RoomType.ELITE to 12,
            RoomType.CHEST to 2,
            RoomType.REST to -3,
            RoomType.SHOP to -3
        ),
        affixWeightModifiers = mapOf(
            AffixType.MOB_DAMAGE_MULTIPLY to 8,
            AffixType.MOB_LOW_HEALTH_RAGE to 8,
            AffixType.ELITE_KEY_CHANCE to 10,
            AffixType.HIDDEN_LOOT_CHANCE to 7,
            AffixType.WEAPON_LUCK to 6
        ),
        eventFamilyModifiers = mapOf("TRIAL_FORGE" to 5, "CHEST_GEAR" to 4),
        riskLevel = 3,
        rewardLevel = 3
    ),
    TREASURE(
        displayName = "秘藏宝库",
        icon = XMaterial.CHEST,
        description = listOf(
            "§7宝箱、赌局与隐藏房倾向提高",
            "§7适合财宝流、隐藏钥匙和宝箱遗物"
        ),
        roomWeightModifiers = mapOf(
            RoomType.CHEST to 13,
            RoomType.GAMBLE to 5,
            RoomType.EXTRACTION to 2,
            RoomType.REST to 2,
            RoomType.COMBAT to -8,
            RoomType.SHOP to -2
        ),
        hiddenRoomChanceBonus = 0.25,
        affixWeightModifiers = mapOf(
            AffixType.SHARD_MULTIPLY to 10,
            AffixType.CHEST_SHARD_BONUS to 10,
            AffixType.HIDDEN_LOOT_CHANCE to 8,
            AffixType.ELITE_KEY_CHANCE to 5
        ),
        eventFamilyModifiers = mapOf(
            "CHEST_SHARD" to 8,
            "CHEST_BOON" to 8,
            "CHEST_FORGE" to 8,
            "CHEST_GEAR" to 8,
            "GAMBLE_GEAR" to 6,
            "HIDDEN_RELIC" to 8
        ),
        riskLevel = 2,
        rewardLevel = 4
    ),
    PILGRIMAGE(
        displayName = "神龛巡礼",
        icon = XMaterial.BEACON,
        description = listOf(
            "§7神龛、休息和试炼房更多",
            "§7适合圣辉、壁垒、霜寒与诅咒净化"
        ),
        roomWeightModifiers = mapOf(
            RoomType.SHRINE to 10,
            RoomType.REST to 8,
            RoomType.TRIAL to 6,
            RoomType.FORGE to 2,
            RoomType.COMBAT to -10,
            RoomType.ELITE to -2
        ),
        hiddenRoomChanceBonus = 0.08,
        affixWeightModifiers = mapOf(
            AffixType.BOON_LUCK to 10,
            AffixType.EXTRA_BOON to 8,
            AffixType.BOSS_RELIC_CHANCE to 5,
            AffixType.HEALING_REDUCE to -6
        ),
        eventFamilyModifiers = mapOf(
            "SHRINE_PURIFY" to 10,
            "REST_TRAINING" to 9,
            "TRIAL_FORGE" to 6,
            "SHRINE_RELIC" to 8
        ),
        riskLevel = 2,
        rewardLevel = 3
    ),
    OPPORTUNITY(
        displayName = "黑市远征",
        icon = XMaterial.CLOCK,
        description = listOf(
            "§7商店、铁匠、契约和赌局更多",
            "§7适合碎片充足、折扣遗物和高风险收益"
        ),
        roomWeightModifiers = mapOf(
            RoomType.SHOP to 8,
            RoomType.FORGE to 7,
            RoomType.CONTRACT to 6,
            RoomType.GAMBLE to 4,
            RoomType.EXTRACTION to 2,
            RoomType.COMBAT to -12,
            RoomType.REST to -2
        ),
        affixWeightModifiers = mapOf(
            AffixType.COMBAT_SHARD_FLAT to 7,
            AffixType.COMBAT_EMBER_FLAT to 8,
            AffixType.BOSS_EMBER_BONUS to 7,
            AffixType.MOB_LIFESTEAL to 5
        ),
        eventFamilyModifiers = mapOf(
            "SHOP_DISCOUNT" to 9,
            "SHOP_RELIC" to 8,
            "SHOP_MATERIAL" to 8,
            "SHOP_BLACK" to 8,
            "GAMBLE_SAFE" to 6,
            "FORGE_SOUL" to 6
        ),
        riskLevel = 3,
        rewardLevel = 4
    ),
    EXTREME(
        displayName = "极境路线",
        icon = XMaterial.NETHERITE_UPGRADE_SMITHING_TEMPLATE,
        description = listOf(
            "§7危险词缀、精英和高价值房明显提高",
            "§7风险极高，但适合成型构筑冲高收益"
        ),
        roomWeightModifiers = mapOf(
            RoomType.ELITE to 14,
            RoomType.CONTRACT to 8,
            RoomType.CHEST to 5,
            RoomType.COMBAT to 8,
            RoomType.TRIAL to 4,
            RoomType.REST to -5,
            RoomType.SHOP to -3
        ),
        hiddenRoomChanceBonus = 0.15,
        affixWeightModifiers = mapOf(
            AffixType.MOB_HP_MULTIPLY to 8,
            AffixType.MOB_DAMAGE_MULTIPLY to 9,
            AffixType.VOID_FIELD to 8,
            AffixType.BOSS_DAMAGE_MULTIPLY to 8,
            AffixType.HIDDEN_LOOT_CHANCE to 8,
            AffixType.BOON_LUCK to 8
        ),
        eventFamilyModifiers = mapOf(
            "CHEST_GEAR" to 7,
            "TRIAL_FORGE" to 7,
            "HIDDEN_RELIC" to 7,
            "GAMBLE_GEAR" to 6
        ),
        riskLevel = 5,
        rewardLevel = 5,
        requiredUnlockId = "extreme_route"
    )
}
