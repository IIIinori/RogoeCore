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
            BoonEffectType.FIRST_KILL_SHARD ->
                "§6首杀: §7每个房间首次击杀获得 §e${format(value)} §7本局碎片$suffix"
            BoonEffectType.ROOM_STREAK_SHARD ->
                "§6连清: §7连续清理战斗房时获得 §e${format(value)} §7基础碎片，连清越高收益越高$suffix"
            BoonEffectType.OVERHEAL_SHIELD ->
                "§b溢疗: §7清房治疗溢出时转化为 §b${format(value)} §7吸收护盾$suffix"
            BoonEffectType.SHIELD_TO_SHARD ->
                "§6盾税: §7清房时将现有护盾转化为至多 §e${format(value)} §7本局碎片$suffix"
            BoonEffectType.SHOP_SPEND_REFUND ->
                "§6返利: §7商店消费后返还 §e${format(value)}% §7本局碎片$suffix"
            BoonEffectType.CHEST_OPEN_BONUS ->
                "§6开箱: §7开启宝箱时额外获得 §e${format(value)} §7本局碎片$suffix"
            BoonEffectType.ROUTE_PICK_BONUS ->
                "§d规划: §7选择下一层路线时获得 §e${format(value)} §7本局碎片$suffix"
            BoonEffectType.ELITE_ROOM_SHARD ->
                "§6猎首: §7清理精英房时获得 §e${format(value)} §7本局碎片$suffix"
            BoonEffectType.BOSS_ROOM_SHARD ->
                "§6破冠: §7清理 Boss 房时获得 §e${format(value)} §7本局碎片$suffix"
            BoonEffectType.HIDDEN_ROOM_SHARD ->
                "§9秘藏: §7开启隐藏房时获得 §e${format(value)} §7本局碎片$suffix"
            BoonEffectType.SHARD_HELD_SCALING ->
                "§6复利: §7清房时按当前本局碎片的 §e${format(value)}% §7获得额外收益$suffix"
            BoonEffectType.RELIC_COUNT_SHARD ->
                "§d遗响: §7清房时每件遗物提供 §e${format(value)} §7本局碎片$suffix"
            BoonEffectType.BOON_COUNT_SHARD ->
                "§d群星: §7清房时每个神恩提供 §e${format(value)} §7本局碎片$suffix"
            BoonEffectType.LOW_HEALTH_ROOM_SHARD ->
                "§c险境: §7生命低于 ${format(threshold)}% 清房时获得 §e${format(value)} §7本局碎片$suffix"
            BoonEffectType.NEXT_BOON_ECHO ->
                "§d回响: §7获得后使下一次神恩选择额外复制 §d${format(value.coerceAtLeast(1.0))} §7次"
            BoonEffectType.NEXT_BOON_MUTATION ->
                "§5变质: §7获得后使下一次神恩选择额外出现 §d${format(value.coerceAtLeast(1.0))} §7个候选"
            BoonEffectType.ROOM_PROPHECY ->
                "§d预言: §7获得后预言 §f${tag.ifBlank { "随机" }} §7房，期限 §b${format(threshold.coerceAtLeast(1.0))} §7房"
            BoonEffectType.ROUTE_CHAIN ->
                "§a订单: §7获得后要求按 §f${tag.ifBlank { "指定路线" }.replace(">", " > ")} §7推进，完成后触发奖励"
            BoonEffectType.SOUL_DEBT_RELIEF ->
                "§a赎债: §7清房时削减 §e${format(value)} §7灵魂债务$suffix"
            BoonEffectType.DELAYED_REWARD_SHARD ->
                "§d托管: §7获得后封存 §e${format(value)} §7本局碎片，§b${format(threshold.coerceAtLeast(1.0))} §7房后兑现"
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
