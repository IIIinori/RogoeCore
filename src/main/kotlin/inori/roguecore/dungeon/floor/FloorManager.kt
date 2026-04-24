package inori.roguecore.dungeon.floor

import inori.roguecore.dungeon.DungeonConfig
import inori.roguecore.dungeon.route.NextFloorRoute
import inori.roguecore.dungeon.room.RoomType
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.info
import taboolib.common.platform.function.warning
import taboolib.library.xseries.XMaterial
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

/**
 * 楼层管理器 — 从 config.yml 加载楼层配置
 */
object FloorManager {

    @Config("config.yml")
    lateinit var config: Configuration
        private set

    /** 楼层范围 -> (主题, BSP深度) */
    private val floorThemes = mutableListOf<FloorEntry>()

    // 地牢全局参数
    private var baseWidth = 80
    private var widthPerFloor = 5
    private var minPartitionSize = 16
    private var minRoomSize = 12
    private var roomHeight = 4
    private var corridorWidth = 3
    private var floorLevel = 64
    private var hiddenRoomEnabled = true
    private var hiddenRoomChance = 0.35
    private var hiddenEliteKeyChance = 0.35
    private var hiddenBossKeys = 1

    private data class FloorEntry(
        val minFloor: Int,
        val maxFloor: Int,
        val theme: FloorTheme,
        val bspDepth: Int
    )

    @Awake(LifeCycle.ENABLE)
    fun load() {
        floorThemes.clear()

        // 全局参数
        baseWidth = config.getInt("dungeon.base-width", 80)
        widthPerFloor = config.getInt("dungeon.width-per-floor", 5)
        minPartitionSize = config.getInt("dungeon.min-partition-size", 16)
        minRoomSize = config.getInt("dungeon.min-room-size", 12)
        roomHeight = config.getInt("dungeon.room-height", 4)
        corridorWidth = config.getInt("dungeon.corridor-width", 3)
        floorLevel = config.getInt("dungeon.floor-level", 64)
        hiddenRoomEnabled = config.getBoolean("dungeon.hidden-room.enabled", true)
        hiddenRoomChance = config.getDouble("dungeon.hidden-room.chance", 0.35)
        hiddenEliteKeyChance = config.getDouble("dungeon.hidden-room.elite-key-chance", 0.35)
        hiddenBossKeys = config.getInt("dungeon.hidden-room.boss-keys", 1).coerceAtLeast(0)

        // 楼层主题
        val floorsSection = config.getConfigurationSection("floors")
        if (floorsSection == null) {
            warning("[RogueCore] config.yml 中未找到 floors 节点，使用默认配置")
            floorThemes.add(FloorEntry(1, 999, FloorTheme.DEFAULT, 4))
            return
        }

        for (key in floorsSection.getKeys(false)) {
            val section = floorsSection.getConfigurationSection(key) ?: continue
            try {
                val name = section.getString("name") ?: key
                val rangeStr = section.getString("range") ?: "1-999"
                val rangeParts = rangeStr.split("-")
                val minFloor = rangeParts.getOrNull(0)?.toIntOrNull() ?: 1
                val maxFloor = rangeParts.getOrNull(1)?.toIntOrNull() ?: 999
                val bspDepth = section.getInt("bsp-depth", 4)

                val theme = FloorTheme(
                    name = name,
                    floor = FloorTheme.parseMaterial(section.getString("floor") ?: "STONE_BRICKS", XMaterial.STONE_BRICKS),
                    wall = FloorTheme.parseMaterial(section.getString("wall") ?: "STONE_BRICKS", XMaterial.STONE_BRICKS),
                    ceiling = FloorTheme.parseMaterial(section.getString("ceiling") ?: "STONE_BRICKS", XMaterial.STONE_BRICKS),
                    accent = FloorTheme.parseMaterial(section.getString("accent") ?: "MOSSY_STONE_BRICKS", XMaterial.MOSSY_STONE_BRICKS),
                    light = FloorTheme.parseMaterial(section.getString("light") ?: "SEA_LANTERN", XMaterial.SEA_LANTERN)
                )

                floorThemes.add(FloorEntry(minFloor, maxFloor, theme, bspDepth))
            } catch (e: Exception) {
                warning("[RogueCore] 加载楼层主题 '$key' 失败: ${e.message}")
            }
        }

        // 按 minFloor 排序
        floorThemes.sortBy { it.minFloor }
        info("[RogueCore] 已加载 ${floorThemes.size} 个楼层主题")
    }

    /**
     * 获取指定楼层的地牢配置
     */
    fun getFloorConfig(
        floorNumber: Int,
        route: NextFloorRoute? = null,
        extraRoomWeightModifiers: Map<RoomType, Int> = emptyMap(),
        extraHiddenRoomChanceBonus: Double = 0.0
    ): DungeonConfig {
        val entry = floorThemes.firstOrNull { floorNumber in it.minFloor..it.maxFloor }
            ?: floorThemes.lastOrNull()
            ?: FloorEntry(1, 999, FloorTheme.DEFAULT, 4)

        val size = baseWidth + (floorNumber - 1) * widthPerFloor

        return DungeonConfig(
            dungeonWidth = size,
            dungeonDepth = size,
            minPartitionSize = minPartitionSize,
            maxBSPDepth = entry.bspDepth,
            minRoomSize = minRoomSize,
            roomHeight = roomHeight,
            corridorWidth = corridorWidth,
            theme = entry.theme,
            floorLevel = floorLevel,
            floorNumber = floorNumber,
            hiddenRoomEnabled = hiddenRoomEnabled,
            hiddenRoomChance = (hiddenRoomChance + (route?.hiddenRoomChanceBonus ?: 0.0) + extraHiddenRoomChanceBonus).coerceAtMost(1.0),
            hiddenEliteKeyChance = hiddenEliteKeyChance,
            hiddenBossKeys = hiddenBossKeys,
            route = route,
            roomWeightModifiers = (route?.roomWeightModifiers ?: emptyMap()) + extraRoomWeightModifiers.mapValues { (type, value) ->
                value + ((route?.roomWeightModifiers ?: emptyMap())[type] ?: 0)
            }
        )
    }
}
