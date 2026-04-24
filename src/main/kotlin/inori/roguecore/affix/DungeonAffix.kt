package inori.roguecore.affix

/**
 * 副本词缀定义
 */
data class DungeonAffix(
    val id: String,
    val name: String,
    val description: String,
    val type: AffixType,
    val value: Double,
    /** true=增加难度, false=增加奖励 */
    val difficulty: Boolean,
    val weight: Int,
    val minFloor: Int = 1,
    val advanced: Boolean = false
)
