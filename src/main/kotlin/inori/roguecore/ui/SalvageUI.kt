package inori.roguecore.ui

import inori.roguecore.salvage.SalvageManager
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest

object SalvageUI {

    private val itemSlots = (0 until 45).toList()
    private const val PREVIOUS_SLOT = 45
    private const val AUTO_SLOT = 47
    private const val INFO_SLOT = 49
    private const val CLOSE_SLOT = 50
    private const val NEXT_SLOT = 53
    private const val PAGE_SIZE = 45

    fun open(player: Player, page: Int = 0) {
        val previews = SalvageManager.scanInventory(player)
        val maxPage = ((previews.size - 1) / PAGE_SIZE).coerceAtLeast(0)
        val safePage = page.coerceIn(0, maxPage)
        val offset = safePage * PAGE_SIZE
        val visible = previews.drop(offset).take(PAGE_SIZE)
        val autoCount = previews.count { it.autoEligible && !it.blocked }

        player.openMenu<Chest>("§6§l回收工坊 §7(${previews.size} 件 第 ${safePage + 1}/${maxPage + 1} 页)") {
            rows(6)
            handLocked(true)

            val controlSlots = setOf(PREVIOUS_SLOT, AUTO_SLOT, INFO_SLOT, CLOSE_SLOT, NEXT_SLOT)
            val glass = XMaterial.GRAY_STAINED_GLASS_PANE.parseItem()!!.apply {
                itemMeta = itemMeta?.also { it.setDisplayName(" ") }
            }
            for (slot in 45 until 54) {
                if (slot !in controlSlots) set(slot, glass)
            }

            for ((index, preview) in visible.withIndex()) {
                set(itemSlots[index], previewItem(preview))
            }
            set(PREVIOUS_SLOT, if (safePage > 0) pageItem("§e上一页") else disabledItem("§8上一页"))
            set(AUTO_SLOT, autoItem(autoCount))
            set(INFO_SLOT, infoItem(previews.size, autoCount))
            set(CLOSE_SLOT, closeItem())
            set(NEXT_SLOT, if (safePage < maxPage) pageItem("§e下一页") else disabledItem("§8下一页"))

            onClick { event ->
                event.isCancelled = true
                when (event.rawSlot) {
                    PREVIOUS_SLOT -> if (safePage > 0) open(player, safePage - 1)
                    NEXT_SLOT -> if (safePage < maxPage) open(player, safePage + 1)
                    AUTO_SLOT -> {
                        val result = SalvageManager.salvageAuto(player)
                        player.sendMessage(result.message)
                        open(player, safePage)
                    }
                    INFO_SLOT -> Unit
                    CLOSE_SLOT -> player.closeInventory()
                    in itemSlots -> {
                        val localIndex = itemSlots.indexOf(event.rawSlot)
                        val preview = visible.getOrNull(localIndex) ?: return@onClick
                        val result = if (event.clickEvent().click == ClickType.SHIFT_LEFT) {
                            SalvageManager.salvageSameCategory(player, preview.category)
                        } else {
                            SalvageManager.salvageSlot(player, preview.inventorySlot)
                        }
                        player.sendMessage(result.message)
                        open(player, safePage)
                    }
                }
            }
        }
    }

    private fun previewItem(preview: SalvageManager.SalvagePreview): ItemStack {
        return preview.item.clone().apply {
            itemMeta = itemMeta?.also { meta ->
                val lore = (meta.lore ?: emptyList()).toMutableList()
                lore += ""
                lore += "§7分类: §f${preview.category.displayName}"
                lore += "§7背包槽位: §f${preview.inventorySlot}"
                lore += "§7回收奖励: ${SalvageManager.formatRewards(preview.rewards)}"
                if (preview.blocked) {
                    lore += "§c不可分解: ${preview.blockedReason.removePrefix("§c")}"
                } else {
                    lore += "§e左键: 分解当前物品"
                    lore += "§eShift+左键: 分解所有同分类物品"
                    if (preview.autoEligible) lore += "§a可被一键低价值回收处理"
                }
                meta.lore = lore
            }
        }
    }

    private fun autoItem(count: Int): ItemStack {
        val enabled = count > 0
        return (if (enabled) XMaterial.HOPPER else XMaterial.GRAY_STAINED_GLASS_PANE).parseItem()!!.apply {
            itemMeta = itemMeta?.also { meta ->
                meta.setDisplayName(if (enabled) "§a一键分解低价值" else "§8一键分解低价值")
                meta.lore = listOf(
                    "",
                    "§7会分解临时装备、未鉴定装备、密封饰品",
                    "§7以及普通/稀有饰品、低品质书类。",
                    "§7不会分解收藏永久装备或高品质书。",
                    "",
                    "§7当前可处理: §a$count",
                    if (enabled) "§e点击执行" else "§8暂无可处理物品"
                )
            }
        }
    }

    private fun infoItem(total: Int, autoCount: Int): ItemStack = XMaterial.BOOK.parseItem()!!.apply {
        itemMeta = itemMeta?.also { meta ->
            meta.setDisplayName("§6回收说明")
            meta.lore = listOf(
                "",
                "§7可分解物品: §f$total",
                "§7低价值一键数量: §a$autoCount",
                "",
                "§7支持: 临时装备、永久装备、未鉴定装备、锻造书、饰品、密封饰品、饰品刻印书。",
                "§7永久装备需要手动分解，收藏或非本人绑定不可分解。",
                "§7奖励会发放为本局碎片、灵魂碎片或局外材料。"
            )
        }
    }

    private fun pageItem(name: String): ItemStack = XMaterial.ARROW.parseItem()!!.apply {
        itemMeta = itemMeta?.also { meta ->
            meta.setDisplayName(name)
            meta.lore = listOf("", "§e点击翻页")
        }
    }

    private fun disabledItem(name: String): ItemStack = XMaterial.GRAY_STAINED_GLASS_PANE.parseItem()!!.apply {
        itemMeta = itemMeta?.also { meta ->
            meta.setDisplayName(name)
            meta.lore = listOf("", "§8没有更多页面")
        }
    }

    private fun closeItem(): ItemStack = XMaterial.BARRIER.parseItem()!!.apply {
        itemMeta = itemMeta?.also { meta ->
            meta.setDisplayName("§c关闭")
            meta.lore = listOf("", "§7关闭回收工坊")
        }
    }
}
