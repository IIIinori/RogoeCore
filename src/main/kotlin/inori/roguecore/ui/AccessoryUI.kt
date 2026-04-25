package inori.roguecore.ui

import inori.roguecore.accessory.AccessoryItemCodec
import inori.roguecore.accessory.AccessorySlot
import inori.roguecore.accessory.PlayerAccessoryData
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest

object AccessoryUI {

    private val slotMap = mapOf(
        AccessorySlot.NECKLACE to 11,
        AccessorySlot.RING_1 to 13,
        AccessorySlot.RING_2 to 15,
        AccessorySlot.CHARM to 29,
        AccessorySlot.TROPHY to 33
    )

    private const val INFO_SLOT = 40
    private const val BACK_SLOT = 49

    fun open(player: Player) {
        player.openMenu<Chest>("§d§l饰品匣") {
            rows(6)
            handLocked(true)

            val buttons = slotMap.values.toSet() + setOf(INFO_SLOT, BACK_SLOT)
            val glass = XMaterial.PURPLE_STAINED_GLASS_PANE.parseItem()!!.apply {
                itemMeta = itemMeta?.also { it.setDisplayName(" ") }
            }
            for (slot in 0 until 54) {
                if (slot !in buttons) set(slot, glass)
            }

            val equipped = PlayerAccessoryData.getEquipped(player)
            for ((accessorySlot, menuSlot) in slotMap) {
                val instance = equipped[accessorySlot]
                set(menuSlot, if (instance != null) {
                    AccessoryItemCodec.toItemStack(instance, menuPreview = true) ?: emptySlot(accessorySlot)
                } else {
                    emptySlot(accessorySlot)
                })
            }
            set(INFO_SLOT, infoItem(player))
            set(BACK_SLOT, backItem())

            onClick { event ->
                event.isCancelled = true
                val raw = event.rawSlot
                if (raw == BACK_SLOT) {
                    RogueMenuUI.open(player)
                    return@onClick
                }

                val accessorySlot = slotMap.entries.firstOrNull { it.value == raw }?.key
                if (accessorySlot != null) {
                    val message = PlayerAccessoryData.unequip(player, accessorySlot)
                    player.sendMessage(message)
                    open(player)
                    return@onClick
                }

                val topSize = event.view.topInventory.size
                if (raw >= topSize) {
                    val current = event.currentItem ?: return@onClick
                    if (!AccessoryItemCodec.isAccessory(current)) {
                        return@onClick
                    }
                    val inventorySlot = raw - topSize
                    val message = PlayerAccessoryData.equipFromInventory(player, inventorySlot)
                    player.sendMessage(message)
                    open(player)
                }
            }
        }
    }

    private fun emptySlot(slot: AccessorySlot): ItemStack {
        val material = when (slot) {
            AccessorySlot.NECKLACE -> XMaterial.GOLD_NUGGET
            AccessorySlot.RING_1, AccessorySlot.RING_2 -> XMaterial.IRON_NUGGET
            AccessorySlot.CHARM -> XMaterial.RABBIT_FOOT
            AccessorySlot.TROPHY -> XMaterial.DRAGON_BREATH
            AccessorySlot.RING -> XMaterial.IRON_NUGGET
        }
        return material.parseItem()!!.apply {
            itemMeta = itemMeta?.also { meta ->
                meta.setDisplayName("§7${slot.displayName}")
                meta.lore = listOf(
                    "",
                    "§8空饰品槽",
                    "§7点击背包中的饰品可装备到这里",
                    "§7戒指类饰品会自动填入可用槽位",
                    "",
                    "§e点击背包饰品进行装备"
                )
            }
        }
    }

    private fun infoItem(player: Player): ItemStack = XMaterial.BOOK.parseItem()!!.apply {
        val equipped = PlayerAccessoryData.getEquipped(player)
        itemMeta = itemMeta?.also { meta ->
            meta.setDisplayName("§d饰品匣说明")
            meta.lore = buildList {
                add("")
                add("§7已装备: §d${equipped.size}§7/§f${AccessorySlot.equippedSlots.size}")
                add("§7饰品存放在这个 GUI 的虚拟槽中")
                add("§7放入后从背包移除并开始生效")
                add("§7取下时会回到背包")
                add("")
                add("§e点击背包内饰品: 装备/替换")
                add("§e点击上方已装备饰品: 取下")
            }
        }
    }

    private fun backItem(): ItemStack = XMaterial.ARROW.parseItem()!!.apply {
        itemMeta = itemMeta?.also { meta ->
            meta.setDisplayName("§e返回主菜单")
            meta.lore = listOf("", "§e点击返回")
        }
    }
}
