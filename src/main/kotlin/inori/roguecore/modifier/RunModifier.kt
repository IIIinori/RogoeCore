package inori.roguecore.modifier

/**
 * 玩家当前 run 内的临时修正实例。
 */
data class RunModifier(
    val type: RunModifierType,
    var remainingRooms: Int,
    var charges: Int,
    var value: Double,
    val source: String,
    var payload: String = ""
)
