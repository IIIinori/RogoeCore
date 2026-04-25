package inori.roguecore.workshop

/**
 * 材料工坊配方类型。
 */
enum class WorkshopRecipeType(val displayName: String) {
    CRAFT("合成"),
    DISMANTLE("拆解"),
    PURCHASE("补料")
}
