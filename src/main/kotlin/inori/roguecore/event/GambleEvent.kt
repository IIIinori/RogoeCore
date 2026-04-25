package inori.roguecore.event

import inori.roguecore.boon.BoonSelectManager
import inori.roguecore.data.ShardRewardManager
import inori.roguecore.dungeon.DungeonInstance
import inori.roguecore.dungeon.room.RoomType
import inori.roguecore.item.DungeonLootManager
import inori.roguecore.modifier.RunModifierManager
import inori.roguecore.modifier.RunModifierType
import inori.roguecore.relic.RelicEffectHandler
import inori.roguecore.ui.DungeonGuiGuard
import org.bukkit.entity.Player
import taboolib.library.xseries.XMaterial
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest
import kotlin.random.Random

/**
 * 赌局房事件 — 明确选择稳健、加注或藏宝牌。
 */
object GambleEvent {

    @Config("events.yml")
    lateinit var config: Configuration
        private set

    fun trigger(player: Player, instance: DungeonInstance) {
        val title = "§2§l赌局桌"
        val currentStake = RunModifierManager.getValue(player, RunModifierType.GAMBLE_STREAK)

        DungeonGuiGuard.lock(player, title) { target -> trigger(target, instance) }

        player.openMenu<Chest>(title) {
            rows(3)
            handLocked(true)

            val optionSlots = setOf(11, 13, 15)
            val glass = XMaterial.GREEN_STAINED_GLASS_PANE.parseItem()!!.apply {
                itemMeta = itemMeta?.also { it.setDisplayName(" ") }
            }
            for (slot in 0 until 27) {
                if (slot !in optionSlots) set(slot, glass)
            }

            set(11, XMaterial.LIME_DYE.parseItem()!!.apply {
                itemMeta = itemMeta?.also { meta ->
                    meta.setDisplayName("§a稳健下注")
                    meta.lore = buildList {
                        add("")
                        add("§7正常奖励，失败惩罚降低")
                        add("§7不会产生下一次赌局加注")
                        if (currentStake > 1.0) add("§6当前已有加注: x${String.format("%.1f", currentStake)} §7会在本次消耗")
                        add("")
                        add("§e点击选择稳健下注")
                    }
                }
            })

            set(13, XMaterial.EMERALD.parseItem()!!.apply {
                itemMeta = itemMeta?.also { meta ->
                    val next = RunModifierManager.gambleStakeMultiplier(EventAffixManager.getFamilyPower(instance, RoomType.GAMBLE, "GAMBLE_HIGH"))
                    meta.setDisplayName(if (RunModifierManager.isEnabled(RunModifierType.GAMBLE_STREAK)) "§6连胜加注" else "§8连胜加注")
                    meta.lore = if (RunModifierManager.isEnabled(RunModifierType.GAMBLE_STREAK)) {
                        buildList {
                            add("")
                            add("§7本次收益与失败惩罚均提高")
                            add("§7若获胜，下一次赌局获得 §6x${String.format("%.1f", next)} §7加注")
                            if (currentStake > 1.0) add("§6当前已有加注: x${String.format("%.1f", currentStake)} §7会在本次叠入风险")
                            add("")
                            add("§e点击选择连胜加注")
                        }
                    } else {
                        listOf("", "§8当前服务器未启用赌局加注")
                    }
                }
            })

            set(15, XMaterial.MAP.parseItem()!!.apply {
                itemMeta = itemMeta?.also { meta ->
                    meta.setDisplayName("§6藏宝牌")
                    meta.lore = buildList {
                        add("")
                        add("§7更偏向装备/隐藏战利品结果")
                        add("§7碎片收益略低，失败惩罚正常")
                        if (currentStake > 1.0) add("§6当前已有加注: x${String.format("%.1f", currentStake)} §7会在本次消耗")
                        add("")
                        add("§e点击翻开藏宝牌")
                    }
                }
            })

            onClick { event ->
                event.isCancelled = true
                val mode = when (event.rawSlot) {
                    11 -> GambleMode.STEADY
                    13 -> {
                        if (!RunModifierManager.isEnabled(RunModifierType.GAMBLE_STREAK)) {
                            player.sendMessage("§c当前服务器未启用赌局加注。")
                            return@onClick
                        }
                        GambleMode.STAKE
                    }
                    15 -> GambleMode.TREASURE
                    else -> return@onClick
                }
                DungeonGuiGuard.unlock(player)
                player.closeInventory()
                resolve(player, instance, mode)
            }
        }
    }

    private fun resolve(player: Player, instance: DungeonInstance, mode: GambleMode) {
        val gamblePower = EventAffixManager.getFamilyPower(instance, RoomType.GAMBLE, "GAMBLE")
        val safePower = EventAffixManager.getFamilyPower(instance, RoomType.GAMBLE, "GAMBLE_SAFE")
        val highPower = EventAffixManager.getFamilyPower(instance, RoomType.GAMBLE, "GAMBLE_HIGH")
        val gearPower = EventAffixManager.getFamilyPower(instance, RoomType.GAMBLE, "GAMBLE_GEAR")
        val insurance = RelicEffectHandler.getGambleInsurancePercent(player) / 100.0
        val existingStake = RunModifierManager.consumeGambleMultiplier(player)
        if (existingStake > 1.0) {
            player.sendMessage("§6已有赌局加注生效: 本次基础倍率 x${String.format("%.1f", existingStake)}。")
        }

        val modeRewardMultiplier = when (mode) {
            GambleMode.STEADY -> 1.0
            GambleMode.STAKE -> 1.25
            GambleMode.TREASURE -> 0.85
        }
        val modeRiskMultiplier = when (mode) {
            GambleMode.STEADY -> 0.72
            GambleMode.STAKE -> 1.35
            GambleMode.TREASURE -> 1.0
        }
        val treasureBonus = if (mode == GambleMode.TREASURE) 16 else 0
        val roll = Random.nextInt(100)
        val winChance = (35 + gamblePower * 2 + if (mode == GambleMode.STAKE) highPower + 4 else 0).coerceAtMost(64)
        val boonChance = (55 + gamblePower + gearPower).coerceAtMost(74)
        val gearChance = (boonChance + gearPower * 2 + treasureBonus).coerceAtMost(92)
        val lossChance = (80 + gamblePower + safePower * 2 + if (mode == GambleMode.STEADY) 6 else 0).coerceAtMost(96)
        val totalRewardMultiplier = existingStake * modeRewardMultiplier
        val totalRiskMultiplier = existingStake * modeRiskMultiplier

        when {
            roll < winChance -> {
                val reward = (Random.nextInt(
                    EventScaling.reward(instance, config.getInt("gamble.shard-win-min", 12) + (gamblePower + highPower) * 2),
                    EventScaling.reward(instance, config.getInt("gamble.shard-win-max", 36) + (gamblePower + highPower) * 4) + 1
                ) * totalRewardMultiplier).toInt().coerceAtLeast(1)
                ShardRewardManager.addRunShards(player.uniqueId, reward)
                if (mode == GambleMode.STAKE && RunModifierManager.isEnabled(RunModifierType.GAMBLE_STREAK)) {
                    RunModifierManager.addModifier(player, RunModifierType.GAMBLE_STREAK, 0, 1, RunModifierManager.gambleStakeMultiplier(highPower), "赌局连胜")
                    player.sendMessage("§a加注得手，获得 §e$reward §a本局碎片。下一次赌局继续加注。")
                } else {
                    player.sendMessage("§a赌局得手，获得 §e$reward §a本局碎片。")
                }
            }

            roll < boonChance && mode != GambleMode.TREASURE -> {
                player.sendMessage("§d你抽到了神谕，获得一次神恩选择。")
                BoonSelectManager.offerBoonSelection(player, EventScaling.boonOfferCount(instance))
            }

            roll < gearChance -> {
                if (DungeonLootManager.grantHiddenLoot(player, instance)) {
                    player.sendMessage("§6你翻到了一张藏宝牌，获得一件隐藏战利品。")
                } else {
                    player.sendMessage("§d藏宝牌化作神谕，获得一次神恩选择。")
                    BoonSelectManager.offerBoonSelection(player, EventScaling.boonOfferCount(instance))
                }
            }

            roll < lossChance -> {
                val lossPercent = (EventScaling.riskPercent(instance, config.getDouble("gamble.loss-percent", 0.35) + highPower * 0.01 - safePower * 0.015) * (1.0 - insurance) * totalRiskMultiplier).coerceAtLeast(0.0)
                val current = ShardRewardManager.getRunShards(player.uniqueId)
                val loss = (current * lossPercent).toInt().coerceAtLeast(1)
                if (current > 0 && ShardRewardManager.takeRunShards(player.uniqueId, loss)) {
                    player.sendMessage("§c赌局失利，失去了 §e$loss §c本局碎片。")
                } else {
                    player.sendMessage("§7你这次赌输得不算太惨，只是空手而归。")
                }
            }

            else -> {
                val damagePercent = (EventScaling.riskPercent(instance, config.getDouble("gamble.damage-percent", 0.25) + highPower * 0.01 - safePower * 0.015) * (1.0 - insurance) * totalRiskMultiplier).coerceAtLeast(0.0)
                val damage = (player.health * damagePercent).coerceAtLeast(1.0)
                if (player.health > damage) {
                    player.damage(damage)
                    player.sendMessage("§c赌局翻车，你受到了反噬。")
                } else {
                    player.sendMessage("§7反噬险些要命，你侥幸逃过一劫。")
                }
            }
        }
    }

    private enum class GambleMode {
        STEADY,
        STAKE,
        TREASURE
    }
}
