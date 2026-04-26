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

            val contentSlots = setOf(4, 10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 37, 38, 39, 40, 41, 49)
            val glass = XMaterial.BLACK_STAINED_GLASS_PANE.parseItem()!!.apply {
                itemMeta = itemMeta?.also { it.setDisplayName(" ") }
            }
            for (slot in 0 until 54) {
                if (slot !in contentSlots) set(slot, glass)
            }

            set(4, overviewItem(summary))
            set(10, resultItem(summary))
            set(11, resourceItem(summary))
            set(12, lootItem(summary))
            set(13, salvageItem(summary))
            set(14, collectionItem(summary))
            set(15, buildItem(summary))
            set(16, roomItem(summary))
            set(19, routeItem(summary))
            set(20, milestoneItem(summary))
            set(21, modifierItem(summary))
            set(22, resonanceItem(summary))
            set(23, nextStepItem(summary))
            set(37, shortcutItem(XMaterial.HOPPER, "§6回收工坊", "/rogue gear storage salvage"))
            set(38, shortcutItem(XMaterial.SPYGLASS, "§e装备鉴定", "/rogue gear storage identify"))
            set(39, shortcutItem(XMaterial.CRAFTING_TABLE, "§d饰品工坊", "/rogue accessory workshop"))
            set(40, shortcutItem(XMaterial.LECTERN, "§5收藏馆", "/rogue progress collection"))
            set(41, shortcutItem(XMaterial.ENCHANTED_BOOK, "§d构筑详情", "/rogue run build"))
            set(49, closeItem())

            onClick { event ->
                event.isCancelled = true
                val command = when (event.rawSlot) {
                    37 -> "rogue salvage"
                    38 -> "rogue identify"
                    39 -> "rogue aworkshop"
                    40 -> "rogue collection"
                    41 -> "rogue build"
                    else -> null
                }
                if (command != null) {
                    player.closeInventory()
                    player.performCommand(command)
                    return@onClick
                }
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

    private fun lootItem(summary: RunSummary): ItemStack {
        return XMaterial.CHEST.parseItem()!!.apply {
            itemMeta = itemMeta?.also { meta ->
                meta.setDisplayName("§b掉落统计")
                meta.lore = buildList {
                    add("")
                    lootKeys().forEach { key -> add("§7${lootLabel(key)}: §f${summary.lootCounts[key] ?: 0}") }
                }
            }
        }
    }

    private fun salvageItem(summary: RunSummary): ItemStack {
        return XMaterial.HOPPER.parseItem()!!.apply {
            itemMeta = itemMeta?.also { meta ->
                meta.setDisplayName("§6回收统计")
                meta.lore = buildList {
                    add("")
                    add("§7回收件数: §e${summary.salvagedCount}")
                    add("§7回收本局碎片: §6${summary.salvagedRunShards}")
                    add("§7回收灵魂碎片: §e${summary.salvagedSoulShards}")
                    if (summary.salvagedMaterials.isEmpty()) {
                        add("§8没有记录回收材料")
                    } else {
                        add("§7回收材料:")
                        summary.salvagedMaterials.entries.sortedBy { it.key }.take(6).forEach { (id, amount) ->
                            add("§7- ${materialName(id)} §fx$amount")
                        }
                        if (summary.salvagedMaterials.size > 6) add("§7... 还有 ${summary.salvagedMaterials.size - 6} 种材料")
                    }
                }
            }
        }
    }

    private fun collectionItem(summary: RunSummary): ItemStack {
        return XMaterial.LECTERN.parseItem()!!.apply {
            itemMeta = itemMeta?.also { meta ->
                meta.setDisplayName("§5收藏点亮")
                meta.lore = buildList {
                    add("")
                    add("§7Boss 首杀: §5${summary.bossFirstKills}")
                    add("§7点亮数量: §d${summary.collectionUnlocks.size}")
                    if (summary.collectionUnlocks.isEmpty()) {
                        add("§8本局没有点亮收藏")
                    } else {
                        summary.collectionUnlocks.take(8).forEach { add("§a- $it") }
                        if (summary.collectionUnlocks.size > 8) add("§7... 还有 ${summary.collectionUnlocks.size - 8} 项")
                    }
                }
            }
        }
    }

    private fun nextStepItem(summary: RunSummary): ItemStack {
        return XMaterial.COMPASS.parseItem()!!.apply {
            itemMeta = itemMeta?.also { meta ->
                meta.setDisplayName("§a下一步建议")
                meta.lore = buildList {
                    add("")
                    var suggestions = 0
                    if ((summary.lootCounts["unidentified_gear"] ?: 0) > 0) { add("§e未鉴定装备 → /rogue gear storage identify"); suggestions++ }
                    if ((summary.lootCounts["forge_book"] ?: 0) > 0) { add("§6锻造书 → /rogue gear storage craft"); suggestions++ }
                    if ((summary.lootCounts["sealed_accessory"] ?: 0) > 0) { add("§d密封饰品 → /rogue accessory identify"); suggestions++ }
                    if ((summary.lootCounts["accessory_inscription"] ?: 0) > 0) { add("§b饰品刻印书 → /rogue accessory inscribe"); suggestions++ }
                    if (summary.salvagedCount == 0 && summary.lootCounts.values.sum() > 0) { add("§6低价值物品 → /rogue gear storage salvage"); suggestions++ }
                    if (summary.collectionUnlocks.isNotEmpty()) { add("§5查看收藏进度 → /rogue progress collection"); suggestions++ }
                    if (suggestions == 0) add("§7继续挑战更高层，或整理背包回收材料。")
                }
            }
        }
    }

    private fun shortcutItem(material: XMaterial, name: String, command: String): ItemStack {
        return material.parseItem()!!.apply {
            itemMeta = itemMeta?.also { meta ->
                meta.setDisplayName(name)
                meta.lore = listOf("", "§7命令: §f$command", "", "§e点击打开")
            }
        }
    }

    private fun lootKeys(): List<String> = listOf("temporary_gear", "unidentified_gear", "forge_book", "accessory", "sealed_accessory", "accessory_inscription")

    private fun lootLabel(key: String): String = when (key) {
        "temporary_gear" -> "临时装备"
        "unidentified_gear" -> "未鉴定装备"
        "forge_book" -> "锻造书"
        "accessory" -> "饰品"
        "sealed_accessory" -> "密封饰品"
        "accessory_inscription" -> "饰品刻印书"
        else -> key
    }

    private fun materialName(id: String): String {
        return when (id) {
            "soul_iron" -> "§7魂铁"
            "inscription_dust" -> "§b铭刻粉尘"
            "relic_fragment" -> "§d遗物残片"
            "crown_shard" -> "§6王冠碎片"
            "astral_core" -> "§5星界核心"
            else -> "§f$id"
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
