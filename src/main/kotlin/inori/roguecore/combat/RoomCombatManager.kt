package inori.roguecore.combat

import inori.roguecore.accessory.AccessoryDropManager
import inori.roguecore.affix.AffixManager
import inori.roguecore.affix.AffixType
import inori.roguecore.boon.BoonEffectHandler
import inori.roguecore.boon.BoonSelectManager
import inori.roguecore.collection.CollectionManager
import inori.roguecore.data.PlayerDataManager
import inori.roguecore.data.ForgeMaterialManager
import inori.roguecore.data.ForgeMaterialType
import inori.roguecore.data.ShardRewardManager
import inori.roguecore.dungeon.DungeonInstance
import inori.roguecore.dungeon.DungeonManager
import inori.roguecore.dungeon.RunPersistenceManager
import inori.roguecore.event.EventScaling
import inori.roguecore.event.RoomEventManager
import inori.roguecore.dungeon.room.Room
import inori.roguecore.dungeon.room.RoomType
import inori.roguecore.item.DungeonLootManager
import inori.roguecore.milestone.RunMilestoneManager
import inori.roguecore.modifier.RunModifierManager
import inori.roguecore.stats.BalanceStatsManager
import inori.roguecore.summary.RunSummaryManager
import inori.roguecore.party.PartyManager
import inori.roguecore.relic.RelicEffectHandler
import inori.roguecore.ui.DungeonHudManager
import inori.roguecore.ui.DungeonSceneCueManager
import inori.roguecore.ui.RunCompleteUI
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.World
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.info
import taboolib.common.platform.function.submit
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * 房间战斗管理器
 * 负责：怪物生成、战斗状态追踪、通关判定
 */
object RoomCombatManager {

    @Config("events.yml")
    lateinit var eventsConfig: Configuration
        private set

    /** 怪物 UUID -> (副本ID, 房间ID) 的反向映射，用于快速查找怪物所属房间 */
    private val mobRoomMap = ConcurrentHashMap<UUID, Pair<String, Int>>()

    /** 玩家当前所在的房间 ID: 玩家UUID -> (副本ID, 房间ID) */
    private val playerRoomMap = ConcurrentHashMap<UUID, Pair<String, Int>>()

    /** 玩家移动拦截提示冷却，避免连续刷屏 */
    private val movementWarnAt = ConcurrentHashMap<UUID, Long>()

    /** 当前激活战斗房的可视封门粒子点位 */
    private val activeRoomSeals = ConcurrentHashMap<String, List<Location>>()

    @Awake(LifeCycle.ENABLE)
    fun startSealRenderer() {
        submit(period = 8L) {
            for (points in activeRoomSeals.values) {
                for (point in points) {
                    val world = point.world ?: continue
                    world.spawnParticle(Particle.PORTAL, point, 3, 0.12, 0.22, 0.12, 0.01)
                }
            }
            enforceActiveRoomMobBounds()
        }
    }

    /**
     * 检测玩家位置，判断是否进入了新房间
     */
    fun checkPlayerRoom(player: Player) {
        val instance = DungeonManager.getPlayerDungeon(player) ?: return
        val room = getRoomAtPlayer(player, instance) ?: return

        val current = playerRoomMap[player.uniqueId]
        // 还在同一个房间，不处理
        if (current != null && current.first == instance.id && current.second == room.id) return

        // 进入了新房间
        playerRoomMap[player.uniqueId] = instance.id to room.id
        onPlayerEnterRoom(player, instance, room)
    }

    /**
     * 处理副本中的房间封锁移动。
     *
     * 当某个战斗房正在进行时：
     * - 已在战斗房里的玩家不能离开；
     * - 其他玩家只能进入该战斗房，不能继续探别的房间。
     *
     * @return true 表示本次移动需要被拦截
     */
    fun handleMovement(player: Player, from: Location, to: Location): Boolean {
        val instance = DungeonManager.getPlayerDungeon(player) ?: return false
        if (instance.completed) {
            return false
        }

        val activeRoom = instance.rooms.firstOrNull { it.isCombatRoom && it.state == RoomState.ACTIVE } ?: return false
        val fromRoom = getRoomAtLocation(from, instance)
        val toRoom = getRoomAtLocation(to, instance)

        if (toRoom?.id == activeRoom.id) {
            return false
        }

        if (fromRoom?.id == activeRoom.id) {
            warnMovementLocked(player, "§c当前房间战斗未结束，无法离开。")
            return true
        }

        if (toRoom != null && toRoom.id != activeRoom.id) {
            warnMovementLocked(player, "§c请先完成当前战斗房，再前往其他房间。")
            return true
        }

        return false
    }

    private fun warnMovementLocked(player: Player, message: String) {
        val now = System.currentTimeMillis()
        val last = movementWarnAt[player.uniqueId] ?: 0L
        if (now - last < 1000L) {
            return
        }
        movementWarnAt[player.uniqueId] = now
        player.sendMessage(message)
    }

    /**
     * 玩家进入房间时的处理
     */
    private fun onPlayerEnterRoom(player: Player, instance: DungeonInstance, room: Room) {
        // 非战斗房间交给事件管理器处理；只在事件真正触发时推进局内修正，避免重复进出已清理房间消耗预言/路线。
        if (!room.isCombatRoom) {
            RoomEventManager.onEnterRoom(player, instance, room)
            return
        }

        // 战斗房间：只有 IDLE 状态才触发
        if (room.state != RoomState.IDLE) return

        for (member in instance.getOnlinePlayers()) {
            RunModifierManager.onRoomEntered(member, room.type)
        }

        room.state = RoomState.ACTIVE
        RunPersistenceManager.markDirty()
        val spawned = spawnMonstersInRoom(instance, room)
        if (spawned <= 0) {
            room.state = RoomState.CLEARED
            RunPersistenceManager.markDirty()
            player.sendMessage("§e该房间未能生成怪物，已自动跳过")
            checkDungeonComplete(instance)
            return
        }

        sealRoom(instance, room)
        val typeName = room.type.displayName
        for (member in instance.getOnlinePlayers()) {
            member.sendMessage("§c⚔ ${typeName}房间已激活! 消灭所有怪物!")
            DungeonHudManager.pushActionBar(member, "§c$typeName 已封锁，清除全部怪物")
            RelicEffectHandler.onCombatStart(member)
        }
        DungeonSceneCueManager.broadcastCombatStart(instance, room)
    }

    /**
     * 在房间内生成怪物
     */
    private fun spawnMonstersInRoom(instance: DungeonInstance, room: Room): Int {
        room.spawnedMobCount = 0
        val waves = MonsterConfig.getWaves(room.type, instance.config.floorNumber)
        if (waves.isEmpty()) return 0

        val origin = instance.origin
        val baseY = instance.config.floorLevel + 1
        var spawnedCount = 0

        // 词缀倍率
        val countMultiplier = AffixManager.getMobCountMultiplier(instance) * getPartySizeMultiplier(instance)
        val hpMultiplier = AffixManager.getMobHpMultiplier(instance)
        val speedBonus = AffixManager.getMobSpeedBonus(instance)

        for (wave in waves) {
            val adjustedCount = (wave.count * countMultiplier).toInt().coerceAtLeast(1)

            for (i in 0 until adjustedCount) {
                val spawnLoc = getRandomSpawnLocation(room, origin, baseY)
                val entity = MythicMobBridge.spawnMob(wave.mobId, spawnLoc)
                if (entity != null) {
                    room.aliveMobs.add(entity.uniqueId)
                    mobRoomMap[entity.uniqueId] = instance.id to room.id
                    spawnedCount++

                    if (entity is org.bukkit.entity.LivingEntity) {
                        if (hpMultiplier > 0.0 && hpMultiplier != 1.0) {
                            val hpAttr = entity.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH)
                            if (hpAttr != null) {
                                hpAttr.baseValue = (hpAttr.baseValue * hpMultiplier).coerceAtLeast(1.0)
                                entity.health = hpAttr.value.coerceAtMost(hpAttr.baseValue)
                            }
                        }
                        val spawnShield = AffixManager.getMobSpawnShield(instance)
                        if (spawnShield > 0.0) {
                            val amplifier = (spawnShield / 4.0).toInt().coerceIn(0, 9)
                            entity.addPotionEffect(PotionEffect(PotionEffectType.ABSORPTION, 20 * 60, amplifier, true, true, true))
                        }
                        // 移速词缀
                        if (speedBonus > 0) {
                            val attr = entity.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MOVEMENT_SPEED)
                            if (attr != null) {
                                attr.baseValue = attr.baseValue * (1.0 + speedBonus)
                            }
                        }
                    }
                }
            }
        }

        room.spawnedMobCount = spawnedCount
        RunPersistenceManager.markDirty()
        info("[RogueCore] 副本 ${instance.id} 房间 #${room.id}(${room.type.displayName}) 生成了 ${room.aliveMobs.size} 只怪物")
        return spawnedCount
    }

    private fun getPartySizeMultiplier(instance: DungeonInstance): Double {
        val onlinePlayers = instance.getOnlinePlayerCount().coerceAtLeast(1)
        return 1.0 + (onlinePlayers - 1) * 0.5
    }

    private fun sealRoom(instance: DungeonInstance, room: Room) {
        val sealPoints = collectSealPoints(instance, room)
        if (sealPoints.isEmpty()) {
            activeRoomSeals.remove(instance.id)
            return
        }
        activeRoomSeals[instance.id] = sealPoints
        for (player in instance.getOnlinePlayers()) {
            player.playSound(player.location, Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.8f)
        }
    }

    private fun enforceActiveRoomMobBounds() {
        for (instance in DungeonManager.getActiveDungeons()) {
            if (instance.completed) continue
            val room = instance.rooms.firstOrNull { it.isCombatRoom && it.state == RoomState.ACTIVE } ?: continue
            val baseY = instance.config.floorLevel + 1
            val escaped = mutableListOf<UUID>()
            for (mobId in room.aliveMobs) {
                val entity = Bukkit.getEntity(mobId)
                if (entity == null || entity.isDead || entity.world.uid != instance.world.uid) {
                    escaped += mobId
                    continue
                }
                val currentRoom = getRoomAtLocation(entity.location, instance)
                if (currentRoom?.id == room.id) {
                    continue
                }
                val returnLocation = getRandomSpawnLocation(room, instance.origin, baseY)
                entity.teleport(returnLocation)
                entity.velocity = entity.velocity.multiply(0.0)
                entity.world.spawnParticle(Particle.PORTAL, returnLocation.clone().add(0.0, 1.0, 0.0), 18, 0.25, 0.35, 0.25, 0.03)
            }
            if (escaped.isNotEmpty()) {
                room.aliveMobs.removeAll(escaped.toSet())
                escaped.forEach(mobRoomMap::remove)
                if (room.aliveMobs.isEmpty() && room.state == RoomState.ACTIVE) {
                    onRoomCleared(instance, room)
                }
            }
        }
    }

    private fun unsealRoom(instance: DungeonInstance) {
        if (activeRoomSeals.remove(instance.id) == null) {
            return
        }
        for (player in instance.getOnlinePlayers()) {
            player.playSound(player.location, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 0.6f, 1.2f)
        }
    }

    private fun collectSealPoints(instance: DungeonInstance, room: Room): List<Location> {
        val points = mutableListOf<Location>()
        val baseY = instance.config.floorLevel
        val height = instance.config.roomHeight
        val world = instance.world
        val originX = instance.origin.blockX
        val originZ = instance.origin.blockZ

        for (x in (room.x + 1) until (room.x + room.width - 1)) {
            appendSealColumn(points, world, originX + x, originZ + room.z, 0, -1, baseY, height)
            appendSealColumn(points, world, originX + x, originZ + room.z + room.depth - 1, 0, 1, baseY, height)
        }
        for (z in (room.z + 1) until (room.z + room.depth - 1)) {
            appendSealColumn(points, world, originX + room.x, originZ + z, -1, 0, baseY, height)
            appendSealColumn(points, world, originX + room.x + room.width - 1, originZ + z, 1, 0, baseY, height)
        }
        return points
    }

    private fun appendSealColumn(
        points: MutableList<Location>,
        world: World,
        blockX: Int,
        blockZ: Int,
        outsideDx: Int,
        outsideDz: Int,
        baseY: Int,
        height: Int
    ) {
        if (!isOpenDoorway(world, blockX, blockZ, outsideDx, outsideDz, baseY, height)) {
            return
        }
        for (y in 1..height) {
            points += Location(world, blockX + 0.5, baseY + y + 0.5, blockZ + 0.5)
        }
    }

    private fun isOpenDoorway(
        world: World,
        blockX: Int,
        blockZ: Int,
        outsideDx: Int,
        outsideDz: Int,
        baseY: Int,
        height: Int
    ): Boolean {
        val outsideX = blockX + outsideDx
        val outsideZ = blockZ + outsideDz
        var hasAirColumn = false
        for (y in 1..height) {
            val inner = world.getBlockAt(blockX, baseY + y, blockZ)
            val outer = world.getBlockAt(outsideX, baseY + y, outsideZ)
            if (inner.type.isSolid || outer.type.isSolid) {
                return false
            }
            hasAirColumn = true
        }
        return hasAirColumn
    }

    /**
     * 在房间内部随机取一个生成点
     */
    private fun getRandomSpawnLocation(room: Room, origin: Location, y: Int): Location {
        // 房间内部区域（排除墙壁，留 2 格边距）
        val minX = room.x + 2
        val maxX = room.x + room.width - 3
        val minZ = room.z + 2
        val maxZ = room.z + room.depth - 3

        val rx = if (maxX > minX) (minX..maxX).random() else minX
        val rz = if (maxZ > minZ) (minZ..maxZ).random() else minZ

        return Location(
            origin.world,
            (origin.blockX + rx).toDouble() + 0.5,
            y.toDouble(),
            (origin.blockZ + rz).toDouble() + 0.5
        )
    }

    /**
     * 怪物死亡处理
     */
    fun onMobDeath(entity: Entity) {
        val mobUUID = entity.uniqueId
        val (dungeonId, roomId) = mobRoomMap.remove(mobUUID) ?: return

        val instance = DungeonManager.getDungeonById(dungeonId) ?: return
        val room = instance.rooms.firstOrNull { it.id == roomId } ?: return

        room.aliveMobs.remove(mobUUID)

        // 击杀统计
        for (uuid in instance.players) {
            PlayerDataManager.addKills(uuid, 1)
        }

        // 检查房间是否清完
        if (room.aliveMobs.isEmpty() && room.state == RoomState.ACTIVE) {
            onRoomCleared(instance, room)
        }
    }

    /**
     * 房间通关处理
     */
    private fun onRoomCleared(instance: DungeonInstance, room: Room) {
        room.state = RoomState.CLEARED
        RunPersistenceManager.markDirty()
        unsealRoom(instance)
        rewardHiddenKeys(instance, room)

        // 通知副本内所有玩家 + Boon 选择 + 碎片积累 + 词缀效果
        for (uuid in instance.players) {
            val player = Bukkit.getPlayer(uuid) ?: continue
            player.sendMessage("§a✔ ${room.type.displayName}房间已通关!")
            DungeonHudManager.pushActionBar(player, "§a${room.type.displayName}已通关")
            ShardRewardManager.onRoomClear(uuid, instance.config.floorNumber, AffixManager.getShardMultiplier(instance))
            val relicRoomBonus = RelicEffectHandler.getRoomClearShardBonus(player)
            if (relicRoomBonus > 0) {
                ShardRewardManager.addRunShards(uuid, relicRoomBonus)
                player.sendMessage("§6遗物清房奖励 §e$relicRoomBonus §6本局碎片。")
            }
            val affixShardBonus = AffixManager.getCombatShardFlat(instance)
            if (affixShardBonus > 0) {
                ShardRewardManager.addRunShards(uuid, affixShardBonus)
                player.sendMessage("§6战斗词缀额外奖励 §e$affixShardBonus §6本局碎片。")
            }
            val affixEmberBonus = AffixManager.getCombatEmberFlat(instance)
            if (affixEmberBonus > 0) {
                ForgeMaterialManager.add(uuid, ForgeMaterialType.BOSS_EMBER, affixEmberBonus)
                player.sendMessage("§6战斗余烬凝成 ${ForgeMaterialType.BOSS_EMBER.coloredName()} §ex$affixEmberBonus")
            }
            BoonEffectHandler.onRoomCleared(player, room.type)
            RelicEffectHandler.onRoomCleared(player)
            RunMilestoneManager.onRoomCleared(player, room.type)
            RunSummaryManager.onRoomCleared(player, room.type)
            RunModifierManager.onRoomCleared(player, room.type, instance)
            when (room.type) {
                RoomType.ELITE -> {
                    val eliteShardBonus = RelicEffectHandler.getEliteShardBonus(player) + AffixManager.getEliteShardFlat(instance)
                    if (eliteShardBonus > 0) {
                        ShardRewardManager.addRunShards(uuid, eliteShardBonus)
                        player.sendMessage("§6精英战利品额外奖励 §e$eliteShardBonus §6本局碎片。")
                    }
                    if (DungeonLootManager.grantEliteLoot(player, instance)) {
                        player.sendMessage("§6精英倒下后留下了一件临时装备。")
                    }
                    AccessoryDropManager.tryGrantElite(player, instance)
                    val eliteLootChance = RelicEffectHandler.getEliteLootChance(player) + AffixManager.getHiddenLootChance(instance) * 100.0
                    if (Random.nextDouble() * 100.0 < eliteLootChance && DungeonLootManager.grantEliteLoot(player, instance)) {
                        player.sendMessage("§d额外战利品共鸣让精英掉落了一件装备。")
                    }
                    val eliteRelicChance = RelicEffectHandler.getEliteRelicChance(player)
                    if (Random.nextDouble() * 100.0 < eliteRelicChance) {
                        inori.roguecore.relic.RelicSelectManager.offerRelicSelection(player, EventScaling.relicOfferCount(instance, 3))
                    }
                }
                RoomType.BOSS -> {
                    CollectionManager.recordBossKill(player, instance.config.floorNumber)
                    val bossShardBonus = RelicEffectHandler.getBossShardBonus(player) + AffixManager.getBossShardFlat(instance)
                    if (bossShardBonus > 0) {
                        ShardRewardManager.addRunShards(uuid, bossShardBonus)
                        player.sendMessage("§6Boss 战利品额外奖励 §e$bossShardBonus §6本局碎片。")
                    }
                    if (DungeonLootManager.grantBossLoot(player, instance)) {
                        player.sendMessage("§6Boss 战利品中包含临时装备。")
                    }
                    AccessoryDropManager.grantBoss(player, instance)
                    val bossExtraLootChance = RelicEffectHandler.getBossLootChance(player) + AffixManager.getHiddenLootChance(instance) * 100.0
                    if (Random.nextDouble() * 100.0 < bossExtraLootChance && DungeonLootManager.grantBossLoot(player, instance)) {
                        player.sendMessage("§d额外战利品共鸣让 Boss 多掉落了一件装备。")
                    }
                    val bossRelicChance = AffixManager.getBossRelicChance(instance)
                    if (bossRelicChance > 0.0 && Random.nextDouble() < bossRelicChance) {
                        inori.roguecore.relic.RelicSelectManager.offerRelicSelection(player, EventScaling.relicOfferCount(instance, 3))
                    }
                    val embers = rewardBossEmbers(player, instance)
                    if (embers > 0) {
                        player.sendMessage("§6Boss 炉心崩裂，掉出了 ${ForgeMaterialType.BOSS_EMBER.coloredName()} §ex$embers")
                    }
                }
                else -> Unit
            }
            if (room.type != RoomType.BOSS) {
                BoonSelectManager.offerBoonSelection(player)
                repeat(AffixManager.getExtraBoonCount(instance)) {
                    BoonSelectManager.offerBoonSelection(player)
                }
            }
        }

        DungeonSceneCueManager.broadcastRoomCleared(instance, room)
        info("[RogueCore] 副本 ${instance.id} 房间 #${room.id}(${room.type.displayName}) 已通关")

        // 检查是否所有战斗房间都通关了
        checkDungeonComplete(instance)
    }

    /**
     * 检查副本是否全部通关
     */
    private fun checkDungeonComplete(instance: DungeonInstance) {
        if (instance.completed) {
            return
        }

        val reachableIds = collectReachableRoomIds(instance)
        val allCombatRooms = instance.rooms.filter { it.isCombatRoom && it.id in reachableIds }
        val allCleared = allCombatRooms.all { it.state == RoomState.CLEARED }

        if (allCleared) {
            instance.completed = true
            RunPersistenceManager.markDirty()
            DungeonSceneCueManager.broadcastDungeonComplete(instance)
            val party = PartyManager.getPartyByDungeonId(instance.id)
            for (uuid in instance.players) {
                val player = Bukkit.getPlayer(uuid) ?: continue
                ShardRewardManager.onDungeonClear(uuid, instance.config.floorNumber, AffixManager.getShardMultiplier(instance))
                BalanceStatsManager.recordFloorCleared(instance.config.floorNumber)
                PlayerDataManager.updateBestFloor(uuid, instance.config.floorNumber)
                player.sendMessage("§6§l★ 副本通关! 所有房间已清除!")
                player.sendMessage("§e当前本局碎片: §6${ShardRewardManager.getRunShards(uuid)}")
                player.sendMessage("§e立即结算可获得: §6${ShardRewardManager.getSettlementPreview(uuid)} §e灵魂碎片")

                if (party != null && !party.isLeader(uuid)) {
                    player.sendMessage("§e等待队长选择：前往下一层或结算离开")
                    continue
                }

                RunCompleteUI.open(player, instance, party)
            }
            info("[RogueCore] 副本 ${instance.id} 全部通关!")
        }
    }

    private fun rewardHiddenKeys(instance: DungeonInstance, room: Room) {
        val relicKeyChance = instance.getOnlinePlayers().maxOfOrNull { RelicEffectHandler.getHiddenKeyChance(it) } ?: 0.0
        val affixKeyChance = AffixManager.getEliteKeyChance(instance)
        val reward = when (room.type) {
            RoomType.ELITE -> if (Random.nextDouble() <= instance.config.hiddenEliteKeyChance + affixKeyChance + relicKeyChance / 100.0) 1 else 0
            RoomType.BOSS -> instance.config.hiddenBossKeys
            else -> 0
        }
        if (reward <= 0) {
            return
        }
        val total = instance.addHiddenKeys(reward)
        RunPersistenceManager.markDirty()
        for (player in instance.getOnlinePlayers()) {
            player.sendMessage("§9获得隐藏钥匙 §bx$reward §7(当前: §b$total§7)")
            DungeonHudManager.pushActionBar(player, "§b隐藏钥匙 +$reward §7(当前 $total)")
        }
    }

    private fun rewardBossEmbers(player: Player, instance: DungeonInstance): Int {
        val bonus = AffixManager.getBossEmberBonus(instance) + RelicEffectHandler.getBossEmberBonus(player)
        val min = (eventsConfig.getInt("forge.materials.boss-ember.reward-min", 1) + bonus).coerceAtLeast(0)
        val max = (eventsConfig.getInt("forge.materials.boss-ember.reward-max", min) + bonus).coerceAtLeast(min)
        if (max <= 0) {
            return 0
        }
        val amount = if (max > min) Random.nextInt(min, max + 1) else min
        if (amount <= 0) {
            return 0
        }
        ForgeMaterialManager.add(player.uniqueId, ForgeMaterialType.BOSS_EMBER, amount)
        return amount
    }

    fun getPlayerRoom(player: Player): Room? {
        val instance = DungeonManager.getPlayerDungeon(player) ?: return null
        return getRoomAtPlayer(player, instance)
    }

    fun getActiveRoom(instance: DungeonInstance): Room? {
        return instance.rooms.firstOrNull { it.isCombatRoom && it.state == RoomState.ACTIVE }
    }

    fun getCombatProgress(instance: DungeonInstance): Pair<Int, Int> {
        val reachableIds = collectReachableRoomIds(instance)
        val combatRooms = instance.rooms.filter { it.isCombatRoom && it.id in reachableIds }
        val cleared = combatRooms.count { it.state == RoomState.CLEARED }
        return cleared to combatRooms.size
    }

    /**
     * 获取玩家当前所在的房间
     */
    private fun getRoomAtPlayer(player: Player, instance: DungeonInstance): Room? {
        return getRoomAtLocation(player.location, instance)
    }

    private fun getRoomAtLocation(location: Location, instance: DungeonInstance): Room? {
        val ox = instance.origin.blockX
        val oz = instance.origin.blockZ
        val px = location.blockX - ox
        val pz = location.blockZ - oz
        return instance.rooms.firstOrNull { it.contains(px, pz) }
    }

    private fun collectReachableRoomIds(instance: DungeonInstance): Set<Int> {
        val start = instance.rooms.firstOrNull { it.type == RoomType.SPAWN } ?: instance.rooms.firstOrNull() ?: return emptySet()
        val byId = instance.rooms.associateBy { it.id }
        val visited = linkedSetOf<Int>()
        val queue = ArrayDeque<Room>()
        visited += start.id
        queue.add(start)
        while (queue.isNotEmpty()) {
            val room = queue.removeFirst()
            for (nextId in room.connections) {
                val next = byId[nextId] ?: continue
                if (!visited.add(nextId)) {
                    continue
                }
                queue.add(next)
            }
        }
        return visited
    }

    /**
     * 玩家离开副本时清理
     */
    fun onPlayerLeave(player: Player) {
        playerRoomMap.remove(player.uniqueId)
        movementWarnAt.remove(player.uniqueId)
    }

    /**
     * 副本销毁时清理所有关联数据
     */
    fun onDungeonDestroy(dungeonId: String) {
        // 清理怪物映射
        mobRoomMap.entries.removeIf { it.value.first == dungeonId }
        activeRoomSeals.remove(dungeonId)

        // 清理玩家房间映射与移动提示状态
        val leavingPlayers = playerRoomMap.entries
            .filter { it.value.first == dungeonId }
            .map { it.key }
        playerRoomMap.entries.removeIf { it.value.first == dungeonId }
        leavingPlayers.forEach(movementWarnAt::remove)
    }
}
