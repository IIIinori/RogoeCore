package inori.roguecore.ui

import inori.roguecore.summary.RunEndReason
import inori.roguecore.summary.RunSummary
import inori.roguecore.summary.RunSummaryManager
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest

/**
 * 最近一次冒险结算报告。
 */
object RunSummaryUI {

    fun open(player: Player) {
        val summary = RunSummaryManager.getDisplaySummary(player)
        if (summary == null) {
            player.sendMessage("§7你还没有可查看的冒险报告。")
            return
        }

        player.openMenu<Chest>("§6§l冒险报告") {
            rows(6)
            handLocked(true)

            val contentSlots = setOf(4, 10, 12, 14, 16, 28, 30, 32, 34, 49)
            val glass = XMaterial.BLACK_STAINED_GLASS_PANE.parseItem()!!.apply {
                itemMeta = itemMeta?.also { it.setDisplayName(" ") }
            }
            for (slot in 0 until 54) {
                if (slot !in contentSlots) set(slot, glass)
            }

            set(4, overviewItem(summary))
            set(10, resultItem(summary))
            set(12, buildItem(summary))
            set(14, routeItem(summary))
            set(16, milestoneItem(summary))
            set(28, modifierItem(summary))
            set(30, roomItem(summary))
            set(32, resourceItem(summary))
            set(34, resonanceItem(summary))
            set(49, closeItem())

            onClick { event ->
                event.isCancelled = true
                if (event.rawSlot == 49) player.closeInventory()
            }
        }
    }

    private fun overviewItem(summary: RunSummary): ItemStack {
        return XMaterial.NETHER_STAR.parseItem()!!.apply {
            itemMeta = itemMeta?.also { meta ->
                meta.setDisplayName("§6冒险总览")
                meta.lore = listOf(
                    "",
                    "§7结果: ${resultColor(summary.result)}${summary.result.displayName}",
                    "§7最高楼层: §e${summary.highestFloor}",
                    "§7持续时间: §f${formatDuration(summary.durationSeconds)}",
                    "§7结算灵魂碎片: §6${summary.settledSoulShards}",
                    "§7本局碎片峰值: §e${summary.peakRunShards}"
                )
            }
        }
    }

    private fun resultItem(summary: RunSummary): ItemStack {
        val icon = when (summary.result) {
            RunEndReason.CLEAR -> XMaterial.DRAGON_EGG
            RunEndReason.DEATH -> XMaterial.SKELETON_SKULL
            RunEndReason.EXTRACT -> XMaterial.ENDER_PEARL
            RunEndReason.LEAVE -> XMaterial.OAK_DOOR
            RunEndReason.ONGOING -> XMaterial.COMPASS
        }
        return icon.parseItem()!!.apply {
            itemMeta = itemMeta?.also { meta ->
                meta.setDisplayName("§e基础结果")
                meta.lore = listOf(
                    "",
                    "§7开始楼层: §f${summary.startFloor}",
                    "§7最高楼层: §f${summary.highestFloor}",
                    "§7清理房间: §a${summary.roomsCleared}",
                    "§7精英房: §d${summary.eliteRoomsCleared}",
                    "§7Boss 房: §c${summary.bossRoomsCleared}"
                )
            }
        }
    }

    private fun buildItem(summary: RunSummary): ItemStack {
        return XMaterial.ENCHANTED_BOOK.parseItem()!!.apply {
            itemMeta = itemMeta?.also { meta ->
                meta.setDisplayName("§d构筑摘要")
                meta.lore = listOf(
                    "",
                    "§7神恩数量: §d${summary.boonCount}",
                    "§7遗物数量: §5${summary.relicCount}",
                    "§7诅咒数量: §c${summary.curseCount}",
                    "§7离场时临时修正: §b${summary.modifierCount}",
                    "§7已激活共鸣: §e${summary.resonanceLines.size}"
                )
            }
        }
    }

    private fun routeItem(summary: RunSummary): ItemStack {
        return XMaterial.COMPASS.parseItem()!!.apply {
            itemMeta = itemMeta?.also { meta ->
                meta.setDisplayName("§b路线记录")
                meta.lore = buildList {
                    add("")
                    if (summary.routeHistory.isEmpty()) {
                        add("§8没有选择过下一层路线")
                    } else {
                        summary.routeHistory.take(12).forEachIndexed { index, route ->
                            add("§7${index + 1}. §f$route")
                        }
                        if (summary.routeHistory.size > 12) add("§7... 还有 ${summary.routeHistory.size - 12} 条")
                    }
                }
            }
        }
    }

    private fun milestoneItem(summary: RunSummary): ItemStack {
        return XMaterial.TOTEM_OF_UNDYING.parseItem()!!.apply {
            itemMeta = itemMeta?.also { meta ->
                meta.setDisplayName("§6里程碑")
                meta.lore = buildList {
                    add("")
                    add("§7达成数量: §e${summary.milestoneNames.size}")
                    if (summary.milestoneNames.isEmpty()) {
                        add("§8本局没有达成里程碑")
                    } else {
                        summary.milestoneNames.take(10).forEach { add("§a- $it") }
                        if (summary.milestoneNames.size > 10) add("§7... 还有 ${summary.milestoneNames.size - 10} 个")
                    }
                }
            }
        }
    }

    private fun modifierItem(summary: RunSummary): ItemStack {
        return XMaterial.BEACON.parseItem()!!.apply {
            itemMeta = itemMeta?.also { meta ->
                meta.setDisplayName("§b事件链")
                meta.lore = buildList {
                    add("")
                    if (summary.modifierCounts.isEmpty()) {
                        add("§8没有触发本局临时修正")
                    } else {
                        summary.modifierCounts.entries.forEach { (name, count) ->
                            add("§7$name §fx$count")
                        }
                    }
                }
            }
        }
    }

    private fun roomItem(summary: RunSummary): ItemStack {
        return XMaterial.IRON_SWORD.parseItem()!!.apply {
            itemMeta = itemMeta?.also { meta ->
                meta.setDisplayName("§c房间表现")
                meta.lore = listOf(
                    "",
                    "§7总清理: §a${summary.roomsCleared}",
                    "§7普通战斗: §f${summary.combatRoomsCleared}",
                    "§7精英: §d${summary.eliteRoomsCleared}",
                    "§7Boss: §c${summary.bossRoomsCleared}"
                )
            }
        }
    }

    private fun resourceItem(summary: RunSummary): ItemStack {
        return XMaterial.GOLD_INGOT.parseItem()!!.apply {
            itemMeta = itemMeta?.also { meta ->
                meta.setDisplayName("§e资源表现")
                meta.lore = listOf(
                    "",
                    "§7本局碎片峰值: §e${summary.peakRunShards}",
                    "§7结算灵魂碎片: §6${summary.settledSoulShards}",
                    "§7持续时间: §f${formatDuration(summary.durationSeconds)}"
                )
            }
        }
    }

    private fun resonanceItem(summary: RunSummary): ItemStack {
        return XMaterial.AMETHYST_SHARD.parseItem()!!.apply {
            itemMeta = itemMeta?.also { meta ->
                meta.setDisplayName("§d流派共鸣")
                meta.lore = buildList {
                    add("")
                    if (summary.resonanceLines.isEmpty()) {
                        add("§8没有激活流派共鸣")
                    } else {
                        summary.resonanceLines.take(10).forEach { add(it) }
                    }
                }
            }
        }
    }

    private fun closeItem(): ItemStack {
        return XMaterial.BARRIER.parseItem()!!.apply {
            itemMeta = itemMeta?.also { meta ->
                meta.setDisplayName("§c关闭")
                meta.lore = listOf("", "§7点击关闭界面")
            }
        }
    }

    private fun resultColor(reason: RunEndReason): String {
        return when (reason) {
            RunEndReason.CLEAR -> "§a"
            RunEndReason.DEATH -> "§c"
            RunEndReason.EXTRACT -> "§b"
            RunEndReason.LEAVE -> "§e"
            RunEndReason.ONGOING -> "§7"
        }
    }

    private fun formatDuration(seconds: Long): String {
        val minutes = seconds / 60
        val remain = seconds % 60
        return if (minutes > 0) "${minutes}分${remain}秒" else "${remain}秒"
    }
}
