package inori.roguecore.affix

import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.info
import taboolib.common.platform.function.warning
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

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

    private data class RotationPool(
        val name: String,
        val ids: Set<String>,
        val idPrefixes: List<String>,
        val types: Set<AffixType>
    ) {
        fun matches(affix: DungeonAffix): Boolean {
            if (affix.id.lowercase() in ids) {
                return true
            }
            if (idPrefixes.any { prefix -> affix.id.startsWith(prefix, ignoreCase = true) }) {
                return true
            }
            return affix.type in types
        }
    }

    data class RotationSnapshot(
        val enabled: Boolean,
        val activePool: String?,
        val cycleDays: Int,
        val anchorDate: String,
        val timezone: String
    )

    private var rotationEnabled = false
    private var rotationCycleDays = 7
    private var rotationAnchorDate: LocalDate = LocalDate.of(2026, 1, 5)
    private var rotationZoneId: ZoneId = ZoneId.systemDefault()
    private var rotationFallbackToAll = true
    private var rotationDefaultPool: String? = null
    private var rotationOrder: List<String> = emptyList()
    private var rotationPools: Map<String, RotationPool> = emptyMap()
    private var rotationPoolPriority: List<String> = emptyList()

    fun getAll(): Collection<DungeonAffix> = affixes.values

    fun get(id: String): DungeonAffix? = affixes[id]

    fun getDifficultyAffixes(floorNumber: Int): List<DungeonAffix> {
        return applyRotationFilter(affixes.values.filter { it.difficulty && floorNumber >= it.minFloor })
    }

    fun getRewardAffixes(floorNumber: Int): List<DungeonAffix> {
        return applyRotationFilter(affixes.values.filter { !it.difficulty && floorNumber >= it.minFloor })
    }

    fun getAdvancedAffixes(floorNumber: Int): List<DungeonAffix> {
        return applyRotationFilter(affixes.values.filter { it.advanced && floorNumber >= it.minFloor })
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

    fun getRotationSnapshot(nowEpochMillis: Long = System.currentTimeMillis()): RotationSnapshot {
        return RotationSnapshot(
            enabled = rotationEnabled,
            activePool = getActiveRotationPoolName(nowEpochMillis),
            cycleDays = rotationCycleDays,
            anchorDate = rotationAnchorDate.toString(),
            timezone = rotationZoneId.id
        )
    }

    @Awake(LifeCycle.ENABLE)
    fun load() {
        affixes.clear()
        rules.clear()
        loadRotationConfig()

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
                val name = s.getString("name")?.takeUnless { it == id } ?: "未命名词缀"
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
        if (rotationEnabled) {
            val activePool = getActiveRotationPoolName() ?: "未命中"
            info("[RogueCore] 词缀轮换池已启用: 当前池=$activePool, 周期=${rotationCycleDays}天, 锚点=${rotationAnchorDate}, 时区=${rotationZoneId.id}")
        }
    }

    private fun loadRotationConfig() {
        rotationEnabled = false
        rotationCycleDays = 7
        rotationAnchorDate = LocalDate.of(2026, 1, 5)
        rotationZoneId = ZoneId.systemDefault()
        rotationFallbackToAll = true
        rotationDefaultPool = null
        rotationOrder = emptyList()
        rotationPools = emptyMap()
        rotationPoolPriority = emptyList()

        val section = config.getConfigurationSection("rotation") ?: return
        rotationEnabled = section.getBoolean("enabled", false)
        rotationCycleDays = section.getInt("cycle-days", 7).coerceAtLeast(1)
        rotationFallbackToAll = section.getBoolean("fallback-to-all", true)
        rotationDefaultPool = section.getString("default-pool")
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        val timezoneText = section.getString("timezone")
            ?.trim()
            .orEmpty()
        rotationZoneId = runCatching {
            if (timezoneText.isBlank()) ZoneId.systemDefault() else ZoneId.of(timezoneText)
        }.getOrElse {
            warning("[RogueCore] affixes.yml rotation.timezone 无效: $timezoneText，已回退系统时区")
            ZoneId.systemDefault()
        }

        val anchorText = section.getString("anchor-date")
            ?.trim()
            .orEmpty()
        rotationAnchorDate = runCatching {
            if (anchorText.isBlank()) LocalDate.of(2026, 1, 5) else LocalDate.parse(anchorText)
        }.getOrElse {
            warning("[RogueCore] affixes.yml rotation.anchor-date 无效: $anchorText，已回退 2026-01-05")
            LocalDate.of(2026, 1, 5)
        }

        val poolsSection = section.getConfigurationSection("pools")
        if (poolsSection == null) {
            if (rotationEnabled) {
                warning("[RogueCore] affixes.yml 已启用词缀轮换，但 rotation.pools 缺失；将自动回退全池")
            }
            return
        }

        val loadedPools = linkedMapOf<String, RotationPool>()
        for (poolName in poolsSection.getKeys(false)) {
            val node = poolsSection.getConfigurationSection(poolName) ?: continue
            val ids = node.getStringList("ids")
                .map { it.trim().lowercase() }
                .filter { it.isNotBlank() }
                .toSet()
            val idPrefixes = node.getStringList("id-prefixes")
                .map { it.trim() }
                .filter { it.isNotBlank() }
            val types = node.getStringList("types")
                .mapNotNull { raw ->
                    runCatching { AffixType.valueOf(raw.trim().uppercase()) }.getOrElse {
                        warning("[RogueCore] affixes.yml rotation.pools.$poolName.types 存在未知词缀类型: $raw")
                        null
                    }
                }
                .toSet()
            loadedPools[poolName] = RotationPool(
                name = poolName,
                ids = ids,
                idPrefixes = idPrefixes,
                types = types
            )
        }
        rotationPools = loadedPools
        if (rotationPools.isEmpty()) {
            if (rotationEnabled) {
                warning("[RogueCore] affixes.yml rotation.pools 为空；将自动回退全池")
            }
            return
        }

        val configuredOrder = section.getStringList("order")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (configuredOrder.isNotEmpty()) {
            val unknown = configuredOrder.filterNot { it in rotationPools }
            if (unknown.isNotEmpty()) {
                warning("[RogueCore] affixes.yml rotation.order 包含未知池: ${unknown.joinToString(", ")}")
            }
            rotationOrder = configuredOrder.filter { it in rotationPools }
        } else {
            rotationOrder = rotationPools.keys.toList()
        }
        if (rotationOrder.isEmpty()) {
            rotationOrder = rotationPools.keys.toList()
        }

        if (rotationDefaultPool != null && rotationDefaultPool !in rotationPools) {
            warning("[RogueCore] affixes.yml rotation.default-pool 指向未知池: $rotationDefaultPool")
            rotationDefaultPool = null
        }

        rotationPoolPriority = (rotationOrder + rotationPools.keys.filter { it !in rotationOrder }).distinct()
    }

    private fun applyRotationFilter(pool: List<DungeonAffix>): List<DungeonAffix> {
        val activePool = getActiveRotationPoolName() ?: return pool
        val filtered = pool.filter { affix ->
            resolveAffixPoolName(affix)?.equals(activePool, ignoreCase = true) == true
        }
        if (filtered.isNotEmpty() || !rotationFallbackToAll) {
            return filtered
        }
        return pool
    }

    private fun getActiveRotationPoolName(nowEpochMillis: Long = System.currentTimeMillis()): String? {
        if (!rotationEnabled || rotationOrder.isEmpty() || rotationPools.isEmpty()) {
            return null
        }
        val date = Instant.ofEpochMilli(nowEpochMillis)
            .atZone(rotationZoneId)
            .toLocalDate()
        val daysBetween = ChronoUnit.DAYS.between(rotationAnchorDate, date)
        val cycleIndex = Math.floorDiv(daysBetween, rotationCycleDays.toLong())
        val orderIndex = Math.floorMod(cycleIndex, rotationOrder.size.toLong()).toInt()
        val poolName = rotationOrder[orderIndex]
        return poolName.takeIf { it in rotationPools }
    }

    private fun resolveAffixPoolName(affix: DungeonAffix): String? {
        for (poolName in rotationPoolPriority) {
            val pool = rotationPools[poolName] ?: continue
            if (pool.matches(affix)) {
                return poolName
            }
        }
        return rotationDefaultPool?.takeIf { it in rotationPools }
    }
}
