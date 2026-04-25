package inori.roguecore.milestone

import taboolib.library.xseries.XMaterial

/**
 * 单局内里程碑。
 */
enum class RunMilestoneType(
    val displayName: String,
    val description: String,
    val category: String,
    val rewardShards: Int,
    val icon: XMaterial
) {
    RESONANCE_I(
        "共鸣初醒",
        "首次激活任意 I 阶流派共鸣",
        "构筑",
        20,
        XMaterial.AMETHYST_SHARD
    ),
    RESONANCE_II(
        "共鸣成型",
        "首次激活任意 II 阶流派共鸣",
        "构筑",
        35,
        XMaterial.AMETHYST_CLUSTER
    ),
    RESONANCE_III(
        "共鸣极致",
        "首次激活任意 III 阶流派共鸣",
        "构筑",
        60,
        XMaterial.BEACON
    ),
    BOONS_10(
        "神恩集群",
        "同一局持有 10 个神恩",
        "构筑",
        30,
        XMaterial.ENCHANTED_BOOK
    ),
    RELICS_5(
        "遗物收藏家",
        "同一局持有 5 件遗物",
        "构筑",
        45,
        XMaterial.ECHO_SHARD
    ),
    SHARDS_100(
        "百魂盈囊",
        "本局碎片达到 100",
        "经济",
        15,
        XMaterial.GOLD_INGOT
    ),
    SHARDS_250(
        "魂潮涌动",
        "本局碎片达到 250",
        "经济",
        35,
        XMaterial.GOLD_BLOCK
    ),
    SHARDS_500(
        "金魂满载",
        "本局碎片达到 500",
        "经济",
        70,
        XMaterial.NETHER_STAR
    ),
    COMBAT_STREAK_3(
        "清场节奏",
        "连续清理 3 个普通战斗房",
        "战斗",
        25,
        XMaterial.IRON_SWORD
    ),
    ELITE_CLEAR(
        "精英猎首",
        "击败一个精英房",
        "战斗",
        30,
        XMaterial.CROSSBOW
    ),
    BOSS_CLEAR(
        "Boss 破冠",
        "击败本层 Boss",
        "战斗",
        55,
        XMaterial.DRAGON_HEAD
    ),
    HIDDEN_ROOM(
        "秘门开启",
        "进入并开启隐藏房",
        "探索",
        35,
        XMaterial.ENDER_EYE
    ),
    EXTREME_ROUTE(
        "踏入极境",
        "选择一次极境路线",
        "探索",
        45,
        XMaterial.NETHERITE_UPGRADE_SMITHING_TEMPLATE
    )
}
