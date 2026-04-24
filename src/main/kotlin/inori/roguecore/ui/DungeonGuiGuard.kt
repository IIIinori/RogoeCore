package inori.roguecore.ui

import inori.roguecore.dungeon.DungeonManager
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryCloseEvent
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.submit
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 副本内强制选择 GUI 守卫。
 *
 * 参考 InoriHicks 的做法：关闭事件本身不能取消，关闭后延迟 2 tick 重新打开。
 */
object DungeonGuiGuard {

    private data class LockedGui(
        val title: String,
        val reopen: (Player) -> Unit
    )

    private val lockedGui = ConcurrentHashMap<UUID, LockedGui>()

    fun lock(player: Player, title: String, reopen: (Player) -> Unit) {
        if (!DungeonManager.isInDungeon(player)) {
            lockedGui.remove(player.uniqueId)
            return
        }
        lockedGui[player.uniqueId] = LockedGui(title, reopen)
    }

    fun unlock(player: Player) {
        lockedGui.remove(player.uniqueId)
    }

    @SubscribeEvent
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        val locked = lockedGui[player.uniqueId] ?: return

        if (event.view.title != locked.title) {
            return
        }

        submit(delay = 2L) {
            val current = lockedGui[player.uniqueId] ?: return@submit
            if (current.title != locked.title) {
                return@submit
            }
            if (!player.isOnline || !DungeonManager.isInDungeon(player)) {
                lockedGui.remove(player.uniqueId)
                return@submit
            }
            current.reopen(player)
        }
    }
}
