package inori.roguecore.event

import inori.roguecore.dungeon.room.RoomType

/**
 * 副本事件词缀。
 *
 * 只影响特定事件房的内容，不参与战斗词缀结算。
 */
data class DungeonEventAffix(
    val id: String,
    val name: String,
    val description: String,
    val rooms: Set<RoomType>,
    val minFloor: Int,
    val weight: Int,
    val family: String = "",
    val power: Int = 1
)
