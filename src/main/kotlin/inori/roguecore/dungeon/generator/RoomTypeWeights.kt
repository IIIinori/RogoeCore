package inori.roguecore.dungeon.generator

import inori.roguecore.dungeon.room.RoomType
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.info
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration
import kotlin.math.pow

/**
 * 房间类型权重 — 从 config.yml 加载
 */
object RoomTypeWeights {

    @Config("config.yml")
    lateinit var config: Configuration
        private set

    private var weights = mapOf(
        RoomType.COMBAT to 60,
        RoomType.ELITE to 10,
        RoomType.SHOP to 10,
        RoomType.FORGE to 6,
        RoomType.CHEST to 10,
        RoomType.REST to 8,
        RoomType.EXTRACTION to 4,
        RoomType.CONTRACT to 4,
        RoomType.TRIAL to 6,
        RoomType.GAMBLE to 3,
        RoomType.SHRINE to 3
    )

    private var diversityEnabled = true
    private var postCapMultiplier = 0.35
    private var uniqueTypes = mutableSetOf(RoomType.EXTRACTION)
    private var softCaps = mutableMapOf(
        RoomType.SHOP to 2,
        RoomType.FORGE to 2,
        RoomType.REST to 2,
        RoomType.EXTRACTION to 1,
        RoomType.CONTRACT to 2,
        RoomType.TRIAL to 2,
        RoomType.GAMBLE to 1,
        RoomType.SHRINE to 1
    )

    @Awake(LifeCycle.ENABLE)
    fun load() {
        val section = config.getConfigurationSection("room-weights") ?: return
        val loaded = mutableMapOf<RoomType, Int>()

        for (key in section.getKeys(false)) {
            val type = try { RoomType.valueOf(key.uppercase()) } catch (_: Exception) { continue }
            // SPAWN 不参与随机分配
            if (type == RoomType.SPAWN) continue
            loaded[type] = section.getInt(key, 10)
        }

        if (loaded.isNotEmpty()) {
            weights = loaded
        }

        loadDiversityRules()
        info("[RogueCore] 房间类型权重已加载: ${weights.entries.joinToString { "${it.key.name}=${it.value}" }}")
    }

    fun getWeights(modifiers: Map<RoomType, Int> = emptyMap()): Map<RoomType, Int> {
        if (modifiers.isEmpty()) {
            return weights
        }
        val merged = weights.toMutableMap()
        for ((type, delta) in modifiers) {
            if (type == RoomType.SPAWN || type == RoomType.BOSS || type == RoomType.HIDDEN) {
                continue
            }
            merged[type] = (merged[type] ?: 0) + delta
        }
        return merged.filterValues { it > 0 }
    }

    fun applyDiversityRules(weights: Map<RoomType, Int>, assignedCounts: Map<RoomType, Int>): Map<RoomType, Int> {
        if (!diversityEnabled || assignedCounts.isEmpty()) {
            return weights
        }
        val adjusted = mutableMapOf<RoomType, Int>()
        for ((type, weight) in weights) {
            if (weight <= 0) {
                continue
            }
            if (type in uniqueTypes && (assignedCounts[type] ?: 0) > 0) {
                continue
            }
            val count = assignedCounts[type] ?: 0
            val softCap = softCaps[type]
            if (softCap != null && count >= softCap) {
                val overflow = count - softCap + 1
                val scaled = (weight * postCapMultiplier.pow(overflow)).toInt().coerceAtLeast(1)
                adjusted[type] = scaled
                continue
            }
            adjusted[type] = weight
        }
        return adjusted.filterValues { it > 0 }
    }

    private fun loadDiversityRules() {
        diversityEnabled = config.getBoolean("room-diversity.enabled", true)
        postCapMultiplier = config.getDouble("room-diversity.post-cap-multiplier", 0.35).coerceIn(0.05, 1.0)

        uniqueTypes = config.getStringList("room-diversity.unique-types")
            .mapNotNull(::parseRoomType)
            .toMutableSet()
            .ifEmpty { mutableSetOf(RoomType.EXTRACTION) }

        val loadedSoftCaps = mutableMapOf<RoomType, Int>()
        val section = config.getConfigurationSection("room-diversity.soft-caps")
        if (section != null) {
            for (key in section.getKeys(false)) {
                val type = parseRoomType(key) ?: continue
                loadedSoftCaps[type] = section.getInt(key, 1).coerceAtLeast(1)
            }
        }
        if (loadedSoftCaps.isNotEmpty()) {
            softCaps = loadedSoftCaps
        } else {
            softCaps = mutableMapOf(
                RoomType.SHOP to 2,
                RoomType.FORGE to 2,
                RoomType.REST to 2,
                RoomType.EXTRACTION to 1,
                RoomType.CONTRACT to 2,
                RoomType.TRIAL to 2,
                RoomType.GAMBLE to 1,
                RoomType.SHRINE to 1
            )
        }
    }

    private fun parseRoomType(id: String): RoomType? {
        return try {
            RoomType.valueOf(id.uppercase())
        } catch (_: Exception) {
            null
        }
    }
}
