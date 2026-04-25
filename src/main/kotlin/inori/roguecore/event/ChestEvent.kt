package inori.roguecore.event

import inori.roguecore.boon.BoonSelectManager
import inori.roguecore.data.ForgeMaterialManager
import inori.roguecore.data.ForgeMaterialType
import inori.roguecore.data.ShardRewardManager
import inori.roguecore.dungeon.DungeonInstance
import inori.roguecore.dungeon.room.Room
import inori.roguecore.dungeon.room.RoomType
import inori.roguecore.item.DungeonLootManager
import inori.roguecore.modifier.RunModifierManager
import inori.roguecore.modifier.RunModifierType
import inori.roguecore.relic.RelicEffectHandler
import inori.roguecore.summary.RunSummaryManager
import inori.roguecore.ui.DungeonGuiGuard
import org.bukkit.entity.Player
import taboolib.library.xseries.XMaterial
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest
import kotlin.random.Random

/**
 * 宝箱房事件。
 *
 * 现在改为明确选择：普通开启拿即时收益，封印开启降低即时收益但获得后续战斗回响。
 */
object ChestEvent {

    @Config("events.yml")
    lateinit var config: Configuration
        private set

    fun trigger(player: Player, instance: DungeonInstance, room: Room) {
        openChestChoiceUI(player, instance, room)
    }

    private fun openChestChoiceUI(player: Player, instance: DungeonInstance, room: Room) {
        val chestPower = EventAffixManager.getFamilyPower(instance, RoomType.CHEST, "CHEST")
        val shardPower = EventAffixManager.getFamilyPower(instance, RoomType.CHEST, "CHEST_SHARD")
        val gearPower = EventAffixManager.getFamilyPower(instance, RoomType.CHEST, "CHEST_GEAR")
        val sealedPower = chestPower + gearPower + shardPower
        val sealedReward = RunModifierManager.sealedChestReward(instance, sealedPower)
        val sealedDuration = RunModifierManager.sealedChestDurationRooms()
        val sealedCharges = RunModifierManager.sealedChestCharges()
        val sealedEnabled = RunModifierManager.isEnabled(RunModifierType.SEALED_CHEST_PRESSURE)
        val title = "§6§l宝箱房"

        DungeonGuiGuard.lock(player, title) { target ->
            openChestChoiceUI(target, instance, room)
        }

        player.openMenu<Chest>(title) {
            rows(3)
            handLocked(true)

            val glass = XMaterial.ORANGE_STAINED_GLASS_PANE.parseItem()!!.apply {
                itemMeta = itemMeta?.also { it.setDisplayName(" ") }
            }
            for (slot in 0 until 27) {
                if (slot != 11 && slot != 15) {
                    set(slot, glass)
                }
            }

            set(11, XMaterial.CHEST.parseItem()!!.apply {
                itemMeta = itemMeta?.also { meta ->
                    meta.setDisplayName("§e普通开启")
                    meta.lore = listOf(
                        "",
                        "§7立即获得完整宝箱收益:",
                        "§6碎片 §7/ §b装备 §7/ §d神恩",
                        "",
                        "§8不会产生后续房间修正",
                        "§e点击普通开启"
                    )
                }
            })

            set(15, XMaterial.ENDER_CHEST.parseItem()!!.apply {
                itemMeta = itemMeta?.also { meta ->
                    meta.setDisplayName(if (sealedEnabled) "§5封印开启" else "§8封印开启")
                    meta.lore = if (sealedEnabled) {
                        listOf(
                            "",
                            "§7立即宝箱碎片收益降低 §c35%",
                            "§7但获得本局修正: §d${RunModifierType.SEALED_CHEST_PRESSURE.displayName}",
                            "§7后续战斗房通关额外获得 §6$sealedReward §7碎片",
                            "§7持续房间: §b$sealedDuration §7/ 触发次数: §d$sealedCharges",
                            "",
                            "§e点击封印开启"
                        )
                    } else {
                        listOf("", "§8当前服务器未启用封印宝箱回响")
                    }
                }
            })

            onClick { event ->
                event.isCancelled = true
                when (event.rawSlot) {
                    11 -> {
                        DungeonGuiGuard.unlock(player)
                        player.closeInventory()
                        resolve(player, instance, sealed = false)
                    }
                    15 -> {
                        if (!sealedEnabled) {
                            player.sendMessage("§c当前服务器未启用封印宝箱回响。")
                            return@onClick
                        }
                        DungeonGuiGuard.unlock(player)
                        player.closeInventory()
                        resolve(player, instance, sealed = true)
                    }
                }
            }
        }
    }

    private fun resolve(player: Player, instance: DungeonInstance, sealed: Boolean) {
        val chestPower = EventAffixManager.getFamilyPower(instance, RoomType.CHEST, "CHEST")
        val shardPower = EventAffixManager.getFamilyPower(instance, RoomType.CHEST, "CHEST_SHARD")
        val boonPower = EventAffixManager.getFamilyPower(instance, RoomType.CHEST, "CHEST_BOON")
        val forgePower = EventAffixManager.getFamilyPower(instance, RoomType.CHEST, "CHEST_FORGE")
        val gearPower = EventAffixManager.getFamilyPower(instance, RoomType.CHEST, "CHEST_GEAR")
        val relicMaterialBonus = RelicEffectHandler.getChestMaterialBonus(player)
        val affixChestBonus = inori.roguecore.affix.AffixManager.getChestShardBonus(instance)
        val shardMin = EventScaling.reward(instance, config.getInt("chest.shard-min", 10) + (chestPower + shardPower) * 3 + affixChestBonus)
        val shardMax = EventScaling.reward(instance, config.getInt("chest.shard-max", 30) + (chestPower + shardPower) * 6 + affixChestBonus * 2).coerceAtLeast(shardMin)
        val giveBoon = config.getBoolean("chest.give-boon", true)

        player.sendMessage(if (sealed) "§5§l✦ §d你以封印方式开启了宝箱!" else "§6§l✦ §e你打开了宝箱!")

        val rolledShards = Random.nextInt(shardMin, shardMax + 1)
        val shards = if (sealed) (rolledShards * 0.65).toInt().coerceAtLeast(1) else rolledShards
        ShardRewardManager.onRoomClear(player.uniqueId, instance.config.floorNumber)
        RunSummaryManager.onRoomCleared(player, RoomType.CHEST)
        ShardRewardManager.addRunShards(player.uniqueId, shards)
        player.sendMessage("§e  获得 §6$shards §e本局碎片" + if (sealed) " §8(封印开启折减)" else "")

        val extraChestLootChance = RelicEffectHandler.getChestExtraLootChance(player)
        val lootRolls = 1 + ((chestPower + gearPower) / 4).coerceAtMost(3)
        repeat(lootRolls) { index ->
            if (DungeonLootManager.grantChestLoot(player, instance)) {
                player.sendMessage(if (index == 0) "§6  宝箱里还藏着一件临时装备。" else "§6  事件词缀让宝箱额外吐出一件装备。")
            }
        }
        if (extraChestLootChance > 0.0 && Random.nextDouble() * 100.0 < extraChestLootChance && DungeonLootManager.grantChestLoot(player, instance)) {
            player.sendMessage("§6  遗物让宝箱额外吐出一件临时装备。")
        }

        if (forgePower > 0 || relicMaterialBonus > 0) {
            val embers = (forgePower / 3 + relicMaterialBonus).coerceAtLeast(1)
            val sigils = (forgePower / 5 + relicMaterialBonus / 2).coerceAtLeast(0)
            ForgeMaterialManager.add(player.uniqueId, ForgeMaterialType.BOSS_EMBER, embers)
            if (sigils > 0) {
                ForgeMaterialManager.add(player.uniqueId, ForgeMaterialType.HIDDEN_SIGIL, sigils)
            }
            player.sendMessage("§6  宝箱夹层中有锻造材料: ${ForgeMaterialType.BOSS_EMBER.coloredName()} §ex$embers" + if (sigils > 0) " §7+ ${ForgeMaterialType.HIDDEN_SIGIL.coloredName()} §bx$sigils" else "")
        }

        if (sealed && RunModifierManager.isEnabled(RunModifierType.SEALED_CHEST_PRESSURE)) {
            val sealedPower = chestPower + gearPower + shardPower
            val sealedReward = RunModifierManager.sealedChestReward(instance, sealedPower)
            RunModifierManager.addModifier(
                player,
                RunModifierType.SEALED_CHEST_PRESSURE,
                RunModifierManager.sealedChestDurationRooms(),
                RunModifierManager.sealedChestCharges(),
                sealedReward.toDouble(),
                "封印宝箱"
            )
        }

        if (giveBoon) {
            val extra = if (sealed) 0 else ((chestPower + boonPower) / 3).coerceAtMost(3)
            BoonSelectManager.offerBoonSelection(player, EventScaling.boonOfferCount(instance, 1 + extra))
        }
    }
}
