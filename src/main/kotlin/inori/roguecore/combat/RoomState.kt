package inori.roguecore.combat

/**
 * 房间战斗状态
 */
enum class RoomState {
    /** 未激活 — 玩家未进入 */
    IDLE,
    /** 战斗中 — 怪物已生成 */
    ACTIVE,
    /** 已通关 — 怪物全部清除 */
    CLEARED
}
