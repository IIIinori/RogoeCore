package inori.roguecore.affix

import inori.roguecore.combat.RoomState
import inori.roguecore.dungeon.DungeonManager
import org.bukkit.Bukkit
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import taboolib.common.platform.event.EventPriority
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityRegainHealthEvent
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.submit
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 词缀效果执行器
 * 处理需要持续生效的词缀（地板着火、禁止回复）
 */
object AffixEffectHandler {

    /** 下次允许触发灼烧的时间戳 */
    private val nextFloorFireAt = ConcurrentHashMap<UUID, Long>()
    private val nextVoidFieldAt = ConcurrentHashMap<UUID, Long>()

    /**
     * 启动周期性任务 — 地板着火效果
     */
    @Awake(LifeCycle.ENABLE)
    fun startFireTask() {
        // 每秒检查一次，由词缀 value 控制真实触发间隔
        submit(period = 20L) {
            val now = System.currentTimeMillis()
            for (player in Bukkit.getOnlinePlayers()) {
                val uuid = player.uniqueId
                val instance = DungeonManager.getPlayerDungeon(player)
                if (instance == null || instance.completed) {
                    nextFloorFireAt.remove(uuid)
                    nextVoidFieldAt.remove(uuid)
                    continue
                }
                if (AffixManager.hasAffix(instance, AffixType.FLOOR_FIRE) && shouldTriggerFloorFire(player, instance)) {
                    val nextAt = nextFloorFireAt[uuid] ?: 0L
                    if (now >= nextAt) {
                        triggerFloorFire(player)

                        val intervalMillis = (AffixManager.getAffixValue(instance, AffixType.FLOOR_FIRE)
                            .coerceAtLeast(1.0) * 1000.0).toLong()
                        nextFloorFireAt[uuid] = now + intervalMillis
                    }
                } else {
                    nextFloorFireAt.remove(uuid)
                }

                if (AffixManager.hasAffix(instance, AffixType.VOID_FIELD) && shouldTriggerFloorFire(player, instance)) {
                    val nextVoidAt = nextVoidFieldAt[uuid] ?: 0L
                    if (now >= nextVoidAt) {
                        triggerVoidField(player, AffixManager.getAffixValue(instance, AffixType.VOID_FIELD))
                        nextVoidFieldAt[uuid] = now + 4000L
                    }
                } else {
                    nextVoidFieldAt.remove(uuid)
                }
            }
            regenerateMobs()
        }
    }

    private fun regenerateMobs() {
        for (instance in DungeonManager.getActiveDungeons()) {
            val regen = AffixManager.getMobRegenPercent(instance)
            if (regen <= 0.0 || instance.completed) {
                continue
            }
            val room = inori.roguecore.combat.RoomCombatManager.getActiveRoom(instance) ?: continue
            if (room.state != inori.roguecore.combat.RoomState.ACTIVE) {
                continue
            }
            for (entity in instance.world.livingEntities) {
                if (entity is Player || entity.isDead) {
                    continue
                }
                val maxHealth = entity.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH)?.value ?: continue
                if (maxHealth <= 0.0 || entity.health >= maxHealth) {
                    continue
                }
                entity.health = (entity.health + maxHealth * regen).coerceAtMost(maxHealth)
            }
        }
    }

    /**
     * 只在战斗房里、玩家脚下有实体地面时触发灼烧
     */
    private fun shouldTriggerFloorFire(player: Player, instance: inori.roguecore.dungeon.DungeonInstance): Boolean {
        if (!player.isOnline || player.isDead) {
            return false
        }

        val px = player.location.blockX - instance.origin.blockX
        val pz = player.location.blockZ - instance.origin.blockZ
        val room = instance.rooms.firstOrNull { it.contains(px, pz) } ?: return false
        if (!room.isCombatRoom || room.state != RoomState.ACTIVE) {
            return false
        }

        val floorBlock = player.location.clone().add(0.0, -1.0, 0.0).block
        return floorBlock.type.isSolid
    }

    /**
     * 灼烧玩家，并给出明显的视觉和音效反馈
     */
    private fun triggerFloorFire(player: Player) {
        val location = player.location.clone().add(0.0, 0.1, 0.0)

        player.fireTicks = maxOf(player.fireTicks, 60)
        player.damage(2.0)
        player.world.spawnParticle(Particle.FLAME, location, 24, 0.35, 0.05, 0.35, 0.01)
        player.world.spawnParticle(Particle.LAVA, location, 6, 0.25, 0.02, 0.25, 0.0)
        player.world.playSound(location, Sound.BLOCK_FIRE_AMBIENT, 0.7f, 1.15f)
    }

    private fun triggerVoidField(player: Player, strength: Double) {
        val location = player.location.clone().add(0.0, 0.8, 0.0)
        val damage = strength.coerceAtLeast(0.5)
        player.damage(damage)
        player.addPotionEffect(PotionEffect(PotionEffectType.SLOW, 60, 0, true, true, true))
        player.world.spawnParticle(Particle.PORTAL, location, 30, 0.5, 0.6, 0.5, 0.04)
        player.world.playSound(location, Sound.ENTITY_ENDERMAN_AMBIENT, 0.45f, 0.8f)
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onEntityDamage(event: EntityDamageByEntityEvent) {
        val player = event.entity as? Player ?: return
        val instance = DungeonManager.getPlayerDungeon(player) ?: return
        val damager = event.damager as? LivingEntity ?: return

        val mobDamageMultiplier = AffixManager.getMobDamageMultiplier(instance)
        if (mobDamageMultiplier > 0.0 && mobDamageMultiplier != 1.0) {
            event.damage *= mobDamageMultiplier
        }

        val mobMaxHealth = damager.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH)?.value ?: 0.0
        if (mobMaxHealth > 0.0 && damager.health / mobMaxHealth <= 0.35) {
            event.damage *= 1.0 + AffixManager.getMobLowHealthRage(instance)
        }

        val room = inori.roguecore.combat.RoomCombatManager.getPlayerRoom(player)
        if (room?.type == inori.roguecore.dungeon.room.RoomType.BOSS) {
            event.damage *= AffixManager.getBossDamageMultiplier(instance)
        }

        val lifesteal = AffixManager.getMobLifesteal(instance)
        if (lifesteal > 0.0) {
            val max = damager.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH)?.value ?: damager.health
            damager.health = (damager.health + event.damage * lifesteal).coerceAtMost(max)
        }

        if (AffixManager.hasAffix(instance, AffixType.MOB_FIRE_ATTACK)) {
            player.fireTicks = maxOf(player.fireTicks, (AffixManager.getAffixValue(instance, AffixType.MOB_FIRE_ATTACK) * 20.0).toInt().coerceAtLeast(40))
        }

        val pressure = AffixManager.getLowHealthPressure(instance)
        if (pressure > 0.0) {
            val maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH)?.value ?: 20.0
            if (maxHealth > 0.0 && player.health / maxHealth <= 0.35) {
                event.damage *= 1.0 + pressure
            }
        }
    }

    /**
     * 禁止自然回复 — 拦截回复事件
     */
    @SubscribeEvent
    fun onHealthRegain(event: EntityRegainHealthEvent) {
        val player = event.entity as? Player ?: return
        val instance = DungeonManager.getPlayerDungeon(player) ?: return

        if (AffixManager.hasAffix(instance, AffixType.HEALING_REDUCE)) {
            event.amount *= AffixManager.getHealingMultiplier(instance)
        }

        if (!AffixManager.hasAffix(instance, AffixType.NO_HEAL)) return

        // 只拦截自然回复和饱食回复，不拦截药水/技能回复
        if (event.regainReason == EntityRegainHealthEvent.RegainReason.SATIATED ||
            event.regainReason == EntityRegainHealthEvent.RegainReason.REGEN) {
            event.isCancelled = true
        }
    }
}
