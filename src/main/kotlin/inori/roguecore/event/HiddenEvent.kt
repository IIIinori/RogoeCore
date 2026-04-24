package inori.roguecore.event

import inori.roguecore.boon.BoonSelectManager
import inori.roguecore.data.ForgeMaterialManager
import inori.roguecore.data.ForgeMaterialType
import inori.roguecore.data.ShardRewardManager
import inori.roguecore.dungeon.DungeonInstance
import inori.roguecore.item.DungeonLootManager
import inori.roguecore.relic.RelicSelectManager
import inori.roguecore.unlock.UnlockManager
import org.bukkit.entity.Player
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration
import kotlin.random.Random

/**
 * 隐藏宝藏房事件。
 */
object HiddenEvent {

    @Config("events.yml")
    lateinit var config: Configuration
        private set

    fun trigger(player: Player, instance: DungeonInstance) {
        val stockpile = EventAffixManager.hasAffix(instance, "shadow_stockpile")
        val shardMin = EventScaling.reward(instance, config.getInt("hidden.shard-min", 24))
        val shardMax = EventScaling.reward(instance, config.getInt("hidden.shard-max", 48)).coerceAtLeast(shardMin)
        val giveBoon = config.getBoolean("hidden.give-boon", true)
        val materialBonus = EventScaling.materialBonus(instance)
        val sigilExtra = if (stockpile) 1 else 0
        val sigilMin = (config.getInt("forge.materials.hidden-sigil.reward-min", 1) + materialBonus + sigilExtra).coerceAtLeast(0)
        val sigilMax = (config.getInt("forge.materials.hidden-sigil.reward-max", sigilMin) + materialBonus + sigilExtra).coerceAtLeast(sigilMin)

        player.sendMessage("§9§l✦ 你开启了隐藏宝藏!")

        val shards = Random.nextInt(shardMin, shardMax + 1)
        ShardRewardManager.addRunShards(player.uniqueId, shards)
        player.sendMessage("§e  获得 §6$shards §e本局碎片")

        if (sigilMax > 0) {
            val sigils = if (sigilMax > sigilMin) Random.nextInt(sigilMin, sigilMax + 1) else sigilMin
            if (sigils > 0) {
                ForgeMaterialManager.add(player.uniqueId, ForgeMaterialType.HIDDEN_SIGIL, sigils)
                player.sendMessage("§9  获得 ${ForgeMaterialType.HIDDEN_SIGIL.coloredName()} §bx$sigils")
            }
        }

        if (DungeonLootManager.grantHiddenLoot(player, instance)) {
            player.sendMessage("§6  你从暗格中取出了一件临时装备。")
        }

        if (stockpile && DungeonLootManager.grantHiddenLoot(player, instance)) {
            player.sendMessage("§9  事件词缀撬开了更深的一层秘格。")
        }

        if (UnlockManager.hasSealedVault(player) && DungeonLootManager.grantHiddenLoot(player, instance)) {
            player.sendMessage("§9  封印秘库研究撬开了第二道暗格。")
        }

        val giveRelic = config.getBoolean("hidden.give-relic", true)
        val relicOfferCount = EventScaling.relicOfferCount(instance, 3, UnlockManager.getRelicOfferBonus(player))
        val relicOffered = giveRelic && RelicSelectManager.offerRelicSelection(player, relicOfferCount)

        if (giveBoon && !relicOffered) {
            BoonSelectManager.offerBoonSelection(player, EventScaling.boonOfferCount(instance))
        }
    }
}
