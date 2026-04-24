package inori.roguecore.relic

import taboolib.library.xseries.XMaterial

data class Relic(
    val id: String,
    val name: String,
    val description: String,
    val rarity: RelicRarity,
    val icon: XMaterial,
    val effectType: RelicEffectType,
    val value: Double,
    val threshold: Double = 0.0,
    val requiredUnlock: String? = null
)
