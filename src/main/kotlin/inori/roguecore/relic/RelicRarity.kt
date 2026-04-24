package inori.roguecore.relic

enum class RelicRarity(
    val displayName: String,
    val color: String,
    val weight: Int
) {
    COMMON("普通", "§f", 60),
    RARE("稀有", "§9", 28),
    EPIC("史诗", "§5", 10),
    LEGENDARY("传说", "§6", 2)
}
