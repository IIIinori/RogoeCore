package inori.roguecore.item

import inori.roguecore.dungeon.DungeonManager
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.PlayerInventory
import org.bukkit.persistence.PersistentDataType
import taboolib.common.platform.event.SubscribeEvent

/**
 * 副本绑定物品工具。
 *
 * 任何带有该 NBT 标记的物品都不能被带出副本。
 */
object DungeonBoundItem {

    private val boundKey = NamespacedKey("roguecore", "dungeon_bound")

    /**
     * 给物品打上副本绑定标记。
     */
    fun mark(item: ItemStack?): ItemStack? {
        if (item == null) {
            return null
        }
        val meta = item.itemMeta ?: return item
        meta.persistentDataContainer.set(boundKey, PersistentDataType.BYTE, 1)
        item.itemMeta = meta
        return item
    }

    /**
     * 清除物品上的副本绑定标记。
     */
    fun unmark(item: ItemStack?): ItemStack? {
        if (item == null) {
            return null
        }
        val meta = item.itemMeta ?: return item
        meta.persistentDataContainer.remove(boundKey)
        item.itemMeta = meta
        return item
    }

    /**
     * 检测物品是否带有副本绑定 NBT。
     */
    fun hasTag(item: ItemStack?): Boolean {
        if (item == null) {
            return false
        }
        val meta = item.itemMeta ?: return false
        return meta.persistentDataContainer.has(boundKey, PersistentDataType.BYTE)
    }

    /**
     * 移除玩家身上所有带有副本绑定 NBT 的物品。
     */
    fun clearFromPlayer(player: Player) {
        clearInventory(player.inventory)
        if (hasTag(player.itemOnCursor)) {
            player.setItemOnCursor(null)
        }
    }

    private fun clearInventory(inventory: PlayerInventory) {
        for (slot in 0 until inventory.size) {
            if (hasTag(inventory.getItem(slot))) {
                inventory.setItem(slot, null)
            }
        }
        for (slot in inventory.armorContents.indices) {
            val item = inventory.armorContents[slot]
            if (hasTag(item)) {
                inventory.armorContents = inventory.armorContents.clone().also { it[slot] = null }
            }
        }
        if (hasTag(inventory.itemInOffHand)) {
            inventory.setItemInOffHand(null)
        }
    }

    @SubscribeEvent
    fun onDrop(event: PlayerDropItemEvent) {
        if (!hasTag(event.itemDrop.itemStack)) {
            return
        }
        event.isCancelled = true
        event.player.sendMessage("§c临时装备无法被丢弃。")
    }

    @SubscribeEvent
    fun onPickup(event: EntityPickupItemEvent) {
        val player = event.entity as? Player ?: return
        if (!hasTag(event.item.itemStack)) {
            return
        }
        if (!DungeonManager.isInDungeon(player)) {
            event.isCancelled = true
        }
    }

    @SubscribeEvent
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val current = event.currentItem
        val cursor = event.cursor
        val topSize = event.view.topInventory.size
        val clickedTop = event.rawSlot in 0 until topSize

        if (clickedTop && (hasTag(current) || hasTag(cursor))) {
            event.isCancelled = true
            player.sendMessage("§c临时装备无法放入外部容器。")
            return
        }

        if (clickedTop && event.hotbarButton >= 0 && hasTag(player.inventory.getItem(event.hotbarButton))) {
            event.isCancelled = true
            player.sendMessage("§c临时装备无法放入外部容器。")
        }
    }

    @SubscribeEvent
    fun onInventoryDrag(event: InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return
        if (!hasTag(event.oldCursor)) {
            return
        }
        val topSize = event.view.topInventory.size
        if (event.rawSlots.any { it < topSize }) {
            event.isCancelled = true
            player.sendMessage("§c临时装备无法放入外部容器。")
        }
    }
}
