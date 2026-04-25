package inori.roguecore.stats

import inori.roguecore.boon.Boon
import inori.roguecore.boon.BoonRegistry
import inori.roguecore.dungeon.route.NextFloorRoute
import inori.roguecore.milestone.RunMilestoneType
import inori.roguecore.modifier.RunModifierType
import inori.roguecore.relic.Relic
import inori.roguecore.relic.RelicRegistry
import org.bukkit.command.CommandSender
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.submit
import taboolib.module.configuration.Configuration
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 平衡数据记录。
 *
 * 记录玩家在实际游玩中选择了什么、死在哪层、哪些路线/里程碑触发最多。
 */
object BalanceStatsManager {

    private const val SAVE_PERIOD_TICKS = 20L * 60L
    private val file = File(getDataFolder(), "balance-stats.yml")

    private val boonAcquired = ConcurrentHashMap<String, Int>()
    private val boonUpgraded = ConcurrentHashMap<String, Int>()
    private val boonTags = ConcurrentHashMap<String, Int>()
    private val relicAcquired = ConcurrentHashMap<String, Int>()
    private val routeSelected = ConcurrentHashMap<String, Int>()
    private val milestoneAchieved = ConcurrentHashMap<String, Int>()
    private val modifierApplied = ConcurrentHashMap<String, Int>()
    private val floorEntered = ConcurrentHashMap<String, Int>()
    private val floorCleared = ConcurrentHashMap<String, Int>()
    private val floorDeath = ConcurrentHashMap<String, Int>()

    @Volatile
    private var totalSettledSoulShards = 0

    @Volatile
    private var dirty = false

    @Awake(LifeCycle.ENABLE)
    fun load() {
        loadFromFile()
        submit(period = SAVE_PERIOD_TICKS) {
            saveIfDirty()
        }
    }

    @Awake(LifeCycle.DISABLE)
    fun shutdown() {
        saveIfDirty(force = true)
    }

    fun recordBoonAcquired(boon: Boon) {
        inc(boonAcquired, boon.id)
        for (tag in boon.tags) {
            inc(boonTags, tag)
        }
    }

    fun recordBoonUpgraded(boon: Boon) {
        inc(boonUpgraded, boon.id)
    }

    fun recordRelicAcquired(relic: Relic) {
        inc(relicAcquired, relic.id)
    }

    fun recordRouteSelected(route: NextFloorRoute) {
        inc(routeSelected, route.name)
    }

    fun recordMilestone(type: RunMilestoneType) {
        inc(milestoneAchieved, type.name)
    }

    fun recordModifierApplied(type: RunModifierType) {
        inc(modifierApplied, type.name)
    }

    fun recordFloorEntered(floor: Int) {
        inc(floorEntered, floor.coerceAtLeast(1).toString())
    }

    fun recordFloorCleared(floor: Int) {
        inc(floorCleared, floor.coerceAtLeast(1).toString())
    }

    fun recordFloorDeath(floor: Int) {
        inc(floorDeath, floor.coerceAtLeast(1).toString())
    }

    fun recordSoulShardsSettled(amount: Int) {
        if (amount <= 0) return
        totalSettledSoulShards += amount
        dirty = true
    }

    fun reset() {
        boonAcquired.clear()
        boonUpgraded.clear()
        boonTags.clear()
        relicAcquired.clear()
        routeSelected.clear()
        milestoneAchieved.clear()
        modifierApplied.clear()
        floorEntered.clear()
        floorCleared.clear()
        floorDeath.clear()
        totalSettledSoulShards = 0
        dirty = true
        saveIfDirty(force = true)
    }

    fun sendSummary(sender: CommandSender) {
        for (line in formatSummary()) {
            sender.sendMessage(line)
        }
    }

    fun formatSummary(): List<String> {
        val totalFloorEntries = floorEntered.values.sum()
        val totalClears = floorCleared.values.sum()
        val totalDeaths = floorDeath.values.sum()
        val clearRate = percent(totalClears, totalFloorEntries)
        val deathRate = percent(totalDeaths, totalFloorEntries)

        return buildList {
            add("§6===== RogueCore 平衡统计 =====")
            add("§7楼层进入: §f$totalFloorEntries §7| 通关: §a$totalClears §7($clearRate) | 死亡: §c$totalDeaths §7($deathRate)")
            add("§7累计结算灵魂碎片: §6$totalSettledSoulShards")
            add("")
            add("§d神恩获得 TOP:")
            addAll(topLines(boonAcquired, 5) { id -> displayBoon(id) })
            add("§d神恩升级 TOP:")
            addAll(topLines(boonUpgraded, 5) { id -> displayBoon(id) })
            add("§b标签选择 TOP:")
            addAll(topLines(boonTags, 5) { it })
            add("§5遗物获得 TOP:")
            addAll(topLines(relicAcquired, 5) { id -> displayRelic(id) })
            add("§a路线选择:")
            addAll(routeLines())
            add("§6里程碑达成 TOP:")
            addAll(topLines(milestoneAchieved, 5) { id -> displayMilestone(id) })
            add("§b本局修正触发 TOP:")
            addAll(topLines(modifierApplied, 5) { id -> displayModifier(id) })
            add("§e楼层表现:")
            addAll(floorLines())
            add("")
            add("§7重置统计: §e/rogue admin stats reset")
        }
    }

    private fun loadFromFile() {
        if (!file.exists()) {
            return
        }
        val config = runCatching { Configuration.loadFromFile(file) }.getOrNull() ?: return
        loadMap(config, "boons.acquired", boonAcquired)
        loadMap(config, "boons.upgraded", boonUpgraded)
        loadMap(config, "boons.tags", boonTags)
        loadMap(config, "relics.acquired", relicAcquired)
        loadMap(config, "routes.selected", routeSelected)
        loadMap(config, "milestones.achieved", milestoneAchieved)
        loadMap(config, "modifiers.applied", modifierApplied)
        loadMap(config, "floors.entered", floorEntered)
        loadMap(config, "floors.cleared", floorCleared)
        loadMap(config, "floors.deaths", floorDeath)
        totalSettledSoulShards = config.getInt("totals.settled-soul-shards", 0).coerceAtLeast(0)
        dirty = false
    }

    private fun saveIfDirty(force: Boolean = false) {
        if (!dirty && !force) {
            return
        }
        val config = Configuration.empty()
        config["version"] = 1
        config["boons.acquired"] = boonAcquired.toSortedMap()
        config["boons.upgraded"] = boonUpgraded.toSortedMap()
        config["boons.tags"] = boonTags.toSortedMap()
        config["relics.acquired"] = relicAcquired.toSortedMap()
        config["routes.selected"] = routeSelected.toSortedMap()
        config["milestones.achieved"] = milestoneAchieved.toSortedMap()
        config["modifiers.applied"] = modifierApplied.toSortedMap()
        config["floors.entered"] = floorEntered.toSortedMap(compareBy { it.toIntOrNull() ?: Int.MAX_VALUE })
        config["floors.cleared"] = floorCleared.toSortedMap(compareBy { it.toIntOrNull() ?: Int.MAX_VALUE })
        config["floors.deaths"] = floorDeath.toSortedMap(compareBy { it.toIntOrNull() ?: Int.MAX_VALUE })
        config["totals.settled-soul-shards"] = totalSettledSoulShards
        file.parentFile?.mkdirs()
        config.saveToFile(file)
        dirty = false
    }

    private fun loadMap(config: Configuration, path: String, target: ConcurrentHashMap<String, Int>) {
        target.clear()
        val section = config.getConfigurationSection(path) ?: return
        for (key in section.getKeys(false)) {
            val value = section.getInt(key, 0)
            if (value > 0) {
                target[key] = value
            }
        }
    }

    private fun inc(map: ConcurrentHashMap<String, Int>, key: String, amount: Int = 1) {
        if (key.isBlank() || amount <= 0) return
        map[key] = (map[key] ?: 0) + amount
        dirty = true
    }

    private fun topLines(map: Map<String, Int>, limit: Int, name: (String) -> String): List<String> {
        if (map.isEmpty()) return listOf("  §8暂无数据")
        return map.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(limit)
            .mapIndexed { index, entry -> "  §7${index + 1}. §f${name(entry.key)} §7x${entry.value}" }
    }

    private fun routeLines(): List<String> {
        if (routeSelected.isEmpty()) return listOf("  §8暂无数据")
        val total = routeSelected.values.sum().coerceAtLeast(1)
        return NextFloorRoute.entries.map { route ->
            val count = routeSelected[route.name] ?: 0
            "  §f${route.displayName} §7x$count §8(${String.format("%.1f", count * 100.0 / total)}%)"
        }
    }

    private fun floorLines(): List<String> {
        val floors = (floorEntered.keys + floorCleared.keys + floorDeath.keys)
            .mapNotNull { it.toIntOrNull() }
            .distinct()
            .sorted()
        if (floors.isEmpty()) return listOf("  §8暂无数据")
        return floors.takeLast(12).map { floor ->
            val entered = floorEntered[floor.toString()] ?: 0
            val cleared = floorCleared[floor.toString()] ?: 0
            val deaths = floorDeath[floor.toString()] ?: 0
            "  §7F$floor §f进入 $entered §a通关 $cleared §c死亡 $deaths §8(通关 ${percent(cleared, entered)}, 死亡 ${percent(deaths, entered)})"
        }
    }

    private fun displayBoon(id: String): String = BoonRegistry.get(id)?.name ?: id

    private fun displayRelic(id: String): String = RelicRegistry.get(id)?.name ?: id

    private fun displayMilestone(id: String): String {
        return runCatching { RunMilestoneType.valueOf(id).displayName }.getOrDefault(id)
    }

    private fun displayModifier(id: String): String {
        return runCatching { RunModifierType.valueOf(id).displayName }.getOrDefault(id)
    }

    private fun percent(value: Int, total: Int): String {
        if (total <= 0) return "0.0%"
        return "${String.format("%.1f", value * 100.0 / total)}%"
    }
}
