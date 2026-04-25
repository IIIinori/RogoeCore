package inori.roguecore.event

import inori.roguecore.boon.BoonSelectManager
import inori.roguecore.boon.PlayerBoonData
import inori.roguecore.data.ShardRewardManager
import inori.roguecore.dungeon.DungeonInstance
import inori.roguecore.dungeon.room.RoomType
import inori.roguecore.relic.RelicSelectManager
import inori.roguecore.ui.DungeonGuiGuard
import inori.roguecore.unlock.UnlockManager
import org.bukkit.entity.Player
import taboolib.library.xseries.XMaterial
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest
import kotlin.random.Random

/**
 * 试炼房事件 — 付出代价换取确定收益。
 */
object TrialEvent {

    @Config("events.yml")
    lateinit var config: Configuration
        private set

    fun trigger(player: Player, instance: DungeonInstance) {
        val hpCostPercent = EventScaling.riskPercent(instance, config.getDouble("trial.hp-cost-percent", 0.2))
        val relicCostPercent = EventScaling.riskPercent(instance, config.getDouble("trial.relic-hp-cost-percent", 0.35))
        val shardMin = EventScaling.reward(instance, config.getInt("trial.shard-reward-min", 18))
        val shardMax = EventScaling.reward(instance, config.getInt("trial.shard-reward-max", 40)).coerceAtLeast(shardMin)
        val hasForbiddenTrials = UnlockManager.hasForbiddenTrials(player)
        val trialPower = EventAffixManager.getFamilyPower(instance, RoomType.TRIAL, "TRIAL")
        val scarletOath = trialPower > 0
        val hasBloodTrial = scarletOath || instance.config.floorNumber >= config.getInt("event-variants.trial.blood-trial-floor", 8)
        val bloodCostPercent = EventScaling.riskPercent(
            instance,
            config.getDouble("event-variants.trial.blood-trial-health-percent", 0.4) + trialPower * 0.01
        )
        val bloodShardMin = EventScaling.reward(instance, config.getInt("event-variants.trial.blood-trial-shard-min", 30) + trialPower * 4)
        val bloodShardMax = EventScaling.reward(instance, config.getInt("event-variants.trial.blood-trial-shard-max", 56) + trialPower * 6)
            .coerceAtLeast(bloodShardMin)
        val title = "§5§l试炼之室"

        DungeonGuiGuard.lock(player, title) { target -> trigger(target, instance) }

        player.openMenu<Chest>(title) {
            rows(3)
            handLocked(true)

            val shardSlot = if (hasForbiddenTrials) 9 else 10
            val boonSlot = when {
                hasBloodTrial && hasForbiddenTrials -> 11
                hasBloodTrial -> 12
                hasForbiddenTrials -> 11
                else -> 13
            }
            val bloodSlot = when {
                hasBloodTrial && hasForbiddenTrials -> 13
                hasBloodTrial -> 14
                else -> null
            }
            val masterySlot = when {
                hasBloodTrial && hasForbiddenTrials -> 15
                hasBloodTrial -> 16
                hasForbiddenTrials -> 15
                else -> 16
            }
            val relicSlot = if (hasForbiddenTrials) 17 else null
            val optionSlots = buildSet {
                add(shardSlot)
                add(boonSlot)
                add(masterySlot)
                if (bloodSlot != null) add(bloodSlot)
                if (relicSlot != null) add(relicSlot)
            }

            val glass = XMaterial.PURPLE_STAINED_GLASS_PANE.parseItem()!!.apply {
                itemMeta = itemMeta?.also { it.setDisplayName(" ") }
            }
            for (slot in 0 until 27) {
                if (slot !in optionSlots) {
                    set(slot, glass)
                }
            }

            val shardItem = XMaterial.AMETHYST_SHARD.parseItem()!!.apply {
                itemMeta = itemMeta?.also { meta ->
                    val reward = "$shardMin-$shardMax"
                    meta.setDisplayName("§6贪欲试炼")
                    meta.lore = listOf(
                        "",
                        "§7失去 §c${(hpCostPercent * 100).toInt()}% §7当前生命",
                        "§7获得 §6$reward §7本局碎片",
                        "",
                        "§e点击接受试炼"
                    )
                }
            }
            set(shardSlot, shardItem)

            val boonItem = XMaterial.ENCHANTED_BOOK.parseItem()!!.apply {
                itemMeta = itemMeta?.also { meta ->
                    meta.setDisplayName("§b启示试炼")
                    meta.lore = listOf(
                        "",
                        "§7失去 §c${(hpCostPercent * 100).toInt()}% §7当前生命",
                        "§7获得一次 §d神恩选择",
                        "",
                        "§e点击接受试炼"
                    )
                }
            }
            set(boonSlot, boonItem)

            if (bloodSlot != null) {
                val bloodItem = XMaterial.NETHER_WART.parseItem()!!.apply {
                    itemMeta = itemMeta?.also { meta ->
                        meta.setDisplayName("§4歃血试炼")
                        meta.lore = listOf(
                            "",
                            "§7失去 §c${(bloodCostPercent * 100).toInt()}% §7当前生命",
                            "§7获得 §6$bloodShardMin-$bloodShardMax §7本局碎片",
                            "§7并获得一次 §d神恩选择",
                            "",
                            "§e点击接受更残酷的试炼"
                        )
                    }
                }
                set(bloodSlot, bloodItem)
            }

            val masteryItem = XMaterial.ANVIL.parseItem()!!.apply {
                itemMeta = itemMeta?.also { meta ->
                    meta.setDisplayName("§a专精试炼")
                    meta.lore = listOf(
                        "",
                        "§7失去 §c${(hpCostPercent * 100).toInt()}% §7当前生命",
                        "§7随机升级一个已有神恩",
                        "§7若没有可升级神恩则改为神恩选择",
                        "",
                        "§e点击接受试炼"
                    )
                }
            }
            set(masterySlot, masteryItem)

            if (relicSlot != null) {
                val relicItem = XMaterial.DRAGON_BREATH.parseItem()!!.apply {
                    itemMeta = itemMeta?.also { meta ->
                        meta.setDisplayName("§5超越试炼")
                        meta.lore = listOf(
                            "",
                            "§7失去 §c${(relicCostPercent * 100).toInt()}% §7当前生命",
                            "§7获得一次 §d遗物选择",
                            "",
                            "§e点击接受试炼"
                        )
                    }
                }
                set(relicSlot, relicItem)
            }

            onClick { event ->
                event.isCancelled = true
                when (event.rawSlot) {
                    shardSlot -> {
                        DungeonGuiGuard.unlock(player)
                        player.closeInventory()
                        if (!applyHealthCost(player, hpCostPercent)) return@onClick
                        val reward = Random.nextInt(shardMin, shardMax + 1)
                        ShardRewardManager.addRunShards(player.uniqueId, reward)
                        player.sendMessage("§6试炼完成，获得 §e$reward §6本局碎片。")
                    }

                    boonSlot -> {
                        DungeonGuiGuard.unlock(player)
                        player.closeInventory()
                        if (!applyHealthCost(player, hpCostPercent)) return@onClick
                        player.sendMessage("§b试炼完成，你获得了新的启示。")
                        BoonSelectManager.offerBoonSelection(player, EventScaling.boonOfferCount(instance))
                    }

                    bloodSlot -> {
                        DungeonGuiGuard.unlock(player)
                        player.closeInventory()
                        if (!applyHealthCost(player, bloodCostPercent)) return@onClick
                        val reward = Random.nextInt(bloodShardMin, bloodShardMax + 1)
                        ShardRewardManager.addRunShards(player.uniqueId, reward)
                        player.sendMessage("§4歃血试炼完成，获得 §e$reward §4本局碎片。")
                        BoonSelectManager.offerBoonSelection(
                            player,
                            EventScaling.boonOfferCount(instance, if (scarletOath) 4 else 3)
                        )
                    }

                    masterySlot -> {
                        DungeonGuiGuard.unlock(player)
                        player.closeInventory()
                        if (!applyHealthCost(player, hpCostPercent)) return@onClick
                        val upgradeable = PlayerBoonData.getBoons(player).filter { it.canUpgrade }.randomOrNull()
                        if (upgradeable != null) {
                            PlayerBoonData.addBoon(player, upgradeable.boon)
                            player.sendMessage("§a试炼完成，你的神恩得到了磨砺。")
                        } else {
                            player.sendMessage("§7你没有可升级的神恩，转化为一次神恩选择。")
                            BoonSelectManager.offerBoonSelection(player, EventScaling.boonOfferCount(instance))
                        }
                    }

                    relicSlot -> {
                        DungeonGuiGuard.unlock(player)
                        player.closeInventory()
                        if (!applyHealthCost(player, relicCostPercent)) return@onClick
                        if (!RelicSelectManager.offerRelicSelection(player, EventScaling.relicOfferCount(instance, 3, UnlockManager.getRelicOfferBonus(player)))) {
                            player.sendMessage("§7没有新的遗物可供试炼，转化为一次神恩选择。")
                            BoonSelectManager.offerBoonSelection(player, EventScaling.boonOfferCount(instance))
                        }
                    }
                }
            }
        }
    }

    private fun applyHealthCost(player: Player, percent: Double): Boolean {
        val cost = (player.health * percent).coerceAtLeast(1.0)
        if (player.health <= cost) {
            player.sendMessage("§c你当前生命太低，无法承受这场试炼。")
            return false
        }
        player.damage(cost)
        return true
    }
}
