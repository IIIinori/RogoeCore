package inori.roguecore.ui

import inori.roguecore.dungeon.route.NextFloorRoute
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
            rows(5)
            handLocked(true)

            val contentSlots = setOf(10, 12, 14, 16, 22, 40)
            val glass = XMaterial.BLACK_STAINED_GLASS_PANE.parseItem()!!.apply {
                itemMeta = itemMeta?.also { it.setDisplayName(" ") }
            }
            for (slot in 0 until 45) {
                if (slot !in contentSlots) {
                    set(slot, glass)
                }
            }

            set(10, buildRoutesItem(player))
            set(12, buildRelicsItem(player))
            set(14, buildCoreEventsItem(player))
            set(16, buildAdvancedEventsItem(player))
            set(22, buildProgressItem(player, unlockedIds.size))
            set(40, XMaterial.ENCHANTING_TABLE.parseItem()!!.apply {
                itemMeta = itemMeta?.also { meta ->
                    meta.setDisplayName("§d返回研究所")
                    meta.lore = listOf("", "§7点击返回研究界面")
                }
            })

            onClick { event ->
                event.isCancelled = true
                when (event.rawSlot) {
                    40 -> {
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
