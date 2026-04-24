package inori.roguecore.dungeon.room

import inori.roguecore.dungeon.floor.FloorTheme
import org.bukkit.Location
import org.bukkit.World
import taboolib.library.xseries.XMaterial

/**
 * 房间内容填充器
 * P0 阶段：仅在房间中心放置类型标记方块
 * P1 阶段：对接 MythicMobs 生成怪物
 */
object RoomPopulator {

    /**
     * 填充房间内容
     */
    fun populate(room: Room, world: World, origin: Location, theme: FloorTheme, roomHeight: Int) {
        val baseY = origin.blockY

        // 在房间中心地板上放置类型标记方块
        val cx = origin.blockX + room.centerX
        val cz = origin.blockZ + room.centerZ
        world.getBlockAt(cx, baseY, cz).setType(room.type.marker.get() ?: XMaterial.WHITE_WOOL.get()!!, false)

        // 按房间尺寸铺顶灯网格，避免大房间只有四角点灯时亮度不足
        val lightY = baseY + roomHeight + 1
        val xPositions = createLightPositions(room.x + 2, room.x + room.width - 3)
        val zPositions = createLightPositions(room.z + 2, room.z + room.depth - 3)

        for (lx in xPositions) {
            for (lz in zPositions) {
                world.getBlockAt(origin.blockX + lx, lightY, origin.blockZ + lz)
                    .setType(theme.light.get() ?: XMaterial.SEA_LANTERN.get()!!, true)
            }
        }
    }

    private fun createLightPositions(min: Int, max: Int): List<Int> {
        if (max <= min) {
            return listOf((min + max) / 2)
        }

        val positions = linkedSetOf<Int>()
        val center = (min + max) / 2
        val span = max - min

        when {
            span <= 4 -> {
                positions += center
            }
            span <= 8 -> {
                positions += min
                positions += max
            }
            else -> {
                var current = min
                while (current <= max) {
                    positions += current
                    current += 5
                }
                positions += center
                positions += max
            }
        }

        return positions.toList()
    }
}
