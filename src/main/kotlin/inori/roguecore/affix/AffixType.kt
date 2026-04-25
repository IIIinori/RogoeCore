package inori.roguecore.affix

/**
 * 词缀效果类型
 */
enum class AffixType {
    /** 怪物生命倍率 */
    MOB_HP_MULTIPLY,
    /** 怪物数量倍率 */
    MOB_COUNT_MULTIPLY,
    /** 怪物移速加成 */
    MOB_SPEED,
    /** 地板着火 */
    FLOOR_FIRE,
    /** 禁止自然回复 */
    NO_HEAL,
    /** 碎片奖励倍率 */
    SHARD_MULTIPLY,
    /** 额外 Boon 选择 */
    EXTRA_BOON,
    /** 武器掉率倍率 */
    WEAPON_LUCK,
    /** 怪物伤害倍率 */
    MOB_DAMAGE_MULTIPLY,
    /** 战斗房通关额外本局碎片 */
    COMBAT_SHARD_FLAT,
    /** 精英房额外隐藏钥匙概率 */
    ELITE_KEY_CHANCE,
    /** Boss 房额外炉核余烬 */
    BOSS_EMBER_BONUS,
    /** 玩家低血时承受更高压力 */
    LOW_HEALTH_PRESSURE,
    /** 怪物周期恢复生命 */
    MOB_REGEN,
    /** 怪物生成时获得吸收护盾 */
    MOB_SPAWN_SHIELD,
    /** 怪物低血时伤害提高 */
    MOB_LOW_HEALTH_RAGE,
    /** 怪物攻击点燃玩家 */
    MOB_FIRE_ATTACK,
    /** 战斗房周期性虚空脉冲 */
    VOID_FIELD,
    /** 降低玩家治疗量 */
    HEALING_REDUCE,
    /** 怪物攻击吸血 */
    MOB_LIFESTEAL,
    /** Boss 房怪物伤害倍率 */
    BOSS_DAMAGE_MULTIPLY,
    /** 战斗房通关额外炉核余烬 */
    COMBAT_EMBER_FLAT,
    /** 精英/Boss 额外隐藏战利品概率 */
    HIDDEN_LOOT_CHANCE,
    /** 提高神恩稀有度权重 */
    BOON_LUCK,
    /** Boss 房额外遗物祈愿概率 */
    BOSS_RELIC_CHANCE,
    /** 宝箱房额外碎片 */
    CHEST_SHARD_BONUS
}
