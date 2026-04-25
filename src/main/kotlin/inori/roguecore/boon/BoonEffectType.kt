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
    ROOM_UPGRADE_RANDOM,

    /** 每个房间首次击杀获得本局碎片 */
    FIRST_KILL_SHARD,

    /** 连续清理战斗房时获得递增碎片 */
    ROOM_STREAK_SHARD,

    /** 清房治疗溢出时转化为吸收护盾 */
    OVERHEAL_SHIELD,

    /** 清房时将现有吸收护盾转化为本局碎片 */
    SHIELD_TO_SHARD,

    /** 在商店消费后返还本局碎片 */
    SHOP_SPEND_REFUND,

    /** 开启宝箱时获得额外收益 */
    CHEST_OPEN_BONUS,

    /** 选择下一层路线时获得路线规划奖励 */
    ROUTE_PICK_BONUS,

    /** 清理精英房时获得额外碎片 */
    ELITE_ROOM_SHARD,

    /** 清理 Boss 房时获得额外碎片 */
    BOSS_ROOM_SHARD,

    /** 开启隐藏房时获得额外碎片 */
    HIDDEN_ROOM_SHARD,

    /** 按当前持有本局碎片比例获得额外收益 */
    SHARD_HELD_SCALING,

    /** 按当前遗物数量获得额外收益 */
    RELIC_COUNT_SHARD,

    /** 按当前神恩数量获得额外收益 */
    BOON_COUNT_SHARD,

    /** 低血清房时获得额外碎片 */
    LOW_HEALTH_ROOM_SHARD,

    /** 获得该神恩时，使下一次神恩选择产生回响 */
    NEXT_BOON_ECHO,

    /** 获得该神恩时，使下一次神恩选择出现变质候选 */
    NEXT_BOON_MUTATION,

    /** 获得该神恩时创建房间预言 */
    ROOM_PROPHECY,

    /** 获得该神恩时创建路线连锁订单 */
    ROUTE_CHAIN,

    /** 清房时偿还/削减灵魂债务 */
    SOUL_DEBT_RELIEF,

    /** 获得该神恩时创建延迟碎片奖励 */
    DELAYED_REWARD_SHARD
}
