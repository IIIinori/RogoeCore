package inori.roguecore.accessory

import inori.roguecore.dependency.DependencySelfCheckManager
import inori.roguecore.dungeon.RunPersistenceManager
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.serverct.ersha.api.AttributeAPI
import taboolib.common.platform.function.warning
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object PlayerAccessoryData {

    private const val AP_SOURCE_PREFIX = "rogue_accessory_"
    private val equipped = ConcurrentHashMap<UUID, MutableMap<AccessorySlot, AccessoryInstance>>()

    fun getEquipped(player: Player): Map<AccessorySlot, AccessoryInstance> = getEquipped(player.uniqueId)

    fun getEquipped(uuid: UUID): Map<AccessorySlot, AccessoryInstance> {
        return equipped[uuid]?.toMap() ?: emptyMap()
    }

    fun get(slot: AccessorySlot, player: Player): AccessoryInstance? = equipped[player.uniqueId]?.get(slot)

    fun getEffects(player: Player, type: AccessoryEffectType): List<AccessoryEffect> {
        return getEquipped(player).values.flatMap { it.effects }.filter { it.type == type }
    }

    fun equipFromInventory(player: Player, inventorySlot: Int): String {
        val item = player.inventory.getItem(inventorySlot) ?: return "§c该位置没有饰品。"
        val instance = AccessoryItemCodec.parse(item) ?: return "§c这不是有效饰品。"
        val target = chooseTargetSlot(player, instance.definition.slot) ?: return "§c没有可用饰品槽。"
        return equip(player, target, instance, consumeInventorySlot = inventorySlot)
    }

    fun equip(player: Player, targetSlot: AccessorySlot, instance: AccessoryInstance, consumeInventorySlot: Int? = null): String {
        if (!targetSlot.accepts(instance.definition.slot)) {
            return "§c${instance.definition.name} 不能装备到 ${targetSlot.displayName}。"
        }
        val map = equipped.getOrPut(player.uniqueId) { mutableMapOf() }
        val replaced = map[targetSlot]
        if (replaced != null && consumeInventorySlot == null && !canGive(player, replaced)) {
            return "§c背包空间不足，无法替换饰品。"
        }

        if (consumeInventorySlot != null) {
            removeOne(player.inventory.getItem(consumeInventorySlot))
            if ((player.inventory.getItem(consumeInventorySlot)?.amount ?: 0) <= 0) {
                player.inventory.setItem(consumeInventorySlot, null)
            }
        }

        if (replaced != null) {
            give(player, replaced)
        }
        map[targetSlot] = instance
        applySlot(player, targetSlot, instance)
        RunPersistenceManager.markDirty()
        return "§a已装备饰品: ${instance.rarity.color}${instance.definition.name} §7(${targetSlot.displayName})"
    }

    fun unequip(player: Player, slot: AccessorySlot): String {
        val map = equipped[player.uniqueId] ?: return "§7该槽位没有饰品。"
        val instance = map[slot] ?: return "§7该槽位没有饰品。"
        if (!canGive(player, instance)) {
            return "§c背包空间不足，无法取下饰品。"
        }
        map.remove(slot)
        if (map.isEmpty()) equipped.remove(player.uniqueId)
        removeSlotSource(player, slot)
        give(player, instance)
        RunPersistenceManager.markDirty()
        return "§e已取下饰品: §f${instance.definition.name}"
    }

    fun clear(player: Player) {
        val map = equipped.remove(player.uniqueId) ?: return
        for (slot in map.keys) {
            removeSlotSource(player, slot)
        }
        RunPersistenceManager.markDirty()
    }

    fun restore(uuid: UUID, values: Map<AccessorySlot, AccessoryInstance>) {
        if (values.isEmpty()) {
            equipped.remove(uuid)
        } else {
            equipped[uuid] = values.toMutableMap()
        }
        RunPersistenceManager.markDirty()
    }

    fun reapply(player: Player) {
        removeAllSources(player)
        for ((slot, instance) in getEquipped(player)) {
            applySlot(player, slot, instance)
        }
    }

    private fun chooseTargetSlot(player: Player, definitionSlot: AccessorySlot): AccessorySlot? {
        val targets = AccessorySlot.equipTargets(definitionSlot)
        val map = equipped[player.uniqueId] ?: emptyMap()
        return targets.firstOrNull { it !in map } ?: targets.firstOrNull()
    }

    private fun canGive(player: Player, instance: AccessoryInstance): Boolean {
        val item = AccessoryItemCodec.toItemStack(instance) ?: return false
        return player.inventory.firstEmpty() >= 0 || player.inventory.containsAtLeast(item, item.amount)
    }

    private fun give(player: Player, instance: AccessoryInstance) {
        val item = AccessoryItemCodec.toItemStack(instance) ?: return
        val leftovers = player.inventory.addItem(item)
        leftovers.values.forEach { player.world.dropItemNaturally(player.location, it) }
    }

    private fun removeOne(item: ItemStack?) {
        if (item == null || item.type == Material.AIR) return
        item.amount = item.amount - 1
    }

    private fun applySlot(player: Player, slot: AccessorySlot, instance: AccessoryInstance) {
        removeSlotSource(player, slot)
        if (instance.rolledAttributes.isEmpty()) return
        if (!DependencySelfCheckManager.isAttributePlusAvailable()) {
            DependencySelfCheckManager.warnAttributePlusUnavailable("饰品 ${instance.definition.id}")
            return
        }
        try {
            val data = AttributeAPI.getAttrData(player) ?: return
            val attrs = hashMapOf<String, Array<Number>>()
            for ((name, value) in instance.rolledAttributes) {
                attrs[name] = arrayOf(value, value)
            }
            AttributeAPI.addSourceAttribute(data, sourceKey(slot), attrs)
            AttributeAPI.updateAttribute(player)
        } catch (ex: Exception) {
            warning("[RogueCore] 应用饰品 ${instance.definition.id} 到 AP 失败: ${ex.message}")
        }
    }

    private fun removeAllSources(player: Player) {
        for (slot in AccessorySlot.equippedSlots) {
            removeSlotSource(player, slot, update = false)
        }
        if (DependencySelfCheckManager.isAttributePlusAvailable()) {
            runCatching { AttributeAPI.updateAttribute(player) }
        }
    }

    private fun removeSlotSource(player: Player, slot: AccessorySlot, update: Boolean = true) {
        if (!DependencySelfCheckManager.isAttributePlusAvailable()) return
        try {
            val data = AttributeAPI.getAttrData(player) ?: return
            AttributeAPI.takeSourceAttribute(data, sourceKey(slot))
            if (update) AttributeAPI.updateAttribute(player)
        } catch (ex: Exception) {
            warning("[RogueCore] 移除饰品槽 ${slot.name} AP 属性失败: ${ex.message}")
        }
    }

    private fun sourceKey(slot: AccessorySlot): String = "$AP_SOURCE_PREFIX${slot.name}"
}
