package inori.roguecore.ui

import inori.roguecore.accessory.AccessoryIdentificationTaskManager
import inori.roguecore.accessory.AccessoryInscriptionTaskManager
import inori.roguecore.balance.BalanceConfigManager
import inori.roguecore.collection.CollectionManager
import inori.roguecore.data.DatabaseManager
import inori.roguecore.data.PermanentMaterialManager
import inori.roguecore.data.PlayerDataManager
import inori.roguecore.dungeon.DungeonManager
import inori.roguecore.item.ForgeBookTaskManager
import inori.roguecore.item.GearStorageManager
import inori.roguecore.item.IdentificationTaskManager
import inori.roguecore.item.LootStorageManager
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.bukkit.scoreboard.RenderType
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.scoreboard.Criteria
import org.bukkit.scoreboard.Objective
import org.bukkit.scoreboard.Scoreboard
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.submit
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** 副本外常驻 HUD。 */
object LobbyHudManager {

    private const val SETTINGS_KEY = "settings.lobby-hud"
    private const val MAX_LINES = 15
    private val LINE_ENTRIES = arrayOf(
        "§0", "§1", "§2", "§3", "§4",
        "§5", "§6", "§7", "§8", "§9",
        "§a", "§b", "§c", "§d", "§e"
    )

    private data class LobbySession(
        val previousScoreboard: Scoreboard,
        val scoreboard: Scoreboard,
        val objective: Objective,
        var lastRender: Long = 0L,
        var lastTitle: String = "",
        val lastLines: Array<String> = Array(MAX_LINES) { "" }
    )

    private val sessions = ConcurrentHashMap<UUID, LobbySession>()

    @Awake(LifeCycle.ENABLE)
    fun startRenderer() {
        submit(period = 20L) {
            tick()
        }
    }

    @Awake(LifeCycle.DISABLE)
    fun shutdown() {
        for ((uuid, session) in sessions) {
            Bukkit.getPlayer(uuid)?.scoreboard = session.previousScoreboard
        }
        sessions.clear()
    }

    fun isEnabled(player: Player): Boolean {
        if (!BalanceConfigManager.getBoolean("hud.lobby.enabled", true)) return false
        val raw = DatabaseManager.getOrCreateContainer(player.uniqueId)[SETTINGS_KEY]
        return raw?.toBooleanStrictOrNull() ?: BalanceConfigManager.getBoolean("hud.lobby.default-enabled", true)
    }

    fun setEnabled(player: Player, enabled: Boolean) {
        DatabaseManager.getOrCreateContainer(player.uniqueId)[SETTINGS_KEY] = enabled.toString()
        if (!enabled) {
            detach(player)
        }
    }

    fun toggle(player: Player): Boolean {
        val enabled = !isEnabled(player)
        setEnabled(player, enabled)
        return enabled
    }

    fun sendStatus(player: Player) {
        player.sendMessage("§6局外侧栏: ${if (isEnabled(player)) "§a开启" else "§c关闭"}")
        player.sendMessage("§7用法: §f/rogue progress hud on/off/toggle")
    }

    private fun tick() {
        val online = Bukkit.getOnlinePlayers()
        val onlineIds = online.mapTo(hashSetOf()) { it.uniqueId }
        for (player in online) {
            if (DungeonManager.isInDungeon(player) || !isEnabled(player)) {
                detach(player)
                continue
            }
            attach(player)
            render(player)
        }
        val offline = sessions.keys.filter { it !in onlineIds }
        for (uuid in offline) {
            sessions.remove(uuid)
        }
    }

    private fun attach(player: Player) {
        if (sessions.containsKey(player.uniqueId)) return
        val manager = Bukkit.getScoreboardManager() ?: return
        val scoreboard = manager.newScoreboard
        val objective = scoreboard.registerNewObjective("rogue_lobby", Criteria.DUMMY, "§6§lRogueCore", RenderType.INTEGER)
        objective.displaySlot = DisplaySlot.SIDEBAR
        setupSidebarSlots(scoreboard, objective)
        sessions[player.uniqueId] = LobbySession(player.scoreboard, scoreboard, objective)
        player.scoreboard = scoreboard
    }

    private fun detach(player: Player) {
        val session = sessions.remove(player.uniqueId) ?: return
        if (player.isOnline) {
            player.scoreboard = session.previousScoreboard
        }
    }

    private fun render(player: Player) {
        val session = sessions[player.uniqueId] ?: return
        val now = System.currentTimeMillis()
        val periodTicks = BalanceConfigManager.getInt("hud.lobby.update-period-ticks", 40).coerceAtLeast(20)
        if (now - session.lastRender < periodTicks * 50L) return
        session.lastRender = now

        val scoreboard = session.scoreboard
        val objective = session.objective
        val title = "§6§l局外成长"
        if (session.lastTitle != title) {
            objective.displayName = title
            session.lastTitle = title
        }
        val lines = normalizeLines(buildLines(player))
        for (index in 0 until MAX_LINES) {
            val line = lines[index]
            if (session.lastLines[index] == line) {
                continue
            }
            applySidebarLine(scoreboard, index, line)
            session.lastLines[index] = line
        }
        if (player.scoreboard !== scoreboard) {
            player.scoreboard = scoreboard
        }
    }

    private fun buildLines(player: Player): List<String> {
        val data = PlayerDataManager.get(player.uniqueId)
        val materials = PermanentMaterialManager.MaterialType.entries.associateWith { PermanentMaterialManager.get(player, it) }
        val identifyDone = IdentificationTaskManager.getCompletedCount(player.uniqueId)
        val forgeDone = ForgeBookTaskManager.getCompletedCount(player.uniqueId)
        val accessoryDone = AccessoryIdentificationTaskManager.getCompletedCount(player.uniqueId) + AccessoryInscriptionTaskManager.getCompletedCount(player.uniqueId)
        val progress = CollectionManager.getProgress(player)
        val collectionDone = progress.gearCollected + progress.accessoryCollected + progress.bossCollected
        val collectionTotal = progress.gearTotal + progress.accessoryTotal + progress.bossTotal
        return buildList {
            add("§8§m--------------")
            add("§e灵魂碎片 §f${data.soulShards}")
            add("§7魂铁 §f${materials[PermanentMaterialManager.MaterialType.SOUL_IRON] ?: 0}")
            add("§b铭刻粉尘 §f${materials[PermanentMaterialManager.MaterialType.INSCRIPTION_DUST] ?: 0}")
            add("§d遗物残片 §f${materials[PermanentMaterialManager.MaterialType.RELIC_FRAGMENT] ?: 0}")
            add("§6王冠碎片 §f${materials[PermanentMaterialManager.MaterialType.CROWN_SHARD] ?: 0}")
            add("§5星界核心 §f${materials[PermanentMaterialManager.MaterialType.ASTRAL_CORE] ?: 0}")
            add("§8§m--------------§r")
            add("§a装备仓库 §f${GearStorageManager.getItems(player).size}/${GearStorageManager.getCapacity(player)}")
            add("§b战利品仓库 §f${LootStorageManager.getItems(player).size}/${LootStorageManager.getCapacity(player)}")
            add("§e鉴定完成 §f$identifyDone")
            add("§6锻造完成 §f$forgeDone")
            add("§d饰品任务 §f$accessoryDone")
            add("§5收藏 §f$collectionDone/$collectionTotal")
            add("§8§m--------------§r")
            add("§7/rogue help")
        }
    }

    private fun setupSidebarSlots(scoreboard: Scoreboard, objective: Objective) {
        for (index in 0 until MAX_LINES) {
            val entry = LINE_ENTRIES[index]
            val team = scoreboard.registerNewTeam(teamName(index))
            team.addEntry(entry)
            objective.getScore(entry).score = MAX_LINES - index
        }
    }

    private fun normalizeLines(lines: List<String>): List<String> {
        val result = lines.take(MAX_LINES).map(::trimLine).toMutableList()
        while (result.size < MAX_LINES) {
            result += ""
        }
        return result
    }

    private fun applySidebarLine(scoreboard: Scoreboard, index: Int, line: String) {
        val team = scoreboard.getTeam(teamName(index)) ?: return
        if (team.prefix != line) {
            team.prefix = line
        }
        if (team.suffix.isNotEmpty()) {
            team.suffix = ""
        }
    }

    private fun teamName(index: Int): String = "rcl$index"

    private fun trimLine(line: String): String {
        val strippedLength = ChatColor.stripColor(line)?.length ?: line.length
        return if (strippedLength <= 32) line else line.take(40)
    }
}
