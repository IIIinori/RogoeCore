package inori.roguecore.workshop

import inori.roguecore.data.PermanentMaterialManager
import taboolib.library.xseries.XMaterial

/**
 * 局外材料工坊配方。
 */
data class WorkshopRecipe(
    val id: String,
    val name: String,
    val description: String,
    val type: WorkshopRecipeType,
    val icon: XMaterial,
    val slot: Int,
    val soulShards: Int,
    val cost: Map<PermanentMaterialManager.MaterialType, Int>,
    val reward: Map<PermanentMaterialManager.MaterialType, Int>
)
