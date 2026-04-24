package inori.roguecore.dungeon

import inori.roguecore.affix.DungeonAffix
import inori.roguecore.dungeon.room.Room
import inori.roguecore.dungeon.room.RoomType
import inori.roguecore.event.DungeonEventAffix
import org.bukkit.Bukkit
import inori.roguecore.world.VoidWorldManager
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 地牢实例 — 每个实例拥有独立的世界
 */
class DungeonInstance(
    val id: String,
    val world: World,
    val origin: Location,
    val rooms: List<Room>,
    val config: DungeonConfig,
    val affixes: List<DungeonAffix> = emptyList(),
    val eventAffixes: List<DungeonEventAffix> = emptyList(),
    val players: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
) {

    var completed = false
    private var hiddenKeys = 0

    fun getOnlinePlayers(): List<Player> {
        return players.mapNotNull { Bukkit.getPlayer(it) }
    }

    fun getOnlinePlayerCount(): Int {
        return getOnlinePlayers().size
    }

    fun getHiddenKeys(): Int {
        return hiddenKeys
    }

    fun addHiddenKeys(amount: Int): Int {
        if (amount <= 0) {
            return hiddenKeys
        }
        hiddenKeys += amount
        return hiddenKeys
    }

    fun consumeHiddenKey(): Boolean {
        if (hiddenKeys <= 0) {
            return false
        }
        hiddenKeys--
        return true
    }

    /**
     * 获取起点房间的传送位置
     */
    fun getSpawnLocation(): Location {
        val spawnRoom = rooms.firstOrNull { it.type == RoomType.SPAWN } ?: rooms.first()
        return Location(
            world,
            (origin.blockX + spawnRoom.centerX).toDouble() + 0.5,
            (config.floorLevel + 1).toDouble(),
            (origin.blockZ + spawnRoom.centerZ).toDouble() + 0.5
        )
    }

    /**
     * 销毁副本 — 直接卸载并删除整个世界
     */
    fun destroy() {
        VoidWorldManager.destroyInstanceWorld(world)
    }
}
