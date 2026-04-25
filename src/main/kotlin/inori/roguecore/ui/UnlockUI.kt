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

    private val researchSlots = listOf(
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    )
    private const val PAGE_SIZE = 28
    private const val PREVIOUS_SLOT = 45
    private const val OVERVIEW_SLOT = 48
    private const val CODEX_SLOT = 49
    private const val CLOSE_SLOT = 50
    private const val NEXT_SLOT = 53

    fun open(player: Player, page: Int = 0) {
        val unlocks = UnlockRegistry.getAllSorted()
        val data = PlayerDataManager.get(player.uniqueId)
        val maxPage = ((unlocks.size - 1) / PAGE_SIZE).coerceAtLeast(0)
        val safePage = page.coerceIn(0, maxPage)
        val visibleUnlocks = unlocks.drop(safePage * PAGE_SIZE).take(PAGE_SIZE)
        val researchMapping = researchSlots.zip(visibleUnlocks).toMap()

        player.openMenu<Chest>("§d§l研究所 §7(碎片: §e${data.soulShards}§7 第 ${safePage + 1}/${maxPage + 1} 页)") {
            rows(6)
            handLocked(true)

            val activeSlots = researchSlots.toSet() + setOf(PREVIOUS_SLOT, OVERVIEW_SLOT, CODEX_SLOT, CLOSE_SLOT, NEXT_SLOT)
            val glass = XMaterial.PURPLE_STAINED_GLASS_PANE.parseItem()!!.apply {
                itemMeta = itemMeta?.also { it.setDisplayName(" ") }
            }
            for (slot in 0 until 54) {
                if (slot !in activeSlots) {
                    set(slot, glass)
                }
            }

            for ((slot, unlock) in researchMapping) {
                set(slot, buildItem(player, unlock))
            }

            if (safePage > 0) {
                set(PREVIOUS_SLOT, pageItem("§e上一页", "§7查看上一页研究项目"))
            } else {
                set(PREVIOUS_SLOT, disabledPageItem("§8上一页"))
            }
            set(OVERVIEW_SLOT, XMaterial.NETHER_STAR.parseItem()!!.apply {
                itemMeta = itemMeta?.also { meta ->
                    meta.setDisplayName("§d研究效果总览")
                    meta.lore = listOf("", "§7查看当前已完成研究带来的总加成", "§7包含仓库、鉴定、锻造书、局外锻造和路线事件", "", "§e点击打开")
                }
            })
            set(CODEX_SLOT, XMaterial.BOOK.parseItem()!!.apply {
                itemMeta = itemMeta?.also { meta ->
                    meta.setDisplayName("§5打开冒险图鉴")
                    meta.lore = listOf("", "§7查看路线、遗物池与事件强化总览")
                }
            })
            set(CLOSE_SLOT, XMaterial.BARRIER.parseItem()!!.apply {
                itemMeta = itemMeta?.also { meta ->
                    meta.setDisplayName("§c关闭")
                    meta.lore = listOf("", "§7关闭研究所")
                }
            })
            if (safePage < maxPage) {
                set(NEXT_SLOT, pageItem("§e下一页", "§7查看下一页研究项目"))
            } else {
                set(NEXT_SLOT, disabledPageItem("§8下一页"))
            }

            onClick { event ->
                event.isCancelled = true
                when (event.rawSlot) {
                    PREVIOUS_SLOT -> {
                        if (safePage > 0) {
                            open(player, safePage - 1)
                        }
                        return@onClick
                    }
                    NEXT_SLOT -> {
                        if (safePage < maxPage) {
                            open(player, safePage + 1)
                        }
                        return@onClick
                    }
                    OVERVIEW_SLOT -> {
                        player.closeInventory()
                        ResearchOverviewUI.open(player)
                        return@onClick
                    }
                    CODEX_SLOT -> {
                        player.closeInventory()
                        CodexUI.open(player)
                        return@onClick
                    }
                    CLOSE_SLOT -> {
                        player.closeInventory()
                        return@onClick
                    }
                }

                val unlock = researchMapping[event.rawSlot] ?: return@onClick
                if (UnlockManager.purchase(player, unlock.id)) {
                    player.closeInventory()
                    open(player, safePage)
                }
            }
        }
    }

    private fun pageItem(name: String, line: String): ItemStack {
        return XMaterial.ARROW.parseItem()!!.apply {
            itemMeta = itemMeta?.also { meta ->
                meta.setDisplayName(name)
                meta.lore = listOf("", line)
            }
        }
    }

    private fun disabledPageItem(name: String): ItemStack {
        return XMaterial.PURPLE_STAINED_GLASS_PANE.parseItem()!!.apply {
            itemMeta = itemMeta?.also { meta ->
                meta.setDisplayName(name)
                meta.lore = listOf("", "§8没有更多研究页面")
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
