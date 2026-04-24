package inori.roguecore.event

import inori.roguecore.boon.BoonSelectManager
import inori.roguecore.boon.PlayerBoonData
import inori.roguecore.curse.RunCurseManager
import inori.roguecore.data.ShardRewardManager
import inori.roguecore.dungeon.DungeonInstance
import inori.roguecore.relic.RelicSelectManager
import inori.roguecore.ui.DungeonGuiGuard
import inori.roguecore.unlock.UnlockManager
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import taboolib.library.xseries.XMaterial
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest

/**
 * 神龛房事件 — 在稳健祝福和痛苦赐福之间选择。
 */
object ShrineEvent {

    @Config("events.yml")
    lateinit var config: Configuration
        private set

    fun trigger(player: Player, instance: DungeonInstance) {
        val blessingCost = EventScaling.price(instance, config.getInt("shrine.blessing-cost", 20))
        val purifyPrice = EventScaling.price(instance, config.getInt("shrine.purify-price", 26))
        val healPercent = config.getDouble("shrine.heal-percent", 0.3)
        val sacrificePercent = EventScaling.riskPercent(instance, config.getDouble("shrine.sacrifice-health-percent", 0.2))
        val shardReward = EventScaling.reward(instance, config.getInt("shrine.sacrifice-shard-reward", 28))
        val hasSanctifiedPrayer = UnlockManager.hasSanctifiedPrayer(player)
        val twinnedFaith = EventAffixManager.hasAffix(instance, "twinned_faith")
        val hasTwinPrayer = twinnedFaith || instance.config.floorNumber >= config.getInt("event-variants.shrine.twin-prayer-floor", 8)
        val twinPrice = EventScaling.price(instance, config.getInt("event-variants.shrine.twin-prayer-price", 44))
        val bonusHeal = if (twinnedFaith) 0.1 else 0.0
        val effectiveHealPercent = (healPercent + bonusHeal).coerceAtMost(1.0)
        val twinHealPercent = (config.getDouble("event-variants.shrine.twin-prayer-heal-percent", 0.45) + bonusHeal).coerceAtLeast(effectiveHealPercent)
        val title = "§f§l古老神龛"

        DungeonGuiGuard.lock(player, title) { target -> trigger(target, instance) }

        player.openMenu<Chest>(title) {
            rows(3)
            handLocked(true)

            val blessingSlot = when {
                hasTwinPrayer && hasSanctifiedPrayer -> 9
                hasTwinPrayer -> 10
                hasSanctifiedPrayer -> 10
                else -> 11
            }
            val purifySlot = if (hasSanctifiedPrayer) {
                if (hasTwinPrayer) 11 else 13
            } else {
                null
            }
            val twinSlot = if (hasTwinPrayer) {
                if (hasSanctifiedPrayer) 15 else 12
            } else {
                null
            }
            val sacrificeSlot = when {
                hasTwinPrayer && hasSanctifiedPrayer -> 17
                hasTwinPrayer -> 14
                hasSanctifiedPrayer -> 16
                else -> 15
            }
            val optionSlots = buildSet {
                add(blessingSlot)
                add(sacrificeSlot)
                if (purifySlot != null) add(purifySlot)
                if (twinSlot != null) add(twinSlot)
            }

            val glass = XMaterial.WHITE_STAINED_GLASS_PANE.parseItem()!!.apply {
                itemMeta = itemMeta?.also { it.setDisplayName(" ") }
            }
            for (slot in 0 until 27) {
                if (slot !in optionSlots) {
                    set(slot, glass)
                }
            }

            val blessingItem = XMaterial.END_CRYSTAL.parseItem()!!.apply {
                itemMeta = itemMeta?.also { meta ->
                        meta.setDisplayName("§b虔诚祈祷")
                        meta.lore = listOf(
                            "",
                            "§7消耗 §e$blessingCost §7本局碎片",
                            "§7回复 §a${(effectiveHealPercent * 100).toInt()}% §7最大生命",
                            "§7并获得一次 §d神恩选择",
                            "",
                            "§e点击接受赐福"
                    )
                }
            }
            set(blessingSlot, blessingItem)

            val sacrificeItem = XMaterial.BLAZE_POWDER.parseItem()!!.apply {
                itemMeta = itemMeta?.also { meta ->
                    meta.setDisplayName("§6痛苦献祭")
                    meta.lore = listOf(
                        "",
                        "§7失去 §c${(sacrificePercent * 100).toInt()}% §7当前生命",
                        "§7获得 §6$shardReward §7本局碎片",
                        "",
                        "§e点击接受代价"
                    )
                }
            }
            set(sacrificeSlot, sacrificeItem)

            if (purifySlot != null) {
                val hasCurse = RunCurseManager.getCurses(player).isNotEmpty()
                val purifyItem = XMaterial.TOTEM_OF_UNDYING.parseItem()!!.apply {
                    itemMeta = itemMeta?.also { meta ->
                        meta.setDisplayName("§a净化祷告")
                        meta.lore = listOf(
                            "",
                            "§7消耗 §e$purifyPrice §7本局碎片",
                            if (hasCurse) "§7移除一条随机契约诅咒" else "§7若没有诅咒，则改为一次遗物选择",
                            "§7并回复 §a${(effectiveHealPercent * 100).toInt()}% §7最大生命",
                            "",
                            "§e点击祈求净化"
                        )
                    }
                }
                set(purifySlot, purifyItem)
            }

            if (twinSlot != null) {
                val twinItem = XMaterial.BEACON.parseItem()!!.apply {
                    itemMeta = itemMeta?.also { meta ->
                        meta.setDisplayName("§d双生祷文")
                        meta.lore = listOf(
                            "",
                            "§7消耗 §e$twinPrice §7本局碎片",
                            "§7回复 §a${(twinHealPercent * 100).toInt()}% §7最大生命",
                            "§7额外获得 1 份随机神恩",
                            "§7并净化一条诅咒，若没有诅咒则改为遗物选择",
                            "",
                            "§e点击献上更昂贵的祷词"
                        )
                    }
                }
                set(twinSlot, twinItem)
            }

            onClick { event ->
                event.isCancelled = true
                when (event.rawSlot) {
                    blessingSlot -> {
                        DungeonGuiGuard.unlock(player)
                        player.closeInventory()
                        if (!ShardRewardManager.takeRunShards(player.uniqueId, blessingCost)) {
                            player.sendMessage("§c你的本局碎片不足，无法完成祈祷。")
                            return@onClick
                        }
                        val maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.value ?: 20.0
                        player.health = (player.health + maxHealth * effectiveHealPercent).coerceAtMost(maxHealth)
                        player.sendMessage("§b神龛回应了你的祈祷。")
                        BoonSelectManager.offerBoonSelection(player, EventScaling.boonOfferCount(instance))
                    }

                    purifySlot -> {
                        DungeonGuiGuard.unlock(player)
                        player.closeInventory()
                        if (!ShardRewardManager.takeRunShards(player.uniqueId, purifyPrice)) {
                            player.sendMessage("§c你的本局碎片不足，无法完成净化祷告。")
                            return@onClick
                        }
                        val maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.value ?: 20.0
                        player.health = (player.health + maxHealth * effectiveHealPercent).coerceAtMost(maxHealth)
                        val removed = RunCurseManager.getCurses(player).randomOrNull()?.let { RunCurseManager.removeCurse(player, it) } == true
                        if (!removed) {
                            player.sendMessage("§7你身上没有可净化的诅咒，神龛赐下了一次遗物选择。")
                            if (!RelicSelectManager.offerRelicSelection(player, EventScaling.relicOfferCount(instance, 3, UnlockManager.getRelicOfferBonus(player)))) {
                                BoonSelectManager.offerBoonSelection(player, EventScaling.boonOfferCount(instance))
                            }
                        }
                    }

                    twinSlot -> {
                        DungeonGuiGuard.unlock(player)
                        player.closeInventory()
                        if (!ShardRewardManager.takeRunShards(player.uniqueId, twinPrice)) {
                            player.sendMessage("§c你的本局碎片不足，无法完成双生祷文。")
                            return@onClick
                        }
                        val maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.value ?: 20.0
                        player.health = (player.health + maxHealth * twinHealPercent).coerceAtMost(maxHealth)
                        val boon = BoonSelectManager.rollBoons(1, player).firstOrNull()
                        if (boon != null) {
                            PlayerBoonData.addBoon(player, boon)
                            player.sendMessage("§d神龛额外赐下了 ${boon.rarity.color}${boon.name}")
                        }
                        val removed = RunCurseManager.getCurses(player).randomOrNull()?.let { RunCurseManager.removeCurse(player, it) } == true
                        if (!removed) {
                            player.sendMessage("§7没有可净化的诅咒，双生祷文转化为一次遗物选择。")
                            if (!RelicSelectManager.offerRelicSelection(player, EventScaling.relicOfferCount(instance, 3, UnlockManager.getRelicOfferBonus(player)))) {
                                BoonSelectManager.offerBoonSelection(player, EventScaling.boonOfferCount(instance))
                            }
                        }
                    }

                    sacrificeSlot -> {
                        DungeonGuiGuard.unlock(player)
                        player.closeInventory()
                        val damage = (player.health * sacrificePercent).coerceAtLeast(1.0)
                        if (player.health <= damage) {
                            player.sendMessage("§c你现在太虚弱，无法承受这场献祭。")
                            return@onClick
                        }
                        player.damage(damage)
                        ShardRewardManager.addRunShards(player.uniqueId, shardReward)
                        player.sendMessage("§6你以痛苦换来了 §e$shardReward §6本局碎片。")
                    }
                }
            }
        }
    }
}
