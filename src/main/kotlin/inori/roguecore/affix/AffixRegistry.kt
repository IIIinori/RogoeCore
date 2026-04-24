package inori.roguecore.affix

import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.info
import taboolib.common.platform.function.warning
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

/**
 * 词缀注册表 — 从 affixes.yml 加载
 */
object AffixRegistry {

    @Config("affixes.yml")
    lateinit var config: Configuration
        private set

    private val affixes = mutableMapOf<String, DungeonAffix>()

    /** 楼层范围 -> [最少, 最多] */
    private val rules = mutableListOf<Triple<Int, Int, IntArray>>()

    fun getAll(): Collection<DungeonAffix> = affixes.values

    fun get(id: String): DungeonAffix? = affixes[id]

    fun getDifficultyAffixes(floorNumber: Int): List<DungeonAffix> {
        return affixes.values.filter { it.difficulty && floorNumber >= it.minFloor }
    }

    fun getRewardAffixes(floorNumber: Int): List<DungeonAffix> {
        return affixes.values.filter { !it.difficulty && floorNumber >= it.minFloor }
    }

    fun getAdvancedAffixes(floorNumber: Int): List<DungeonAffix> {
        return affixes.values.filter { it.advanced && floorNumber >= it.minFloor }
    }

    /**
     * 获取指定楼层的词缀数量范围
     * @return [最少, 最多]
     */
    fun getAffixCountRange(floorNumber: Int): IntArray {
        for ((min, max, range) in rules) {
            if (floorNumber in min..max) return range
        }
        return intArrayOf(0, 1)
    }

    @Awake(LifeCycle.ENABLE)
    fun load() {
        affixes.clear()
        rules.clear()

        // 加载规则
        val rulesSection = config.getConfigurationSection("rules")
        if (rulesSection != null) {
            for (key in rulesSection.getKeys(false)) {
                val parts = key.split("-")
                val minFloor = parts.getOrNull(0)?.toIntOrNull() ?: 1
                val maxFloor = parts.getOrNull(1)?.toIntOrNull() ?: 99
                val list = rulesSection.getIntegerList(key)
                val minCount = list.getOrElse(0) { 0 }
                val maxCount = list.getOrElse(1) { 1 }
                rules.add(Triple(minFloor, maxFloor, intArrayOf(minCount, maxCount)))
            }
        }
        rules.sortBy { it.first }

        // 加载词缀
        val section = config.getConfigurationSection("affixes") ?: return
        for (id in section.getKeys(false)) {
            val s = section.getConfigurationSection(id) ?: continue
            try {
                val name = s.getString("name") ?: id
                val description = s.getString("description") ?: ""
                val typeStr = s.getString("type") ?: continue
                val type = try { AffixType.valueOf(typeStr.uppercase()) } catch (_: Exception) {
                    warning("[RogueCore] 未知词缀类型: $typeStr (词缀: $id)")
                    continue
                }
                val value = s.getDouble("value", 1.0)
                val difficulty = s.getBoolean("difficulty", true)
                val weight = s.getInt("weight", 10)
                val minFloor = s.getInt("min-floor", 1).coerceAtLeast(1)
                val advanced = s.getBoolean("advanced", false)

                affixes[id] = DungeonAffix(id, name, description, type, value, difficulty, weight, minFloor, advanced)
            } catch (e: Exception) {
                warning("[RogueCore] 加载词缀 '$id' 失败: ${e.message}")
            }
        }

        info("[RogueCore] 已加载 ${affixes.size} 个副本词缀")
    }
}
