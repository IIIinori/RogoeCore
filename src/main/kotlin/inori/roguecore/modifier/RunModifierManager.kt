package inori.roguecore.modifier

import inori.roguecore.accessory.AccessoryEffectHandler
import inori.roguecore.affix.AffixManager
import inori.roguecore.boon.BoonSelectManager
import inori.roguecore.curse.RunCurseManager
import inori.roguecore.curse.RunCurseType
import inori.roguecore.data.ForgeMaterialManager
import inori.roguecore.data.ForgeMaterialType
import inori.roguecore.data.ShardRewardManager
import inori.roguecore.dungeon.DungeonInstance
import inori.roguecore.dungeon.DungeonManager
import inori.roguecore.dungeon.RunPersistenceManager
import inori.roguecore.dungeon.room.RoomType
import inori.roguecore.event.EventScaling
import inori.roguecore.item.DungeonLootManager
import inori.roguecore.relic.PlayerRelicData
import inori.roguecore.relic.RelicEffectType
import inori.roguecore.relic.RelicSelectManager
import inori.roguecore.display.ContentDisplayNameResolver
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
import kotlin.random.Random

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

    private var soulDebtEnabled = true
    private var soulDebtGrantBase = 72
    private var soulDebtGrantPowerScale = 8
    private var soulDebtPrincipalMultiplier = 1.25
    private var soulDebtInterestPerRoom = 8
    private var soulDebtDeadlineRooms = 3
    private var soulDebtPenalty = "VULNERABLE"

    private var delayedRewardEnabled = true
    private var delayedRewardRooms = 3
    private var delayedRewardShardMultiplier = 1.8
    private var delayedRewardFallbackRatio = 0.5

    private var prophecyEnabled = true
    private var prophecyWithinRooms = 4
    private var prophecyRewardShards = 36
    private var prophecyRoomWeightBonus = 25
    private var prophecyMissPenalty = 14

    private var routeChainEnabled = true
    private var routeChainTolerance = 1
    private var routeChainRoomWeightBonus = 18

    private var boonEchoEnabled = true
    private var boonMutationEnabled = true
    private var relicChargeEnabled = true
    private var sealedFutureEnabled = true

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

        soulDebtEnabled = config.getBoolean("soul-debt.enabled", true)
        soulDebtGrantBase = config.getInt("soul-debt.grant-base", 72).coerceAtLeast(0)
        soulDebtGrantPowerScale = config.getInt("soul-debt.grant-power-scale", 8).coerceAtLeast(0)
        soulDebtPrincipalMultiplier = config.getDouble("soul-debt.principal-multiplier", 1.25).coerceAtLeast(1.0)
        soulDebtInterestPerRoom = config.getInt("soul-debt.interest-per-room", 8).coerceAtLeast(0)
        soulDebtDeadlineRooms = config.getInt("soul-debt.deadline-rooms", 3).coerceAtLeast(1)
        soulDebtPenalty = config.getString("soul-debt.default-penalty") ?: "VULNERABLE"

        delayedRewardEnabled = config.getBoolean("delayed-reward.enabled", true)
        delayedRewardRooms = config.getInt("delayed-reward.default-rooms", 3).coerceAtLeast(1)
        delayedRewardShardMultiplier = config.getDouble("delayed-reward.shard-multiplier", 1.8).coerceAtLeast(1.0)
        delayedRewardFallbackRatio = config.getDouble("delayed-reward.early-fallback-ratio", 0.5).coerceIn(0.0, 1.0)

        prophecyEnabled = config.getBoolean("room-prophecy.enabled", true)
        prophecyWithinRooms = config.getInt("room-prophecy.default-within-rooms", 4).coerceAtLeast(1)
        prophecyRewardShards = config.getInt("room-prophecy.reward-shards", 36).coerceAtLeast(0)
        prophecyRoomWeightBonus = config.getInt("room-prophecy.room-weight-bonus", 25).coerceAtLeast(0)
        prophecyMissPenalty = config.getInt("room-prophecy.miss-penalty-shards", 14).coerceAtLeast(0)

        routeChainEnabled = config.getBoolean("route-chain.enabled", true)
        routeChainTolerance = config.getInt("route-chain.default-tolerance", 1).coerceAtLeast(0)
        routeChainRoomWeightBonus = config.getInt("route-chain.room-weight-bonus", 18).coerceAtLeast(0)

        boonEchoEnabled = config.getBoolean("boon-echo.enabled", true)
        boonMutationEnabled = config.getBoolean("boon-mutation.enabled", true)
        relicChargeEnabled = config.getBoolean("relic-charge.enabled", true)
        sealedFutureEnabled = config.getBoolean("sealed-future.enabled", true)
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
            RunModifierType.SOUL_DEBT -> soulDebtEnabled
            RunModifierType.DELAYED_REWARD -> delayedRewardEnabled
            RunModifierType.ROOM_PROPHECY -> prophecyEnabled
            RunModifierType.ROUTE_CHAIN -> routeChainEnabled
            RunModifierType.BOON_ECHO -> boonEchoEnabled
            RunModifierType.BOON_MUTATION -> boonMutationEnabled
            RunModifierType.RELIC_CHARGE_RULE -> relicChargeEnabled
            RunModifierType.SEALED_FUTURE -> sealedFutureEnabled
        }
    }

    fun shopDebtGrant(instance: DungeonInstance, power: Int): Int {
        return EventScaling.reward(instance, shopDebtGrantBase + power * shopDebtGrantPowerScale).coerceAtLeast(0)
    }

    fun shopDebtPerRoom(instance: DungeonInstance): Int {
        return EventScaling.reward(instance, shopDebtPerRoom).coerceAtLeast(0)
    }

    fun shopDebtDurationRooms(): Int = shopDebtDurationRooms

    fun soulDebtGrant(instance: DungeonInstance, power: Int): Int {
        return EventScaling.reward(instance, soulDebtGrantBase + power * soulDebtGrantPowerScale).coerceAtLeast(0)
    }

    fun soulDebtPrincipal(grant: Int): Int {
        return (grant * soulDebtPrincipalMultiplier).toInt().coerceAtLeast(grant)
    }

    fun soulDebtInterest(instance: DungeonInstance, power: Int = 0): Int {
        return EventScaling.reward(instance, soulDebtInterestPerRoom + power).coerceAtLeast(0)
    }

    fun soulDebtDeadlineRooms(): Int = soulDebtDeadlineRooms

    fun delayedRewardRooms(): Int = delayedRewardRooms

    fun delayedShardReward(base: Int): Int = (base * delayedRewardShardMultiplier).toInt().coerceAtLeast(base)

    fun prophecyWithinRooms(): Int = prophecyWithinRooms

    fun prophecyReward(instance: DungeonInstance, power: Int = 0): Int {
        return EventScaling.reward(instance, prophecyRewardShards + power * 4).coerceAtLeast(0)
    }

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
        source: String,
        payload: String = ""
    ) {
        if (!isEnabled(type)) return
        if (!DungeonManager.isInDungeon(player)) return
        val list = modifiers.getOrPut(player.uniqueId) { mutableListOf() }
        val existing = findMergeTarget(list, type, payload)
        if (existing != null) {
            existing.remainingRooms = maxOf(existing.remainingRooms, remainingRooms)
            existing.value = maxOf(existing.value, value)
            if (type == RunModifierType.BOON_ECHO || type == RunModifierType.BOON_MUTATION) {
                existing.charges += charges.coerceAtLeast(0)
            } else {
                existing.charges = maxOf(existing.charges, charges)
            }
            if (payload.isNotBlank()) existing.payload = payload
        } else {
            list += RunModifier(type, remainingRooms, charges, value, source, payload)
            BalanceStatsManager.recordModifierApplied(type)
            RunSummaryManager.onModifierApplied(player.uniqueId, type)
            player.sendMessage("§b获得本局修正: §f${type.displayName} §7- ${type.description}")
        }
        RunPersistenceManager.markDirty()
    }

    fun addSoulDebt(player: Player, principal: Int, interest: Int, deadlineRooms: Int, source: String, penalty: String = soulDebtPenalty) {
        if (!isEnabled(RunModifierType.SOUL_DEBT)) return
        if (!DungeonManager.isInDungeon(player)) return
        val safePrincipal = principal.coerceAtLeast(1)
        val safeDeadline = (deadlineRooms + AccessoryEffectHandler.getSoulDebtDeadlineBonus(player)).coerceAtLeast(1)
        val safeInterest = (interest - AccessoryEffectHandler.getSoulDebtInterestReduction(player)).coerceAtLeast(0)
        val list = modifiers.getOrPut(player.uniqueId) { mutableListOf() }
        val existing = list.firstOrNull { it.type == RunModifierType.SOUL_DEBT }
        if (existing != null) {
            existing.value += safePrincipal
            existing.remainingRooms = maxOf(existing.remainingRooms, safeDeadline)
            existing.charges = 1
            existing.payload = encodePayload(
                payloadMap(existing.payload) + mapOf(
                    "interest" to maxOf(payloadInt(existing, "interest", 0), safeInterest).toString(),
                    "penalty" to penalty,
                    "principal" to existing.value.toInt().toString()
                )
            )
            player.sendMessage("§c灵魂债务追加，本金提高到 §e${existing.value.toInt()}§c。")
            RunPersistenceManager.markDirty()
            return
        }
        addModifier(
            player,
            RunModifierType.SOUL_DEBT,
            safeDeadline,
            1,
            safePrincipal.toDouble(),
            source,
            encodePayload(
                mapOf(
                    "principal" to safePrincipal.toString(),
                    "interest" to safeInterest.toString(),
                    "deadline" to safeDeadline.toString(),
                    "penalty" to penalty
                )
            )
        )
    }

    fun addDelayedReward(player: Player, kind: String, amount: Int, rooms: Int, source: String, failPolicy: String = "partial") {
        if (!isEnabled(RunModifierType.DELAYED_REWARD)) return
        addModifier(
            player,
            RunModifierType.DELAYED_REWARD,
            (rooms - AccessoryEffectHandler.getDelayedRewardRoomReduction(player)).coerceAtLeast(1),
            1,
            amount.coerceAtLeast(1).toDouble(),
            source,
            encodePayload(
                mapOf(
                    "kind" to kind.lowercase(),
                    "amount" to ((amount * (1.0 + AccessoryEffectHandler.getDelayedRewardBonusPercent(player) / 100.0)).toInt()).coerceAtLeast(1).toString(),
                    "fail" to failPolicy
                )
            )
        )
    }

    fun addRoomProphecy(player: Player, target: RoomType, withinRooms: Int, rewardKind: String, rewardAmount: Int, source: String, missPenalty: Int = prophecyMissPenalty) {
        if (!isEnabled(RunModifierType.ROOM_PROPHECY)) return
        addModifier(
            player,
            RunModifierType.ROOM_PROPHECY,
            withinRooms.coerceAtLeast(1),
            1,
            ((rewardAmount * (1.0 + AccessoryEffectHandler.getProphecyRewardBonusPercent(player) / 100.0)).toInt()).coerceAtLeast(0).toDouble(),
            source,
            encodePayload(
                mapOf(
                    "target" to target.name,
                    "reward" to rewardKind.lowercase(),
                    "amount" to ((rewardAmount * (1.0 + AccessoryEffectHandler.getProphecyRewardBonusPercent(player) / 100.0)).toInt()).coerceAtLeast(0).toString(),
                    "miss" to (missPenalty - AccessoryEffectHandler.getProphecyMissReduction(player)).coerceAtLeast(0).toString()
                )
            )
        )
    }

    fun addRouteChain(player: Player, sequence: List<RoomType>, source: String, rewardKind: String = "boon_echo", rewardAmount: Int = 1, tolerance: Int = routeChainTolerance) {
        if (!isEnabled(RunModifierType.ROUTE_CHAIN) || sequence.isEmpty()) return
        addModifier(
            player,
            RunModifierType.ROUTE_CHAIN,
            0,
            1,
            rewardAmount.coerceAtLeast(1).toDouble(),
            source,
            encodePayload(
                mapOf(
                    "sequence" to sequence.joinToString(">") { it.name },
                    "progress" to "0",
                    "reward" to rewardKind.lowercase(),
                    "amount" to (rewardAmount + AccessoryEffectHandler.getRouteChainRewardBonus(player)).coerceAtLeast(1).toString(),
                    "tolerance" to (tolerance + AccessoryEffectHandler.getRouteChainToleranceBonus(player)).coerceAtLeast(0).toString()
                )
            )
        )
    }

    fun addBoonEcho(player: Player, charges: Int = 1, source: String = "神恩回响") {
        if (!isEnabled(RunModifierType.BOON_ECHO)) return
        addModifier(player, RunModifierType.BOON_ECHO, 0, charges.coerceAtLeast(1), charges.coerceAtLeast(1).toDouble(), source)
    }

    fun addBoonMutation(player: Player, extraChoices: Int = 1, source: String = "神恩变质") {
        if (!isEnabled(RunModifierType.BOON_MUTATION)) return
        addModifier(player, RunModifierType.BOON_MUTATION, 0, 1, extraChoices.coerceAtLeast(1).toDouble(), source)
    }

    fun onRoomEntered(player: Player, roomType: RoomType) {
        if (roomType == RoomType.SPAWN) return
        val list = modifiers[player.uniqueId] ?: return
        if (list.isEmpty()) return
        val remove = mutableListOf<RunModifier>()
        for (modifier in list.toList()) {
            when (modifier.type) {
                RunModifierType.ROOM_PROPHECY -> handleRoomProphecy(player, modifier, roomType, remove)
                RunModifierType.ROUTE_CHAIN -> handleRouteChain(player, modifier, roomType, remove)
                else -> Unit
            }
        }
        if (remove.isNotEmpty()) {
            list.removeAll(remove.toSet())
            if (list.isEmpty()) modifiers.remove(player.uniqueId)
        }
        RunPersistenceManager.markDirty()
    }

    fun onRoomCleared(player: Player, roomType: RoomType, instance: DungeonInstance? = DungeonManager.getPlayerDungeon(player)) {
        val list = modifiers[player.uniqueId] ?: return
        if (list.isEmpty()) return

        val remove = mutableListOf<RunModifier>()
        for (modifier in list.toList()) {
            when (modifier.type) {
                RunModifierType.SHOP_DEBT -> applyShopDebt(player, modifier)
                RunModifierType.SEALED_CHEST_PRESSURE -> {
                    if (isCombatLike(roomType)) {
                        val reward = modifier.value.toInt().coerceAtLeast(1)
                        ShardRewardManager.addRunShards(player.uniqueId, reward)
                        player.sendMessage("§6封印宝箱回响兑现，额外获得 §e$reward §6本局碎片。")
                        modifier.charges -= 1
                    }
                }
                RunModifierType.SHRINE_BLESSING -> applyShrineBlessing(player, modifier)
                RunModifierType.SOUL_DEBT -> applySoulDebtTick(player, modifier)
                RunModifierType.DELAYED_REWARD -> applyDelayedRewardTick(player, instance, modifier)
                RunModifierType.GAMBLE_STREAK,
                RunModifierType.FORGE_OVERDRIVE,
                RunModifierType.ROOM_PROPHECY,
                RunModifierType.ROUTE_CHAIN,
                RunModifierType.BOON_ECHO,
                RunModifierType.BOON_MUTATION,
                RunModifierType.RELIC_CHARGE_RULE,
                RunModifierType.SEALED_FUTURE -> Unit
            }

            if (modifier.type == RunModifierType.SHOP_DEBT || modifier.type == RunModifierType.SHRINE_BLESSING) {
                modifier.charges -= 1
            }
            if (shouldTickRemaining(modifier.type, roomType) && modifier.remainingRooms > 0) {
                modifier.remainingRooms -= 1
            }
            val chargeOnlyDone = modifier.type == RunModifierType.SEALED_CHEST_PRESSURE && modifier.charges <= 0
            val timedChargeDone = modifier.remainingRooms == 0 && modifier.charges <= 0
            val forcedDone = modifier.charges < 0
            if (chargeOnlyDone || timedChargeDone || forcedDone) {
                remove += modifier
            }
        }
        if (remove.isNotEmpty()) {
            list.removeAll(remove.toSet())
            if (list.isEmpty()) modifiers.remove(player.uniqueId)
        }
        RunPersistenceManager.markDirty()
    }

    fun onRunEnding(player: Player, instance: DungeonInstance?, death: Boolean) {
        val list = modifiers[player.uniqueId] ?: return
        if (list.isEmpty()) return
        val remove = mutableListOf<RunModifier>()
        for (modifier in list.toList()) {
            when (modifier.type) {
                RunModifierType.SOUL_DEBT -> {
                    val debt = modifier.value.toInt().coerceAtLeast(0)
                    if (debt > 0) {
                        settleSoulDebt(player, debt, modifier, ending = true)
                    }
                    remove += modifier
                }
                RunModifierType.DELAYED_REWARD -> {
                    val kind = payloadString(modifier, "kind", "shards")
                    val amount = modifier.value.toInt().coerceAtLeast(0)
                    if (death) {
                        player.sendMessage("§8托管奖励因死亡而消散。")
                    } else if (kind == "shards" && amount > 0) {
                        val fallback = (amount * delayedRewardFallbackRatio).toInt().coerceAtLeast(1)
                        ShardRewardManager.addRunShards(player.uniqueId, fallback)
                        player.sendMessage("§7未成熟的托管奖励提前结算为 §e$fallback §7本局碎片。")
                    } else {
                        player.sendMessage("§8未成熟的托管奖励没有来得及兑现。")
                    }
                    remove += modifier
                }
                else -> Unit
            }
        }
        if (remove.isNotEmpty()) {
            list.removeAll(remove.toSet())
            if (list.isEmpty()) modifiers.remove(player.uniqueId)
            RunPersistenceManager.markDirty()
        }
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

    fun getBoonOfferExtra(player: Player): Int {
        if (!boonMutationEnabled) return 0
        return modifiers[player.uniqueId]
            ?.filter { it.type == RunModifierType.BOON_MUTATION && it.charges > 0 }
            ?.sumOf { it.value.toInt().coerceAtLeast(1) }
            ?.coerceAtMost(2)
            ?: 0
    }

    fun consumeBoonEcho(player: Player): Int {
        if (!boonEchoEnabled) return 0
        val modifier = modifiers[player.uniqueId]?.firstOrNull { it.type == RunModifierType.BOON_ECHO && it.charges > 0 }
            ?: return 0
        val copies = modifier.value.toInt().coerceAtLeast(1).coerceAtMost(2)
        consume(player, modifier)
        return copies
    }

    fun consumeBoonMutation(player: Player): Boolean {
        if (!boonMutationEnabled) return false
        val modifier = modifiers[player.uniqueId]?.firstOrNull { it.type == RunModifierType.BOON_MUTATION && it.charges > 0 }
            ?: return false
        consume(player, modifier)
        return true
    }

    fun reduceSoulDebt(player: Player, amount: Int): Int {
        if (amount <= 0) return 0
        val list = modifiers[player.uniqueId] ?: return 0
        val modifier = list.firstOrNull { it.type == RunModifierType.SOUL_DEBT } ?: return 0
        val paid = amount.coerceAtMost(modifier.value.toInt().coerceAtLeast(0))
        if (paid <= 0) return 0
        modifier.value -= paid
        modifier.payload = encodePayload(payloadMap(modifier.payload) + mapOf("principal" to modifier.value.toInt().coerceAtLeast(0).toString()))
        if (modifier.value <= 0.0) {
            list.remove(modifier)
            if (list.isEmpty()) modifiers.remove(player.uniqueId)
            player.sendMessage("§a灵魂债务已被完全清偿。")
        } else {
            player.sendMessage("§a灵魂债务减少 §e$paid§a，剩余 §e${modifier.value.toInt()}§a。")
        }
        RunPersistenceManager.markDirty()
        return paid
    }

    fun repaySoulDebt(player: Player, maxAmount: Int = Int.MAX_VALUE): Int {
        val current = ShardRewardManager.getRunShards(player.uniqueId)
        if (current <= 0) return 0
        val list = modifiers[player.uniqueId] ?: return 0
        val debts = list.filter { it.type == RunModifierType.SOUL_DEBT && it.value > 0.0 }
        if (debts.isEmpty()) return 0
        var budget = minOf(current, maxAmount.coerceAtLeast(0))
        var paidTotal = 0
        for (modifier in debts) {
            if (budget <= 0) break
            val paid = minOf(budget, modifier.value.toInt().coerceAtLeast(0))
            if (paid <= 0) continue
            if (!ShardRewardManager.takeRunShards(player.uniqueId, paid)) break
            budget -= paid
            paidTotal += paid
            modifier.value -= paid
            modifier.payload = encodePayload(payloadMap(modifier.payload) + mapOf("principal" to modifier.value.toInt().coerceAtLeast(0).toString()))
        }
        val cleared = debts.filter { it.value <= 0.0 }
        if (cleared.isNotEmpty()) {
            list.removeAll(cleared.toSet())
            if (list.isEmpty()) modifiers.remove(player.uniqueId)
        }
        if (paidTotal > 0) {
            player.sendMessage("§a你主动偿还了 §e$paidTotal §a灵魂债务。")
            RunPersistenceManager.markDirty()
        }
        return paidTotal
    }

    fun getSoulDebtTotal(player: Player): Int {
        return modifiers[player.uniqueId]
            ?.filter { it.type == RunModifierType.SOUL_DEBT }
            ?.sumOf { it.value.toInt().coerceAtLeast(0) }
            ?: 0
    }

    fun chargeRelicRule(player: Player, relicId: String, relicName: String, maxCharges: Int, rewardKind: String, rewardAmount: Int = 1): Boolean {
        if (!relicChargeEnabled || maxCharges <= 0) return false
        val list = modifiers.getOrPut(player.uniqueId) { mutableListOf() }
        val modifier = list.firstOrNull { it.type == RunModifierType.RELIC_CHARGE_RULE && payloadString(it, "relic", "") == relicId }
            ?: RunModifier(
                RunModifierType.RELIC_CHARGE_RULE,
                0,
                0,
                maxCharges.toDouble(),
                relicName,
                encodePayload(
                    mapOf(
                        "relic" to relicId,
                        "max" to maxCharges.coerceAtLeast(1).toString(),
                        "reward" to rewardKind.lowercase(),
                        "amount" to rewardAmount.coerceAtLeast(1).toString()
                    )
                )
            ).also {
                list += it
                BalanceStatsManager.recordModifierApplied(RunModifierType.RELIC_CHARGE_RULE)
                RunSummaryManager.onModifierApplied(player.uniqueId, RunModifierType.RELIC_CHARGE_RULE)
                player.sendMessage("§b遗物开始充能: §f$relicName")
            }

        modifier.charges += 1
        val max = payloadInt(modifier, "max", maxCharges).coerceAtLeast(1)
        if (modifier.charges >= max) {
            if (rewardKind.equals("charge_only", ignoreCase = true)) {
                modifier.charges = max
                player.sendMessage("§d$relicName §d充能已满，等待抵消费用或惩罚。")
                RunPersistenceManager.markDirty()
                return true
            }
            modifier.charges = 0
            grantModifierReward(player, DungeonManager.getPlayerDungeon(player), rewardKind, rewardAmount.coerceAtLeast(1), "${relicName}充能")
            player.sendMessage("§d$relicName §d充能已满，触发了 ${rewardName(rewardKind)}。")
            RunPersistenceManager.markDirty()
            return true
        }
        player.sendMessage("§7$relicName 充能: §b${modifier.charges}§7/§f$max")
        RunPersistenceManager.markDirty()
        return false
    }

    fun consumeRelicCharge(player: Player, relicId: String, cost: Int): Boolean {
        if (cost <= 0) return true
        val modifier = modifiers[player.uniqueId]?.firstOrNull {
            it.type == RunModifierType.RELIC_CHARGE_RULE && payloadString(it, "relic", "") == relicId
        } ?: return false
        if (modifier.charges < cost) return false
        modifier.charges -= cost
        RunPersistenceManager.markDirty()
        return true
    }

    fun getRoomWeightBonus(player: Player): Map<RoomType, Int> {
        val result = mutableMapOf<RoomType, Int>()
        val list = modifiers[player.uniqueId] ?: return emptyMap()
        for (modifier in list) {
            when (modifier.type) {
                RunModifierType.ROOM_PROPHECY -> {
                    val target = payloadRoomType(modifier, "target")
                    if (target != null) {
                        result[target] = (result[target] ?: 0) + prophecyRoomWeightBonus
                    }
                }
                RunModifierType.ROUTE_CHAIN -> {
                    val next = nextRouteChainRoom(modifier)
                    if (next != null) {
                        result[next] = (result[next] ?: 0) + routeChainRoomWeightBonus
                    }
                }
                else -> Unit
            }
        }
        return result
    }

    fun applyFloorProphecyAffix(player: Player, instance: DungeonInstance) {
        val power = AffixManager.getFloorProphecyPower(instance)
        if (power <= 0 || !prophecyEnabled || hasModifier(player, RunModifierType.ROOM_PROPHECY)) {
            return
        }
        val candidates = listOf(RoomType.CHEST, RoomType.ELITE, RoomType.SHRINE, RoomType.FORGE, RoomType.TRIAL, RoomType.CONTRACT)
        val target = candidates.random()
        val reward = prophecyReward(instance, power)
        addRoomProphecy(player, target, prophecyWithinRooms + (power / 3).coerceAtMost(2), "shards", reward, "副本预兆", prophecyMissPenalty)
        player.sendMessage("§d本层词缀投下预言：在期限内找到 §f${target.displayName} §d房可获得 §e$reward §d本局碎片。")
    }

    fun describePayload(modifier: RunModifier): List<String> {
        return when (modifier.type) {
            RunModifierType.SOUL_DEBT -> listOf(
                "§7债务本金: §c${modifier.value.toInt().coerceAtLeast(0)}",
                "§7每房利息: §e${payloadInt(modifier, "interest", 0)}",
                "§7逾期惩罚: §c${displayName(payloadString(modifier, "penalty", soulDebtPenalty), "惩罚")}"
            )
            RunModifierType.DELAYED_REWARD -> listOf(
                "§7托管类型: §f${rewardName(payloadString(modifier, "kind", "shards"))}",
                "§7预计收益: §e${modifier.value.toInt().coerceAtLeast(0)}",
                "§7提前结束: §f${payloadString(modifier, "fail", "partial")}"
            )
            RunModifierType.ROOM_PROPHECY -> listOf(
                "§7目标房间: §d${payloadRoomType(modifier, "target")?.displayName ?: "未知"}",
                "§7完成奖励: §f${rewardName(payloadString(modifier, "reward", "shards"))} §e${payloadInt(modifier, "amount", modifier.value.toInt())}",
                "§7失败扣除: §c${payloadInt(modifier, "miss", prophecyMissPenalty)} §7本局碎片"
            )
            RunModifierType.ROUTE_CHAIN -> listOf(
                "§7目标序列: ${formatRouteSequence(payloadString(modifier, "sequence", ""))}",
                "§7当前进度: §b${payloadInt(modifier, "progress", 0)}",
                "§7容错次数: §e${payloadInt(modifier, "tolerance", 0)}"
            )
            RunModifierType.BOON_ECHO -> listOf("§7下一次神恩选择后额外复制 §d${modifier.value.toInt().coerceAtLeast(1)} §7次")
            RunModifierType.BOON_MUTATION -> listOf("§7下一次神恩候选额外增加 §d${modifier.value.toInt().coerceAtLeast(1)} §7项")
            RunModifierType.RELIC_CHARGE_RULE -> listOf(
                "§7遗物: §f${displayName(modifier.source, "遗物")}",
                "§7充能: §b${modifier.charges}§7/§f${payloadInt(modifier, "max", modifier.value.toInt())}",
                "§7满层奖励: §d${rewardName(payloadString(modifier, "reward", "boon_echo"))}"
            )
            else -> emptyList()
        }
    }

    fun payloadString(modifier: RunModifier, key: String, default: String = ""): String {
        return payloadMap(modifier.payload)[key] ?: default
    }

    fun payloadInt(modifier: RunModifier, key: String, default: Int = 0): Int {
        return payloadString(modifier, key, default.toString()).toIntOrNull() ?: default
    }

    fun payloadDouble(modifier: RunModifier, key: String, default: Double = 0.0): Double {
        return payloadString(modifier, key, default.toString()).toDoubleOrNull() ?: default
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

    private fun findMergeTarget(list: List<RunModifier>, type: RunModifierType, payload: String): RunModifier? {
        return when (type) {
            RunModifierType.DELAYED_REWARD -> null
            RunModifierType.RELIC_CHARGE_RULE -> {
                val relic = payloadMap(payload)["relic"] ?: return null
                list.firstOrNull { it.type == type && payloadString(it, "relic", "") == relic }
            }
            else -> list.firstOrNull { it.type == type }
        }
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

    private fun applySoulDebtTick(player: Player, modifier: RunModifier) {
        if (modifier.remainingRooms <= 1) {
            settleSoulDebt(player, modifier.value.toInt().coerceAtLeast(0), modifier, ending = false)
            modifier.value = 0.0
            modifier.charges = 0
            modifier.remainingRooms = 0
            return
        }
        val interest = payloadInt(modifier, "interest", soulDebtInterestPerRoom).coerceAtLeast(0)
        if (interest > 0) {
            modifier.value += interest
            modifier.payload = encodePayload(payloadMap(modifier.payload) + mapOf("principal" to modifier.value.toInt().toString()))
            player.sendMessage("§c灵魂债务计息 §e+$interest§c，当前债务 §e${modifier.value.toInt()}§c。")
        }
    }

    private fun settleSoulDebt(player: Player, amount: Int, modifier: RunModifier, ending: Boolean) {
        if (amount <= 0) return
        val current = ShardRewardManager.getRunShards(player.uniqueId)
        val paid = amount.coerceAtMost(current)
        if (paid > 0) {
            ShardRewardManager.takeRunShards(player.uniqueId, paid)
            player.sendMessage("§c灵魂债务到期，偿还 §e$paid §c本局碎片。")
        }
        val shortage = amount - paid
        if (shortage <= 0) {
            player.sendMessage("§a灵魂债务已清偿。")
            return
        }
        player.sendMessage("§4灵魂债务仍缺 §e$shortage§4。")
        if (ending) {
            return
        }
        if (trySpendDebtShieldCharge(player)) {
            player.sendMessage("§d充能遗物替你抵消了债务逾期惩罚。")
            return
        }
        if (AccessoryEffectHandler.rollSoulDebtPenaltyShield(player)) {
            player.sendMessage("§d饰品担保替你抵消了债务逾期惩罚。")
            return
        }
        val curse = runCatching { RunCurseType.valueOf(payloadString(modifier, "penalty", soulDebtPenalty).uppercase()) }
            .getOrDefault(RunCurseType.VULNERABLE)
        RunCurseManager.addCurse(player, curse)
    }

    private fun applyDelayedRewardTick(player: Player, instance: DungeonInstance?, modifier: RunModifier) {
        if (modifier.remainingRooms > 1) {
            return
        }
        val kind = payloadString(modifier, "kind", "shards")
        val amount = modifier.value.toInt().coerceAtLeast(1)
        grantModifierReward(player, instance, kind, amount, modifier.source)
        modifier.charges = 0
        modifier.remainingRooms = 0
    }

    private fun grantModifierReward(player: Player, instance: DungeonInstance?, kind: String, amount: Int, source: String) {
        val safeAmount = amount.coerceAtLeast(1)
        when (kind.lowercase()) {
            "shards", "shard" -> {
                ShardRewardManager.addRunShards(player.uniqueId, safeAmount)
                player.sendMessage("§6$source 兑现，获得 §e$safeAmount §6本局碎片。")
            }
            "boon" -> {
                player.sendMessage("§d$source 兑现，获得一次神恩选择。")
                BoonSelectManager.offerBoonSelection(player, safeAmount.coerceIn(1, 4))
            }
            "relic" -> {
                player.sendMessage("§d$source 兑现，获得一次遗物选择。")
                if (!RelicSelectManager.offerRelicSelection(player, safeAmount.coerceIn(1, 4))) {
                    BoonSelectManager.offerBoonSelection(player, 3)
                }
            }
            "loot", "hidden_loot" -> {
                if (instance != null && DungeonLootManager.grantHiddenLoot(player, instance)) {
                    player.sendMessage("§6$source 兑现，吐出一件隐藏战利品。")
                } else {
                    player.sendMessage("§7$source 没有找到可用战利品，转化为一次神恩选择。")
                    BoonSelectManager.offerBoonSelection(player, 3)
                }
            }
            "forge", "ember" -> {
                ForgeMaterialManager.add(player.uniqueId, ForgeMaterialType.BOSS_EMBER, safeAmount)
                player.sendMessage("§6$source 兑现，获得 ${ForgeMaterialType.BOSS_EMBER.coloredName()} §ex$safeAmount")
            }
            "boon_echo" -> addBoonEcho(player, safeAmount, source)
            "boon_mutation" -> addBoonMutation(player, safeAmount, source)
            else -> {
                ShardRewardManager.addRunShards(player.uniqueId, safeAmount)
                player.sendMessage("§6$source 兑现，获得 §e$safeAmount §6本局碎片。")
            }
        }
    }

    private fun handleRoomProphecy(player: Player, modifier: RunModifier, roomType: RoomType, remove: MutableList<RunModifier>) {
        val target = payloadRoomType(modifier, "target") ?: return
        if (roomType == target) {
            grantModifierReward(
                player,
                DungeonManager.getPlayerDungeon(player),
                payloadString(modifier, "reward", "shards"),
                payloadInt(modifier, "amount", modifier.value.toInt()).coerceAtLeast(1),
                modifier.source
            )
            player.sendMessage("§d房间预言完成: §f${target.displayName}§d。")
            remove += modifier
            return
        }
        modifier.remainingRooms -= 1
        if (modifier.remainingRooms <= 0) {
            val miss = payloadInt(modifier, "miss", prophecyMissPenalty).coerceAtLeast(0)
            if (miss > 0) {
                val current = ShardRewardManager.getRunShards(player.uniqueId)
                val loss = miss.coerceAtMost(current)
                if (loss > 0) ShardRewardManager.takeRunShards(player.uniqueId, loss)
                player.sendMessage("§5房间预言破碎，失去 §e$loss §5本局碎片。")
            } else {
                player.sendMessage("§5房间预言破碎。")
            }
            remove += modifier
        } else {
            player.sendMessage("§7预言目标仍是 §d${target.displayName}§7，剩余 §b${modifier.remainingRooms} §7个房间。")
        }
    }

    private fun handleRouteChain(player: Player, modifier: RunModifier, roomType: RoomType, remove: MutableList<RunModifier>) {
        val sequence = payloadString(modifier, "sequence", "")
            .split(">")
            .mapNotNull { parseRoomType(it) }
        if (sequence.isEmpty()) {
            remove += modifier
            return
        }
        val progress = payloadInt(modifier, "progress", 0).coerceIn(0, sequence.size)
        val expected = sequence.getOrNull(progress)
        if (expected == roomType) {
            val nextProgress = progress + 1
            if (nextProgress >= sequence.size) {
                grantModifierReward(
                    player,
                    DungeonManager.getPlayerDungeon(player),
                    payloadString(modifier, "reward", "boon_echo"),
                    payloadInt(modifier, "amount", modifier.value.toInt()).coerceAtLeast(1),
                    modifier.source
                )
                player.sendMessage("§a路线连锁完成: §f${sequence.joinToString(" > ") { it.displayName }}")
                remove += modifier
            } else {
                modifier.payload = encodePayload(payloadMap(modifier.payload) + mapOf("progress" to nextProgress.toString()))
                player.sendMessage("§a路线连锁推进: §f${roomType.displayName} §7→ 下一个目标 §e${sequence[nextProgress].displayName}")
            }
            return
        }

        val tolerance = payloadInt(modifier, "tolerance", 0)
        if (tolerance > 0) {
            modifier.payload = encodePayload(payloadMap(modifier.payload) + mapOf("tolerance" to (tolerance - 1).toString()))
            player.sendMessage("§e路线连锁偏离一次，剩余容错 §f${tolerance - 1}§e。")
            return
        }

        val restart = if (sequence.firstOrNull() == roomType) 1 else 0
        modifier.payload = encodePayload(payloadMap(modifier.payload) + mapOf("progress" to restart.toString()))
        player.sendMessage("§7路线连锁被重置。" + if (restart > 0) " §a但当前房间重新接上第一环。" else "")
    }

    private fun trySpendDebtShieldCharge(player: Player): Boolean {
        for (relic in PlayerRelicData.getRelics(player)) {
            if (relic.effectType != RelicEffectType.RELIC_PAY_DEBT_WITH_CHARGE) continue
            val cost = relic.threshold.toInt().coerceAtLeast(1)
            if (consumeRelicCharge(player, relic.id, cost)) {
                return true
            }
        }
        return false
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

    private fun shouldTickRemaining(type: RunModifierType, roomType: RoomType): Boolean {
        return when (type) {
            RunModifierType.SEALED_CHEST_PRESSURE -> isCombatLike(roomType)
            RunModifierType.GAMBLE_STREAK,
            RunModifierType.FORGE_OVERDRIVE,
            RunModifierType.ROOM_PROPHECY,
            RunModifierType.ROUTE_CHAIN,
            RunModifierType.BOON_ECHO,
            RunModifierType.BOON_MUTATION,
            RunModifierType.RELIC_CHARGE_RULE,
            RunModifierType.SEALED_FUTURE -> false
            else -> true
        }
    }

    private fun isCombatLike(roomType: RoomType): Boolean {
        return roomType == RoomType.COMBAT || roomType == RoomType.ELITE || roomType == RoomType.BOSS
    }

    private fun nextRouteChainRoom(modifier: RunModifier): RoomType? {
        val sequence = payloadString(modifier, "sequence", "")
            .split(">")
            .mapNotNull { parseRoomType(it) }
        val progress = payloadInt(modifier, "progress", 0).coerceAtLeast(0)
        return sequence.getOrNull(progress)
    }

    private fun payloadRoomType(modifier: RunModifier, key: String): RoomType? {
        return parseRoomType(payloadString(modifier, key, ""))
    }

    private fun parseRoomType(value: String): RoomType? {
        return runCatching { RoomType.valueOf(value.trim().uppercase()) }.getOrNull()
    }

    private fun payloadMap(payload: String): Map<String, String> {
        if (payload.isBlank()) return emptyMap()
        return payload.split(";")
            .mapNotNull { entry ->
                val index = entry.indexOf('=')
                if (index <= 0) return@mapNotNull null
                val key = entry.substring(0, index).trim()
                val value = entry.substring(index + 1).trim()
                if (key.isBlank()) null else key to value
            }
            .toMap()
    }

    private fun encodePayload(values: Map<String, String>): String {
        return values.entries
            .filter { it.key.isNotBlank() && it.value.isNotBlank() }
            .joinToString(";") { "${it.key}=${it.value.replace(";", ",")}" }
    }

    private fun rewardName(kind: String): String {
        return when (kind.lowercase()) {
            "shards", "shard" -> "本局碎片"
            "boon" -> "神恩选择"
            "relic" -> "遗物选择"
            "loot", "hidden_loot" -> "隐藏战利品"
            "forge", "ember" -> "炉核余烬"
            "boon_echo" -> "神恩回响"
            "boon_mutation" -> "神恩变质"
            "charge_only" -> "保留充能"
            else -> displayName(kind, "奖励")
        }
    }

    private fun formatRouteSequence(raw: String): String {
        val parts = raw.split(">").map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) {
            return "§f未指定"
        }
        return parts.joinToString(" §8> §f") { displayName(it, "房间") }.let { "§f$it" }
    }

    private fun displayName(raw: String, fallback: String): String {
        return ContentDisplayNameResolver.safeText(raw, fallback)
    }
}
