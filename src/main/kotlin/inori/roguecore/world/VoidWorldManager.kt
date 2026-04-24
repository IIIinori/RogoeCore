package inori.roguecore.world

import org.bukkit.Bukkit
import org.bukkit.GameRule
import org.bukkit.World
import org.bukkit.WorldCreator
import org.bukkit.WorldType
import org.bukkit.generator.ChunkGenerator
import org.bukkit.generator.WorldInfo
import taboolib.common.platform.function.info
import taboolib.common.platform.function.warning
import java.io.File

/**
 * 虚空世界管理器
 * 每个副本实例对应一个独立的虚空世界，完全隔离
 */
object VoidWorldManager {

    private const val WORLD_PREFIX = "rogue_"

    /** 共享的虚空区块生成器实例 */
    private val voidGenerator = VoidChunkGenerator()

    /**
     * 为副本创建一个独立的虚空世界
     * @param instanceId 副本实例 ID，用于生成世界名
     * @return 创建好的世界，失败返回 null
     */
    fun createInstanceWorld(instanceId: String): World? {
        val worldName = "$WORLD_PREFIX$instanceId"

        // 防止重名
        Bukkit.getWorld(worldName)?.let {
            warning("[RogueCore] 世界 $worldName 已存在，直接复用")
            return applyRules(it)
        }

        val creator = WorldCreator(worldName)
            .type(WorldType.FLAT)
            .generator(voidGenerator)
            .generateStructures(false)
            .environment(World.Environment.NORMAL)

        val world = Bukkit.createWorld(creator)
        if (world == null) {
            warning("[RogueCore] 创建世界 $worldName 失败!")
            return null
        }

        info("[RogueCore] 副本世界 $worldName 已创建")
        return applyRules(world)
    }

    /**
     * 销毁副本世界 — 卸载并删除世界文件
     * @param world 要销毁的世界
     */
    fun destroyInstanceWorld(world: World) {
        val worldName = world.name
        val worldFolder = world.worldFolder

        // 先把世界里的玩家踢到主世界
        val mainWorld = Bukkit.getWorlds().firstOrNull() ?: return
        for (player in world.players) {
            player.teleport(mainWorld.spawnLocation)
        }

        // 卸载世界（不保存）
        Bukkit.unloadWorld(world, false)

        // 异步删除世界文件夹
        deleteWorldFolder(worldFolder)
        info("[RogueCore] 副本世界 $worldName 已销毁")
    }

    /**
     * 销毁所有副本世界（关服时调用）
     */
    fun destroyAll() {
        val rogueWorlds = Bukkit.getWorlds().filter { it.name.startsWith(WORLD_PREFIX) }
        for (world in rogueWorlds) {
            destroyInstanceWorld(world)
        }
    }

    /**
     * 应用副本世界规则
     */
    private fun applyRules(world: World): World {
        world.apply {
            setGameRule(GameRule.DO_MOB_SPAWNING, false)
            setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false)
            setGameRule(GameRule.DO_WEATHER_CYCLE, false)
            setGameRule(GameRule.DO_FIRE_TICK, false)
            setGameRule(GameRule.MOB_GRIEFING, false)
            setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false)
            setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true)
            setGameRule(GameRule.KEEP_INVENTORY, true)
            setSpawnFlags(false, false)
            time = 6000
            setStorm(false)
            isThundering = false
            isAutoSave = false
        }
        return world
    }

    /**
     * 递归删除世界文件夹
     */
    private fun deleteWorldFolder(folder: File) {
        if (!folder.exists()) return
        folder.deleteRecursively()
    }

    /**
     * 虚空区块生成器
     */
    private class VoidChunkGenerator : ChunkGenerator() {
        override fun generateNoise(
            worldInfo: WorldInfo,
            random: java.util.Random,
            chunkX: Int,
            chunkZ: Int,
            chunkData: ChunkData
        ) {
            // 全空气
        }

        override fun shouldGenerateNoise(): Boolean = false
        override fun shouldGenerateSurface(): Boolean = false
        override fun shouldGenerateCaves(): Boolean = false
        override fun shouldGenerateDecorations(): Boolean = false
        override fun shouldGenerateMobs(): Boolean = false
        override fun shouldGenerateStructures(): Boolean = false
        override fun canSpawn(world: World, x: Int, z: Int): Boolean = true
    }
}
