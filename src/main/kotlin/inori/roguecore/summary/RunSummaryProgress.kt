package inori.roguecore.summary

import inori.roguecore.milestone.RunMilestoneType
import inori.roguecore.modifier.RunModifierType

/**
 * 正在进行的 run 报告进度。
 */
data class RunSummaryProgress(
    val startedAt: Long,
    var startFloor: Int,
    var highestFloor: Int,
    var roomsCleared: Int = 0,
    var combatRoomsCleared: Int = 0,
    var eliteRoomsCleared: Int = 0,
    var bossRoomsCleared: Int = 0,
    var settledSoulShards: Int = 0,
    var peakRunShards: Int = 0,
    val routeHistory: MutableList<String> = mutableListOf(),
    val milestones: MutableSet<RunMilestoneType> = mutableSetOf(),
    val modifierCounts: MutableMap<RunModifierType, Int> = mutableMapOf()
)
