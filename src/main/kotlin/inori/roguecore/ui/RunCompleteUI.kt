package inori.roguecore.ui

import inori.roguecore.boon.PlayerBoonData
import inori.roguecore.data.ShardRewardManager
import inori.roguecore.dungeon.DungeonInstance
import inori.roguecore.dungeon.DungeonManager
import inori.roguecore.dungeon.room.RoomType
import inori.roguecore.dungeon.route.NextFloorRoute
import inori.roguecore.party.Party
import inori.roguecore.relic.PlayerRelicData
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
            rows(4)
            handLocked(true)

            val routes = NextFloorRoute.entries.filter { route ->
                route.requiredUnlockId == null || UnlockManager.hasUnlock(player, route.requiredUnlockId)
            }
            val routePositions = when (routes.size) {
                1 -> listOf(13)
                2 -> listOf(12, 14)
                3 -> listOf(11, 13, 15)
                else -> listOf(10, 12, 14, 16)
            }
            val routeSlots = routePositions.mapIndexedNotNull { index, slot ->
                routes.getOrNull(index)?.let { slot to it }
            }.toMap(linkedMapOf())
            val optionSlots = routeSlots.keys + setOf(4, 22, 31)
            val glass = XMaterial.BLACK_STAINED_GLASS_PANE.parseItem()!!.apply {
                itemMeta = itemMeta?.also { it.setDisplayName(" ") }
            }
            for (slot in 0 until 36) {
                if (slot !in optionSlots) {
                    set(slot, glass)
                }
            }

            set(4, buildRunSummary(player, instance, party))
            set(22, buildBuildSummary(player))

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
                        "§7当前可结算: §6${ShardRewardManager.getSettlementPreview(player.uniqueId)}",
                        "",
                        if (party != null) "§e点击后全队结算离开" else "§e点击结束本次冒险"
                    )
                }
            }
            set(31, leaveItem)

            onClick { event ->
                event.isCancelled = true
                val route = routeSlots[event.rawSlot]
                if (route != null) {
                    DungeonGuiGuard.unlock(player)
                    player.closeInventory()
                    DungeonManager.advanceDungeon(instance.id, player.uniqueId, route)
                    return@onClick
                }
                if (event.rawSlot == 31) {
                    DungeonGuiGuard.unlock(player)
                    player.closeInventory()
                    DungeonManager.finishDungeon(instance.id, player.uniqueId)
                }
            }
        }
    }

    private fun buildRunSummary(player: Player, instance: DungeonInstance, party: Party?) = XMaterial.TOTEM_OF_UNDYING.parseItem()!!.apply {
        val runShards = ShardRewardManager.getRunShards(player.uniqueId)
        itemMeta = itemMeta?.also { meta ->
            meta.setDisplayName("§6本次 run 总览")
            meta.lore = buildList {
                add("")
                add("§7通关楼层: §f${instance.config.floorNumber}")
                add("§7本局碎片: §6$runShards")
                add("§7隐藏钥匙: §b${instance.getHiddenKeys()}")
                add("§7当前玩家数: §f${party?.size ?: 1}")
                add("§7战斗词缀: §f${instance.affixes.size}")
                add("§7事件词缀: §d${instance.eventAffixes.size}")
                add("")
                add("§e选择路线继续构筑，或直接结算离开")
            }
        }
    }

    private fun buildBuildSummary(player: Player) = XMaterial.ENCHANTED_BOOK.parseItem()!!.apply {
        val boons = PlayerBoonData.getBoons(player)
        val relics = PlayerRelicData.getRelics(player)
        val tagSummary = boons.flatMap { it.boon.tags }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(3)
            .joinToString(" / ") { "${it.key}x${it.value}" }
            .ifBlank { "尚未成型" }

        itemMeta = itemMeta?.also { meta ->
            meta.setDisplayName("§d构筑摘要")
            meta.lore = listOf(
                "",
                "§7神恩数量: §f${boons.size}",
                "§7遗物数量: §f${relics.size}",
                "§7主流派: §f$tagSummary",
                "§7继续前进更适合补强现有方向",
                "",
                "§e路线会决定下一层的房型倾向"
            )
        }
    }

    private fun describeRouteBonus(modifiers: Map<RoomType, Int>): String {
        return modifiers.entries.joinToString(" / ") { "${it.key.displayName}+${it.value}" }
    }
}
