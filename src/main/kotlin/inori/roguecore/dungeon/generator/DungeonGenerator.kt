package inori.roguecore.dungeon.generator

import inori.roguecore.affix.DungeonAffix
import inori.roguecore.dungeon.DungeonConfig
import inori.roguecore.dungeon.DungeonInstance
import inori.roguecore.dungeon.room.Room
import inori.roguecore.dungeon.room.RoomPopulator
import inori.roguecore.dungeon.room.RoomType
import inori.roguecore.event.DungeonEventAffix
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import taboolib.common.platform.function.submit
import taboolib.library.xseries.XMaterial
import java.util.ArrayDeque
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

    private data class BlockOp(
        val x: Int,
        val y: Int,
        val z: Int,
        val material: Material,
        val applyPhysics: Boolean = false
    )

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
        val rooms = bsp.generate().toMutableList()
        val connectionPairs = bsp.getConnectionPairs()

        if (rooms.isEmpty()) {
            throw IllegalStateException("BSP 未能生成任何房间")
        }

        // 2. 分配房间类型
        assignRoomTypes(rooms)
        val keptRoomIds = rooms.map { it.id }.toSet()

        // 3. 收集走廊坐标
        val corridorCoords = mutableSetOf<Pair<Int, Int>>()
        for ((roomA, roomB) in connectionPairs) {
            if (roomA.id !in keptRoomIds || roomB.id !in keptRoomIds) {
                continue
            }
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

    fun generateChunked(
        instanceId: String,
        affixes: List<DungeonAffix> = emptyList(),
        eventAffixes: List<DungeonEventAffix> = emptyList(),
        blocksPerTick: Int = 1200,
        onProgress: (Double) -> Unit = {},
        onComplete: (DungeonInstance) -> Unit,
        onFailure: (Throwable) -> Unit
    ) {
        try {
            val bsp = BSPTree(
                totalWidth = config.dungeonWidth,
                totalDepth = config.dungeonDepth,
                minPartitionSize = config.minPartitionSize,
                minRoomSize = config.minRoomSize,
                maxDepth = config.maxBSPDepth
            )
            val rooms = bsp.generate().toMutableList()
            val connectionPairs = bsp.getConnectionPairs()
            if (rooms.isEmpty()) error("BSP 未能生成任何房间")
            assignRoomTypes(rooms)
            val keptRoomIds = rooms.map { it.id }.toSet()
            val corridorCoords = mutableSetOf<Pair<Int, Int>>()
            for ((roomA, roomB) in connectionPairs) {
                if (roomA.id !in keptRoomIds || roomB.id !in keptRoomIds) {
                    continue
                }
                corridorCoords.addAll(CorridorBuilder.buildCorridor(roomA, roomB, config.corridorWidth))
            }
            val ops = ArrayList<BlockOp>(rooms.sumOf { it.width * it.depth * (config.roomHeight + 2) })
            collectRoomOps(rooms, ops)
            collectCorridorOps(corridorCoords, rooms, ops)
            collectShellOps(rooms, corridorCoords, ops)
            collectCorridorLightOps(corridorCoords, rooms, ops)
            collectPopulateOps(rooms, ops)
            applyChunked(
                ops = ops,
                blocksPerTick = blocksPerTick.coerceAtLeast(100),
                onProgress = onProgress,
                onComplete = {
                    onComplete(
                        DungeonInstance(
                            id = instanceId,
                            world = world,
                            origin = origin,
                            rooms = rooms,
                            corridorCoords = corridorCoords,
                            config = config,
                            affixes = affixes,
                            eventAffixes = eventAffixes
                        )
                    )
                },
                onFailure = onFailure
            )
        } catch (t: Throwable) {
            onFailure(t)
        }
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
    private fun assignRoomTypes(rooms: MutableList<Room>) {
        if (rooms.isEmpty()) return

        val spawnSeed = rooms.first()
        val spawnReachableIds = collectReachableRoomIds(rooms, spawnSeed.id)
        if (spawnReachableIds.isEmpty()) {
            throw IllegalStateException("起点房间连通域为空")
        }

        if (spawnReachableIds.size != rooms.size) {
            retainRoomsByIds(rooms, spawnReachableIds)
        }

        val spawnRoom = rooms.firstOrNull { it.id == spawnSeed.id } ?: rooms.first()

        if (rooms.size <= 2) {
            spawnRoom.type = RoomType.SPAWN
            rooms.firstOrNull { it.id != spawnRoom.id }?.type = RoomType.BOSS
            return
        }

        trimRoomsToTargetCount(rooms, spawnRoom)

        // 离起点最远的可达房间固定作为 Boss 终点，保证收尾房间必定可达。
        val bossRoom = rooms
            .asSequence()
            .filter { it.id != spawnRoom.id }
            .maxByOrNull { room ->
                val dx = room.centerX - spawnRoom.centerX
                val dz = room.centerZ - spawnRoom.centerZ
                dx * dx + dz * dz
            } ?: rooms.last()

        spawnRoom.type = RoomType.SPAWN
        bossRoom.type = RoomType.BOSS

        // 起点和 Boss 以外的房间按权重随机分配。
        val middleRooms = rooms.filter { it !== spawnRoom && it !== bossRoom }
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

    private fun trimRoomsToTargetCount(rooms: MutableList<Room>, spawn: Room) {
        val target = config.targetRoomCount.coerceAtLeast(3)
        if (rooms.size <= target) {
            return
        }

        val byId = rooms.associateBy { it.id }
        val keepIds = linkedSetOf(spawn.id)
        val queue = ArrayDeque<Room>()
        val visited = hashSetOf(spawn.id)
        queue.add(spawn)

        while (queue.isNotEmpty() && keepIds.size < target) {
            val room = queue.removeFirst()
            val neighbors = room.connections.mapNotNull { byId[it] }.sortedByDescending { next ->
                val dx = next.centerX - spawn.centerX
                val dz = next.centerZ - spawn.centerZ
                dx * dx + dz * dz
            }
            for (next in neighbors) {
                if (!visited.add(next.id)) {
                    continue
                }
                queue.add(next)
                keepIds += next.id
                if (keepIds.size >= target) {
                    break
                }
            }
        }

        retainRoomsByIds(rooms, keepIds)
    }

    private fun retainRoomsByIds(rooms: MutableList<Room>, keepIds: Set<Int>) {
        rooms.retainAll { it.id in keepIds }
        for (room in rooms) {
            room.connections.retainAll { it in keepIds }
        }
    }

    private fun collectReachableRoomIds(rooms: List<Room>, startRoomId: Int): Set<Int> {
        val byId = rooms.associateBy { it.id }
        val start = byId[startRoomId] ?: return emptySet()
        val visited = linkedSetOf<Int>()
        val queue = ArrayDeque<Room>()
        visited += start.id
        queue.add(start)
        while (queue.isNotEmpty()) {
            val room = queue.removeFirst()
            for (nextId in room.connections) {
                val next = byId[nextId] ?: continue
                if (!visited.add(nextId)) {
                    continue
                }
                queue.add(next)
            }
        }
        return visited
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

    private fun mat(material: XMaterial, fallback: XMaterial): Material = material.get() ?: fallback.get()!!

    private fun collectRoomOps(rooms: List<Room>, ops: MutableList<BlockOp>) {
        val ox = origin.blockX
        val oz = origin.blockZ
        val floor = mat(theme.floor, XMaterial.STONE_BRICKS)
        val wall = mat(theme.wall, XMaterial.STONE_BRICKS)
        val ceiling = mat(theme.ceiling, XMaterial.STONE_BRICKS)
        val air = XMaterial.AIR.get()!!
        for (room in rooms) {
            for (rx in 0 until room.width) for (rz in 0 until room.depth) {
                val wx = ox + room.x + rx
                val wz = oz + room.z + rz
                val isWall = rx == 0 || rx == room.width - 1 || rz == 0 || rz == room.depth - 1
                ops += BlockOp(wx, baseY, wz, floor)
                for (y in 1..config.roomHeight) ops += BlockOp(wx, baseY + y, wz, if (isWall) wall else air)
                ops += BlockOp(wx, baseY + config.roomHeight + 1, wz, ceiling)
            }
        }
    }

    private fun collectCorridorOps(corridorCoords: Set<Pair<Int, Int>>, rooms: List<Room>, ops: MutableList<BlockOp>) {
        val ox = origin.blockX
        val oz = origin.blockZ
        val floor = mat(theme.floor, XMaterial.STONE_BRICKS)
        val wall = mat(theme.wall, XMaterial.STONE_BRICKS)
        val ceiling = mat(theme.ceiling, XMaterial.STONE_BRICKS)
        val air = XMaterial.AIR.get()!!
        val roomInterior = mutableSetOf<Pair<Int, Int>>()
        for (room in rooms) for (rx in 0 until room.width) for (rz in 0 until room.depth) roomInterior.add((room.x + rx) to (room.z + rz))
        for ((cx, cz) in corridorCoords) {
            val wx = ox + cx; val wz = oz + cz
            ops += BlockOp(wx, baseY, wz, floor)
            for (y in 1..config.roomHeight) ops += BlockOp(wx, baseY + y, wz, air)
            ops += BlockOp(wx, baseY + config.roomHeight + 1, wz, ceiling)
        }
        val corridorAndRoom = corridorCoords + roomInterior
        val wallCoords = linkedSetOf<Pair<Int, Int>>()
        for ((cx, cz) in corridorCoords) {
            if ((cx to cz) in roomInterior) continue
            for ((dx, dz) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) {
                val next = (cx + dx) to (cz + dz)
                if (next !in corridorAndRoom) wallCoords += next
            }
        }
        for ((x, z) in wallCoords) {
            val wx = ox + x; val wz = oz + z
            ops += BlockOp(wx, baseY, wz, floor)
            for (y in 1..config.roomHeight) ops += BlockOp(wx, baseY + y, wz, wall)
            ops += BlockOp(wx, baseY + config.roomHeight + 1, wz, ceiling)
        }
    }

    private fun collectShellOps(rooms: List<Room>, corridorCoords: Set<Pair<Int, Int>>, ops: MutableList<BlockOp>) {
        val floor = mat(theme.floor, XMaterial.STONE_BRICKS)
        val wall = mat(theme.wall, XMaterial.STONE_BRICKS)
        val ceiling = mat(theme.ceiling, XMaterial.STONE_BRICKS)
        val air = XMaterial.AIR.get()!!
        val walkable = mutableSetOf<Pair<Int, Int>>()
        for (room in rooms) for (x in (room.x + 1) until (room.x + room.width - 1)) for (z in (room.z + 1) until (room.z + room.depth - 1)) walkable += x to z
        walkable += corridorCoords
        val shell = linkedSetOf<Pair<Int, Int>>()
        for ((x, z) in walkable) {
            val wx = origin.blockX + x; val wz = origin.blockZ + z
            ops += BlockOp(wx, baseY, wz, floor)
            for (y in 1..config.roomHeight) ops += BlockOp(wx, baseY + y, wz, air)
            ops += BlockOp(wx, baseY + config.roomHeight + 1, wz, ceiling)
            for ((dx, dz) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1, -1 to -1, -1 to 1, 1 to -1, 1 to 1)) {
                val neighbor = (x + dx) to (z + dz)
                if (neighbor !in walkable) shell += neighbor
            }
        }
        for ((x, z) in shell) {
            val wx = origin.blockX + x; val wz = origin.blockZ + z
            ops += BlockOp(wx, baseY, wz, floor)
            for (y in 1..config.roomHeight) ops += BlockOp(wx, baseY + y, wz, wall)
            ops += BlockOp(wx, baseY + config.roomHeight + 1, wz, ceiling)
        }
    }

    private fun collectCorridorLightOps(corridorCoords: Set<Pair<Int, Int>>, rooms: List<Room>, ops: MutableList<BlockOp>) {
        val roomInterior = mutableSetOf<Pair<Int, Int>>()
        for (room in rooms) for (x in (room.x + 1) until (room.x + room.width - 1)) for (z in (room.z + 1) until (room.z + room.depth - 1)) roomInterior += x to z
        val corridorOnly = corridorCoords.filter { it !in roomInterior }.toSet()
        val light = mat(theme.light, XMaterial.SEA_LANTERN)
        val lightY = baseY + config.roomHeight + 1
        for ((x, z) in corridorOnly) if (shouldPlaceCorridorLight(x, z, corridorOnly)) ops += BlockOp(origin.blockX + x, lightY, origin.blockZ + z, light, true)
    }

    private fun collectPopulateOps(rooms: List<Room>, ops: MutableList<BlockOp>) {
        val light = mat(theme.light, XMaterial.SEA_LANTERN)
        for (room in rooms) {
            ops += BlockOp(origin.blockX + room.centerX, baseY, origin.blockZ + room.centerZ, room.type.marker.get() ?: XMaterial.WHITE_WOOL.get()!!)
            val lightY = baseY + config.roomHeight + 1
            for (lx in createLightPositions(room.x + 2, room.x + room.width - 3)) {
                for (lz in createLightPositions(room.z + 2, room.z + room.depth - 3)) ops += BlockOp(origin.blockX + lx, lightY, origin.blockZ + lz, light, true)
            }
        }
    }

    private fun createLightPositions(min: Int, max: Int): List<Int> {
        if (max <= min) return listOf((min + max) / 2)
        val positions = linkedSetOf<Int>()
        val center = (min + max) / 2
        val span = max - min
        when {
            span <= 4 -> positions += center
            span <= 8 -> { positions += min; positions += max }
            else -> { var current = min; while (current <= max) { positions += current; current += 5 }; positions += center; positions += max }
        }
        return positions.toList()
    }

    private fun applyChunked(ops: List<BlockOp>, blocksPerTick: Int, onProgress: (Double) -> Unit, onComplete: () -> Unit, onFailure: (Throwable) -> Unit) {
        if (ops.isEmpty()) {
            onProgress(1.0)
            onComplete()
            return
        }
        var index = 0
        fun tick() {
            try {
                var processed = 0
                while (index < ops.size && processed < blocksPerTick) {
                    val op = ops[index++]
                    world.getBlockAt(op.x, op.y, op.z).setType(op.material, op.applyPhysics)
                    processed++
                }
                onProgress(index.toDouble() / ops.size.toDouble())
                if (index >= ops.size) onComplete() else submit(delay = 1L) { tick() }
            } catch (t: Throwable) {
                onFailure(t)
            }
        }
        submit(delay = 1L) { tick() }
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
