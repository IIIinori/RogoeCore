package inori.roguecore.dungeon.generator

import inori.roguecore.affix.DungeonAffix
import inori.roguecore.dungeon.DungeonConfig
import inori.roguecore.dungeon.DungeonInstance
import inori.roguecore.dungeon.room.Room
import inori.roguecore.dungeon.room.RoomPopulator
import inori.roguecore.dungeon.room.RoomType
import inori.roguecore.event.DungeonEventAffix
import org.bukkit.Location
import org.bukkit.World
import taboolib.library.xseries.XMaterial
import kotlin.random.Random

/**
 * 地牢生成器 — 整合 BSP 算法、走廊生成、方块放置
 */
class DungeonGenerator(
    private val world: World,
    private val origin: Location,
    private val config: DungeonConfig
) {

    private val theme = config.theme
    private val baseY = config.floorLevel

    /**
     * 生成完整地牢并返回实例
     * @param instanceId 副本实例 ID
     */
    fun generate(
        instanceId: String,
        affixes: List<DungeonAffix> = emptyList(),
        eventAffixes: List<DungeonEventAffix> = emptyList()
    ): DungeonInstance {
        // 1. BSP 生成房间布局
        val bsp = BSPTree(
            totalWidth = config.dungeonWidth,
            totalDepth = config.dungeonDepth,
            minPartitionSize = config.minPartitionSize,
            minRoomSize = config.minRoomSize,
            maxDepth = config.maxBSPDepth
        )
        val rooms = bsp.generate()
        val connectionPairs = bsp.getConnectionPairs()

        if (rooms.isEmpty()) {
            throw IllegalStateException("BSP 未能生成任何房间")
        }

        // 2. 分配房间类型
        assignRoomTypes(rooms)

        // 3. 收集走廊坐标
        val corridorCoords = mutableSetOf<Pair<Int, Int>>()
        for ((roomA, roomB) in connectionPairs) {
            corridorCoords.addAll(
                CorridorBuilder.buildCorridor(roomA, roomB, config.corridorWidth)
            )
        }

        // 4. 放置方块
        buildRooms(rooms)
        buildCorridors(corridorCoords, rooms)
        reinforceShell(rooms, corridorCoords)
        lightCorridors(corridorCoords, rooms)
        populateRooms(rooms)

        // 5. 构建实例
        return DungeonInstance(
            id = instanceId,
            world = world,
            origin = origin,
            rooms = rooms,
            corridorCoords = corridorCoords,
            config = config,
            affixes = affixes,
            eventAffixes = eventAffixes
        )
    }

    fun restore(
        instanceId: String,
        rooms: List<Room>,
        corridorCoords: Set<Pair<Int, Int>>,
        affixes: List<DungeonAffix> = emptyList(),
        eventAffixes: List<DungeonEventAffix> = emptyList(),
        hiddenKeys: Int = 0,
        completed: Boolean = false
    ): DungeonInstance {
        buildRooms(rooms)
        buildCorridors(corridorCoords, rooms)
        reinforceShell(rooms, corridorCoords)
        lightCorridors(corridorCoords, rooms)
        populateRooms(rooms)

        return DungeonInstance(
            id = instanceId,
            world = world,
            origin = origin,
            rooms = rooms,
            corridorCoords = corridorCoords,
            config = config,
            affixes = affixes,
            eventAffixes = eventAffixes,
            hiddenKeys = hiddenKeys
        ).also {
            it.completed = completed
        }
    }

    /**
     * 分配房间类型
     */
    private fun assignRoomTypes(rooms: List<Room>) {
        if (rooms.isEmpty()) return

        val spawnRoom = rooms.first()
        spawnRoom.type = RoomType.SPAWN

        if (rooms.size <= 1) {
            return
        }

        // 离起点最远的房间固定作为 Boss 终点，保证每局都有明确收尾。
        val bossRoom = rooms.drop(1).maxByOrNull { room ->
            val dx = room.centerX - spawnRoom.centerX
            val dz = room.centerZ - spawnRoom.centerZ
            dx * dx + dz * dz
        }

        bossRoom?.type = RoomType.BOSS

        // 起点和 Boss 以外的房间按权重随机分配。
        val middleRooms = rooms.drop(1).filter { it !== bossRoom }
        val baseWeights = RoomTypeWeights.getWeights(config.roomWeightModifiers)
            .filterKeys { it != RoomType.SPAWN && it != RoomType.BOSS }
        val assignedCounts = mutableMapOf<RoomType, Int>()

        for (room in middleRooms) {
            val adjustedWeights = RoomTypeWeights.applyDiversityRules(baseWeights, assignedCounts)
                .ifEmpty { baseWeights }
            room.type = rollRoomType(adjustedWeights)
            assignedCounts[room.type] = (assignedCounts[room.type] ?: 0) + 1
        }

        assignHiddenRoom(spawnRoom, middleRooms)
    }

    private fun assignHiddenRoom(spawnRoom: Room, rooms: List<Room>) {
        if (!config.hiddenRoomEnabled || rooms.size < 4) {
            return
        }
        if (Random.nextDouble() > config.hiddenRoomChance) {
            return
        }

        val candidates = rooms
            .sortedByDescending { room ->
                val dx = room.centerX - spawnRoom.centerX
                val dz = room.centerZ - spawnRoom.centerZ
                dx * dx + dz * dz
            }
            .take(maxOf(1, rooms.size / 2))

        candidates.randomOrNull()?.type = RoomType.HIDDEN
    }

    /**
     * 按权重随机选取房间类型
     */
    private fun rollRoomType(weights: Map<RoomType, Int>): RoomType {
        val totalWeight = weights.values.sum()
        if (totalWeight <= 0) return RoomType.COMBAT

        var roll = Random.nextInt(totalWeight)
        for ((type, weight) in weights) {
            roll -= weight
            if (roll < 0) return type
        }
        return RoomType.COMBAT
    }

    /**
     * 构建所有房间的方块
     */
    private fun buildRooms(rooms: List<Room>) {
        for (room in rooms) {
            buildRoom(room)
        }
    }

    /**
     * 构建单个房间
     */
    private fun buildRoom(room: Room) {
        val ox = origin.blockX
        val oz = origin.blockZ

        for (rx in 0 until room.width) {
            for (rz in 0 until room.depth) {
                val wx = ox + room.x + rx
                val wz = oz + room.z + rz

                val isWall = rx == 0 || rx == room.width - 1 || rz == 0 || rz == room.depth - 1

                // 地板
                world.getBlockAt(wx, baseY, wz).setType(theme.floor.get() ?: XMaterial.STONE_BRICKS.get()!!, false)

                if (isWall) {
                    // 墙壁
                    for (y in 1..config.roomHeight) {
                        world.getBlockAt(wx, baseY + y, wz).setType(theme.wall.get() ?: XMaterial.STONE_BRICKS.get()!!, false)
                    }
                } else {
                    // 内部空气
                    for (y in 1..config.roomHeight) {
                        world.getBlockAt(wx, baseY + y, wz).setType(XMaterial.AIR.get()!!, false)
                    }
                }

                // 天花板
                world.getBlockAt(wx, baseY + config.roomHeight + 1, wz)
                    .setType(theme.ceiling.get() ?: XMaterial.STONE_BRICKS.get()!!, false)
            }
        }
    }

    /**
     * 构建走廊方块
     */
    private fun buildCorridors(corridorCoords: Set<Pair<Int, Int>>, rooms: List<Room>) {
        val ox = origin.blockX
        val oz = origin.blockZ

        // 收集所有房间内部坐标，走廊不覆盖房间内部
        val roomInterior = mutableSetOf<Pair<Int, Int>>()
        for (room in rooms) {
            for (rx in 0 until room.width) {
                for (rz in 0 until room.depth) {
                    roomInterior.add((room.x + rx) to (room.z + rz))
                }
            }
        }

        for ((cx, cz) in corridorCoords) {
            val wx = ox + cx
            val wz = oz + cz

            // 地板
            world.getBlockAt(wx, baseY, wz).setType(theme.floor.get() ?: XMaterial.STONE_BRICKS.get()!!, false)

            // 走廊内部空气 + 两侧墙壁由相邻走廊格自然形成
            for (y in 1..config.roomHeight) {
                world.getBlockAt(wx, baseY + y, wz).setType(XMaterial.AIR.get()!!, false)
            }

            // 天花板
            world.getBlockAt(wx, baseY + config.roomHeight + 1, wz)
                .setType(theme.ceiling.get() ?: XMaterial.STONE_BRICKS.get()!!, false)
        }

        // 走廊墙壁：遍历走廊坐标的相邻格，如果不是走廊也不是房间，就放墙
        val corridorAndRoom = corridorCoords + roomInterior
        val wallCoords = mutableSetOf<Pair<Int, Int>>()
        for ((cx, cz) in corridorCoords) {
            if ((cx to cz) in roomInterior) continue
            for ((dx, dz) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) {
                val nx = cx + dx
                val nz = cz + dz
                if ((nx to nz) !in corridorAndRoom) {
                    wallCoords.add(nx to nz)
                }
            }
        }

        for ((wx2, wz2) in wallCoords) {
            val bx = ox + wx2
            val bz = oz + wz2
            // 地板
            world.getBlockAt(bx, baseY, bz).setType(theme.floor.get() ?: XMaterial.STONE_BRICKS.get()!!, false)
            // 墙壁
            for (y in 1..config.roomHeight) {
                world.getBlockAt(bx, baseY + y, bz).setType(theme.wall.get() ?: XMaterial.STONE_BRICKS.get()!!, false)
            }
            // 天花板
            world.getBlockAt(bx, baseY + config.roomHeight + 1, bz)
                .setType(theme.ceiling.get() ?: XMaterial.STONE_BRICKS.get()!!, false)
        }
    }

    /**
     * 对房间内部和走廊重新封边，避免局部缺墙或顶部断裂。
     */
    private fun reinforceShell(rooms: List<Room>, corridorCoords: Set<Pair<Int, Int>>) {
        val walkable = mutableSetOf<Pair<Int, Int>>()

        for (room in rooms) {
            for (x in (room.x + 1) until (room.x + room.width - 1)) {
                for (z in (room.z + 1) until (room.z + room.depth - 1)) {
                    walkable += x to z
                }
            }
        }
        walkable += corridorCoords

        val ox = origin.blockX
        val oz = origin.blockZ
        val shell = mutableSetOf<Pair<Int, Int>>()

        for ((x, z) in walkable) {
            val wx = ox + x
            val wz = oz + z

            world.getBlockAt(wx, baseY, wz).setType(theme.floor.get() ?: XMaterial.STONE_BRICKS.get()!!, false)
            for (y in 1..config.roomHeight) {
                world.getBlockAt(wx, baseY + y, wz).setType(XMaterial.AIR.get()!!, false)
            }
            world.getBlockAt(wx, baseY + config.roomHeight + 1, wz).setType(theme.ceiling.get() ?: XMaterial.STONE_BRICKS.get()!!, false)

            for ((dx, dz) in listOf(
                -1 to 0, 1 to 0, 0 to -1, 0 to 1,
                -1 to -1, -1 to 1, 1 to -1, 1 to 1
            )) {
                val neighbor = (x + dx) to (z + dz)
                if (neighbor !in walkable) {
                    shell += neighbor
                }
            }
        }

        for ((x, z) in shell) {
            val wx = ox + x
            val wz = oz + z
            world.getBlockAt(wx, baseY, wz).setType(theme.floor.get() ?: XMaterial.STONE_BRICKS.get()!!, false)
            for (y in 1..config.roomHeight) {
                world.getBlockAt(wx, baseY + y, wz).setType(theme.wall.get() ?: XMaterial.STONE_BRICKS.get()!!, false)
            }
            world.getBlockAt(wx, baseY + config.roomHeight + 1, wz).setType(theme.ceiling.get() ?: XMaterial.STONE_BRICKS.get()!!, false)
        }
    }

    /**
     * 为走廊补充顶灯，避免通道过暗。
     */
    private fun lightCorridors(corridorCoords: Set<Pair<Int, Int>>, rooms: List<Room>) {
        val roomInterior = mutableSetOf<Pair<Int, Int>>()
        for (room in rooms) {
            for (x in (room.x + 1) until (room.x + room.width - 1)) {
                for (z in (room.z + 1) until (room.z + room.depth - 1)) {
                    roomInterior += x to z
                }
            }
        }

        val corridorOnly = corridorCoords.filter { it !in roomInterior }.toSet()
        val ox = origin.blockX
        val oz = origin.blockZ
        val lightY = baseY + config.roomHeight + 1

        for ((x, z) in corridorOnly) {
            if (!shouldPlaceCorridorLight(x, z, corridorOnly)) {
                continue
            }
            world.getBlockAt(ox + x, lightY, oz + z).setType(theme.light.get() ?: XMaterial.SEA_LANTERN.get()!!, true)
        }
    }

    private fun shouldPlaceCorridorLight(x: Int, z: Int, corridorOnly: Set<Pair<Int, Int>>): Boolean {
        val west = (x - 1 to z) in corridorOnly
        val east = (x + 1 to z) in corridorOnly
        val north = (x to z - 1) in corridorOnly
        val south = (x to z + 1) in corridorOnly

        val horizontal = west && east
        val vertical = north && south

        if (!horizontal && !vertical) {
            return false
        }

        return when {
            horizontal && vertical -> ((x + z) and 3) == 0
            horizontal -> x % 5 == 0
            else -> z % 5 == 0
        }
    }

    /**
     * 填充房间内容
     */
    private fun populateRooms(rooms: List<Room>) {
        for (room in rooms) {
            RoomPopulator.populate(room, world, origin, theme, config.roomHeight)
        }
    }
}
