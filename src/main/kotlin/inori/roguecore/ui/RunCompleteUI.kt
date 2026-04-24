package inori.roguecore.ui

import inori.roguecore.dungeon.DungeonInstance
import inori.roguecore.dungeon.DungeonManager
import inori.roguecore.dungeon.route.NextFloorRoute
import inori.roguecore.dungeon.room.RoomType
import inori.roguecore.party.Party
import inori.roguecore.unlock.UnlockManager
import org.bukkit.entity.Player
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest

/**
 * 副本通关后的下一步选择。
 */
object RunCompleteUI {

    fun open(player: Player, instance: DungeonInstance, party: Party?) {
        val floor = instance.config.floorNumber
        val nextFloor = floor + 1
        val title = "§6§l副本通关"

        DungeonGuiGuard.lock(player, title) { target ->
            val latest = DungeonManager.getPlayerDungeon(target) ?: return@lock
            open(target, latest, party)
        }

        player.openMenu<Chest>(title) {
            rows(3)
            handLocked(true)

            val routes = NextFloorRoute.entries.filter { route ->
                route.requiredUnlockId == null || UnlockManager.hasUnlock(player, route.requiredUnlockId)
            }
            val routePositions = when (routes.size) {
                1 -> listOf(13)
                2 -> listOf(11, 15)
                3 -> listOf(10, 13, 16)
                else -> listOf(9, 11, 13, 15)
            }
            val routeSlots = routePositions.mapIndexedNotNull { index, slot ->
                routes.getOrNull(index)?.let { slot to it }
            }.toMap(linkedMapOf())
            val optionSlots = routeSlots.keys + 22
            val glass = XMaterial.BLACK_STAINED_GLASS_PANE.parseItem()!!.apply {
                itemMeta = itemMeta?.also { it.setDisplayName(" ") }
            }
            for (slot in 0 until 27) {
                if (slot !in optionSlots) {
                    set(slot, glass)
                }
            }

            for ((slot, route) in routeSlots) {
                set(slot, (route.icon.parseItem() ?: XMaterial.MAP.parseItem()!!).apply {
                    itemMeta = itemMeta?.also { meta ->
                        val routeBonus = UnlockManager.getRouteWeightBonus(player.uniqueId, route)
                        val hiddenBonus = UnlockManager.getRouteHiddenBonus(player.uniqueId, route)
                        meta.setDisplayName("§a${route.displayName}")
                        meta.lore = buildList {
                            add("")
                            add("§7当前楼层: §f$floor")
                            add("§7下一楼层: §a$nextFloor")
                            add("")
                            addAll(route.description)
                            if (routeBonus.isNotEmpty() || hiddenBonus > 0.0) {
                                add("")
                                add("§d研究强化:")
                                if (routeBonus.isNotEmpty()) {
                                    add("§7额外偏移: ${describeRouteBonus(routeBonus)}")
                                }
                                if (hiddenBonus > 0.0) {
                                    add("§7额外隐藏房概率: §a+${(hiddenBonus * 100).toInt()}%")
                                }
                            }
                            add("")
                            add(if (party != null) "§e点击后全队沿此路线前进" else "§e点击选择此路线")
                        }
                    }
                })
            }

            val leaveItem = XMaterial.REDSTONE_BLOCK.parseItem()!!.apply {
                itemMeta = itemMeta?.also { meta ->
                    meta.setDisplayName("§c结算离开")
                    meta.lore = listOf(
                        "",
                        "§7结束本次 run 并返回原地点",
                        "§7保留当前已获得的碎片结算",
                        "",
                        if (party != null) "§e点击后全队结算离开" else "§e点击结束本次冒险"
                    )
                }
            }
            set(22, leaveItem)

            onClick { event ->
                event.isCancelled = true
                val route = routeSlots[event.rawSlot]
                if (route != null) {
                    DungeonGuiGuard.unlock(player)
                    player.closeInventory()
                    DungeonManager.advanceDungeon(instance.id, player.uniqueId, route)
                    return@onClick
                }
                if (event.rawSlot == 22) {
                    DungeonGuiGuard.unlock(player)
                    player.closeInventory()
                    DungeonManager.finishDungeon(instance.id, player.uniqueId)
                }
            }
        }
    }

    private fun describeRouteBonus(modifiers: Map<RoomType, Int>): String {
        return modifiers.entries.joinToString(" / ") { "${it.key.displayName}+${it.value}" }
    }
}
