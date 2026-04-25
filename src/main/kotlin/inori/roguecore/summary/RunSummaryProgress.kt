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
    val modifierCounts: MutableMap<RunModifierType, Int> = mutableMapOf(),
    val lootCounts: MutableMap<String, Int> = mutableMapOf(),
    var salvagedCount: Int = 0,
    var salvagedRunShards: Int = 0,
    var salvagedSoulShards: Int = 0,
    val salvagedMaterials: MutableMap<String, Int> = mutableMapOf(),
    val collectionUnlocks: MutableList<String> = mutableListOf(),
    var bossFirstKills: Int = 0
)
