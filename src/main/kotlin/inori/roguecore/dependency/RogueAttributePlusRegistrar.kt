package inori.roguecore.dependency

import org.bukkit.Bukkit
import org.bukkit.entity.LivingEntity
import org.serverct.ersha.AttributePlus
import org.serverct.ersha.api.AttributeAPI
import org.serverct.ersha.api.component.SubAttribute
import org.serverct.ersha.attribute.AttributeHandle
import org.serverct.ersha.attribute.enums.AttributeType
import taboolib.common.platform.function.info
import taboolib.common.platform.function.warning
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

/**
 * RogueCore 自定义 AttributePlus 属性注册器。
 *
 * AttributePlus 的 attribute.yml 主要是已注册属性的配置承载，不负责凭空创建属性。
 * 这里通过 AP 的 registerAttribute(SubAttribute) 入口注册 RogueCore 赛季内容池使用的自定义属性。
 */
object RogueAttributePlusRegistrar {

    private val registered = ConcurrentHashMap.newKeySet<String>()

    private val specs = listOf(
        AttributeSpec("物理攻击", "rogue_physical_attack", AttributeKind.FLAT_ATTACK, 32, 1.0),
        AttributeSpec("暴击伤害", "rogue_crit_damage", AttributeKind.PERCENT_ATTACK, 33, 1.2),
        AttributeSpec("精英伤害", "rogue_elite_damage", AttributeKind.PERCENT_ATTACK, 34, 1.1),
        AttributeSpec("首领伤害", "rogue_boss_damage", AttributeKind.PERCENT_ATTACK, 35, 1.2),
        AttributeSpec("破甲", "rogue_armor_break", AttributeKind.PERCENT_ATTACK, 36, 0.9),
        AttributeSpec("雷霆伤害", "rogue_thunder_damage", AttributeKind.FLAT_ATTACK, 37, 1.1),
        AttributeSpec("技能伤害", "rogue_skill_damage", AttributeKind.PERCENT_ATTACK, 38, 1.0),
        AttributeSpec("元素伤害", "rogue_element_damage", AttributeKind.FLAT_ATTACK, 39, 1.0),
        AttributeSpec("冰霜伤害", "rogue_frost_damage", AttributeKind.FLAT_ATTACK, 40, 1.0),
        AttributeSpec("深渊伤害", "rogue_abyss_damage", AttributeKind.FLAT_ATTACK, 41, 1.15),
        AttributeSpec("圣辉伤害", "rogue_radiance_damage", AttributeKind.FLAT_ATTACK, 42, 1.1),
        AttributeSpec("生命偷取", "rogue_life_steal", AttributeKind.LIFESTEAL, 43, 0.8),
        AttributeSpec("减伤比例", "rogue_damage_reduce", AttributeKind.PERCENT_DEFENSE, 44, 1.0),
        AttributeSpec("护盾强度", "rogue_shield_power", AttributeKind.FLAT_DEFENSE, 45, 0.8),
        AttributeSpec("反伤比例", "rogue_thorns_ratio", AttributeKind.REFLECT, 46, 0.9),
        AttributeSpec("生命上限", "rogue_max_health", AttributeKind.OTHER, -1, 0.6),
        AttributeSpec("攻击速度", "rogue_attack_speed", AttributeKind.OTHER, -1, 0.5),
        AttributeSpec("移动速度", "rogue_move_speed", AttributeKind.OTHER, -1, 0.5),
        AttributeSpec("治疗加成", "rogue_heal_bonus", AttributeKind.OTHER, -1, 0.5),
        AttributeSpec("生命恢复", "rogue_restore", AttributeKind.OTHER, -1, 0.4),
        AttributeSpec("冷却缩减", "rogue_cooldown_reduce", AttributeKind.OTHER, -1, 0.4),
        AttributeSpec("幸运", "rogue_luck", AttributeKind.OTHER, -1, 0.4)
    )

    fun registerConfiguredAttributes(configuredNames: Set<String>): Int {
        // RogueCore 现在优先使用 AttributePlus 已有标签；AP 没有的扩展属性通过
        // plugins/AttributePlus/script/RogueCore 下的 JavaScript 脚本注册。
        // 这里保留方法作为旧调用入口，但不再用代码直接注册，避免和 AP 脚本/原生属性重复。
        return 0
    }

    private fun isRegisteredOrExists(name: String): Boolean {
        if (name in registered) {
            return true
        }
        return runCatching { AttributeAPI.getAttribute(name) != null }.getOrDefault(false)
    }

    private fun buildAttribute(spec: AttributeSpec): SubAttribute {
        return when (spec.kind) {
            AttributeKind.FLAT_ATTACK -> FlatAttackAttribute(spec)
            AttributeKind.PERCENT_ATTACK -> PercentAttackAttribute(spec)
            AttributeKind.LIFESTEAL -> LifestealAttribute(spec)
            AttributeKind.PERCENT_DEFENSE -> PercentDefenseAttribute(spec)
            AttributeKind.FLAT_DEFENSE -> FlatDefenseAttribute(spec)
            AttributeKind.REFLECT -> ReflectAttribute(spec)
            AttributeKind.OTHER -> OtherAttribute(spec)
        }
    }

    private data class AttributeSpec(
        val name: String,
        val placeholder: String,
        val kind: AttributeKind,
        val priority: Int,
        val combatPower: Double
    )

    private enum class AttributeKind {
        FLAT_ATTACK,
        PERCENT_ATTACK,
        LIFESTEAL,
        PERCENT_DEFENSE,
        FLAT_DEFENSE,
        REFLECT,
        OTHER
    }

    private abstract class RogueSubAttribute(
        private val spec: AttributeSpec,
        type: AttributeType
    ) : SubAttribute(spec.priority, spec.combatPower, spec.name, type, spec.placeholder) {

        override fun onLoad(): SubAttribute {
            when (spec.kind) {
                AttributeKind.FLAT_ATTACK -> listOf(
                    "§6${spec.name}! §f对 {entityB} 额外造成 {${spec.placeholder}} 点伤害。",
                    "§6${spec.name}! §f{entityA} 对你额外造成 {${spec.placeholder}} 点伤害。"
                ).setMessages()
                AttributeKind.PERCENT_ATTACK -> listOf(
                    "§6${spec.name}! §f对 {entityB} 追加 {${spec.placeholder}} 点伤害。",
                    "§6${spec.name}! §f{entityA} 对你追加 {${spec.placeholder}} 点伤害。"
                ).setMessages()
                AttributeKind.LIFESTEAL -> listOf(
                    "§c${spec.name}! §f吸取 {${spec.placeholder}} 点生命。",
                    "§c${spec.name}! §f{entityA} 吸取了你的生命。"
                ).setMessages()
                AttributeKind.PERCENT_DEFENSE, AttributeKind.FLAT_DEFENSE -> listOf(
                    "§b${spec.name}! §f对方抵消了 {${spec.placeholder}} 点伤害。",
                    "§b${spec.name}! §f你抵消了 {${spec.placeholder}} 点伤害。"
                ).setMessages()
                AttributeKind.REFLECT -> listOf(
                    "§c${spec.name}! §f对方反弹了 {${spec.placeholder}} 点伤害。",
                    "§c${spec.name}! §f你反弹了 {${spec.placeholder}} 点伤害。"
                ).setMessages()
                AttributeKind.OTHER -> Unit
            }
            return this
        }

        protected fun attrValue(entity: LivingEntity, handle: AttributeHandle): Double {
            return runCatching { entity.getRandomValue(handle).toDouble() }.getOrDefault(0.0)
        }

        protected fun currentDamage(entity: LivingEntity, handle: AttributeHandle): Double {
            return runCatching { entity.getDamage(handle).toDouble() }.getOrDefault(0.0)
        }
    }

    private class FlatAttackAttribute(spec: AttributeSpec) : RogueSubAttribute(spec, AttributeType.ATTACK) {
        override fun runAttack(attacker: LivingEntity, entity: LivingEntity, handle: AttributeHandle): Boolean {
            val value = attrValue(attacker, handle)
            if (value <= 0.0) return false
            attacker.addDamage(value, handle)
            storageValue(placeholder, value, handle)
            return true
        }
    }

    private class PercentAttackAttribute(spec: AttributeSpec) : RogueSubAttribute(spec, AttributeType.ATTACK) {
        override fun runAttack(attacker: LivingEntity, entity: LivingEntity, handle: AttributeHandle): Boolean {
            val value = attrValue(attacker, handle)
            if (value <= 0.0) return false
            val extra = currentDamage(attacker, handle) * value / 100.0
            if (extra <= 0.0) return false
            attacker.addDamage(extra, handle)
            storageValue(placeholder, extra, handle)
            return true
        }
    }

    private class LifestealAttribute(spec: AttributeSpec) : RogueSubAttribute(spec, AttributeType.ATTACK) {
        override fun runAttack(attacker: LivingEntity, entity: LivingEntity, handle: AttributeHandle): Boolean {
            val value = attrValue(attacker, handle)
            if (value <= 0.0) return false
            val heal = currentDamage(attacker, handle) * value / 100.0
            if (heal <= 0.0) return false
            val maxHealth = attacker.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH)?.value ?: 20.0
            attacker.health = (attacker.health + heal).coerceAtMost(maxHealth)
            storageValue(placeholder, heal, handle)
            return true
        }
    }

    private class PercentDefenseAttribute(spec: AttributeSpec) : RogueSubAttribute(spec, AttributeType.DEFENSE) {
        override fun runDefense(entity: LivingEntity, killer: LivingEntity, handle: AttributeHandle): Boolean {
            val value = attrValue(entity, handle).coerceAtMost(90.0)
            if (value <= 0.0) return false
            val reduced = currentDamage(entity, handle) * value / 100.0
            if (reduced <= 0.0) return false
            entity.takeDamage(reduced, handle)
            storageValue(placeholder, reduced, handle)
            return true
        }
    }

    private class FlatDefenseAttribute(spec: AttributeSpec) : RogueSubAttribute(spec, AttributeType.DEFENSE) {
        override fun runDefense(entity: LivingEntity, killer: LivingEntity, handle: AttributeHandle): Boolean {
            val reduced = attrValue(entity, handle)
            if (reduced <= 0.0) return false
            entity.takeDamage(reduced, handle)
            storageValue(placeholder, reduced, handle)
            return true
        }
    }

    private class ReflectAttribute(spec: AttributeSpec) : RogueSubAttribute(spec, AttributeType.DEFENSE) {
        override fun runDefense(entity: LivingEntity, killer: LivingEntity, handle: AttributeHandle): Boolean {
            val value = attrValue(entity, handle)
            if (value <= 0.0) return false
            val reflected = max(0.0, currentDamage(entity, handle) * value / 100.0)
            if (reflected <= 0.0) return false
            killer.addDamage(reflected, handle)
            storageValue(placeholder, reflected, handle)
            return true
        }
    }

    private class OtherAttribute(spec: AttributeSpec) : RogueSubAttribute(spec, AttributeType.OTHER)
}
