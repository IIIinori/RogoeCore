package inori.roguecore.relic

import inori.roguecore.boon.PlayerBoonData
import inori.roguecore.data.ShardRewardManager
import inori.roguecore.dungeon.DungeonManager
import inori.roguecore.dungeon.room.RoomType
import inori.roguecore.modifier.RunModifierManager
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
                RelicEffectType.RELIC_CHARGE_ON_ROOM -> {
                    RunModifierManager.chargeRelicRule(
                        player,
                        relic.id,
                        relic.name,
                        relic.value.toInt().coerceAtLeast(1),
                        "boon_echo",
                        1
                    )
                }
                RelicEffectType.RELIC_SPEND_CHARGE_FOR_REWARD -> {
                    val max = relic.threshold.toInt().coerceAtLeast(3)
                    RunModifierManager.chargeRelicRule(player, relic.id, relic.name, max, "shards", relic.value.toInt().coerceAtLeast(1))
                }
                RelicEffectType.RELIC_PAY_DEBT_WITH_CHARGE -> {
                    val max = relic.threshold.toInt().coerceAtLeast(2)
                    RunModifierManager.chargeRelicRule(player, relic.id, relic.name, max, "charge_only", 1)
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

    fun onEventRoomEntered(player: Player, roomType: RoomType) {
        if (roomType.isCombatRoomLike()) return
        for (relic in PlayerRelicData.getRelics(player)) {
            if (relic.effectType != RelicEffectType.RELIC_CHARGE_ON_EVENT) continue
            RunModifierManager.chargeRelicRule(
                player,
                relic.id,
                relic.name,
                relic.value.toInt().coerceAtLeast(1),
                "boon_mutation",
                1
            )
        }
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

    fun getShopFirstDiscountPercent(player: Player): Double = sumValue(player, RelicEffectType.SHOP_FIRST_DISCOUNT)

    fun getGambleFailProtectionPercent(player: Player): Double = sumValue(player, RelicEffectType.GAMBLE_FAIL_PROTECTION)

    fun getSealedChestBonus(player: Player): Int = intValue(player, RelicEffectType.SEALED_CHEST_BONUS)

    fun getShrinePurifyReward(player: Player): Int = intValue(player, RelicEffectType.SHRINE_PURIFY_REWARD)

    fun getTrialRefundMaterial(player: Player): Int = intValue(player, RelicEffectType.TRIAL_REFUND_MATERIAL)

    fun getRouteStreakReward(player: Player): Int = intValue(player, RelicEffectType.ROUTE_STREAK_REWARD)

    fun getForgeAccelerationDiscountPercent(player: Player): Double = sumValue(player, RelicEffectType.FORGE_ACCELERATION_DISCOUNT)

    fun getIdentifyAccelerationDiscountPercent(player: Player): Double = sumValue(player, RelicEffectType.IDENTIFY_ACCELERATION_DISCOUNT)

    fun getRoomClearShardBonus(player: Player): Int = intValue(player, RelicEffectType.ROOM_CLEAR_SHARD_BONUS)

    fun getEliteShardBonus(player: Player): Int = intValue(player, RelicEffectType.ELITE_SHARD_BONUS)

    fun getBossShardBonus(player: Player): Int = intValue(player, RelicEffectType.BOSS_SHARD_BONUS)

    fun getHiddenShardBonus(player: Player): Int = intValue(player, RelicEffectType.HIDDEN_SHARD_BONUS)

    fun getChestShardBonus(player: Player): Int = intValue(player, RelicEffectType.CHEST_SHARD_BONUS)

    fun getLowHealthCashoutBonus(player: Player): Double = sumValue(player, RelicEffectType.LOW_HEALTH_CASHOUT_BONUS)

    fun getMaterialWorkshopDiscount(player: Player): Double = sumValue(player, RelicEffectType.MATERIAL_WORKSHOP_DISCOUNT)

    fun getSalvageMaterialBonus(player: Player): Int = intValue(player, RelicEffectType.SALVAGE_MATERIAL_BONUS)

    private fun sumValue(player: Player, type: RelicEffectType): Double {
        return PlayerRelicData.getRelics(player).filter { it.effectType == type }.sumOf { it.value }
    }

    private fun intValue(player: Player, type: RelicEffectType): Int {
        return PlayerRelicData.getRelics(player).filter { it.effectType == type }.sumOf { it.value.toInt().coerceAtLeast(0) }
    }

    private fun RoomType.isCombatRoomLike(): Boolean {
        return this == RoomType.COMBAT || this == RoomType.ELITE || this == RoomType.BOSS
    }

    private fun resolvePlayerDamager(entity: Entity): Player? {
        return when (entity) {
            is Player -> entity
            is Projectile -> entity.shooter as? Player
            else -> null
        }
    }
}
