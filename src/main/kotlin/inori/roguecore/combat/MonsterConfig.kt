package inori.roguecore.combat

import inori.roguecore.dungeon.room.RoomType
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.info
import taboolib.common.platform.function.warning
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

/**
 * 怪物波次定义
 */
data class MonsterWave(
    val mobId: String,
    val count: Int
)

private data class MonsterFloorEntry(
    val minFloor: Int,
    val maxFloor: Int,
    val normalMobs: List<String>,
    val combatBaseCount: Int,
    val combatCountPerFloors: Int,
    val combatMaxCount: Int,
    val eliteMobs: List<String>,
    val eliteCount: Int,
    val bossMobId: String,
    val bossCount: Int
)

/**
 * 怪物配置 — 从 monsters.yml 按楼层段加载
 */
object MonsterConfig {

    @Config("monsters.yml")
    lateinit var config: Configuration
        private set

    private val floorEntries = mutableListOf<MonsterFloorEntry>()

    @Awake(LifeCycle.ENABLE)
    fun load() {
        floorEntries.clear()

        val floorsSection = config.getConfigurationSection("floors")
        if (floorsSection == null) {
            warning("[RogueCore] monsters.yml 中未找到 floors 节点")
            return
        }

        for (key in floorsSection.getKeys(false)) {
            val section = floorsSection.getConfigurationSection(key) ?: continue
            try {
                val rangeStr = section.getString("range") ?: "1-999"
                val rangeParts = rangeStr.split("-")
                val minFloor = rangeParts.getOrNull(0)?.toIntOrNull() ?: 1
                val maxFloor = rangeParts.getOrNull(1)?.toIntOrNull() ?: minFloor

                val normalMobs = section.getStringList("combat.normal-mobs")
                val combatBaseCount = section.getInt("combat.base-count", 3)
                val combatCountPerFloors = section.getInt("combat.count-per-floors", 3)
                val combatMaxCount = section.getInt("combat.max-count", 6)

                val eliteMobs = section.getStringList("elite.mobs")
                val eliteCount = section.getInt("elite.count", 1)

                val bossMobId = section.getString("boss.mob-id") ?: "rogue_boss_floor{floor}"
                val bossCount = section.getInt("boss.count", 1)

                if (normalMobs.isEmpty()) {
                    warning("[RogueCore] 怪物配置 '$key' 未配置 combat.normal-mobs，已跳过")
                    continue
                }
                if (eliteMobs.isEmpty()) {
                    warning("[RogueCore] 怪物配置 '$key' 未配置 elite.mobs，已跳过")
                    continue
                }

                floorEntries += MonsterFloorEntry(
                    minFloor = minFloor,
                    maxFloor = maxFloor,
                    normalMobs = normalMobs,
                    combatBaseCount = combatBaseCount,
                    combatCountPerFloors = combatCountPerFloors,
                    combatMaxCount = combatMaxCount,
                    eliteMobs = eliteMobs,
                    eliteCount = eliteCount,
                    bossMobId = bossMobId,
                    bossCount = bossCount
                )
            } catch (e: Exception) {
                warning("[RogueCore] 加载怪物配置 '$key' 失败: ${e.message}")
            }
        }

        floorEntries.sortBy { it.minFloor }
        info("[RogueCore] 已加载 ${floorEntries.size} 组楼层怪物配置")
    }

    /**
     * 获取房间的怪物波次
     */
    fun getWaves(roomType: RoomType, floorNumber: Int): List<MonsterWave> {
        val entry = floorEntries.firstOrNull { floorNumber in it.minFloor..it.maxFloor }
            ?: floorEntries.lastOrNull()
            ?: return emptyList()

        return when (roomType) {
            RoomType.COMBAT -> getCombatWaves(floorNumber, entry)
            RoomType.ELITE -> getEliteWaves(entry)
            RoomType.BOSS -> getBossWaves(floorNumber, entry)
            else -> emptyList()
        }
    }

    private fun getCombatWaves(floor: Int, entry: MonsterFloorEntry): List<MonsterWave> {
        val count = if (entry.combatCountPerFloors > 0) {
            (entry.combatBaseCount + floor / entry.combatCountPerFloors).coerceAtMost(entry.combatMaxCount)
        } else {
            entry.combatBaseCount.coerceAtMost(entry.combatMaxCount)
        }
        val mobId = entry.normalMobs[(floor - entry.minFloor).mod(entry.normalMobs.size)]
        return listOf(MonsterWave(mobId, count))
    }

    private fun getEliteWaves(entry: MonsterFloorEntry): List<MonsterWave> {
        val mobId = entry.eliteMobs.random()
        return listOf(MonsterWave(mobId, entry.eliteCount))
    }

    private fun getBossWaves(floor: Int, entry: MonsterFloorEntry): List<MonsterWave> {
        val mobId = entry.bossMobId.replace("{floor}", floor.toString())
        return listOf(MonsterWave(mobId, entry.bossCount))
    }
}
