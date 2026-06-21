package inori.roguecore.balance

import inori.roguecore.data.PermanentMaterialManager
import inori.roguecore.ops.OpsConfigManager
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

object BalanceConfigManager {

    @Config("balance.yml")
    lateinit var config: Configuration
        private set

    fun getInt(path: String, default: Int): Int {
        return OpsConfigManager.getInt(config, path, default)
    }

    fun getDouble(path: String, default: Double): Double {
        return OpsConfigManager.getDouble(config, path, default)
    }

    fun getBoolean(path: String, default: Boolean): Boolean {
        return OpsConfigManager.getBoolean(config, path, default)
    }

    fun getMaterialMap(path: String, default: Map<PermanentMaterialManager.MaterialType, Int> = emptyMap()): Map<PermanentMaterialManager.MaterialType, Int> {
        val section = config.getConfigurationSection(path) ?: return default
        val result = linkedMapOf<PermanentMaterialManager.MaterialType, Int>()
        for (key in section.getKeys(false)) {
            val type = PermanentMaterialManager.MaterialType.fromId(key) ?: continue
            val amount = section.getInt(key, 0).coerceAtLeast(0)
            if (amount > 0) result[type] = amount
        }
        return if (result.isEmpty()) default else result
    }
}
