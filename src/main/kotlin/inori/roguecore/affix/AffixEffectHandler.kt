package inori.roguecore.affix

import inori.roguecore.combat.RoomState
import inori.roguecore.dungeon.DungeonManager
import org.bukkit.Bukkit
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Player
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
                if (instance == null || instance.completed || !AffixManager.hasAffix(instance, AffixType.FLOOR_FIRE)) {
                    nextFloorFireAt.remove(uuid)
                    continue
                }
                if (!shouldTriggerFloorFire(player, instance)) {
                    continue
                }

                val nextAt = nextFloorFireAt[uuid] ?: 0L
                if (now < nextAt) {
                    continue
                }

                triggerFloorFire(player)

                val intervalMillis = (AffixManager.getAffixValue(instance, AffixType.FLOOR_FIRE)
                    .coerceAtLeast(1.0) * 1000.0).toLong()
                nextFloorFireAt[uuid] = now + intervalMillis
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

    /**
     * 禁止自然回复 — 拦截回复事件
     */
    @SubscribeEvent
    fun onHealthRegain(event: EntityRegainHealthEvent) {
        val player = event.entity as? Player ?: return
        val instance = DungeonManager.getPlayerDungeon(player) ?: return

        if (!AffixManager.hasAffix(instance, AffixType.NO_HEAL)) return

        // 只拦截自然回复和饱食回复，不拦截药水/技能回复
        if (event.regainReason == EntityRegainHealthEvent.RegainReason.SATIATED ||
            event.regainReason == EntityRegainHealthEvent.RegainReason.REGEN) {
            event.isCancelled = true
        }
    }
}
