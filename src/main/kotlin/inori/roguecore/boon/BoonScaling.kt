package inori.roguecore.boon

/**
 * 神恩楼层成长参数。
 *
 * 每 10 层提升一次倍率，避免前期过强并保证后期神恩有存在感。
 */
object BoonScaling {

    private const val FLOOR_STEP = 10
    private const val BONUS_PER_STEP = 0.10

    fun floorMultiplier(floorNumber: Int): Double {
        val steps = ((floorNumber.coerceAtLeast(1) - 1) / FLOOR_STEP).coerceAtLeast(0)
        return 1.0 + steps * BONUS_PER_STEP
    }
}
