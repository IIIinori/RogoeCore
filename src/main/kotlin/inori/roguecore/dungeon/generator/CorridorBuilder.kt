package inori.roguecore.dungeon.generator

import inori.roguecore.dungeon.room.Room
import kotlin.random.Random

/**
 * 走廊生成器 — 连接两个房间中心，生成 L 形走廊
 */
object CorridorBuilder {

    /**
     * 生成连接两个房间的走廊坐标
     * @param roomA 起始房间
     * @param roomB 目标房间
     * @param width 走廊宽度
     * @return 走廊覆盖的所有 (x, z) 坐标
     */
    fun buildCorridor(roomA: Room, roomB: Room, width: Int = 3): Set<Pair<Int, Int>> {
        val coords = mutableSetOf<Pair<Int, Int>>()
        val halfW = width / 2

        val ax = roomA.centerX
        val az = roomA.centerZ
        val bx = roomB.centerX
        val bz = roomB.centerZ

        // 随机选择先水平后垂直，或先垂直后水平
        if (Random.nextBoolean()) {
            // 先水平（X 方向）再垂直（Z 方向）
            addHorizontalSegment(coords, ax, bx, az, halfW)
            addVerticalSegment(coords, az, bz, bx, halfW)
        } else {
            // 先垂直（Z 方向）再水平（X 方向）
            addVerticalSegment(coords, az, bz, ax, halfW)
            addHorizontalSegment(coords, ax, bx, bz, halfW)
        }

        return coords
    }

    /**
     * 水平走廊段
     */
    private fun addHorizontalSegment(
        coords: MutableSet<Pair<Int, Int>>,
        x1: Int, x2: Int, z: Int, halfW: Int
    ) {
        val minX = minOf(x1, x2)
        val maxX = maxOf(x1, x2)
        for (x in minX..maxX) {
            for (dz in -halfW..halfW) {
                coords.add(x to (z + dz))
            }
        }
    }

    /**
     * 垂直走廊段
     */
    private fun addVerticalSegment(
        coords: MutableSet<Pair<Int, Int>>,
        z1: Int, z2: Int, x: Int, halfW: Int
    ) {
        val minZ = minOf(z1, z2)
        val maxZ = maxOf(z1, z2)
        for (z in minZ..maxZ) {
            for (dx in -halfW..halfW) {
                coords.add((x + dx) to z)
            }
        }
    }
}
