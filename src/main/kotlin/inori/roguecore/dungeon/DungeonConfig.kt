package inori.roguecore.dungeon

import inori.roguecore.dungeon.floor.FloorTheme
import inori.roguecore.dungeon.room.RoomType
import inori.roguecore.dungeon.route.NextFloorRoute

/**
 * 地牢生成配置
 */
data class DungeonConfig(
    /** 地牢总宽（X 方向，方块数） */
    val dungeonWidth: Int = 80,
    /** 地牢总深（Z 方向，方块数） */
    val dungeonDepth: Int = 80,
    /** BSP 最小分区尺寸 */
    val minPartitionSize: Int = 16,
    /** BSP 最大递归深度 */
    val maxBSPDepth: Int = 4,
    /** 房间最小外框尺寸 */
    val minRoomSize: Int = 12,
    /** 房间净高（不含地板和天花板） */
    val roomHeight: Int = 4,
    /** 走廊宽度 */
    val corridorWidth: Int = 3,
    /** 楼层主题 */
    val theme: FloorTheme = FloorTheme.DEFAULT,
    /** 生成 Y 坐标（地板层） */
    val floorLevel: Int = 64,
    /** 楼层编号（影响怪物数量与副本规模） */
    val floorNumber: Int = 1,
    /** 是否启用隐藏房 */
    val hiddenRoomEnabled: Boolean = true,
    /** 本层生成隐藏房概率 */
    val hiddenRoomChance: Double = 0.35,
    /** 精英房掉落隐藏钥匙的概率 */
    val hiddenEliteKeyChance: Double = 0.35,
    /** Boss 房固定掉落隐藏钥匙数量 */
    val hiddenBossKeys: Int = 1,
    /** 下一层路线 */
    val route: NextFloorRoute? = null,
    /** 房间类型额外权重修正 */
    val roomWeightModifiers: Map<RoomType, Int> = emptyMap()
)
