package inori.roguecore.ui

import inori.roguecore.dungeon.DungeonManager
import inori.roguecore.milestone.RunMilestoneManager
import inori.roguecore.milestone.RunMilestoneType
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest

/** 本局里程碑面板。 */
object RunMilestoneUI {

    private val milestoneKeys = "abcdefghijklmnopqrstu".toList()

    fun open(player: Player) {
        val achieved = RunMilestoneManager.getAchieved(player.uniqueId)
        val inDungeon = DungeonManager.isInDungeon(player)
        player.openMenu<Chest>("§6§l本局里程碑") {
            rows(6)
            handLocked(true)
            map(
                "####H####",
                "#.......#",
                "#abcdefg#",
                "#hijklmn#",
                "#opqrstu#",
                "###R#C###"
            )
            val glass = glass(); set('#', glass); set('.', glass)
            set('H', buildSummaryItem(player, achieved, inDungeon))
            RunMilestoneType.entries.take(milestoneKeys.size).forEachIndexed { index, milestone -> set(milestoneKeys[index], buildMilestoneItem(milestone, achieved.contains(milestone))) }
            set('R', buildRefreshItem())
            set('C', buildCloseItem())
            onClick { event -> event.isCancelled = true }
            onClick('R') { open(player) }
            onClick('C') { player.closeInventory() }
        }
    }

    private fun buildSummaryItem(player: Player, achieved: Set<RunMilestoneType>, inDungeon: Boolean): ItemStack = XMaterial.NETHER_STAR.parseItem()!!.apply {
        itemMeta = itemMeta?.also { meta ->
            meta.setDisplayName("§6里程碑总览")
            val totalReward = achieved.sumOf { it.rewardShards }
            val progress = achieved.size.toDouble() / RunMilestoneType.entries.size.toDouble()
            meta.lore = buildList {
                add(""); add(if (inDungeon) "§a当前正在冒险中" else "§7当前不在副本中"); add("§7已达成: §e${achieved.size}§7/§e${RunMilestoneType.entries.size}"); add("§7进度: ${progressBar(progress)}"); add("§7已获得里程碑奖励: §6+$totalReward §7本局碎片"); add("§7普通战斗房连清: §f${RunMilestoneManager.getCombatStreak(player.uniqueId)}"); add(""); add("§7里程碑每局重置，达成后会即时奖励。")
            }
        }
    }

    private fun buildMilestoneItem(type: RunMilestoneType, achieved: Boolean): ItemStack = (type.icon.parseItem() ?: XMaterial.PAPER.parseItem()!!).apply {
        itemMeta = itemMeta?.also { meta -> meta.setDisplayName(if (achieved) "§a${type.displayName}" else "§7${type.displayName}"); meta.lore = listOf("", "§7分类: §f${type.category}", "§7目标: §f${type.description}", "§7奖励: §6+${type.rewardShards} §7本局碎片", "", if (achieved) "§a已达成" else "§8未达成") }
    }

    private fun buildRefreshItem(): ItemStack = XMaterial.CLOCK.parseItem()!!.apply { itemMeta = itemMeta?.also { it.setDisplayName("§a刷新"); it.lore = listOf("", "§7点击刷新里程碑状态") } }
    private fun buildCloseItem(): ItemStack = XMaterial.BARRIER.parseItem()!!.apply { itemMeta = itemMeta?.also { it.setDisplayName("§c关闭"); it.lore = listOf("", "§7点击关闭界面") } }
    private fun glass(): ItemStack = XMaterial.BLACK_STAINED_GLASS_PANE.parseItem()!!.apply { itemMeta = itemMeta?.also { it.setDisplayName(" ") } }
    private fun progressBar(progress: Double): String { val filled = (progress.coerceIn(0.0, 1.0) * 10).toInt(); return "§a" + "■".repeat(filled) + "§8" + "■".repeat(10 - filled) }
}
