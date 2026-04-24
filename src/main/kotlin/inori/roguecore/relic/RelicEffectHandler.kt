package inori.roguecore.relic

import inori.roguecore.data.ShardRewardManager
import inori.roguecore.dungeon.DungeonManager
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDeathEvent
import taboolib.common.platform.event.SubscribeEvent

object RelicEffectHandler {

    @SubscribeEvent(ignoreCancelled = true)
    fun onEntityDamage(event: EntityDamageByEntityEvent) {
        val attacker = resolvePlayerDamager(event.damager) ?: return
        if (!DungeonManager.isInDungeon(attacker)) {
            return
        }
        val target = event.entity as? LivingEntity ?: return
        if (target is Player) {
            return
        }

        for (relic in PlayerRelicData.getRelics(attacker)) {
            if (relic.effectType != RelicEffectType.LOW_HEALTH_DAMAGE) {
                continue
            }
            val maxHealth = attacker.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.value ?: 20.0
            if (maxHealth <= 0.0) {
                continue
            }
            val healthRate = attacker.health / maxHealth
            if (healthRate > relic.threshold / 100.0) {
                continue
            }
            event.damage *= (1.0 + relic.value / 100.0)
        }
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

        for (relic in PlayerRelicData.getRelics(killer)) {
            if (relic.effectType == RelicEffectType.KILL_SHARD) {
                ShardRewardManager.addRunShards(killer.uniqueId, relic.value.toInt().coerceAtLeast(1))
            }
        }
    }

    fun onRoomCleared(player: Player) {
        for (relic in PlayerRelicData.getRelics(player)) {
            if (relic.effectType != RelicEffectType.ROOM_HEAL) {
                continue
            }
            val maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.value ?: 20.0
            player.health = (player.health + maxHealth * relic.value / 100.0).coerceAtMost(maxHealth)
        }
    }

    fun getBonusLootChance(player: Player): Double {
        return PlayerRelicData.getRelics(player)
            .filter { it.effectType == RelicEffectType.BONUS_LOOT_CHANCE }
            .sumOf { it.value }
    }

    private fun resolvePlayerDamager(entity: Entity): Player? {
        return when (entity) {
            is Player -> entity
            is Projectile -> entity.shooter as? Player
            else -> null
        }
    }
}
