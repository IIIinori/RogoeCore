package inori.roguecore.ui

import inori.roguecore.relic.PlayerRelicData
import inori.roguecore.relic.Relic
import org.bukkit.entity.Player
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest

object RelicSelectUI {

    fun open(player: Player, relics: List<Relic>) {
        if (relics.isEmpty()) {
            return
        }

        val title = "§d§l选择遗物"
        DungeonGuiGuard.lock(player, title) { target -> open(target, relics) }

        player.openMenu<Chest>(title) {
            rows(3)
            handLocked(true)

            val slots = when (relics.size) {
                1 -> listOf(13)
                2 -> listOf(11, 15)
                3 -> listOf(11, 13, 15)
                else -> listOf(10, 12, 14, 16)
            }

            val glass = XMaterial.PURPLE_STAINED_GLASS_PANE.parseItem()!!.apply {
                itemMeta = itemMeta?.also { it.setDisplayName(" ") }
            }
            for (slot in 0 until 27) {
                if (slot !in slots) {
                    set(slot, glass)
                }
            }

            for ((index, slot) in slots.withIndex()) {
                if (index >= relics.size) {
                    break
                }
                val relic = relics[index]
                set(slot, (relic.icon.parseItem() ?: XMaterial.PAPER.parseItem()!!).apply {
                    itemMeta = itemMeta?.also { meta ->
                        meta.setDisplayName("${relic.rarity.color}[${relic.rarity.displayName}] §f${relic.name}")
                        meta.lore = listOf("", "§7${relic.description}", "", "§e点击选择")
                    }
                })
            }

            onClick { event ->
                event.isCancelled = true
                val index = slots.indexOf(event.rawSlot)
                if (index < 0 || index >= relics.size) {
                    return@onClick
                }
                DungeonGuiGuard.unlock(player)
                player.closeInventory()
                PlayerRelicData.addRelic(player, relics[index])
            }
        }
    }
}
