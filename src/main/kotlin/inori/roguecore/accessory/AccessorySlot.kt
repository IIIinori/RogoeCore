package inori.roguecore.accessory

/** 饰品定义槽与实际装备槽。 */
enum class AccessorySlot(val displayName: String, val templateOnly: Boolean = false) {
    NECKLACE("项链"),
    RING("戒指", templateOnly = true),
    RING_1("戒指"),
    RING_2("印记"),
    CHARM("护符"),
    TROPHY("战利品");

    fun isEquippedSlot(): Boolean = !templateOnly

    fun accepts(definitionSlot: AccessorySlot): Boolean {
        return when (this) {
            RING_1, RING_2 -> definitionSlot == RING || definitionSlot == RING_1 || definitionSlot == RING_2
            else -> this == definitionSlot
        }
    }

    companion object {
        val equippedSlots: List<AccessorySlot> = listOf(NECKLACE, RING_1, RING_2, CHARM, TROPHY)

        fun parse(value: String?): AccessorySlot? {
            if (value.isNullOrBlank()) return null
            return entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) }
        }

        fun equipTargets(definitionSlot: AccessorySlot): List<AccessorySlot> {
            return when (definitionSlot) {
                RING -> listOf(RING_1, RING_2)
                RING_1, RING_2 -> listOf(definitionSlot)
                else -> listOf(definitionSlot)
            }.filter { it.isEquippedSlot() }
        }
    }
}
