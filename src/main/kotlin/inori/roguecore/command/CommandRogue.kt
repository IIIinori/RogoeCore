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
import inori.roguecore.party.PartyManager
import inori.roguecore.relic.PlayerRelicData
import inori.roguecore.relic.RelicRegistry
import inori.roguecore.stats.BalanceStatsManager
import inori.roguecore.talent.TalentManager
import inori.roguecore.talent.TalentRegistry
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
import inori.roguecore.ui.PermanentForgeUI
import inori.roguecore.ui.RogueMenuUI
import inori.roguecore.ui.RunCompleteUI
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


@CommandHeader("rogue", permission = "roguecore.use")
object CommandRogue {

    private val giveKinds = listOf("gear", "unidentified", "forgebook", "accessory", "sealed", "inscription")
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
            execute<Player> { sender, _, _ -> enterDungeon(sender, 1) }
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
        execute<CommandSender> { sender, _, _ -> showProgressHelp(sender) }
    }

    @CommandBody(description = "管理员命令", permission = "roguecore.admin")
    val admin = subCommand {
        literal("audit") { execute<CommandSender> { sender, _, _ -> runContentAudit(sender) } }
        literal("stats") {
            literal("reset") { execute<CommandSender> { sender, _, _ -> BalanceStatsManager.reset(); sender.sendMessage("§a平衡统计已重置。") } }
            execute<CommandSender> { sender, _, _ -> BalanceStatsManager.sendSummary(sender) }
        }
        literal("reload") { execute<CommandSender> { sender, _, _ -> reloadConfigs(sender) } }
        literal("dungeons") { execute<CommandSender> { sender, _, _ -> showDungeons(sender) } }
        literal("destroy") { dynamic("dungeonId") { suggestion<CommandSender> { _, _ -> DungeonManager.getActiveDungeons().map { it.id } }; execute<CommandSender> { sender, _, argument -> destroyDungeon(sender, argument) } } }
        literal("forceleave") { dynamic("player") { suggestion<CommandSender> { _, _ -> Bukkit.getOnlinePlayers().map { it.name } }; execute<CommandSender> { sender, _, argument -> forceLeave(sender, argument) } } }
        literal("shards") {
            literal("give") { adminShardAmount("give") }
            literal("take") { adminShardAmount("take") }
            literal("set") { adminShardAmount("set") }
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
                                            "gear" -> listOf("temporary", "permanent")
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
        val party = PartyManager.getParty(player)
        val config = FloorManager.getFloorConfig(floor)
        if (party != null) {
            if (!party.isLeader(player.uniqueId)) {
                player.sendMessage("§c只有队长才能开启副本!")
                return
            }
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
            }
            player.sendMessage("§e正在为队伍生成地牢...")
            val instance = DungeonManager.joinPartyDungeon(party, config)
            if (instance == null) {
                player.sendMessage("§c地牢创建失败!")
                return
            }
            for (uuid in party.members) {
                Bukkit.getPlayer(uuid)?.sendMessage("§a队伍已进入地牢 §f${instance.id} §a(${config.theme.name} - ${instance.rooms.size}个房间)")
            }
        } else {
            player.sendMessage("§e正在生成地牢...")
            val instance = DungeonManager.createDungeon(config)
            if (instance == null) {
                player.sendMessage("§c地牢创建失败!")
                return
            }
            DungeonManager.joinDungeon(player, instance.id)
            player.sendMessage("§a已进入地牢 §f${instance.id} §a(${config.theme.name} - ${instance.rooms.size}个房间)")
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
            val name = Bukkit.getPlayer(uuid)?.name ?: Bukkit.getOfflinePlayer(uuid).name ?: uuid.toString()
            val tag = if (party.isLeader(uuid)) " §6[队长]" else ""
            val online = if (Bukkit.getPlayer(uuid) != null) "§a" else "§7"
            player.sendMessage("  $online$name$tag")
        }
        if (party.dungeonId != null) player.sendMessage("§e当前副本: §f${party.dungeonId}")
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
                val tags = if (b.boon.tags.isNotEmpty()) " §8(${b.boon.tags.joinToString("/")})" else ""
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

    private fun adminGive(sender: CommandSender, args: List<String>) {
        if (args.size < 3) {
            sender.sendMessage("§c用法: /rogue admin give <player> <kind> <id> [source] [floor] [extra]")
            sender.sendMessage("§7kind: gear, unidentified, forgebook, accessory, sealed, inscription")
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
                if (definition == null) DungeonLootManager.AdminGiveItemResult(false, null, "§c饰品模板不存在: §f$id")
                else DungeonLootManager.AdminGiveItemResult(true, AccessoryItemCodec.buildSealedAccessory(definition, source, floor), "§a已生成密封饰品: §f${definition.name}")
            }
            "inscription" -> {
                val definition = AccessoryRegistry.get(id)
                val quality = AccessoryRegistry.getInscriptionQuality(extra) ?: AccessoryRegistry.getInscriptionQuality("rough")
                when {
                    definition == null -> DungeonLootManager.AdminGiveItemResult(false, null, "§c饰品模板不存在: §f$id")
                    quality == null -> DungeonLootManager.AdminGiveItemResult(false, null, "§c刻印品质不存在: §f$extra")
                    else -> DungeonLootManager.AdminGiveItemResult(true, AccessoryItemCodec.buildInscriptionBook(definition, source, floor, quality), "§a已生成饰品刻印书: §f${definition.name} §7(${quality.displayName})")
                }
            }
            else -> DungeonLootManager.AdminGiveItemResult(false, null, "§c未知 kind: §f$kind")
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
        val dungeons = DungeonManager.getActiveDungeons()
        if (dungeons.isEmpty()) {
            sender.sendMessage("§7当前没有活跃副本。")
            return
        }
        sender.sendMessage("§6===== 活跃副本 ${dungeons.size} 个 =====")
        for (dungeon in dungeons) {
            val names = dungeon.players.map { uuid -> Bukkit.getPlayer(uuid)?.name ?: Bukkit.getOfflinePlayer(uuid).name ?: uuid.toString() }
            sender.sendMessage("§e${dungeon.id} §7- Floor §f${dungeon.config.floorNumber} §8(${dungeon.config.theme.name}) §7玩家:${dungeon.players.size} §8[${names.joinToString(", ")}]")
        }
    }

    private fun destroyDungeon(sender: CommandSender, dungeonId: String?) {
        if (dungeonId.isNullOrBlank()) {
            sender.sendMessage("§c用法: /rogue admin destroy <dungeonId>")
            return
        }
        val dungeon = DungeonManager.getDungeonById(dungeonId)
        if (dungeon == null) {
            sender.sendMessage("§c未找到副本: §f$dungeonId")
            return
        }
        val playerCount = dungeon.players.size
        val floor = dungeon.config.floorNumber
        DungeonManager.destroyDungeon(dungeon.id)
        sender.sendMessage("§a已销毁副本 §f${dungeon.id} §7(Floor $floor, 玩家 $playerCount)")
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
        sender.sendMessage("§a已强制让 §f${target.name} §a离开副本 §f${dungeon.id}")
        target.sendMessage("§c管理员已强制将你移出副本。")
    }

    private fun showDungeonInfo(player: Player) {
        val dungeon = DungeonManager.getPlayerDungeon(player)
        if (dungeon == null) {
            player.sendMessage("§c你不在地牢中!")
            return
        }
        player.sendMessage("§6===== 地牢信息 =====")
        player.sendMessage("§eID: §f${dungeon.id}")
        player.sendMessage("§e主题: §f${dungeon.config.theme.name}")
        player.sendMessage("§e房间数: §f${dungeon.rooms.size}")
        player.sendMessage("§e玩家数: §f${dungeon.players.size}")
        for (room in dungeon.rooms) {
            val stateColor = when (room.state.name) {
                "CLEARED" -> "§a"
                "ACTIVE" -> "§c"
                else -> "§7"
            }
            player.sendMessage("  ${stateColor}#${room.id} §f${room.type.displayName} ${stateColor}[${room.state.name}]")
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
        player.sendMessage("§a新地牢 §f${instance.id} §a已生成 (${instance.rooms.size}个房间)")
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
        sender.sendMessage("§e/rogue gear storage ... §7- 装备、鉴定、锻造、回收")
        sender.sendMessage("§e/rogue accessory box ... §7- 饰品匣、饰品鉴定、饰品刻印")
        sender.sendMessage("§e/rogue progress ... §7- 天赋、研究、收藏、图鉴、引导")
        sender.sendMessage("§e/rogue admin ... §7- 管理与测试")
    }

    private fun showRunHelp(sender: CommandSender, unknown: String = "") {
        if (unknown.isNotBlank()) sender.sendMessage("§c未知冒险操作: §f$unknown")
        sender.sendMessage("§6冒险: §e/rogue run enter [floor]§7, leave, rejoin, route, build, modifiers, milestones, summary")
    }

    private fun showPartyHelp(sender: CommandSender, unknown: String = "") {
        if (unknown.isNotBlank()) sender.sendMessage("§c未知队伍操作: §f$unknown")
        sender.sendMessage("§6队伍: §e/rogue party create§7, invite <player>, accept, quit, kick <player>, disband, list")
    }

    private fun showGearHelp(sender: CommandSender, unknown: String = "") {
        if (unknown.isNotBlank()) sender.sendMessage("§c未知装备操作: §f$unknown")
        sender.sendMessage("§6装备: §e/rogue gear storage§7, forge, identify, craft, salvage, materials, workshop")
    }

    private fun showAccessoryHelp(sender: CommandSender, unknown: String = "") {
        if (unknown.isNotBlank()) sender.sendMessage("§c未知饰品操作: §f$unknown")
        sender.sendMessage("§6饰品: §e/rogue accessory box§7, workshop, identify, inscribe")
    }

    private fun showProgressHelp(sender: CommandSender, unknown: String = "") {
        if (unknown.isNotBlank()) sender.sendMessage("§c未知成长操作: §f$unknown")
        sender.sendMessage("§6成长: §e/rogue progress talent§7, unlocks, collection, codex, stats, guide")
    }

    private fun showAdminHelp(sender: CommandSender) {
        sender.sendMessage("§6===== RogueCore 管理命令 =====")
        sender.sendMessage("§e/rogue admin audit §7- 内容自检")
        sender.sendMessage("§e/rogue admin stats [reset] §7- 平衡统计")
        sender.sendMessage("§e/rogue admin reload §7- 重载配置")
        sender.sendMessage("§e/rogue admin dungeons/destroy/forceleave §7- 副本管理")
        sender.sendMessage("§e/rogue admin shards/unlock/talentset/reset §7- 玩家数据")
        sender.sendMessage("§e/rogue admin give <player> <kind> <id> [source] [floor] [extra] §7- 给测试物品")
    }

    private fun runContentAudit(sender: CommandSender) {
        val result = ContentAuditManager.run()
        for (line in ContentAuditManager.format(result)) sender.sendMessage(line)
    }

    private data class ResolvedTarget(val uuid: UUID, val name: String, val player: Player? = null)

    private fun resolveTarget(name: String): ResolvedTarget? {
        Bukkit.getPlayerExact(name)?.let { player -> return ResolvedTarget(player.uniqueId, player.name, player) }
        val offline = Bukkit.getOfflinePlayers().firstOrNull { it.name?.equals(name, ignoreCase = true) == true } ?: return null
        return ResolvedTarget(offline.uniqueId, offline.name ?: name)
    }
}
