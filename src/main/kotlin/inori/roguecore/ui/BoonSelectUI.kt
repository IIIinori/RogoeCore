package inori.roguecore.ui

import inori.roguecore.boon.Boon
import inori.roguecore.boon.PlayerBoonData
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest

/**
 * Boon 选择界面。
 */
object BoonSelectUI {

    /**
     * 打开 Boon 选择界面
     * @param player 目标玩家
     * @param boons 可选的 Boon 列表（最多 4 个）
     */
    fun open(player: Player, boons: List<Boon>) {
        if (boons.isEmpty()) return

        val title = "§6§l选择神恩"
        DungeonGuiGuard.lock(player, title) { target -> open(target, boons) }

        player.openMenu<Chest>(title) {
            rows(3)
            handLocked(true)

            // Boon 选项放在中间行
            val slots = when (boons.size) {
                1 -> listOf(13)
                2 -> listOf(11, 15)
                3 -> listOf(11, 13, 15)
                else -> listOf(10, 12, 14, 16)
            }

            // 用玻璃板填充背景
            val glass = XMaterial.BLACK_STAINED_GLASS_PANE.parseItem()!!.apply {
                itemMeta = itemMeta?.also { it.setDisplayName(" ") }
            }
            for (i in 0 until 27) {
                if (i !in slots) {
                    set(i, glass)
                }
            }

            for ((index, slot) in slots.withIndex()) {
                if (index >= boons.size) break
                val boon = boons[index]
                val currentInstance = PlayerBoonData.getBoons(player).firstOrNull { it.boon.id == boon.id }
                val currentLevel = currentInstance?.level ?: 0
                val nextLevel = currentLevel + 1

                set(slot, buildBoonItem(boon, currentLevel, nextLevel))
            }

            // 点击事件
            onClick { event ->
                event.isCancelled = true
                val clickedSlot = event.rawSlot

                val slotList = when (boons.size) {
                    1 -> listOf(13)
                    2 -> listOf(11, 15)
                    3 -> listOf(11, 13, 15)
                    else -> listOf(10, 12, 14, 16)
                }

                val boonIndex = slotList.indexOf(clickedSlot)
                if (boonIndex < 0 || boonIndex >= boons.size) return@onClick

                val selectedBoon = boons[boonIndex]
                DungeonGuiGuard.unlock(player)
                player.closeInventory()
                PlayerBoonData.addBoon(player, selectedBoon)
            }
        }
    }

    /**
     * 构建 Boon 展示物品
     */
    private fun buildBoonItem(boon: Boon, currentLevel: Int, nextLevel: Int): ItemStack {
        val item = boon.icon.parseItem() ?: XMaterial.PAPER.parseItem()!!
        val meta = item.itemMeta ?: return item

        val rarityTag = "${boon.rarity.color}[${boon.rarity.displayName}]"
        val levelTag = if (currentLevel > 0) " §eLv.$currentLevel → Lv.$nextLevel" else " §eLv.1"

        meta.setDisplayName("$rarityTag §f${boon.name}$levelTag")

        val lore = mutableListOf<String>()
        lore.add("")
        lore.addAll(boon.getPreviewLore(nextLevel))

        lore.add("")
        if (currentLevel > 0) {
            lore.add("§e点击升级")
        } else {
            lore.add("§e点击选择")
        }

        meta.lore = lore
        item.itemMeta = meta
        return item
    }
}
