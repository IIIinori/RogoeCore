package inori.roguecore.boon

import inori.roguecore.display.ContentDisplayNameResolver
import taboolib.library.xseries.XMaterial

/**
 * Boon 定义
 * @param id 唯一 ID
 * @param name 显示名称
 * @param description 效果描述（用 {value} 占位，运行时替换为当前等级数值）
 * @param rarity 稀有度
 * @param icon GUI 图标材质
 * @param maxLevel 最大等级
 * @param attributes 每级属性增量: AP属性名 -> [min增量, max增量]
 */
data class Boon(
    val id: String,
    val name: String,
    val description: String,
    val rarity: BoonRarity,
    val icon: XMaterial,
    val maxLevel: Int = 3,
    val attributes: Map<String, DoubleArray>,
    val tags: List<String> = emptyList(),
    val effects: List<BoonEffect> = emptyList()
) {
    /**
     * 获取指定等级的属性值
     * @return AP属性名 -> Number[min, max]
     */
    fun getAttributesAtLevel(level: Int): HashMap<String, Array<Number>> {
        return getAttributesAtLevel(level, floorNumber = 1)
    }

    /**
     * 获取指定等级与楼层下的属性值
     * @return AP属性名 -> Number[min, max]
     */
    fun getAttributesAtLevel(level: Int, floorNumber: Int): HashMap<String, Array<Number>> {
        val result = hashMapOf<String, Array<Number>>()
        val floorMultiplier = BoonScaling.floorMultiplier(floorNumber)
        for ((attrName, perLevel) in attributes) {
            val min = perLevel[0] * level * floorMultiplier
            val max = perLevel[1] * level * floorMultiplier
            result[attrName] = arrayOf(min, max)
        }
        return result
    }

    /**
     * 生成当前等级下的展示文本。
     */
    fun getPreviewLore(level: Int): List<String> {
        return getPreviewLore(level, floorNumber = 1)
    }

    /**
     * 生成当前等级与楼层下的展示文本。
     */
    fun getPreviewLore(level: Int, floorNumber: Int): List<String> {
        val lore = mutableListOf<String>()
        val floorMultiplier = BoonScaling.floorMultiplier(floorNumber)
        val rendered = renderDescription(level, floorNumber)
        if (rendered.isNotBlank()) {
            lore += "§7$rendered"
        }
        for ((attrName, values) in attributes) {
            lore += "§7$attrName: §a+${format(values[0] * level * floorMultiplier)}"
        }
        for (effect in effects) {
            lore += effect.describe(level, floorNumber)
        }
        if (tags.isNotEmpty()) {
            lore += "§8流派: ${tags.joinToString(" / ") { "§f${displayName(it, "流派")}" }}"
        }
        return lore
    }

    private fun renderDescription(level: Int, floorNumber: Int): String {
        if (description.isBlank()) {
            return ""
        }
        val floorMultiplier = BoonScaling.floorMultiplier(floorNumber)
        val firstAttr = attributes.values.firstOrNull()?.getOrNull(0)?.times(level)?.times(floorMultiplier)
        val firstEffect = effects.firstOrNull()
        return description
            .replace("{value}", format(firstAttr ?: firstEffect?.valueAt(level, floorNumber) ?: 0.0))
            .replace("{threshold}", format(firstEffect?.threshold ?: 0.0))
            .replace("{per_tag}", format(firstEffect?.perTag ?: 0.0))
            .replace("{tag}", firstEffect?.tag?.takeIf { it.isNotBlank() }?.let { displayName(it, "目标") } ?: "")
    }

    private fun format(value: Double): String {
        return if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            String.format("%.1f", value)
        }
    }

    private fun displayName(raw: String, fallback: String): String {
        return ContentDisplayNameResolver.safeText(raw, fallback)
    }
}
