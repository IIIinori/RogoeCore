package inori.roguecore.item

import org.bukkit.inventory.EquipmentSlot
import taboolib.library.xseries.XMaterial

data class DungeonLootDefinition(
    val id: String,
    val name: String,
    val material: XMaterial,
    val lore: List<String>,
    val theme: String?,
    val tags: Set<String>,
    val sources: Set<DungeonLootSource>,
    val weight: Int,
    val minFloor: Int,
    val maxFloor: Int,
    val equipmentSlot: EquipmentSlot?,
    val attributes: List<DungeonLootAttributeDefinition>
) {
    fun matches(source: DungeonLootSource, floor: Int): Boolean {
        return source in sources && floor in minFloor..maxFloor
    }
}

data class DungeonLootAttributeDefinition(
    val name: String,
    val min: Double,
    val max: Double,
    val percentage: Boolean = false
)

data class DungeonLootRarity(
    val id: String,
    val displayName: String,
    val color: String,
    val weight: Int,
    val multiplier: Double,
    val maxAffixes: Int
)

enum class DungeonLootAffixType {
    PREFIX,
    SUFFIX
}

data class DungeonLootAffix(
    val id: String,
    val name: String,
    val type: DungeonLootAffixType,
    val weight: Int,
    val minFloor: Int,
    val maxFloor: Int,
    val group: String?,
    val excludes: Set<String>,
    val slots: Set<EquipmentSlot>,
    val sources: Set<DungeonLootSource>,
    val slotWeight: Map<EquipmentSlot, Double>,
    val sourceWeight: Map<DungeonLootSource, Double>,
    val lore: List<String>,
    val attributes: List<DungeonLootAttributeDefinition>
)

data class DungeonLootSetBonus(
    val id: String,
    val name: String,
    val themes: Set<String>,
    val tags: Set<String>,
    val tiers: List<DungeonLootSetTier>
) {
    fun matches(definition: DungeonLootDefinition): Boolean {
        val themeMatch = definition.theme != null && definition.theme in themes
        val tagMatch = definition.tags.any { it in tags }
        return themeMatch || tagMatch
    }
}

data class DungeonLootSetTier(
    val count: Int,
    val lore: List<String>,
    val attributes: List<DungeonLootAttributeDefinition>
)
