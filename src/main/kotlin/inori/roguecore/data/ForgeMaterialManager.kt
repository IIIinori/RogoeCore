package inori.roguecore.data

import inori.roguecore.dungeon.RunPersistenceManager
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 局内锻造材料管理。
 *
 * 不生成实体物品，只记录本次 run 的临时锻造资源。
 */
object ForgeMaterialManager {

    private val materials = ConcurrentHashMap<UUID, ConcurrentHashMap<ForgeMaterialType, Int>>()

    fun add(uuid: UUID, type: ForgeMaterialType, amount: Int) {
        if (amount <= 0) {
            return
        }
        val bag = materials.computeIfAbsent(uuid) { ConcurrentHashMap() }
        bag[type] = (bag[type] ?: 0) + amount
        RunPersistenceManager.markDirty()
    }

    fun get(uuid: UUID, type: ForgeMaterialType): Int {
        return materials[uuid]?.get(type) ?: 0
    }

    fun take(uuid: UUID, type: ForgeMaterialType, amount: Int): Boolean {
        if (amount <= 0) {
            return true
        }
        val bag = materials[uuid] ?: return false
        val current = bag[type] ?: 0
        if (current < amount) {
            return false
        }
        val remain = current - amount
        if (remain > 0) {
            bag[type] = remain
        } else {
            bag.remove(type)
        }
        if (bag.isEmpty()) {
            materials.remove(uuid)
        }
        RunPersistenceManager.markDirty()
        return true
    }

    fun clear(uuid: UUID) {
        if (materials.remove(uuid) != null) {
            RunPersistenceManager.markDirty()
        }
    }

    fun getAll(uuid: UUID): Map<ForgeMaterialType, Int> {
        return materials[uuid]?.toMap() ?: emptyMap()
    }

    fun restore(uuid: UUID, values: Map<ForgeMaterialType, Int>) {
        if (values.isEmpty()) {
            materials.remove(uuid)
            return
        }
        val bag = ConcurrentHashMap<ForgeMaterialType, Int>()
        for ((type, amount) in values) {
            if (amount > 0) {
                bag[type] = amount
            }
        }
        if (bag.isEmpty()) {
            materials.remove(uuid)
        } else {
            materials[uuid] = bag
        }
        RunPersistenceManager.markDirty()
    }
}

enum class ForgeMaterialType(
    val id: String,
    val displayName: String,
    val color: String
) {
    BOSS_EMBER("boss_ember", "炉核余烬", "§6"),
    HIDDEN_SIGIL("hidden_sigil", "秘库印记", "§9");

    fun coloredName(): String {
        return "$color$displayName"
    }
}
