package inori.roguecore.listener

import inori.roguecore.combat.RoomCombatManager
import inori.roguecore.data.DatabaseManager
import inori.roguecore.data.ShardRewardManager
import inori.roguecore.dungeon.DungeonManager
import inori.roguecore.party.PartyManager
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerRespawnEvent
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.submit

/**
 * 副本事件监听器
 */
object DungeonListener {

    @SubscribeEvent
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val uuid = event.player.uniqueId
        submit(async = true) {
            DatabaseManager.preload(uuid)
        }
    }

    /**
     * 玩家移动 — 检测是否进入新房间
     */
    @SubscribeEvent
    fun onPlayerMove(event: PlayerMoveEvent) {
        val from = event.from
        val to = event.to ?: return
        if (from.blockX == to.blockX && from.blockZ == to.blockZ) return

        val player = event.player
        if (!DungeonManager.isInDungeon(player)) return

        RoomCombatManager.checkPlayerRoom(player)
    }

    /**
     * 实体死亡 — 检测怪物是否被击杀
     */
    @SubscribeEvent
    fun onEntityDeath(event: EntityDeathEvent) {
        val entity = event.entity
        if (!entity.world.name.startsWith("rogue_")) return
        RoomCombatManager.onMobDeath(entity)
    }

    /**
     * 玩家死亡重生 — 死亡惩罚（碎片打折）+ 踢出副本
     */
    @SubscribeEvent
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        val player = event.player
        if (!DungeonManager.isInDungeon(player)) return

        // 死亡结算碎片（打折）
        val shards = ShardRewardManager.settleDeath(player.uniqueId)
        player.sendMessage("§c你在副本中死亡!")
        if (shards > 0) {
            player.sendMessage("§e本次冒险结算 §6$shards §e灵魂碎片 §7(死亡惩罚)")
        }
        DungeonManager.leaveDungeon(player)
    }

    /**
     * 玩家下线 — 从副本移除
     */
    @SubscribeEvent
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        if (DungeonManager.isInDungeon(player)) {
            DungeonManager.leaveDungeon(player)
        }

        // 清理队伍邀请
        PartyManager.onPlayerQuit(player)

        // 卸载缓存
        DatabaseManager.release(player.uniqueId)
    }
}
