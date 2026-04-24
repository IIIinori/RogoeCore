package inori.roguecore.ui

import inori.roguecore.data.PlayerDataManager
import inori.roguecore.talent.Talent
import inori.roguecore.talent.TalentManager
import inori.roguecore.talent.TalentRegistry
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest

/**
 * 天赋树 GUI
 */
object TalentUI {

    fun open(player: Player) {
        val talents = TalentRegistry.getAll().toList()
        val playerData = PlayerDataManager.get(player.uniqueId)

        player.openMenu<Chest>("§6§l天赋树 §7(碎片: §e${playerData.soulShards}§7)") {
            rows(3)
            handLocked(true)

            val slots = listOf(10, 11, 12, 13, 14, 15, 16)

            // 背景
            val glass = XMaterial.GRAY_STAINED_GLASS_PANE.parseItem()!!.apply {
                itemMeta = itemMeta?.also { it.setDisplayName(" ") }
            }
            for (i in 0 until 27) {
                if (i !in slots) {
                    set(i, glass)
                }
            }

            // 天赋放在中间行
            for ((index, slot) in slots.withIndex()) {
                if (index >= talents.size) break
                val talent = talents[index]
                val level = TalentManager.getTalentLevel(player.uniqueId, talent.id)
                set(slot, buildTalentItem(talent, level))
            }

            onClick { event ->
                event.isCancelled = true
                val clickedSlot = event.rawSlot

                val slotList = listOf(10, 11, 12, 13, 14, 15, 16)
                val talentIndex = slotList.indexOf(clickedSlot)
                if (talentIndex < 0 || talentIndex >= talents.size) return@onClick

                val talent = talents[talentIndex]
                val success = TalentManager.upgradeTalent(player, talent.id)
                if (success) {
                    // 刷新界面
                    player.closeInventory()
                    open(player)
                }
            }
        }
    }

    private fun buildTalentItem(talent: Talent, currentLevel: Int): ItemStack {
        val maxed = currentLevel >= talent.maxLevel
        val item = if (maxed) {
            XMaterial.ENCHANTED_GOLDEN_APPLE.parseItem()!!
        } else {
            talent.icon.parseItem() ?: XMaterial.PAPER.parseItem()!!
        }
        val meta = item.itemMeta ?: return item

        val levelColor = if (maxed) "§a" else "§e"
        meta.setDisplayName("§f${talent.name} $levelColor[Lv.$currentLevel/${talent.maxLevel}]")

        val lore = mutableListOf<String>()
        lore.add("")

        // 当前效果
        if (currentLevel > 0) {
            val value = talent.getValue(currentLevel)
            val desc = talent.description.replace("{value}", formatValue(value))
            lore.add("§7当前: §a$desc")
        }

        // 下一级
        if (!maxed) {
            val nextValue = talent.getValue(currentLevel + 1)
            val nextDesc = talent.description.replace("{value}", formatValue(nextValue))
            val cost = talent.getCost(currentLevel + 1)
            lore.add("§7下一级: §e$nextDesc")
            lore.add("")
            lore.add("§7消耗: §e$cost §7灵魂碎片")
            lore.add("§e点击升级")
        } else {
            lore.add("")
            lore.add("§a已满级")
        }

        meta.lore = lore
        item.itemMeta = meta
        return item
    }

    private fun formatValue(value: Double): String {
        return if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            String.format("%.1f", value)
        }
    }
}
