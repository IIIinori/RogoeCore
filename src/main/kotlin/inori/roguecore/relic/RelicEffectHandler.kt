package inori.roguecore.relic

import inori.roguecore.boon.PlayerBoonData
import inori.roguecore.data.ShardRewardManager
import inori.roguecore.dungeon.DungeonManager
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
            when (relic.effectType) {
                RelicEffectType.ROOM_HEAL -> {
                    val maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.value ?: 20.0
                    player.health = (player.health + maxHealth * relic.value / 100.0).coerceAtMost(maxHealth)
                }
                RelicEffectType.ROOM_UPGRADE_CHANCE -> {
                    if (kotlin.random.Random.nextDouble() * 100.0 < relic.value) {
                        val upgradeable = PlayerBoonData.getBoons(player).filter { it.canUpgrade }.randomOrNull()
                        if (upgradeable != null) {
                            PlayerBoonData.addBoon(player, upgradeable.boon)
                            player.sendMessage("§d${relic.name} 让一项神恩获得了成长。")
                        }
                    }
                }
                else -> Unit
            }
        }
    }

    fun onCombatStart(player: Player) {
        val shield = PlayerRelicData.getRelics(player)
            .filter { it.effectType == RelicEffectType.COMBAT_START_SHIELD }
            .sumOf { it.value }
        if (shield <= 0.0) {
            return
        }
        val amplifier = (shield / 4.0).toInt().coerceIn(0, 9)
        player.addPotionEffect(PotionEffect(PotionEffectType.ABSORPTION, 20 * 18, amplifier, true, true, true))
    }

    fun getBonusLootChance(player: Player): Double {
        return PlayerRelicData.getRelics(player)
            .filter { it.effectType == RelicEffectType.BONUS_LOOT_CHANCE }
            .sumOf { it.value }
    }

    fun getShopDiscountPercent(player: Player): Double {
        return PlayerRelicData.getRelics(player)
            .filter { it.effectType == RelicEffectType.SHOP_DISCOUNT }
            .sumOf { it.value }
    }

    fun getBossEmberBonus(player: Player): Int {
        return PlayerRelicData.getRelics(player)
            .filter { it.effectType == RelicEffectType.BOSS_EMBER_BONUS }
            .sumOf { it.value.toInt().coerceAtLeast(0) }
    }

    fun getHiddenKeyChance(player: Player): Double {
        return PlayerRelicData.getRelics(player)
            .filter { it.effectType == RelicEffectType.HIDDEN_KEY_CHANCE }
            .sumOf { it.value }
    }

    fun getEliteLootChance(player: Player): Double {
        return PlayerRelicData.getRelics(player)
            .filter { it.effectType == RelicEffectType.ELITE_LOOT_CHANCE }
            .sumOf { it.value }
    }

    fun getShopHealDiscountPercent(player: Player): Double {
        return PlayerRelicData.getRelics(player)
            .filter { it.effectType == RelicEffectType.SHOP_HEAL_DISCOUNT }
            .sumOf { it.value }
    }

    fun getShopMaterialBonus(player: Player): Int {
        return PlayerRelicData.getRelics(player)
            .filter { it.effectType == RelicEffectType.SHOP_MATERIAL_BONUS }
            .sumOf { it.value.toInt().coerceAtLeast(0) }
    }

    fun getShrineRelicBoost(player: Player): Int {
        return PlayerRelicData.getRelics(player)
            .filter { it.effectType == RelicEffectType.SHRINE_RELIC_BOOST }
            .sumOf { it.value.toInt().coerceAtLeast(0) }
    }

    fun getGambleInsurancePercent(player: Player): Double {
        return PlayerRelicData.getRelics(player)
            .filter { it.effectType == RelicEffectType.GAMBLE_INSURANCE }
            .sumOf { it.value }
    }

    fun getTrialForgeBonus(player: Player): Int {
        return PlayerRelicData.getRelics(player)
            .filter { it.effectType == RelicEffectType.TRIAL_FORGE_BONUS }
            .sumOf { it.value.toInt().coerceAtLeast(0) }
    }

    fun getChestMaterialBonus(player: Player): Int {
        return PlayerRelicData.getRelics(player)
            .filter { it.effectType == RelicEffectType.CHEST_MATERIAL_BONUS }
            .sumOf { it.value.toInt().coerceAtLeast(0) }
    }

    fun getHiddenLootBonus(player: Player): Int {
        return PlayerRelicData.getRelics(player)
            .filter { it.effectType == RelicEffectType.HIDDEN_LOOT_BONUS }
            .sumOf { it.value.toInt().coerceAtLeast(0) }
    }

    fun getBoonOfferBonus(player: Player): Int {
        return PlayerRelicData.getRelics(player)
            .filter { it.effectType == RelicEffectType.BOON_OFFER_BONUS }
            .sumOf { it.value.toInt().coerceAtLeast(0) }
    }

    fun getBoonRarityLuckBonus(player: Player): Double {
        return PlayerRelicData.getRelics(player)
            .filter { it.effectType == RelicEffectType.BOON_RARITY_LUCK }
            .sumOf { it.value }
    }

    fun getBossLootChance(player: Player): Double {
        return PlayerRelicData.getRelics(player)
            .filter { it.effectType == RelicEffectType.BOSS_LOOT_CHANCE }
            .sumOf { it.value }
    }

    fun getHiddenRelicBoost(player: Player): Int {
        return PlayerRelicData.getRelics(player)
            .filter { it.effectType == RelicEffectType.HIDDEN_RELIC_BOOST }
            .sumOf { it.value.toInt().coerceAtLeast(0) }
    }

    fun getEliteRelicChance(player: Player): Double {
        return PlayerRelicData.getRelics(player)
            .filter { it.effectType == RelicEffectType.ELITE_RELIC_CHANCE }
            .sumOf { it.value }
    }

    fun getBlackMarketDiscountPercent(player: Player): Double {
        return PlayerRelicData.getRelics(player)
            .filter { it.effectType == RelicEffectType.BLACK_MARKET_DISCOUNT }
            .sumOf { it.value }
    }

    fun getChestExtraLootChance(player: Player): Double {
        return PlayerRelicData.getRelics(player)
            .filter { it.effectType == RelicEffectType.CHEST_EXTRA_LOOT_CHANCE }
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
