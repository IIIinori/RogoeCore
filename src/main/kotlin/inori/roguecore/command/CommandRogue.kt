package inori.roguecore.command

import inori.roguecore.accessory.AccessoryDropManager
import inori.roguecore.accessory.AccessoryItemCodec
import inori.roguecore.accessory.AccessoryRegistry
import inori.roguecore.affix.AffixRegistry
import inori.roguecore.audit.ContentAuditManager
import inori.roguecore.boon.BoonRegistry
import inori.roguecore.boon.PlayerBoonData
import inori.roguecore.combat.MonsterConfig
import inori.roguecore.curse.RunCurseManager
import inori.roguecore.data.PermanentMaterialManager
import inori.roguecore.data.PlayerDataManager
import inori.roguecore.data.ShardRewardManager
import inori.roguecore.dependency.DependencySelfCheckManager
import inori.roguecore.dungeon.DungeonManager
import inori.roguecore.dungeon.floor.FloorManager
import inori.roguecore.dungeon.generator.RoomTypeWeights
import inori.roguecore.event.ChestEvent
import inori.roguecore.event.ContractEvent
import inori.roguecore.event.ExtractionEvent
import inori.roguecore.event.ForgeEvent
import inori.roguecore.event.GambleEvent
import inori.roguecore.event.HiddenEvent
import inori.roguecore.event.RestEvent
import inori.roguecore.event.ShrineEvent
import inori.roguecore.event.ShopEvent
import inori.roguecore.event.TrialEvent
import inori.roguecore.item.DungeonLootManager
import inori.roguecore.item.DungeonLootSource
import inori.roguecore.modifier.RunModifierManager
import inori.roguecore.ops.OpsConfigManager
import inori.roguecore.party.PartyManager
import inori.roguecore.relic.PlayerRelicData
import inori.roguecore.relic.RelicRegistry
import inori.roguecore.stats.BalanceStatsManager
import inori.roguecore.stats.PerfMonitor
import inori.roguecore.talent.TalentManager
import inori.roguecore.talent.TalentRegistry
import inori.roguecore.display.ContentDisplayNameResolver
import inori.roguecore.ui.AccessoryIdentifyUI
import inori.roguecore.ui.AccessoryInscriptionUI
import inori.roguecore.ui.AccessoryUI
import inori.roguecore.ui.AccessoryWorkshopUI
import inori.roguecore.ui.BuildUI
import inori.roguecore.ui.CodexUI
import inori.roguecore.ui.CollectionUI
import inori.roguecore.ui.ForgeBookUI
import inori.roguecore.ui.GearStorageUI
import inori.roguecore.ui.GuideUI
import inori.roguecore.ui.IdentifyUI
import inori.roguecore.ui.LobbyHudManager
import inori.roguecore.ui.LootStorageUI
import inori.roguecore.ui.PermanentForgeUI
import inori.roguecore.ui.RogueMenuUI
import inori.roguecore.ui.RunCompleteUI
import inori.roguecore.ui.RunEnterUI
import inori.roguecore.ui.RunMilestoneUI
import inori.roguecore.ui.RunModifierUI
import inori.roguecore.ui.RunSummaryUI
import inori.roguecore.ui.SalvageUI
import inori.roguecore.ui.TalentUI
import inori.roguecore.ui.UnlockUI
import inori.roguecore.ui.WorkshopUI
import inori.roguecore.unlock.UnlockManager
import inori.roguecore.unlock.UnlockRegistry
import inori.roguecore.workshop.WorkshopManager
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.subCommand
import taboolib.common.platform.command.component.CommandComponent
import taboolib.expansion.createHelper
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap


@CommandHeader("rogue", permission = "roguecore.use")
object CommandRogue {

    private val giveKinds = listOf("gear", "unidentified", "forgebook", "accessory", "sealed", "inscription")
    private val dungeonEnterPending = ConcurrentHashMap.newKeySet<UUID>()
    private val sources = DungeonLootSource.entries.map { it.name }

    @CommandBody
    val main = mainCommand {
        createHelper()
    }

    @CommandBody(description = "打开主菜单")
    val menu = subCommand {
        execute<Player> { sender, _, _ -> RogueMenuUI.open(sender) }
    }

    @CommandBody(description = "查看分类命令帮助")
    val help = subCommand {
        execute<CommandSender> { sender, _, _ -> showRootHelp(sender) }
    }

    @CommandBody(description = "冒险命令")
    val run = subCommand {
        literal("enter") {
            dynamic("floor") {
                suggestion<Player> { _, _ -> (1..100).map { it.toString() } }
                execute<Player> { sender, _, argument -> enterDungeon(sender, argument.toIntOrNull() ?: 1) }
            }
            execute<Player> { sender, _, _ -> if (DungeonManager.isInDungeon(sender)) enterDungeon(sender, 1) else RunEnterUI.open(sender) }
        }
        literal("leave") { execute<Player> { sender, _, _ -> leaveDungeon(sender) } }
        literal("rejoin") { execute<Player> { sender, _, _ -> rejoinDungeon(sender) } }
        literal("route") { execute<Player> { sender, _, _ -> openRoute(sender) } }
        literal("build") { execute<Player> { sender, _, _ -> BuildUI.open(sender) } }
        literal("modifiers") { execute<Player> { sender, _, _ -> RunModifierUI.open(sender) } }
        literal("milestones") { execute<Player> { sender, _, _ -> RunMilestoneUI.open(sender) } }
        literal("summary") { execute<Player> { sender, _, _ -> RunSummaryUI.open(sender) } }
        execute<CommandSender> { sender, _, _ -> showRunHelp(sender) }
    }

    @CommandBody(description = "队伍命令")
    val party = subCommand {
        literal("create") { execute<Player> { sender, _, _ -> PartyManager.createParty(sender) } }
        literal("invite") {
            dynamic("player") {
                suggestion<Player> { _, _ -> Bukkit.getOnlinePlayers().map { it.name } }
                execute<Player> { sender, _, argument -> PartyManager.invitePlayer(sender, argument) }
            }
        }
        literal("accept") { execute<Player> { sender, _, _ -> PartyManager.acceptInvite(sender) } }
        literal("quit") { execute<Player> { sender, _, _ -> PartyManager.leaveParty(sender) } }
        literal("kick") {
            dynamic("player") {
                suggestion<Player> { sender, _ -> PartyManager.getParty(sender)?.members?.mapNotNull { Bukkit.getPlayer(it)?.name }?.filter { it != sender.name } ?: emptyList() }
                execute<Player> { sender, _, argument -> PartyManager.kickPlayer(sender, argument) }
            }
        }
        literal("disband") { execute<Player> { sender, _, _ -> PartyManager.disbandParty(sender) } }
        literal("list") { execute<Player> { sender, _, _ -> showPartyList(sender) } }
        execute<CommandSender> { sender, _, _ -> showPartyHelp(sender) }
    }

    @CommandBody(description = "装备命令")
    val gear = subCommand {
        literal("storage") { execute<Player> { sender, _, _ -> GearStorageUI.open(sender) } }
        literal("loot") { execute<Player> { sender, _, _ -> LootStorageUI.open(sender) } }
        literal("forge") { execute<Player> { sender, _, _ -> openPermanentForge(sender) } }
        literal("identify") { execute<Player> { sender, _, _ -> IdentifyUI.open(sender) } }
        literal("craft") { execute<Player> { sender, _, _ -> ForgeBookUI.open(sender) } }
        literal("salvage") { execute<Player> { sender, _, _ -> SalvageUI.open(sender) } }
        literal("materials") { execute<Player> { sender, _, _ -> showMaterials(sender) } }
        literal("workshop") { execute<Player> { sender, _, _ -> WorkshopUI.open(sender) } }
        execute<CommandSender> { sender, _, _ -> showGearHelp(sender) }
    }

    @CommandBody(description = "饰品命令")
    val accessory = subCommand {
        literal("box") { execute<Player> { sender, _, _ -> AccessoryUI.open(sender) } }
        literal("workshop") { execute<Player> { sender, _, _ -> AccessoryWorkshopUI.open(sender) } }
        literal("identify") { execute<Player> { sender, _, _ -> AccessoryIdentifyUI.open(sender) } }
        literal("inscribe") { execute<Player> { sender, _, _ -> AccessoryInscriptionUI.open(sender) } }
        execute<CommandSender> { sender, _, _ -> showAccessoryHelp(sender) }
    }

    @CommandBody(description = "成长命令")
    val progress = subCommand {
        literal("talent") { execute<Player> { sender, _, _ -> TalentUI.open(sender) } }
        literal("unlocks") { execute<Player> { sender, _, _ -> UnlockUI.open(sender) } }
        literal("collection") { execute<Player> { sender, _, _ -> CollectionUI.open(sender) } }
        literal("codex") { execute<Player> { sender, _, _ -> CodexUI.open(sender) } }
        literal("stats") { execute<Player> { sender, _, _ -> showStats(sender) } }
        literal("guide") { execute<Player> { sender, _, _ -> GuideUI.open(sender) } }
        literal("hud") {
            literal("on") { execute<Player> { sender, _, _ -> LobbyHudManager.setEnabled(sender, true); sender.sendMessage("§a局外侧栏 已开启。") } }
            literal("off") { execute<Player> { sender, _, _ -> LobbyHudManager.setEnabled(sender, false); sender.sendMessage("§c局外侧栏 已关闭。") } }
            literal("toggle") { execute<Player> { sender, _, _ -> sender.sendMessage(if (LobbyHudManager.toggle(sender)) "§a局外侧栏 已开启。" else "§c局外侧栏 已关闭。") } }
            execute<Player> { sender, _, _ -> LobbyHudManager.sendStatus(sender) }
        }
        execute<CommandSender> { sender, _, _ -> showProgressHelp(sender) }
    }

    @CommandBody(description = "管理员命令", permission = "roguecore.admin")
    val admin = subCommand {
        literal("audit") { execute<CommandSender> { sender, _, _ -> runContentAudit(sender) } }
        literal("perf") {
            literal("reset") { execute<CommandSender> { sender, _, _ -> PerfMonitor.reset(); sender.sendMessage("§a性能采样已重置。") } }
            execute<CommandSender> { sender, _, _ -> PerfMonitor.sendSummary(sender) }
        }
        literal("ops") {
            literal("list") { execute<CommandSender> { sender, _, _ -> showOpsList(sender) } }
            literal("clear-all") { execute<CommandSender> { sender, _, _ -> OpsConfigManager.clearAll(); sender.sendMessage("§a在线覆盖已全部清除。") } }
            literal("get") {
                dynamic("path") {
                    execute<CommandSender> { sender, _, argument -> showOpsGet(sender, argument) }
                }
            }
            literal("clear") {
                dynamic("path") {
                    execute<CommandSender> { sender, _, argument -> clearOpsPath(sender, argument) }
                }
            }
            literal("set") {
                dynamic("path") {
                    dynamic("value") {
                        execute<CommandSender> { sender, context, argument ->
                            setOpsPath(sender, context["path"], argument)
                        }
                    }
                }
            }
            execute<CommandSender> { sender, _, _ -> showOpsHelp(sender) }
        }
        literal("stats") {
            literal("reset") { execute<CommandSender> { sender, _, _ -> BalanceStatsManager.reset(); sender.sendMessage("§a平衡统计已重置。") } }
            execute<CommandSender> { sender, _, _ -> BalanceStatsManager.sendSummary(sender) }
        }
        literal("affix-rotation") {
            literal("status") { execute<CommandSender> { sender, _, _ -> showAffixRotationStatus(sender) } }
            execute<CommandSender> { sender, _, _ -> showAffixRotationStatus(sender) }
        }
        literal("reload") { execute<CommandSender> { sender, _, _ -> reloadConfigs(sender) } }
        literal("dungeons") { execute<CommandSender> { sender, _, _ -> showDungeons(sender) } }
        literal("destroy") {
            dynamic("dungeon") {
                suggestion<CommandSender> { _, _ ->
                    val size = DungeonManager.getActiveDungeons().size
                    (1..size).map { it.toString() }
                }
                execute<CommandSender> { sender, _, argument -> destroyDungeon(sender, argument) }
            }
        }
        literal("forceleave") { dynamic("player") { suggestion<CommandSender> { _, _ -> Bukkit.getOnlinePlayers().map { it.name } }; execute<CommandSender> { sender, _, argument -> forceLeave(sender, argument) } } }
        literal("shards") {
            literal("give") { adminShardAmount("give") }
            literal("take") { adminShardAmount("take") }
            literal("set") { adminShardAmount("set") }
        }
        literal("materials") {
            literal("give") { adminMaterialAmount("give") }
            literal("take") { adminMaterialAmount("take") }
            literal("set") { adminMaterialAmount("set") }
            literal("show") {
                dynamic("player") {
                    suggestion<CommandSender> { _, _ -> Bukkit.getOnlinePlayers().map { it.name } }
                    execute<CommandSender> { sender, _, argument -> adminMaterialShow(sender, argument) }
                }
            }
        }
        literal("unlock") {
            dynamic("player") {
                suggestion<CommandSender> { _, _ -> Bukkit.getOnlinePlayers().map { it.name } }
                dynamic("unlockId") { suggestion<CommandSender> { _, _ -> UnlockRegistry.getAll().map { it.id }.sorted() }; execute<CommandSender> { sender, context, argument -> adminUnlock(sender, listOf(context["player"], argument)) } }
            }
        }
        literal("talentset") {
            dynamic("player") {
                suggestion<CommandSender> { _, _ -> Bukkit.getOnlinePlayers().map { it.name } }
                dynamic("talentId") {
                    suggestion<CommandSender> { _, _ -> TalentRegistry.getAll().map { it.id }.sorted() }
                    dynamic("level") { suggestion<CommandSender> { _, _ -> (0..10).map { it.toString() } }; execute<CommandSender> { sender, context, argument -> adminTalentSet(sender, listOf(context["player"], context["talentId"], argument)) } }
                }
            }
        }
        literal("reset") { dynamic("player") { suggestion<CommandSender> { _, _ -> Bukkit.getOnlinePlayers().map { it.name } }; execute<CommandSender> { sender, _, argument -> adminReset(sender, argument) } } }
        literal("info") { execute<Player> { sender, _, _ -> showDungeonInfo(sender) } }
        literal("regen") { execute<Player> { sender, _, _ -> regenDungeon(sender) } }
        literal("give") {
            dynamic("player") {
                suggestion<CommandSender> { _, _ -> Bukkit.getOnlinePlayers().map { it.name } }
                dynamic("kind") {
                    suggestion<CommandSender> { _, _ -> giveKinds }
                    dynamic("id") {
                        suggestion<CommandSender> { _, context -> if (context["kind"].lowercase() in listOf("accessory", "sealed", "inscription")) AccessoryRegistry.getAll().map { it.id }.sorted() else DungeonLootManager.getDefinitionIds() }
                        execute<CommandSender> { sender, context, argument -> adminGive(sender, listOf(context["player"], context["kind"], argument)) }
                        dynamic("source") {
                            suggestion<CommandSender> { _, _ -> sources }
                            execute<CommandSender> { sender, context, argument -> adminGive(sender, listOf(context["player"], context["kind"], context["id"], argument)) }
                            dynamic("floor") {
                                suggestion<CommandSender> { _, _ -> listOf("1", "10", "25", "50", "75", "100") }
                                execute<CommandSender> { sender, context, argument -> adminGive(sender, listOf(context["player"], context["kind"], context["id"], context["source"], argument)) }
                                dynamic("extra") {
                                    suggestion<CommandSender> { _, context ->
                                        when (context["kind"].lowercase()) {
                                            "gear" -> listOf("temporary", "permanent", "permanent_common_noaffix_fixedroll")
                                            "forgebook" -> DungeonLootManager.getForgeBookQualityIds()
                                            "inscription" -> AccessoryRegistry.getInscriptionQualities().map { it.id }.sorted()
                                            else -> emptyList()
                                        }
                                    }
                                    execute<CommandSender> { sender, context, argument -> adminGive(sender, listOf(context["player"], context["kind"], context["id"], context["source"], context["floor"], argument)) }
                                }
                            }
                        }
                    }
                }
            }
        }
        execute<CommandSender> { sender, _, _ -> showAdminHelp(sender) }
    }

    private fun enterDungeon(player: Player, floor: Int) {
        if (DungeonManager.isInDungeon(player)) {
            player.sendMessage("§c你已经在地牢中了! 使用 /rogue run leave 离开")
            return
        }
        if (player.uniqueId in dungeonEnterPending) {
            player.sendMessage("§7副本正在生成中，请不要重复点击。")
            return
        }
        val party = PartyManager.getParty(player)
        val config = FloorManager.getFloorConfig(floor)
        if (party != null) {
            if (!party.isLeader(player.uniqueId)) {
                player.sendMessage("§c只有队长才能开启副本!")
                return
            }
            val members = mutableListOf<Player>()
            for (uuid in party.members) {
                val member = Bukkit.getPlayer(uuid)
                if (member == null) {
                    player.sendMessage("§c队员有人不在线，无法开启!")
                    return
                }
                if (DungeonManager.isInDungeon(member)) {
                    player.sendMessage("§c队员 ${member.name} 已在副本中!")
                    return
                }
                if (member.uniqueId in dungeonEnterPending) {
                    player.sendMessage("§c队员 ${member.name} 正在生成副本，请稍候。")
                    return
                }
                members += member
            }
            members.forEach { dungeonEnterPending += it.uniqueId }
            members.forEach { it.closeInventory(); it.sendMessage("§e正在为队伍生成副本，请稍候...") }
            DungeonManager.createDungeonAsync(config, members) { instance ->
                if (instance == null) {
                    members.forEach { it.sendMessage("§c地牢创建失败!") }
                    members.forEach { dungeonEnterPending -= it.uniqueId }
                    return@createDungeonAsync
                }
                party.dungeonId = instance.id
                for (member in members) {
                    DungeonManager.joinDungeon(member, instance.id)
                    member.sendMessage("§a队伍已进入第 §f${config.floorNumber} §a层副本 §7(${displayThemeName(config.theme.name)} - ${instance.rooms.size} 个房间)")
                }
                members.forEach { dungeonEnterPending -= it.uniqueId }
            }
        } else {
            dungeonEnterPending += player.uniqueId
            player.closeInventory()
            player.sendMessage("§e正在生成副本，请稍候...")
            DungeonManager.createDungeonAsync(config, listOf(player)) { instance ->
                if (instance == null) {
                    player.sendMessage("§c地牢创建失败!")
                    dungeonEnterPending -= player.uniqueId
                    return@createDungeonAsync
                }
                DungeonManager.joinDungeon(player, instance.id)
                player.sendMessage("§a已进入第 §f${config.floorNumber} §a层副本 §7(${displayThemeName(config.theme.name)} - ${instance.rooms.size} 个房间)")
                dungeonEnterPending -= player.uniqueId
            }
        }
    }

    private fun leaveDungeon(player: Player) {
        if (!DungeonManager.isInDungeon(player)) {
            player.sendMessage("§c你不在地牢中!")
            return
        }
        DungeonManager.leaveDungeon(player)
        player.sendMessage("§a已离开地牢")
    }

    private fun rejoinDungeon(player: Player) {
        if (DungeonManager.isInDungeon(player)) {
            player.sendMessage("§e你已经在副本中，无需重连。")
            return
        }
        if (!DungeonManager.rejoinDungeon(player)) {
            player.sendMessage("§c没有可重连的副本，或该副本已经结束。")
        }
    }

    private fun openRoute(player: Player) {
        val dungeon = DungeonManager.getPlayerDungeon(player)
        if (dungeon == null) {
            player.sendMessage("§c你不在副本中。")
            return
        }
        if (!dungeon.completed) {
            player.sendMessage("§c当前副本尚未通关，不能选择下一层路线。")
            return
        }
        val party = PartyManager.getPartyByDungeonId(dungeon.id)
        if (party != null && !party.isLeader(player.uniqueId)) {
            player.sendMessage("§c只有队长可以选择下一层路线。")
            return
        }
        RunCompleteUI.open(player, dungeon, party)
    }

    private fun showPartyList(player: Player) {
        val party = PartyManager.getParty(player)
        if (party == null) {
            player.sendMessage("§c你不在任何队伍中!")
            return
        }
        player.sendMessage("§6===== 队伍信息 §7(${party.size}/${party.maxSize}) §6=====")
        for (uuid in party.members) {
            val name = playerDisplayName(uuid)
            val tag = if (party.isLeader(uuid)) " §6[队长]" else ""
            val online = if (Bukkit.getPlayer(uuid) != null) "§a" else "§7"
            player.sendMessage("  $online$name$tag")
        }
        party.dungeonId?.let { dungeonId ->
            val dungeon = DungeonManager.getDungeonById(dungeonId)
            val dungeonText = dungeon?.let { "第 ${it.config.floorNumber} 层" } ?: "进行中"
            player.sendMessage("§e当前副本: §f$dungeonText")
        }
    }

    private fun openPermanentForge(player: Player) {
        if (DungeonManager.isInDungeon(player)) {
            player.sendMessage("§c副本内请使用铁匠房，局外锻造只能在副本外使用。")
            return
        }
        PermanentForgeUI.open(player)
    }

    private fun showMaterials(player: Player) {
        player.sendMessage("§6===== 局外锻造材料 =====")
        for (line in PermanentMaterialManager.formatOwned(player)) player.sendMessage(line)
    }

    private fun showStats(player: Player) {
        val data = PlayerDataManager.get(player.uniqueId)
        player.sendMessage("§6===== 个人统计 =====")
        player.sendMessage("§e灵魂碎片: §6${data.soulShards}")
        player.sendMessage("§e总运行次数: §f${data.totalRuns}")
        player.sendMessage("§e总通关次数: §f${data.totalClears}")
        player.sendMessage("§e最高楼层: §f${data.bestFloor}")
        player.sendMessage("§e总击杀数: §f${data.totalKills}")
        val runShards = ShardRewardManager.getRunShards(player.uniqueId)
        if (runShards > 0) {
            player.sendMessage("§e当前本局碎片: §6$runShards")
            player.sendMessage("§e当前结算预览: §6${ShardRewardManager.getSettlementPreview(player.uniqueId)} §e灵魂碎片")
        }
        val boons = PlayerBoonData.getBoons(player)
        if (boons.isNotEmpty()) {
            player.sendMessage("§e当前神恩:")
            for (b in boons) {
                val tags = if (b.boon.tags.isNotEmpty()) " §8(${b.boon.tags.joinToString("/") { inori.roguecore.display.ContentDisplayNameResolver.safeText(it, "流派") }})" else ""
                player.sendMessage("  ${b.boon.rarity.color}${b.boon.name} §eLv.${b.level}$tags")
            }
        }
        val relics = PlayerRelicData.getRelics(player)
        if (relics.isNotEmpty()) {
            player.sendMessage("§d当前遗物:")
            for (relic in relics) player.sendMessage("  ${relic.rarity.color}${relic.name} §7- ${relic.description}")
        }
    }

    private fun CommandComponent.adminShardAmount(action: String) {
        dynamic("player") {
            suggestion<CommandSender> { _, _ -> Bukkit.getOnlinePlayers().map { it.name } }
            dynamic("amount") {
                suggestion<CommandSender> { _, _ -> listOf("10", "50", "100", "500", "1000") }
                execute<CommandSender> { sender, context, argument -> adminShards(sender, listOf(action, context["player"], argument)) }
            }
        }
    }

    private fun CommandComponent.adminMaterialAmount(action: String) {
        dynamic("player") {
            suggestion<CommandSender> { _, _ -> Bukkit.getOnlinePlayers().map { it.name } }
            dynamic("material") {
                suggestion<CommandSender> { _, _ -> PermanentMaterialManager.materialNames() }
                dynamic("amount") {
                    suggestion<CommandSender> { _, _ -> listOf("1", "5", "10", "50", "100") }
                    execute<CommandSender> { sender, context, argument ->
                        adminMaterials(sender, listOf(action, context["player"], context["material"], argument))
                    }
                }
            }
        }
    }

    private fun adminGive(sender: CommandSender, args: List<String>) {
        if (args.size < 3) {
            sender.sendMessage("§c用法: /rogue admin give <玩家> <类型> <内容> [来源] [层数] [额外]")
            sender.sendMessage("§7类型: 装备、未鉴定装备、锻造书、饰品、密封饰品、刻印书")
            return
        }
        val target = Bukkit.getPlayerExact(args[0])
        if (target == null) {
            sender.sendMessage("§c玩家不在线: §f${args[0]}")
            return
        }
        val kind = args[1].lowercase()
        val id = args[2]
        val source = args.getOrNull(3)?.let { parseSource(it) } ?: DungeonLootSource.CHEST
        val floor = args.getOrNull(4)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val extra = args.getOrNull(5) ?: when (kind) {
            "gear" -> "temporary"
            "forgebook", "inscription" -> "rough"
            else -> ""
        }
        val result = when (kind) {
            "gear", "unidentified", "forgebook" -> DungeonLootManager.buildAdminGiveItem(target, kind, id, source, floor, extra)
            "accessory" -> AccessoryDropManager.buildIdentifiedItemForPlayer(target, id, source, floor).let {
                DungeonLootManager.AdminGiveItemResult(it.success, it.item, it.message)
            }
            "sealed" -> {
                val definition = AccessoryRegistry.get(id)
                if (definition == null) {
                    DungeonLootManager.AdminGiveItemResult(
                        false,
                        null,
                        "§c饰品模板不存在: §f${ContentDisplayNameResolver.safeText(id, "未知饰品")}"
                    )
                }
                else DungeonLootManager.AdminGiveItemResult(true, AccessoryItemCodec.buildSealedAccessory(definition, source, floor), "§a已生成密封饰品: §f${definition.name}")
            }
            "inscription" -> {
                val definition = AccessoryRegistry.get(id)
                val quality = AccessoryRegistry.getInscriptionQuality(extra) ?: AccessoryRegistry.getInscriptionQuality("rough")
                when {
                    definition == null -> DungeonLootManager.AdminGiveItemResult(
                        false,
                        null,
                        "§c饰品模板不存在: §f${ContentDisplayNameResolver.safeText(id, "未知饰品")}"
                    )
                    quality == null -> DungeonLootManager.AdminGiveItemResult(
                        false,
                        null,
                        "§c刻印品质不存在: §f${ContentDisplayNameResolver.safeText(extra, "未知品质")}"
                    )
                    else -> DungeonLootManager.AdminGiveItemResult(true, AccessoryItemCodec.buildInscriptionBook(definition, source, floor, quality), "§a已生成饰品刻印书: §f${definition.name} §7(${quality.displayName})")
                }
            }
            else -> DungeonLootManager.AdminGiveItemResult(false, null, "§c未知测试物品类型: §f$kind")
        }
        if (!result.success || result.item == null) {
            sender.sendMessage(result.message)
            return
        }
        giveItem(target, result.item)
        sender.sendMessage("§a已给予 §f${target.name} §a测试物品。§7${result.message}")
        target.sendMessage("§a管理员给予了你一个 RogueCore 测试物品。")
    }

    private fun giveItem(player: Player, item: ItemStack) {
        val leftovers = player.inventory.addItem(item)
        leftovers.values.forEach { player.world.dropItemNaturally(player.location, it) }
    }

    private fun parseSource(value: String): DungeonLootSource? {
        return runCatching { DungeonLootSource.valueOf(value.uppercase()) }.getOrNull()
    }

    private fun adminShards(sender: CommandSender, args: List<String>) {
        if (args.size < 3) {
            sender.sendMessage("§c用法: /rogue admin shards <give|take|set> <player> <amount>")
            return
        }
        val target = resolveTarget(args[1])
        if (target == null) {
            sender.sendMessage("§c未找到玩家: §f${args[1]} §7(需在线或至少进服一次)")
            return
        }
        val amount = args[2].toIntOrNull()
        if (amount == null || amount < 0) {
            sender.sendMessage("§c金额必须是大于等于 0 的整数。")
            return
        }
        when (args[0].lowercase()) {
            "give" -> {
                PlayerDataManager.addSoulShards(target.uuid, amount)
                sender.sendMessage("§a已为 §f${target.name} §a增加 §6$amount §a灵魂碎片。")
            }
            "take" -> {
                if (!PlayerDataManager.takeSoulShards(target.uuid, amount)) {
                    sender.sendMessage("§c${target.name} 的灵魂碎片不足，无法扣除 §6$amount§c。")
                    return
                }
                sender.sendMessage("§a已从 §f${target.name} §a扣除 §6$amount §a灵魂碎片。")
            }
            "set" -> {
                PlayerDataManager.setSoulShards(target.uuid, amount)
                sender.sendMessage("§a已将 §f${target.name} §a的灵魂碎片设置为 §6$amount§a。")
            }
            else -> sender.sendMessage("§c未知操作: §f${args[0]}")
        }
    }

    private fun adminMaterials(sender: CommandSender, args: List<String>) {
        if (args.size < 4) {
            sender.sendMessage("§c用法: /rogue admin materials <give|take|set> <player> <材料中文名> <amount>")
            sender.sendMessage("§7材料: ${PermanentMaterialManager.materialNames().joinToString("、")}")
            return
        }
        val target = Bukkit.getPlayerExact(args[1])
        if (target == null) {
            sender.sendMessage("§c玩家不在线: §f${args[1]}")
            return
        }
        val type = PermanentMaterialManager.fromDisplayName(args[2])
        if (type == null) {
            sender.sendMessage("§c未知材料: §f${args[2]}")
            sender.sendMessage("§7仅支持中文名: ${PermanentMaterialManager.materialNames().joinToString("、")}")
            return
        }
        val amount = args[3].toIntOrNull()
        if (amount == null || amount < 0) {
            sender.sendMessage("§c数量必须是大于等于 0 的整数。")
            return
        }
        when (args[0].lowercase()) {
            "give" -> {
                PermanentMaterialManager.add(target, type, amount)
                sender.sendMessage("§a已为 §f${target.name} §a增加 ${type.coloredName()} §fx$amount§a。")
            }
            "take" -> {
                if (!PermanentMaterialManager.take(target, type, amount)) {
                    sender.sendMessage("§c${target.name} 的${type.displayName}不足，无法扣除 §fx$amount§c。")
                    return
                }
                sender.sendMessage("§a已从 §f${target.name} §a扣除 ${type.coloredName()} §fx$amount§a。")
            }
            "set" -> {
                PermanentMaterialManager.set(target, type, amount)
                sender.sendMessage("§a已将 §f${target.name} §a的${type.displayName}设置为 §fx$amount§a。")
            }
            else -> sender.sendMessage("§c未知操作: §f${args[0]}")
        }
    }

    private fun adminMaterialShow(sender: CommandSender, playerName: String?) {
        if (playerName.isNullOrBlank()) {
            sender.sendMessage("§c用法: /rogue admin materials show <player>")
            return
        }
        val target = Bukkit.getPlayerExact(playerName)
        if (target == null) {
            sender.sendMessage("§c玩家不在线: §f$playerName")
            return
        }
        sender.sendMessage("§6===== ${target.name} 的局外锻造材料 =====")
        PermanentMaterialManager.MaterialType.values().forEach { type ->
            sender.sendMessage("§7- ${PermanentMaterialManager.formatOwnedLine(target, type)}")
        }
    }

    private fun adminUnlock(sender: CommandSender, args: List<String>) {
        if (args.size < 2) {
            sender.sendMessage("§c用法: /rogue admin unlock <player> <unlockId>")
            return
        }
        val target = resolveTarget(args[0])
        val unlock = UnlockRegistry.get(args[1])
        if (target == null) {
            sender.sendMessage("§c未找到玩家: §f${args[0]}")
            return
        }
        if (unlock == null) {
            sender.sendMessage("§c未找到研究: §f${args[1]}")
            return
        }
        if (!UnlockManager.grantUnlock(target.uuid, unlock.id)) {
            sender.sendMessage("§c${target.name} 已拥有研究 §f${unlock.name}§c。")
            return
        }
        sender.sendMessage("§a已为 §f${target.name} §a授予研究 §d${unlock.name}§a。")
    }

    private fun adminTalentSet(sender: CommandSender, args: List<String>) {
        if (args.size < 3) {
            sender.sendMessage("§c用法: /rogue admin talentset <player> <talentId> <level>")
            return
        }
        val target = resolveTarget(args[0])
        val talent = TalentRegistry.get(args[1])
        val level = args[2].toIntOrNull()
        if (target == null) {
            sender.sendMessage("§c未找到玩家: §f${args[0]}")
            return
        }
        if (talent == null) {
            sender.sendMessage("§c未找到天赋: §f${args[1]}")
            return
        }
        if (level == null || level !in 0..talent.maxLevel) {
            sender.sendMessage("§c等级必须在 0-${talent.maxLevel} 之间。")
            return
        }
        TalentManager.setTalentLevel(target.uuid, talent.id, level)
        target.player?.takeIf { DungeonManager.isInDungeon(it) }?.let {
            TalentManager.removeTalents(it)
            TalentManager.applyTalents(it)
        }
        sender.sendMessage("§a已将 §f${target.name} §a的天赋 §f${talent.name} §a设置为 Lv.$level。")
    }

    private fun adminReset(sender: CommandSender, name: String?) {
        if (name.isNullOrBlank()) {
            sender.sendMessage("§c用法: /rogue admin reset <player>")
            return
        }
        val target = resolveTarget(name)
        if (target == null) {
            sender.sendMessage("§c未找到玩家: §f$name")
            return
        }
        target.player?.let { online -> if (DungeonManager.isInDungeon(online)) DungeonManager.leaveDungeon(online) }
        TalentManager.clearAll(target.uuid)
        UnlockManager.clearAll(target.uuid)
        PlayerDataManager.reset(target.uuid)
        sender.sendMessage("§a已重置玩家 §f${target.name} §a的永久数据、研究与天赋。")
        target.player?.sendMessage("§c管理员已重置你的 RogueCore 进度。")
    }

    private fun showDungeons(sender: CommandSender) {
        val dungeons = DungeonManager.getActiveDungeons().sortedBy { it.config.floorNumber }
        if (dungeons.isEmpty()) {
            sender.sendMessage("§7当前没有活跃副本。")
            return
        }
        sender.sendMessage("§6===== 活跃副本 ${dungeons.size} 个 =====")
        dungeons.forEachIndexed { index, dungeon ->
            val names = dungeon.players.map(::playerDisplayName)
            sender.sendMessage("§e副本 ${index + 1} §7- 第 §f${dungeon.config.floorNumber} §7层 §8(${displayThemeName(dungeon.config.theme.name)}) §7玩家:${dungeon.players.size} §8[${names.joinToString(", ")}]")
        }
    }

    private fun destroyDungeon(sender: CommandSender, dungeonToken: String?) {
        if (dungeonToken.isNullOrBlank()) {
            sender.sendMessage("§c用法: /rogue admin destroy <副本编号>")
            return
        }
        val dungeons = DungeonManager.getActiveDungeons().sortedBy { it.config.floorNumber }
        val dungeon = dungeonToken.toIntOrNull()
            ?.let { index -> dungeons.getOrNull(index - 1) }
            ?: DungeonManager.getDungeonById(dungeonToken)
        if (dungeon == null) {
            sender.sendMessage("§c未找到对应的活跃副本。")
            return
        }
        val playerCount = dungeon.players.size
        val floor = dungeon.config.floorNumber
        DungeonManager.destroyDungeon(dungeon.id)
        sender.sendMessage("§a已销毁该副本 §7(Floor $floor, 玩家 $playerCount)")
    }

    private fun forceLeave(sender: CommandSender, name: String?) {
        if (name.isNullOrBlank()) {
            sender.sendMessage("§c用法: /rogue admin forceleave <player>")
            return
        }
        val target = Bukkit.getPlayerExact(name)
        if (target == null) {
            sender.sendMessage("§c玩家不在线: §f$name")
            return
        }
        val dungeon = DungeonManager.getPlayerDungeon(target)
        if (dungeon == null) {
            sender.sendMessage("§c${target.name} 当前不在副本中。")
            return
        }
        DungeonManager.leaveDungeon(target)
        sender.sendMessage("§a已强制让 §f${target.name} §a离开当前副本。")
        target.sendMessage("§c管理员已强制将你移出副本。")
    }

    private fun showDungeonInfo(player: Player) {
        val dungeon = DungeonManager.getPlayerDungeon(player)
        if (dungeon == null) {
            player.sendMessage("§c你不在地牢中!")
            return
        }
        player.sendMessage("§6===== 副本信息 =====")
        player.sendMessage("§e当前楼层: §f第 ${dungeon.config.floorNumber} 层")
        player.sendMessage("§e主题: §f${displayThemeName(dungeon.config.theme.name)}")
        player.sendMessage("§e房间数: §f${dungeon.rooms.size}")
        player.sendMessage("§e玩家数: §f${dungeon.players.size}")
        dungeon.rooms.forEachIndexed { index, room ->
            val stateColor = when (room.state.name) {
                "CLEARED" -> "§a"
                "ACTIVE" -> "§c"
                else -> "§7"
            }
            player.sendMessage("  ${stateColor}房间 ${index + 1} §f${room.type.displayName} ${stateColor}[${roomStateName(room.state.name)}]")
        }
        player.sendMessage("§e隐藏钥匙: §b${dungeon.getHiddenKeys()}")
    }

    private fun regenDungeon(player: Player) {
        if (DungeonManager.isInDungeon(player)) DungeonManager.leaveDungeon(player)
        player.sendMessage("§e正在重新生成地牢...")
        val config = FloorManager.getFloorConfig(1)
        val instance = DungeonManager.createDungeon(config)
        if (instance == null) {
            player.sendMessage("§c地牢创建失败!")
            return
        }
        DungeonManager.joinDungeon(player, instance.id)
        player.sendMessage("§a新的第 §f${config.floorNumber} §a层副本已生成 §7(${instance.rooms.size} 个房间)")
    }

    private fun reloadConfigs(sender: CommandSender) {
        FloorManager.config.reload(); FloorManager.load()
        BoonRegistry.config.reload(); BoonRegistry.load()
        RelicRegistry.config.reload(); RelicRegistry.load()
        UnlockRegistry.config.reload(); UnlockRegistry.load()
        MonsterConfig.config.reload(); MonsterConfig.load()
        RoomTypeWeights.config.reload(); RoomTypeWeights.load()
        TalentRegistry.config.reload(); TalentRegistry.load()
        ShardRewardManager.config.reload(); ShardRewardManager.load()
        DungeonLootManager.config.reload(); DungeonLootManager.load()
        AccessoryRegistry.config.reload(); AccessoryRegistry.load()
        ChestEvent.config.reload(); RestEvent.config.reload(); ShopEvent.config.reload(); ForgeEvent.config.reload(); ExtractionEvent.config.reload(); ContractEvent.config.reload(); RunCurseManager.config.reload(); TrialEvent.config.reload(); GambleEvent.config.reload(); ShrineEvent.config.reload(); HiddenEvent.config.reload()
        RunModifierManager.config.reload(); RunModifierManager.load()
        WorkshopManager.config.reload(); WorkshopManager.load()
        AffixRegistry.config.reload(); AffixRegistry.load()
        inori.roguecore.event.EventAffixManager.config.reload(); inori.roguecore.event.EventAffixManager.load()
        PartyManager.load()
        DependencySelfCheckManager.runCheck(sender)
        sender.sendMessage("§a配置文件已重载")
    }

    private fun showRootHelp(sender: CommandSender) {
        sender.sendMessage("§6===== RogueCore 命令分类 =====")
        sender.sendMessage("§e/rogue §7- 打开主菜单")
        sender.sendMessage("§e/rogue run ... §7- 冒险、副本、构筑、报告")
        sender.sendMessage("§e/rogue party ... §7- 队伍管理")
        sender.sendMessage("§e/rogue gear storage/loot ... §7- 装备仓库、战利品仓库、鉴定、锻造、回收")
        sender.sendMessage("§e/rogue accessory box ... §7- 饰品匣、饰品鉴定、饰品刻印")
        sender.sendMessage("§e/rogue progress ... §7- 天赋、研究、收藏、图鉴、引导、局外侧栏")
        sender.sendMessage("§e/rogue admin ... §7- 管理与测试")
    }

    private fun showRunHelp(sender: CommandSender, unknown: String = "") {
        if (unknown.isNotBlank()) sender.sendMessage("§c未知冒险操作: §f$unknown")
        sender.sendMessage("§6冒险: §e/rogue run enter §7打开层数选择，§e/rogue run enter <floor>§7直接进入，leave, rejoin, route, build, modifiers, milestones, summary")
    }

    private fun showPartyHelp(sender: CommandSender, unknown: String = "") {
        if (unknown.isNotBlank()) sender.sendMessage("§c未知队伍操作: §f$unknown")
        sender.sendMessage("§6队伍: §e/rogue party create§7, invite <player>, accept, quit, kick <player>, disband, list")
    }

    private fun showGearHelp(sender: CommandSender, unknown: String = "") {
        if (unknown.isNotBlank()) sender.sendMessage("§c未知装备操作: §f$unknown")
        sender.sendMessage("§6装备: §e/rogue gear storage§7, loot, forge, identify, craft, salvage, materials, workshop")
    }

    private fun showAccessoryHelp(sender: CommandSender, unknown: String = "") {
        if (unknown.isNotBlank()) sender.sendMessage("§c未知饰品操作: §f$unknown")
        sender.sendMessage("§6饰品: §e/rogue accessory box§7, workshop, identify, inscribe")
    }

    private fun showProgressHelp(sender: CommandSender, unknown: String = "") {
        if (unknown.isNotBlank()) sender.sendMessage("§c未知成长操作: §f$unknown")
        sender.sendMessage("§6成长: §e/rogue progress talent§7, unlocks, collection, codex, stats, guide, hud")
    }

    private fun showAdminHelp(sender: CommandSender) {
        sender.sendMessage("§6===== RogueCore 管理命令 =====")
        sender.sendMessage("§e/rogue admin audit §7- 内容自检")
        sender.sendMessage("§e/rogue admin stats [reset] §7- 平衡统计")
        sender.sendMessage("§e/rogue admin perf [reset] §7- 关键路径性能采样")
        sender.sendMessage("§e/rogue admin affix-rotation [status] §7- 查看副本词缀轮换池")
        sender.sendMessage("§e/rogue admin ops ... §7- 在线覆盖平衡参数(内存)")
        sender.sendMessage("§e/rogue admin reload §7- 重载配置")
        sender.sendMessage("§e/rogue admin dungeons/destroy/forceleave §7- 副本管理")
        sender.sendMessage("§e/rogue admin shards/materials/unlock/talentset/reset §7- 玩家数据")
        sender.sendMessage("§e/rogue admin give <玩家> <类型> <内容> [来源] [层数] [额外] §7- 给测试物品")
        sender.sendMessage("§e/rogue admin materials <give|take|set|show> <玩家> [材料中文名] [数量] §7- 管理局外锻造材料")
    }

    private fun showOpsHelp(sender: CommandSender) {
        sender.sendMessage("§6===== RogueCore 在线调参 =====")
        sender.sendMessage("§e/rogue admin ops list §7- 查看当前覆盖")
        sender.sendMessage("§e/rogue admin ops get <path> §7- 查看单个覆盖")
        sender.sendMessage("§e/rogue admin ops set <path> <value> §7- 设置覆盖(支持 int/double/bool)")
        sender.sendMessage("§e/rogue admin ops clear <path> §7- 清除单个覆盖")
        sender.sendMessage("§e/rogue admin ops clear-all §7- 清除所有覆盖")
        sender.sendMessage("§7允许前缀: hud., guide., generation., storage.loot.")
    }

    private fun showOpsList(sender: CommandSender) {
        val entries = OpsConfigManager.list()
        if (entries.isEmpty()) {
            sender.sendMessage("§7当前没有在线覆盖。")
            return
        }
        sender.sendMessage("§6===== 当前在线覆盖 ${entries.size} 项 =====")
        entries.forEach { (path, value) ->
            sender.sendMessage("§e$path §7= §f$value")
        }
    }

    private fun showOpsGet(sender: CommandSender, path: String?) {
        if (path.isNullOrBlank()) {
            sender.sendMessage("§c用法: /rogue admin ops get <path>")
            return
        }
        val value = OpsConfigManager.get(path)
        if (value == null) {
            sender.sendMessage("§7未设置覆盖: §f$path")
            return
        }
        sender.sendMessage("§e$path §7= §f$value")
    }

    private fun clearOpsPath(sender: CommandSender, path: String?) {
        if (path.isNullOrBlank()) {
            sender.sendMessage("§c用法: /rogue admin ops clear <path>")
            return
        }
        if (OpsConfigManager.clear(path)) {
            sender.sendMessage("§a已清除覆盖: §f$path")
        } else {
            sender.sendMessage("§7该路径没有覆盖: §f$path")
        }
    }

    private fun setOpsPath(sender: CommandSender, path: String?, rawValue: String?) {
        if (path.isNullOrBlank() || rawValue.isNullOrBlank()) {
            sender.sendMessage("§c用法: /rogue admin ops set <path> <value>")
            return
        }
        val result = OpsConfigManager.set(path, rawValue)
        if (result.isSuccess) {
            sender.sendMessage("§a已设置覆盖: §f$path §7= §f$rawValue")
        } else {
            sender.sendMessage("§c设置失败: ${result.exceptionOrNull()?.message ?: "未知错误"}")
        }
    }

    private fun runContentAudit(sender: CommandSender) {
        val result = ContentAuditManager.run()
        for (line in ContentAuditManager.format(result)) sender.sendMessage(line)
    }

    private fun showAffixRotationStatus(sender: CommandSender) {
        val snapshot = AffixRegistry.getRotationSnapshot()
        sender.sendMessage("§6===== 副本词缀轮换池 =====")
        sender.sendMessage("§7状态: ${if (snapshot.enabled) "§a已启用" else "§8未启用"}")
        sender.sendMessage("§7当前池: §f${snapshot.activePool ?: "全池(未限制)"}")
        sender.sendMessage("§7轮换周期: §f${snapshot.cycleDays} 天")
        sender.sendMessage("§7锚点日期: §f${snapshot.anchorDate}")
        sender.sendMessage("§7时区: §f${snapshot.timezone}")
    }

    private fun playerDisplayName(uuid: UUID): String {
        return Bukkit.getPlayer(uuid)?.name ?: Bukkit.getOfflinePlayer(uuid).name ?: "离线玩家"
    }

    private fun displayThemeName(raw: String): String = ContentDisplayNameResolver.safeText(raw, "未知主题")

    private fun roomStateName(state: String): String = when (state) {
        "CLEARED" -> "已清理"
        "ACTIVE" -> "进行中"
        "COMPLETED" -> "已完成"
        "LOCKED" -> "未开放"
        "VISITED" -> "已进入"
        "IDLE" -> "未进入"
        else -> "未知"
    }

    private data class ResolvedTarget(val uuid: UUID, val name: String, val player: Player? = null)

    private fun resolveTarget(name: String): ResolvedTarget? {
        Bukkit.getPlayerExact(name)?.let { player -> return ResolvedTarget(player.uniqueId, player.name, player) }
        val offline = Bukkit.getOfflinePlayers().firstOrNull { it.name?.equals(name, ignoreCase = true) == true } ?: return null
        return ResolvedTarget(offline.uniqueId, offline.name ?: name)
    }
}
