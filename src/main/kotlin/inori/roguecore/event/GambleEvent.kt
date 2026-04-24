package inori.roguecore.event

import inori.roguecore.boon.BoonSelectManager
import inori.roguecore.data.ShardRewardManager
import inori.roguecore.dungeon.DungeonInstance
import inori.roguecore.ui.DungeonGuiGuard
import org.bukkit.entity.Player
import taboolib.library.xseries.XMaterial
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest
import kotlin.random.Random

/**
 * 赌局房事件 — 三选一翻牌，结果随机。
 */
object GambleEvent {

    @Config("events.yml")
    lateinit var config: Configuration
        private set

    fun trigger(player: Player, instance: DungeonInstance) {
        val title = "§2§l赌局桌"
        val slots = listOf(11, 13, 15)

        DungeonGuiGuard.lock(player, title) { target -> trigger(target, instance) }

        player.openMenu<Chest>(title) {
            rows(3)
            handLocked(true)

            val glass = XMaterial.GREEN_STAINED_GLASS_PANE.parseItem()!!.apply {
                itemMeta = itemMeta?.also { it.setDisplayName(" ") }
            }
            val card = XMaterial.CHEST.parseItem()!!.apply {
                itemMeta = itemMeta?.also { meta ->
                    meta.setDisplayName("§a未知赌注")
                    meta.lore = listOf("", "§7可能暴富，也可能付出代价", "", "§e点击翻开")
                }
            }

            for (slot in 0 until 27) {
                set(slot, if (slot in slots) card else glass)
            }

            onClick { event ->
                event.isCancelled = true
                if (event.rawSlot !in slots) return@onClick

                DungeonGuiGuard.unlock(player)
                player.closeInventory()
                resolve(player, instance)
            }
        }
    }

    private fun resolve(player: Player, instance: DungeonInstance) {
        val loadedDice = EventAffixManager.hasAffix(instance, "loaded_dice")
        val roll = Random.nextInt(100)
        when {
            roll < if (loadedDice) 45 else 35 -> {
                val reward = Random.nextInt(
                    EventScaling.reward(instance, config.getInt("gamble.shard-win-min", 12)),
                    EventScaling.reward(instance, config.getInt("gamble.shard-win-max", 36)) + if (loadedDice) 12 else 1
                )
                ShardRewardManager.addRunShards(player.uniqueId, reward)
                player.sendMessage("§a赌局得手，获得 §e$reward §a本局碎片。")
            }

            roll < if (loadedDice) 60 else 55 -> {
                player.sendMessage("§d你抽到了神谕，获得一次神恩选择。")
                BoonSelectManager.offerBoonSelection(player, EventScaling.boonOfferCount(instance))
            }

            roll < if (loadedDice) 85 else 80 -> {
                val lossPercent = EventScaling.riskPercent(instance, config.getDouble("gamble.loss-percent", 0.35) + if (loadedDice) 0.08 else 0.0)
                val current = ShardRewardManager.getRunShards(player.uniqueId)
                val loss = (current * lossPercent).toInt().coerceAtLeast(1)
                if (current > 0 && ShardRewardManager.takeRunShards(player.uniqueId, loss)) {
                    player.sendMessage("§c赌局失利，失去了 §e$loss §c本局碎片。")
                } else {
                    player.sendMessage("§7你这次赌输得不算太惨，只是空手而归。")
                }
            }

            else -> {
                val damagePercent = EventScaling.riskPercent(instance, config.getDouble("gamble.damage-percent", 0.25) + if (loadedDice) 0.08 else 0.0)
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
}
