package inori.roguecore.boon

import inori.roguecore.data.ShardRewardManager
import inori.roguecore.dungeon.DungeonManager
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import taboolib.common.platform.event.SubscribeEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * Boon 触发型效果执行器。
 */
object BoonEffectHandler {

    private val cooldowns = ConcurrentHashMap<String, Long>()
    private val chainProcessing = ConcurrentHashMap.newKeySet<UUID>()

    @SubscribeEvent(ignoreCancelled = true)
    fun onEntityDamage(event: EntityDamageByEntityEvent) {
        val attacker = resolvePlayerDamager(event.damager)
        val target = event.entity as? LivingEntity

        if (attacker != null && target != null && target !is Player && DungeonManager.isInDungeon(attacker)) {
            handleAttackBoons(attacker, target, event)
        }

        val victim = event.entity as? Player ?: return
        if (!DungeonManager.isInDungeon(victim)) {
            return
        }

        val attackerEntity = event.damager as? LivingEntity ?: return
        handleDefenseBoons(victim, attackerEntity)
    }

    @SubscribeEvent
    fun onEntityDeath(event: EntityDeathEvent) {
        if (!event.entity.world.name.startsWith("rogue_")) {
            return
        }
        val killer = event.entity.killer ?: return
        if (!DungeonManager.isInDungeon(killer)) {
            return
        }
        for (instance in PlayerBoonData.getBoons(killer)) {
            for (effect in instance.boon.effects) {
                when (effect.type) {
                    BoonEffectType.KILL_HEAL -> {
                        val amount = effect.valueAt(instance.level) + effect.scaleAt(PlayerBoonData.getTagCount(killer.uniqueId, effect.tag))
                        heal(killer, amount)
                    }
                    BoonEffectType.KILL_SHARD -> {
                        val amount = effect.valueAt(instance.level) + effect.scaleAt(PlayerBoonData.getTagCount(killer.uniqueId, effect.tag))
                        ShardRewardManager.addRunShards(killer.uniqueId, amount.toInt().coerceAtLeast(1))
                    }
                    BoonEffectType.KILL_SPEED -> {
                        if (roll(effect) && isReady(killer.uniqueId, instance.boon.id, effect)) {
                            val bonus = effect.valueAt(instance.level) + effect.scaleAt(PlayerBoonData.getTagCount(killer.uniqueId, effect.tag))
                            applySpeed(killer, bonus, effect.durationSeconds)
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    fun onRoomCleared(player: Player) {
        if (!DungeonManager.isInDungeon(player)) {
            return
        }
        for (instance in PlayerBoonData.getBoons(player)) {
            for (effect in instance.boon.effects) {
                when (effect.type) {
                    BoonEffectType.ROOM_HEAL -> {
                        val percent = effect.valueAt(instance.level) + effect.scaleAt(PlayerBoonData.getTagCount(player.uniqueId, effect.tag))
                        val maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.value ?: 20.0
                        heal(player, maxHealth * percent / 100.0)
                    }
                    BoonEffectType.ROOM_SHARD -> {
                        val amount = effect.valueAt(instance.level) + effect.scaleAt(PlayerBoonData.getTagCount(player.uniqueId, effect.tag))
                        ShardRewardManager.addRunShards(player.uniqueId, amount.toInt().coerceAtLeast(1))
                    }
                    BoonEffectType.ROOM_UPGRADE_RANDOM -> {
                        if (roll(effect) && isReady(player.uniqueId, instance.boon.id, effect)) {
                            val upgradeable = PlayerBoonData.getBoons(player).filter { it.canUpgrade }.randomOrNull()
                            if (upgradeable != null) {
                                PlayerBoonData.addBoon(player, upgradeable.boon)
                                player.sendMessage("§d顿悟回响让一项神恩自行成长。")
                            }
                        }
                    }
                    else -> Unit
                }
            }
        }
        BoonResonanceManager.onRoomCleared(player)
    }

    private fun handleAttackBoons(player: Player, target: LivingEntity, event: EntityDamageByEntityEvent) {
        val maxHealth = target.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.value ?: return
        if (maxHealth <= 0.0) {
            return
        }
        val healthRate = target.health / maxHealth
        val skipChain = player.uniqueId in chainProcessing

        for (instance in PlayerBoonData.getBoons(player)) {
            for (effect in instance.boon.effects) {
                when (effect.type) {
                    BoonEffectType.EXECUTE -> {
                        val threshold = effect.threshold / 100.0
                        if (healthRate <= threshold) {
                            val bonus = effect.valueAt(instance.level) + effect.scaleAt(PlayerBoonData.getTagCount(player.uniqueId, effect.tag))
                            event.damage = event.damage * (1.0 + bonus / 100.0)
                        }
                    }
                    BoonEffectType.CHAIN_DAMAGE -> {
                        if (!skipChain && roll(effect) && isReady(player.uniqueId, instance.boon.id, effect)) {
                            val damage = effect.valueAt(instance.level) + effect.scaleAt(PlayerBoonData.getTagCount(player.uniqueId, effect.tag))
                            chainDamage(player, target, damage, effect.radius, effect.limit)
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun handleDefenseBoons(player: Player, attacker: LivingEntity) {
        val maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.value ?: 20.0
        val healthRate = if (maxHealth > 0.0) player.health / maxHealth else 1.0
        for (instance in PlayerBoonData.getBoons(player)) {
            for (effect in instance.boon.effects) {
                when (effect.type) {
                    BoonEffectType.RETALIATE -> {
                        val damage = effect.valueAt(instance.level) + effect.scaleAt(PlayerBoonData.getTagCount(player.uniqueId, effect.tag))
                        attacker.damage(damage, player)
                    }
                    BoonEffectType.LOW_HEALTH_SHIELD -> {
                        val threshold = (effect.threshold / 100.0).coerceIn(0.0, 1.0)
                        if (healthRate <= threshold && roll(effect) && isReady(player.uniqueId, instance.boon.id, effect)) {
                            val amount = effect.valueAt(instance.level) + effect.scaleAt(PlayerBoonData.getTagCount(player.uniqueId, effect.tag))
                            applyAbsorption(player, amount, effect.durationSeconds)
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun chainDamage(player: Player, target: LivingEntity, damage: Double, radius: Double, limit: Int) {
        val safeRadius = radius.coerceAtLeast(1.0)
        val safeLimit = limit.coerceAtLeast(1)
        val world = target.world
        val nearby = world.getNearbyEntities(target.location, safeRadius, safeRadius, safeRadius)
            .asSequence()
            .filterIsInstance<LivingEntity>()
            .filter { it != target && it !is Player && !it.isDead }
            .take(safeLimit)
            .toList()
        if (nearby.isEmpty()) {
            return
        }
        chainProcessing += player.uniqueId
        try {
            for (entity in nearby) {
                entity.damage(damage.coerceAtLeast(0.0), player)
                entity.world.spawnParticle(Particle.ELECTRIC_SPARK, entity.location.add(0.0, 1.0, 0.0), 10, 0.3, 0.3, 0.3, 0.02)
            }
        } finally {
            chainProcessing -= player.uniqueId
        }
        player.world.playSound(player.location, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 0.45f, 1.45f)
    }

    private fun applySpeed(player: Player, bonusPercent: Double, durationSeconds: Double) {
        val amplifier = (bonusPercent / 20.0).toInt().coerceIn(0, 4)
        val durationTicks = (durationSeconds.coerceAtLeast(1.0) * 20.0).toInt()
        player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, durationTicks, amplifier, true, true, true))
    }

    private fun applyAbsorption(player: Player, amount: Double, durationSeconds: Double) {
        val amplifier = (amount / 4.0).toInt().coerceIn(0, 9)
        val durationTicks = (durationSeconds.coerceAtLeast(4.0) * 20.0).toInt()
        player.addPotionEffect(PotionEffect(PotionEffectType.ABSORPTION, durationTicks, amplifier, true, true, true))
        player.world.playSound(player.location, Sound.ITEM_TOTEM_USE, 0.5f, 1.6f)
    }

    private fun roll(effect: BoonEffect): Boolean {
        return effect.chance >= 1.0 || Random.nextDouble() <= effect.chance
    }

    private fun isReady(uuid: UUID, boonId: String, effect: BoonEffect): Boolean {
        val cooldownMillis = (effect.cooldownSeconds * 1000.0).toLong()
        if (cooldownMillis <= 0L) {
            return true
        }
        val key = "$uuid:$boonId:${effect.type}:${effect.tag}"
        val now = System.currentTimeMillis()
        val last = cooldowns[key] ?: 0L
        if (now - last < cooldownMillis) {
            return false
        }
        cooldowns[key] = now
        return true
    }

    private fun resolvePlayerDamager(entity: Entity): Player? {
        return when (entity) {
            is Player -> entity
            is Projectile -> entity.shooter as? Player
            else -> null
        }
    }

    private fun heal(player: Player, amount: Double) {
        if (amount <= 0.0) {
            return
        }
        val maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.value ?: 20.0
        player.health = (player.health + amount).coerceAtMost(maxHealth)
    }
}
