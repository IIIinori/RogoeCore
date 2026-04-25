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
    ROOM_HEAL,

    /** 击杀怪物后获得本局碎片 */
    KILL_SHARD,

    /** 击杀怪物后获得短暂移速 */
    KILL_SPEED,

    /** 攻击时概率连锁伤害附近敌人 */
    CHAIN_DAMAGE,

    /** 低血受击时获得吸收护盾 */
    LOW_HEALTH_SHIELD,

    /** 房间通关后获得额外本局碎片 */
    ROOM_SHARD,

    /** 房间通关后概率升级一个已有神恩 */
    ROOM_UPGRADE_RANDOM
}
