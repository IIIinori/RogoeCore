package inori.roguecore.dungeon.route

import inori.roguecore.dungeon.room.RoomType
import taboolib.library.xseries.XMaterial

enum class NextFloorRoute(
    val displayName: String,
    val icon: XMaterial,
    val description: List<String>,
    val roomWeightModifiers: Map<RoomType, Int>,
    val hiddenRoomChanceBonus: Double = 0.0,
    val requiredUnlockId: String? = null
) {
    BATTLE(
        displayName = "征伐路线",
        icon = XMaterial.CROSSBOW,
        description = listOf(
            "§7下一层战斗房与精英房更多",
            "§7更适合追求掉落与高压推进"
        ),
        roomWeightModifiers = mapOf(
            RoomType.COMBAT to 18,
            RoomType.ELITE to 8,
            RoomType.SHOP to -4,
            RoomType.CHEST to -4,
            RoomType.REST to -2
        )
    ),
    OPPORTUNITY(
        displayName = "机遇路线",
        icon = XMaterial.CLOCK,
        description = listOf(
            "§7下一层事件房明显增多",
            "§7更容易遇到商店、铁匠与契约"
        ),
        roomWeightModifiers = mapOf(
            RoomType.SHOP to 6,
            RoomType.FORGE to 5,
            RoomType.EXTRACTION to 2,
            RoomType.CONTRACT to 4,
            RoomType.SHRINE to 4,
            RoomType.REST to 3,
            RoomType.COMBAT to -12,
            RoomType.ELITE to -3
        )
    ),
    TREASURE(
        displayName = "藏宝路线",
        icon = XMaterial.CHEST,
        description = listOf(
            "§7下一层更偏向宝箱与奇遇",
            "§7隐藏房出现概率也会提高"
        ),
        roomWeightModifiers = mapOf(
            RoomType.CHEST to 10,
            RoomType.GAMBLE to 3,
            RoomType.EXTRACTION to 1,
            RoomType.REST to 2,
            RoomType.COMBAT to -8,
            RoomType.SHOP to -2
        ),
        hiddenRoomChanceBonus = 0.25
    ),
    EXTREME(
        displayName = "极境路线",
        icon = XMaterial.NETHERITE_UPGRADE_SMITHING_TEMPLATE,
        description = listOf(
            "§7下一层精英、契约与高价值房更多",
            "§7风险极高，但更容易滚起高强度构筑"
        ),
        roomWeightModifiers = mapOf(
            RoomType.ELITE to 12,
            RoomType.CONTRACT to 6,
            RoomType.CHEST to 4,
            RoomType.COMBAT to 6,
            RoomType.REST to -4,
            RoomType.SHOP to -3
        ),
        hiddenRoomChanceBonus = 0.15,
        requiredUnlockId = "extreme_route"
    )
}
