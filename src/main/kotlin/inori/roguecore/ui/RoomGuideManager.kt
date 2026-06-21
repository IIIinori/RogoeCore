package inori.roguecore.ui

import inori.roguecore.balance.BalanceConfigManager
import inori.roguecore.combat.RoomCombatManager
import inori.roguecore.combat.RoomState
import inori.roguecore.dungeon.DungeonInstance
import inori.roguecore.dungeon.DungeonManager
import inori.roguecore.dungeon.room.Room
import inori.roguecore.dungeon.room.RoomType
import inori.roguecore.stats.PerfMonitor
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.entity.Player
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.submit
import kotlin.math.abs

/**
 * 房间导航管理器。
 *
 * - 计算当前导航目标
 * - 使用房间连接图构建简易路径
 * - 在探索阶段绘制粒子路标
 * - 接近目标房间时进行区域高亮
 */
object RoomGuideManager {

    private data class GuideState(
        var lastTargetRoomId: Int? = null,
        var lastPlayerBlockX: Int = Int.MIN_VALUE,
        var lastPlayerBlockY: Int = Int.MIN_VALUE,
        var lastPlayerBlockZ: Int = Int.MIN_VALUE,
        var lastPathRenderAt: Long = 0L
    )

    private var tickCounter = 0
    private val guideStates = java.util.concurrent.ConcurrentHashMap<java.util.UUID, GuideState>()

    @Awake(LifeCycle.ENABLE)
    fun startRenderer() {
        submit(period = 10L) {
            tick()
        }
    }

    fun describeTarget(player: Player, instance: DungeonInstance): String {
        if (!isEnabled()) {
            return if (instance.completed) "§6选择去留" else "§7继续探索"
        }
        val activeRoom = RoomCombatManager.getActiveRoom(instance)
        if (instance.completed) {
            return "§6前往结算 / 选择下一步"
        }
        if (activeRoom != null) {
            val total = activeRoom.spawnedMobCount.coerceAtLeast(activeRoom.aliveMobs.size).coerceAtLeast(1)
            val cleared = (total - activeRoom.aliveMobs.size).coerceAtLeast(0)
            return "${color(activeRoom.type)}清理${activeRoom.type.displayName}房 $cleared/$total"
        }
        if (!sidebarTargetEnabled()) {
            return "§7继续探索"
        }
        val target = getTargetRoom(player, instance) ?: return "§7继续探索"
        return if (target.type == RoomType.HIDDEN && instance.getHiddenKeys() <= 0) {
            "${color(target.type)}发现隐藏房 §8(需要隐藏钥匙)"
        } else {
            "${color(target.type)}前往最近的${target.type.displayName}房"
        }
    }

    fun getTargetRoom(player: Player, instance: DungeonInstance): Room? {
        val activeRoom = RoomCombatManager.getActiveRoom(instance)
        if (activeRoom != null) return activeRoom
        if (instance.completed) return null

        val reachableIds = collectReachableRoomIds(instance)
        if (reachableIds.isEmpty()) {
            return null
        }

        val currentRoom = RoomCombatManager.getPlayerRoom(player)
        if (currentRoom != null && currentRoom.isCombatRoom && currentRoom.state != RoomState.CLEARED) {
            return currentRoom
        }

        val candidates = instance.rooms.filter { room ->
            room.id in reachableIds &&
            room.type != RoomType.SPAWN && when {
                room.isCombatRoom -> room.state != RoomState.CLEARED
                else -> room.state == RoomState.IDLE
            }
        }
        if (candidates.isEmpty()) return null
        val from = currentRoom
        return candidates.minWithOrNull(
            compareBy<Room> {
                roomPriority(it, instance)
            }.thenBy {
                estimateDistance(from, it)
            }.thenBy { it.id }
        )
    }

    private fun tick() {
        if (!isEnabled()) return
        tickCounter += 10
        val period = refreshPeriodTicks().coerceAtLeast(10)
        if (tickCounter < period) return
        tickCounter = 0
        val onlineIds = hashSetOf<java.util.UUID>()
        for (instance in DungeonManager.getActiveDungeons()) {
            for (player in instance.getOnlinePlayers()) {
                onlineIds += player.uniqueId
                renderGuide(player, instance)
            }
        }
        if (guideStates.isNotEmpty()) {
            guideStates.keys.removeIf { it !in onlineIds }
        }
    }

    private fun renderGuide(player: Player, instance: DungeonInstance) {
        if (instance.completed) return
        val activeRoom = RoomCombatManager.getActiveRoom(instance)
        if (activeRoom != null) return

        val currentRoom = RoomCombatManager.getPlayerRoom(player) ?: return
        val targetRoom = getTargetRoom(player, instance) ?: return
        if (currentRoom.id == targetRoom.id) return
        val state = guideStates.computeIfAbsent(player.uniqueId) { GuideState() }
        val location = player.location
        val targetChanged = state.lastTargetRoomId != targetRoom.id
        val movedBlock = location.blockX != state.lastPlayerBlockX ||
            location.blockY != state.lastPlayerBlockY ||
            location.blockZ != state.lastPlayerBlockZ
        val now = System.currentTimeMillis()
        val keepAliveDue = now - state.lastPathRenderAt >= particleKeepaliveTicks().coerceAtLeast(4) * 50L
        if (!targetChanged && !movedBlock && !keepAliveDue) return

        if (particlePathEnabled()) {
            val path = findRoomPath(instance, currentRoom, targetRoom)
            PerfMonitor.measure("guide.path.render") {
                renderPath(player, instance, path.ifEmpty { listOf(currentRoom, targetRoom) })
            }
            state.lastPathRenderAt = now
        }
        if (entranceHighlightEnabled()) {
            PerfMonitor.measure("guide.highlight.render") {
                renderTargetHighlight(player, instance, targetRoom)
            }
        }
        state.lastTargetRoomId = targetRoom.id
        state.lastPlayerBlockX = location.blockX
        state.lastPlayerBlockY = location.blockY
        state.lastPlayerBlockZ = location.blockZ
    }

    private fun findRoomPath(instance: DungeonInstance, from: Room, target: Room): List<Room> {
        if (from.id == target.id) return listOf(from)
        val roomById = instance.rooms.associateBy { it.id }
        val queue = ArrayDeque<Int>()
        val visited = hashSetOf<Int>()
        val previous = hashMapOf<Int, Int>()
        queue.add(from.id)
        visited += from.id

        while (queue.isNotEmpty()) {
            val roomId = queue.removeFirst()
            if (roomId == target.id) break
            val room = roomById[roomId] ?: continue
            for (nextId in room.connections) {
                if (!visited.add(nextId)) continue
                previous[nextId] = roomId
                queue.add(nextId)
            }
        }

        if (target.id !in visited) {
            return emptyList()
        }
        val ids = mutableListOf<Int>()
        var cursor = target.id
        ids += cursor
        while (cursor != from.id) {
            cursor = previous[cursor] ?: return emptyList()
            ids += cursor
        }
        ids.reverse()
        return ids.mapNotNull(roomById::get)
    }

    private fun renderPath(player: Player, instance: DungeonInstance, path: List<Room>) {
        if (path.size < 2) return
        val dust = Particle.DustOptions(colorOf(path.last().type), 1.6f)
        val maxSegments = maxPathSegments().coerceAtLeast(1)
        val particlesPerSegment = particlesPerSegment().coerceAtLeast(3)
        val start = player.location.clone().add(0.0, 0.15, 0.0)
        var previousPoint = start

        val limit = minOf(path.size - 1, maxSegments)
        for (indexInPath in 1..limit) {
            val room = path[indexInPath]
            val target = roomCenter(instance, room)
            repeat(particlesPerSegment) { index ->
                val ratio = (index + 1).toDouble() / particlesPerSegment.toDouble()
                val x = previousPoint.x + (target.x - previousPoint.x) * ratio
                val y = previousPoint.y + (target.y - previousPoint.y) * ratio
                val z = previousPoint.z + (target.z - previousPoint.z) * ratio
                player.spawnParticle(Particle.REDSTONE, x, y, z, 2, 0.02, 0.02, 0.02, 0.0, dust)
            }
            previousPoint = target
        }
        DungeonHudManager.pushActionBar(player, describeTarget(player, instance), 20L)
    }

    private fun renderTargetHighlight(player: Player, instance: DungeonInstance, targetRoom: Room) {
        val center = roomCenter(instance, targetRoom)
        val radius = entranceHighlightRadius().coerceAtLeast(4)
        if (player.location.distanceSquared(center) > radius * radius.toDouble()) {
            return
        }
        val dust = Particle.DustOptions(colorOf(targetRoom.type), 1.5f)
        val corners = listOf(
            offset(instance, targetRoom.x + 1, targetRoom.z + 1),
            offset(instance, targetRoom.x + targetRoom.width - 2, targetRoom.z + 1),
            offset(instance, targetRoom.x + 1, targetRoom.z + targetRoom.depth - 2),
            offset(instance, targetRoom.x + targetRoom.width - 2, targetRoom.z + targetRoom.depth - 2)
        )
        corners.forEach { point ->
            repeat(3) { height ->
                player.spawnParticle(Particle.REDSTONE, point.x, point.y + height * 0.5, point.z, 1, 0.0, 0.0, 0.0, 0.0, dust)
            }
        }
        DungeonHudManager.pushActionBar(player, "${color(targetRoom.type)}目标就在前方：${targetRoom.type.displayName}房", 20L)
    }

    private fun roomCenter(instance: DungeonInstance, room: Room): Location {
        return Location(
            instance.world,
            instance.origin.blockX + room.centerX + 0.5,
            instance.config.floorLevel + 1.15,
            instance.origin.blockZ + room.centerZ + 0.5
        )
    }

    private fun offset(instance: DungeonInstance, x: Int, z: Int): Location {
        return Location(
            instance.world,
            instance.origin.blockX + x + 0.5,
            instance.config.floorLevel + 1.2,
            instance.origin.blockZ + z + 0.5
        )
    }

    private fun roomPriority(room: Room, instance: DungeonInstance): Int {
        return when (room.type) {
            RoomType.HIDDEN -> if (instance.getHiddenKeys() > 0) 0 else 3
            RoomType.BOSS -> 0
            RoomType.ELITE -> 1
            RoomType.SHRINE, RoomType.CHEST, RoomType.SHOP, RoomType.FORGE, RoomType.REST, RoomType.TRIAL, RoomType.GAMBLE, RoomType.CONTRACT, RoomType.EXTRACTION -> 2
            RoomType.COMBAT -> 4
            else -> 5
        }
    }

    private fun estimateDistance(from: Room?, target: Room): Int {
        if (from == null) return target.id
        return abs(from.centerX - target.centerX) + abs(from.centerZ - target.centerZ)
    }

    private fun color(type: RoomType): String {
        return when (type) {
            RoomType.SPAWN -> "§a"
            RoomType.COMBAT -> "§6"
            RoomType.ELITE -> "§c"
            RoomType.BOSS -> "§5"
            RoomType.SHOP -> "§e"
            RoomType.FORGE -> "§6"
            RoomType.CHEST -> "§b"
            RoomType.REST -> "§3"
            RoomType.EXTRACTION -> "§b"
            RoomType.CONTRACT -> "§4"
            RoomType.HIDDEN -> "§9"
            RoomType.TRIAL -> "§d"
            RoomType.GAMBLE -> "§2"
            RoomType.SHRINE -> "§f"
        }
    }

    private fun colorOf(type: RoomType): Color {
        return when (type) {
            RoomType.COMBAT -> Color.fromRGB(255, 170, 32)
            RoomType.ELITE -> Color.fromRGB(255, 70, 70)
            RoomType.BOSS -> Color.fromRGB(180, 70, 255)
            RoomType.SHOP -> Color.fromRGB(255, 220, 80)
            RoomType.FORGE -> Color.fromRGB(255, 140, 30)
            RoomType.CHEST -> Color.fromRGB(60, 220, 255)
            RoomType.REST -> Color.fromRGB(120, 200, 255)
            RoomType.EXTRACTION -> Color.fromRGB(80, 255, 220)
            RoomType.CONTRACT -> Color.fromRGB(180, 30, 30)
            RoomType.HIDDEN -> Color.fromRGB(60, 110, 255)
            RoomType.TRIAL -> Color.fromRGB(220, 120, 255)
            RoomType.GAMBLE -> Color.fromRGB(80, 220, 90)
            RoomType.SHRINE -> Color.fromRGB(245, 245, 245)
            else -> Color.fromRGB(255, 255, 255)
        }
    }

    private fun isEnabled(): Boolean = BalanceConfigManager.getBoolean("guide.room-navigation.enabled", true)

    private fun sidebarTargetEnabled(): Boolean = BalanceConfigManager.getBoolean("guide.room-navigation.sidebar-target", true)

    private fun particlePathEnabled(): Boolean = BalanceConfigManager.getBoolean("guide.room-navigation.particle-path", true)

    private fun entranceHighlightEnabled(): Boolean = BalanceConfigManager.getBoolean("guide.room-navigation.entrance-highlight", true)

    private fun refreshPeriodTicks(): Int = BalanceConfigManager.getInt("guide.room-navigation.refresh-period-ticks", 10)

    private fun maxPathSegments(): Int = BalanceConfigManager.getInt("guide.room-navigation.max-path-segments", 3)

    private fun particlesPerSegment(): Int = BalanceConfigManager.getInt("guide.room-navigation.particles-per-segment", 8)

    private fun particleKeepaliveTicks(): Int = BalanceConfigManager.getInt("guide.room-navigation.particle-keepalive-ticks", 8)

    private fun entranceHighlightRadius(): Int = BalanceConfigManager.getInt("guide.room-navigation.entrance-highlight-radius", 12)

    private fun collectReachableRoomIds(instance: DungeonInstance): Set<Int> {
        val start = instance.rooms.firstOrNull { it.type == RoomType.SPAWN } ?: instance.rooms.firstOrNull() ?: return emptySet()
        val byId = instance.rooms.associateBy { it.id }
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
}
