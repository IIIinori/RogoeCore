package inori.roguecore.item

import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.inventory.ItemStack
import taboolib.common.platform.event.SubscribeEvent
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration
import java.util.UUID

/**
 * RogueCore 永久装备保护。
 */
object PermanentGearProtection {

    @Config("loot.yml")
    lateinit var config: Configuration
        private set

    private val warnAt = mutableMapOf<UUID, Long>()

    @SubscribeEvent
    fun onDrop(event: PlayerDropItemEvent) {
        val item = event.itemDrop.itemStack
        if (!DungeonLootManager.isPermanentLoot(item) || allowDrop()) {
            return
        }
        event.isCancelled = true
        warn(event.player, "§c永久装备已绑定，无法丢弃。")
    }

    @SubscribeEvent
    fun onPickup(event: EntityPickupItemEvent) {
        val player = event.entity as? Player ?: return
        val item = event.item.itemStack
        if (!DungeonLootManager.isPermanentLoot(item)) {
            return
        }
        if (DungeonLootManager.getPermanentOwnerUuid(item) == null) {
            DungeonLootManager.ensurePermanentOwner(item, player)
            return
        }
        if (!allowPickupByOthers() && !DungeonLootManager.isPermanentLootOwnedBy(item, player)) {
            event.isCancelled = true
            warn(player, "§c这件永久装备不属于你。")
        }
    }

    @SubscribeEvent
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val current = event.currentItem
        val cursor = event.cursor
        val hotbar = event.hotbarButton.takeIf { it >= 0 }?.let { player.inventory.getItem(it) }

        if (!allowUseByOthers() && (isForeign(current, player) || isForeign(cursor, player) || isForeign(hotbar, player))) {
            event.isCancelled = true
            warn(player, "§c这件永久装备不属于你，无法操作。")
            return
        }

        if (!allowContainer() && isMovingPermanentToExternalContainer(event, current, cursor, hotbar)) {
            event.isCancelled = true
            warn(player, "§c永久装备无法放入外部容器。")
        }
    }

    @SubscribeEvent
    fun onInventoryDrag(event: InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return
        val item = event.oldCursor
        if (!DungeonLootManager.isPermanentLoot(item)) {
            return
        }
        if (!allowUseByOthers() && isForeign(item, player)) {
            event.isCancelled = true
            warn(player, "§c这件永久装备不属于你，无法操作。")
            return
        }
        if (!allowContainer()) {
            val topSize = event.view.topInventory.size
            if (event.rawSlots.any { it < topSize }) {
                event.isCancelled = true
                warn(player, "§c永久装备无法放入外部容器。")
            }
        }
    }

    @SubscribeEvent
    fun onHeld(event: PlayerItemHeldEvent) {
        val item = event.player.inventory.getItem(event.newSlot)
        if (!allowUseByOthers() && isForeign(item, event.player)) {
            event.isCancelled = true
            warn(event.player, "§c这件永久装备不属于你，无法使用。")
        }
    }

    @SubscribeEvent
    fun onSwap(event: PlayerSwapHandItemsEvent) {
        if (!allowUseByOthers() && (isForeign(event.mainHandItem, event.player) || isForeign(event.offHandItem, event.player))) {
            event.isCancelled = true
            warn(event.player, "§c这件永久装备不属于你，无法使用。")
        }
    }

    private fun isMovingPermanentToExternalContainer(
        event: InventoryClickEvent,
        current: ItemStack?,
        cursor: ItemStack?,
        hotbar: ItemStack?
    ): Boolean {
        val topSize = event.view.topInventory.size
        val clickedTop = event.rawSlot in 0 until topSize
        if (clickedTop && (DungeonLootManager.isPermanentLoot(current) || DungeonLootManager.isPermanentLoot(cursor))) {
            return true
        }
        if (clickedTop && DungeonLootManager.isPermanentLoot(hotbar)) {
            return true
        }
        if (event.isShiftClick && !clickedTop && DungeonLootManager.isPermanentLoot(current)) {
            return true
        }
        return false
    }

    private fun isForeign(item: ItemStack?, player: Player): Boolean {
        if (!DungeonLootManager.isPermanentLoot(item)) {
            return false
        }
        val owner = DungeonLootManager.getPermanentOwnerUuid(item)
        if (owner == null) {
            DungeonLootManager.ensurePermanentOwner(item, player)
            return false
        }
        return owner != player.uniqueId
    }

    private fun allowDrop(): Boolean {
        return config.getBoolean("permanent-gear.allow-drop", false)
    }

    private fun allowContainer(): Boolean {
        return config.getBoolean("permanent-gear.allow-container", false)
    }

    private fun allowPickupByOthers(): Boolean {
        return config.getBoolean("permanent-gear.allow-pickup-by-others", false)
    }

    private fun allowUseByOthers(): Boolean {
        return config.getBoolean("permanent-gear.allow-use-by-others", false)
    }

    private fun warn(player: Player, message: String) {
        val now = System.currentTimeMillis()
        val last = warnAt[player.uniqueId] ?: 0L
        if (now - last < 1000L) {
            return
        }
        warnAt[player.uniqueId] = now
        player.sendMessage(message)
    }
}
