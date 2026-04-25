package inori.roguecore.modifier

import taboolib.library.xseries.XMaterial

/**
 * 本局临时修正。
 *
 * 由事件房、神恩、遗物或词缀选择产生，影响后续房间/事件，让 run 形成短链条。
 */
enum class RunModifierType(
    val displayName: String,
    val description: String,
    val icon: XMaterial,
    val positive: Boolean
) {
    SHOP_DEBT(
        "商店赊账",
        "之后清房时会偿还本局碎片债务",
        XMaterial.WRITABLE_BOOK,
        false
    ),
    SEALED_CHEST_PRESSURE(
        "封印宝箱回响",
        "下一个战斗房通关时获得额外本局碎片",
        XMaterial.CHEST,
        true
    ),
    GAMBLE_STREAK(
        "赌局加注",
        "下一次赌局收益提高，但失败惩罚也提高",
        XMaterial.EMERALD,
        true
    ),
    SHRINE_BLESSING(
        "神龛祝福",
        "后续数个房间通关时恢复生命并获得额外碎片",
        XMaterial.BEACON,
        true
    ),
    FORGE_OVERDRIVE(
        "炉火过载",
        "下一次铁匠锻造碎片价格降低",
        XMaterial.BLAST_FURNACE,
        true
    ),
    SOUL_DEBT(
        "灵魂债务",
        "债务会随房间推进计息，到期自动偿还，不足则触发惩罚",
        XMaterial.BOOK,
        false
    ),
    DELAYED_REWARD(
        "托管奖励",
        "奖励被押后结算，撑过指定房间后兑现更高收益",
        XMaterial.ENDER_CHEST,
        true
    ),
    ROOM_PROPHECY(
        "房间预言",
        "承诺在期限内进入指定房间，完成后兑现预言奖励",
        XMaterial.ENDER_EYE,
        true
    ),
    ROUTE_CHAIN(
        "路线连锁",
        "按顺序进入指定房间，完成整条路线后获得独特奖励",
        XMaterial.FILLED_MAP,
        true
    ),
    BOON_ECHO(
        "神恩回响",
        "下一次神恩选择会被额外复制一次",
        XMaterial.ECHO_SHARD,
        true
    ),
    BOON_MUTATION(
        "神恩变质",
        "下一次神恩选择获得额外候选，选择后消耗",
        XMaterial.DRAGON_BREATH,
        true
    ),
    RELIC_CHARGE_RULE(
        "遗物充能",
        "遗物正在积蓄充能，满层后触发特殊奖励或抵消代价",
        XMaterial.AMETHYST_CLUSTER,
        true
    ),
    SEALED_FUTURE(
        "封存未来",
        "当前提前取得收益，未来一次选择会被征税或封锁",
        XMaterial.CRYING_OBSIDIAN,
        false
    )
}
