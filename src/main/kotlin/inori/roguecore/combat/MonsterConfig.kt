package inori.roguecore.combat

import inori.roguecore.dungeon.room.RoomType
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.info
import taboolib.common.platform.function.warning
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

/**
 * 怪物波次定义。
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

private data class MonsterWaveRules(
    val normalTypesMin: Int = 3,
    val normalTypesMax: Int = 5,
    val eliteAddsEnabled: Boolean = true,
    val eliteAddsTypes: Int = 3,
    val eliteAddsTotal: Int = 6,
    val bossAddsEnabled: Boolean = true,
    val bossAddsTypes: Int = 4,
    val bossAddsTotal: Int = 12
)

/**
 * 怪物配置 — 从 monsters.yml 按楼层段加载。
 *
 * RogueCore 只负责决定 MythicMobs ID 与波次数量；怪物实体、技能和 AP 属性由 MythicMobs 配置提供。
 */
object MonsterConfig {

    @Config("monsters.yml")
    lateinit var config: Configuration
        private set

    private val floorEntries = mutableListOf<MonsterFloorEntry>()
    private var waveRules = MonsterWaveRules()

    @Awake(LifeCycle.ENABLE)
    fun load() {
        floorEntries.clear()
        loadWaveRules()

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
        info("[RogueCore] 已加载 ${floorEntries.size} 组楼层怪物配置，普通房混合 ${waveRules.normalTypesMin}-${waveRules.normalTypesMax} 种怪")
    }

    private fun loadWaveRules() {
        val section = config.getConfigurationSection("wave-rules")
        waveRules = MonsterWaveRules(
            normalTypesMin = section?.getInt("normal-types-min", 3)?.coerceAtLeast(1) ?: 3,
            normalTypesMax = section?.getInt("normal-types-max", 5)?.coerceAtLeast(1) ?: 5,
            eliteAddsEnabled = section?.getBoolean("elite-adds-enabled", true) ?: true,
            eliteAddsTypes = section?.getInt("elite-adds-types", 3)?.coerceAtLeast(0) ?: 3,
            eliteAddsTotal = section?.getInt("elite-adds-total", 6)?.coerceAtLeast(0) ?: 6,
            bossAddsEnabled = section?.getBoolean("boss-adds-enabled", true) ?: true,
            bossAddsTypes = section?.getInt("boss-adds-types", 4)?.coerceAtLeast(0) ?: 4,
            bossAddsTotal = section?.getInt("boss-adds-total", 12)?.coerceAtLeast(0) ?: 12
        )
    }

    fun getConfiguredMobIdsForSelfCheck(): Set<String> {
        val ids = linkedSetOf<String>()
        for (entry in floorEntries) {
            ids += entry.normalMobs
            ids += entry.eliteMobs
            if (entry.bossMobId.contains("{floor}")) {
                val floorCount = (entry.maxFloor - entry.minFloor + 1).coerceIn(1, 200)
                for (floor in entry.minFloor until entry.minFloor + floorCount) {
                    ids += entry.bossMobId.replace("{floor}", floor.toString())
                }
            } else {
                ids += entry.bossMobId
            }
        }
        return ids.filter { it.isNotBlank() }.toSet()
    }

    /**
     * 获取房间的怪物波次。
     */
    fun getWaves(roomType: RoomType, floorNumber: Int): List<MonsterWave> {
        val entry = floorEntries.firstOrNull { floorNumber in it.minFloor..it.maxFloor }
            ?: floorEntries.lastOrNull()
            ?: return emptyList()

        return when (roomType) {
            RoomType.COMBAT -> getCombatWaves(floorNumber, entry)
            RoomType.ELITE -> getEliteWaves(floorNumber, entry)
            RoomType.BOSS -> getBossWaves(floorNumber, entry)
            else -> emptyList()
        }
    }

    private fun getCombatWaves(floor: Int, entry: MonsterFloorEntry): List<MonsterWave> {
        val totalCount = if (entry.combatCountPerFloors > 0) {
            (entry.combatBaseCount + floor / entry.combatCountPerFloors).coerceAtMost(entry.combatMaxCount)
        } else {
            entry.combatBaseCount.coerceAtMost(entry.combatMaxCount)
        }.coerceAtLeast(1)

        val minTypes = waveRules.normalTypesMin.coerceAtMost(entry.normalMobs.size).coerceAtLeast(1)
        val maxTypes = waveRules.normalTypesMax.coerceAtMost(entry.normalMobs.size).coerceAtLeast(minTypes)
        val typeCount = maxTypes.coerceAtMost(totalCount.coerceAtLeast(minTypes))
        val selected = pickRotating(entry.normalMobs, floor - entry.minFloor, typeCount)
        return distributeCount(selected, totalCount)
    }

    private fun getEliteWaves(floor: Int, entry: MonsterFloorEntry): List<MonsterWave> {
        val waves = mutableListOf<MonsterWave>()
        waves += MonsterWave(entry.eliteMobs.random(), entry.eliteCount.coerceAtLeast(1))
        if (waveRules.eliteAddsEnabled && waveRules.eliteAddsTotal > 0 && waveRules.eliteAddsTypes > 0) {
            val adds = pickRotating(entry.normalMobs, floor - entry.minFloor + 2, waveRules.eliteAddsTypes.coerceAtMost(entry.normalMobs.size))
            waves += distributeCount(adds, waveRules.eliteAddsTotal)
        }
        return waves
    }

    private fun getBossWaves(floor: Int, entry: MonsterFloorEntry): List<MonsterWave> {
        val waves = mutableListOf<MonsterWave>()
        waves += MonsterWave(entry.bossMobId.replace("{floor}", floor.toString()), entry.bossCount.coerceAtLeast(1))
        if (waveRules.bossAddsEnabled && waveRules.bossAddsTotal > 0 && waveRules.bossAddsTypes > 0) {
            val adds = pickRotating(entry.normalMobs, floor - entry.minFloor + 4, waveRules.bossAddsTypes.coerceAtMost(entry.normalMobs.size))
            waves += distributeCount(adds, waveRules.bossAddsTotal)
        }
        return waves
    }

    private fun pickRotating(values: List<String>, start: Int, count: Int): List<String> {
        if (values.isEmpty() || count <= 0) return emptyList()
        val safeCount = count.coerceAtMost(values.size)
        return (0 until safeCount).map { offset -> values[(start + offset).mod(values.size)] }
    }

    private fun distributeCount(mobIds: List<String>, totalCount: Int): List<MonsterWave> {
        if (mobIds.isEmpty() || totalCount <= 0) return emptyList()
        val base = (totalCount / mobIds.size).coerceAtLeast(1)
        val remainder = totalCount - base * mobIds.size
        return mobIds.mapIndexed { index, mobId ->
            MonsterWave(mobId, base + if (index < remainder) 1 else 0)
        }
    }
}
