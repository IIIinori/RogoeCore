package inori.roguecore.boon

import inori.roguecore.data.ShardRewardManager
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * 神恩流派共鸣。
 *
 * 共鸣只看标签数量，给玩家一个“凑流派”的长期局内目标。
 * 3/5/7 个同标签分别触发 I/II/III 阶共鸣。
 */
object BoonResonanceManager {

    private val resonanceTags = listOf("狩猎", "壁垒", "鲜血", "风暴", "霜寒", "深渊", "圣辉", "财宝")
    private val chainProcessing = ConcurrentHashMap.newKeySet<UUID>()

    fun getLevel(player: Player, tag: String): Int {
        return getLevel(player.uniqueId, tag)
    }

    fun getLevel(uuid: UUID, tag: String): Int {
        val count = PlayerBoonData.getTagCount(uuid, tag)
        return when {
            count >= 7 -> 3
            count >= 5 -> 2
            count >= 3 -> 1
            else -> 0
        }
    }

    fun getActiveResonanceLines(player: Player): List<String> {
        return resonanceTags.mapNotNull { tag ->
            val count = PlayerBoonData.getTagCount(player.uniqueId, tag)
            val level = getLevel(player.uniqueId, tag)
            if (level <= 0) {
                null
            } else {
                "§7${tag}共鸣 ${roman(level)} §8($count) §f- ${shortDescription(tag, level)}"
            }
        }
    }

    fun onPlayerDamageMob(player: Player, target: LivingEntity, event: EntityDamageByEntityEvent) {
        val hunt = getLevel(player, "狩猎")
        val abyss = getLevel(player, "深渊")
        val storm = getLevel(player, "风暴")
        var multiplier = 1.0

        if (hunt > 0) {
            multiplier += hunt * 0.04
        }
        if (abyss > 0 && isTargetWounded(target)) {
            multiplier += abyss * 0.05
        }
        if (multiplier != 1.0) {
            event.damage *= multiplier
        }

        if (storm > 0 && player.uniqueId !in chainProcessing && Random.nextDouble() < 0.08 * storm) {
            chainProcessing += player.uniqueId
            try {
                chainDamage(player, target, 3.0 + storm * 3.0, 3.5 + storm, 1 + storm)
            } finally {
                chainProcessing -= player.uniqueId
            }
        }
    }

    fun onMobKilled(player: Player) {
        val hunt = getLevel(player, "狩猎")
        if (hunt > 0) {
            applySpeed(player, 5.0 + hunt * 5.0, 3.0 + hunt)
        }

        val blood = getLevel(player, "鲜血")
        if (blood > 0) {
            heal(player, 1.5 + blood * 1.5)
        }

        val treasure = getLevel(player, "财宝")
        if (treasure > 0) {
            ShardRewardManager.addRunShards(player.uniqueId, treasure)
        }
    }

    fun onRoomCleared(player: Player) {
        val bulwark = getLevel(player, "壁垒")
        if (bulwark > 0) {
            applyAbsorption(player, 3.0 + bulwark * 3.0, 12.0)
        }

        val frost = getLevel(player, "霜寒")
        if (frost > 0) {
            applyAbsorption(player, 2.0 + frost * 2.0, 8.0)
        }

        val radiance = getLevel(player, "圣辉")
        if (radiance > 0) {
            val maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.value ?: 20.0
            heal(player, maxHealth * (0.02 + radiance * 0.02))
        }

        val treasure = getLevel(player, "财宝")
        if (treasure > 0) {
            ShardRewardManager.addRunShards(player.uniqueId, 2 + treasure * 2)
        }

        val abyss = getLevel(player, "深渊")
        if (abyss > 0 && Random.nextDouble() < 0.04 * abyss) {
            val upgradeable = PlayerBoonData.getBoons(player).filter { it.canUpgrade }.randomOrNull()
            if (upgradeable != null) {
                PlayerBoonData.addBoon(player, upgradeable.boon)
                player.sendMessage("§5深渊共鸣吞噬余响，强化了一项神恩。")
            }
        }
    }

    private fun chainDamage(player: Player, target: LivingEntity, damage: Double, radius: Double, limit: Int) {
        val nearby = target.world.getNearbyEntities(target.location, radius, radius, radius)
            .asSequence()
            .filterIsInstance<LivingEntity>()
            .filter { it != target && it !is Player && !it.isDead }
            .take(limit.coerceAtLeast(1))
            .toList()
        if (nearby.isEmpty()) {
            return
        }
        for (entity in nearby) {
            entity.damage(damage.coerceAtLeast(0.0), player)
            entity.world.spawnParticle(Particle.ELECTRIC_SPARK, entity.location.clone().add(0.0, 1.0, 0.0), 8, 0.25, 0.25, 0.25, 0.02)
        }
        player.world.playSound(player.location, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 0.35f, 1.75f)
    }

    private fun isTargetWounded(target: LivingEntity): Boolean {
        val maxHealth = target.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.value ?: return false
        return maxHealth > 0.0 && target.health / maxHealth <= 0.5
    }

    private fun applySpeed(player: Player, bonusPercent: Double, durationSeconds: Double) {
        val amplifier = (bonusPercent / 20.0).toInt().coerceIn(0, 4)
        player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, (durationSeconds * 20.0).toInt(), amplifier, true, true, true))
    }

    private fun applyAbsorption(player: Player, amount: Double, durationSeconds: Double) {
        val amplifier = (amount / 4.0).toInt().coerceIn(0, 9)
        player.addPotionEffect(PotionEffect(PotionEffectType.ABSORPTION, (durationSeconds * 20.0).toInt(), amplifier, true, true, true))
    }

    private fun heal(player: Player, amount: Double) {
        val maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.value ?: 20.0
        player.health = (player.health + amount).coerceAtMost(maxHealth)
    }

    private fun shortDescription(tag: String, level: Int): String {
        return when (tag) {
            "狩猎" -> "伤害 +${level * 4}%，击杀加速"
            "壁垒" -> "清房获得护盾"
            "鲜血" -> "击杀回血"
            "风暴" -> "攻击概率连锁"
            "霜寒" -> "清房获得冰盾"
            "深渊" -> "伤害压制残血，概率顿悟"
            "圣辉" -> "清房治疗"
            "财宝" -> "击杀与清房额外碎片"
            else -> "标签联动强化"
        }
    }

    private fun roman(level: Int): String {
        return when (level) {
            1 -> "I"
            2 -> "II"
            else -> "III"
        }
    }
}
