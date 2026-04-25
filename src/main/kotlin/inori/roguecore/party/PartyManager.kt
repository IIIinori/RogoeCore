package inori.roguecore.party

import inori.roguecore.dungeon.DungeonManager
import inori.roguecore.dungeon.RunPersistenceManager
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

    private data class DungeonReconnect(
        val dungeonId: String,
        val disconnectAt: Long
    )

    data class PartySnapshot(
        val id: String,
        val leader: UUID,
        val maxSize: Int,
        val members: Set<UUID>,
        val dungeonId: String?
    )

    @Config("config.yml")
    lateinit var config: Configuration
        private set

    /** 所有队伍 */
    private val parties = ConcurrentHashMap<String, Party>()

    /** 玩家 -> 队伍 ID */
    private val playerPartyMap = ConcurrentHashMap<UUID, String>()

    /** 待处理的邀请: 被邀请者UUID -> (队伍ID, 过期时间戳) */
    private val pendingInvites = ConcurrentHashMap<UUID, Pair<String, Long>>()

    /** 副本中断线后的重连资格 */
    private val dungeonReconnects = ConcurrentHashMap<UUID, DungeonReconnect>()

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
        RunPersistenceManager.markDirty()

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
            clearDungeonReconnect(uuid)
            Bukkit.getPlayer(uuid)?.sendMessage("§c队伍已被解散")
        }
        parties.remove(party.id)
        RunPersistenceManager.markDirty()
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
        RunPersistenceManager.markDirty()

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

        val wasLeader = party.isLeader(player.uniqueId)
        val oldLeader = party.leader
        party.removeMember(player.uniqueId)
        playerPartyMap.remove(player.uniqueId)
        clearDungeonReconnect(player.uniqueId)

        if (party.members.isEmpty()) {
            parties.remove(party.id)
            RunPersistenceManager.markDirty()
            player.sendMessage("§e你离开了队伍")
            return
        }

        if (wasLeader) {
            val newLeader = findOnlineMember(party) ?: party.members.first()
            party.leader = newLeader
            broadcastLeaderChanged(party, oldLeader, newLeader)
        }

        RunPersistenceManager.markDirty()
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
        clearDungeonReconnect(target.uniqueId)
        RunPersistenceManager.markDirty()

        target.sendMessage("§c你被踢出了队伍")
        for (uuid in party.members) {
            Bukkit.getPlayer(uuid)?.sendMessage("§e${target.name} §7被踢出了队伍 §7(${party.size}/${party.maxSize})")
        }
    }

    /**
     * 获取玩家所在的队伍
     */
    fun getParty(player: Player): Party? {
        return getParty(player.uniqueId)
    }

    fun getParty(uuid: UUID): Party? {
        val partyId = playerPartyMap[uuid] ?: return null
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

    fun hasDungeonReconnect(uuid: UUID): Boolean {
        return dungeonReconnects.containsKey(uuid)
    }

    fun resolveReconnectDungeonId(uuid: UUID): String? {
        val partyDungeonId = getParty(uuid)?.dungeonId
        if (partyDungeonId != null) {
            return partyDungeonId
        }
        return dungeonReconnects[uuid]?.dungeonId
    }

    fun clearDungeonReconnect(uuid: UUID) {
        if (dungeonReconnects.remove(uuid) != null) {
            RunPersistenceManager.markDirty()
        }
    }

    fun getReconnectPlayersForDungeon(dungeonId: String): List<UUID> {
        return dungeonReconnects.filterValues { it.dungeonId == dungeonId }.keys.toList()
    }

    fun clearReconnectsForDungeon(dungeonId: String) {
        if (dungeonReconnects.entries.removeIf { (_, reconnect) -> reconnect.dungeonId == dungeonId }) {
            RunPersistenceManager.markDirty()
        }
    }

    fun markDungeonDisconnect(player: Player, dungeonId: String) {
        dungeonReconnects[player.uniqueId] = DungeonReconnect(dungeonId, System.currentTimeMillis())
        RunPersistenceManager.markDirty()
        getParty(player.uniqueId)?.let { party ->
            if (party.dungeonId == dungeonId && party.isLeader(player.uniqueId)) {
                promoteLeaderIfNeeded(party, player.uniqueId, dungeonId)
            }
            for (memberUuid in party.members) {
                if (memberUuid == player.uniqueId) {
                    continue
                }
                Bukkit.getPlayer(memberUuid)?.sendMessage("§e${player.name} §7暂时断开连接，可在回来后使用 §f/rogue rejoin §7回到副本。")
            }
        }
    }

    fun onDungeonMemberRemoved(uuid: UUID, dungeonId: String) {
        val party = getParty(uuid) ?: return
        if (party.dungeonId != dungeonId || !party.isLeader(uuid)) {
            return
        }
        promoteLeaderIfNeeded(party, uuid, dungeonId)
    }

    fun onPlayerJoin(player: Player) {
        val party = getParty(player.uniqueId) ?: return
        if (party.dungeonId != null) {
            ensureOnlineLeader(party, preferred = player.uniqueId, dungeonId = party.dungeonId)
        }
    }

    fun clearDungeonBinding(dungeonId: String) {
        clearReconnectsForDungeon(dungeonId)
        for (party in parties.values) {
            if (party.dungeonId == dungeonId) {
                party.dungeonId = null
                RunPersistenceManager.markDirty()
            }
        }
    }

    /**
     * 玩家下线时清理
     */
    fun onPlayerQuit(player: Player) {
        pendingInvites.remove(player.uniqueId)
        // 不自动离开队伍，允许短暂掉线重连
    }

    private fun ensureOnlineLeader(party: Party, preferred: UUID? = null, dungeonId: String? = null): UUID? {
        if (Bukkit.getPlayer(party.leader) != null) {
            return party.leader
        }
        val candidates = party.members.filter { memberUuid ->
            memberUuid != party.leader &&
                Bukkit.getPlayer(memberUuid) != null &&
                (dungeonId == null || DungeonManager.getDungeonId(memberUuid) == dungeonId || memberUuid == preferred)
        }
        val newLeader = when {
            preferred != null && preferred in candidates -> preferred
            candidates.isNotEmpty() -> candidates.first()
            else -> null
        } ?: return null
        val oldLeader = party.leader
        party.leader = newLeader
        broadcastLeaderChanged(party, oldLeader, newLeader)
        RunPersistenceManager.markDirty()
        return newLeader
    }

    private fun promoteLeaderIfNeeded(party: Party, leavingUuid: UUID, dungeonId: String? = null) {
        if (!party.isLeader(leavingUuid)) {
            return
        }
        val candidates = party.members.filter { memberUuid ->
            memberUuid != leavingUuid &&
                Bukkit.getPlayer(memberUuid) != null &&
                (dungeonId == null || DungeonManager.getDungeonId(memberUuid) == dungeonId)
        }
        val newLeader = candidates.firstOrNull() ?: return
        val oldLeader = party.leader
        party.leader = newLeader
        broadcastLeaderChanged(party, oldLeader, newLeader)
        RunPersistenceManager.markDirty()
    }

    private fun findOnlineMember(party: Party): UUID? {
        return party.members.firstOrNull { Bukkit.getPlayer(it) != null }
    }

    private fun broadcastLeaderChanged(party: Party, oldLeader: UUID, newLeader: UUID) {
        val oldLeaderName = Bukkit.getOfflinePlayer(oldLeader).name ?: oldLeader.toString()
        val newLeaderName = Bukkit.getOfflinePlayer(newLeader).name ?: newLeader.toString()
        for (memberUuid in party.members) {
            Bukkit.getPlayer(memberUuid)?.sendMessage("§e队长已从 §f$oldLeaderName §e转移为 §f$newLeaderName§e。")
        }
    }

    fun getPartySnapshots(): List<PartySnapshot> {
        return parties.values.map { party ->
            PartySnapshot(
                id = party.id,
                leader = party.leader,
                maxSize = party.maxSize,
                members = party.members.toSet(),
                dungeonId = party.dungeonId
            )
        }
    }

    fun getReconnectSnapshots(): Map<UUID, String> {
        return dungeonReconnects.mapValues { it.value.dungeonId }
    }

    fun restoreState(partiesSnapshot: List<PartySnapshot>, reconnects: Map<UUID, String>) {
        parties.clear()
        playerPartyMap.clear()
        pendingInvites.clear()
        dungeonReconnects.clear()

        for (snapshot in partiesSnapshot) {
            val party = Party(snapshot.id, snapshot.leader, snapshot.maxSize)
            party.members.clear()
            party.members.addAll(snapshot.members)
            party.dungeonId = snapshot.dungeonId
            parties[party.id] = party
            for (member in party.members) {
                playerPartyMap[member] = party.id
            }
        }

        for ((uuid, dungeonId) in reconnects) {
            dungeonReconnects[uuid] = DungeonReconnect(dungeonId, System.currentTimeMillis())
        }
        RunPersistenceManager.markDirty()
    }

    fun restoreDungeonReconnect(uuid: UUID, dungeonId: String) {
        dungeonReconnects[uuid] = DungeonReconnect(dungeonId, System.currentTimeMillis())
        RunPersistenceManager.markDirty()
    }
}
