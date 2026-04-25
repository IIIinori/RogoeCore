package inori.roguecore.dungeon.room

import inori.roguecore.combat.RoomState
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 地牢房间数据
 * @param id 房间编号
 * @param x 左上角 X（相对于地牢原点）
 * @param z 左上角 Z（相对于地牢原点）
 * @param width X 方向宽度
 * @param depth Z 方向深度
 * @param type 房间类型
 * @param connections 连接的房间 ID 列表
 */
data class Room(
    val id: Int,
    val x: Int,
    val z: Int,
    val width: Int,
    val depth: Int,
    var type: RoomType = RoomType.COMBAT,
    val connections: MutableList<Int> = mutableListOf()
) {
    val centerX: Int get() = x + width / 2
    val centerZ: Int get() = z + depth / 2

    /** 房间战斗状态 */
    var state: RoomState = RoomState.IDLE

    /** 房间内存活的怪物 UUID */
    val aliveMobs: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    /** 本次激活时总共生成的怪物数量，用于 HUD 进度显示 */
    var spawnedMobCount: Int = 0

    /** 该房间是否需要战斗（战斗/精英/Boss 房间） */
    val isCombatRoom: Boolean
        get() = type == RoomType.COMBAT || type == RoomType.ELITE || type == RoomType.BOSS

    /** 检查某个坐标是否在房间内部（不含墙壁） */
    fun contains(px: Int, pz: Int): Boolean {
        return px in (x + 1) until (x + width - 1) && pz in (z + 1) until (z + depth - 1)
    }
}
