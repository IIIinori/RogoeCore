package inori.roguecore.boon

import org.bukkit.entity.Player
import org.serverct.ersha.api.AttributeAPI
import taboolib.common.platform.function.warning
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 玩家 Boon 数据管理
 * 负责 Boon 的添加/升级/清除，以及与 AttributePlus 的属性同步
 */
object PlayerBoonData {

    /** 玩家 UUID -> 持有的 Boon 实例列表 */
    private val playerBoons = ConcurrentHashMap<UUID, MutableList<BoonInstance>>()

    /** AP 属性源 key 前缀 */
    private const val AP_SOURCE_PREFIX = "rogue_boon_"

    /**
     * 获取玩家所有 Boon
     */
    fun getBoons(player: Player): List<BoonInstance> {
        return playerBoons[player.uniqueId]?.toList() ?: emptyList()
    }

    fun getBoons(uuid: UUID): List<BoonInstance> {
        return playerBoons[uuid]?.toList() ?: emptyList()
    }

    fun getTagCount(uuid: UUID, tag: String): Int {
        if (tag.isBlank()) {
            return 0
        }
        return getBoons(uuid).sumOf { instance -> instance.boon.tags.count { it == tag } }
    }

    /**
     * 添加 Boon（已有同 ID 则升级）
     */
    fun addBoon(player: Player, boon: Boon) {
        val boons = playerBoons.getOrPut(player.uniqueId) { mutableListOf() }

        val existing = boons.firstOrNull { it.boon.id == boon.id }
        if (existing != null) {
            // 已有，升级
            existing.upgrade()
            applyBoonToAP(player, existing)
            player.sendMessage("§6${boon.rarity.color}${boon.name} §e升级到 Lv.${existing.level}!")
        } else {
            // 新增
            val instance = BoonInstance(boon)
            boons.add(instance)
            applyBoonToAP(player, instance)
            player.sendMessage("§a获得 ${boon.rarity.color}${boon.name} §aLv.1!")
        }
    }

    /**
     * 清除玩家所有 Boon（死亡/离开副本时调用）
     */
    fun clearBoons(player: Player) {
        val boons = playerBoons.remove(player.uniqueId) ?: return

        // 从 AP 移除所有 Boon 属性源
        for (instance in boons) {
            removeBoonFromAP(player, instance)
        }
    }

    /**
     * 将单个 Boon 的属性应用到 AP
     * 先移除旧的再添加新的（处理升级场景）
     */
    private fun applyBoonToAP(player: Player, instance: BoonInstance) {
        try {
            val data = AttributeAPI.getAttrData(player) ?: return
            val sourceKey = "$AP_SOURCE_PREFIX${instance.boon.id}"

            // 先移除旧属性（如果存在）
            AttributeAPI.takeSourceAttribute(data, sourceKey)

            // 添加新属性
            val attrs = instance.boon.getAttributesAtLevel(instance.level)
            AttributeAPI.addSourceAttribute(data, sourceKey, attrs)

            // 刷新属性
            AttributeAPI.updateAttribute(player)
        } catch (e: Exception) {
            warning("[RogueCore] 应用 Boon ${instance.boon.id} 到 AP 失败: ${e.message}")
        }
    }

    /**
     * 从 AP 移除单个 Boon 的属性
     */
    private fun removeBoonFromAP(player: Player, instance: BoonInstance) {
        try {
            val data = AttributeAPI.getAttrData(player) ?: return
            val sourceKey = "$AP_SOURCE_PREFIX${instance.boon.id}"
            AttributeAPI.takeSourceAttribute(data, sourceKey)
            AttributeAPI.updateAttribute(player)
        } catch (e: Exception) {
            warning("[RogueCore] 移除 Boon ${instance.boon.id} 从 AP 失败: ${e.message}")
        }
    }
}
