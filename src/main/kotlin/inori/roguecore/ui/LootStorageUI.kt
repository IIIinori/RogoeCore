package inori.roguecore.ui

import inori.roguecore.item.LootStorageManager
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest

/** 战利品仓库界面。 */
object LootStorageUI {

    private val storageSlots = (0 until 45).toList()
    private const val PAGE_SIZE = 45
    private const val PREVIOUS_SLOT = 45
    private const val STORE_ALL_SLOT = 46
    private const val INFO_SLOT = 47
    private const val SALVAGE_SLOT = 48
    private const val CLOSE_SLOT = 49
    private const val IDENTIFY_SLOT = 50
    private const val ACCESSORY_SLOT = 51
    private const val NEXT_SLOT = 53

    fun open(player: Player, page: Int = 0) {
        if (!LootStorageManager.isEnabled()) {
            player.sendMessage("§c当前服务器未启用战利品仓库。")
            return
        }
        val items = LootStorageManager.getItems(player)
        val capacity = LootStorageManager.getCapacity(player)
        val maxPage = ((maxOf(capacity, items.size) - 1) / PAGE_SIZE).coerceAtLeast(0)
        val safePage = page.coerceIn(0, maxPage)
        val offset = safePage * PAGE_SIZE
        val visibleItems = items.drop(offset).take(PAGE_SIZE)

        player.openMenu<Chest>("§b§l战利品仓库 §7(${items.size}/$capacity 第 ${safePage + 1}/${maxPage + 1} 页)") {
            rows(6)
            handLocked(false)

            val controlSlots = setOf(PREVIOUS_SLOT, STORE_ALL_SLOT, INFO_SLOT, SALVAGE_SLOT, CLOSE_SLOT, IDENTIFY_SLOT, ACCESSORY_SLOT, NEXT_SLOT)
            val glass = XMaterial.GRAY_STAINED_GLASS_PANE.parseItem()!!.apply { itemMeta = itemMeta?.also { it.setDisplayName(" ") } }
            for (slot in 45 until 54) if (slot !in controlSlots) set(slot, glass)

            for ((localIndex, item) in visibleItems.withIndex()) {
                set(storageSlots[localIndex], toStorageItem(item, offset + localIndex))
            }

            set(PREVIOUS_SLOT, if (safePage > 0) pageItem("§e上一页", "§7查看上一页战利品") else disabledPageItem("§8上一页"))
            set(STORE_ALL_SLOT, storeAllItem())
            set(INFO_SLOT, infoItem(items.size, capacity, safePage, maxPage))
            set(SALVAGE_SLOT, shortcutItem(XMaterial.HOPPER, "§6回收工坊", "/rogue gear salvage"))
            set(CLOSE_SLOT, closeItem())
            set(IDENTIFY_SLOT, shortcutItem(XMaterial.SPYGLASS, "§e装备鉴定", "/rogue gear identify"))
            set(ACCESSORY_SLOT, shortcutItem(XMaterial.AMETHYST_SHARD, "§d饰品工坊", "/rogue accessory workshop"))
            set(NEXT_SLOT, if (safePage < maxPage) pageItem("§e下一页", "§7查看下一页战利品") else disabledPageItem("§8下一页"))

            onClick { event ->
                event.isCancelled = true
                when (event.rawSlot) {
                    PREVIOUS_SLOT -> { if (safePage > 0) open(player, safePage - 1); return@onClick }
                    NEXT_SLOT -> { if (safePage < maxPage) open(player, safePage + 1); return@onClick }
                    STORE_ALL_SLOT -> { player.sendMessage(LootStorageManager.storeAll(player)); open(player, safePage); return@onClick }
                    CLOSE_SLOT -> { player.closeInventory(); return@onClick }
                    SALVAGE_SLOT -> { player.closeInventory(); player.performCommand("rogue gear salvage"); return@onClick }
                    IDENTIFY_SLOT -> { player.closeInventory(); player.performCommand("rogue gear identify"); return@onClick }
                    ACCESSORY_SLOT -> { player.closeInventory(); player.performCommand("rogue accessory workshop"); return@onClick }
                }

                val topSize = event.view.topInventory.size
                if (event.rawSlot in storageSlots) {
                    val localIndex = storageSlots.indexOf(event.rawSlot)
                    val index = offset + localIndex
                    if (index >= items.size) {
                        val cursor = event.cursorItem ?: return@onClick
                        val message = LootStorageManager.storeItem(player, cursor)
                        player.sendMessage(message)
                        if (message.startsWith("§a")) {
                            val remaining = cursor.clone()
                            remaining.amount = remaining.amount - 1
                            event.cursorItem = if (remaining.amount > 0) remaining else null
                            open(player, safePage)
                        }
                        return@onClick
                    }
                    player.sendMessage(LootStorageManager.withdraw(player, index))
                    open(player, safePage)
                    return@onClick
                }

                if (event.rawSlot >= topSize) {
                    event.currentItem ?: return@onClick
                    val playerSlot = event.clickEvent().slot
                    player.sendMessage(LootStorageManager.storeFromInventory(player, playerSlot))
                    open(player, safePage)
                }
            }
        }
    }

    private fun toStorageItem(item: ItemStack, index: Int): ItemStack = item.clone().apply {
        itemMeta = itemMeta?.also { meta ->
            val lore = (meta.lore ?: emptyList()).toMutableList()
            lore += ""
            lore += "§7仓库槽位: §f${index + 1}"
            lore += "§e左键: 取出到背包"
            meta.lore = lore
        }
    }

    private fun infoItem(size: Int, capacity: Int, page: Int, maxPage: Int): ItemStack = XMaterial.CHEST.parseItem()!!.apply {
        itemMeta = itemMeta?.also { meta ->
            meta.setDisplayName("§b战利品仓库说明")
            meta.lore = listOf(
                "",
                "§7容量: §f$size/$capacity",
                "§7页数: §f${page + 1}/${maxPage + 1}",
                "",
                "§7可存入临时装备、未鉴定装备、锻造书、",
                "§7饰品、密封饰品和饰品刻印书。",
                "§8永久装备请放入装备仓库。"
            )
        }
    }

    private fun storeAllItem(): ItemStack = XMaterial.CHEST_MINECART.parseItem()!!.apply {
        itemMeta = itemMeta?.also { meta ->
            meta.setDisplayName("§a一键存入")
            meta.lore = listOf("", "§7扫描背包，将所有可存入战利品放入仓库。", "§8仓库满时会自动停止。")
        }
    }

    private fun shortcutItem(material: XMaterial, name: String, command: String): ItemStack = material.parseItem()!!.apply {
        itemMeta = itemMeta?.also { meta -> meta.setDisplayName(name); meta.lore = listOf("", "§7命令: §f$command", "", "§e点击打开") }
    }

    private fun pageItem(name: String, line: String): ItemStack = XMaterial.ARROW.parseItem()!!.apply {
        itemMeta = itemMeta?.also { meta -> meta.setDisplayName(name); meta.lore = listOf("", line) }
    }

    private fun disabledPageItem(name: String): ItemStack = XMaterial.GRAY_STAINED_GLASS_PANE.parseItem()!!.apply {
        itemMeta = itemMeta?.also { meta -> meta.setDisplayName(name); meta.lore = listOf("", "§8没有更多页面") }
    }

    private fun closeItem(): ItemStack = XMaterial.BARRIER.parseItem()!!.apply {
        itemMeta = itemMeta?.also { meta -> meta.setDisplayName("§c关闭"); meta.lore = listOf("", "§7关闭战利品仓库") }
    }
}
