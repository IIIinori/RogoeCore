package inori.roguecore.boon

/**
 * Boon 的触发型效果定义。
 */
data class BoonEffect(
    val type: BoonEffectType,
    val valuePerLevel: Double,
    val threshold: Double = 0.0,
    val tag: String = "",
    val perTag: Double = 0.0
) {

    fun valueAt(level: Int): Double {
        return valuePerLevel * level
    }

    fun scaleAt(tagCount: Int): Double {
        return if (tag.isBlank() || perTag <= 0.0) {
            0.0
        } else {
            perTag * tagCount
        }
    }

    fun describe(level: Int): String {
        val value = valueAt(level)
        val scale = if (tag.isBlank()) 0.0 else perTag
        val suffix = if (tag.isNotBlank() && scale > 0.0) {
            "§8(每个${tag}再 +${format(scale)})"
        } else {
            ""
        }

        return when (type) {
            BoonEffectType.EXECUTE ->
                "§d斩杀: §7生命低于 ${format(threshold)}% 时额外造成 §c${format(value)}% §7伤害$suffix"
            BoonEffectType.KILL_HEAL ->
                "§c鲜血: §7击杀怪物后回复 §a${format(value)} §7生命$suffix"
            BoonEffectType.RETALIATE ->
                "§6反刺: §7受击后反击造成 §c${format(value)} §7伤害$suffix"
            BoonEffectType.ROOM_HEAL ->
                "§b复苏: §7通关房间后回复 §a${format(value)}% §7最大生命$suffix"
        }
    }

    private fun format(value: Double): String {
        return if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            String.format("%.1f", value)
        }
    }
}
