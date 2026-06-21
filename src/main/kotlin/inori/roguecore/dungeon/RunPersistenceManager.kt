package inori.roguecore.dungeon

import inori.roguecore.accessory.AccessoryEffect
import inori.roguecore.accessory.AccessoryEffectType
import inori.roguecore.accessory.AccessoryInstance
import inori.roguecore.accessory.AccessoryRegistry
import inori.roguecore.accessory.AccessorySlot
import inori.roguecore.accessory.PlayerAccessoryData
import inori.roguecore.affix.AffixRegistry
import inori.roguecore.boon.BoonInstance
import inori.roguecore.boon.BoonRegistry
import inori.roguecore.boon.PlayerBoonData
import inori.roguecore.combat.RoomState
import inori.roguecore.curse.RunCurseManager
import inori.roguecore.curse.RunCurseType
import inori.roguecore.data.ForgeMaterialManager
import inori.roguecore.data.ForgeMaterialType
import inori.roguecore.data.ShardRewardManager
import inori.roguecore.dungeon.floor.FloorTheme
import inori.roguecore.dungeon.generator.DungeonGenerator
import inori.roguecore.dungeon.room.Room
import inori.roguecore.dungeon.room.RoomType
import inori.roguecore.dungeon.route.NextFloorRoute
import inori.roguecore.event.EventAffixManager
import inori.roguecore.item.DungeonLootSource
import inori.roguecore.listener.DungeonListener
import inori.roguecore.milestone.RunMilestoneManager
import inori.roguecore.milestone.RunMilestoneType
import inori.roguecore.modifier.RunModifier
import inori.roguecore.modifier.RunModifierManager
import inori.roguecore.modifier.RunModifierType
import inori.roguecore.summary.RunSummaryManager
import inori.roguecore.party.PartyManager
import inori.roguecore.relic.PlayerRelicData
import inori.roguecore.relic.RelicRegistry
import inori.roguecore.world.VoidWorldManager
import org.bukkit.Bukkit
import org.bukkit.Location
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.info
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.warning
import taboolib.module.configuration.Configuration
import java.io.File
import java.util.UUID

object RunPersistenceManager {

    private const val AUTO_SAVE_PERIOD_TICKS = 20L * 60L

    private val snapshotFile = File(getDataFolder(), "run-state.yml")

    @Volatile
    private var dirty = false

    @Volatile
    private var restoring = false

    @Awake(LifeCycle.ENABLE)
    fun startAutoSave() {
        submit(delay = AUTO_SAVE_PERIOD_TICKS, period = AUTO_SAVE_PERIOD_TICKS) {
            saveIfDirty()
        }
    }

    fun markDirty() {
        if (!restoring) {
            dirty = true
        }
    }

    fun saveIfDirty() {
        if (dirty) {
            saveAll()
        }
    }

    fun saveAll() {
        val dungeons = DungeonManager.getActiveDungeons()
        if (dungeons.isEmpty()) {
            deleteSnapshot()
            dirty = false
            return
        }

        val config = Configuration.empty()
        config["version"] = 1
        saveParties(config)
        savePlayers(config, dungeons)
        saveDungeons(config, dungeons)
        snapshotFile.parentFile?.mkdirs()
        config.saveToFile(snapshotFile)
        dirty = false
        info("[RogueCore] 已保存 ${dungeons.size} 个运行中的冒险快照")
    }

    fun restoreAll() {
        if (!snapshotFile.exists()) {
            return
        }

        val config = runCatching { Configuration.loadFromFile(snapshotFile) }.getOrElse {
            warning("[RogueCore] 读取 run 持久化快照失败: ${it.message}")
            return
        }

        restoring = true
        try {
            restoreParties(config)
            restorePlayers(config)
            restoreDungeons(config)
        } finally {
            restoring = false
        }

        for (player in Bukkit.getOnlinePlayers()) {
            if (DungeonManager.canRejoinDungeon(player.uniqueId)) {
                player.sendMessage("§e你有一场未结束的冒险，输入 §f/rogue run rejoin §e可返回副本。")
            }
        }

        markDirty()
        saveIfDirty()
    }

    private fun saveParties(config: Configuration) {
        val parties = PartyManager.getPartySnapshots()
        config["parties.list"] = parties.map { snapshot ->
            mapOf(
                "id" to snapshot.id,
                "leader" to snapshot.leader.toString(),
                "max-size" to snapshot.maxSize,
                "members" to snapshot.members.map(UUID::toString),
                "dungeon-id" to snapshot.dungeonId
            )
        }
        config["parties.reconnects"] = PartyManager.getReconnectSnapshots().mapKeys { it.key.toString() }
    }

    private fun savePlayers(config: Configuration, dungeons: List<DungeonInstance>) {
        val returnLocations = DungeonManager.getReturnLocations()
        val relevantPlayers = linkedSetOf<UUID>()
        for (instance in dungeons) {
            relevantPlayers += instance.players
        }
        relevantPlayers += returnLocations.keys
        relevantPlayers += PartyManager.getReconnectSnapshots().keys

        val serializedPlayers = relevantPlayers.map { uuid ->
            mapOf(
                "uuid" to uuid.toString(),
                "run-shards" to ShardRewardManager.getRunShards(uuid),
                "boons" to PlayerBoonData.getBoons(uuid).map { boon ->
                    mapOf("id" to boon.boon.id, "level" to boon.level)
                },
                "relics" to PlayerRelicData.getRelics(uuid).map { it.id },
                "accessories" to PlayerAccessoryData.getEquipped(uuid).map { (slot, accessory) ->
                    mapOf(
                        "slot" to slot.name,
                        "id" to accessory.definition.id,
                        "rarity" to accessory.rarity.id,
                        "source" to accessory.source.name,
                        "floor" to accessory.floor,
                        "score" to accessory.score,
                        "attributes" to accessory.rolledAttributes,
                        "effects" to accessory.effects.map { effect ->
                            mapOf("type" to effect.type.name, "value" to effect.value, "chance" to effect.chance, "tag" to effect.tag)
                        }
                    )
                },
                "curses" to RunCurseManager.getCurses(uuid).map(RunCurseType::name),
                "milestones" to RunMilestoneManager.getSnapshot(uuid).milestones.map(RunMilestoneType::name),
                "combat-streak" to RunMilestoneManager.getSnapshot(uuid).combatStreak,
                "modifiers" to RunModifierManager.getModifiers(uuid).map { modifier ->
                    mapOf(
                        "type" to modifier.type.name,
                        "remaining-rooms" to modifier.remainingRooms,
                        "charges" to modifier.charges,
                        "value" to modifier.value,
                        "source" to modifier.source,
                        "payload" to modifier.payload
                    )
                },
                "materials" to ForgeMaterialManager.getAll(uuid).mapKeys { it.key.id },
                "summary" to RunSummaryManager.getSnapshot(uuid),
                "return-location" to returnLocations[uuid]?.let(::serializeLocation)
            )
        }
        config["players.list"] = serializedPlayers
    }

    private fun saveDungeons(config: Configuration, dungeons: List<DungeonInstance>) {
        config["dungeons.list"] = dungeons.map { instance ->
            mapOf(
                "id" to instance.id,
                "completed" to instance.completed,
                "hidden-keys" to instance.getHiddenKeys(),
                "players" to instance.players.map(UUID::toString),
                "affixes" to instance.affixes.map { it.id },
                "event-affixes" to instance.eventAffixes.map { it.id },
                "config" to serializeConfig(instance.config),
                "rooms" to instance.rooms.map { room ->
                    mapOf(
                        "id" to room.id,
                        "x" to room.x,
                        "z" to room.z,
                        "width" to room.width,
                        "depth" to room.depth,
                        "type" to room.type.name,
                        "connections" to room.connections,
                        "state" to room.state.name,
                        "spawned-mob-count" to room.spawnedMobCount
                    )
                },
                "corridors" to instance.corridorCoords.map { "${it.first},${it.second}" }
            )
        }
    }

    private fun restoreParties(config: Configuration) {
        val partyMaps = config.getMapList("parties.list")
        val parties = partyMaps.mapNotNull { raw ->
            val id = raw["id"]?.toString() ?: return@mapNotNull null
            val leader = raw["leader"]?.toString()?.let(UUID::fromString) ?: return@mapNotNull null
            val maxSize = raw["max-size"]?.toString()?.toIntOrNull() ?: 4
            val members = (raw["members"] as? List<*>)?.mapNotNull { it?.toString()?.let(UUID::fromString) }?.toSet()
                ?: return@mapNotNull null
            val dungeonId = raw["dungeon-id"]?.toString()?.takeIf { it.isNotBlank() }
            PartyManager.PartySnapshot(id, leader, maxSize, members, dungeonId)
        }
        val reconnects = config.getConfigurationSection("parties.reconnects")
            ?.getKeys(false)
            ?.mapNotNull { key ->
                runCatching { UUID.fromString(key) }.getOrNull()?.let { it to (config.getString("parties.reconnects.$key") ?: return@mapNotNull null) }
            }
            ?.toMap()
            ?: emptyMap()
        PartyManager.restoreState(parties, reconnects)
    }

    private fun restorePlayers(config: Configuration) {
        val playerMaps = config.getMapList("players.list")
        val runShards = mutableMapOf<UUID, Int>()
        val returnLocations = mutableMapOf<UUID, Location>()

        for (raw in playerMaps) {
            val uuid = raw["uuid"]?.toString()?.let(UUID::fromString) ?: continue
            val shardAmount = raw["run-shards"]?.toString()?.toIntOrNull() ?: 0
            if (shardAmount > 0) {
                runShards[uuid] = shardAmount
            }

            val boons = (raw["boons"] as? List<*>)?.mapNotNull { value ->
                val node = value as? Map<*, *> ?: return@mapNotNull null
                val boon = BoonRegistry.get(node["id"]?.toString() ?: return@mapNotNull null) ?: return@mapNotNull null
                val level = node["level"]?.toString()?.toIntOrNull() ?: 1
                BoonInstance(boon, level.coerceIn(1, boon.maxLevel))
            } ?: emptyList()
            PlayerBoonData.restoreBoons(uuid, boons)

            val relics = (raw["relics"] as? List<*>)?.mapNotNull { id -> id?.toString()?.let(RelicRegistry::get) } ?: emptyList()
            PlayerRelicData.restoreRelics(uuid, relics)

            val accessories = (raw["accessories"] as? List<*>)?.mapNotNull { value ->
                val node = value as? Map<*, *> ?: return@mapNotNull null
                val slot = AccessorySlot.parse(node["slot"]?.toString()) ?: return@mapNotNull null
                val definition = AccessoryRegistry.get(node["id"]?.toString() ?: return@mapNotNull null) ?: return@mapNotNull null
                val rarity = AccessoryRegistry.getRarity(node["rarity"]?.toString() ?: "common") ?: return@mapNotNull null
                val source = runCatching { DungeonLootSource.valueOf(node["source"]?.toString() ?: "CHEST") }.getOrDefault(DungeonLootSource.CHEST)
                val floor = node["floor"]?.toString()?.toIntOrNull() ?: 1
                val score = node["score"]?.toString()?.toDoubleOrNull() ?: 0.0
                val attributes = (node["attributes"] as? Map<*, *>)?.mapNotNull { (key, attrValue) ->
                    val attrName = key?.toString() ?: return@mapNotNull null
                    val amount = attrValue?.toString()?.toDoubleOrNull() ?: return@mapNotNull null
                    attrName to amount
                }?.toMap() ?: emptyMap()
                val effects = (node["effects"] as? List<*>)?.mapNotNull { rawEffect ->
                    val effectNode = rawEffect as? Map<*, *> ?: return@mapNotNull null
                    val type = runCatching { AccessoryEffectType.valueOf(effectNode["type"].toString()) }.getOrNull() ?: return@mapNotNull null
                    AccessoryEffect(
                        type = type,
                        value = effectNode["value"]?.toString()?.toDoubleOrNull() ?: 0.0,
                        chance = effectNode["chance"]?.toString()?.toDoubleOrNull() ?: 1.0,
                        tag = effectNode["tag"]?.toString() ?: ""
                    )
                } ?: definition.effects
                slot to AccessoryInstance(definition, rarity, source, floor, attributes, effects, score)
            }?.toMap() ?: emptyMap()
            PlayerAccessoryData.restore(uuid, accessories)

            val curses = (raw["curses"] as? List<*>)?.mapNotNull { id ->
                runCatching { RunCurseType.valueOf(id.toString()) }.getOrNull()
            }?.toSet() ?: emptySet()
            RunCurseManager.restore(uuid, curses)

            val milestones = (raw["milestones"] as? List<*>)?.mapNotNull { id ->
                runCatching { RunMilestoneType.valueOf(id.toString()) }.getOrNull()
            }?.toSet() ?: emptySet()
            val combatStreak = raw["combat-streak"]?.toString()?.toIntOrNull() ?: 0
            RunMilestoneManager.restore(uuid, milestones, combatStreak)

            val modifiers = (raw["modifiers"] as? List<*>)?.mapNotNull { value ->
                val node = value as? Map<*, *> ?: return@mapNotNull null
                val type = runCatching { RunModifierType.valueOf(node["type"].toString()) }.getOrNull() ?: return@mapNotNull null
                RunModifier(
                    type = type,
                    remainingRooms = node["remaining-rooms"]?.toString()?.toIntOrNull() ?: 0,
                    charges = node["charges"]?.toString()?.toIntOrNull() ?: 0,
                    value = node["value"]?.toString()?.toDoubleOrNull() ?: 0.0,
                    source = node["source"]?.toString() ?: type.displayName,
                    payload = node["payload"]?.toString() ?: ""
                )
            } ?: emptyList()
            RunModifierManager.restore(uuid, modifiers)
            RunSummaryManager.restore(uuid, raw["summary"] as? Map<*, *>)

            val materials = (raw["materials"] as? Map<*, *>)?.mapNotNull { (key, value) ->
                val type = ForgeMaterialType.entries.firstOrNull { it.id.equals(key?.toString(), ignoreCase = true) } ?: return@mapNotNull null
                val amount = value?.toString()?.toIntOrNull() ?: return@mapNotNull null
                type to amount
            }?.toMap() ?: emptyMap()
            ForgeMaterialManager.restore(uuid, materials)

            val returnLocation = (raw["return-location"] as? Map<*, *>)?.let(::deserializeLocation)
            if (returnLocation != null) {
                returnLocations[uuid] = returnLocation
            }
        }

        ShardRewardManager.restoreRunShards(runShards)
        DungeonManager.restoreReturnLocations(returnLocations)
    }

    private fun restoreDungeons(config: Configuration) {
        val dungeonMaps = config.getMapList("dungeons.list")
        var restored = 0

        for (raw in dungeonMaps) {
            val instanceId = raw["id"]?.toString() ?: continue
            val dungeonConfig = deserializeConfig(raw["config"] as? Map<*, *> ?: continue) ?: continue
            val world = VoidWorldManager.createInstanceWorld(instanceId) ?: continue
            val origin = Location(world, 0.0, dungeonConfig.floorLevel.toDouble(), 0.0)
            val generator = DungeonGenerator(world, origin, dungeonConfig)

            val rooms = (raw["rooms"] as? List<*>)?.mapNotNull(::deserializeRoom) ?: emptyList()
            if (rooms.isEmpty()) {
                warning("[RogueCore] 恢复副本 $instanceId 失败: 房间快照为空")
                VoidWorldManager.destroyInstanceWorld(world)
                continue
            }
            val corridors = (raw["corridors"] as? List<*>)?.mapNotNull { text ->
                val parts = text.toString().split(",")
                val x = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
                val z = parts.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
                x to z
            }?.toSet() ?: emptySet()
            val affixes = (raw["affixes"] as? List<*>)?.mapNotNull { AffixRegistry.get(it.toString()) } ?: emptyList()
            val eventAffixes = (raw["event-affixes"] as? List<*>)?.mapNotNull { id ->
                EventAffixManager.getAll().firstOrNull { it.id.equals(id.toString(), ignoreCase = true) }
            } ?: emptyList()
            val hiddenKeys = raw["hidden-keys"]?.toString()?.toIntOrNull() ?: 0
            val completed = raw["completed"]?.toString()?.toBooleanStrictOrNull() ?: false
            val instance = generator.restore(
                instanceId = instanceId,
                rooms = rooms,
                corridorCoords = corridors,
                affixes = affixes,
                eventAffixes = eventAffixes,
                hiddenKeys = hiddenKeys,
                completed = completed
            )
            DungeonManager.restoreDungeon(instance)
            restored++

            val participants = (raw["players"] as? List<*>)?.mapNotNull { value ->
                runCatching { UUID.fromString(value.toString()) }.getOrNull()
            } ?: emptyList()
            for (uuid in participants) {
                if (PartyManager.getParty(uuid)?.dungeonId != instance.id) {
                    PartyManager.restoreDungeonReconnect(uuid, instance.id)
                }
            }
        }

        if (restored > 0) {
            info("[RogueCore] 已恢复 $restored 个未结束的冒险实例")
        }
    }

    private fun serializeConfig(config: DungeonConfig): Map<String, Any> {
        return mapOf(
            "dungeon-width" to config.dungeonWidth,
            "dungeon-depth" to config.dungeonDepth,
            "min-partition-size" to config.minPartitionSize,
            "max-bsp-depth" to config.maxBSPDepth,
            "min-room-size" to config.minRoomSize,
            "room-height" to config.roomHeight,
            "corridor-width" to config.corridorWidth,
            "floor-level" to config.floorLevel,
            "floor-number" to config.floorNumber,
            "hidden-room-enabled" to config.hiddenRoomEnabled,
            "hidden-room-chance" to config.hiddenRoomChance,
            "hidden-elite-key-chance" to config.hiddenEliteKeyChance,
            "hidden-boss-keys" to config.hiddenBossKeys,
            "route" to (config.route?.name ?: ""),
            "target-room-count" to config.targetRoomCount,
            "room-weight-modifiers" to config.roomWeightModifiers.mapKeys { it.key.name },
            "theme" to mapOf(
                "name" to config.theme.name,
                "floor" to config.theme.floor.name,
                "wall" to config.theme.wall.name,
                "ceiling" to config.theme.ceiling.name,
                "accent" to config.theme.accent.name,
                "light" to config.theme.light.name
            )
        )
    }

    private fun deserializeConfig(raw: Map<*, *>): DungeonConfig? {
        val themeNode = raw["theme"] as? Map<*, *> ?: return null
        val theme = FloorTheme(
            name = themeNode["name"]?.toString() ?: "恢复主题",
            floor = FloorTheme.parseMaterial(themeNode["floor"]?.toString() ?: "STONE_BRICKS", FloorTheme.DEFAULT.floor),
            wall = FloorTheme.parseMaterial(themeNode["wall"]?.toString() ?: "STONE_BRICKS", FloorTheme.DEFAULT.wall),
            ceiling = FloorTheme.parseMaterial(themeNode["ceiling"]?.toString() ?: "STONE_BRICKS", FloorTheme.DEFAULT.ceiling),
            accent = FloorTheme.parseMaterial(themeNode["accent"]?.toString() ?: "MOSSY_STONE_BRICKS", FloorTheme.DEFAULT.accent),
            light = FloorTheme.parseMaterial(themeNode["light"]?.toString() ?: "SEA_LANTERN", FloorTheme.DEFAULT.light)
        )
        val route = raw["route"]?.toString()?.takeIf { it.isNotBlank() }?.let {
            runCatching { NextFloorRoute.valueOf(it) }.getOrNull()
        }
        val roomWeightModifiers = (raw["room-weight-modifiers"] as? Map<*, *>)?.mapNotNull { (key, value) ->
            val type = runCatching { RoomType.valueOf(key.toString()) }.getOrNull() ?: return@mapNotNull null
            val weight = value?.toString()?.toIntOrNull() ?: return@mapNotNull null
            type to weight
        }?.toMap() ?: emptyMap()
        return DungeonConfig(
            dungeonWidth = raw["dungeon-width"]?.toString()?.toIntOrNull() ?: 80,
            dungeonDepth = raw["dungeon-depth"]?.toString()?.toIntOrNull() ?: 80,
            minPartitionSize = raw["min-partition-size"]?.toString()?.toIntOrNull() ?: 16,
            maxBSPDepth = raw["max-bsp-depth"]?.toString()?.toIntOrNull() ?: 4,
            minRoomSize = raw["min-room-size"]?.toString()?.toIntOrNull() ?: 12,
            roomHeight = raw["room-height"]?.toString()?.toIntOrNull() ?: 4,
            corridorWidth = raw["corridor-width"]?.toString()?.toIntOrNull() ?: 3,
            theme = theme,
            floorLevel = raw["floor-level"]?.toString()?.toIntOrNull() ?: 64,
            floorNumber = raw["floor-number"]?.toString()?.toIntOrNull() ?: 1,
            hiddenRoomEnabled = raw["hidden-room-enabled"]?.toString()?.toBooleanStrictOrNull() ?: true,
            hiddenRoomChance = raw["hidden-room-chance"]?.toString()?.toDoubleOrNull() ?: 0.35,
            hiddenEliteKeyChance = raw["hidden-elite-key-chance"]?.toString()?.toDoubleOrNull() ?: 0.35,
            hiddenBossKeys = raw["hidden-boss-keys"]?.toString()?.toIntOrNull() ?: 1,
            route = route,
            targetRoomCount = raw["target-room-count"]?.toString()?.toIntOrNull() ?: 10,
            roomWeightModifiers = roomWeightModifiers
        )
    }

    private fun deserializeRoom(raw: Any?): Room? {
        val node = raw as? Map<*, *> ?: return null
        val type = node["type"]?.toString()?.let { runCatching { RoomType.valueOf(it) }.getOrNull() } ?: RoomType.COMBAT
        val room = Room(
            id = node["id"]?.toString()?.toIntOrNull() ?: return null,
            x = node["x"]?.toString()?.toIntOrNull() ?: return null,
            z = node["z"]?.toString()?.toIntOrNull() ?: return null,
            width = node["width"]?.toString()?.toIntOrNull() ?: return null,
            depth = node["depth"]?.toString()?.toIntOrNull() ?: return null,
            type = type,
            connections = ((node["connections"] as? List<*>)?.mapNotNull { it?.toString()?.toIntOrNull() } ?: emptyList()).toMutableList()
        )
        room.state = when (node["state"]?.toString()?.let { runCatching { RoomState.valueOf(it) }.getOrNull() } ?: RoomState.IDLE) {
            RoomState.ACTIVE -> RoomState.IDLE
            else -> node["state"]?.toString()?.let { runCatching { RoomState.valueOf(it) }.getOrNull() } ?: RoomState.IDLE
        }
        room.spawnedMobCount = if (room.state == RoomState.CLEARED) {
            node["spawned-mob-count"]?.toString()?.toIntOrNull() ?: 0
        } else {
            0
        }
        room.aliveMobs.clear()
        return room
    }

    private fun serializeLocation(location: Location): Map<String, Any> {
        return mapOf(
            "world" to (location.world?.name ?: Bukkit.getWorlds().firstOrNull()?.name.orEmpty()),
            "x" to location.x,
            "y" to location.y,
            "z" to location.z,
            "yaw" to location.yaw,
            "pitch" to location.pitch
        )
    }

    private fun deserializeLocation(raw: Map<*, *>): Location? {
        val worldName = raw["world"]?.toString() ?: return null
        val world = Bukkit.getWorld(worldName) ?: Bukkit.getWorlds().firstOrNull() ?: return null
        return Location(
            world,
            raw["x"]?.toString()?.toDoubleOrNull() ?: world.spawnLocation.x,
            raw["y"]?.toString()?.toDoubleOrNull() ?: world.spawnLocation.y,
            raw["z"]?.toString()?.toDoubleOrNull() ?: world.spawnLocation.z,
            raw["yaw"]?.toString()?.toFloatOrNull() ?: 0f,
            raw["pitch"]?.toString()?.toFloatOrNull() ?: 0f
        )
    }

    private fun deleteSnapshot() {
        if (snapshotFile.exists()) {
            snapshotFile.delete()
        }
    }
}
