package inori.roguecore.talent

import inori.roguecore.data.DatabaseManager
import inori.roguecore.data.PlayerDataManager
import inori.roguecore.dependency.DependencySelfCheckManager
import org.bukkit.entity.Player
import org.serverct.ersha.api.AttributeAPI
import taboolib.common.platform.function.warning
import taboolib.expansion.DataContainer
import java.util.UUID

/**
 * 天赋管理器
 * 负责：天赋等级查询/升级、进入副本时应用天赋效果、灵魂碎片/Boon 加成计算
 */
object TalentManager {

    private const val AP_SOURCE_PREFIX = "rogue_talent_"
    private const val TALENT_PREFIX = "talent."

    fun talentKey(talentId: String): String {
        return "$TALENT_PREFIX$talentId"
    }

    fun getTalentLevel(uuid: UUID, talentId: String): Int {
        return DatabaseManager.getOrCreateContainer(uuid).getInt(talentKey(talentId))
    }

    fun getPlayerTalents(uuid: UUID): Map<String, Int> {
        val container = DatabaseManager.getOrCreateContainer(uuid)
        return container.values()
            .filterKeys { it.startsWith(TALENT_PREFIX) }
            .mapKeys { it.key.removePrefix(TALENT_PREFIX) }
            .mapValues { it.value.toIntOrNull() ?: 0 }
            .filterValues { it > 0 }
    }

    fun upgradeTalent(player: Player, talentId: String): Boolean {
        val talent = TalentRegistry.get(talentId) ?: return false
        val currentLevel = getTalentLevel(player.uniqueId, talentId)

        if (currentLevel >= talent.maxLevel) {
            player.sendMessage("§c该天赋已满级!")
            return false
        }

        val cost = talent.getCost(currentLevel + 1)
        if (!PlayerDataManager.takeSoulShards(player.uniqueId, cost)) {
            player.sendMessage("§c灵魂碎片不足! 需要 §e$cost §c碎片")
            return false
        }

        val newLevel = currentLevel + 1
        setTalentLevel(player.uniqueId, talentId, newLevel)
        player.sendMessage("§a天赋 §f${talent.name} §a升级到 Lv.$newLevel!")
        return true
    }

    fun setTalentLevel(uuid: UUID, talentId: String, level: Int): Boolean {
        val talent = TalentRegistry.get(talentId) ?: return false
        val container = DatabaseManager.getOrCreateContainer(uuid)
        container[talentKey(talent.id)] = level.coerceIn(0, talent.maxLevel)
        return true
    }

    fun clearAll(uuid: UUID) {
        val container = DatabaseManager.getOrCreateContainer(uuid)
        for (talentId in getPlayerTalents(uuid).keys) {
            container[talentKey(talentId)] = 0
        }
    }

    fun applyTalents(player: Player) {
        if (!DependencySelfCheckManager.isAttributePlusAvailable()) {
            DependencySelfCheckManager.warnAttributePlusUnavailable("天赋属性")
            return
        }
        val talents = getPlayerTalents(player.uniqueId)

        for ((talentId, level) in talents) {
            val talent = TalentRegistry.get(talentId) ?: continue
            if (talent.effectType != TalentEffectType.ATTRIBUTE) continue
            if (talent.attribute.isBlank()) continue

            try {
                val data = AttributeAPI.getAttrData(player) ?: continue
                val sourceKey = "$AP_SOURCE_PREFIX$talentId"
                val value = talent.getValue(level)
                val attrs = hashMapOf<String, Array<Number>>(talent.attribute to arrayOf(value, value))

                AttributeAPI.takeSourceAttribute(data, sourceKey)
                AttributeAPI.addSourceAttribute(data, sourceKey, attrs)
            } catch (e: Exception) {
                warning("[RogueCore] 应用天赋 $talentId 失败: ${e.message}")
            }
        }

        runCatching { AttributeAPI.updateAttribute(player) }
    }

    fun removeTalents(player: Player) {
        if (!DependencySelfCheckManager.isAttributePlusAvailable()) {
            return
        }
        val talents = getPlayerTalents(player.uniqueId)

        for ((talentId, level) in talents) {
            if (level <= 0) continue
            val talent = TalentRegistry.get(talentId) ?: continue
            if (talent.effectType != TalentEffectType.ATTRIBUTE) continue

            try {
                val data = AttributeAPI.getAttrData(player) ?: continue
                AttributeAPI.takeSourceAttribute(data, "$AP_SOURCE_PREFIX$talentId")
            } catch (_: Exception) {
            }
        }

        runCatching { AttributeAPI.updateAttribute(player) }
    }

    fun getShardBonus(uuid: UUID): Double {
        var bonus = 1.0
        for ((talentId, level) in getPlayerTalents(uuid)) {
            val talent = TalentRegistry.get(talentId) ?: continue
            if (talent.effectType == TalentEffectType.SHARD_BONUS) {
                bonus += talent.getValue(level) / 100.0
            }
        }
        return bonus
    }

    fun getBoonLuckBonus(uuid: UUID): Double {
        var bonus = 0.0
        for ((talentId, level) in getPlayerTalents(uuid)) {
            val talent = TalentRegistry.get(talentId) ?: continue
            if (talent.effectType == TalentEffectType.BOON_LUCK) {
                bonus += talent.getValue(level)
            }
        }
        return bonus
    }

    fun preload(uuid: UUID) {
        DatabaseManager.preload(uuid)
    }

    fun unload(uuid: UUID) {
        DatabaseManager.release(uuid)
    }

    private fun DataContainer.getInt(key: String): Int {
        return this[key]?.toIntOrNull() ?: 0
    }
}
