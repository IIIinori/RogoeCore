package inori.roguecore.boon

/**
 * Boon 的触发型效果定义。
 */
data class BoonEffect(
    val type: BoonEffectType,
    val valuePerLevel: Double,
    val threshold: Double = 0.0,
    val tag: String = "",
    val perTag: Double = 0.0,
    val chance: Double = 1.0,
    val cooldownSeconds: Double = 0.0,
    val durationSeconds: Double = 0.0,
    val radius: Double = 0.0,
    val limit: Int = 0
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
            BoonEffectType.KILL_SHARD ->
                "§6掠魂: §7击杀怪物后获得 §e${format(value)} §7本局碎片$suffix"
            BoonEffectType.KILL_SPEED ->
                "§a疾行: §7击杀后获得 §a${format(value)}% §7移速，持续 §f${format(durationSeconds)}秒$suffix"
            BoonEffectType.CHAIN_DAMAGE ->
                "§d连锁: §7攻击时有 §f${format(chance * 100)}% §7概率对附近敌人造成 §c${format(value)} §7伤害$suffix"
            BoonEffectType.LOW_HEALTH_SHIELD ->
                "§b护命: §7生命低于 ${format(threshold)}% 受击时获得 §b${format(value)} §7吸收护盾$suffix"
            BoonEffectType.ROOM_SHARD ->
                "§6清扫: §7通关房间后获得 §e${format(value)} §7本局碎片$suffix"
            BoonEffectType.ROOM_UPGRADE_RANDOM ->
                "§d顿悟: §7通关房间后有 §f${format(chance * 100)}% §7概率升级一个已有神恩$suffix"
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
