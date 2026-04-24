package inori.roguecore.dungeon.room

import taboolib.library.xseries.XMaterial

enum class RoomType(
    val displayName: String,
    val marker: XMaterial
) {
    SPAWN("起点", XMaterial.LIME_WOOL),
    COMBAT("战斗", XMaterial.RED_WOOL),
    ELITE("精英", XMaterial.ORANGE_WOOL),
    BOSS("Boss", XMaterial.MAGENTA_WOOL),
    SHOP("商店", XMaterial.YELLOW_WOOL),
    FORGE("铁匠", XMaterial.ANVIL),
    CHEST("宝箱", XMaterial.CYAN_WOOL),
    REST("休息", XMaterial.LIGHT_BLUE_WOOL),
    EXTRACTION("撤离", XMaterial.LODESTONE),
    CONTRACT("契约", XMaterial.BLACK_WOOL),
    HIDDEN("隐藏", XMaterial.BLUE_WOOL),
    TRIAL("试炼", XMaterial.PURPLE_WOOL),
    GAMBLE("赌局", XMaterial.GREEN_WOOL),
    SHRINE("神龛", XMaterial.WHITE_WOOL)
}
