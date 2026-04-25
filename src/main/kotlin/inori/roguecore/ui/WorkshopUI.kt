package inori.roguecore.ui

import inori.roguecore.data.PermanentMaterialManager
import inori.roguecore.data.PlayerDataManager
import inori.roguecore.workshop.WorkshopManager
import inori.roguecore.workshop.WorkshopRecipe
import inori.roguecore.workshop.WorkshopRecipeType
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest

/**
 * 局外材料工坊 UI。
 */
object WorkshopUI {

    private const val INFO_SLOT = 4
    private const val CLOSE_SLOT = 49

    fun open(player: Player) {
        val recipes = WorkshopManager.getAll()
        val recipeSlots = recipes.map { it.slot }.filter { it in 0 until 54 }.toSet()
        val activeSlots = recipeSlots + setOf(INFO_SLOT, CLOSE_SLOT)
        val shards = PlayerDataManager.get(player.uniqueId).soulShards

        player.openMenu<Chest>("§6§l材料工坊 §7(灵魂碎片: §e$shards§7)") {
            rows(6)
            handLocked(true)

            val glass = XMaterial.BROWN_STAINED_GLASS_PANE.parseItem()!!.apply {
                itemMeta = itemMeta?.also { it.setDisplayName(" ") }
            }
            for (slot in 0 until 54) {
                if (slot !in activeSlots) set(slot, glass)
            }

            set(INFO_SLOT, infoItem(player))
            for (recipe in recipes) {
                if (recipe.slot in 0 until 54) {
                    set(recipe.slot, recipeItem(player, recipe))
                }
            }
            set(CLOSE_SLOT, closeItem())

            onClick { event ->
                event.isCancelled = true
                if (event.rawSlot == CLOSE_SLOT) {
                    player.closeInventory()
                    return@onClick
                }
                val recipe = recipes.firstOrNull { it.slot == event.rawSlot } ?: return@onClick
                val message = WorkshopManager.execute(player, recipe.id)
                player.sendMessage(message)
                open(player)
            }
        }
    }

    private fun recipeItem(player: Player, recipe: WorkshopRecipe): ItemStack {
        val canExecute = WorkshopManager.canExecute(player, recipe)
        val item = recipe.icon.parseItem() ?: XMaterial.PAPER.parseItem()!!
        return item.apply {
            itemMeta = itemMeta?.also { meta ->
                meta.setDisplayName((if (canExecute) "§a" else "§7") + recipe.name + " §8[${recipe.type.displayName}]")
                meta.lore = buildList {
                    add("")
                    if (recipe.description.isNotBlank()) add("§7${recipe.description}")
                    add("")
                    add("§e消耗:")
                    if (recipe.soulShards > 0) {
                        val owned = PlayerDataManager.get(player.uniqueId).soulShards
                        add(if (owned >= recipe.soulShards) "§a灵魂碎片 x${recipe.soulShards}" else "§c灵魂碎片 x${recipe.soulShards} §8(当前 $owned)")
                    }
                    if (recipe.cost.isEmpty() && recipe.soulShards <= 0) {
                        add("§8无消耗")
                    } else {
                        for ((type, amount) in recipe.cost) {
                            val owned = PermanentMaterialManager.get(player, type)
                            add(if (owned >= amount) "§a${type.coloredName()} §fx$amount" else "§c${type.coloredName()} §fx$amount §8(当前 $owned)")
                        }
                    }
                    add("")
                    add("§a获得:")
                    for ((type, amount) in recipe.reward) {
                        add("§7${type.coloredName()} §fx$amount")
                    }
                    add("")
                    add(if (canExecute) "§e点击兑换" else "§c材料或灵魂碎片不足")
                }
            }
        }
    }

    private fun infoItem(player: Player): ItemStack {
        val recipes = WorkshopManager.getAll()
        return XMaterial.CRAFTING_TABLE.parseItem()!!.apply {
            itemMeta = itemMeta?.also { meta ->
                meta.setDisplayName("§6工坊总览")
                meta.lore = buildList {
                    add("")
                    add("§7配方数量: §f${recipes.size}")
                    add("§7正向合成: §f${recipes.count { it.type == WorkshopRecipeType.CRAFT }}")
                    add("§7反向拆解: §f${recipes.count { it.type == WorkshopRecipeType.DISMANTLE }}")
                    add("§7灵魂补料: §f${recipes.count { it.type == WorkshopRecipeType.PURCHASE }}")
                    add("")
                    add("§7材料库存:")
                    addAll(PermanentMaterialManager.formatOwned(player))
                    add("")
                    add("§8点击配方进行兑换")
                }
            }
        }
    }

    private fun closeItem(): ItemStack {
        return XMaterial.BARRIER.parseItem()!!.apply {
            itemMeta = itemMeta?.also { meta ->
                meta.setDisplayName("§c关闭")
                meta.lore = listOf("", "§7关闭材料工坊")
            }
        }
    }
}
