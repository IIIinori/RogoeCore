package inori.roguecore.event

import inori.roguecore.combat.RoomState
import inori.roguecore.dungeon.DungeonInstance
import inori.roguecore.dungeon.room.Room
import inori.roguecore.dungeon.room.RoomType
import inori.roguecore.ui.DungeonSceneCueManager
import org.bukkit.entity.Player

/**
 * 房间事件调度器
 * 处理非战斗房间（商店/宝箱/休息）的进入事件
 */
object RoomEventManager {

    private fun announceEventRoom(trigger: Player, instance: DungeonInstance, roomType: RoomType) {
        DungeonSceneCueManager.broadcastEventRoom(instance, trigger, roomType)
    }

    private fun sendEventAffixHint(player: Player, instance: DungeonInstance, roomType: RoomType) {
        val affixes = EventAffixManager.getAffixesForRoom(instance, roomType)
        if (affixes.isEmpty()) {
            return
        }
        DungeonSceneCueManager.showEventAffixHint(player, affixes)
        player.sendMessage("§d本层事件词缀:")
        affixes.forEach { affix ->
            player.sendMessage("  ${affix.name} §7- ${affix.description}")
        }
    }

    /**
     * 玩家进入非战斗房间时调用
     * @return true 表示已处理
     */
    fun onEnterRoom(player: Player, instance: DungeonInstance, room: Room): Boolean {
        // 只处理 IDLE 状态的非战斗房间
        if (room.isCombatRoom) return false
        if (room.state != RoomState.IDLE) return false

        val onlinePlayers = instance.getOnlinePlayers()
        if (onlinePlayers.isEmpty()) {
            return false
        }

        when (room.type) {
            RoomType.SHOP -> {
                room.state = RoomState.CLEARED
                announceEventRoom(player, instance, room.type)
                for (member in onlinePlayers) {
                    if (member.uniqueId == player.uniqueId) {
                        member.sendMessage("§e§l🏪 你找到了商店，全队都可以购买。")
                    } else {
                        member.sendMessage("§e§l🏪 ${player.name} 找到了商店，你也获得了购买机会。")
                    }
                    sendEventAffixHint(member, instance, room.type)
                    ShopEvent.trigger(member, instance)
                }
                return true
            }
            RoomType.FORGE -> {
                room.state = RoomState.CLEARED
                announceEventRoom(player, instance, room.type)
                for (member in onlinePlayers) {
                    if (member.uniqueId == player.uniqueId) {
                        member.sendMessage("§6§l⚒ 你找到了铁匠铺，全队都可以重整装备。")
                    } else {
                        member.sendMessage("§6§l⚒ ${player.name} 找到了铁匠铺，你也可以进行锻造。")
                    }
                    sendEventAffixHint(member, instance, room.type)
                    ForgeEvent.trigger(member, instance)
                }
                return true
            }
            RoomType.CHEST -> {
                room.state = RoomState.CLEARED
                announceEventRoom(player, instance, room.type)
                for (member in onlinePlayers) {
                    if (member.uniqueId == player.uniqueId) {
                        member.sendMessage("§6§l✦ 你发现了共享宝箱!")
                    } else {
                        member.sendMessage("§6§l✦ ${player.name} 发现了共享宝箱，你也获得了奖励。")
                    }
                    ChestEvent.trigger(member, instance, room)
                }
                return true
            }
            RoomType.REST -> {
                room.state = RoomState.CLEARED
                announceEventRoom(player, instance, room.type)
                for (member in onlinePlayers) {
                    if (member.uniqueId == player.uniqueId) {
                        member.sendMessage("§b§l🛏 你找到了休息点，全队都可以选择恢复。")
                    } else {
                        member.sendMessage("§b§l🛏 ${player.name} 找到了休息点，你也可以进行选择。")
                    }
                    sendEventAffixHint(member, instance, room.type)
                    RestEvent.trigger(member, instance)
                }
                return true
            }
            RoomType.EXTRACTION -> {
                room.state = RoomState.CLEARED
                announceEventRoom(player, instance, room.type)
                for (member in onlinePlayers) {
                    if (member.uniqueId == player.uniqueId) {
                        member.sendMessage("§3§l⬖ 你找到了撤离点，全队都可以选择撤离或继续冒险。")
                    } else {
                        member.sendMessage("§3§l⬖ ${player.name} 找到了撤离点，你也可以做出选择。")
                    }
                    sendEventAffixHint(member, instance, room.type)
                    ExtractionEvent.trigger(member, instance)
                }
                return true
            }
            RoomType.CONTRACT -> {
                room.state = RoomState.CLEARED
                announceEventRoom(player, instance, room.type)
                for (member in onlinePlayers) {
                    if (member.uniqueId == player.uniqueId) {
                        member.sendMessage("§4§l✒ 你触碰了契约祭坛，全队都可以签下一份代价。")
                    } else {
                        member.sendMessage("§4§l✒ ${player.name} 触碰了契约祭坛，你也获得了选择权。")
                    }
                    sendEventAffixHint(member, instance, room.type)
                    ContractEvent.trigger(member, instance)
                }
                return true
            }
            RoomType.HIDDEN -> {
                if (!instance.consumeHiddenKey()) {
                    player.sendMessage("§9§l🔒 这间隐藏宝藏房需要一把隐藏钥匙。")
                    player.sendMessage("§7当前队伍钥匙: §b${instance.getHiddenKeys()}")
                    return true
                }
                room.state = RoomState.CLEARED
                announceEventRoom(player, instance, room.type)
                for (member in onlinePlayers) {
                    if (member.uniqueId == player.uniqueId) {
                        member.sendMessage("§9§l✦ 你使用隐藏钥匙开启了宝藏房。")
                    } else {
                        member.sendMessage("§9§l✦ ${player.name} 使用隐藏钥匙开启了宝藏房。")
                    }
                    member.sendMessage("§7剩余隐藏钥匙: §b${instance.getHiddenKeys()}")
                    sendEventAffixHint(member, instance, room.type)
                    HiddenEvent.trigger(member, instance)
                }
                return true
            }
            RoomType.TRIAL -> {
                room.state = RoomState.CLEARED
                announceEventRoom(player, instance, room.type)
                for (member in onlinePlayers) {
                    if (member.uniqueId == player.uniqueId) {
                        member.sendMessage("§5§l⚖ 你踏入了试炼之室，全队都要面对抉择。")
                    } else {
                        member.sendMessage("§5§l⚖ ${player.name} 触发了试炼之室，你也必须做出选择。")
                    }
                    sendEventAffixHint(member, instance, room.type)
                    TrialEvent.trigger(member, instance)
                }
                return true
            }
            RoomType.GAMBLE -> {
                room.state = RoomState.CLEARED
                announceEventRoom(player, instance, room.type)
                for (member in onlinePlayers) {
                    if (member.uniqueId == player.uniqueId) {
                        member.sendMessage("§2§l☘ 你找到了赌局桌，全队都能翻一次牌。")
                    } else {
                        member.sendMessage("§2§l☘ ${player.name} 找到了赌局桌，你也能参加一局。")
                    }
                    sendEventAffixHint(member, instance, room.type)
                    GambleEvent.trigger(member, instance)
                }
                return true
            }
            RoomType.SHRINE -> {
                room.state = RoomState.CLEARED
                announceEventRoom(player, instance, room.type)
                for (member in onlinePlayers) {
                    if (member.uniqueId == player.uniqueId) {
                        member.sendMessage("§f§l✧ 你唤醒了古老神龛，全队都能接受赐福。")
                    } else {
                        member.sendMessage("§f§l✧ ${player.name} 唤醒了古老神龛，你也能进行祈祷。")
                    }
                    sendEventAffixHint(member, instance, room.type)
                    ShrineEvent.trigger(member, instance)
                }
                return true
            }
            else -> return false
        }
    }
}
