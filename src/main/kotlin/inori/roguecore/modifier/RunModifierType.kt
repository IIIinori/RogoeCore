package inori.roguecore.modifier

import taboolib.library.xseries.XMaterial

/**
 * 本局临时修正。
 *
 * 由事件房选择产生，影响后续房间/事件，让事件形成短链条。
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
    )
}
