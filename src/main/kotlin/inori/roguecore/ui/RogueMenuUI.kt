package inori.roguecore.ui

import inori.roguecore.accessory.AccessoryIdentificationTaskManager
import inori.roguecore.accessory.AccessoryInscriptionTaskManager
import inori.roguecore.accessory.PlayerAccessoryData
import inori.roguecore.collection.CollectionManager
import inori.roguecore.data.PermanentMaterialManager
import inori.roguecore.data.PlayerDataManager
import inori.roguecore.data.ShardRewardManager
import inori.roguecore.dungeon.DungeonManager
import inori.roguecore.item.ForgeBookTaskManager
import inori.roguecore.item.GearStorageManager
import inori.roguecore.item.IdentificationTaskManager
import inori.roguecore.item.LootStorageManager
import inori.roguecore.party.PartyManager
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest

/** RogueCore 分类主菜单。 */
object RogueMenuUI {

    private data class MenuButton(val material: XMaterial, val name: String, val lore: List<String>, val action: (Player) -> Unit)
    private val pageKeys = "abcdefghijklmn".toList()
    private const val PAGE_SIZE = 14

    fun open(player: Player) = openHome(player)

    private fun openHome(player: Player) {
        player.openMenu<Chest>("§6§lRogueCore §8控制台") {
            rows(6)
            handLocked(true)
            map(
                "#########",
                "#A#G#X#M#",
                "#.......#",
                "#P#S#T#I#",
                "#...H...#",
                "####C####"
            )
            val glass = glass()
            set('#', glass); set('.', glass)
            set('S', button(XMaterial.BEACON, "§6§l状态总览", safeStatusLore(player)))
            set('A', categoryButton(XMaterial.IRON_SWORD, "§a冒险", "§8冒险功能", listOf("§7进入副本、路线、构筑和冒险报告")))
            set('G', categoryButton(XMaterial.ENDER_CHEST, "§6装备", "§8装备系统", listOf("§7仓库、鉴定、锻造、回收和材料")))
            set('X', categoryButton(XMaterial.AMETHYST_SHARD, "§d饰品", "§8饰品系统", listOf("§7饰品匣、饰品鉴定和饰品刻印")))
            set('P', categoryButton(XMaterial.NETHER_STAR, "§5成长", "§8成长系统", listOf("§7天赋、研究、收藏、统计和局外侧栏")))
            set('T', categoryButton(XMaterial.PLAYER_HEAD, "§e队伍", "§8队伍系统", listOf("§7组队、邀请、离队和队伍列表")))
            set('I', categoryButton(XMaterial.KNOWLEDGE_BOOK, "§b资料", "§8资料索引", listOf("§7引导手册、图鉴和命令帮助")))
            set('H', categoryButton(if (LobbyHudManager.isEnabled(player)) XMaterial.LIME_DYE else XMaterial.GRAY_DYE, if (LobbyHudManager.isEnabled(player)) "§a局外侧栏: 开" else "§7局外侧栏: 关", "§8侧栏开关", listOf("§7点击切换副本外侧边栏")))
            set('M', if (player.hasPermission("roguecore.admin")) categoryButton(XMaterial.COMMAND_BLOCK, "§c管理", "§8管理工具", listOf("§7内容自检、重载和测试工具")) else categoryButton(XMaterial.BARRIER, "§8管理", "§8管理工具", listOf("§c你没有管理权限")))
            set('C', button(XMaterial.BARRIER, "§c关闭", listOf("§7关闭主菜单")))
            onClick { event -> event.isCancelled = true }
            onClick('A') { openRun(player) }
            onClick('G') { openGear(player) }
            onClick('X') { openAccessory(player) }
            onClick('P') { openProgress(player) }
            onClick('T') { openParty(player) }
            onClick('I') { openInfo(player) }
            onClick('H') { player.sendMessage(if (LobbyHudManager.toggle(player)) "§a局外侧栏 已开启。" else "§c局外侧栏 已关闭。"); openHome(player) }
            onClick('M') { if (player.hasPermission("roguecore.admin")) openAdmin(player) else player.sendMessage("§c你没有权限打开管理菜单。") }
            onClick('C') { player.closeInventory() }
        }
    }

    private fun openRun(player: Player) = openPage(player, "§a§l冒险", listOf(
        MenuButton(if (DungeonManager.isInDungeon(player)) XMaterial.ENDER_PEARL else XMaterial.IRON_SWORD, "§a开始/重连", listOf("§7选择起始层数进入副本", "§7有未结束副本时优先重连")) { p -> if (DungeonManager.isInDungeon(p)) p.closeInventory() else if (DungeonManager.canRejoinDungeon(p.uniqueId)) p.performCommand("rogue run rejoin") else RunEnterUI.open(p) },
        MenuButton(XMaterial.MAP, "§b路线选择", listOf("§7通关当前层后选择下一层路线")) { it.performCommand("rogue run route") },
        MenuButton(XMaterial.ENCHANTED_BOOK, "§d当前构筑", listOf("§7查看神恩、遗物、词缀和装备摘要")) { BuildUI.open(it) },
        MenuButton(XMaterial.TOTEM_OF_UNDYING, "§6本局里程碑", listOf("§7查看本局短期目标")) { RunMilestoneUI.open(it) },
        MenuButton(XMaterial.BEACON, "§b临时修正", listOf("§7查看局内状态修正")) { RunModifierUI.open(it) },
        MenuButton(XMaterial.WRITABLE_BOOK, "§e冒险报告", listOf("§7查看当前或最近一次冒险总结")) { RunSummaryUI.open(it) },
        MenuButton(XMaterial.OAK_DOOR, "§c结算离开", listOf("§7离开当前副本并结算")) { it.performCommand("rogue run leave") }
    ))

    private fun openGear(player: Player) = openPage(player, "§6§l装备", listOf(
        MenuButton(XMaterial.ENDER_CHEST, "§6装备仓库", listOf("§7存放永久装备", "§7当前: §f${GearStorageManager.getItems(player).size}/${GearStorageManager.getCapacity(player)}")) { GearStorageUI.open(it) },
        MenuButton(XMaterial.BARREL, "§b战利品仓库", listOf("§7存放未鉴定、锻造书、饰品和刻印书", "§7当前: §f${LootStorageManager.getItems(player).size}/${LootStorageManager.getCapacity(player)}")) { LootStorageUI.open(it) },
        MenuButton(XMaterial.SPYGLASS, "§e装备鉴定", listOf("§7完成待领取: §a${IdentificationTaskManager.getCompletedCount(player.uniqueId)}")) { IdentifyUI.open(it) },
        MenuButton(XMaterial.BLAST_FURNACE, "§6装备打造", listOf("§7完成待领取: §a${ForgeBookTaskManager.getCompletedCount(player.uniqueId)}")) { ForgeBookUI.open(it) },
        MenuButton(XMaterial.SMITHING_TABLE, "§6局外锻造", listOf("§7升阶、重铸、锁词和升品")) { if (DungeonManager.isInDungeon(it)) it.sendMessage("§c局外锻造只能在副本外使用。") else PermanentForgeUI.open(it) },
        MenuButton(XMaterial.RAW_IRON, "§6锻造材料", PermanentMaterialManager.formatOwned(player)) {},
        MenuButton(XMaterial.CRAFTING_TABLE, "§6材料工坊", listOf("§7合成、拆解和补充局外材料")) { WorkshopUI.open(it) },
        MenuButton(XMaterial.HOPPER, "§6回收工坊", listOf("§7分解没用的装备、饰品和书类")) { SalvageUI.open(it) }
    ))

    private fun openAccessory(player: Player) = openPage(player, "§d§l饰品", listOf(
        MenuButton(XMaterial.AMETHYST_SHARD, "§d饰品匣", listOf("§7已装备: §d${PlayerAccessoryData.getEquipped(player).size}§7/§f5")) { AccessoryUI.open(it) },
        MenuButton(XMaterial.CRAFTING_TABLE, "§d饰品工坊", listOf("§7完成待领取: §a${AccessoryIdentificationTaskManager.getCompletedCount(player.uniqueId) + AccessoryInscriptionTaskManager.getCompletedCount(player.uniqueId)}")) { AccessoryWorkshopUI.open(it) },
        MenuButton(XMaterial.SPYGLASS, "§e饰品鉴定", listOf("§7完成待领取: §a${AccessoryIdentificationTaskManager.getCompletedCount(player.uniqueId)}")) { AccessoryIdentifyUI.open(it) },
        MenuButton(XMaterial.WRITABLE_BOOK, "§6饰品刻印", listOf("§7完成待领取: §a${AccessoryInscriptionTaskManager.getCompletedCount(player.uniqueId)}")) { AccessoryInscriptionUI.open(it) }
    ))

    private fun openProgress(player: Player): Unit = openPage(player, "§5§l成长", listOf(
        MenuButton(XMaterial.NETHER_STAR, "§d天赋树", listOf("§7消耗灵魂碎片获得永久属性")) { TalentUI.open(it) },
        MenuButton(XMaterial.ENCHANTING_TABLE, "§5研究所", listOf("§7解锁局外研究和玩法强化")) { UnlockUI.open(it) },
        MenuButton(XMaterial.LECTERN, "§5收藏馆", collectionLore(player)) { CollectionUI.open(it) },
        MenuButton(XMaterial.BOOK, "§b冒险图鉴", listOf("§7查看神恩、遗物、词缀和路线")) { CodexUI.open(it) },
        MenuButton(XMaterial.PAPER, "§f个人统计", statsLore(player)) { it.performCommand("rogue progress stats") },
        MenuButton(XMaterial.KNOWLEDGE_BOOK, "§e引导手册", listOf("§7查看系统说明和成长路线")) { GuideUI.open(it) },
        MenuButton(if (LobbyHudManager.isEnabled(player)) XMaterial.LIME_DYE else XMaterial.GRAY_DYE, if (LobbyHudManager.isEnabled(player)) "§a局外侧栏: 开" else "§7局外侧栏: 关", listOf("§7点击切换局外侧栏")) { p -> p.sendMessage(if (LobbyHudManager.toggle(p)) "§a局外侧栏 已开启。" else "§c局外侧栏 已关闭。"); openProgress(p) }
    ))

    private fun openParty(player: Player) = openPage(player, "§e§l队伍", listOf(
        MenuButton(XMaterial.PLAYER_HEAD, "§a创建队伍", listOf("§7创建一个新的冒险队伍")) { it.performCommand("rogue party create") },
        MenuButton(XMaterial.EMERALD, "§a接受邀请", listOf("§7接受最近的队伍邀请")) { it.performCommand("rogue party accept") },
        MenuButton(XMaterial.OAK_DOOR, "§e离开队伍", listOf("§7离开当前队伍")) { it.performCommand("rogue party quit") },
        MenuButton(XMaterial.BARRIER, "§c解散队伍", listOf("§7队长解散当前队伍")) { it.performCommand("rogue party disband") },
        MenuButton(XMaterial.PAPER, "§f队伍列表", listOf("§7查看当前队伍成员")) { it.performCommand("rogue party list") },
        MenuButton(XMaterial.COMPASS, "§e邀请/踢出说明", listOf("§7邀请: §f/rogue party invite <玩家>", "§7踢出: §f/rogue party kick <玩家>")) {}
    ))

    private fun openInfo(player: Player) = openPage(player, "§b§l资料", listOf(
        MenuButton(XMaterial.KNOWLEDGE_BOOK, "§e引导手册", listOf("§7查看冒险、装备、饰品、回收和收藏说明")) { GuideUI.open(it) },
        MenuButton(XMaterial.BOOK, "§b冒险图鉴", listOf("§7查看当前内容池")) { CodexUI.open(it) },
        MenuButton(XMaterial.COMPASS, "§e命令帮助", listOf("§7查看分类命令")) { it.performCommand("rogue help") }
    ))

    private fun openAdmin(player: Player) = openPage(player, "§c§l管理", listOf(
        MenuButton(XMaterial.COMMAND_BLOCK, "§c内容自检", listOf("§7执行 /rogue admin audit")) { it.performCommand("rogue admin audit") },
        MenuButton(XMaterial.REDSTONE, "§c依赖/信息自检", listOf("§7执行 /rogue admin info")) { it.performCommand("rogue admin info") },
        MenuButton(XMaterial.REPEATER, "§e重载配置", listOf("§7执行 /rogue admin reload")) { it.performCommand("rogue admin reload") },
        MenuButton(XMaterial.MAP, "§b副本列表", listOf("§7执行 /rogue admin dungeons")) { it.performCommand("rogue admin dungeons") },
        MenuButton(XMaterial.PAPER, "§f平衡统计", listOf("§7执行 /rogue admin stats")) { it.performCommand("rogue admin stats") },
        MenuButton(XMaterial.GRASS_BLOCK, "§a重新生成世界", listOf("§7执行 /rogue admin regen")) { it.performCommand("rogue admin regen") },
        MenuButton(XMaterial.CHEST, "§6测试给物品", listOf("§7命令: /rogue admin give <玩家> <类型> <内容> ...")) {}
    ))

    private fun openPage(player: Player, title: String, buttons: List<MenuButton>, page: Int = 0) {
        val maxPage = ((buttons.size - 1) / PAGE_SIZE).coerceAtLeast(0)
        val safePage = page.coerceIn(0, maxPage)
        val visible = buttons.drop(safePage * PAGE_SIZE).take(PAGE_SIZE)
        player.openMenu<Chest>(title) {
            rows(6); handLocked(true)
            map(
                "####H####",
                "#.......#",
                "#abcdefg#",
                "#hijklmn#",
                "#.......#",
                "#P##B##N#"
            )
            val glass = glass(); set('#', glass); set('.', glass)
            set('H', button(XMaterial.BEACON, "§6§l$title", listOf("§7选择一个功能入口", "§8第 ${safePage + 1}/${maxPage + 1} 页")))
            set('B', button(XMaterial.OAK_DOOR, "§e返回", listOf("§7返回分类首页")))
            if (safePage > 0) set('P', button(XMaterial.ARROW, "§e上一页", listOf("§7第 $safePage 页"))) else set('P', glass)
            if (safePage < maxPage) set('N', button(XMaterial.ARROW, "§e下一页", listOf("§7第 ${safePage + 2} 页"))) else set('N', glass)
            visible.forEachIndexed { index, b -> val key = pageKeys[index]; set(key, button(b.material, b.name, b.lore + "" + "§e左键打开")); onClick(key) { b.action(player) } }
            onClick { event -> event.isCancelled = true }
            onClick('B') { openHome(player) }
            onClick('P') { if (safePage > 0) openPage(player, title, buttons, safePage - 1) }
            onClick('N') { if (safePage < maxPage) openPage(player, title, buttons, safePage + 1) }
        }
    }

    private fun safeStatusLore(player: Player): List<String> = runCatching { statusLore(player) }.getOrElse { listOf("§c状态读取失败", "§7${it.javaClass.simpleName}") }
    private fun statusLore(player: Player): List<String> { val d = PlayerDataManager.get(player.uniqueId); val dungeon = DungeonManager.getPlayerDungeon(player); val party = PartyManager.getParty(player); return listOf(if (dungeon != null) "§a当前状态: 副本中" else "§7当前状态: 副本外", "§7灵魂碎片: §e${d.soulShards}", "§7本局碎片: §6${ShardRewardManager.getRunShards(player.uniqueId)}", "§7最高楼层: §f${d.bestFloor}", if (party != null) "§7队伍: §f${party.size}/${party.maxSize}" else "§8当前未组队", "§7装备仓库: §f${GearStorageManager.getItems(player).size}/${GearStorageManager.getCapacity(player)}", "§7战利品仓库: §f${LootStorageManager.getItems(player).size}/${LootStorageManager.getCapacity(player)}") }
    private fun collectionLore(player: Player): List<String> { val c = CollectionManager.getProgress(player); return listOf("§7装备主题: §e${c.gearCollected}§7/§f${c.gearTotal}", "§7饰品槽位: §d${c.accessoryCollected}§7/§f${c.accessoryTotal}", "§7Boss 首杀: §5${c.bossCollected}§7/§f${c.bossTotal}") }
    private fun statsLore(player: Player): List<String> { val d = PlayerDataManager.get(player.uniqueId); return listOf("§7总运行次数: §f${d.totalRuns}", "§7总通关次数: §f${d.totalClears}", "§7最高楼层: §f${d.bestFloor}", "§7总击杀数: §f${d.totalKills}") }
    private fun categoryButton(material: XMaterial, name: String, tag: String, lore: List<String>): ItemStack = button(material, name, listOf(tag) + lore + listOf("", "§e左键打开"))
    private fun glass(): ItemStack = XMaterial.GRAY_STAINED_GLASS_PANE.parseItem()!!.apply { itemMeta = itemMeta?.also { it.setDisplayName(" ") } }
    private fun button(material: XMaterial, name: String, lore: List<String>): ItemStack = material.parseItem()!!.apply { itemMeta = itemMeta?.also { it.setDisplayName(name); it.lore = listOf("") + lore } }
}
