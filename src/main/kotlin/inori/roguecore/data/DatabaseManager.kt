package inori.roguecore.data

import inori.roguecore.talent.TalentManager
import org.bukkit.entity.Player
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.info
import taboolib.common.platform.function.warning
import taboolib.expansion.DataContainer
import taboolib.expansion.getPlayerDataContainer
import taboolib.expansion.releasePlayerDataContainer
import taboolib.expansion.setupPlayerDataContainer
import taboolib.expansion.setupPlayerDatabase
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID

/**
 * 玩家数据容器入口。
 *
 * 当前永久数据统一进入 TabooLib DatabasePlayer 的 key-value 容器。
 */
object DatabaseManager {

    private const val DEFAULT_TABLE = "roguecore_playerdata"
    private const val MIGRATION_MARKER = "__legacy_migrated_v1"

    @Config("config.yml")
    lateinit var config: Configuration
        private set

    private lateinit var databaseFile: File
    @Volatile
    private var shuttingDown = false

    @Awake(LifeCycle.ENABLE)
    fun init() {
        shuttingDown = false
        val fileName = config.getString("database.file") ?: "data.db"
        val tableName = config.getString("database.table") ?: DEFAULT_TABLE
        databaseFile = File(getDataFolder(), fileName)
        setupPlayerDatabase(databaseFile, tableName)
        info("[RogueCore] 玩家数据库容器已连接: ${databaseFile.name}#$tableName")
    }

    fun preload(uuid: UUID) {
        getOrCreateContainer(uuid)
    }

    fun release(uuid: UUID) {
        uuid.releasePlayerDataContainer()
    }

    fun markShuttingDown() {
        shuttingDown = true
    }

    fun isShuttingDown(): Boolean {
        return shuttingDown
    }

    fun getOrCreateContainer(uuid: UUID): DataContainer {
        val container = runCatching { uuid.getPlayerDataContainer() }.getOrElse {
            uuid.setupPlayerDataContainer()
            uuid.getPlayerDataContainer()
        }
        migrateLegacyData(uuid, container)
        return container
    }

    fun getOrCreateContainer(player: Player): DataContainer {
        return getOrCreateContainer(player.uniqueId)
    }

    private fun migrateLegacyData(uuid: UUID, container: DataContainer) {
        if (container[MIGRATION_MARKER] == "true") return

        try {
            DriverManager.getConnection("jdbc:sqlite:${databaseFile.absolutePath}").use { conn ->
                migrateLegacyPlayer(uuid, container, conn)
                migrateLegacyTalents(uuid, container, conn)
            }
        } catch (e: Exception) {
            warning("[RogueCore] 迁移旧玩家数据失败 $uuid: ${e.message}")
        } finally {
            container[MIGRATION_MARKER] = true
        }
    }

    private fun migrateLegacyPlayer(uuid: UUID, container: DataContainer, conn: Connection) {
        if (!tableExists(conn, "rogue_player")) return

        conn.prepareStatement("SELECT * FROM rogue_player WHERE uuid = ?").use { ps ->
            ps.setString(1, uuid.toString())
            val rs = ps.executeQuery()
            if (!rs.next()) return

            if (container[PlayerDataManager.KEY_SOUL_SHARDS] == null) {
                container[PlayerDataManager.KEY_SOUL_SHARDS] = rs.getInt("soul_shards")
            }
            if (container[PlayerDataManager.KEY_TOTAL_RUNS] == null) {
                container[PlayerDataManager.KEY_TOTAL_RUNS] = rs.getInt("total_runs")
            }
            if (container[PlayerDataManager.KEY_TOTAL_CLEARS] == null) {
                container[PlayerDataManager.KEY_TOTAL_CLEARS] = rs.getInt("total_clears")
            }
            if (container[PlayerDataManager.KEY_BEST_FLOOR] == null) {
                container[PlayerDataManager.KEY_BEST_FLOOR] = rs.getInt("best_floor")
            }
            if (container[PlayerDataManager.KEY_TOTAL_KILLS] == null) {
                container[PlayerDataManager.KEY_TOTAL_KILLS] = rs.getInt("total_kills")
            }
        }
    }

    private fun migrateLegacyTalents(uuid: UUID, container: DataContainer, conn: Connection) {
        if (!tableExists(conn, "rogue_talent")) return

        conn.prepareStatement("SELECT talent_id, level FROM rogue_talent WHERE uuid = ?").use { ps ->
            ps.setString(1, uuid.toString())
            val rs = ps.executeQuery()
            while (rs.next()) {
                val key = TalentManager.talentKey(rs.getString("talent_id"))
                if (container[key] == null) {
                    container[key] = rs.getInt("level")
                }
            }
        }
    }

    private fun tableExists(conn: Connection, tableName: String): Boolean {
        return conn.prepareStatement("SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?").use { ps ->
            ps.setString(1, tableName)
            ps.executeQuery().use { it.next() }
        }
    }
}
