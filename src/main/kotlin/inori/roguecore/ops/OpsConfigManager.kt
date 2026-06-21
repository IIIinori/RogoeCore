package inori.roguecore.ops

import taboolib.module.configuration.Configuration
import java.util.concurrent.ConcurrentHashMap

/**
 * 运行时在线调参覆盖层。
 *
 * 覆盖仅保存在内存中，用于运营快速验证，不会写回 balance.yml。
 */
object OpsConfigManager {

    private enum class ValueType {
        INT, DOUBLE, BOOLEAN
    }

    private data class OverrideValue(
        val raw: String,
        val type: ValueType
    )

    private val allowedPrefixes = listOf(
        "hud.",
        "guide.",
        "generation.",
        "storage.loot."
    )

    private val overrides = ConcurrentHashMap<String, OverrideValue>()

    fun isAllowedPath(path: String): Boolean {
        val normalized = normalize(path)
        return allowedPrefixes.any { normalized.startsWith(it) }
    }

    fun set(path: String, raw: String): Result<Unit> {
        val normalized = normalize(path)
        if (!isAllowedPath(normalized)) {
            return Result.failure(IllegalArgumentException("仅允许以下前缀: ${allowedPrefixes.joinToString(", ")}"))
        }
        val override = parseOverride(raw)
            ?: return Result.failure(IllegalArgumentException("值格式无效，仅支持整数/小数/布尔(true|false)"))
        overrides[normalized] = override
        return Result.success(Unit)
    }

    fun clear(path: String): Boolean {
        return overrides.remove(normalize(path)) != null
    }

    fun clearAll() {
        overrides.clear()
    }

    fun get(path: String): String? {
        val value = overrides[normalize(path)] ?: return null
        return value.raw
    }

    fun list(): List<Pair<String, String>> {
        return overrides.entries
            .map { it.key to it.value.raw }
            .sortedBy { it.first }
    }

    fun getInt(config: Configuration, path: String, default: Int): Int {
        val override = overrides[normalize(path)]
        return when (override?.type) {
            ValueType.INT -> override.raw.toIntOrNull() ?: default
            ValueType.DOUBLE -> override.raw.toDoubleOrNull()?.toInt() ?: default
            ValueType.BOOLEAN -> if (override.raw.equals("true", ignoreCase = true)) 1 else 0
            null -> config.getInt(path, default)
        }
    }

    fun getDouble(config: Configuration, path: String, default: Double): Double {
        val override = overrides[normalize(path)]
        return when (override?.type) {
            ValueType.INT -> override.raw.toIntOrNull()?.toDouble() ?: default
            ValueType.DOUBLE -> override.raw.toDoubleOrNull() ?: default
            ValueType.BOOLEAN -> if (override.raw.equals("true", ignoreCase = true)) 1.0 else 0.0
            null -> config.getDouble(path, default)
        }
    }

    fun getBoolean(config: Configuration, path: String, default: Boolean): Boolean {
        val override = overrides[normalize(path)]
        return when (override?.type) {
            ValueType.BOOLEAN -> override.raw.equals("true", ignoreCase = true)
            ValueType.INT -> (override.raw.toIntOrNull() ?: if (default) 1 else 0) != 0
            ValueType.DOUBLE -> (override.raw.toDoubleOrNull() ?: if (default) 1.0 else 0.0) != 0.0
            null -> config.getBoolean(path, default)
        }
    }

    private fun parseOverride(raw: String): OverrideValue? {
        val text = raw.trim()
        if (text.isBlank()) {
            return null
        }
        if (text.equals("true", ignoreCase = true) || text.equals("false", ignoreCase = true)) {
            return OverrideValue(text.lowercase(), ValueType.BOOLEAN)
        }
        if (text.toIntOrNull() != null) {
            return OverrideValue(text, ValueType.INT)
        }
        if (text.toDoubleOrNull() != null) {
            return OverrideValue(text, ValueType.DOUBLE)
        }
        return null
    }

    private fun normalize(path: String): String = path.trim().lowercase()
}
