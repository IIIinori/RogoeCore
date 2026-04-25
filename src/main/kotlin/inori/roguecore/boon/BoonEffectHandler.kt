package inori.roguecore.boon

import inori.roguecore.data.ShardRewardManager
import inori.roguecore.dungeon.DungeonManager
import inori.roguecore.dungeon.route.NextFloorRoute
import inori.roguecore.dungeon.room.RoomType
import inori.roguecore.modifier.RunModifierManager
import inori.roguecore.relic.PlayerRelicData
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
    private val firstKillClaimed = ConcurrentHashMap<UUID, Boolean>()
    private val roomClearStreak = ConcurrentHashMap<UUID, Int>()

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
        val firstKill = firstKillClaimed.put(killer.uniqueId, true) != true
        for (instance in PlayerBoonData.getBoons(killer)) {
            for (effect in instance.boon.effects) {
                when (effect.type) {
                    BoonEffectType.KILL_HEAL -> {
                        val amount = effectValue(killer, instance, effect)
                        heal(killer, amount)
                    }
                    BoonEffectType.KILL_SHARD -> {
                        val amount = effectValue(killer, instance, effect)
                        ShardRewardManager.addRunShards(killer.uniqueId, amount.toInt().coerceAtLeast(1))
                    }
                    BoonEffectType.FIRST_KILL_SHARD -> {
                        if (firstKill) {
                            val amount = effectValue(killer, instance, effect)
                            ShardRewardManager.addRunShards(killer.uniqueId, amount.toInt().coerceAtLeast(1))
                            killer.sendMessage("§6首杀神恩触发，获得 §e${amount.toInt().coerceAtLeast(1)} §6本局碎片。")
                        }
                    }
                    BoonEffectType.KILL_SPEED -> {
                        if (roll(effect) && isReady(killer.uniqueId, instance.boon.id, effect)) {
                            val bonus = effectValue(killer, instance, effect)
                            applySpeed(killer, bonus, effect.durationSeconds)
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    fun onRoomCleared(player: Player, roomType: RoomType? = null) {
        if (!DungeonManager.isInDungeon(player)) {
            return
        }
        val streak = roomClearStreak.merge(player.uniqueId, 1) { old, _ -> (old + 1).coerceAtMost(999) } ?: 1
        for (instance in PlayerBoonData.getBoons(player)) {
            for (effect in instance.boon.effects) {
                when (effect.type) {
                    BoonEffectType.ROOM_HEAL -> {
                        val percent = effectValue(player, instance, effect)
                        val maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.value ?: 20.0
                        heal(player, maxHealth * percent / 100.0)
                    }
                    BoonEffectType.OVERHEAL_SHIELD -> {
                        val percent = effectValue(player, instance, effect)
                        val maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.value ?: 20.0
                        val healAmount = maxHealth * percent / 100.0
                        val missing = (maxHealth - player.health).coerceAtLeast(0.0)
                        val overheal = (healAmount - missing).coerceAtLeast(0.0)
                        heal(player, healAmount)
                        if (overheal > 0.0) {
                            applyAbsorption(player, overheal + percent, effect.durationSeconds.coerceAtLeast(8.0))
                        }
                    }
                    BoonEffectType.ROOM_SHARD -> {
                        val amount = effectValue(player, instance, effect)
                        ShardRewardManager.addRunShards(player.uniqueId, amount.toInt().coerceAtLeast(1))
                    }
                    BoonEffectType.ROOM_STREAK_SHARD -> {
                        val amount = effectValue(player, instance, effect) * streak.coerceAtMost(8)
                        ShardRewardManager.addRunShards(player.uniqueId, amount.toInt().coerceAtLeast(1))
                    }
                    BoonEffectType.ELITE_ROOM_SHARD -> {
                        if (roomType == RoomType.ELITE) {
                            val amount = effectValue(player, instance, effect).toInt().coerceAtLeast(1)
                            ShardRewardManager.addRunShards(player.uniqueId, amount)
                        }
                    }
                    BoonEffectType.BOSS_ROOM_SHARD -> {
                        if (roomType == RoomType.BOSS) {
                            val amount = effectValue(player, instance, effect).toInt().coerceAtLeast(1)
                            ShardRewardManager.addRunShards(player.uniqueId, amount)
                        }
                    }
                    BoonEffectType.SHARD_HELD_SCALING -> {
                        val percent = effectValue(player, instance, effect).coerceAtLeast(0.0)
                        val amount = (ShardRewardManager.getRunShards(player.uniqueId) * percent / 100.0).toInt().coerceAtLeast(0)
                        if (amount > 0) ShardRewardManager.addRunShards(player.uniqueId, amount)
                    }
                    BoonEffectType.RELIC_COUNT_SHARD -> {
                        val amount = (effectValue(player, instance, effect) * PlayerRelicData.getRelics(player).size).toInt().coerceAtLeast(0)
                        if (amount > 0) ShardRewardManager.addRunShards(player.uniqueId, amount)
                    }
                    BoonEffectType.BOON_COUNT_SHARD -> {
                        val amount = (effectValue(player, instance, effect) * PlayerBoonData.getBoons(player).size).toInt().coerceAtLeast(0)
                        if (amount > 0) ShardRewardManager.addRunShards(player.uniqueId, amount)
                    }
                    BoonEffectType.LOW_HEALTH_ROOM_SHARD -> {
                        val maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.value ?: 20.0
                        val healthRate = if (maxHealth > 0.0) player.health / maxHealth else 1.0
                        if (healthRate <= (effect.threshold / 100.0).coerceIn(0.0, 1.0)) {
                            val amount = effectValue(player, instance, effect).toInt().coerceAtLeast(1)
                            ShardRewardManager.addRunShards(player.uniqueId, amount)
                        }
                    }
                    BoonEffectType.SOUL_DEBT_RELIEF -> {
                        val amount = effectValue(player, instance, effect).toInt().coerceAtLeast(1)
                        RunModifierManager.reduceSoulDebt(player, amount)
                    }
                    BoonEffectType.SHIELD_TO_SHARD -> {
                        val cap = effectValue(player, instance, effect).toInt().coerceAtLeast(1)
                        val amount = absorptionValue(player).toInt().coerceIn(0, cap)
                        if (amount > 0) {
                            ShardRewardManager.addRunShards(player.uniqueId, amount)
                            player.sendMessage("§6剩余护盾被转化为 §e$amount §6本局碎片。")
                        }
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
        firstKillClaimed[player.uniqueId] = false
        BoonResonanceManager.onRoomCleared(player)
    }

    fun onShopPurchase(player: Player, spent: Int) {
        if (spent <= 0 || !DungeonManager.isInDungeon(player)) return
        for (instance in PlayerBoonData.getBoons(player)) {
            for (effect in instance.boon.effects) {
                if (effect.type != BoonEffectType.SHOP_SPEND_REFUND) continue
                val percent = effectValue(player, instance, effect).coerceAtLeast(0.0)
                val refund = (spent * percent / 100.0).toInt().coerceAtLeast(0)
                if (refund > 0) {
                    ShardRewardManager.addRunShards(player.uniqueId, refund)
                    player.sendMessage("§6商店返利神恩返还 §e$refund §6本局碎片。")
                }
            }
        }
    }

    fun onChestOpened(player: Player) {
        if (!DungeonManager.isInDungeon(player)) return
        for (instance in PlayerBoonData.getBoons(player)) {
            for (effect in instance.boon.effects) {
                if (effect.type != BoonEffectType.CHEST_OPEN_BONUS) continue
                val amount = effectValue(player, instance, effect).toInt().coerceAtLeast(1)
                ShardRewardManager.addRunShards(player.uniqueId, amount)
                player.sendMessage("§6开箱神恩额外获得 §e$amount §6本局碎片。")
            }
        }
    }

    fun onHiddenRoomOpened(player: Player) {
        if (!DungeonManager.isInDungeon(player)) return
        for (instance in PlayerBoonData.getBoons(player)) {
            for (effect in instance.boon.effects) {
                if (effect.type != BoonEffectType.HIDDEN_ROOM_SHARD) continue
                val amount = effectValue(player, instance, effect).toInt().coerceAtLeast(1)
                ShardRewardManager.addRunShards(player.uniqueId, amount)
                player.sendMessage("§9隐藏房神恩额外获得 §e$amount §9本局碎片。")
            }
        }
    }

    fun onRouteSelected(player: Player, route: NextFloorRoute) {
        if (!DungeonManager.isInDungeon(player)) return
        for (instance in PlayerBoonData.getBoons(player)) {
            for (effect in instance.boon.effects) {
                if (effect.type != BoonEffectType.ROUTE_PICK_BONUS) continue
                val amount = (effectValue(player, instance, effect) * route.rewardLevel).toInt().coerceAtLeast(1)
                ShardRewardManager.addRunShards(player.uniqueId, amount)
                player.sendMessage("§d路线规划神恩因 §f${route.displayName} §d获得 §e$amount §d本局碎片。")
            }
        }
    }

    fun onBoonAcquired(player: Player, boon: Boon, level: Int) {
        if (!DungeonManager.isInDungeon(player)) return
        val instance = DungeonManager.getPlayerDungeon(player)
        for (effect in boon.effects) {
            when (effect.type) {
                BoonEffectType.NEXT_BOON_ECHO -> {
                    val charges = effect.valueAt(level).toInt().coerceAtLeast(1)
                    RunModifierManager.addBoonEcho(player, charges, boon.name)
                }
                BoonEffectType.NEXT_BOON_MUTATION -> {
                    val extra = effect.valueAt(level).toInt().coerceAtLeast(1)
                    RunModifierManager.addBoonMutation(player, extra, boon.name)
                }
                BoonEffectType.ROOM_PROPHECY -> {
                    val target = parseRoomType(effect.tag) ?: listOf(RoomType.CHEST, RoomType.ELITE, RoomType.SHRINE, RoomType.FORGE).random()
                    val within = effect.threshold.toInt().coerceAtLeast(RunModifierManager.prophecyWithinRooms())
                    val amount = effect.valueAt(level).toInt().coerceAtLeast(instance?.let { RunModifierManager.prophecyReward(it) } ?: 24)
                    RunModifierManager.addRoomProphecy(player, target, within, "shards", amount, boon.name)
                }
                BoonEffectType.ROUTE_CHAIN -> {
                    val sequence = effect.tag.split(">").mapNotNull(::parseRoomType)
                    if (sequence.isNotEmpty()) {
                        RunModifierManager.addRouteChain(
                            player,
                            sequence,
                            boon.name,
                            rewardKind = "boon_echo",
                            rewardAmount = effect.valueAt(level).toInt().coerceAtLeast(1)
                        )
                    }
                }
                BoonEffectType.DELAYED_REWARD_SHARD -> {
                    val rooms = effect.threshold.toInt().coerceAtLeast(RunModifierManager.delayedRewardRooms())
                    val amount = effect.valueAt(level).toInt().coerceAtLeast(1)
                    RunModifierManager.addDelayedReward(player, "shards", amount, rooms, boon.name)
                }
                else -> Unit
            }
        }
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

    private fun effectValue(player: Player, instance: BoonInstance, effect: BoonEffect): Double {
        return effect.valueAt(instance.level) + effect.scaleAt(PlayerBoonData.getTagCount(player.uniqueId, effect.tag))
    }

    private fun parseRoomType(value: String): RoomType? {
        return runCatching { RoomType.valueOf(value.trim().uppercase()) }.getOrNull()
    }

    private fun absorptionValue(player: Player): Double {
        val effect = player.getPotionEffect(PotionEffectType.ABSORPTION) ?: return 0.0
        return (effect.amplifier + 1) * 4.0
    }

    private fun heal(player: Player, amount: Double) {
        if (amount <= 0.0) {
            return
        }
        val maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.value ?: 20.0
        player.health = (player.health + amount).coerceAtMost(maxHealth)
    }
}
