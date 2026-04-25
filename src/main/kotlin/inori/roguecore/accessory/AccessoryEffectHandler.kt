package inori.roguecore.accessory

import org.bukkit.entity.Player
import kotlin.random.Random

object AccessoryEffectHandler {

    fun getSoulDebtInterestReduction(player: Player): Int = sum(player, AccessoryEffectType.SOUL_DEBT_INTEREST_REDUCTION).toInt().coerceAtLeast(0)

    fun getSoulDebtDeadlineBonus(player: Player): Int = sum(player, AccessoryEffectType.SOUL_DEBT_DEADLINE_BONUS).toInt().coerceAtLeast(0)

    fun rollSoulDebtPenaltyShield(player: Player): Boolean = rollPercent(player, AccessoryEffectType.SOUL_DEBT_PENALTY_SHIELD_CHANCE)

    fun getDelayedRewardBonusPercent(player: Player): Double = sum(player, AccessoryEffectType.DELAYED_REWARD_BONUS_PERCENT).coerceAtLeast(0.0)

    fun getDelayedRewardRoomReduction(player: Player): Int = sum(player, AccessoryEffectType.DELAYED_REWARD_ROOM_REDUCTION).toInt().coerceAtLeast(0)

    fun getDelayedRewardEarlyKeepPercent(player: Player): Double = sum(player, AccessoryEffectType.DELAYED_REWARD_EARLY_KEEP_PERCENT).coerceAtLeast(0.0)

    fun getProphecyRewardBonusPercent(player: Player): Double = sum(player, AccessoryEffectType.PROPHECY_REWARD_BONUS_PERCENT).coerceAtLeast(0.0)

    fun getProphecyMissReduction(player: Player): Int = sum(player, AccessoryEffectType.PROPHECY_MISS_REDUCTION).toInt().coerceAtLeast(0)

    fun getRouteChainToleranceBonus(player: Player): Int = sum(player, AccessoryEffectType.ROUTE_CHAIN_TOLERANCE_BONUS).toInt().coerceAtLeast(0)

    fun getRouteChainRewardBonus(player: Player): Int = sum(player, AccessoryEffectType.ROUTE_CHAIN_REWARD_BONUS).toInt().coerceAtLeast(0)

    fun getBoonOfferBonus(player: Player): Int = sum(player, AccessoryEffectType.BOON_OFFER_BONUS).toInt().coerceAtLeast(0)

    fun rollBoonEcho(player: Player): Boolean = rollPercent(player, AccessoryEffectType.BOON_ECHO_CHANCE)

    fun rollBoonMutation(player: Player): Boolean = rollPercent(player, AccessoryEffectType.BOON_MUTATION_CHANCE)

    fun getAccessoryDropChance(player: Player): Double = sum(player, AccessoryEffectType.ACCESSORY_DROP_CHANCE).coerceAtLeast(0.0)

    fun getAccessoryRarityLuck(player: Player): Double = sum(player, AccessoryEffectType.ACCESSORY_RARITY_LUCK).coerceAtLeast(0.0)

    fun getHiddenAccessoryBonus(player: Player): Double = sum(player, AccessoryEffectType.HIDDEN_ACCESSORY_BONUS).coerceAtLeast(0.0)

    private fun sum(player: Player, type: AccessoryEffectType): Double {
        return PlayerAccessoryData.getEffects(player, type).sumOf { it.value }
    }

    private fun rollPercent(player: Player, type: AccessoryEffectType): Boolean {
        val chance = sum(player, type).coerceIn(0.0, 100.0)
        return chance > 0.0 && Random.nextDouble() * 100.0 < chance
    }
}
