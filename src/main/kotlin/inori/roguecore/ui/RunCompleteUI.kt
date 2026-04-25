package inori.roguecore.ui

import inori.roguecore.boon.PlayerBoonData
import inori.roguecore.data.ShardRewardManager
import inori.roguecore.dungeon.DungeonInstance
import inori.roguecore.dungeon.DungeonManager
import inori.roguecore.dungeon.room.RoomType
import inori.roguecore.dungeon.route.NextFloorRoute
import inori.roguecore.party.Party
import inori.roguecore.relic.PlayerRelicData
import inori.roguecore.summary.RunSummary
import inori.roguecore.summary.RunSummaryManager
import inori.roguecore.unlock.UnlockManager
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
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
            rows(6)
            handLocked(true)

            val routes = NextFloorRoute.entries.filter { route ->
                route.requiredUnlockId == null || UnlockManager.hasUnlock(player, route.requiredUnlockId)
            }
            val routePositions = when (routes.size) {
                1 -> listOf(13)
                2 -> listOf(12, 14)
                3 -> listOf(11, 13, 15)
                4 -> listOf(10, 12, 14, 16)
                5 -> listOf(10, 12, 14, 16, 28)
                else -> listOf(10, 12, 14, 16, 28, 30)
            }
            val routeSlots = routePositions.mapIndexedNotNull { index, slot ->
                routes.getOrNull(index)?.let { slot to it }
            }.toMap(linkedMapOf())
            val summary = RunSummaryManager.getDisplaySummary(player)
            val optionSlots = routeSlots.keys + setOf(2, 3, 4, 5, 6, 22, 24, 36, 37, 38, 39, 40, 42, 49)
            val glass = XMaterial.BLACK_STAINED_GLASS_PANE.parseItem()!!.apply {
                itemMeta = itemMeta?.also { it.setDisplayName(" ") }
            }
            for (slot in 0 until 54) {
                if (slot !in optionSlots) {
                    set(slot, glass)
                }
            }

            set(2, buildRunSummary(player, instance, party))
            set(3, buildResourceSummary(player, summary))
            set(4, buildLootSummary(summary))
            set(5, buildBuildSummary(player))
            set(6, buildCollectionSummary(summary))
            set(22, buildRoutePreviewHint())
            set(24, buildNextStepHint(summary))
            set(36, shortcutItem(XMaterial.HOPPER, "§6回收工坊", "§7处理低价值装备、饰品和书类", "/rogue salvage"))
            set(37, shortcutItem(XMaterial.SPYGLASS, "§e装备鉴定", "§7鉴定未鉴定装备", "/rogue identify"))
            set(38, shortcutItem(XMaterial.CRAFTING_TABLE, "§d饰品工坊", "§7鉴定密封饰品或刻印饰品书", "/rogue aworkshop"))
            set(39, shortcutItem(XMaterial.LECTERN, "§5收藏馆", "§7提交高品质装备和饰品", "/rogue collection"))
            set(40, shortcutItem(XMaterial.WRITABLE_BOOK, "§e完整报告", "§7查看本局详细报告", "/rogue summary"))
            set(42, shortcutItem(XMaterial.ENCHANTED_BOOK, "§d构筑详情", "§7查看神恩、遗物、修正和饰品", "/rogue build"))

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
                            add("")
                            add("§7风险等级: ${bars(route.riskLevel, "§c")}")
                            add("§7收益等级: ${bars(route.rewardLevel, "§6")}")
                            add("§7房间倾向: ${describeRouteBonus(route.roomWeightModifiers)}")
                            if (route.affixWeightModifiers.isNotEmpty()) {
                                add("§7词缀倾向: ${describeAffixBonus(route.affixWeightModifiers)}")
                            }
                            if (route.eventFamilyModifiers.isNotEmpty()) {
                                add("§7事件倾向: ${describeFamilyBonus(route.eventFamilyModifiers)}")
                            }
                            if (route.hiddenRoomChanceBonus > 0.0) {
                                add("§7隐藏房基础加成: §a+${(route.hiddenRoomChanceBonus * 100).toInt()}%")
                            }
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
            set(49, leaveItem)

            onClick { event ->
                event.isCancelled = true
                val route = routeSlots[event.rawSlot]
                if (route != null) {
                    DungeonGuiGuard.unlock(player)
                    player.closeInventory()
                    DungeonManager.advanceDungeon(instance.id, player.uniqueId, route)
                    return@onClick
                }
                val command = when (event.rawSlot) {
                    36 -> "rogue salvage"
                    37 -> "rogue identify"
                    38 -> "rogue aworkshop"
                    39 -> "rogue collection"
                    40 -> "rogue summary"
                    42 -> "rogue build"
                    else -> null
                }
                if (command != null) {
                    DungeonGuiGuard.unlock(player)
                    player.closeInventory()
                    player.performCommand(command)
                    return@onClick
                }
                if (event.rawSlot == 49) {
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

    private fun buildRoutePreviewHint() = XMaterial.FILLED_MAP.parseItem()!!.apply {
        itemMeta = itemMeta?.also { meta ->
            meta.setDisplayName("§a路线预览说明")
            meta.lore = listOf(
                "",
                "§7路线会影响下一层的房间权重、",
                "§7隐藏房概率、副本词缀和事件词缀倾向。",
                "",
                "§7建议先用 §e/rogue build §7确认当前构筑，",
                "§7再选择能放大主流派收益的路线。"
            )
        }
    }

    private fun buildResourceSummary(player: Player, summary: RunSummary?) = XMaterial.GOLD_INGOT.parseItem()!!.apply {
        itemMeta = itemMeta?.also { meta ->
            meta.setDisplayName("§e资源收益")
            meta.lore = listOf(
                "",
                "§7当前本局碎片: §6${ShardRewardManager.getRunShards(player.uniqueId)}",
                "§7预计结算灵魂碎片: §6${ShardRewardManager.getSettlementPreview(player.uniqueId)}",
                "§7峰值本局碎片: §e${summary?.peakRunShards ?: 0}",
                "§7回收件数: §f${summary?.salvagedCount ?: 0}",
                "§7回收本局碎片: §6${summary?.salvagedRunShards ?: 0}",
                "§7回收灵魂碎片: §e${summary?.salvagedSoulShards ?: 0}"
            )
        }
    }

    private fun buildLootSummary(summary: RunSummary?) = XMaterial.CHEST.parseItem()!!.apply {
        val counts = summary?.lootCounts.orEmpty()
        itemMeta = itemMeta?.also { meta ->
            meta.setDisplayName("§b本局掉落")
            meta.lore = buildList {
                add("")
                lootKeys().forEach { key -> add("§7${lootLabel(key)}: §f${counts[key] ?: 0}") }
                add("")
                add("§8离开后可在对应工坊处理这些物品")
            }
        }
    }

    private fun buildCollectionSummary(summary: RunSummary?) = XMaterial.LECTERN.parseItem()!!.apply {
        itemMeta = itemMeta?.also { meta ->
            meta.setDisplayName("§5收藏进度")
            meta.lore = buildList {
                add("")
                add("§7Boss 首杀: §5${summary?.bossFirstKills ?: 0}")
                add("§7本局点亮: §d${summary?.collectionUnlocks?.size ?: 0}")
                summary?.collectionUnlocks?.take(4)?.forEach { add("§a- $it") }
                if ((summary?.collectionUnlocks?.size ?: 0) > 4) add("§7... 还有 ${(summary?.collectionUnlocks?.size ?: 0) - 4} 项")
                add("")
                add("§e点击下方收藏馆入口查看长期进度")
            }
        }
    }

    private fun buildNextStepHint(summary: RunSummary?) = XMaterial.COMPASS.parseItem()!!.apply {
        itemMeta = itemMeta?.also { meta ->
            meta.setDisplayName("§a下一步建议")
            meta.lore = buildList {
                add("")
                val counts = summary?.lootCounts.orEmpty()
                var suggestions = 0
                if ((counts["unidentified_gear"] ?: 0) > 0) { add("§e有未鉴定装备 → /rogue identify"); suggestions++ }
                if ((counts["forge_book"] ?: 0) > 0) { add("§6有锻造书 → /rogue craft"); suggestions++ }
                if ((counts["sealed_accessory"] ?: 0) > 0) { add("§d有密封饰品 → /rogue aid"); suggestions++ }
                if ((counts["accessory_inscription"] ?: 0) > 0) { add("§b有饰品刻印书 → /rogue inscribe"); suggestions++ }
                if ((summary?.collectionUnlocks?.size ?: 0) > 0) { add("§5本局有收藏点亮 → /rogue collection"); suggestions++ }
                if (suggestions == 0) add("§7继续选择路线，或者结算后回收低价值物品。")
                add("")
                add("§7下方按钮可快速打开相关系统。")
            }
        }
    }

    private fun shortcutItem(material: XMaterial, name: String, description: String, command: String): ItemStack = material.parseItem()!!.apply {
        itemMeta = itemMeta?.also { meta ->
            meta.setDisplayName(name)
            meta.lore = listOf("", description, "§7命令: §f$command", "", "§e点击打开")
        }
    }

    private fun lootKeys(): List<String> = listOf("temporary_gear", "unidentified_gear", "forge_book", "accessory", "sealed_accessory", "accessory_inscription")

    private fun lootLabel(key: String): String = when (key) {
        "temporary_gear" -> "临时装备"
        "unidentified_gear" -> "未鉴定装备"
        "forge_book" -> "锻造书"
        "accessory" -> "饰品"
        "sealed_accessory" -> "密封饰品"
        "accessory_inscription" -> "饰品刻印书"
        else -> key
    }

    private fun describeRouteBonus(modifiers: Map<RoomType, Int>): String {
        return modifiers.entries
            .sortedByDescending { kotlin.math.abs(it.value) }
            .take(4)
            .joinToString(" / ") { "${it.key.displayName}${signed(it.value)}" }
    }

    private fun describeAffixBonus(modifiers: Map<inori.roguecore.affix.AffixType, Int>): String {
        return modifiers.entries
            .sortedByDescending { kotlin.math.abs(it.value) }
            .take(3)
            .joinToString(" / ") { "${it.key.name}${signed(it.value)}" }
    }

    private fun describeFamilyBonus(modifiers: Map<String, Int>): String {
        return modifiers.entries
            .sortedByDescending { kotlin.math.abs(it.value) }
            .take(3)
            .joinToString(" / ") { "${it.key}${signed(it.value)}" }
    }

    private fun signed(value: Int): String {
        return if (value >= 0) "+$value" else value.toString()
    }

    private fun bars(value: Int, color: String): String {
        val clamped = value.coerceIn(0, 5)
        return color + "■".repeat(clamped) + "§8" + "■".repeat(5 - clamped)
    }
}
