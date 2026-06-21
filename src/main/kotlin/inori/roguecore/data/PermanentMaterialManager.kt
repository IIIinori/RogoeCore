package inori.roguecore.data

import org.bukkit.entity.Player
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration
import kotlin.random.Random

/**
 * 局外锻造材料管理器。
 */
object PermanentMaterialManager {

    @Config("loot.yml")
    lateinit var config: Configuration
        private set

    enum class MaterialType(val id: String, val displayName: String, val color: String) {
        SOUL_IRON("soul_iron", "魂铁", "§7"),
        INSCRIPTION_DUST("inscription_dust", "铭刻粉尘", "§b"),
        RELIC_FRAGMENT("relic_fragment", "遗物残片", "§d"),
        CROWN_SHARD("crown_shard", "王冠碎片", "§6"),
        ASTRAL_CORE("astral_core", "星界核心", "§5");

        fun coloredName(): String = "$color$displayName"

        companion object {
            fun fromId(id: String): MaterialType? {
                return values().firstOrNull { it.id.equals(id, ignoreCase = true) }
            }
        }
    }

    private fun key(type: MaterialType): String = "material.${type.id}"

    fun get(player: Player, type: MaterialType): Int {
        return DatabaseManager.getOrCreateContainer(player.uniqueId)[key(type)]?.toIntOrNull() ?: 0
    }

    fun add(player: Player, type: MaterialType, amount: Int) {
        if (amount <= 0) return
        val container = DatabaseManager.getOrCreateContainer(player.uniqueId)
        container[key(type)] = get(player, type) + amount
    }

    fun set(player: Player, type: MaterialType, amount: Int) {
        val container = DatabaseManager.getOrCreateContainer(player.uniqueId)
        container[key(type)] = amount.coerceAtLeast(0)
    }

    fun take(player: Player, type: MaterialType, amount: Int): Boolean {
        val safeAmount = amount.coerceAtLeast(0)
        if (safeAmount == 0) return true
        val current = get(player, type)
        if (current < safeAmount) return false
        val container = DatabaseManager.getOrCreateContainer(player.uniqueId)
        container[key(type)] = current - safeAmount
        return true
    }

    fun addAll(player: Player, rewards: Map<MaterialType, Int>) {
        for ((type, amount) in rewards) {
            add(player, type, amount)
        }
    }

    fun materialNames(): List<String> {
        return MaterialType.values().map { it.displayName }
    }

    fun fromDisplayName(name: String): MaterialType? {
        return MaterialType.values().firstOrNull { it.displayName == name }
    }

    fun formatOwnedLine(player: Player, type: MaterialType): String {
        return "${type.coloredName()} §fx${get(player, type)}"
    }

    fun hasCost(player: Player, cost: Map<MaterialType, Int>): Boolean {
        return cost.all { (type, amount) -> get(player, type) >= amount.coerceAtLeast(0) }
    }

    fun takeCost(player: Player, cost: Map<MaterialType, Int>): Boolean {
        if (!hasCost(player, cost)) {
            return false
        }
        val container = DatabaseManager.getOrCreateContainer(player.uniqueId)
        for ((type, amount) in cost) {
            val safeAmount = amount.coerceAtLeast(0)
            if (safeAmount > 0) {
                container[key(type)] = get(player, type) - safeAmount
            }
        }
        return true
    }

    fun formatCost(cost: Map<MaterialType, Int>): String {
        if (cost.isEmpty()) {
            return "§8无材料消耗"
        }
        return cost.entries.joinToString(" §7+ ") { (type, amount) ->
            "${type.coloredName()} §fx$amount"
        }
    }

    fun formatOwned(player: Player): List<String> {
        return MaterialType.values().map { type ->
            "§7${type.coloredName()}: §f${get(player, type)}"
        }
    }

    fun parseCost(section: taboolib.library.configuration.ConfigurationSection?): Map<MaterialType, Int> {
        if (section == null) {
            return emptyMap()
        }
        return section.getKeys(false).mapNotNull { key ->
            val type = MaterialType.fromId(key) ?: return@mapNotNull null
            type to section.getInt(key, 0).coerceAtLeast(0)
        }.filter { it.second > 0 }.toMap()
    }

    fun rollSalvageRewards(rarityId: String, bonusPerMaterial: Int = 0): Map<MaterialType, Int> {
        val section = config.getConfigurationSection("permanent-materials.salvage.${rarityId.lowercase()}") ?: return emptyMap()
        val rewards = linkedMapOf<MaterialType, Int>()
        val safeBonus = bonusPerMaterial.coerceAtLeast(0)
        for (key in section.getKeys(false)) {
            val type = MaterialType.fromId(key) ?: continue
            val values = section.getIntegerList(key)
            val amount = when {
                values.size >= 2 -> Random.nextInt(values[0].coerceAtMost(values[1]), values[0].coerceAtLeast(values[1]) + 1)
                values.size == 1 -> values[0]
                else -> section.getInt(key, 0)
            }.coerceAtLeast(0) + safeBonus
            if (amount > 0) {
                rewards[type] = amount
            }
        }
        return rewards
    }
}
