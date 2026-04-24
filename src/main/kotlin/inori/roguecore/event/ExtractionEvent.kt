package inori.roguecore.event

import inori.roguecore.data.ShardRewardManager
import inori.roguecore.dungeon.DungeonInstance
import inori.roguecore.dungeon.DungeonManager
import inori.roguecore.ui.DungeonGuiGuard
import org.bukkit.entity.Player
import taboolib.library.xseries.XMaterial
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest

/**
 * 撤离点事件。
 *
 * 玩家可以安全结束本次冒险，或把一部分本局碎片提前提现后继续推进。
 */
object ExtractionEvent {

    @Config("events.yml")
    lateinit var config: Configuration
        private set

    fun trigger(player: Player, instance: DungeonInstance) {
        val ratio = config.getDouble("extraction.cashout-ratio", 0.5).coerceIn(0.0, 1.0)
        val minCashOut = config.getInt("extraction.min-cashout-run-shards", 20).coerceAtLeast(0)
        val runShards = ShardRewardManager.getRunShards(player.uniqueId)
        val preview = ShardRewardManager.getCashOutPreview(player.uniqueId, ratio)
        val title = "§3§l撤离点 §7(本局碎片: §e$runShards§7)"
        val optionSlots = setOf(11, 13, 15)

        DungeonGuiGuard.lock(player, title) { target ->
            val latest = DungeonManager.getPlayerDungeon(target) ?: return@lock
            trigger(target, latest)
        }

        player.openMenu<Chest>(title) {
            rows(3)
            handLocked(true)

            val glass = XMaterial.CYAN_STAINED_GLASS_PANE.parseItem()!!.apply {
                itemMeta = itemMeta?.also { it.setDisplayName(" ") }
            }
            for (slot in 0 until 27) {
                if (slot !in optionSlots) {
                    set(slot, glass)
                }
            }

            set(11, XMaterial.ENDER_PEARL.parseItem()!!.apply {
                itemMeta = itemMeta?.also { meta ->
                    meta.setDisplayName("§a安全撤离")
                    meta.lore = listOf(
                        "",
                        "§7结束本次冒险并离开副本",
                        "§7当前本局碎片会正常结算",
                        "",
                        "§e点击撤离"
                    )
                }
            })

            set(13, XMaterial.COMPASS.parseItem()!!.apply {
                itemMeta = itemMeta?.also { meta ->
                    meta.setDisplayName("§b继续冒险")
                    meta.lore = listOf(
                        "",
                        "§7不进行任何结算",
                        "§7继续探索当前副本",
                        "",
                        "§e点击继续"
                    )
                }
            })

            val canCashOut = runShards >= minCashOut && preview > 0
            set(15, (if (canCashOut) XMaterial.AMETHYST_SHARD else XMaterial.BARRIER).parseItem()!!.apply {
                itemMeta = itemMeta?.also { meta ->
                    meta.setDisplayName(if (canCashOut) "§6提现继续" else "§7提现继续")
                    meta.lore = listOf(
                        "",
                        "§7将 §e${(ratio * 100).toInt()}% §7本局碎片提前结算",
                        "§7预计获得: §6$preview §7灵魂碎片",
                        "§7剩余本局碎片保留并继续冒险",
                        "",
                        if (canCashOut) "§e点击提现" else "§c至少需要 $minCashOut 本局碎片"
                    )
                }
            })

            onClick { event ->
                event.isCancelled = true
                when (event.rawSlot) {
                    11 -> {
                        DungeonGuiGuard.unlock(player)
                        player.closeInventory()
                        player.sendMessage("§a你选择安全撤离，本次冒险到此结束。")
                        DungeonManager.leaveDungeon(player)
                    }

                    13 -> {
                        DungeonGuiGuard.unlock(player)
                        player.closeInventory()
                        player.sendMessage("§b你放弃撤离，继续深入地牢。")
                    }

                    15 -> {
                        if (!canCashOut) {
                            player.sendMessage("§c本局碎片不足，无法在撤离点提现。")
                            return@onClick
                        }
                        DungeonGuiGuard.unlock(player)
                        player.closeInventory()
                        val gained = ShardRewardManager.cashOut(player.uniqueId, ratio)
                        if (gained > 0) {
                            player.sendMessage("§6你提前提现了 §e$gained §6灵魂碎片，剩余收益继续冒险。")
                        } else {
                            player.sendMessage("§c本次提现没有获得灵魂碎片。")
                        }
                    }
                }
            }
        }
    }
}
