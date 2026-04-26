package inori.roguecore.dungeon

import inori.roguecore.accessory.PlayerAccessoryData
import inori.roguecore.affix.AffixManager
import inori.roguecore.affix.AffixType
import inori.roguecore.affix.DungeonAffix
import inori.roguecore.boon.BoonEffectHandler
import inori.roguecore.boon.PlayerBoonData
import inori.roguecore.combat.RoomCombatManager
import inori.roguecore.curse.RunCurseManager
import inori.roguecore.data.ForgeMaterialManager
import inori.roguecore.data.PlayerDataManager
import inori.roguecore.data.ShardRewardManager
import inori.roguecore.dungeon.floor.FloorManager
import inori.roguecore.dungeon.generator.DungeonGenerator
import inori.roguecore.dungeon.route.NextFloorRoute
import inori.roguecore.event.EventAffixManager
import inori.roguecore.item.DungeonBoundItem
import inori.roguecore.item.DungeonLootManager
import inori.roguecore.milestone.RunMilestoneManager
import inori.roguecore.modifier.RunModifierManager
import inori.roguecore.stats.BalanceStatsManager
import inori.roguecore.summary.RunEndReason
import inori.roguecore.summary.RunSummaryManager
import inori.roguecore.relic.PlayerRelicData
import inori.roguecore.relic.RelicEffectHandler
import inori.roguecore.ui.DungeonGuiGuard
import inori.roguecore.ui.DungeonHudManager
import inori.roguecore.ui.DungeonSceneCueManager
import inori.roguecore.ui.RunCompleteUI
import inori.roguecore.party.Party
import inori.roguecore.party.PartyManager
import inori.roguecore.talent.TalentManager
import inori.roguecore.unlock.UnlockManager
import inori.roguecore.world.VoidWorldManager
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import taboolib.common.platform.function.info
import taboolib.common.platform.function.warning
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 地牢管理器 — 管理所有副本实例的生命周期
 * 每个副本 = 一个独立世界，完全隔离
 */
object DungeonManager {

    /** 所有活跃的副本实例 */
    private val instances = ConcurrentHashMap<String, DungeonInstance>()

    /** 玩家 -> 副本 ID 映射 */
    private val playerDungeonMap = ConcurrentHashMap<UUID, String>()

    /** 玩家进入副本前的位置 */
    private val returnLocations = ConcurrentHashMap<UUID, Location>()

    /** 离线期间副本结束后，等待玩家下次上线时清理的 run 临时状态 */
    private val pendingOfflineCleanup = ConcurrentHashMap.newKeySet<UUID>()

    /**
     * 创建新副本实例（独立世界）
     */
    fun createDungeon(config: DungeonConfig = DungeonConfig()): DungeonInstance? {
        // 生成实例 ID
        val instanceId = UUID.randomUUID().toString().substring(0, 8)

        // 为这个副本创建独立世界
        val world = VoidWorldManager.createInstanceWorld(instanceId)
        if (world == null) {
            warning("[RogueCore] 无法为副本 $instanceId 创建世界")
            return null
        }

        // 在世界原点生成地牢
        val origin = Location(world, 0.0, config.floorLevel.toDouble(), 0.0)
        val affixes = AffixManager.rollAffixes(config.floorNumber, config.route)
        val eventAffixes = EventAffixManager.rollAffixes(config.floorNumber, config.route)
        val adjustedConfig = applyGenerationAffixes(config, affixes)
        val generator = DungeonGenerator(world, origin, adjustedConfig)
        val instance = generator.generate(instanceId, affixes, eventAffixes)

        instances[instance.id] = instance
        RunPersistenceManager.markDirty()
        val affixNames = if (affixes.isNotEmpty()) affixes.joinToString(", ") { it.name } else "无"
        val eventAffixNames = if (eventAffixes.isNotEmpty()) eventAffixes.joinToString(", ") { it.name } else "无"
        info("[RogueCore] 副本 ${instance.id} 已创建 (${instance.rooms.size}个房间, 战斗词缀: $affixNames, 事件词缀: $eventAffixNames)")
        return instance
    }

    private fun applyGenerationAffixes(config: DungeonConfig, affixes: List<DungeonAffix>): DungeonConfig {
        if (affixes.isEmpty()) return config
        val roomWeights = config.roomWeightModifiers.toMutableMap()
        fun add(type: inori.roguecore.dungeon.room.RoomType, value: Int) {
            if (value != 0) roomWeights[type] = (roomWeights[type] ?: 0) + value
        }
        val eventWeight = affixes.filter { it.type == AffixType.EXTRA_EVENT_ROOM_WEIGHT }.sumOf { it.value.toInt().coerceAtLeast(0) }
        if (eventWeight > 0) {
            listOf(
                inori.roguecore.dungeon.room.RoomType.SHOP,
                inori.roguecore.dungeon.room.RoomType.CHEST,
                inori.roguecore.dungeon.room.RoomType.SHRINE,
                inori.roguecore.dungeon.room.RoomType.FORGE,
                inori.roguecore.dungeon.room.RoomType.TRIAL,
                inori.roguecore.dungeon.room.RoomType.GAMBLE,
                inori.roguecore.dungeon.room.RoomType.REST,
                inori.roguecore.dungeon.room.RoomType.CONTRACT
            ).forEach { add(it, eventWeight) }
        }
        add(inori.roguecore.dungeon.room.RoomType.CHEST, affixes.filter { it.type == AffixType.EXTRA_CHEST_WEIGHT }.sumOf { it.value.toInt().coerceAtLeast(0) })
        add(inori.roguecore.dungeon.room.RoomType.SHRINE, affixes.filter { it.type == AffixType.EXTRA_SHRINE_WEIGHT }.sumOf { it.value.toInt().coerceAtLeast(0) })
        add(inori.roguecore.dungeon.room.RoomType.FORGE, affixes.filter { it.type == AffixType.EXTRA_FORGE_WEIGHT }.sumOf { it.value.toInt().coerceAtLeast(0) })
        val hiddenBonus = affixes.filter { it.type == AffixType.EXTRA_HIDDEN_CHANCE }.sumOf { it.value }.coerceAtLeast(0.0)
        return config.copy(
            hiddenRoomChance = (config.hiddenRoomChance + hiddenBonus).coerceAtMost(1.0),
            roomWeightModifiers = roomWeights
        )
    }

    private fun notifyAffixes(player: Player, instance: DungeonInstance) {
        if (instance.affixes.isEmpty() && instance.eventAffixes.isEmpty()) {
            return
        }
        if (instance.affixes.isNotEmpty()) {
            player.sendMessage("§6§l副本词缀:")
            for (affix in instance.affixes) {
                player.sendMessage("  ${affix.name} §7- ${affix.description}")
            }
        }
        if (instance.eventAffixes.isNotEmpty()) {
            player.sendMessage("§d§l事件词缀:")
            for (affix in instance.eventAffixes) {
                player.sendMessage("  ${affix.name} §7- ${affix.description}")
            }
        }
    }

    /**
     * 玩家加入副本
     */
    fun joinDungeon(player: Player, dungeonId: String): Boolean {
        return joinDungeon(player, dungeonId, startRun = true)
    }

    private fun joinDungeon(player: Player, dungeonId: String, startRun: Boolean): Boolean {
        val instance = instances[dungeonId] ?: return false

        // 如果玩家已在其他副本，先离开
        if (isInDungeon(player)) {
            leaveDungeon(player)
        }

        // 保存返回位置（重连回副本时保留首次进入前的位置）
        if (PartyManager.hasDungeonReconnect(player.uniqueId)) {
            returnLocations.putIfAbsent(player.uniqueId, player.location.clone())
        } else {
            returnLocations[player.uniqueId] = player.location.clone()
        }

        instance.players.add(player.uniqueId)
        playerDungeonMap[player.uniqueId] = dungeonId
        PartyManager.clearDungeonReconnect(player.uniqueId)

        // 传送到起点
        player.teleport(instance.getSpawnLocation())

        if (startRun) {
            // 记录运行 + 应用天赋，仅在新 run 生效。
            PlayerDataManager.recordRunStart(player.uniqueId)
            BalanceStatsManager.recordFloorEntered(instance.config.floorNumber)
            RunSummaryManager.startRun(player, instance.config.floorNumber)
            PlayerAccessoryData.clear(player)
            TalentManager.applyTalents(player)
        }

        notifyAffixes(player, instance)
        if (startRun) {
            RunModifierManager.applyFloorProphecyAffix(player, instance)
        }
        DungeonLootManager.refreshEquippedSetBonuses(player)
        DungeonHudManager.attach(player)
        DungeonSceneCueManager.showDungeonEntry(player, instance)
        RunPersistenceManager.markDirty()
        return true
    }

    fun restoreDungeon(instance: DungeonInstance) {
        instances[instance.id] = instance
        RunPersistenceManager.markDirty()
    }

    /**
     * 队伍一起进入副本
     * @return 创建的副本实例，失败返回 null
     */
    fun joinPartyDungeon(party: Party, config: DungeonConfig): DungeonInstance? {
        val instance = createDungeon(config) ?: return null
        party.dungeonId = instance.id

        for (uuid in party.members) {
            val member = Bukkit.getPlayer(uuid) ?: continue
            joinDungeon(member, instance.id, startRun = true)
        }

        return instance
    }

    fun canRejoinDungeon(uuid: UUID): Boolean {
        val dungeonId = PartyManager.resolveReconnectDungeonId(uuid) ?: return false
        return instances.containsKey(dungeonId)
    }

    fun processPendingJoinState(player: Player) {
        if (!pendingOfflineCleanup.remove(player.uniqueId)) {
            return
        }
        PlayerBoonData.clearBoons(player)
        RunCurseManager.clear(player)
        PlayerRelicData.clearRelics(player)
        PlayerAccessoryData.clear(player)
        RunMilestoneManager.clear(player.uniqueId)
        RunModifierManager.clear(player.uniqueId)
        TalentManager.removeTalents(player)
        DungeonBoundItem.clearFromPlayer(player)
        ForgeMaterialManager.clear(player.uniqueId)
        DungeonGuiGuard.unlock(player)
        DungeonHudManager.detach(player)
        player.sendMessage("§e你离线期间本次冒险已经结束，临时状态已自动结算并清理。")
    }

    fun rejoinDungeon(player: Player): Boolean {
        if (isInDungeon(player)) {
            return true
        }
        val dungeonId = PartyManager.resolveReconnectDungeonId(player.uniqueId) ?: return false
        val instance = instances[dungeonId] ?: run {
            PartyManager.clearDungeonReconnect(player.uniqueId)
            return false
        }

        returnLocations.putIfAbsent(player.uniqueId, player.location.clone())
        instance.players.add(player.uniqueId)
        playerDungeonMap[player.uniqueId] = instance.id
        PartyManager.clearDungeonReconnect(player.uniqueId)
        PartyManager.onPlayerJoin(player)
        player.teleport(instance.getSpawnLocation())
        TalentManager.applyTalents(player)
        PlayerBoonData.reapply(player)
        RunCurseManager.reapply(player)
        PlayerAccessoryData.reapply(player)
        notifyAffixes(player, instance)
        DungeonLootManager.refreshEquippedSetBonuses(player)
        DungeonHudManager.attach(player)
        DungeonSceneCueManager.showDungeonEntry(player, instance, "重返冒险")
        player.sendMessage("§a你已重返未结束的副本 §f${instance.id}§a。")
        RunPersistenceManager.markDirty()
        val party = PartyManager.getPartyByDungeonId(instance.id)
        if (instance.completed) {
            if (party != null && !party.isLeader(player.uniqueId)) {
                player.sendMessage("§e该副本已通关，等待队长决定去留。")
            } else {
                RunCompleteUI.open(player, instance, party)
            }
        }
        return true
    }

    fun handleDisconnect(player: Player) {
        val dungeonId = playerDungeonMap.remove(player.uniqueId) ?: return
        val instance = instances[dungeonId] ?: return

        instance.players.remove(player.uniqueId)
        RoomCombatManager.onPlayerLeave(player)
        DungeonGuiGuard.unlock(player)
        DungeonHudManager.detach(player)
        PartyManager.markDungeonDisconnect(player, dungeonId)
        RunPersistenceManager.markDirty()
        info("[RogueCore] 玩家 ${player.name} 从副本 $dungeonId 断线离场，保留重连资格")
    }

    /**
     * 通关后进入下一层。
     * 单人直接推进，队伍则由触发者代表全队推进。
     */
    fun advanceDungeon(dungeonId: String, requestedBy: UUID? = null, route: NextFloorRoute? = null): Boolean {
        val current = instances[dungeonId] ?: return false
        if (!current.completed) {
            return false
        }

        val party = PartyManager.getPartyByDungeonId(dungeonId)
        if (party != null && requestedBy != null && !party.isLeader(requestedBy)) {
            Bukkit.getPlayer(requestedBy)?.sendMessage("§c只有队长才能决定下一层。")
            return false
        }

        if (route != null) {
            for (uuid in current.players) {
                Bukkit.getPlayer(uuid)?.let { player ->
                    RunMilestoneManager.onRouteSelected(player, route)
                    BoonEffectHandler.onRouteSelected(player, route)
                    val relicReward = RelicEffectHandler.getRouteStreakReward(player) * route.rewardLevel
                    if (relicReward > 0) {
                        ShardRewardManager.addRunShards(player.uniqueId, relicReward)
                        player.sendMessage("§d路线遗物因 §f${route.displayName} §d获得 §e$relicReward §d本局碎片。")
                    }
                }
                RunSummaryManager.onRouteSelected(uuid, route)
                BalanceStatsManager.recordRouteSelected(route)
            }
        }

        val nextFloor = current.config.floorNumber + 1
        val unlockRouteWeightBonus = if (requestedBy != null && route != null) {
            UnlockManager.getRouteWeightBonus(requestedBy, route)
        } else {
            emptyMap()
        }
        val modifierRouteWeightBonus = requestedBy
            ?.let(Bukkit::getPlayer)
            ?.let(RunModifierManager::getRoomWeightBonus)
            ?: emptyMap()
        val routeWeightBonus = (unlockRouteWeightBonus.keys + modifierRouteWeightBonus.keys).associateWith { type ->
            (unlockRouteWeightBonus[type] ?: 0) + (modifierRouteWeightBonus[type] ?: 0)
        }
        val routeHiddenBonus = if (requestedBy != null && route != null) {
            UnlockManager.getRouteHiddenBonus(requestedBy, route)
        } else {
            0.0
        }
        val nextConfig = FloorManager.getFloorConfig(nextFloor, route, routeWeightBonus, routeHiddenBonus)
        val nextInstance = createDungeon(nextConfig)
        if (nextInstance == null) {
            Bukkit.getPlayer(requestedBy ?: current.players.firstOrNull() ?: return false)
                ?.sendMessage("§c下一层创建失败，本次冒险已终止。")
            finishDungeon(dungeonId, requestedBy)
            return false
        }

        val transferring = current.players.toList()
        for (uuid in transferring) {
            val player = Bukkit.getPlayer(uuid) ?: continue
            current.players.remove(uuid)
            nextInstance.players.add(uuid)
            playerDungeonMap[uuid] = nextInstance.id
            BalanceStatsManager.recordFloorEntered(nextConfig.floorNumber)
            RunSummaryManager.onFloorEntered(uuid, nextConfig.floorNumber)
            player.teleport(nextInstance.getSpawnLocation())
            notifyAffixes(player, nextInstance)
            RunModifierManager.applyFloorProphecyAffix(player, nextInstance)
            PlayerAccessoryData.reapply(player)
            DungeonLootManager.refreshEquippedSetBonuses(player)
            DungeonHudManager.attach(player)
            val routeText = route?.let { " §7(${it.displayName})" } ?: ""
            player.sendMessage("§b已进入下一层: §f${nextConfig.floorNumber}$routeText")
            DungeonSceneCueManager.showDungeonEntry(player, nextInstance, route?.displayName)
        }

        party?.dungeonId = nextInstance.id
        instances.remove(current.id)
        cleanupDungeonWorld(current, clearPartyBinding = false)
        RunPersistenceManager.markDirty()
        return true
    }

    /**
     * 通关后结算并结束本次 run。
     */
    fun finishDungeon(dungeonId: String, requestedBy: UUID? = null): Boolean {
        val current = instances[dungeonId] ?: return false
        val party = PartyManager.getPartyByDungeonId(dungeonId)
        if (party != null && requestedBy != null && !party.isLeader(requestedBy)) {
            Bukkit.getPlayer(requestedBy)?.sendMessage("§c只有队长才能结束本次冒险。")
            return false
        }

        for (uuid in current.players) {
            PlayerDataManager.recordClear(uuid, current.config.floorNumber)
        }

        val targets = current.players.toList()
        for (uuid in targets) {
            RunSummaryManager.markEndReason(uuid, RunEndReason.CLEAR)
        }
        for (uuid in targets) {
            val player = Bukkit.getPlayer(uuid) ?: continue
            if (isInDungeon(player)) {
                leaveDungeon(player)
            }
        }
        RunPersistenceManager.markDirty()
        return true
    }

    /**
     * 玩家离开副本
     */
    fun leaveDungeon(player: Player) {
        val dungeonId = playerDungeonMap.remove(player.uniqueId) ?: return
        val instance = instances[dungeonId]

        PartyManager.clearDungeonReconnect(player.uniqueId)
        instance?.players?.remove(player.uniqueId)
        PartyManager.onDungeonMemberRemoved(player.uniqueId, dungeonId)
        RoomCombatManager.onPlayerLeave(player)

        RunModifierManager.onRunEnding(player, instance, death = false)
        val shards = ShardRewardManager.settle(player.uniqueId)
        RunSummaryManager.finish(player, instance)
        if (shards > 0) {
            player.sendMessage("§e本次冒险结算 §6$shards §e灵魂碎片")
        }
        player.sendMessage("§7输入 §e/rogue run summary §7查看本次冒险报告。")

        PlayerBoonData.clearBoons(player)
        RunCurseManager.clear(player)
        PlayerRelicData.clearRelics(player)
        PlayerAccessoryData.clear(player)
        RunMilestoneManager.clear(player.uniqueId)
        RunModifierManager.clear(player.uniqueId)
        TalentManager.removeTalents(player)
        DungeonBoundItem.clearFromPlayer(player)
        ForgeMaterialManager.clear(player.uniqueId)

        DungeonGuiGuard.unlock(player)
        DungeonHudManager.detach(player)

        val returnLoc = returnLocations.remove(player.uniqueId)
        if (returnLoc != null) {
            player.teleport(returnLoc)
        } else {
            val mainWorld = Bukkit.getWorlds().firstOrNull() ?: return
            player.teleport(mainWorld.spawnLocation)
        }

        if (instance != null && instance.players.isEmpty()) {
            destroyDungeon(dungeonId)
        }
        RunPersistenceManager.markDirty()
    }

    /**
     * 获取玩家所在的副本
     */
    fun getPlayerDungeon(player: Player): DungeonInstance? {
        val dungeonId = playerDungeonMap[player.uniqueId] ?: return null
        return instances[dungeonId]
    }

    fun getDungeonId(uuid: UUID): String? {
        return playerDungeonMap[uuid]
    }

    /**
     * 通过 ID 获取副本实例
     */
    fun getDungeonById(dungeonId: String): DungeonInstance? {
        return instances[dungeonId]
    }

    fun getActiveDungeons(): List<DungeonInstance> {
        return instances.values.sortedWith(compareBy<DungeonInstance> { it.config.floorNumber }.thenBy { it.id })
    }

    /**
     * 玩家是否在副本中
     */
    fun isInDungeon(player: Player): Boolean {
        return playerDungeonMap.containsKey(player.uniqueId)
    }

    /**
     * 销毁指定副本
     */
    fun destroyDungeon(dungeonId: String) {
        val instance = instances.remove(dungeonId) ?: return

        // 踢出所有玩家
        for (uuid in instance.players.toList()) {
            playerDungeonMap.remove(uuid)
            ShardRewardManager.settle(uuid)
            ForgeMaterialManager.clear(uuid)
            RunMilestoneManager.clear(uuid)
            RunModifierManager.clear(uuid)
            val returnLoc = returnLocations.remove(uuid)
            val player = Bukkit.getPlayer(uuid)
            if (player != null) {
                RunSummaryManager.finish(player, instance)
                PlayerBoonData.clearBoons(player)
                RunCurseManager.clear(player)
                PlayerRelicData.clearRelics(player)
                PlayerAccessoryData.clear(player)
                RunMilestoneManager.clear(uuid)
                RunModifierManager.clear(uuid)
                TalentManager.removeTalents(player)
                DungeonBoundItem.clearFromPlayer(player)
                DungeonGuiGuard.unlock(player)
                DungeonHudManager.detach(player)
                if (returnLoc != null) {
                    player.teleport(returnLoc)
                } else {
                    val mainWorld = Bukkit.getWorlds().firstOrNull() ?: continue
                    player.teleport(mainWorld.spawnLocation)
                }
            }
        }
        instance.players.clear()

        cleanupDungeonWorld(instance, clearPartyBinding = true)
        RunPersistenceManager.markDirty()
    }

    /**
     * 销毁所有副本（关服时调用）
     */
    fun destroyAll() {
        for (id in instances.keys.toList()) {
            destroyDungeon(id)
        }
        playerDungeonMap.clear()
        returnLocations.clear()
        pendingOfflineCleanup.clear()
        // 清理可能残留的副本世界
        VoidWorldManager.destroyAll()
        info("[RogueCore] 所有副本已清理")
    }

    fun getReturnLocations(): Map<UUID, Location> {
        return returnLocations.mapValues { it.value.clone() }
    }

    fun restoreReturnLocations(values: Map<UUID, Location>) {
        returnLocations.clear()
        for ((uuid, location) in values) {
            returnLocations[uuid] = location.clone()
        }
        RunPersistenceManager.markDirty()
    }

    private fun scheduleOfflineCleanup(uuid: UUID) {
        pendingOfflineCleanup.add(uuid)
        ShardRewardManager.settle(uuid)
        ForgeMaterialManager.clear(uuid)
        RunPersistenceManager.markDirty()
    }

    private fun cleanupDungeonWorld(instance: DungeonInstance, clearPartyBinding: Boolean) {
        RoomCombatManager.onDungeonDestroy(instance.id)
        if (clearPartyBinding) {
            val party = PartyManager.getPartyByDungeonId(instance.id)
            if (party != null) {
                for (uuid in party.members) {
                    if (PartyManager.hasDungeonReconnect(uuid)) {
                        scheduleOfflineCleanup(uuid)
                    }
                    returnLocations.remove(uuid)
                    PartyManager.clearDungeonReconnect(uuid)
                }
            } else {
                for (uuid in PartyManager.getReconnectPlayersForDungeon(instance.id)) {
                    scheduleOfflineCleanup(uuid)
                    returnLocations.remove(uuid)
                }
            }
            PartyManager.clearDungeonBinding(instance.id)
        }
        instance.destroy()
        info("[RogueCore] 副本 ${instance.id} 已销毁")
    }
}
