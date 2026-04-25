package inori.roguecore.party

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 队伍数据
 */
class Party(
    val id: String,
    var leader: UUID,
    val maxSize: Int
) {
    /** 所有成员（包含队长） */
    val members: MutableSet<UUID> = ConcurrentHashMap.newKeySet<UUID>().also { it.add(leader) }

    /** 当前关联的副本 ID（进入副本后设置） */
    var dungeonId: String? = null

    val isFull: Boolean get() = members.size >= maxSize

    val size: Int get() = members.size

    fun isLeader(uuid: UUID): Boolean = leader == uuid

    fun isMember(uuid: UUID): Boolean = uuid in members

    fun addMember(uuid: UUID): Boolean {
        if (isFull) return false
        return members.add(uuid)
    }

    fun removeMember(uuid: UUID) {
        members.remove(uuid)
    }
}
