package inori.roguecore.boon

/**
 * 玩家持有的 Boon 实例
 * @param boon Boon 定义
 * @param level 当前等级（同 Boon 再次获得时升级，上限 maxLevel）
 */
data class BoonInstance(
    val boon: Boon,
    var level: Int = 1
) {
    /** 是否可以升级 */
    val canUpgrade: Boolean get() = level < boon.maxLevel

    /** 升级 */
    fun upgrade() {
        if (canUpgrade) level++
    }
}
