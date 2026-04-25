package inori.roguecore.ui

import inori.roguecore.boon.PlayerBoonData
import inori.roguecore.combat.RoomCombatManager
import inori.roguecore.data.ShardRewardManager
import inori.roguecore.dungeon.DungeonInstance
import inori.roguecore.dungeon.DungeonManager
import inori.roguecore.relic.PlayerRelicData
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle
import org.bukkit.boss.BossBar
import org.bukkit.entity.Player
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.scoreboard.Objective
import org.bukkit.scoreboard.Scoreboard
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.adaptPlayer
import taboolib.common.platform.function.submit
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 副本 HUD 管理器。
 *
 * - Scoreboard: 常驻状态
 * - BossBar: 当前目标
 * - ActionBar: 短时反馈
 */
object DungeonHudManager {

    private data class HudSession(
        val previousScoreboard: Scoreboard,
        val scoreboard: Scoreboard,
        val objective: Objective,
        val bossBar: BossBar
    )

    private data class TimedActionBar(
        val message: String,
        val expireAt: Long
    )

    private val sessions = ConcurrentHashMap<UUID, HudSession>()
    private val actionBars = ConcurrentHashMap<UUID, TimedActionBar>()

    @Awake(LifeCycle.ENABLE)
    fun startRenderer() {
        submit(period = 10L) {
            tick()
        }
    }

    @Awake(LifeCycle.DISABLE)
    fun shutdown() {
        for ((uuid, session) in sessions) {
            Bukkit.getPlayer(uuid)?.scoreboard = session.previousScoreboard
            session.bossBar.removeAll()
        }
        sessions.clear()
        actionBars.clear()
    }

    fun attach(player: Player) {
        if (sessions.containsKey(player.uniqueId)) {
            return
        }
        val manager = Bukkit.getScoreboardManager() ?: return
        val scoreboard = manager.newScoreboard
        val objective = scoreboard.registerNewObjective("roguecore", "dummy")
        objective.displaySlot = DisplaySlot.SIDEBAR
        objective.displayName = "§6§lRogueCore"

        val bossBar = Bukkit.createBossBar("§6RogueCore", BarColor.BLUE, BarStyle.SOLID)
        bossBar.addPlayer(player)
        bossBar.isVisible = true

        sessions[player.uniqueId] = HudSession(
            previousScoreboard = player.scoreboard,
            scoreboard = scoreboard,
            objective = objective,
            bossBar = bossBar
        )
        player.scoreboard = scoreboard
    }

    fun detach(player: Player) {
        val session = sessions.remove(player.uniqueId) ?: return
        session.bossBar.removeAll()
        actionBars.remove(player.uniqueId)
        if (player.isOnline) {
            player.scoreboard = session.previousScoreboard
        }
    }

    fun pushActionBar(player: Player, message: String, durationTicks: Long = 40L) {
        if (message.isBlank() || !DungeonManager.isInDungeon(player)) {
            return
        }
        actionBars[player.uniqueId] = TimedActionBar(message, System.currentTimeMillis() + durationTicks * 50L)
    }

    fun pushActionBar(uuid: UUID, message: String, durationTicks: Long = 40L) {
        val player = Bukkit.getPlayer(uuid) ?: return
        pushActionBar(player, message, durationTicks)
    }

    private fun tick() {
        val onlinePlayers = Bukkit.getOnlinePlayers()
        val onlineIds = onlinePlayers.mapTo(hashSetOf()) { it.uniqueId }

        for (player in onlinePlayers) {
            if (DungeonManager.isInDungeon(player)) {
                attach(player)
                render(player)
            } else {
                detach(player)
            }
        }

        val offline = sessions.keys.filter { it !in onlineIds }
        for (uuid in offline) {
            sessions.remove(uuid)?.bossBar?.removeAll()
            actionBars.remove(uuid)
        }
    }

    private fun render(player: Player) {
        val session = sessions[player.uniqueId] ?: return
        val instance = DungeonManager.getPlayerDungeon(player) ?: return
        renderSidebar(session, player, instance)
        renderBossBar(session, player, instance)
        renderActionBar(player)
    }

    private fun renderSidebar(session: HudSession, player: Player, instance: DungeonInstance) {
        val scoreboard = session.scoreboard
        val objective = session.objective
        scoreboard.entries.forEach(scoreboard::resetScores)
        objective.displayName = buildSidebarTitle(instance)

        val lines = uniquify(buildSidebarLines(player, instance).take(15))
        val maxScore = lines.size
        lines.forEachIndexed { index, line ->
            objective.getScore(trimLine(line)).score = maxScore - index
        }

        if (player.scoreboard !== scoreboard) {
            player.scoreboard = scoreboard
        }
    }

    private fun buildSidebarLines(player: Player, instance: DungeonInstance): List<String> {
        val currentRoom = RoomCombatManager.getPlayerRoom(player)
        val activeRoom = RoomCombatManager.getActiveRoom(instance)
        val runShards = ShardRewardManager.getRunShards(player.uniqueId)
        val boonCount = PlayerBoonData.getBoons(player).size
        val relicCount = PlayerRelicData.getRelics(player).size
        val (clearedCombat, totalCombat) = RoomCombatManager.getCombatProgress(instance)
        val roomName = currentRoom?.type?.displayName ?: "走廊"
        val roomColor = currentRoom?.type?.let(::roomColor) ?: "§7"
        val status = when {
            instance.completed -> "§a已通关"
            activeRoom != null -> "${roomColor(activeRoom.type)}${activeRoom.type.displayName}中"
            else -> "§b探索中"
        }
        val target = when {
            instance.completed -> "§6选择去留"
            activeRoom != null -> {
                val total = activeRoom.spawnedMobCount.coerceAtLeast(activeRoom.aliveMobs.size).coerceAtLeast(1)
                val cleared = (total - activeRoom.aliveMobs.size).coerceAtLeast(0)
                "§e$cleared/$total"
            }
            else -> "§7寻找下一房间"
        }

        return buildList {
            add("§8§m--------------")
            add("§7层数 §f${instance.config.floorNumber}")
            add("§7区域 ${roomColor}$roomName")
            add("§7态势 $status")
            add("§7目标 $target")
            add("§8§m--------------§r")
            add("§6碎片 §f$runShards")
            add("§b钥匙 §f${instance.getHiddenKeys()}")
            add("§d构筑 §fB$boonCount / R$relicCount")
            add("§a推进 §f$clearedCombat/$totalCombat")
            add("§6战词 ${summarizeAffixes(instance.affixes.map { it.name })}")
            add("§d事件 ${summarizeAffixes(instance.eventAffixes.map { it.name })}")
        }
    }

    private fun renderBossBar(session: HudSession, player: Player, instance: DungeonInstance) {
        val bossBar = session.bossBar
        if (!bossBar.players.contains(player)) {
            bossBar.addPlayer(player)
        }
        bossBar.isVisible = true

        when {
            instance.completed -> {
                bossBar.color = BarColor.GREEN
                bossBar.style = BarStyle.SEGMENTED_10
                bossBar.progress = 1.0
                bossBar.setTitle("§6副本已通关：选择下一步")
            }
            RoomCombatManager.getActiveRoom(instance) != null -> {
                val room = RoomCombatManager.getActiveRoom(instance) ?: return
                val total = room.spawnedMobCount.coerceAtLeast(room.aliveMobs.size).coerceAtLeast(1)
                val alive = room.aliveMobs.size.coerceAtLeast(0)
                val cleared = (total - alive).coerceAtLeast(0)
                bossBar.color = bossBarColor(room.type)
                bossBar.style = bossBarStyle(room.type)
                bossBar.progress = (cleared.toDouble() / total.toDouble()).coerceIn(0.0, 1.0)
                bossBar.setTitle(buildActiveBossBarTitle(room.type, cleared, total, alive))
            }
            else -> {
                val (clearedCombat, totalCombat) = RoomCombatManager.getCombatProgress(instance)
                bossBar.color = BarColor.BLUE
                bossBar.style = BarStyle.SEGMENTED_10
                bossBar.progress = if (totalCombat <= 0) 0.0 else (clearedCombat.toDouble() / totalCombat.toDouble()).coerceIn(0.0, 1.0)
                bossBar.setTitle("§b探索中：已清理战斗房 $clearedCombat/$totalCombat")
            }
        }
    }

    private fun renderActionBar(player: Player) {
        val state = actionBars[player.uniqueId] ?: return
        if (System.currentTimeMillis() >= state.expireAt) {
            actionBars.remove(player.uniqueId)
            return
        }
        adaptPlayer(player).sendActionBar(state.message)
    }

    private fun buildSidebarTitle(instance: DungeonInstance): String {
        val activeRoom = RoomCombatManager.getActiveRoom(instance)
        return when {
            instance.completed -> "§6§l通关抉择"
            activeRoom?.type == inori.roguecore.dungeon.room.RoomType.BOSS -> "§5§lBoss 讨伐"
            activeRoom?.type == inori.roguecore.dungeon.room.RoomType.ELITE -> "§c§l精英交战"
            activeRoom != null -> "§6§l战斗进行中"
            else -> "§b§l地牢推进"
        }
    }

    private fun bossBarColor(type: inori.roguecore.dungeon.room.RoomType): BarColor {
        return when (type) {
            inori.roguecore.dungeon.room.RoomType.BOSS -> BarColor.PURPLE
            inori.roguecore.dungeon.room.RoomType.ELITE -> BarColor.RED
            else -> BarColor.YELLOW
        }
    }

    private fun bossBarStyle(type: inori.roguecore.dungeon.room.RoomType): BarStyle {
        return when (type) {
            inori.roguecore.dungeon.room.RoomType.BOSS -> BarStyle.SEGMENTED_20
            inori.roguecore.dungeon.room.RoomType.ELITE -> BarStyle.SEGMENTED_12
            else -> BarStyle.SEGMENTED_10
        }
    }

    private fun buildActiveBossBarTitle(
        type: inori.roguecore.dungeon.room.RoomType,
        cleared: Int,
        total: Int,
        alive: Int
    ): String {
        return when (type) {
            inori.roguecore.dungeon.room.RoomType.BOSS -> "§5Boss 讨伐：$cleared/$total §8| §d残存 $alive"
            inori.roguecore.dungeon.room.RoomType.ELITE -> "§c精英歼灭：$cleared/$total §8| §6剩余 $alive"
            else -> "§6战斗清剿：$cleared/$total §8| §e剩余 $alive"
        }
    }

    private fun roomColor(type: inori.roguecore.dungeon.room.RoomType): String {
        return when (type) {
            inori.roguecore.dungeon.room.RoomType.SPAWN -> "§a"
            inori.roguecore.dungeon.room.RoomType.COMBAT -> "§6"
            inori.roguecore.dungeon.room.RoomType.ELITE -> "§c"
            inori.roguecore.dungeon.room.RoomType.BOSS -> "§5"
            inori.roguecore.dungeon.room.RoomType.SHOP -> "§e"
            inori.roguecore.dungeon.room.RoomType.FORGE -> "§6"
            inori.roguecore.dungeon.room.RoomType.CHEST -> "§b"
            inori.roguecore.dungeon.room.RoomType.REST -> "§3"
            inori.roguecore.dungeon.room.RoomType.EXTRACTION -> "§b"
            inori.roguecore.dungeon.room.RoomType.CONTRACT -> "§4"
            inori.roguecore.dungeon.room.RoomType.HIDDEN -> "§9"
            inori.roguecore.dungeon.room.RoomType.TRIAL -> "§d"
            inori.roguecore.dungeon.room.RoomType.GAMBLE -> "§2"
            inori.roguecore.dungeon.room.RoomType.SHRINE -> "§f"
        }
    }

    private fun summarizeAffixes(names: List<String>): String {
        if (names.isEmpty()) {
            return "§8无"
        }
        val stripped = names.mapNotNull { ChatColor.stripColor(it)?.trim()?.takeIf(String::isNotEmpty) }
        val main = stripped.take(2).joinToString("/") { it.take(6) }
        val extra = stripped.size - 2
        return if (extra > 0) "§f$main§7+$extra" else "§f$main"
    }

    private fun uniquify(lines: List<String>): List<String> {
        val used = hashSetOf<String>()
        return lines.map { line ->
            var candidate = line
            while (!used.add(candidate)) {
                candidate += "§r"
            }
            candidate
        }
    }

    private fun trimLine(line: String): String {
        return if (line.length <= 40) line else line.take(40)
    }
}
