package inori.roguecore.listener

import inori.roguecore.combat.RoomCombatManager
import inori.roguecore.data.DatabaseManager
import inori.roguecore.data.ShardRewardManager
import inori.roguecore.stats.BalanceStatsManager
import inori.roguecore.summary.RunEndReason
import inori.roguecore.summary.RunSummaryManager
import inori.roguecore.dungeon.DungeonManager
import inori.roguecore.item.ForgeBookTaskManager
import inori.roguecore.item.IdentificationTaskManager
import inori.roguecore.party.PartyManager
import org.bukkit.entity.Player
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockExplodeEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.player.PlayerBucketEmptyEvent
import org.bukkit.event.player.PlayerBucketFillEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.event.player.PlayerTeleportEvent
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.submit
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 副本事件监听器
 */
object DungeonListener {

    private val protectionWarnAt = ConcurrentHashMap<UUID, Long>()

    @SubscribeEvent
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        val uuid = player.uniqueId
        submit(async = true) {
            DatabaseManager.preload(uuid)
        }
        DungeonManager.processPendingJoinState(player)
        PartyManager.onPlayerJoin(player)
        if (DungeonManager.canRejoinDungeon(uuid)) {
            player.sendMessage("§e你有一场未结束的冒险，输入 §f/rogue rejoin §e可返回副本。")
        }
        val completedIdentify = IdentificationTaskManager.getCompletedCount(uuid)
        if (completedIdentify > 0) {
            player.sendMessage("§e你有 §a$completedIdentify §e个已完成的装备鉴定，输入 §f/rogue identify §e可一键领取。")
        }
        val completedForge = ForgeBookTaskManager.getCompletedCount(uuid)
        if (completedForge > 0) {
            player.sendMessage("§e你有 §a$completedForge §e个已完成的装备打造，输入 §f/rogue craft §e可一键领取。")
        }
    }

    /**
     * 玩家移动 — 先处理房间封锁，再检测是否进入新房间
     */
    @SubscribeEvent
    fun onPlayerMove(event: PlayerMoveEvent) {
        val from = event.from
        val to = event.to ?: return
        if (from.blockX == to.blockX && from.blockZ == to.blockZ) return

        val player = event.player
        if (!DungeonManager.isInDungeon(player)) return

        if (RoomCombatManager.handleMovement(player, from, to)) {
            event.setTo(from)
            return
        }

        RoomCombatManager.checkPlayerRoom(player)
    }

    @SubscribeEvent(ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        if (!isDungeonWorld(event.block.world.name)) {
            return
        }
        event.isCancelled = true
        warnProtection(event.player, "§c副本内禁止破坏地形。")
    }

    @SubscribeEvent(ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        if (!isDungeonWorld(event.block.world.name)) {
            return
        }
        event.isCancelled = true
        warnProtection(event.player, "§c副本内禁止放置方块。")
    }

    @SubscribeEvent(ignoreCancelled = true)
    fun onBucketEmpty(event: PlayerBucketEmptyEvent) {
        if (!isDungeonWorld(event.blockClicked.world.name)) {
            return
        }
        event.isCancelled = true
        warnProtection(event.player, "§c副本内禁止修改地形。")
    }

    @SubscribeEvent(ignoreCancelled = true)
    fun onBucketFill(event: PlayerBucketFillEvent) {
        if (!isDungeonWorld(event.blockClicked.world.name)) {
            return
        }
        event.isCancelled = true
        warnProtection(event.player, "§c副本内禁止修改地形。")
    }

    @SubscribeEvent(ignoreCancelled = true)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = event.player
        if (!isDungeonWorld(player.world.name)) {
            return
        }
        if (event.action != Action.PHYSICAL && event.clickedBlock == null) {
            return
        }
        event.isCancelled = true
        warnProtection(player, "§c副本内禁止操作场景方块。")
    }

    @SubscribeEvent(ignoreCancelled = true)
    fun onPlayerTeleport(event: PlayerTeleportEvent) {
        val player = event.player
        val fromWorld = event.from.world ?: return
        if (!isDungeonWorld(fromWorld.name)) {
            return
        }
        if (player.hasPermission("roguecore.admin")) {
            return
        }
        when (event.cause) {
            PlayerTeleportEvent.TeleportCause.ENDER_PEARL,
            PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT,
            PlayerTeleportEvent.TeleportCause.COMMAND,
            PlayerTeleportEvent.TeleportCause.SPECTATE,
            PlayerTeleportEvent.TeleportCause.NETHER_PORTAL,
            PlayerTeleportEvent.TeleportCause.END_PORTAL -> {
                event.isCancelled = true
                warnProtection(player, "§c副本内无法使用该方式传送。")
            }
            else -> Unit
        }
    }

    @SubscribeEvent(ignoreCancelled = true)
    fun onEntityExplode(event: EntityExplodeEvent) {
        val world = event.location.world ?: return
        if (!isDungeonWorld(world.name)) {
            return
        }
        event.blockList().clear()
    }

    @SubscribeEvent(ignoreCancelled = true)
    fun onBlockExplode(event: BlockExplodeEvent) {
        if (!isDungeonWorld(event.block.world.name)) {
            return
        }
        event.blockList().clear()
    }

    /**
     * 实体死亡 — 检测怪物是否被击杀
     */
    @SubscribeEvent
    fun onEntityDeath(event: EntityDeathEvent) {
        val entity = event.entity
        if (!isDungeonWorld(entity.world.name)) return
        RoomCombatManager.onMobDeath(entity)
    }

    /**
     * 玩家死亡重生 — 死亡惩罚（碎片打折）+ 踢出副本
     */
    @SubscribeEvent
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        val player = event.player
        if (!DungeonManager.isInDungeon(player)) return

        DungeonManager.getPlayerDungeon(player)?.let { BalanceStatsManager.recordFloorDeath(it.config.floorNumber) }
        RunSummaryManager.markEndReason(player.uniqueId, RunEndReason.DEATH)

        // 死亡结算碎片（打折）
        val shards = ShardRewardManager.settleDeath(player.uniqueId)
        player.sendMessage("§c你在副本中死亡!")
        if (shards > 0) {
            player.sendMessage("§e本次冒险结算 §6$shards §e灵魂碎片 §7(死亡惩罚)")
        }
        DungeonManager.leaveDungeon(player)
    }

    /**
     * 玩家下线 — 从副本断线离场，保留重连资格
     */
    @SubscribeEvent
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        if (DungeonManager.isInDungeon(player)) {
            DungeonManager.handleDisconnect(player)
        }

        protectionWarnAt.remove(player.uniqueId)

        // 清理队伍邀请
        PartyManager.onPlayerQuit(player)

        // 卸载缓存
        DatabaseManager.release(player.uniqueId)
    }

    private fun isDungeonWorld(worldName: String): Boolean {
        return worldName.startsWith("rogue_")
    }

    private fun warnProtection(player: Player, message: String) {
        val now = System.currentTimeMillis()
        val last = protectionWarnAt[player.uniqueId] ?: 0L
        if (now - last < 1000L) {
            return
        }
        protectionWarnAt[player.uniqueId] = now
        player.sendMessage(message)
    }
}
