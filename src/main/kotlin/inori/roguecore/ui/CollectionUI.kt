package inori.roguecore.ui

import inori.roguecore.collection.CollectionManager
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest

object CollectionUI {

    private val gearSlots = listOf(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33)
    private val accessorySlots = listOf(37, 38, 39, 40, 41)
    private const val BOSS_SLOT = 43
    private const val INFO_SLOT = 49
    private const val CLOSE_SLOT = 50

    fun open(player: Player) {
        val progress = CollectionManager.getProgress(player)
        player.openMenu<Chest>("§5§l收藏馆") {
            rows(6)
            handLocked(true)

            val buttons = (gearSlots + accessorySlots + listOf(BOSS_SLOT, INFO_SLOT, CLOSE_SLOT)).toSet()
            val glass = XMaterial.GRAY_STAINED_GLASS_PANE.parseItem()!!.apply { itemMeta = itemMeta?.also { it.setDisplayName(" ") } }
            for (slot in 0 until 54) if (slot !in buttons) set(slot, glass)

            for ((index, theme) in CollectionManager.getGearThemes().withIndex()) {
                set(gearSlots[index], gearItem(player, theme))
            }
            for ((index, slot) in CollectionManager.getAccessorySlots().withIndex()) {
                set(accessorySlots[index], accessoryItem(player, slot))
            }
            set(BOSS_SLOT, bossItem(progress.bossCollected, progress.bossTotal))
            set(INFO_SLOT, infoItem(progress))
            set(CLOSE_SLOT, closeItem())

            onClick { event ->
                event.isCancelled = true
                if (event.rawSlot == CLOSE_SLOT) {
                    player.closeInventory()
                    return@onClick
                }
                val topSize = event.view.topInventory.size
                if (event.rawSlot >= topSize) {
                    val slot = event.rawSlot - topSize
                    val result = CollectionManager.submitFromInventory(player, slot)
                    player.sendMessage(result.message)
                    if (result.success) open(player)
                }
            }
        }
    }

    private fun gearItem(player: Player, theme: String): ItemStack {
        val done = CollectionManager.isGearCollected(player, theme)
        return (if (done) XMaterial.ENCHANTED_BOOK else XMaterial.BOOK).parseItem()!!.apply {
            itemMeta = itemMeta?.also { meta ->
                meta.setDisplayName(if (done) "§a${CollectionManager.getThemeName(theme)} 装备" else "§7${CollectionManager.getThemeName(theme)} 装备")
                meta.lore = listOf(
                    "",
                    "§7要求: §d提交一件史诗及以上该主题装备",
                    "§7状态: ${if (done) "§a已点亮" else "§c未点亮"}",
                    "",
                    "§8在下方背包点击符合要求的装备可提交"
                )
            }
        }
    }

    private fun accessoryItem(player: Player, slot: inori.roguecore.accessory.AccessorySlot): ItemStack {
        val done = CollectionManager.isAccessoryCollected(player, slot)
        return (if (done) XMaterial.NETHER_STAR else XMaterial.AMETHYST_SHARD).parseItem()!!.apply {
            itemMeta = itemMeta?.also { meta ->
                meta.setDisplayName(if (done) "§a${slot.displayName} 收藏" else "§d${slot.displayName} 收藏")
                meta.lore = listOf(
                    "",
                    "§7要求: §6提交一件传说 ${slot.displayName} 饰品",
                    "§7状态: ${if (done) "§a已点亮" else "§c未点亮"}",
                    "",
                    "§8在下方背包点击符合要求的饰品可提交"
                )
            }
        }
    }

    private fun bossItem(done: Int, total: Int): ItemStack = XMaterial.WITHER_SKELETON_SKULL.parseItem()!!.apply {
        itemMeta = itemMeta?.also { meta ->
            meta.setDisplayName("§5Boss 首杀收藏")
            meta.lore = listOf(
                "",
                "§7进度: §e$done§7/§f$total",
                progressBar(done, total),
                "",
                "§7Boss 房通关时自动点亮对应楼层首杀。",
                "§7每个 Boss 首杀只奖励一次。"
            )
        }
    }

    private fun infoItem(progress: CollectionManager.Progress): ItemStack = XMaterial.WRITABLE_BOOK.parseItem()!!.apply {
        itemMeta = itemMeta?.also { meta ->
            meta.setDisplayName("§6收藏馆说明")
            meta.lore = listOf(
                "",
                "§7装备主题: §e${progress.gearCollected}§7/§f${progress.gearTotal}",
                "§7饰品槽位: §d${progress.accessoryCollected}§7/§f${progress.accessoryTotal}",
                "§7Boss 首杀: §5${progress.bossCollected}§7/§f${progress.bossTotal}",
                "",
                "§e提交方式:",
                "§7打开收藏馆后，点击下方背包里的符合要求物品。",
                "§c提交会消耗该物品。"
            )
        }
    }

    private fun closeItem(): ItemStack = XMaterial.BARRIER.parseItem()!!.apply {
        itemMeta = itemMeta?.also { meta ->
            meta.setDisplayName("§c关闭")
            meta.lore = listOf("", "§7关闭收藏馆")
        }
    }

    private fun progressBar(done: Int, total: Int): String {
        val filled = if (total <= 0) 0 else ((done.toDouble() / total) * 20).toInt().coerceIn(0, 20)
        return "§8[" + "§a" + "|".repeat(filled) + "§7" + "|".repeat(20 - filled) + "§8]"
    }
}
