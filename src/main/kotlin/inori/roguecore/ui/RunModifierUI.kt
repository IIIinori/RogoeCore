package inori.roguecore.ui

import inori.roguecore.dungeon.DungeonManager
import inori.roguecore.modifier.RunModifier
import inori.roguecore.modifier.RunModifierManager
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest

/**
 * 本局临时修正面板。
 */
object RunModifierUI {

        private val modifierSlots = listOf(
10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25)

    fun open(player: Player) {
        val modifiers = RunModifierManager.getModifiers(player)
        val inDungeon = DungeonManager.isInDungeon(player)

        player.openMenu<Chest>("§b§l本局临时修正") {
            rows(4)
            handLocked(true)

            val contentSlots = modifierSlots.toSet() + setOf(4, 31)
            val glass = XMaterial.CYAN_STAINED_GLASS_PANE.parseItem()!!.apply {
                itemMeta = itemMeta?.also { it.setDisplayName(" ") }
            }
            for (slot in 0 until 36) {
                if (slot !in contentSlots) set(slot, glass)
            }

            set(4, buildSummaryItem(modifiers, inDungeon))
            modifiers.take(modifierSlots.size).forEachIndexed { index, modifier ->
                val slot: Int = modifierSlots[index]
                set(slot, buildModifierItem(modifier))
            }
            set(31, XMaterial.BARRIER.parseItem()!!.apply {
                itemMeta = itemMeta?.also { meta ->
                    meta.setDisplayName("§c关闭")
                    meta.lore = listOf("", "§7点击关闭界面")
                }
            })

            onClick { event ->
                event.isCancelled = true
                if (event.rawSlot == 31) player.closeInventory()
            }
        }
    }

    private fun buildSummaryItem(modifiers: List<RunModifier>, inDungeon: Boolean): ItemStack {
        return XMaterial.BEACON.parseItem()!!.apply {
            itemMeta = itemMeta?.also { meta ->
                meta.setDisplayName("§b修正总览")
                meta.lore = listOf(
                    "",
                    if (inDungeon) "§a当前正在冒险中" else "§7当前不在副本中",
                    "§7激活修正: §b${modifiers.size}",
                    "",
                    "§7临时修正来自事件房选择，",
                    "§7会影响后续房间、事件或锻造。"
                )
            }
        }
    }

    private fun buildModifierItem(modifier: RunModifier): ItemStack {
        val item = modifier.type.icon.parseItem() ?: XMaterial.PAPER.parseItem()!!
        return item.apply {
            itemMeta = itemMeta?.also { meta ->
                meta.setDisplayName((if (modifier.type.positive) "§a" else "§c") + modifier.type.displayName)
                meta.lore = buildList {
                    add("")
                    add("§7来源: §f${modifier.source}")
                    add("§7效果: §f${modifier.type.description}")
                    add("§7数值: §e${formatValue(modifier.value)}")
                    if (modifier.remainingRooms > 0) add("§7剩余房间: §b${modifier.remainingRooms}")
                    if (modifier.charges > 0) add("§7剩余触发: §d${modifier.charges}")
                }
            }
        }
    }

    private fun formatValue(value: Double): String {
        return if (value == value.toInt().toDouble()) value.toInt().toString() else String.format("%.2f", value)
    }
}
