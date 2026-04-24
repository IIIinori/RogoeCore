package inori.roguecore.party

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 队伍管理器
 */
object PartyManager {

    @Config("config.yml")
    lateinit var config: Configuration
        private set

    /** 所有队伍 */
    private val parties = ConcurrentHashMap<String, Party>()

    /** 玩家 -> 队伍 ID */
    private val playerPartyMap = ConcurrentHashMap<UUID, String>()

    /** 待处理的邀请: 被邀请者UUID -> (队伍ID, 过期时间戳) */
    private val pendingInvites = ConcurrentHashMap<UUID, Pair<String, Long>>()

    private var maxSize = 4
    private var inviteTimeout = 60

    @Awake(LifeCycle.ENABLE)
    fun load() {
        maxSize = config.getInt("party.max-size", 4)
        inviteTimeout = config.getInt("party.invite-timeout", 60)
    }

    /**
     * 创建队伍
     */
    fun createParty(leader: Player): Party? {
        if (getParty(leader) != null) {
            leader.sendMessage("§c你已经在一个队伍中了!")
            return null
        }

        val id = UUID.randomUUID().toString().substring(0, 6)
        val party = Party(id, leader.uniqueId, maxSize)
        parties[id] = party
        playerPartyMap[leader.uniqueId] = id

        leader.sendMessage("§a队伍已创建! §7(ID: $id)")
        return party
    }

    /**
     * 解散队伍
     */
    fun disbandParty(player: Player) {
        val party = getParty(player)
        if (party == null) {
            player.sendMessage("§c你不在任何队伍中!")
            return
        }
        if (!party.isLeader(player.uniqueId)) {
            player.sendMessage("§c只有队长才能解散队伍!")
            return
        }

        // 通知所有成员
        for (uuid in party.members.toList()) {
            playerPartyMap.remove(uuid)
            Bukkit.getPlayer(uuid)?.sendMessage("§c队伍已被解散")
        }
        parties.remove(party.id)
    }

    /**
     * 邀请玩家
     */
    fun invitePlayer(player: Player, targetName: String) {
        val party = getParty(player)
        if (party == null) {
            player.sendMessage("§c你不在任何队伍中! 使用 /rogue create 创建队伍")
            return
        }
        if (!party.isLeader(player.uniqueId)) {
            player.sendMessage("§c只有队长才能邀请玩家!")
            return
        }
        if (party.isFull) {
            player.sendMessage("§c队伍已满! (${party.size}/${party.maxSize})")
            return
        }

        val target = Bukkit.getPlayerExact(targetName)
        if (target == null) {
            player.sendMessage("§c玩家 $targetName 不在线!")
            return
        }
        if (target.uniqueId == player.uniqueId) {
            player.sendMessage("§c不能邀请自己!")
            return
        }
        if (getParty(target) != null) {
            player.sendMessage("§c该玩家已在其他队伍中!")
            return
        }
        if (pendingInvites.containsKey(target.uniqueId)) {
            player.sendMessage("§c该玩家已有待处理的邀请!")
            return
        }

        val expireTime = System.currentTimeMillis() + inviteTimeout * 1000L
        pendingInvites[target.uniqueId] = party.id to expireTime

        player.sendMessage("§a已向 §f${target.name} §a发送邀请")
        target.sendMessage("§e§l[队伍邀请] §f${player.name} §e邀请你加入队伍")
        target.sendMessage("§e输入 §a/rogue accept §e接受邀请 §7(${inviteTimeout}秒内有效)")
    }

    /**
     * 接受邀请
     */
    fun acceptInvite(player: Player) {
        val invite = pendingInvites.remove(player.uniqueId)
        if (invite == null) {
            player.sendMessage("§c你没有待处理的邀请!")
            return
        }

        val (partyId, expireTime) = invite
        if (System.currentTimeMillis() > expireTime) {
            player.sendMessage("§c邀请已过期!")
            return
        }

        val party = parties[partyId]
        if (party == null) {
            player.sendMessage("§c队伍已不存在!")
            return
        }
        if (party.isFull) {
            player.sendMessage("§c队伍已满!")
            return
        }
        if (getParty(player) != null) {
            player.sendMessage("§c你已经在一个队伍中了!")
            return
        }

        party.addMember(player.uniqueId)
        playerPartyMap[player.uniqueId] = partyId

        // 通知全队
        for (uuid in party.members) {
            Bukkit.getPlayer(uuid)?.sendMessage("§a${player.name} §e加入了队伍 §7(${party.size}/${party.maxSize})")
        }
    }

    /**
     * 离开队伍
     */
    fun leaveParty(player: Player) {
        val party = getParty(player)
        if (party == null) {
            player.sendMessage("§c你不在任何队伍中!")
            return
        }

        // 队长离开 = 解散
        if (party.isLeader(player.uniqueId)) {
            disbandParty(player)
            return
        }

        party.removeMember(player.uniqueId)
        playerPartyMap.remove(player.uniqueId)

        player.sendMessage("§e你离开了队伍")
        for (uuid in party.members) {
            Bukkit.getPlayer(uuid)?.sendMessage("§e${player.name} §7离开了队伍 §7(${party.size}/${party.maxSize})")
        }
    }

    /**
     * 踢出玩家
     */
    fun kickPlayer(player: Player, targetName: String) {
        val party = getParty(player)
        if (party == null) {
            player.sendMessage("§c你不在任何队伍中!")
            return
        }
        if (!party.isLeader(player.uniqueId)) {
            player.sendMessage("§c只有队长才能踢人!")
            return
        }

        val target = Bukkit.getPlayerExact(targetName)
        if (target == null || !party.isMember(target.uniqueId)) {
            player.sendMessage("§c该玩家不在你的队伍中!")
            return
        }
        if (target.uniqueId == player.uniqueId) {
            player.sendMessage("§c不能踢出自己!")
            return
        }

        party.removeMember(target.uniqueId)
        playerPartyMap.remove(target.uniqueId)

        target.sendMessage("§c你被踢出了队伍")
        for (uuid in party.members) {
            Bukkit.getPlayer(uuid)?.sendMessage("§e${target.name} §7被踢出了队伍 §7(${party.size}/${party.maxSize})")
        }
    }

    /**
     * 获取玩家所在的队伍
     */
    fun getParty(player: Player): Party? {
        val partyId = playerPartyMap[player.uniqueId] ?: return null
        return parties[partyId]
    }

    /**
     * 通过 ID 获取队伍
     */
    fun getPartyById(id: String): Party? = parties[id]

    /**
     * 通过副本 ID 获取队伍
     */
    fun getPartyByDungeonId(dungeonId: String): Party? {
        return parties.values.firstOrNull { it.dungeonId == dungeonId }
    }

    /**
     * 玩家下线时清理
     */
    fun onPlayerQuit(player: Player) {
        pendingInvites.remove(player.uniqueId)
        // 不自动离开队伍，允许短暂掉线重连
    }

    fun clearDungeonBinding(dungeonId: String) {
        for (party in parties.values) {
            if (party.dungeonId == dungeonId) {
                party.dungeonId = null
            }
        }
    }
}
