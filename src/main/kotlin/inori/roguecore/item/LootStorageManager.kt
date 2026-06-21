package inori.roguecore.item

import inori.roguecore.accessory.AccessoryItemCodec
import inori.roguecore.balance.BalanceConfigManager
import inori.roguecore.data.DatabaseManager
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.util.io.BukkitObjectInputStream
import org.bukkit.util.io.BukkitObjectOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64

/** 战利品仓库：存放未鉴定装备、锻造书、饰品与副本绑定战利品。 */
object LootStorageManager {

    private const val STORAGE_KEY = "loot.storage"

    fun isEnabled(): Boolean = BalanceConfigManager.getBoolean("storage.loot.enabled", true)

    fun getCapacity(player: Player): Int {
        val base = BalanceConfigManager.getInt("storage.loot.base-size", 54).coerceAtLeast(9)
        val max = BalanceConfigManager.getInt("storage.loot.max-size", 162).coerceAtLeast(base)
        return base.coerceAtMost(max)
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

    fun canStore(item: ItemStack?): Boolean {
        if (item == null || item.type == Material.AIR) return false
        if (DungeonLootManager.isPermanentLoot(item)) return false
        return DungeonLootManager.isTemporaryLoot(item) ||
            DungeonLootManager.isUnidentifiedLoot(item) ||
            DungeonLootManager.isForgeBook(item) ||
            AccessoryItemCodec.parse(item) != null ||
            AccessoryItemCodec.isSealedAccessory(item) ||
            AccessoryItemCodec.isInscriptionBook(item)
    }

    fun storeFromInventory(player: Player, inventorySlot: Int): String {
        val item = player.inventory.getItem(inventorySlot)
        val message = storeItem(player, item)
        if (message.startsWith("§a")) {
            player.inventory.setItem(inventorySlot, null)
        }
        return message
    }

    fun storeItem(player: Player, item: ItemStack?): String {
        if (!canStore(item)) return "§c只能存入 RogueCore 战利品，永久装备请放入装备仓库。"
        val items = getItems(player)
        if (items.size >= getCapacity(player)) return "§c战利品仓库已满。"
        items += item!!.clone().also { it.amount = 1 }
        saveItems(player, items)
        return "§a已存入战利品仓库。"
    }

    fun storeAll(player: Player): String {
        val items = getItems(player)
        val capacity = getCapacity(player)
        var stored = 0
        for (slot in 0 until player.inventory.size) {
            if (items.size >= capacity) break
            val item = player.inventory.getItem(slot) ?: continue
            if (!canStore(item)) continue
            items += item.clone().also { it.amount = 1 }
            player.inventory.setItem(slot, null)
            stored++
        }
        if (stored > 0) saveItems(player, items)
        return if (stored > 0) "§a已存入 §f$stored §a件战利品。" else "§7背包中没有可存入的战利品。"
    }

    fun withdraw(player: Player, index: Int): String {
        val items = getItems(player)
        val item = items.getOrNull(index) ?: return "§c该仓库槽位没有物品。"
        val leftovers = player.inventory.addItem(item.clone())
        if (leftovers.isNotEmpty()) return "§c背包空间不足。"
        items.removeAt(index)
        saveItems(player, items)
        return "§a已取出战利品。"
    }

    private fun encodeItem(item: ItemStack): String {
        val output = ByteArrayOutputStream()
        BukkitObjectOutputStream(output).use { it.writeObject(item) }
        return Base64.getEncoder().encodeToString(output.toByteArray())
    }

    private fun decodeItem(raw: String): ItemStack? {
        return runCatching {
            val bytes = Base64.getDecoder().decode(raw)
            BukkitObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() as? ItemStack }
        }.getOrNull()
    }
}
