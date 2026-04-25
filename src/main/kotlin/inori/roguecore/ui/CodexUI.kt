package inori.roguecore.ui

import inori.roguecore.affix.AffixRegistry
import inori.roguecore.boon.BoonRegistry
import inori.roguecore.dungeon.route.NextFloorRoute
import inori.roguecore.event.EventAffixManager
import inori.roguecore.relic.RelicRegistry
import inori.roguecore.unlock.UnlockManager
import inori.roguecore.unlock.UnlockRegistry
import org.bukkit.entity.Player
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest

object CodexUI {

    fun open(player: Player) {
        val unlockedIds = UnlockManager.getUnlockedIds(player.uniqueId)

        player.openMenu<Chest>("§5§l冒险图鉴") {
            rows(6)
            handLocked(true)

            val contentSlots = setOf(10, 12, 14, 16, 28, 30, 32, 34, 49)
            val glass = XMaterial.BLACK_STAINED_GLASS_PANE.parseItem()!!.apply {
                itemMeta = itemMeta?.also { it.setDisplayName(" ") }
            }
            for (slot in 0 until 54) {
                if (slot !in contentSlots) {
                    set(slot, glass)
                }
            }

            set(10, buildRoutesItem(player))
            set(12, buildBoonsItem())
            set(14, buildRelicsItem(player))
            set(16, buildDungeonAffixesItem())
            set(28, buildCoreEventsItem(player))
            set(30, buildAdvancedEventsItem(player))
            set(32, buildEventAffixesItem())
            set(34, buildProgressItem(player, unlockedIds.size))
            set(49, XMaterial.ENCHANTING_TABLE.parseItem()!!.apply {
                itemMeta = itemMeta?.also { meta ->
                    meta.setDisplayName("§d返回研究所")
                    meta.lore = listOf("", "§7点击返回研究界面")
                }
            })

            onClick { event ->
                event.isCancelled = true
                when (event.rawSlot) {
                    49 -> {
                        player.closeInventory()
                        UnlockUI.open(player)
                    }
                }
            }
        }
    }

    private fun buildRoutesItem(player: Player) = XMaterial.FILLED_MAP.parseItem()!!.apply {
        itemMeta = itemMeta?.also { meta ->
            meta.setDisplayName("§a路线图鉴")
            val lore = mutableListOf<String>()
            lore += ""
            for (route in NextFloorRoute.entries) {
                val available = route.requiredUnlockId == null || UnlockManager.hasUnlock(player, route.requiredUnlockId)
                val enhanced = UnlockManager.getRouteWeightBonus(player.uniqueId, route).isNotEmpty() ||
                    UnlockManager.getRouteHiddenBonus(player.uniqueId, route) > 0.0
                lore += if (available) "§a${route.displayName}" else "§7${route.displayName} §8(未解锁)"
                lore += route.description.map { "§7$it" }
                if (enhanced) {
                    lore += "§d已获得研究强化"
                }
                lore += ""
            }
            meta.lore = lore
        }
    }

    private fun buildBoonsItem() = XMaterial.ENCHANTED_BOOK.parseItem()!!.apply {
        val boons = BoonRegistry.getAll()
        val byRarity = boons.groupBy { it.rarity }
        val tags = boons.flatMap { it.tags }.groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }
            .joinToString(" / ") { "${it.key}x${it.value}" }

        itemMeta = itemMeta?.also { meta ->
            meta.setDisplayName("§6神恩图鉴")
            meta.lore = buildList {
                add("")
                add("§7神恩总数: §f${boons.size}")
                for (rarity in byRarity.keys.sortedBy { it.ordinal }) {
                    add("${rarity.color}${rarity.displayName} §7x${byRarity[rarity]?.size ?: 0}")
                }
                add("")
                add("§7流派分布: §f$tags")
                add("§7更高稀有度更容易成为构筑核心")
            }
        }
    }

    private fun buildRelicsItem(player: Player) = XMaterial.AMETHYST_CLUSTER.parseItem()!!.apply {
        itemMeta = itemMeta?.also { meta ->
            meta.setDisplayName("§d遗物图鉴")
            val lore = mutableListOf<String>()
            val unlockedRelics = RelicRegistry.getAll().filter { relic ->
                relic.requiredUnlock == null || UnlockManager.hasUnlock(player, relic.requiredUnlock)
            }
            lore += ""
            lore += "§7当前可进入池子的遗物: §f${unlockedRelics.size}"
            lore += "§7遗物额外候选: §a+${UnlockManager.getRelicOfferBonus(player)}"
            lore += ""
            val byRarity = unlockedRelics.groupBy { it.rarity }
            for (rarity in byRarity.keys.sortedBy { it.ordinal }) {
                lore += "${rarity.color}${rarity.displayName} §7x${byRarity[rarity]?.size ?: 0}"
            }
            lore += ""
            lore += "§7高阶遗物研究:"
            lore += markLine(player, "advanced_relics", "古遗物学")
            lore += markLine(player, "legendary_relics", "神话残章")
            lore += markLine(player, "relic_transmutation", "遗物转化学")
            meta.lore = lore
        }
    }

    private fun buildDungeonAffixesItem() = XMaterial.BLAZE_POWDER.parseItem()!!.apply {
        val affixes = AffixRegistry.getAll()
        val difficulty = affixes.count { it.difficulty }
        val reward = affixes.count { !it.difficulty }
        val advanced = affixes.count { it.advanced }

        itemMeta = itemMeta?.also { meta ->
            meta.setDisplayName("§c副本词缀")
            meta.lore = listOf(
                "",
                "§7总词缀数: §f${affixes.size}",
                "§7难度词缀: §c$difficulty",
                "§7奖励词缀: §6$reward",
                "§7高层进阶词缀: §5$advanced",
                "",
                "§7越深层越容易遇到复合词缀组合"
            )
        }
    }

    private fun buildCoreEventsItem(player: Player) = XMaterial.ANVIL.parseItem()!!.apply {
        itemMeta = itemMeta?.also { meta ->
            meta.setDisplayName("§6核心事件强化")
            meta.lore = listOf(
                "",
                markLine(player, "black_market_network", "机遇路线强化"),
                markLine(player, "soul_tempering", "铁匠房开启灵魂淬火"),
                markLine(player, "treasure_cipher", "藏宝路线强化"),
                markLine(player, "sealed_vault", "隐藏房额外暗格"),
                markLine(player, "arcane_exchange", "商店房出售遗物契据")
            )
        }
    }

    private fun buildAdvancedEventsItem(player: Player) = XMaterial.CRYING_OBSIDIAN.parseItem()!!.apply {
        itemMeta = itemMeta?.also { meta ->
            meta.setDisplayName("§5高阶事件强化")
            meta.lore = listOf(
                "",
                markLine(player, "void_contract", "契约房开启虚空契约"),
                markLine(player, "abyssal_bargain", "契约房开启王冠契约"),
                markLine(player, "forbidden_trials", "试炼房开启超越试炼"),
                markLine(player, "sanctified_prayer", "神龛房开启净化祷告"),
                markLine(player, "extreme_route", "通关后可选择极境路线")
            )
        }
    }

    private fun buildEventAffixesItem() = XMaterial.BEACON.parseItem()!!.apply {
        val eventAffixes = EventAffixManager.getAll()
        val roomSummary = eventAffixes.flatMap { it.rooms }
            .groupingBy { it.displayName }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .joinToString(" / ") { "${it.key}x${it.value}" }

        itemMeta = itemMeta?.also { meta ->
            meta.setDisplayName("§d事件词缀")
            meta.lore = listOf(
                "",
                "§7事件词缀数: §f${eventAffixes.size}",
                "§7覆盖房型: §f$roomSummary",
                "§7高层会让事件房产生更明显变化",
                "",
                "§7留意房间提示，可提前判断收益倾向"
            )
        }
    }

    private fun buildProgressItem(player: Player, unlockedCount: Int) = XMaterial.WRITABLE_BOOK.parseItem()!!.apply {
        itemMeta = itemMeta?.also { meta ->
            val total = UnlockRegistry.getAll().size
            meta.setDisplayName("§f研究进度")
            meta.lore = listOf(
                "",
                "§7已完成研究: §d$unlockedCount§7/§d$total",
                "§7最高楼层会决定可研究上限",
                "",
                "§7建议优先路线:",
                "§a机遇系 → 铁匠/商店/契约强化",
                "§e藏宝系 → 隐藏房/遗物强化",
                "§c征伐系 → 极境路线/禁忌试炼"
            )
        }
    }

    private fun markLine(player: Player, unlockId: String, name: String): String {
        return if (UnlockManager.hasUnlock(player, unlockId)) {
            "§a$name"
        } else {
            "§7$name §8(未完成)"
        }
    }
}
