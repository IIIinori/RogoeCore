package inori.roguecore.item

import inori.roguecore.data.DatabaseManager
import inori.roguecore.unlock.UnlockManager
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.util.io.BukkitObjectInputStream
import org.bukkit.util.io.BukkitObjectOutputStream
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64

/**
 * RogueCore 永久装备仓库。
 */
object GearStorageManager {

    @Config("loot.yml")
    lateinit var config: Configuration
        private set

    private const val STORAGE_KEY = "gear.storage"

    fun isEnabled(): Boolean = config.getBoolean("gear-storage.enabled", true)

    fun getCapacity(player: Player): Int {
        val base = config.getInt("gear-storage.base-size", 27).coerceIn(9, 54)
        return (base + UnlockManager.getGearStorageBonus(player)).coerceIn(9, 54)
    }

    fun getItems(player: Player): MutableList<ItemStack> {
        val raw = DatabaseManager.getOrCreateContainer(player.uniqueId)[STORAGE_KEY] ?: return mutableListOf()
        return raw.split(";")
            .filter { it.isNotBlank() }
            .mapNotNull { decodeItem(it) }
            .toMutableList()
    }

    fun saveItems(player: Player, items: List<ItemStack>) {
        DatabaseManager.getOrCreateContainer(player.uniqueId)[STORAGE_KEY] = items.joinToString(";") { encodeItem(it) }
    }

    fun canStore(player: Player, item: ItemStack?): Boolean {
        return item != null &&
            item.type != Material.AIR &&
            DungeonLootManager.isPermanentLoot(item) &&
            DungeonLootManager.isPermanentLootOwnedBy(item, player) &&
            !DungeonBoundItem.hasTag(item) &&
            !DungeonLootManager.isUnidentifiedLoot(item) &&
            !DungeonLootManager.isForgeBook(item)
    }

    fun storeFromInventory(player: Player, inventorySlot: Int): String {
        val item = player.inventory.getItem(inventorySlot)
        if (!canStore(player, item)) {
            return "§c只能存入绑定你的 RogueCore 永久装备。"
        }
        val items = getItems(player)
        if (items.size >= getCapacity(player)) {
            return "§c装备仓库已满。"
        }
        items += item!!.clone()
        saveItems(player, items)
        player.inventory.setItem(inventorySlot, null)
        return "§a已存入装备仓库。"
    }

    fun withdraw(player: Player, index: Int): String {
        val items = getItems(player)
        val item = items.getOrNull(index) ?: return "§c该仓库槽位没有装备。"
        val leftovers = player.inventory.addItem(item.clone())
        if (leftovers.isNotEmpty()) {
            return "§c背包空间不足。"
        }
        items.removeAt(index)
        saveItems(player, items)
        return "§a已取出装备。"
    }

    fun toggleFavorite(player: Player, index: Int): String {
        val items = getItems(player)
        val item = items.getOrNull(index) ?: return "§c该仓库槽位没有装备。"
        val favorite = DungeonLootManager.toggleFavorite(item)
            ?: return "§c这件装备无法收藏。"
        items[index] = item
        saveItems(player, items)
        return if (favorite) "§6已收藏该装备。" else "§7已取消收藏该装备。"
    }

    fun equip(player: Player, index: Int): String {
        val items = getItems(player)
        val item = items.getOrNull(index) ?: return "§c该仓库槽位没有装备。"
        val slot = DungeonLootManager.getLootEquipmentSlot(item) ?: return "§c这件装备没有可装备部位。"
        val current = getEquipped(player, slot)
        if (current != null && current.type != Material.AIR) {
            if (!canStore(player, current)) {
                return "§c当前部位已有非 RogueCore 永久装备，无法自动替换。"
            }
            if (items.size >= getCapacity(player)) {
                return "§c仓库已满，无法换下当前装备。"
            }
            items[index] = current.clone()
        } else {
            items.removeAt(index)
        }
        setEquipped(player, slot, item.clone())
        saveItems(player, items)
        DungeonLootManager.refreshEquippedSetBonuses(player)
        return "§a已装备仓库装备。"
    }

    private fun getEquipped(player: Player, slot: EquipmentSlot): ItemStack? {
        return when (slot) {
            EquipmentSlot.HAND -> player.inventory.itemInMainHand
            EquipmentSlot.OFF_HAND -> player.inventory.itemInOffHand
            EquipmentSlot.HEAD -> player.inventory.helmet
            EquipmentSlot.CHEST -> player.inventory.chestplate
            EquipmentSlot.LEGS -> player.inventory.leggings
            EquipmentSlot.FEET -> player.inventory.boots
        }
    }

    private fun setEquipped(player: Player, slot: EquipmentSlot, item: ItemStack?) {
        when (slot) {
            EquipmentSlot.HAND -> player.inventory.setItemInMainHand(item)
            EquipmentSlot.OFF_HAND -> player.inventory.setItemInOffHand(item)
            EquipmentSlot.HEAD -> player.inventory.helmet = item
            EquipmentSlot.CHEST -> player.inventory.chestplate = item
            EquipmentSlot.LEGS -> player.inventory.leggings = item
            EquipmentSlot.FEET -> player.inventory.boots = item
        }
    }

    private fun encodeItem(item: ItemStack): String {
        val output = ByteArrayOutputStream()
        BukkitObjectOutputStream(output).use { stream ->
            stream.writeObject(item)
        }
        return Base64.getEncoder().encodeToString(output.toByteArray())
    }

    private fun decodeItem(raw: String): ItemStack? {
        return runCatching {
            val bytes = Base64.getDecoder().decode(raw)
            BukkitObjectInputStream(ByteArrayInputStream(bytes)).use { stream ->
                stream.readObject() as? ItemStack
            }
        }.getOrNull()
    }
}
