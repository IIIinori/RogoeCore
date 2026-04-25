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
import kotlin.math.ceil

/**
 * 天赋树 GUI
 */
object TalentUI {

    private val talentSlots = listOf(
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34
    )
    private const val PREVIOUS_PAGE_SLOT = 45
    private const val SUMMARY_SLOT = 49
    private const val NEXT_PAGE_SLOT = 53

    fun open(player: Player, page: Int = 0) {
        val talents = TalentRegistry.getAll().toList()
        val playerData = PlayerDataManager.get(player.uniqueId)
        val totalPages = ceil(talents.size / talentSlots.size.toDouble()).toInt().coerceAtLeast(1)
        val safePage = page.coerceIn(0, totalPages - 1)
        val fromIndex = safePage * talentSlots.size
        val pageTalents = talents.drop(fromIndex).take(talentSlots.size)
        val investedLevels = TalentManager.getPlayerTalents(player.uniqueId).values.sum()

        player.openMenu<Chest>("§6§l天赋树 §7(${safePage + 1}/$totalPages · 碎片: §e${playerData.soulShards}§7)") {
            rows(6)
            handLocked(true)

            val interactiveSlots = talentSlots.toMutableSet().also {
                it += PREVIOUS_PAGE_SLOT
                it += SUMMARY_SLOT
                it += NEXT_PAGE_SLOT
            }

            val glass = XMaterial.GRAY_STAINED_GLASS_PANE.parseItem()!!.apply {
                itemMeta = itemMeta?.also { it.setDisplayName(" ") }
            }
            for (slot in 0 until 54) {
                if (slot !in interactiveSlots) {
                    set(slot, glass)
                }
            }

            set(SUMMARY_SLOT, buildSummaryItem(playerData.soulShards, investedLevels, talents.size, safePage, totalPages))
            set(PREVIOUS_PAGE_SLOT, buildPageButton("§e上一页", safePage > 0, "§7查看前一页天赋"))
            set(NEXT_PAGE_SLOT, buildPageButton("§e下一页", safePage < totalPages - 1, "§7查看后一页天赋"))

            for ((index, slot) in talentSlots.withIndex()) {
                val talent = pageTalents.getOrNull(index) ?: continue
                val level = TalentManager.getTalentLevel(player.uniqueId, talent.id)
                set(slot, buildTalentItem(talent, level))
            }

            onClick { event ->
                event.isCancelled = true
                when (event.rawSlot) {
                    PREVIOUS_PAGE_SLOT -> {
                        if (safePage > 0) {
                            open(player, safePage - 1)
                        }
                        return@onClick
                    }
                    NEXT_PAGE_SLOT -> {
                        if (safePage < totalPages - 1) {
                            open(player, safePage + 1)
                        }
                        return@onClick
                    }
                }

                val talentIndex = talentSlots.indexOf(event.rawSlot)
                if (talentIndex < 0 || talentIndex >= pageTalents.size) return@onClick

                val talent = pageTalents[talentIndex]
                val success = TalentManager.upgradeTalent(player, talent.id)
                if (success) {
                    open(player, safePage)
                }
            }
        }
    }

    private fun buildSummaryItem(shards: Int, investedLevels: Int, totalTalents: Int, page: Int, totalPages: Int): ItemStack {
        return XMaterial.NETHER_STAR.parseItem()!!.apply {
            itemMeta = itemMeta?.also { meta ->
                meta.setDisplayName("§6天赋总览")
                meta.lore = listOf(
                    "",
                    "§7当前灵魂碎片: §e$shards",
                    "§7已投入天赋等级: §a$investedLevels",
                    "§7天赋数量: §f$totalTalents",
                    "§7当前页: §f${page + 1}/$totalPages",
                    "",
                    "§8点击各天赋图标进行升级"
                )
            }
        }
    }

    private fun buildPageButton(name: String, enabled: Boolean, description: String): ItemStack {
        val material = if (enabled) XMaterial.ARROW else XMaterial.BARRIER
        return material.parseItem()!!.apply {
            itemMeta = itemMeta?.also { meta ->
                meta.setDisplayName(if (enabled) name else "§8$name")
                meta.lore = listOf("", if (enabled) description else "§8没有更多页面")
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
        lore.add("§8ID: ${talent.id}")

        if (currentLevel > 0) {
            val value = talent.getValue(currentLevel)
            val desc = talent.description.replace("{value}", formatValue(value))
            lore.add("§7当前: §a$desc")
        } else {
            lore.add("§8当前未投入天赋点")
        }

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
