package inori.roguecore.ui

import inori.roguecore.accessory.AccessoryIdentificationTaskManager
import inori.roguecore.accessory.AccessoryInscriptionTaskManager
import inori.roguecore.data.PlayerDataManager
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest

object AccessoryWorkshopUI {

    private const val IDENTIFY_SLOT = 11
    private const val INSCRIBE_SLOT = 15
    private const val ACCESSORY_SLOT = 31
    private const val BACK_SLOT = 40

    fun open(player: Player) {
        val shards = PlayerDataManager.get(player.uniqueId).soulShards
        val identifyDone = AccessoryIdentificationTaskManager.getCompletedCount(player.uniqueId)
        val inscribeDone = AccessoryInscriptionTaskManager.getCompletedCount(player.uniqueId)

        player.openMenu<Chest>("§d§l饰品工坊") {
            rows(5)
            handLocked(true)

            val buttons = setOf(4, IDENTIFY_SLOT, INSCRIBE_SLOT, ACCESSORY_SLOT, BACK_SLOT)
            val glass = XMaterial.PURPLE_STAINED_GLASS_PANE.parseItem()!!.apply {
                itemMeta = itemMeta?.also { it.setDisplayName(" ") }
            }
            for (slot in 0 until 45) {
                if (slot !in buttons) set(slot, glass)
            }

            set(4, infoItem(shards))
            set(IDENTIFY_SLOT, button(
                XMaterial.SPYGLASS,
                if (identifyDone > 0) "§a饰品鉴定 §7($identifyDone)" else "§d饰品鉴定",
                listOf(
                    "§7处理副本里获得的密封饰品",
                    "§7鉴定后生成可装备饰品",
                    "§7已完成待领取: §a$identifyDone",
                    "",
                    "§e点击打开"
                )
            ))
            set(INSCRIBE_SLOT, button(
                XMaterial.WRITABLE_BOOK,
                if (inscribeDone > 0) "§a饰品刻印 §7($inscribeDone)" else "§b饰品刻印",
                listOf(
                    "§7使用饰品刻印书制造指定饰品",
                    "§7刻印品质会提高升品幸运和数值倍率",
                    "§7已完成待领取: §a$inscribeDone",
                    "",
                    "§e点击打开"
                )
            ))
            set(ACCESSORY_SLOT, button(
                XMaterial.AMETHYST_SHARD,
                "§d饰品匣",
                listOf("§7管理已经获得的饰品", "§7放入虚拟槽后才会生效", "", "§e点击打开")
            ))
            set(BACK_SLOT, button(XMaterial.ARROW, "§e返回主菜单", listOf("§7返回 RogueCore 主菜单")))

            onClick { event ->
                event.isCancelled = true
                when (event.rawSlot) {
                    IDENTIFY_SLOT -> AccessoryIdentifyUI.open(player)
                    INSCRIBE_SLOT -> AccessoryInscriptionUI.open(player)
                    ACCESSORY_SLOT -> AccessoryUI.open(player)
                    BACK_SLOT -> RogueMenuUI.open(player)
                }
            }
        }
    }

    private fun infoItem(shards: Int): ItemStack = XMaterial.CRAFTING_TABLE.parseItem()!!.apply {
        itemMeta = itemMeta?.also { meta ->
            meta.setDisplayName("§d饰品工坊说明")
            meta.lore = listOf(
                "",
                "§7密封饰品需要在这里鉴定。",
                "§7饰品刻印书需要在这里刻印。",
                "§7完成后获得真实饰品物品。",
                "§7当前灵魂碎片: §e$shards",
                "",
                "§8已提交任务可离线计时。"
            )
        }
    }

    private fun button(material: XMaterial, name: String, lore: List<String>): ItemStack {
        return material.parseItem()!!.apply {
            itemMeta = itemMeta?.also { meta ->
                meta.setDisplayName(name)
                meta.lore = listOf("") + lore
            }
        }
    }
}
