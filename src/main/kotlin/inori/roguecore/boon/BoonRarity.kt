package inori.roguecore.boon

/**
 * Boon 稀有度
 * @param displayName 显示名
 * @param color 颜色代码
 * @param weight 随机权重（越大越常见）
 */
enum class BoonRarity(
    val displayName: String,
    val color: String,
    val weight: Int
) {
    COMMON("普通", "§f", 60),
    RARE("稀有", "§9", 25),
    EPIC("史诗", "§5", 12),
    LEGENDARY("传说", "§6", 3)
}
