package inori.roguecore.dungeon.generator

import inori.roguecore.dungeon.room.Room
import inori.roguecore.dungeon.room.RoomType
import kotlin.random.Random

/**
 * BSP 节点 — 表示一个矩形分区
 */
data class BSPNode(
    val x: Int,
    val z: Int,
    val width: Int,
    val depth: Int,
    var left: BSPNode? = null,
    var right: BSPNode? = null,
    var room: Room? = null
) {
    val isLeaf: Boolean get() = left == null && right == null
}

/**
 * BSP 二叉空间分割算法
 * 将矩形区域递归二分，在叶节点中生成房间
 */
class BSPTree(
    private val totalWidth: Int,
    private val totalDepth: Int,
    private val minPartitionSize: Int = 16,
    private val minRoomSize: Int = 12,
    private val maxDepth: Int = 4,
    private val random: Random = Random.Default
) {

    private var roomIdCounter = 0
    private lateinit var root: BSPNode

    /**
     * 执行 BSP 分割并生成房间
     * @return 所有生成的房间列表
     */
    fun generate(): List<Room> {
        roomIdCounter = 0
        root = BSPNode(0, 0, totalWidth, totalDepth)
        split(root, 0)
        createRooms(root)
        return collectRooms(root)
    }

    /**
     * 获取需要连接的房间对（相邻叶节点）
     */
    fun getConnectionPairs(): List<Pair<Room, Room>> {
        val pairs = mutableListOf<Pair<Room, Room>>()
        collectPairs(root, pairs)
        return pairs
    }

    /**
     * 递归分割节点
     */
    private fun split(node: BSPNode, depth: Int) {
        if (depth >= maxDepth) return

        // 判断是否可以分割
        val canSplitH = node.depth >= minPartitionSize * 2
        val canSplitV = node.width >= minPartitionSize * 2

        if (!canSplitH && !canSplitV) return

        // 选择分割方向：偏向长边
        val splitHorizontal = when {
            !canSplitH -> false
            !canSplitV -> true
            node.depth > node.width * 1.25 -> true
            node.width > node.depth * 1.25 -> false
            else -> random.nextBoolean()
        }

        if (splitHorizontal) {
            // 水平分割（沿 Z 轴切）
            val splitPos = randomSplitPos(node.depth)
            node.left = BSPNode(node.x, node.z, node.width, splitPos)
            node.right = BSPNode(node.x, node.z + splitPos, node.width, node.depth - splitPos)
        } else {
            // 垂直分割（沿 X 轴切）
            val splitPos = randomSplitPos(node.width)
            node.left = BSPNode(node.x, node.z, splitPos, node.depth)
            node.right = BSPNode(node.x + splitPos, node.z, node.width - splitPos, node.depth)
        }

        split(node.left!!, depth + 1)
        split(node.right!!, depth + 1)
    }

    /**
     * 在 40%-60% 的位置分割
     */
    private fun randomSplitPos(size: Int): Int {
        val min = (size * 0.4).toInt()
        val max = (size * 0.6).toInt()
        return random.nextInt(min, max + 1)
    }

    /**
     * 在叶节点中创建房间（留出边距）
     */
    private fun createRooms(node: BSPNode) {
        if (node.isLeaf) {
            val padding = 2
            val minRoomDim = minRoomSize.coerceAtLeast(2 * padding + 3)

            val maxW = node.width - padding * 2
            val maxD = node.depth - padding * 2

            if (maxW < minRoomDim || maxD < minRoomDim) return

            val preferredMinW = maxOf(minRoomDim, (maxW * 0.75).toInt())
            val preferredMinD = maxOf(minRoomDim, (maxD * 0.75).toInt())
            val roomW = random.nextInt(preferredMinW, maxW + 1)
            val roomD = random.nextInt(preferredMinD, maxD + 1)
            val roomX = node.x + padding + random.nextInt(0, maxW - roomW + 1)
            val roomZ = node.z + padding + random.nextInt(0, maxD - roomD + 1)

            node.room = Room(
                id = roomIdCounter++,
                x = roomX,
                z = roomZ,
                width = roomW,
                depth = roomD
            )
            return
        }
        node.left?.let { createRooms(it) }
        node.right?.let { createRooms(it) }
    }

    /**
     * 收集所有叶节点的房间
     */
    private fun collectRooms(node: BSPNode): List<Room> {
        if (node.isLeaf) {
            return listOfNotNull(node.room)
        }
        val rooms = mutableListOf<Room>()
        node.left?.let { rooms.addAll(collectRooms(it)) }
        node.right?.let { rooms.addAll(collectRooms(it)) }
        return rooms
    }

    /**
     * 收集需要连接的房间对
     * 每个非叶节点的左右子树各取一个最近的房间进行连接
     */
    private fun collectPairs(node: BSPNode, pairs: MutableList<Pair<Room, Room>>) {
        if (node.isLeaf) return

        val leftRoom = findClosestRoom(node.left, fromRight = true)
        val rightRoom = findClosestRoom(node.right, fromRight = false)

        if (leftRoom != null && rightRoom != null) {
            leftRoom.connections.add(rightRoom.id)
            rightRoom.connections.add(leftRoom.id)
            pairs.add(leftRoom to rightRoom)
        }

        node.left?.let { collectPairs(it, pairs) }
        node.right?.let { collectPairs(it, pairs) }
    }

    /**
     * 在子树中找到最靠近分割线的房间
     */
    private fun findClosestRoom(node: BSPNode?, fromRight: Boolean): Room? {
        if (node == null) return null
        if (node.isLeaf) return node.room

        // 优先从靠近分割线的一侧找
        val first = if (fromRight) node.right else node.left
        val second = if (fromRight) node.left else node.right

        return findClosestRoom(first, fromRight) ?: findClosestRoom(second, fromRight)
    }
}
