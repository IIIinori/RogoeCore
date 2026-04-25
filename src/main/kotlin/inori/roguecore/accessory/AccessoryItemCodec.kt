package inori.roguecore.accessory

import inori.roguecore.item.DungeonBoundItem
import inori.roguecore.item.DungeonLootSource
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import taboolib.library.xseries.XMaterial
import kotlin.math.roundToInt

object AccessoryItemCodec {

    data class SealedAccessoryInfo(
        val definition: AccessoryDefinition,
        val source: DungeonLootSource,
        val floor: Int
    )

    data class InscriptionBookInfo(
        val definition: AccessoryDefinition,
        val source: DungeonLootSource,
        val floor: Int,
        val quality: AccessoryInscriptionQuality
    )

    private val idKey = NamespacedKey("roguecore", "accessory_id")
    private val rarityKey = NamespacedKey("roguecore", "accessory_rarity")
    private val sourceKey = NamespacedKey("roguecore", "accessory_source")
    private val floorKey = NamespacedKey("roguecore", "accessory_floor")
    private val scoreKey = NamespacedKey("roguecore", "accessory_score")
    private val attrKey = NamespacedKey("roguecore", "accessory_attrs")
    private val effectKey = NamespacedKey("roguecore", "accessory_effects")

    private val sealedIdKey = NamespacedKey("roguecore", "sealed_accessory_id")
    private val sealedSourceKey = NamespacedKey("roguecore", "sealed_accessory_source")
    private val sealedFloorKey = NamespacedKey("roguecore", "sealed_accessory_floor")

    private val inscriptionIdKey = NamespacedKey("roguecore", "inscription_accessory_id")
    private val inscriptionQualityKey = NamespacedKey("roguecore", "inscription_quality")
    private val inscriptionSourceKey = NamespacedKey("roguecore", "inscription_source")
    private val inscriptionFloorKey = NamespacedKey("roguecore", "inscription_floor")

    fun isAccessory(item: ItemStack?): Boolean {
        val meta = item?.itemMeta ?: return false
        return meta.persistentDataContainer.has(idKey, PersistentDataType.STRING)
    }

    fun isSealedAccessory(item: ItemStack?): Boolean {
        val meta = item?.itemMeta ?: return false
        return meta.persistentDataContainer.has(sealedIdKey, PersistentDataType.STRING)
    }

    fun isInscriptionBook(item: ItemStack?): Boolean {
        val meta = item?.itemMeta ?: return false
        return meta.persistentDataContainer.has(inscriptionIdKey, PersistentDataType.STRING)
    }

    fun getDefinitionSlot(item: ItemStack?): AccessorySlot? {
        return parse(item)?.definition?.slot ?: parseSealedAccessory(item)?.definition?.slot ?: parseInscriptionBook(item)?.definition?.slot
    }

    fun toItemStack(instance: AccessoryInstance, menuPreview: Boolean = false): ItemStack? {
        val item = instance.definition.material.parseItem() ?: return null
        val meta = item.itemMeta ?: return item
        meta.setDisplayName("${instance.rarity.color}${instance.definition.name}")
        meta.persistentDataContainer.set(idKey, PersistentDataType.STRING, instance.definition.id)
        meta.persistentDataContainer.set(rarityKey, PersistentDataType.STRING, instance.rarity.id)
        meta.persistentDataContainer.set(sourceKey, PersistentDataType.STRING, instance.source.name)
        meta.persistentDataContainer.set(floorKey, PersistentDataType.INTEGER, instance.floor)
        meta.persistentDataContainer.set(scoreKey, PersistentDataType.DOUBLE, instance.score)
        meta.persistentDataContainer.set(attrKey, PersistentDataType.STRING, encodeAttributes(instance.rolledAttributes))
        meta.persistentDataContainer.set(effectKey, PersistentDataType.STRING, encodeEffects(instance.effects))
        meta.lore = buildLore(instance, menuPreview)
        item.itemMeta = meta
        return if (menuPreview) DungeonBoundItem.unmark(item) ?: item else DungeonBoundItem.mark(item) ?: item
    }

    fun buildSealedAccessory(definition: AccessoryDefinition, source: DungeonLootSource, floor: Int): ItemStack? {
        val material = when (definition.slot) {
            AccessorySlot.NECKLACE -> XMaterial.AMETHYST_SHARD
            AccessorySlot.RING, AccessorySlot.RING_1 -> XMaterial.EMERALD
            AccessorySlot.RING_2 -> XMaterial.LAPIS_LAZULI
            AccessorySlot.CHARM -> XMaterial.RABBIT_FOOT
            AccessorySlot.TROPHY -> XMaterial.NETHER_STAR
        }
        val item = material.parseItem() ?: return null
        val meta = item.itemMeta ?: return item
        meta.setDisplayName("§d密封饰品 §7(${definition.slot.displayName})")
        meta.persistentDataContainer.set(sealedIdKey, PersistentDataType.STRING, definition.id)
        meta.persistentDataContainer.set(sealedSourceKey, PersistentDataType.STRING, source.name)
        meta.persistentDataContainer.set(sealedFloorKey, PersistentDataType.INTEGER, floor)
        meta.lore = listOf(
            "",
            "§7一件被封蜡与符印包裹的饰品。",
            "§7原型: §f${definition.name}",
            "§7槽位: §d${definition.slot.displayName}",
            "§7来源: §f${sourceDisplay(source)} §8· 第${floor}层",
            "",
            "§e在 §6/rogue aid §e中鉴定",
            "§c离开副本后消失"
        )
        item.itemMeta = meta
        return DungeonBoundItem.mark(item) ?: item
    }

    fun parseSealedAccessory(item: ItemStack?): SealedAccessoryInfo? {
        val meta = item?.itemMeta ?: return null
        val id = meta.persistentDataContainer.get(sealedIdKey, PersistentDataType.STRING) ?: return null
        val definition = AccessoryRegistry.get(id) ?: return null
        val source = meta.persistentDataContainer.get(sealedSourceKey, PersistentDataType.STRING)
            ?.let { runCatching { DungeonLootSource.valueOf(it) }.getOrNull() }
            ?: DungeonLootSource.CHEST
        val floor = meta.persistentDataContainer.get(sealedFloorKey, PersistentDataType.INTEGER) ?: 1
        return SealedAccessoryInfo(definition, source, floor)
    }

    fun buildInscriptionBook(definition: AccessoryDefinition, source: DungeonLootSource, floor: Int, quality: AccessoryInscriptionQuality): ItemStack? {
        val item = XMaterial.WRITABLE_BOOK.parseItem() ?: return null
        val meta = item.itemMeta ?: return item
        meta.setDisplayName("${quality.color}${quality.displayName}饰品刻印书 §7(${definition.slot.displayName})")
        meta.persistentDataContainer.set(inscriptionIdKey, PersistentDataType.STRING, definition.id)
        meta.persistentDataContainer.set(inscriptionQualityKey, PersistentDataType.STRING, quality.id)
        meta.persistentDataContainer.set(inscriptionSourceKey, PersistentDataType.STRING, source.name)
        meta.persistentDataContainer.set(inscriptionFloorKey, PersistentDataType.INTEGER, floor)
        meta.lore = buildList {
            add("")
            add("§7记录着饰品成型纹路的刻印书。")
            add("§7目标: §f${definition.name}")
            add("§7槽位: §d${definition.slot.displayName}")
            add("§7品质: ${quality.color}${quality.displayName}")
            add("§7来源: §f${sourceDisplay(source)} §8· 第${floor}层")
            add("§7升品幸运: §b+${format(quality.rarityLuck)}%")
            add("§7数值倍率: §a${format(quality.valueMultiplier * 100.0)}%")
            add("")
            add("§e在 §6/rogue inscribe §e中刻印")
            add("§c离开副本后消失")
        }
        item.itemMeta = meta
        return DungeonBoundItem.mark(item) ?: item
    }

    fun parseInscriptionBook(item: ItemStack?): InscriptionBookInfo? {
        val meta = item?.itemMeta ?: return null
        val id = meta.persistentDataContainer.get(inscriptionIdKey, PersistentDataType.STRING) ?: return null
        val definition = AccessoryRegistry.get(id) ?: return null
        val qualityId = meta.persistentDataContainer.get(inscriptionQualityKey, PersistentDataType.STRING) ?: "rough"
        val quality = AccessoryRegistry.getInscriptionQuality(qualityId) ?: return null
        val source = meta.persistentDataContainer.get(inscriptionSourceKey, PersistentDataType.STRING)
            ?.let { runCatching { DungeonLootSource.valueOf(it) }.getOrNull() }
            ?: DungeonLootSource.CHEST
        val floor = meta.persistentDataContainer.get(inscriptionFloorKey, PersistentDataType.INTEGER) ?: 1
        return InscriptionBookInfo(definition, source, floor, quality)
    }

    fun parse(item: ItemStack?): AccessoryInstance? {
        val meta = item?.itemMeta ?: return null
        val id = meta.persistentDataContainer.get(idKey, PersistentDataType.STRING) ?: return null
        val definition = AccessoryRegistry.get(id) ?: return null
        val rarityId = meta.persistentDataContainer.get(rarityKey, PersistentDataType.STRING) ?: "common"
        val rarity = AccessoryRegistry.getRarity(rarityId) ?: AccessoryRegistry.getRarities().firstOrNull() ?: return null
        val source = meta.persistentDataContainer.get(sourceKey, PersistentDataType.STRING)
            ?.let { runCatching { DungeonLootSource.valueOf(it) }.getOrNull() }
            ?: DungeonLootSource.CHEST
        val floor = meta.persistentDataContainer.get(floorKey, PersistentDataType.INTEGER) ?: 1
        val attrs = decodeAttributes(meta.persistentDataContainer.get(attrKey, PersistentDataType.STRING) ?: "")
        val effects = decodeEffects(meta.persistentDataContainer.get(effectKey, PersistentDataType.STRING) ?: "")
            .ifEmpty { definition.effects }
        val score = meta.persistentDataContainer.get(scoreKey, PersistentDataType.DOUBLE)
            ?: estimateScore(attrs, effects)
        return AccessoryInstance(definition, rarity, source, floor, attrs, effects, score)
    }

    private fun buildLore(instance: AccessoryInstance, menuPreview: Boolean): List<String> {
        return buildList {
            add("§8${instance.rarity.displayName} · ${instance.definition.slot.displayName}")
            if (instance.definition.tags.isNotEmpty()) {
                add("§8标签: ${instance.definition.tags.joinToString(" / ") { "§f$it" }}")
            }
            if (instance.definition.lore.isNotEmpty()) {
                addAll(instance.definition.lore)
            }
            if (instance.rolledAttributes.isNotEmpty()) {
                add("")
                add("§7饰品属性:")
                for ((name, value) in instance.rolledAttributes) {
                    add("§8- §f$name §a+${format(value)}")
                }
            }
            if (instance.effects.isNotEmpty()) {
                add("")
                add("§7饰品效果:")
                for (effect in instance.effects) {
                    add(effect.describe())
                }
            }
            add("")
            add("§8来源: ${sourceDisplay(instance.source)} · 第${instance.floor}层")
            add("§8评分: ${instance.score.toInt()}")
            if (menuPreview) {
                add("§e点击取下到背包")
            } else {
                add("§e在 /rogue accessory 中装备")
                add("§c离开副本后消失")
            }
        }
    }

    private fun encodeAttributes(values: Map<String, Double>): String {
        return values.entries.joinToString(";") { "${it.key}=${formatRaw(it.value)}" }
    }

    private fun decodeAttributes(text: String): Map<String, Double> {
        if (text.isBlank()) return emptyMap()
        return text.split(";").mapNotNull { entry ->
            val index = entry.indexOf('=')
            if (index <= 0) return@mapNotNull null
            val key = entry.substring(0, index)
            val value = entry.substring(index + 1).toDoubleOrNull() ?: return@mapNotNull null
            key to value
        }.toMap()
    }

    private fun encodeEffects(effects: List<AccessoryEffect>): String {
        return effects.joinToString("|") { effect ->
            listOf(effect.type.name, formatRaw(effect.value), formatRaw(effect.chance), effect.tag.replace("|", "").replace(",", "")).joinToString(",")
        }
    }

    private fun decodeEffects(text: String): List<AccessoryEffect> {
        if (text.isBlank()) return emptyList()
        return text.split("|").mapNotNull { entry ->
            val parts = entry.split(",")
            val type = runCatching { AccessoryEffectType.valueOf(parts.getOrNull(0) ?: return@mapNotNull null) }.getOrNull()
                ?: return@mapNotNull null
            AccessoryEffect(
                type = type,
                value = parts.getOrNull(1)?.toDoubleOrNull() ?: 0.0,
                chance = parts.getOrNull(2)?.toDoubleOrNull()?.coerceIn(0.0, 1.0) ?: 1.0,
                tag = parts.getOrNull(3) ?: ""
            )
        }
    }

    private fun estimateScore(attrs: Map<String, Double>, effects: List<AccessoryEffect>): Double {
        return attrs.values.sumOf { it.coerceAtLeast(0.0) } + effects.sumOf { it.value.coerceAtLeast(0.0) * 2.0 }
    }

    private fun sourceDisplay(source: DungeonLootSource): String {
        return when (source) {
            DungeonLootSource.CHEST -> "宝箱"
            DungeonLootSource.ELITE -> "精英"
            DungeonLootSource.BOSS -> "Boss"
            DungeonLootSource.HIDDEN -> "隐藏"
        }
    }

    private fun format(number: Double): String {
        val rounded = (number * 100.0).roundToInt() / 100.0
        return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
    }

    private fun formatRaw(number: Double): String = String.format(java.util.Locale.US, "%.4f", number)
}
