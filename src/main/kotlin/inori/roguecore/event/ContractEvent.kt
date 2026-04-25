package inori.roguecore.event

import inori.roguecore.boon.BoonSelectManager
import inori.roguecore.curse.RunCurseManager
import inori.roguecore.curse.RunCurseType
import inori.roguecore.data.ShardRewardManager
import inori.roguecore.dungeon.DungeonInstance
import inori.roguecore.dungeon.room.RoomType
import inori.roguecore.item.DungeonLootManager
import inori.roguecore.modifier.RunModifierManager
import inori.roguecore.modifier.RunModifierType
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
 * 契约房事件。
 *
 * 玩家可选择高价值奖励，但会承受本局持续生效的诅咒。
 */
object ContractEvent {

    @Config("events.yml")
    lateinit var config: Configuration
        private set

    fun trigger(player: Player, instance: DungeonInstance) {
        val contractPower = EventAffixManager.getFamilyPower(instance, RoomType.CONTRACT, "CONTRACT")
        val voidBroker = contractPower > 0
        val shardBonus = EventScaling.reward(instance, contractPower * 4)
        val shardMin = EventScaling.reward(instance, config.getInt("contract.shard-reward-min", 26)) + shardBonus
        val shardMax = (EventScaling.reward(instance, config.getInt("contract.shard-reward-max", 52)) + shardBonus + contractPower * 4)
            .coerceAtLeast(shardMin)
        val title = "§4§l契约祭坛"
        val hasVoidContract = UnlockManager.hasUnlock(player, "void_contract")
        val hasAbyssalBargain = UnlockManager.hasAbyssalBargain(player)
        val relicOfferCount = EventScaling.relicOfferCount(instance, 3, UnlockManager.getRelicOfferBonus(player) + contractPower.coerceAtMost(4))
        val crownReward = EventScaling.reward(instance, config.getInt("contract.crown-shard-reward", 44).coerceAtLeast(1)) + contractPower * 8
        val debtGrant = RunModifierManager.soulDebtGrant(instance, contractPower + 2)
        val debtPrincipal = RunModifierManager.soulDebtPrincipal(debtGrant + contractPower * 6)
        val debtInterest = RunModifierManager.soulDebtInterest(instance, contractPower + 2)
        val debtRooms = RunModifierManager.soulDebtDeadlineRooms()

        DungeonGuiGuard.lock(player, title) { target -> trigger(target, instance) }

        player.openMenu<Chest>(title) {
            rows(4)
            handLocked(true)

            val debtSlot = 31
            val optionSlots = when {
                hasAbyssalBargain -> setOf(9, 11, 13, 15, 17, debtSlot)
                hasVoidContract -> setOf(10, 12, 14, 16, debtSlot)
                else -> setOf(10, 13, 16, debtSlot)
            }
            val glass = XMaterial.BLACK_STAINED_GLASS_PANE.parseItem()!!.apply {
                itemMeta = itemMeta?.also { it.setDisplayName(" ") }
            }
            for (slot in 0 until 36) {
                if (slot !in optionSlots) {
                    set(slot, glass)
                }
            }

            val bloodSlot = if (hasAbyssalBargain) 9 else 10
            val goldSlot = when {
                hasAbyssalBargain -> 11
                hasVoidContract -> 12
                else -> 13
            }
            val crownSlot = 13
            val blackSlot = when {
                hasAbyssalBargain -> 15
                hasVoidContract -> 14
                else -> 16
            }
            val voidSlot = when {
                hasAbyssalBargain -> 17
                else -> 16
            }

            set(bloodSlot, XMaterial.RED_DYE.parseItem()!!.apply {
                itemMeta = itemMeta?.also { meta ->
                    meta.setDisplayName("§c血肉契约")
                    meta.lore = listOf(
                        "",
                        "§7获得一次 §d遗物选择",
                        "§7但背负 §c${RunCurseType.FRAGILE.displayName}",
                        "§8${RunCurseType.FRAGILE.description}",
                        "",
                        if (RunCurseManager.hasCurse(player, RunCurseType.FRAGILE)) "§c你已持有该诅咒" else "§e点击签订"
                    )
                }
            })

            set(goldSlot, XMaterial.GOLD_NUGGET.parseItem()!!.apply {
                itemMeta = itemMeta?.also { meta ->
                    meta.setDisplayName("§6镀金契约")
                    meta.lore = listOf(
                        "",
                        "§7获得 §6$shardMin-$shardMax §7本局碎片",
                        "§7但背负 §c${RunCurseType.WITHERED.displayName}",
                        "§8${RunCurseType.WITHERED.description}",
                        "",
                        if (RunCurseManager.hasCurse(player, RunCurseType.WITHERED)) "§c你已持有该诅咒" else "§e点击签订"
                    )
                }
            })

            if (hasAbyssalBargain) {
                set(crownSlot, XMaterial.GOLDEN_HELMET.parseItem()!!.apply {
                    itemMeta = itemMeta?.also { meta ->
                        meta.setDisplayName("§6王冠契约")
                        meta.lore = listOf(
                            "",
                            "§7获得 §6$crownReward §7本局碎片",
                            "§7并额外获得一件 §6隐藏宝藏级 §7装备",
                            "§7但同时背负 §c${RunCurseType.VULNERABLE.displayName} §7与 §c${RunCurseType.HOLLOW.displayName}",
                            "",
                            if (RunCurseManager.hasCurse(player, RunCurseType.VULNERABLE) || RunCurseManager.hasCurse(player, RunCurseType.HOLLOW)) {
                                "§c你已持有相关诅咒"
                            } else {
                                "§e点击签订"
                            }
                        )
                    }
                })
            }

            set(blackSlot, XMaterial.NETHERITE_SCRAP.parseItem()!!.apply {
                itemMeta = itemMeta?.also { meta ->
                    meta.setDisplayName("§8黑铁契约")
                    meta.lore = listOf(
                        "",
                        "§7获得一件 §6隐藏宝藏级 §7临时装备",
                        "§7但背负 §c${RunCurseType.VULNERABLE.displayName}",
                        "§8${RunCurseType.VULNERABLE.description}",
                        "",
                        if (RunCurseManager.hasCurse(player, RunCurseType.VULNERABLE)) "§c你已持有该诅咒" else "§e点击签订"
                    )
                }
            })

            if (hasVoidContract) {
                set(voidSlot, XMaterial.CRYING_OBSIDIAN.parseItem()!!.apply {
                    itemMeta = itemMeta?.also { meta ->
                        meta.setDisplayName("§5虚空契约")
                        meta.lore = listOf(
                            "",
                            "§7获得一件 §6隐藏宝藏级 §7临时装备",
                            "§7并获得一次 §d遗物选择",
                            "§7但背负 §c${RunCurseType.HOLLOW.displayName}",
                            "§8${RunCurseType.HOLLOW.description}",
                            "",
                            if (RunCurseManager.hasCurse(player, RunCurseType.HOLLOW)) "§c你已持有该诅咒" else "§e点击签订"
                        )
                    }
                })
            }

            set(debtSlot, XMaterial.SOUL_LANTERN.parseItem()!!.apply {
                itemMeta = itemMeta?.also { meta ->
                    meta.setDisplayName(if (RunModifierManager.isEnabled(RunModifierType.SOUL_DEBT)) "§5深渊债契" else "§8深渊债契")
                    meta.lore = if (RunModifierManager.isEnabled(RunModifierType.SOUL_DEBT)) {
                        listOf(
                            "",
                            "§7立刻获得 §6$debtGrant §7本局碎片",
                            "§7并获得一次 §d神恩选择",
                            "§7生成 §c灵魂债务 §e$debtPrincipal",
                            "§7每房计息 §c+$debtInterest §7，到期 §b$debtRooms §7房",
                            "",
                            if (RunModifierManager.hasModifier(player, RunModifierType.SOUL_DEBT)) "§c你已背负灵魂债务" else "§e点击签订"
                        )
                    } else {
                        listOf("", "§8当前服务器未启用灵魂债务")
                    }
                }
            })

            onClick { event ->
                event.isCancelled = true
                when (event.rawSlot) {
                    bloodSlot -> {
                        if (!RunCurseManager.addCurse(player, RunCurseType.FRAGILE)) {
                            player.sendMessage("§c你已经承受了这份契约，换一个选择。")
                            return@onClick
                        }
                        DungeonGuiGuard.unlock(player)
                        player.closeInventory()
                        player.sendMessage("§d契约完成，你从血契中夺得了一件遗物。")
                        RelicSelectManager.offerRelicSelection(player, relicOfferCount)
                    }

                    goldSlot -> {
                        if (!RunCurseManager.addCurse(player, RunCurseType.WITHERED)) {
                            player.sendMessage("§c你已经承受了这份契约，换一个选择。")
                            return@onClick
                        }
                        DungeonGuiGuard.unlock(player)
                        player.closeInventory()
                        val reward = Random.nextInt(shardMin, shardMax + 1)
                        ShardRewardManager.addRunShards(player.uniqueId, reward)
                        player.sendMessage("§6契约完成，你获得了 §e$reward §6本局碎片。")
                    }

                    crownSlot -> {
                        if (!hasAbyssalBargain) {
                            return@onClick
                        }
                        if (RunCurseManager.hasCurse(player, RunCurseType.VULNERABLE) ||
                            RunCurseManager.hasCurse(player, RunCurseType.HOLLOW)) {
                            player.sendMessage("§c你已经背负了王冠契约所需的诅咒。")
                            return@onClick
                        }
                        RunCurseManager.addCurse(player, RunCurseType.VULNERABLE)
                        RunCurseManager.addCurse(player, RunCurseType.HOLLOW)
                        DungeonGuiGuard.unlock(player)
                        player.closeInventory()
                        ShardRewardManager.addRunShards(player.uniqueId, crownReward)
                        if (DungeonLootManager.grantHiddenLoot(player, instance)) {
                            player.sendMessage("§6王冠契约赏赐了一件更沉重的战利品。")
                        }
                        player.sendMessage("§6你以双重诅咒换得了 §e$crownReward §6本局碎片。")
                    }

                    blackSlot -> {
                        if (!RunCurseManager.addCurse(player, RunCurseType.VULNERABLE)) {
                            player.sendMessage("§c你已经承受了这份契约，换一个选择。")
                            return@onClick
                        }
                        DungeonGuiGuard.unlock(player)
                        player.closeInventory()
                        if (DungeonLootManager.grantHiddenLoot(player, instance)) {
                            player.sendMessage("§6契约完成，一件深藏的战利品落入你手中。")
                        } else {
                            player.sendMessage("§7隐藏战利品池为空，转化为一次神恩选择。")
                            BoonSelectManager.offerBoonSelection(player, EventScaling.boonOfferCount(instance))
                        }
                    }

                    voidSlot -> {
                        if (!hasVoidContract) {
                            return@onClick
                        }
                        if (!RunCurseManager.addCurse(player, RunCurseType.HOLLOW)) {
                            player.sendMessage("§c你已经承受了这份契约，换一个选择。")
                            return@onClick
                        }
                        DungeonGuiGuard.unlock(player)
                        player.closeInventory()
                        if (DungeonLootManager.grantHiddenLoot(player, instance)) {
                            player.sendMessage("§5虚空契约为你献上了一件深层战利品。")
                        }
                        RelicSelectManager.offerRelicSelection(player, relicOfferCount)
                    }

                    debtSlot -> {
                        if (!RunModifierManager.isEnabled(RunModifierType.SOUL_DEBT)) {
                            player.sendMessage("§c当前服务器未启用灵魂债务。")
                            return@onClick
                        }
                        if (RunModifierManager.hasModifier(player, RunModifierType.SOUL_DEBT)) {
                            player.sendMessage("§c你已经背负灵魂债务，先偿还再签新的债契。")
                            return@onClick
                        }
                        DungeonGuiGuard.unlock(player)
                        player.closeInventory()
                        ShardRewardManager.addRunShards(player.uniqueId, debtGrant)
                        RunModifierManager.addSoulDebt(player, debtPrincipal, debtInterest, debtRooms, "深渊债契", RunCurseType.HOLLOW.name)
                        player.sendMessage("§5深渊债契完成，你获得 §e$debtGrant §5本局碎片与一次神恩选择。")
                        BoonSelectManager.offerBoonSelection(player, EventScaling.boonOfferCount(instance))
                    }
                }
            }
        }
    }
}
