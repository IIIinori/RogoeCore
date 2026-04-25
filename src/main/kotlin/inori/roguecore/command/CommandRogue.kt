package inori.roguecore.command

import inori.roguecore.affix.AffixRegistry
import inori.roguecore.boon.BoonRegistry
import inori.roguecore.boon.PlayerBoonData
import inori.roguecore.combat.MonsterConfig
import inori.roguecore.curse.RunCurseManager
import inori.roguecore.data.PlayerDataManager
import inori.roguecore.data.ShardRewardManager
import inori.roguecore.dependency.DependencySelfCheckManager
import inori.roguecore.dungeon.DungeonManager
import inori.roguecore.dungeon.floor.FloorManager
import inori.roguecore.dungeon.generator.RoomTypeWeights
import inori.roguecore.event.ChestEvent
import inori.roguecore.event.ContractEvent
import inori.roguecore.event.ExtractionEvent
import inori.roguecore.event.GambleEvent
import inori.roguecore.event.ForgeEvent
import inori.roguecore.event.HiddenEvent
import inori.roguecore.event.RestEvent
import inori.roguecore.party.PartyManager
import inori.roguecore.event.ShrineEvent
import inori.roguecore.event.ShopEvent
import inori.roguecore.event.TrialEvent
import inori.roguecore.item.DungeonLootManager
import inori.roguecore.relic.RelicRegistry
import inori.roguecore.relic.PlayerRelicData
import inori.roguecore.talent.TalentManager
import inori.roguecore.talent.TalentRegistry
import inori.roguecore.ui.TalentUI
import inori.roguecore.ui.CodexUI
import inori.roguecore.ui.UnlockUI
import inori.roguecore.unlock.UnlockManager
import inori.roguecore.unlock.UnlockRegistry
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.subCommand
import taboolib.expansion.createHelper
import java.util.UUID

@CommandHeader("rogue", permission = "roguecore.admin")
object CommandRogue {

    @CommandBody
    val main = mainCommand {
        createHelper()
    }

    // ==================== 副本命令 ====================

    @CommandBody(description = "进入地牢(有队伍则全队进入)")
    val enter = subCommand {
        dynamic("floor", optional = true) {
            suggestion<Player> { _, _ -> (1..15).map { it.toString() } }
            execute<Player> { sender, _, argument ->
                val floor = argument.toIntOrNull() ?: 1
                enterDungeon(sender, floor)
            }
        }
        execute<Player> { sender, _, _ ->
            enterDungeon(sender, 1)
        }
    }

    private fun enterDungeon(player: Player, floor: Int) {
        if (DungeonManager.isInDungeon(player)) {
            player.sendMessage("§c你已经在地牢中了! 使用 /rogue leave 离开")
            return
        }

        val party = PartyManager.getParty(player)
        val config = FloorManager.getFloorConfig(floor)

        if (party != null) {
            // 有队伍
            if (!party.isLeader(player.uniqueId)) {
                player.sendMessage("§c只有队长才能开启副本!")
                return
            }
            // 检查队员是否都在线且不在副本中
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
            // 单人
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

    @CommandBody(description = "离开地牢")
    val leave = subCommand {
        execute<Player> { sender, _, _ ->
            if (!DungeonManager.isInDungeon(sender)) {
                sender.sendMessage("§c你不在地牢中!")
                return@execute
            }
            DungeonManager.leaveDungeon(sender)
            sender.sendMessage("§a已离开地牢")
        }
    }

    @CommandBody(description = "重连返回未结束的副本")
    val rejoin = subCommand {
        execute<Player> { sender, _, _ ->
            if (DungeonManager.isInDungeon(sender)) {
                sender.sendMessage("§e你已经在副本中，无需重连。")
                return@execute
            }
            if (!DungeonManager.rejoinDungeon(sender)) {
                sender.sendMessage("§c没有可重连的副本，或该副本已经结束。")
            }
        }
    }

    // ==================== 队伍命令 ====================

    @CommandBody(description = "队伍 - 创建")
    val create = subCommand {
        execute<Player> { sender, _, _ ->
            PartyManager.createParty(sender)
        }
    }

    @CommandBody(description = "队伍 - 邀请玩家")
    val invite = subCommand {
        dynamic("player") {
            suggestion<Player> { _, _ -> Bukkit.getOnlinePlayers().map { it.name } }
            execute<Player> { sender, _, argument ->
                PartyManager.invitePlayer(sender, argument)
            }
        }
    }

    @CommandBody(description = "队伍 - 接受邀请")
    val accept = subCommand {
        execute<Player> { sender, _, _ ->
            PartyManager.acceptInvite(sender)
        }
    }

    @CommandBody(description = "队伍 - 离开队伍")
    val quit = subCommand {
        execute<Player> { sender, _, _ ->
            PartyManager.leaveParty(sender)
        }
    }

    @CommandBody(description = "队伍 - 踢出玩家")
    val kick = subCommand {
        dynamic("player") {
            suggestion<Player> { sender, _ ->
                val party = PartyManager.getParty(sender)
                if (party != null) {
                    party.members.mapNotNull { Bukkit.getPlayer(it)?.name }.filter { it != sender.name }
                } else emptyList()
            }
            execute<Player> { sender, _, argument ->
                PartyManager.kickPlayer(sender, argument)
            }
        }
    }

    @CommandBody(description = "队伍 - 解散")
    val disband = subCommand {
        execute<Player> { sender, _, _ ->
            PartyManager.disbandParty(sender)
        }
    }

    @CommandBody(description = "队伍 - 查看成员")
    val list = subCommand {
        execute<Player> { sender, _, _ ->
            val party = PartyManager.getParty(sender)
            if (party == null) {
                sender.sendMessage("§c你不在任何队伍中!")
                return@execute
            }
            sender.sendMessage("§6===== 队伍信息 §7(${party.size}/${party.maxSize}) §6=====")
            for (uuid in party.members) {
                val name = Bukkit.getPlayer(uuid)?.name ?: Bukkit.getOfflinePlayer(uuid).name ?: uuid.toString()
                val tag = if (party.isLeader(uuid)) " §6[队长]" else ""
                val online = if (Bukkit.getPlayer(uuid) != null) "§a" else "§7"
                sender.sendMessage("  $online$name$tag")
            }
            if (party.dungeonId != null) {
                sender.sendMessage("§e当前副本: §f${party.dungeonId}")
            }
        }
    }

    // ==================== 其他命令 ====================

    @CommandBody(description = "查看个人统计")
    val stats = subCommand {
        execute<Player> { sender, _, _ ->
            val data = PlayerDataManager.get(sender.uniqueId)
            sender.sendMessage("§6===== 个人统计 =====")
            sender.sendMessage("§e灵魂碎片: §6${data.soulShards}")
            sender.sendMessage("§e总运行次数: §f${data.totalRuns}")
            sender.sendMessage("§e总通关次数: §f${data.totalClears}")
            sender.sendMessage("§e最高楼层: §f${data.bestFloor}")
            sender.sendMessage("§e总击杀数: §f${data.totalKills}")

            val runShards = ShardRewardManager.getRunShards(sender.uniqueId)
            if (runShards > 0) {
                sender.sendMessage("§e当前本局碎片: §6$runShards")
                sender.sendMessage("§e当前结算预览: §6${ShardRewardManager.getSettlementPreview(sender.uniqueId)} §e灵魂碎片")
            }

            val boons = PlayerBoonData.getBoons(sender)
            if (boons.isNotEmpty()) {
                sender.sendMessage("§e当前神恩:")
                for (b in boons) {
                    val tags = if (b.boon.tags.isNotEmpty()) " §8(${b.boon.tags.joinToString("/")})" else ""
                    sender.sendMessage("  ${b.boon.rarity.color}${b.boon.name} §eLv.${b.level}$tags")
                }
            }

            val curses = RunCurseManager.getCurses(sender)
            if (curses.isNotEmpty()) {
                sender.sendMessage("§4当前契约诅咒:")
                for (curse in curses) {
                    sender.sendMessage("  §c${curse.displayName} §7- ${curse.description}")
                }
            }

            val relics = PlayerRelicData.getRelics(sender)
            if (relics.isNotEmpty()) {
                sender.sendMessage("§d当前遗物:")
                for (relic in relics) {
                    sender.sendMessage("  ${relic.rarity.color}${relic.name} §7- ${relic.description}")
                }
            }

            val unlocks = UnlockManager.getUnlockedIds(sender.uniqueId)
            if (unlocks.isNotEmpty()) {
                sender.sendMessage("§d局外研究: §f${unlocks.size} 项已完成")
            }
        }
    }

    @CommandBody(description = "打开天赋树")
    val talent = subCommand {
        execute<Player> { sender, _, _ ->
            TalentUI.open(sender)
        }
    }

    @CommandBody(description = "打开研究所")
    val unlocks = subCommand {
        execute<Player> { sender, _, _ ->
            UnlockUI.open(sender)
        }
    }

    @CommandBody(description = "打开冒险图鉴")
    val codex = subCommand {
        execute<Player> { sender, _, _ ->
            CodexUI.open(sender)
        }
    }

    // ==================== 管理员命令 ====================

    @CommandBody(description = "管理员 - 查看所有活跃副本", permission = "roguecore.admin")
    val dungeons = subCommand {
        execute<CommandSender> { sender, _, _ ->
            val dungeons = DungeonManager.getActiveDungeons()
            if (dungeons.isEmpty()) {
                sender.sendMessage("§7当前没有活跃副本。")
                return@execute
            }
            sender.sendMessage("§6===== 活跃副本 ${dungeons.size} 个 =====")
            for (dungeon in dungeons) {
                val names = dungeon.players.map { uuid ->
                    Bukkit.getPlayer(uuid)?.name ?: Bukkit.getOfflinePlayer(uuid).name ?: uuid.toString()
                }
                val playerSummary = if (names.isEmpty()) "无" else names.joinToString(", ")
                sender.sendMessage("§e${dungeon.id} §7- Floor §f${dungeon.config.floorNumber} §8(${dungeon.config.theme.name}) §7玩家:${dungeon.players.size} §8[$playerSummary]")
            }
        }
    }

    @CommandBody(description = "管理员 - 销毁指定副本", permission = "roguecore.admin")
    val destroy = subCommand {
        dynamic("dungeonId") {
            suggestion<CommandSender> { _, _ -> DungeonManager.getActiveDungeons().map { it.id } }
            execute<CommandSender> { sender, _, argument ->
                val dungeon = DungeonManager.getDungeonById(argument)
                if (dungeon == null) {
                    sender.sendMessage("§c未找到副本: §f$argument")
                    return@execute
                }
                val playerCount = dungeon.players.size
                val floor = dungeon.config.floorNumber
                DungeonManager.destroyDungeon(dungeon.id)
                sender.sendMessage("§a已销毁副本 §f${dungeon.id} §7(Floor $floor, 玩家 $playerCount)")
            }
        }
    }

    @CommandBody(description = "管理员 - 强制让玩家离开副本", permission = "roguecore.admin")
    val forceleave = subCommand {
        dynamic("player") {
            suggestion<CommandSender> { _, _ -> Bukkit.getOnlinePlayers().map { it.name } }
            execute<CommandSender> { sender, _, argument ->
                val target = Bukkit.getPlayerExact(argument)
                if (target == null) {
                    sender.sendMessage("§c玩家不在线: §f$argument")
                    return@execute
                }
                val dungeon = DungeonManager.getPlayerDungeon(target)
                if (dungeon == null) {
                    sender.sendMessage("§c${target.name} 当前不在副本中。")
                    return@execute
                }
                DungeonManager.leaveDungeon(target)
                sender.sendMessage("§a已强制让 §f${target.name} §a离开副本 §f${dungeon.id}")
                target.sendMessage("§c管理员已强制将你移出副本。")
            }
        }
    }

    @CommandBody(description = "管理员 - 管理永久灵魂碎片", permission = "roguecore.admin")
    val shards = subCommand {
        dynamic("action") {
            suggestion<CommandSender> { _, _ -> listOf("give", "take", "set") }
            dynamic("player") {
                suggestion<CommandSender> { _, _ -> Bukkit.getOnlinePlayers().map { it.name } }
                dynamic("amount") {
                    suggestion<CommandSender> { _, _ -> listOf("10", "50", "100", "500", "1000") }
                    execute<CommandSender> { sender, context, argument ->
                        val action = context["action"].lowercase()
                        val playerName = context["player"]
                        val target = resolveTarget(playerName)
                        if (target == null) {
                            sender.sendMessage("§c未找到玩家: §f$playerName §7(需在线或至少进服一次)")
                            return@execute
                        }
                        val amount = argument.toIntOrNull()
                        if (amount == null || amount < 0) {
                            sender.sendMessage("§c金额必须是大于等于 0 的整数。")
                            return@execute
                        }
                        when (action) {
                            "give" -> {
                                PlayerDataManager.addSoulShards(target.uuid, amount)
                                sender.sendMessage("§a已为 §f${target.name} §a增加 §6$amount §a灵魂碎片。")
                            }
                            "take" -> {
                                if (!PlayerDataManager.takeSoulShards(target.uuid, amount)) {
                                    sender.sendMessage("§c${target.name} 的灵魂碎片不足，无法扣除 §6$amount§c。")
                                    return@execute
                                }
                                sender.sendMessage("§a已从 §f${target.name} §a扣除 §6$amount §a灵魂碎片。")
                            }
                            "set" -> {
                                PlayerDataManager.setSoulShards(target.uuid, amount)
                                sender.sendMessage("§a已将 §f${target.name} §a的灵魂碎片设置为 §6$amount§a。")
                            }
                            else -> sender.sendMessage("§c未知操作: §f$action")
                        }
                    }
                }
            }
        }
    }

    @CommandBody(description = "管理员 - 直接授予研究", permission = "roguecore.admin")
    val unlock = subCommand {
        dynamic("player") {
            suggestion<CommandSender> { _, _ -> Bukkit.getOnlinePlayers().map { it.name } }
            dynamic("unlockId") {
                suggestion<CommandSender> { _, _ -> UnlockRegistry.getAll().map { it.id }.sorted() }
                execute<CommandSender> { sender, context, argument ->
                    val playerName = context["player"]
                    val target = resolveTarget(playerName)
                    if (target == null) {
                        sender.sendMessage("§c未找到玩家: §f$playerName §7(需在线或至少进服一次)")
                        return@execute
                    }
                    val unlock = UnlockRegistry.get(argument)
                    if (unlock == null) {
                        sender.sendMessage("§c未找到研究: §f$argument")
                        return@execute
                    }
                    if (!UnlockManager.grantUnlock(target.uuid, unlock.id)) {
                        sender.sendMessage("§c${target.name} 已拥有研究 §f${unlock.name}§c。")
                        return@execute
                    }
                    sender.sendMessage("§a已为 §f${target.name} §a授予研究 §d${unlock.name}§a。")
                }
            }
        }
    }

    @CommandBody(description = "管理员 - 设置玩家天赋等级", permission = "roguecore.admin")
    val talentset = subCommand {
        dynamic("player") {
            suggestion<CommandSender> { _, _ -> Bukkit.getOnlinePlayers().map { it.name } }
            dynamic("talentId") {
                suggestion<CommandSender> { _, _ -> TalentRegistry.getAll().map { it.id }.sorted() }
                dynamic("level") {
                    suggestion<CommandSender> { _, context ->
                        val talent = TalentRegistry.get(context["talentId"])
                        if (talent == null) emptyList() else (0..talent.maxLevel).map { it.toString() }
                    }
                    execute<CommandSender> { sender, context, argument ->
                        val playerName = context["player"]
                        val target = resolveTarget(playerName)
                        if (target == null) {
                            sender.sendMessage("§c未找到玩家: §f$playerName §7(需在线或至少进服一次)")
                            return@execute
                        }
                        val talent = TalentRegistry.get(context["talentId"])
                        if (talent == null) {
                            sender.sendMessage("§c未找到天赋: §f${context["talentId"]}")
                            return@execute
                        }
                        val level = argument.toIntOrNull()
                        if (level == null || level !in 0..talent.maxLevel) {
                            sender.sendMessage("§c等级必须在 0-${talent.maxLevel} 之间。")
                            return@execute
                        }
                        TalentManager.setTalentLevel(target.uuid, talent.id, level)
                        target.player?.takeIf { DungeonManager.isInDungeon(it) }?.let {
                            TalentManager.removeTalents(it)
                            TalentManager.applyTalents(it)
                        }
                        sender.sendMessage("§a已将 §f${target.name} §a的天赋 §f${talent.name} §a设置为 Lv.$level。")
                    }
                }
            }
        }
    }

    @CommandBody(description = "管理员 - 重置玩家数据", permission = "roguecore.admin")
    val reset = subCommand {
        dynamic("player") {
            suggestion<CommandSender> { _, _ -> Bukkit.getOnlinePlayers().map { it.name } }
            execute<CommandSender> { sender, _, argument ->
                val target = resolveTarget(argument)
                if (target == null) {
                    sender.sendMessage("§c未找到玩家: §f$argument §7(需在线或至少进服一次)")
                    return@execute
                }
                target.player?.let { online ->
                    if (DungeonManager.isInDungeon(online)) {
                        DungeonManager.leaveDungeon(online)
                    }
                }
                TalentManager.clearAll(target.uuid)
                UnlockManager.clearAll(target.uuid)
                PlayerDataManager.reset(target.uuid)
                sender.sendMessage("§a已重置玩家 §f${target.name} §a的永久数据、研究与天赋。")
                target.player?.sendMessage("§c管理员已重置你的 RogueCore 进度。")
            }
        }
    }

    @CommandBody(description = "查看地牢信息(调试)", permission = "roguecore.admin")
    val info = subCommand {
        execute<Player> { sender, _, _ ->
            val dungeon = DungeonManager.getPlayerDungeon(sender)
            if (dungeon == null) {
                sender.sendMessage("§c你不在地牢中!")
                return@execute
            }
            sender.sendMessage("§6===== 地牢信息 =====")
            sender.sendMessage("§eID: §f${dungeon.id}")
            sender.sendMessage("§e主题: §f${dungeon.config.theme.name}")
            sender.sendMessage("§e房间数: §f${dungeon.rooms.size}")
            sender.sendMessage("§e玩家数: §f${dungeon.players.size}")
            sender.sendMessage("§e房间列表:")
            for (room in dungeon.rooms) {
                val stateColor = when (room.state.name) {
                    "CLEARED" -> "§a"
                    "ACTIVE" -> "§c"
                    else -> "§7"
                }
                sender.sendMessage("  ${stateColor}#${room.id} §f${room.type.displayName} ${stateColor}[${room.state.name}]")
            }
            if (dungeon.affixes.isNotEmpty()) {
                sender.sendMessage("§e副本词缀:")
                for (affix in dungeon.affixes) {
                    sender.sendMessage("  ${affix.name} §7- ${affix.description}")
                }
            }
            sender.sendMessage("§e隐藏钥匙: §b${dungeon.getHiddenKeys()}")
        }
    }

    @CommandBody(description = "重新生成地牢(调试)", permission = "roguecore.admin")
    val regen = subCommand {
        execute<Player> { sender, _, _ ->
            if (DungeonManager.isInDungeon(sender)) {
                DungeonManager.leaveDungeon(sender)
            }
            sender.sendMessage("§e正在重新生成地牢...")
            val config = FloorManager.getFloorConfig(1)
            val instance = DungeonManager.createDungeon(config)
            if (instance == null) {
                sender.sendMessage("§c地牢创建失败!")
                return@execute
            }
            DungeonManager.joinDungeon(sender, instance.id)
            sender.sendMessage("§a新地牢 §f${instance.id} §a已生成 (${instance.rooms.size}个房间)")
        }
    }

    @CommandBody(description = "重载配置文件", permission = "roguecore.admin")
    val reload = subCommand {
        execute<CommandSender> { sender, _, _ ->
            FloorManager.config.reload()
            FloorManager.load()
            BoonRegistry.config.reload()
            BoonRegistry.load()
            RelicRegistry.config.reload()
            RelicRegistry.load()
            UnlockRegistry.config.reload()
            UnlockRegistry.load()
            MonsterConfig.config.reload()
            MonsterConfig.load()
            RoomTypeWeights.config.reload()
            RoomTypeWeights.load()
            TalentRegistry.config.reload()
            TalentRegistry.load()
            ShardRewardManager.config.reload()
            ShardRewardManager.load()
            DungeonLootManager.config.reload()
            DungeonLootManager.load()
            ChestEvent.config.reload()
            RestEvent.config.reload()
            ShopEvent.config.reload()
            ForgeEvent.config.reload()
            ExtractionEvent.config.reload()
            ContractEvent.config.reload()
            RunCurseManager.config.reload()
            TrialEvent.config.reload()
            GambleEvent.config.reload()
            ShrineEvent.config.reload()
            HiddenEvent.config.reload()
            AffixRegistry.config.reload()
            AffixRegistry.load()
            PartyManager.load()
            DependencySelfCheckManager.runCheck(sender)
            sender.sendMessage("§a配置文件已重载")
        }
    }

    private data class ResolvedTarget(
        val uuid: UUID,
        val name: String,
        val player: Player? = null
    )

    private fun resolveTarget(name: String): ResolvedTarget? {
        Bukkit.getPlayerExact(name)?.let { player ->
            return ResolvedTarget(player.uniqueId, player.name, player)
        }
        val offline = Bukkit.getOfflinePlayers().firstOrNull { it.name?.equals(name, ignoreCase = true) == true } ?: return null
        return ResolvedTarget(offline.uniqueId, offline.name ?: name)
    }
}
