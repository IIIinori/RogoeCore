package inori.roguecore.ui

import inori.roguecore.data.PlayerDataManager
import inori.roguecore.unlock.UnlockDefinition
import inori.roguecore.unlock.UnlockManager
import inori.roguecore.unlock.UnlockRegistry
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest

object UnlockUI {

    fun open(player: Player) {
        val unlocks = UnlockRegistry.getAllSorted()
        val data = PlayerDataManager.get(player.uniqueId)
        val rows = 5

        player.openMenu<Chest>("§d§l研究所 §7(碎片: §e${data.soulShards}§7)") {
            rows(rows)
            handLocked(true)

            val glass = XMaterial.PURPLE_STAINED_GLASS_PANE.parseItem()!!.apply {
                itemMeta = itemMeta?.also { it.setDisplayName(" ") }
            }
            val unlockSlots = unlocks.map { it.slot }.toSet()
            for (slot in 0 until rows * 9) {
                if (slot !in unlockSlots) {
                    set(slot, glass)
                }
            }

            set(36, XMaterial.BOOK.parseItem()!!.apply {
                itemMeta = itemMeta?.also { meta ->
                    meta.setDisplayName("§5打开冒险图鉴")
                    meta.lore = listOf("", "§7查看路线、遗物池与事件强化总览")
                }
            })

            for (unlock in unlocks) {
                set(unlock.slot, buildItem(player, unlock))
            }

            onClick { event ->
                event.isCancelled = true
                if (event.rawSlot == 36) {
                    player.closeInventory()
                    CodexUI.open(player)
                    return@onClick
                }
                val unlock = unlocks.firstOrNull { it.slot == event.rawSlot } ?: return@onClick
                if (UnlockManager.purchase(player, unlock.id)) {
                    player.closeInventory()
                    open(player)
                }
            }
        }
    }

    private fun buildItem(player: Player, unlock: UnlockDefinition): ItemStack {
        val purchased = UnlockManager.hasUnlock(player, unlock.id)
        val bestFloor = PlayerDataManager.get(player.uniqueId).bestFloor
        val requirementsMet = unlock.requires.all { UnlockManager.hasUnlock(player, it) }
        val floorMet = bestFloor >= unlock.requiredBestFloor
        val item = if (purchased) {
            XMaterial.ENCHANTED_BOOK.parseItem()!!
        } else {
            unlock.icon.parseItem() ?: XMaterial.PAPER.parseItem()!!
        }
        val meta = item.itemMeta ?: return item

        meta.setDisplayName(
            if (purchased) "§a${unlock.name} §7[已完成]"
            else "§f${unlock.name} §7[§e${unlock.cost}碎片§7]"
        )

        val lore = mutableListOf<String>()
        lore += ""
        lore += "§7${unlock.description}"
        lore += ""

        if (unlock.requiredBestFloor > 0) {
            lore += if (floorMet) {
                "§a已达到楼层要求: ${unlock.requiredBestFloor}"
            } else {
                "§c需要最高到达 ${unlock.requiredBestFloor} 层"
            }
        }

        if (unlock.requires.isNotEmpty()) {
            lore += ""
            lore += "§7前置研究:"
            for (requirement in unlock.requires) {
                val name = UnlockRegistry.get(requirement)?.name ?: requirement
                lore += if (UnlockManager.hasUnlock(player, requirement)) {
                    "§a$name"
                } else {
                    "§c$name"
                }
            }
        }

        lore += ""
        lore += when {
            purchased -> "§a已完成研究"
            !floorMet -> "§c未满足楼层要求"
            !requirementsMet -> "§c前置研究未完成"
            else -> "§e点击研究"
        }
        meta.lore = lore
        item.itemMeta = meta
        return item
    }
}
