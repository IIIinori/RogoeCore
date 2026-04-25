package inori.roguecore.event

import inori.roguecore.dungeon.DungeonInstance
import inori.roguecore.dungeon.room.RoomType
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.info
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration
import kotlin.random.Random

/**
 * 高层事件专属词缀管理器。
 */
object EventAffixManager {

    @Config("events.yml")
    lateinit var config: Configuration
        private set

    private val affixes = mutableMapOf<String, DungeonEventAffix>()
    private val rules = mutableListOf<Triple<Int, Int, Int>>()

    fun getAll(): Collection<DungeonEventAffix> = affixes.values

    @Awake(LifeCycle.ENABLE)
    fun load() {
        affixes.clear()
        rules.clear()

        val rulesSection = config.getConfigurationSection("event-affixes.rules")
        if (rulesSection != null) {
            for (key in rulesSection.getKeys(false)) {
                val parts = key.split("-")
                val minFloor = parts.getOrNull(0)?.toIntOrNull() ?: 1
                val maxFloor = parts.getOrNull(1)?.toIntOrNull() ?: 99
                val count = rulesSection.getInt(key, 0).coerceAtLeast(0)
                rules += Triple(minFloor, maxFloor, count)
            }
        }
        rules.sortBy { it.first }

        val section = config.getConfigurationSection("event-affixes.affixes")
        if (section != null) {
            for (id in section.getKeys(false)) {
                val node = section.getConfigurationSection(id) ?: continue
                val rooms = node.getStringList("rooms")
                    .mapNotNull(::parseRoomType)
                    .toSet()
                if (rooms.isEmpty()) {
                    continue
                }
                affixes[id] = DungeonEventAffix(
                    id = id,
                    name = node.getString("name") ?: id,
                    description = node.getString("description") ?: "",
                    rooms = rooms,
                    minFloor = node.getInt("min-floor", 1).coerceAtLeast(1),
                    weight = node.getInt("weight", 10).coerceAtLeast(1)
                )
            }
        }

        info("[RogueCore] 已加载 ${affixes.size} 个事件词缀")
    }

    fun rollAffixes(floorNumber: Int): List<DungeonEventAffix> {
        val count = getAffixCount(floorNumber)
        if (count <= 0) {
            return emptyList()
        }
        val pool = affixes.values.filter { floorNumber >= it.minFloor }.toMutableList()
        if (pool.isEmpty()) {
            return emptyList()
        }

        val result = mutableListOf<DungeonEventAffix>()
        repeat(count.coerceAtMost(pool.size)) {
            val picked = weightedRandom(pool) ?: return@repeat
            result += picked
            pool.remove(picked)
        }
        return result
    }

    fun hasAffix(instance: DungeonInstance, id: String): Boolean {
        return instance.eventAffixes.any { it.id.equals(id, ignoreCase = true) }
    }

    fun getAffixesForRoom(instance: DungeonInstance, roomType: RoomType): List<DungeonEventAffix> {
        return instance.eventAffixes.filter { roomType in it.rooms }
    }

    private fun getAffixCount(floorNumber: Int): Int {
        return rules.firstOrNull { floorNumber in it.first..it.second }?.third ?: 0
    }

    private fun weightedRandom(pool: List<DungeonEventAffix>): DungeonEventAffix? {
        if (pool.isEmpty()) {
            return null
        }
        val total = pool.sumOf { it.weight }
        var roll = Random.nextInt(total)
        for (affix in pool) {
            roll -= affix.weight
            if (roll < 0) {
                return affix
            }
        }
        return pool.last()
    }

    private fun parseRoomType(id: String): RoomType? {
        return try {
            RoomType.valueOf(id.uppercase())
        } catch (_: Exception) {
            null
        }
    }
}
