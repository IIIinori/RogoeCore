package inori.roguecore.unlock

import taboolib.library.xseries.XMaterial

data class UnlockDefinition(
    val id: String,
    val name: String,
    val description: String,
    val icon: XMaterial,
    val cost: Int,
    val slot: Int,
    val requiredBestFloor: Int = 0,
    val requires: List<String> = emptyList()
)
