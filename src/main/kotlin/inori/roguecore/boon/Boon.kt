package inori.roguecore.boon

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
        val result = hashMapOf<String, Array<Number>>()
        for ((attrName, perLevel) in attributes) {
            val min = perLevel[0] * level
            val max = perLevel[1] * level
            result[attrName] = arrayOf(min, max)
        }
        return result
    }

    /**
     * 生成当前等级下的展示文本。
     */
    fun getPreviewLore(level: Int): List<String> {
        val lore = mutableListOf<String>()
        val rendered = renderDescription(level)
        if (rendered.isNotBlank()) {
            lore += "§7$rendered"
        }
        for ((attrName, values) in attributes) {
            lore += "§7$attrName: §a+${format(values[0] * level)}"
        }
        for (effect in effects) {
            lore += effect.describe(level)
        }
        if (tags.isNotEmpty()) {
            lore += "§8流派: ${tags.joinToString(" / ") { "§f$it" }}"
        }
        return lore
    }

    private fun renderDescription(level: Int): String {
        if (description.isBlank()) {
            return ""
        }
        val firstAttr = attributes.values.firstOrNull()?.getOrNull(0)?.times(level)
        val firstEffect = effects.firstOrNull()
        return description
            .replace("{value}", format(firstAttr ?: firstEffect?.valueAt(level) ?: 0.0))
            .replace("{threshold}", format(firstEffect?.threshold ?: 0.0))
            .replace("{per_tag}", format(firstEffect?.perTag ?: 0.0))
            .replace("{tag}", firstEffect?.tag ?: "")
    }

    private fun format(value: Double): String {
        return if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            String.format("%.1f", value)
        }
    }
}
