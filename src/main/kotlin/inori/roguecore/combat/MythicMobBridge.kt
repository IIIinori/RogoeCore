package inori.roguecore.combat

import ink.ptms.um.Mythic
import org.bukkit.Location
import org.bukkit.entity.Entity
import taboolib.common.platform.function.warning

/**
 * MythicMobs 桥接层
 * 通过 UM 的 Mythic.API 操作怪物，MM 未加载时安全降级
 */
object MythicMobBridge {

    /**
     * MythicMobs 是否可用
     */
    fun isAvailable(): Boolean {
        return try {
            Mythic.isLoaded()
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 在指定位置生成 MM 怪物
     * @param mobId MythicMobs 怪物 ID
     * @param location 生成位置
     * @return 生成的实体，失败返回 null
     */
    fun spawnMob(mobId: String, location: Location): Entity? {
        if (!isAvailable()) {
            warning("[RogueCore] MythicMobs 未加载，无法生成怪物: $mobId")
            return null
        }

        val mobType = Mythic.API.getMobType(mobId)
        if (mobType == null) {
            warning("[RogueCore] 未找到 MythicMobs 怪物类型: $mobId")
            return null
        }

        return try {
            val activeMob = mobType.spawn(location, 1.0)
            activeMob?.entity
        } catch (e: Exception) {
            warning("[RogueCore] 生成怪物 $mobId 失败: ${e.message}")
            null
        }
    }

    /**
     * 判断实体是否是 MM 怪物
     */
    fun isMythicMob(entity: Entity): Boolean {
        if (!isAvailable()) return false
        return try {
            Mythic.API.getMob(entity) != null
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 获取 MM 怪物 ID
     */
    fun getMobId(entity: Entity): String? {
        if (!isAvailable()) return null
        return try {
            Mythic.API.getMob(entity)?.id
        } catch (_: Exception) {
            null
        }
    }
}
