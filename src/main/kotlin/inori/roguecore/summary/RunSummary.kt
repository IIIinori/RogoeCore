package inori.roguecore.summary

/**
 * 一次冒险的最终报告。
 */
data class RunSummary(
    val result: RunEndReason,
    val startFloor: Int,
    val highestFloor: Int,
    val roomsCleared: Int,
    val combatRoomsCleared: Int,
    val eliteRoomsCleared: Int,
    val bossRoomsCleared: Int,
    val settledSoulShards: Int,
    val peakRunShards: Int,
    val boonCount: Int,
    val relicCount: Int,
    val curseCount: Int,
    val modifierCount: Int,
    val resonanceLines: List<String>,
    val routeHistory: List<String>,
    val milestoneNames: List<String>,
    val modifierCounts: Map<String, Int>,
    val lootCounts: Map<String, Int> = emptyMap(),
    val salvagedCount: Int = 0,
    val salvagedRunShards: Int = 0,
    val salvagedSoulShards: Int = 0,
    val salvagedMaterials: Map<String, Int> = emptyMap(),
    val collectionUnlocks: List<String> = emptyList(),
    val bossFirstKills: Int = 0,
    val durationSeconds: Long
)
