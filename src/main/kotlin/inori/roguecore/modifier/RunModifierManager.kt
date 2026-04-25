package inori.roguecore.modifier

import inori.roguecore.data.ShardRewardManager
import inori.roguecore.dungeon.DungeonInstance
import inori.roguecore.dungeon.DungeonManager
import inori.roguecore.dungeon.RunPersistenceManager
import inori.roguecore.dungeon.room.RoomType
import inori.roguecore.event.EventScaling
import inori.roguecore.stats.BalanceStatsManager
import inori.roguecore.summary.RunSummaryManager
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 本局临时修正管理器。
 */
object RunModifierManager {

    @Config("modifiers.yml")
    lateinit var config: Configuration
        private set

    private val modifiers = ConcurrentHashMap<UUID, MutableList<RunModifier>>()

    private var shopDebtEnabled = true
    private var shopDebtGrantBase = 55
    private var shopDebtGrantPowerScale = 5
    private var shopDebtPerRoom = 24
    private var shopDebtDurationRooms = 3

    private var sealedChestEnabled = true
    private var sealedChestRewardBase = 18
    private var sealedChestRewardPowerScale = 3
    private var sealedChestDurationRooms = 3
    private var sealedChestCharges = 1

    private var gambleStreakEnabled = true
    private var gambleBaseMultiplier = 1.5
    private var gambleHighPowerScale = 0.05
    private var gambleMaxMultiplier = 2.5

    private var shrineBlessingEnabled = true
    private var shrineBlessingDuration = 2
    private var shrineTwinDuration = 3
    private var shrineBlessingRewardBase = 8
    private var shrineTwinRewardBase = 10
    private var shrineRewardPowerScale = 2
    private var shrineTwinRelicScale = 1
    private var shrineHealPercent = 0.06

    private var forgeOverdriveEnabled = true
    private var forgeOverdrivePrice = 24
    private var forgeOverdriveDiscount = 0.25

    @Awake(LifeCycle.ENABLE)
    fun load() {
        shopDebtEnabled = config.getBoolean("shop-debt.enabled", true)
        shopDebtGrantBase = config.getInt("shop-debt.grant-base", 55).coerceAtLeast(0)
        shopDebtGrantPowerScale = config.getInt("shop-debt.grant-power-scale", 5).coerceAtLeast(0)
        shopDebtPerRoom = config.getInt("shop-debt.debt-per-room", 24).coerceAtLeast(0)
        shopDebtDurationRooms = config.getInt("shop-debt.duration-rooms", 3).coerceAtLeast(0)

        sealedChestEnabled = config.getBoolean("sealed-chest-pressure.enabled", true)
        sealedChestRewardBase = config.getInt("sealed-chest-pressure.reward-base", 18).coerceAtLeast(0)
        sealedChestRewardPowerScale = config.getInt("sealed-chest-pressure.reward-power-scale", 3).coerceAtLeast(0)
        sealedChestDurationRooms = config.getInt("sealed-chest-pressure.duration-rooms", 3).coerceAtLeast(0)
        sealedChestCharges = config.getInt("sealed-chest-pressure.charges", 1).coerceAtLeast(0)

        gambleStreakEnabled = config.getBoolean("gamble-streak.enabled", true)
        gambleBaseMultiplier = config.getDouble("gamble-streak.base-multiplier", 1.5).coerceAtLeast(1.0)
        gambleHighPowerScale = config.getDouble("gamble-streak.high-power-scale", 0.05).coerceAtLeast(0.0)
        gambleMaxMultiplier = config.getDouble("gamble-streak.max-multiplier", 2.5).coerceAtLeast(gambleBaseMultiplier)

        shrineBlessingEnabled = config.getBoolean("shrine-blessing.enabled", true)
        shrineBlessingDuration = config.getInt("shrine-blessing.blessing-duration", 2).coerceAtLeast(0)
        shrineTwinDuration = config.getInt("shrine-blessing.twin-duration", 3).coerceAtLeast(0)
        shrineBlessingRewardBase = config.getInt("shrine-blessing.blessing-reward-base", 8).coerceAtLeast(0)
        shrineTwinRewardBase = config.getInt("shrine-blessing.twin-reward-base", 10).coerceAtLeast(0)
        shrineRewardPowerScale = config.getInt("shrine-blessing.reward-power-scale", 2).coerceAtLeast(0)
        shrineTwinRelicScale = config.getInt("shrine-blessing.twin-relic-scale", 1).coerceAtLeast(0)
        shrineHealPercent = config.getDouble("shrine-blessing.heal-percent", 0.06).coerceIn(0.0, 1.0)

        forgeOverdriveEnabled = config.getBoolean("forge-overdrive.enabled", true)
        forgeOverdrivePrice = config.getInt("forge-overdrive.price", 24).coerceAtLeast(0)
        forgeOverdriveDiscount = config.getDouble("forge-overdrive.discount", 0.25).coerceIn(0.0, 0.75)
    }

    fun getModifiers(player: Player): List<RunModifier> = getModifiers(player.uniqueId)

    fun getModifiers(uuid: UUID): List<RunModifier> {
        return modifiers[uuid]?.map { it.copy() } ?: emptyList()
    }

    fun hasModifier(player: Player, type: RunModifierType): Boolean {
        return modifiers[player.uniqueId]?.any { it.type == type } == true
    }

    fun getValue(player: Player, type: RunModifierType): Double {
        return modifiers[player.uniqueId]
            ?.firstOrNull { it.type == type }
            ?.value
            ?: 0.0
    }

    fun isEnabled(type: RunModifierType): Boolean {
        return when (type) {
            RunModifierType.SHOP_DEBT -> shopDebtEnabled
            RunModifierType.SEALED_CHEST_PRESSURE -> sealedChestEnabled
            RunModifierType.GAMBLE_STREAK -> gambleStreakEnabled
            RunModifierType.SHRINE_BLESSING -> shrineBlessingEnabled
            RunModifierType.FORGE_OVERDRIVE -> forgeOverdriveEnabled
        }
    }

    fun shopDebtGrant(instance: DungeonInstance, power: Int): Int {
        return EventScaling.reward(instance, shopDebtGrantBase + power * shopDebtGrantPowerScale).coerceAtLeast(0)
    }

    fun shopDebtPerRoom(instance: DungeonInstance): Int {
        return EventScaling.reward(instance, shopDebtPerRoom).coerceAtLeast(0)
    }

    fun shopDebtDurationRooms(): Int = shopDebtDurationRooms

    fun sealedChestReward(instance: DungeonInstance, power: Int): Int {
        return EventScaling.reward(instance, sealedChestRewardBase + power * sealedChestRewardPowerScale).coerceAtLeast(0)
    }

    fun sealedChestDurationRooms(): Int = sealedChestDurationRooms

    fun sealedChestCharges(): Int = sealedChestCharges

    fun gambleStakeMultiplier(highPower: Int): Double {
        return (gambleBaseMultiplier + highPower * gambleHighPowerScale).coerceIn(1.0, gambleMaxMultiplier)
    }

    fun shrineBlessingDuration(twin: Boolean): Int {
        return if (twin) shrineTwinDuration else shrineBlessingDuration
    }

    fun shrineBlessingReward(instance: DungeonInstance, power: Int, twin: Boolean, relicPower: Int = 0): Int {
        val base = if (twin) shrineTwinRewardBase else shrineBlessingRewardBase
        val relicBonus = if (twin) relicPower * shrineTwinRelicScale else 0
        return EventScaling.reward(instance, base + power * shrineRewardPowerScale + relicBonus).coerceAtLeast(0)
    }

    fun shrineBlessingHealPercent(): Double = shrineHealPercent

    fun forgeOverdrivePrice(instance: DungeonInstance): Int {
        return EventScaling.price(instance, forgeOverdrivePrice).coerceAtLeast(0)
    }

    fun forgeOverdriveDiscount(): Double = forgeOverdriveDiscount

    fun addModifier(
        player: Player,
        type: RunModifierType,
        remainingRooms: Int,
        charges: Int,
        value: Double,
        source: String
    ) {
        if (!isEnabled(type)) return
        if (!DungeonManager.isInDungeon(player)) return
        val list = modifiers.getOrPut(player.uniqueId) { mutableListOf() }
        val existing = list.firstOrNull { it.type == type }
        if (existing != null) {
            existing.remainingRooms = maxOf(existing.remainingRooms, remainingRooms)
            existing.charges = maxOf(existing.charges, charges)
        } else {
            list += RunModifier(type, remainingRooms, charges, value, source)
        }
        RunPersistenceManager.markDirty()
        BalanceStatsManager.recordModifierApplied(type)
        RunSummaryManager.onModifierApplied(player.uniqueId, type)
        player.sendMessage("§b获得本局修正: §f${type.displayName} §7- ${type.description}")
    }

    fun onRoomCleared(player: Player, roomType: RoomType) {
        val list = modifiers[player.uniqueId] ?: return
        if (list.isEmpty()) return

        val remove = mutableListOf<RunModifier>()
        for (modifier in list.toList()) {
            when (modifier.type) {
                RunModifierType.SHOP_DEBT -> applyShopDebt(player, modifier)
                RunModifierType.SEALED_CHEST_PRESSURE -> {
                    if (roomType == RoomType.COMBAT || roomType == RoomType.ELITE || roomType == RoomType.BOSS) {
                        val reward = modifier.value.toInt().coerceAtLeast(1)
                        ShardRewardManager.addRunShards(player.uniqueId, reward)
                        player.sendMessage("§6封印宝箱回响兑现，额外获得 §e$reward §6本局碎片。")
                        modifier.charges -= 1
                    }
                }
                RunModifierType.SHRINE_BLESSING -> applyShrineBlessing(player, modifier)
                RunModifierType.GAMBLE_STREAK, RunModifierType.FORGE_OVERDRIVE -> Unit
            }

            if (modifier.type == RunModifierType.SHOP_DEBT || modifier.type == RunModifierType.SHRINE_BLESSING) {
                modifier.charges -= 1
            }
            if (modifier.remainingRooms > 0) {
                modifier.remainingRooms -= 1
            }
            val chargeOnlyDone = modifier.type == RunModifierType.SEALED_CHEST_PRESSURE && modifier.charges <= 0
            if (chargeOnlyDone || (modifier.remainingRooms == 0 && modifier.charges <= 0) || modifier.charges < 0) {
                remove += modifier
            }
        }
        if (remove.isNotEmpty()) {
            list.removeAll(remove.toSet())
            if (list.isEmpty()) modifiers.remove(player.uniqueId)
        }
        RunPersistenceManager.markDirty()
    }

    fun consumeGambleMultiplier(player: Player): Double {
        if (!gambleStreakEnabled) return 1.0
        val modifier = modifiers[player.uniqueId]?.firstOrNull { it.type == RunModifierType.GAMBLE_STREAK }
            ?: return 1.0
        val multiplier = modifier.value.coerceAtLeast(1.0)
        consume(player, modifier)
        return multiplier
    }

    fun getForgeDiscount(player: Player): Double {
        if (!forgeOverdriveEnabled) return 0.0
        return getValue(player, RunModifierType.FORGE_OVERDRIVE).coerceIn(0.0, 0.75)
    }

    fun consumeForgeOverdrive(player: Player) {
        val modifier = modifiers[player.uniqueId]?.firstOrNull { it.type == RunModifierType.FORGE_OVERDRIVE } ?: return
        consume(player, modifier)
    }

    fun clear(uuid: UUID) {
        if (modifiers.remove(uuid) != null) {
            RunPersistenceManager.markDirty()
        }
    }

    fun restore(uuid: UUID, values: List<RunModifier>) {
        if (values.isEmpty()) {
            modifiers.remove(uuid)
        } else {
            modifiers[uuid] = values.toMutableList()
        }
        RunPersistenceManager.markDirty()
    }

    private fun applyShopDebt(player: Player, modifier: RunModifier) {
        val debt = modifier.value.toInt().coerceAtLeast(1)
        val current = ShardRewardManager.getRunShards(player.uniqueId)
        val paid = debt.coerceAtMost(current)
        if (paid > 0) {
            ShardRewardManager.takeRunShards(player.uniqueId, paid)
            player.sendMessage("§c商店赊账偿还 §e$paid §c本局碎片。")
        } else {
            player.sendMessage("§7商店债务追来，但你没有可扣除的本局碎片。")
        }
    }

    private fun applyShrineBlessing(player: Player, modifier: RunModifier) {
        val shards = modifier.value.toInt().coerceAtLeast(1)
        ShardRewardManager.addRunShards(player.uniqueId, shards)
        val maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.value ?: 20.0
        val heal = maxHealth * shrineHealPercent
        player.health = (player.health + heal).coerceAtMost(maxHealth)
        player.sendMessage("§b神龛祝福延续，恢复生命并获得 §e$shards §b本局碎片。")
    }

    private fun consume(player: Player, modifier: RunModifier) {
        val list = modifiers[player.uniqueId] ?: return
        modifier.charges -= 1
        if (modifier.charges <= 0) {
            list.remove(modifier)
            if (list.isEmpty()) modifiers.remove(player.uniqueId)
        }
        RunPersistenceManager.markDirty()
    }
}
