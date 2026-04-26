package inori.roguecore.ui

import inori.roguecore.item.DungeonLootManager
import inori.roguecore.item.GearStorageManager
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest

/**
 * 永久装备仓库界面。
 */
object GearStorageUI {

    private val storageSlots = (0 until 45).toList()
    private const val PAGE_SIZE = 45
    private const val PREVIOUS_SLOT = 45
    private const val INFO_SLOT = 49
    private const val CLOSE_SLOT = 50
    private const val NEXT_SLOT = 53

    fun open(player: Player, page: Int = 0) {
        if (!GearStorageManager.isEnabled()) {
            player.sendMessage("§c当前服务器未启用装备仓库。")
            return
        }
        val items = GearStorageManager.getItems(player)
        val capacity = GearStorageManager.getCapacity(player)
        val maxPage = ((maxOf(capacity, items.size) - 1) / PAGE_SIZE).coerceAtLeast(0)
        val safePage = page.coerceIn(0, maxPage)
        val offset = safePage * PAGE_SIZE
        val visibleItems = items.drop(offset).take(PAGE_SIZE)

        player.openMenu<Chest>("§6§l装备仓库 §7(${items.size}/$capacity 第 ${safePage + 1}/${maxPage + 1} 页)") {
            rows(6)
            handLocked(false)

            val controlSlots = setOf(PREVIOUS_SLOT, INFO_SLOT, CLOSE_SLOT, NEXT_SLOT)
            val glass = XMaterial.GRAY_STAINED_GLASS_PANE.parseItem()!!.apply {
                itemMeta = itemMeta?.also { it.setDisplayName(" ") }
            }
            for (slot in 45 until 54) {
                if (slot !in controlSlots) {
                    set(slot, glass)
                }
            }

            for ((localIndex, item) in visibleItems.withIndex()) {
                set(storageSlots[localIndex], toStorageItem(item, offset + localIndex))
            }

            if (safePage > 0) {
                set(PREVIOUS_SLOT, pageItem("§e上一页", "§7查看上一页仓库装备"))
            } else {
                set(PREVIOUS_SLOT, disabledPageItem("§8上一页"))
            }
            set(INFO_SLOT, infoItem(items.size, capacity, safePage, maxPage))
            set(CLOSE_SLOT, closeItem())
            if (safePage < maxPage) {
                set(NEXT_SLOT, pageItem("§e下一页", "§7查看下一页仓库装备"))
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
                    CLOSE_SLOT -> {
                        player.closeInventory()
                        return@onClick
                    }
                }

                val topSize = event.view.topInventory.size
                if (event.rawSlot in storageSlots) {
                    val localIndex = storageSlots.indexOf(event.rawSlot)
                    val index = offset + localIndex
                    if (index >= items.size) {
                        val cursor = event.cursorItem ?: return@onClick
                        val message = GearStorageManager.storeItem(player, cursor)
                        player.sendMessage(message)
                        if (message.startsWith("§a")) {
                            val remaining = cursor.clone()
                            remaining.amount = remaining.amount - 1
                            event.cursorItem = if (remaining.amount > 0) remaining else null
                            open(player, safePage)
                        }
                        return@onClick
                    }
                    val message = when (event.clickEvent().click) {
                        ClickType.SHIFT_LEFT -> GearStorageManager.toggleFavorite(player, index)
                        ClickType.RIGHT -> GearStorageManager.equip(player, index)
                        else -> GearStorageManager.withdraw(player, index)
                    }
                    player.sendMessage(message)
                    open(player, safePage)
                    return@onClick
                }

                if (event.rawSlot >= topSize) {
                    event.currentItem ?: return@onClick
                    val playerSlot = event.clickEvent().slot
                    val message = GearStorageManager.storeFromInventory(player, playerSlot)
                    player.sendMessage(message)
                    open(player, safePage)
                }
            }
        }
    }

    private fun toStorageItem(item: ItemStack, index: Int): ItemStack {
        val favorite = DungeonLootManager.isFavorite(item)
        return item.clone().apply {
            itemMeta = itemMeta?.also { meta ->
                if (favorite) {
                    val currentName = if (meta.hasDisplayName()) meta.displayName else item.type.name
                    if (!currentName.startsWith("§6★ ")) {
                        meta.setDisplayName("§6★ $currentName")
                    }
                }
                val lore = (meta.lore ?: emptyList()).toMutableList()
                lore += ""
                lore += "§7仓库槽位: §f${index + 1}"
                lore += if (favorite) "§6★ 收藏状态: 已收藏" else "§7收藏状态: 未收藏"
                lore += "§e左键: 取出到背包"
                lore += "§e右键: 快速装备"
                lore += "§eShift+左键: 收藏/取消收藏"
                meta.lore = lore
            }
        }
    }

    private fun infoItem(size: Int, capacity: Int, page: Int, maxPage: Int): ItemStack {
        return XMaterial.CHEST.parseItem()!!.apply {
            itemMeta = itemMeta?.also { meta ->
                meta.setDisplayName("§6仓库说明")
                meta.lore = listOf(
                    "",
                    "§7容量: §f$size/$capacity",
                    "§7页数: §f${page + 1}/${maxPage + 1}",
                    "",
                    "§7点击玩家背包里的永久装备可存入。",
                    "§7左键仓库装备可取出。",
                    "§7右键仓库装备可快速装备。",
                    "§7Shift+左键可收藏/取消收藏装备。",
                    "§8收藏装备会阻止局外分解。",
                    "§8只允许存入绑定你的 RogueCore 永久装备。"
                )
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
        return XMaterial.GRAY_STAINED_GLASS_PANE.parseItem()!!.apply {
            itemMeta = itemMeta?.also { meta ->
                meta.setDisplayName(name)
                meta.lore = listOf("", "§8没有更多页面")
            }
        }
    }

    private fun closeItem(): ItemStack {
        return XMaterial.BARRIER.parseItem()!!.apply {
            itemMeta = itemMeta?.also { meta ->
                meta.setDisplayName("§c关闭")
                meta.lore = listOf("", "§7关闭装备仓库")
            }
        }
    }
}
