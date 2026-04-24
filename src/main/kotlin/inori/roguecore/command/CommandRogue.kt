package inori.roguecore.command

import inori.roguecore.affix.AffixRegistry
import inori.roguecore.boon.BoonRegistry
import inori.roguecore.boon.PlayerBoonData
import inori.roguecore.combat.MonsterConfig
import inori.roguecore.curse.RunCurseManager
import inori.roguecore.data.PlayerDataManager
import inori.roguecore.data.ShardRewardManager
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
            sender.sendMessage("§a配置文件已重载")
        }
    }
}
