package inori.roguecore.accessory

import inori.roguecore.data.PermanentMaterialManager

data class AccessoryInscriptionQuality(
    val id: String,
    val displayName: String,
    val color: String,
    val weight: Int,
    val timeMillis: Long,
    val soulShards: Int,
    val materials: Map<PermanentMaterialManager.MaterialType, Int>,
    val rarityLuck: Double,
    val valueMultiplier: Double
)
