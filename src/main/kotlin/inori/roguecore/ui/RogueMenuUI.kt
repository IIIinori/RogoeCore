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
import inori.roguecore.item.IdentificationTaskManager
import inori.roguecore.milestone.RunMilestoneManager
import inori.roguecore.modifier.RunModifierManager
import inori.roguecore.party.PartyManager
import inori.roguecore.summary.RunSummaryManager
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest

/**
 * RogueCore 玩家主菜单。
 */
object RogueMenuUI {

    fun open(player: Player) {
        val data = PlayerDataManager.get(player.uniqueId)
        val dungeon = DungeonManager.getPlayerDungeon(player)
        val inDungeon = dungeon != null
        val party = PartyManager.getParty(player)
        val runShards = ShardRewardManager.getRunShards(player.uniqueId)
        val milestoneCount = RunMilestoneManager.getAchieved(player.uniqueId).size
        val modifierCount = RunModifierManager.getModifiers(player.uniqueId).size
        val completedIdentify = IdentificationTaskManager.getCompletedCount(player.uniqueId)
        val completedForge = ForgeBookTaskManager.getCompletedCount(player.uniqueId)
        val completedAccessoryIdentify = AccessoryIdentificationTaskManager.getCompletedCount(player.uniqueId)
        val completedAccessoryInscribe = AccessoryInscriptionTaskManager.getCompletedCount(player.uniqueId)
        val hasSummary = RunSummaryManager.getDisplaySummary(player) != null
        val canRoute = dungeon?.completed == true

        player.openMenu<Chest>("§6§lRogueCore §7主菜单") {
            rows(6)
            handLocked(true)

            val buttons = setOf(
                4,
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34, 37, 38, 39,
                49
            )
            val glass = XMaterial.GRAY_STAINED_GLASS_PANE.parseItem()!!.apply {
                itemMeta = itemMeta?.also { it.setDisplayName(" ") }
            }
            for (slot in 0 until 54) {
                if (slot !in buttons) set(slot, glass)
            }

            set(4, statusButton(player, dungeon, data.soulShards, runShards, milestoneCount, modifierCount, party?.size, party?.maxSize))

            set(10, button(
                if (inDungeon) XMaterial.ENDER_PEARL else if (DungeonManager.canRejoinDungeon(player.uniqueId)) XMaterial.ENDER_EYE else XMaterial.IRON_SWORD,
                when {
                    inDungeon -> "§b当前冒险"
                    DungeonManager.canRejoinDungeon(player.uniqueId) -> "§b重连副本"
                    else -> "§a开始冒险"
                },
                buildList {
                    add(if (inDungeon) "§7你当前已经在副本中" else "§7生成并进入一场新的冒险")
                    if (!inDungeon && DungeonManager.canRejoinDungeon(player.uniqueId)) add("§7检测到可重连副本")
                    add(if (party != null) "§7当前队伍: §f${party.size}/${party.maxSize}" else "§8当前未组队")
                    add("")
                    add(if (inDungeon) "§e点击关闭菜单继续冒险" else if (DungeonManager.canRejoinDungeon(player.uniqueId)) "§e点击执行 /rogue run rejoin" else "§e点击执行 /rogue run enter")
                }
            ))

            set(11, button(
                if (canRoute) XMaterial.MAP else XMaterial.COMPASS,
                if (canRoute) "§b下一层路线" else "§7路线选择",
                listOf(
                    if (canRoute) "§7当前副本已通关，可选择下一层路线" else "§7通关当前层后可选择下一层路线",
                    dungeon?.let { "§7当前楼层: §f${it.config.floorNumber}" } ?: "§8当前不在副本中",
                    "",
                    if (canRoute) "§e点击打开路线选择" else "§8暂不可用"
                )
            ))

            set(12, button(
                XMaterial.ENCHANTED_BOOK,
                "§d当前构筑",
                listOf(
                    "§7查看本局神恩、遗物、共鸣、词缀和装备摘要",
                    if (inDungeon) "§7本局碎片: §6$runShards" else "§8当前不在副本中，也可查看最近状态",
                    "",
                    "§e点击打开"
                )
            ))

            set(13, button(
                XMaterial.TOTEM_OF_UNDYING,
                "§6本局里程碑",
                listOf(
                    "§7查看本局短期目标和达成进度",
                    "§7已达成: §e$milestoneCount",
                    "",
                    "§e点击打开"
                )
            ))

            set(14, button(
                XMaterial.BEACON,
                "§b临时修正",
                listOf(
                    "§7查看事件链带来的本局状态",
                    "§7当前激活: §b$modifierCount",
                    "",
                    "§e点击打开"
                )
            ))

            set(15, button(
                XMaterial.WRITABLE_BOOK,
                if (hasSummary) "§e冒险报告" else "§7冒险报告",
                listOf(
                    "§7查看当前或最近一次冒险总结",
                    if (hasSummary) "§a已有可查看报告" else "§8暂无报告",
                    "",
                    if (hasSummary) "§e点击打开" else "§8完成一次冒险后可查看"
                )
            ))

            set(16, button(
                XMaterial.OAK_DOOR,
                if (inDungeon) "§c离开副本" else "§7离开副本",
                listOf(
                    if (inDungeon) "§7结束当前副本并返回原位" else "§8当前不在副本中",
                    if (inDungeon) "§7会结算本局灵魂碎片" else "",
                    "",
                    if (inDungeon) "§e点击执行 /rogue run leave" else "§8暂不可用"
                ).filter { it.isNotEmpty() }
            ))

            set(19, button(
                XMaterial.NETHER_STAR,
                "§d天赋树",
                listOf("§7消耗灵魂碎片获得永久属性", "§7当前灵魂碎片: §e${data.soulShards}", "", "§e点击打开")
            ))

            set(20, button(
                XMaterial.ENCHANTING_TABLE,
                "§5研究所",
                listOf("§7解锁局外研究和玩法强化", "§7推进长期成长路线", "", "§e点击打开")
            ))

            set(21, button(
                XMaterial.BOOK,
                "§b冒险图鉴",
                listOf("§7查看神恩、遗物、词缀和路线", "§7了解当前内容池", "", "§e点击打开")
            ))

            set(22, button(
                XMaterial.SPYGLASS,
                if (completedIdentify > 0) "§a装备鉴定 §7($completedIdentify)" else "§e装备鉴定",
                listOf(
                    "§7鉴定副本中获得的未鉴定装备",
                    "§7鉴定后变为绑定你的永久装备",
                    "§7已完成待领取: §a$completedIdentify",
                    "",
                    "§e点击打开"
                )
            ))

            set(23, button(
                XMaterial.BLAST_FURNACE,
                if (completedForge > 0) "§a装备打造 §7($completedForge)" else "§6装备打造",
                listOf(
                    "§7使用副本掉落的锻造书打造装备",
                    "§7不同品质锻造书耗时和材料不同",
                    "§7已完成待领取: §a$completedForge",
                    "",
                    "§e点击打开"
                )
            ))

            set(24, button(
                XMaterial.ENDER_CHEST,
                "§6装备仓库",
                listOf("§7存放绑定你的永久装备", "§7支持取出和快速装备", "", "§e点击打开")
            ))

            set(25, button(
                XMaterial.SMITHING_TABLE,
                if (inDungeon) "§7局外锻造" else "§6局外锻造",
                listOf(
                    "§7培养灵魂铭刻后的永久装备",
                    "§7升阶、重铸、锁词、升品、分解",
                    "",
                    if (inDungeon) "§8副本中不可使用" else "§e点击打开"
                )
            ))

            set(28, button(
                XMaterial.RAW_IRON,
                "§6锻造材料",
                PermanentMaterialManager.formatOwned(player)
            ))

            set(29, button(
                XMaterial.CRAFTING_TABLE,
                "§6材料工坊",
                listOf(
                    "§7合成、拆解和补充局外锻造材料",
                    "§7把多余材料转成当前缺口",
                    "",
                    "§e点击打开"
                )
            ))

            set(30, button(
                XMaterial.PAPER,
                "§f个人统计",
                listOf(
                    "§7总运行次数: §f${data.totalRuns}",
                    "§7总通关次数: §f${data.totalClears}",
                    "§7最高楼层: §f${data.bestFloor}",
                    "§7总击杀数: §f${data.totalKills}",
                    "",
                    "§e点击查看详细统计"
                )
            ))

            val accessoryCount = PlayerAccessoryData.getEquipped(player).size
            set(31, button(
                XMaterial.AMETHYST_SHARD,
                "§d饰品匣",
                listOf(
                    "§7管理项链、戒指、印记、护符和战利品",
                    "§7已装备: §d$accessoryCount§7/§f5",
                    "§7饰品放入 GUI 后才会生效",
                    "",
                    "§e点击打开"
                )
            ))

            val accessoryWorkshopDone = completedAccessoryIdentify + completedAccessoryInscribe
            set(32, button(
                XMaterial.CRAFTING_TABLE,
                if (accessoryWorkshopDone > 0) "§a饰品工坊 §7($accessoryWorkshopDone)" else "§d饰品工坊",
                listOf(
                    "§7鉴定密封饰品并刻印饰品刻印书",
                    "§7饰品鉴定完成: §a$completedAccessoryIdentify",
                    "§7饰品刻印完成: §a$completedAccessoryInscribe",
                    "",
                    "§e点击打开"
                )
            ))

            set(33, button(
                XMaterial.PLAYER_HEAD,
                "§e队伍指令",
                listOf("§7/create 创建队伍", "§7/invite 邀请玩家", "§7/accept 接受邀请", "§7/list 查看队伍", "", "§e点击查看队伍命令")
            ))

            set(34, button(
                XMaterial.COMPASS,
                "§e命令帮助",
                listOf("§7查看常用玩家命令", "", "§e点击执行 /rogue help")
            ))

            set(37, button(
                XMaterial.HOPPER,
                "§6回收工坊",
                listOf(
                    "§7分解没用的装备、饰品和书类",
                    "§7获得本局碎片、灵魂碎片和局外材料",
                    "",
                    "§e点击打开"
                )
            ))

            val collection = CollectionManager.getProgress(player)
            set(38, button(
                XMaterial.LECTERN,
                "§5收藏馆",
                listOf(
                    "§7提交高品质装备和饰品点亮长期收藏",
                    "§7装备主题: §e${collection.gearCollected}§7/§f${collection.gearTotal}",
                    "§7饰品槽位: §d${collection.accessoryCollected}§7/§f${collection.accessoryTotal}",
                    "§7Boss 首杀: §5${collection.bossCollected}§7/§f${collection.bossTotal}",
                    "",
                    "§e点击打开"
                )
            ))

            set(39, button(
                XMaterial.KNOWLEDGE_BOOK,
                "§e引导手册",
                listOf(
                    "§7不知道下一步做什么？",
                    "§7查看冒险、装备、饰品、回收和收藏说明",
                    "",
                    "§e点击打开"
                )
            ))

            set(49, button(XMaterial.BARRIER, "§c关闭菜单", listOf("§7关闭 RogueCore 主菜单")))

            onClick { event ->
                event.isCancelled = true
                when (event.rawSlot) {
                    10 -> when {
                        DungeonManager.isInDungeon(player) -> player.closeInventory()
                        DungeonManager.canRejoinDungeon(player.uniqueId) -> player.performCommand("rogue rejoin")
                        else -> player.performCommand("rogue enter")
                    }
                    11 -> {
                        val latest = DungeonManager.getPlayerDungeon(player)
                        if (latest?.completed == true) {
                            player.performCommand("rogue route")
                        } else {
                            player.sendMessage("§c通关当前层后才能选择下一层路线。")
                        }
                    }
                    12 -> BuildUI.open(player)
                    13 -> RunMilestoneUI.open(player)
                    14 -> RunModifierUI.open(player)
                    15 -> RunSummaryUI.open(player)
                    16 -> if (DungeonManager.isInDungeon(player)) player.performCommand("rogue leave") else player.sendMessage("§7你当前不在副本中。")
                    19 -> TalentUI.open(player)
                    20 -> UnlockUI.open(player)
                    21 -> CodexUI.open(player)
                    22 -> IdentifyUI.open(player)
                    23 -> ForgeBookUI.open(player)
                    24 -> GearStorageUI.open(player)
                    25 -> {
                        if (DungeonManager.isInDungeon(player)) {
                            player.sendMessage("§c局外锻造只能在副本外使用。")
                        } else {
                            PermanentForgeUI.open(player)
                        }
                    }
                    28 -> player.performCommand("rogue materials")
                    29 -> WorkshopUI.open(player)
                    30 -> player.performCommand("rogue stats")
                    31 -> AccessoryUI.open(player)
                    32 -> AccessoryWorkshopUI.open(player)
                    33 -> showPartyHelp(player)
                    34 -> player.performCommand("rogue help")
                    37 -> SalvageUI.open(player)
                    38 -> CollectionUI.open(player)
                    39 -> GuideUI.open(player)
                    49 -> player.closeInventory()
                }
            }
        }
    }

    private fun statusButton(
        player: Player,
        dungeon: inori.roguecore.dungeon.DungeonInstance?,
        soulShards: Int,
        runShards: Int,
        milestoneCount: Int,
        modifierCount: Int,
        partySize: Int?,
        partyMaxSize: Int?
    ): ItemStack {
        return button(
            XMaterial.NETHER_STAR,
            "§6状态总览",
            buildList {
                add(if (dungeon != null) "§a当前状态: 副本中" else "§7当前状态: 副本外")
                if (dungeon != null) {
                    add("§7当前楼层: §f${dungeon.config.floorNumber}")
                    add("§7本局碎片: §6$runShards")
                    add("§7隐藏钥匙: §b${dungeon.getHiddenKeys()}")
                    add("§7已达成里程碑: §e$milestoneCount")
                    add("§7临时修正: §b$modifierCount")
                    add(if (dungeon.completed) "§a当前层已通关，可选择路线或结算" else "§7当前层探索中")
                } else {
                    add("§7永久灵魂碎片: §e$soulShards")
                    add(if (DungeonManager.canRejoinDungeon(player.uniqueId)) "§b存在可重连副本" else "§8暂无进行中的副本")
                }
                if (partySize != null && partyMaxSize != null) {
                    add("§7队伍: §f$partySize/$partyMaxSize")
                } else {
                    add("§8当前未组队")
                }
            }
        )
    }

    private fun button(material: XMaterial, name: String, lore: List<String>): ItemStack {
        return material.parseItem()!!.apply {
            itemMeta = itemMeta?.also { meta ->
                meta.setDisplayName(name)
                meta.lore = listOf("") + lore
            }
        }
    }

    private fun showPartyHelp(player: Player) {
        player.closeInventory()
        player.sendMessage("§6===== RogueCore 队伍命令 =====")
        player.sendMessage("§e/rogue party create §7- 创建队伍")
        player.sendMessage("§e/rogue party invite <玩家> §7- 邀请玩家")
        player.sendMessage("§e/rogue party accept §7- 接受邀请")
        player.sendMessage("§e/rogue party quit §7- 离开队伍")
        player.sendMessage("§e/rogue party list §7- 查看队伍成员")
        player.sendMessage("§e/rogue party kick <玩家> §7- 踢出队员")
        player.sendMessage("§e/rogue party disband §7- 解散队伍")
    }
}
