package inori.roguecore.talent

import taboolib.library.xseries.XMaterial

/**
 * 天赋效果类型
 */
enum class TalentEffectType {
    /** 进入副本时通过 AP 添加属性 */
    ATTRIBUTE,
    /** 灵魂碎片获取加成（百分比） */
    SHARD_BONUS,
    /** 稀有 Boon 概率提升（百分比） */
    BOON_LUCK
}

/**
 * 天赋定义
 */
data class Talent(
    val id: String,
    val name: String,
    val description: String,
    val icon: XMaterial,
    val maxLevel: Int,
    /** 每级升级消耗的灵魂碎片 */
    val cost: List<Int>,
    /** 效果类型 */
    val effectType: TalentEffectType,
    /** AP 属性名（仅 ATTRIBUTE 类型使用） */
    val attribute: String = "",
    /** 每级效果数值 */
    val valuePerLevel: Double = 0.0
) {
    /** 获取升级到指定等级的消耗 */
    fun getCost(level: Int): Int {
        return cost.getOrElse(level - 1) { cost.lastOrNull() ?: 999999 }
    }

    /** 获取指定等级的效果数值 */
    fun getValue(level: Int): Double {
        return valuePerLevel * level
    }
}
