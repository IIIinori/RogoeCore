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
    WEAPON_LUCK
}
