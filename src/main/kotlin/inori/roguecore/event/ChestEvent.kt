package inori.roguecore.event

import inori.roguecore.boon.BoonSelectManager
import inori.roguecore.data.ShardRewardManager
import inori.roguecore.dungeon.DungeonInstance
import inori.roguecore.dungeon.room.Room
import inori.roguecore.item.DungeonLootManager
import org.bukkit.entity.Player
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration
import kotlin.random.Random

/**
 * 宝箱房事件 — 进入自动触发，给碎片和随机 Boon
 */
object ChestEvent {

    @Config("events.yml")
    lateinit var config: Configuration
        private set

    fun trigger(player: Player, instance: DungeonInstance, room: Room) {
        val shardMin = EventScaling.reward(instance, config.getInt("chest.shard-min", 10))
        val shardMax = EventScaling.reward(instance, config.getInt("chest.shard-max", 30)).coerceAtLeast(shardMin)
        val giveBoon = config.getBoolean("chest.give-boon", true)

        player.sendMessage("§6§l✦ §e你打开了宝箱!")

        // 局内碎片奖励
        val shards = Random.nextInt(shardMin, shardMax + 1)
        ShardRewardManager.onRoomClear(player.uniqueId, instance.config.floorNumber)
        ShardRewardManager.addRunShards(player.uniqueId, shards)
        player.sendMessage("§e  获得 §6$shards §e本局碎片")

        if (DungeonLootManager.grantChestLoot(player, instance)) {
            player.sendMessage("§6  宝箱里还藏着一件临时装备。")
        }

        // Boon
        if (giveBoon) {
            BoonSelectManager.offerBoonSelection(player, EventScaling.boonOfferCount(instance))
        }
    }
}
