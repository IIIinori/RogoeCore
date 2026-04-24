package inori.roguecore.boon

import inori.roguecore.dungeon.DungeonManager
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDeathEvent
import taboolib.common.platform.event.SubscribeEvent

/**
 * Boon 触发型效果执行器。
 */
object BoonEffectHandler {

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
                if (effect.type != BoonEffectType.KILL_HEAL) {
                    continue
                }
                val amount = effect.valueAt(instance.level) + effect.scaleAt(PlayerBoonData.getTagCount(killer.uniqueId, effect.tag))
                heal(killer, amount)
            }
        }
    }

    fun onRoomCleared(player: Player) {
        if (!DungeonManager.isInDungeon(player)) {
            return
        }
        for (instance in PlayerBoonData.getBoons(player)) {
            for (effect in instance.boon.effects) {
                if (effect.type != BoonEffectType.ROOM_HEAL) {
                    continue
                }
                val percent = effect.valueAt(instance.level) + effect.scaleAt(PlayerBoonData.getTagCount(player.uniqueId, effect.tag))
                val maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.value ?: 20.0
                heal(player, maxHealth * percent / 100.0)
            }
        }
    }

    private fun handleAttackBoons(player: Player, target: LivingEntity, event: EntityDamageByEntityEvent) {
        val maxHealth = target.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.value ?: return
        if (maxHealth <= 0.0) {
            return
        }
        val healthRate = target.health / maxHealth

        for (instance in PlayerBoonData.getBoons(player)) {
            for (effect in instance.boon.effects) {
                if (effect.type != BoonEffectType.EXECUTE) {
                    continue
                }
                val threshold = effect.threshold / 100.0
                if (healthRate > threshold) {
                    continue
                }
                val bonus = effect.valueAt(instance.level) + effect.scaleAt(PlayerBoonData.getTagCount(player.uniqueId, effect.tag))
                event.damage = event.damage * (1.0 + bonus / 100.0)
            }
        }
    }

    private fun handleDefenseBoons(player: Player, attacker: LivingEntity) {
        for (instance in PlayerBoonData.getBoons(player)) {
            for (effect in instance.boon.effects) {
                if (effect.type != BoonEffectType.RETALIATE) {
                    continue
                }
                val damage = effect.valueAt(instance.level) + effect.scaleAt(PlayerBoonData.getTagCount(player.uniqueId, effect.tag))
                attacker.damage(damage, player)
            }
        }
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
