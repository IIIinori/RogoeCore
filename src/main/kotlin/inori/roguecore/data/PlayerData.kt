package inori.roguecore.data

import java.util.UUID

/**
 * 玩家永久数据
 */
data class PlayerData(
    val uuid: UUID,
    /** 灵魂碎片（永久货币） */
    var soulShards: Int = 0,
    /** 总运行次数 */
    var totalRuns: Int = 0,
    /** 总通关次数 */
    var totalClears: Int = 0,
    /** 最高到达楼层 */
    var bestFloor: Int = 0,
    /** 总击杀数 */
    var totalKills: Int = 0
)
