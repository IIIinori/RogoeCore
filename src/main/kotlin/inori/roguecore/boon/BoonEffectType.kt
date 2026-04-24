package inori.roguecore.boon

/**
 * Boon 触发效果类型。
 */
enum class BoonEffectType {
    /** 攻击残血怪物时追加伤害 */
    EXECUTE,

    /** 击杀怪物后回复生命 */
    KILL_HEAL,

    /** 受击后反刺攻击者 */
    RETALIATE,

    /** 房间通关后回复生命 */
    ROOM_HEAL
}
