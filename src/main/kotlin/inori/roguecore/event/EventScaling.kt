package inori.roguecore.event

import inori.roguecore.dungeon.DungeonInstance
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration
import kotlin.math.roundToInt

/**
 * 事件房楼层成长规则。
 *
 * 只缩放事件奖励、代价与候选数量，不影响战斗房基础结算。
 */
object EventScaling {

    @Config("events.yml")
    lateinit var config: Configuration
        private set

    fun reward(instance: DungeonInstance, amount: Int): Int {
        return (amount * rewardMultiplier(instance)).roundToInt().coerceAtLeast(0)
    }

    fun price(instance: DungeonInstance, amount: Int): Int {
        return (amount * priceMultiplier(instance)).roundToInt().coerceAtLeast(0)
    }

    fun riskPercent(instance: DungeonInstance, percent: Double): Double {
        val max = config.getDouble("event-scaling.max-risk-percent", 0.75).coerceIn(0.0, 1.0)
        return (percent * riskMultiplier(instance)).coerceIn(0.0, max)
    }

    fun boonOfferCount(instance: DungeonInstance, base: Int = 3): Int {
        return (base + extraOfferCount(instance)).coerceIn(1, 4)
    }

    fun relicOfferCount(instance: DungeonInstance, base: Int = 3, bonus: Int = 0): Int {
        return (base + bonus + extraOfferCount(instance)).coerceIn(1, 4)
    }

    fun materialBonus(instance: DungeonInstance): Int {
        val every = config.getInt("event-scaling.material-bonus-every-floors", 6).coerceAtLeast(1)
        val max = config.getInt("event-scaling.max-material-bonus", 2).coerceAtLeast(0)
        return ((instance.config.floorNumber - 1) / every).coerceIn(0, max)
    }

    private fun rewardMultiplier(instance: DungeonInstance): Double {
        return floorMultiplier(
            instance,
            config.getDouble("event-scaling.reward-per-floor", 0.08),
            config.getDouble("event-scaling.max-reward-multiplier", 2.2)
        )
    }

    private fun priceMultiplier(instance: DungeonInstance): Double {
        return floorMultiplier(
            instance,
            config.getDouble("event-scaling.price-per-floor", 0.05),
            config.getDouble("event-scaling.max-price-multiplier", 1.8)
        )
    }

    private fun riskMultiplier(instance: DungeonInstance): Double {
        return floorMultiplier(
            instance,
            config.getDouble("event-scaling.risk-per-floor", 0.03),
            config.getDouble("event-scaling.max-risk-multiplier", 1.45)
        )
    }

    private fun extraOfferCount(instance: DungeonInstance): Int {
        val every = config.getInt("event-scaling.extra-offer-every-floors", 5).coerceAtLeast(1)
        val max = config.getInt("event-scaling.max-extra-offers", 1).coerceAtLeast(0)
        return ((instance.config.floorNumber - 1) / every).coerceIn(0, max)
    }

    private fun floorMultiplier(instance: DungeonInstance, perFloor: Double, max: Double): Double {
        val floorIndex = (instance.config.floorNumber - 1).coerceAtLeast(0)
        return (1.0 + floorIndex * perFloor.coerceAtLeast(0.0)).coerceAtMost(max.coerceAtLeast(1.0))
    }
}
