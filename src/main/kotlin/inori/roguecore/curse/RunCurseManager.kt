package inori.roguecore.curse

import inori.roguecore.dungeon.DungeonManager
import inori.roguecore.dungeon.RunPersistenceManager
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.entity.Player
import taboolib.common.platform.event.EventPriority
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityRegainHealthEvent
import taboolib.common.platform.event.SubscribeEvent
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 局内契约诅咒管理器。
 *
 * 诅咒只在当前 run 内生效，离开副本后立即清除。
 */
object RunCurseManager {

    @Config("events.yml")
    lateinit var config: Configuration
        private set

    private val playerCurses = ConcurrentHashMap<UUID, MutableSet<RunCurseType>>()
    private val fragileModifiers = ConcurrentHashMap<UUID, AttributeModifier>()

    fun addCurse(player: Player, type: RunCurseType): Boolean {
        val curses = playerCurses.getOrPut(player.uniqueId) { mutableSetOf() }
        if (!curses.add(type)) {
            return false
        }
        when (type) {
            RunCurseType.FRAGILE -> applyFragile(player)
            RunCurseType.WITHERED, RunCurseType.VULNERABLE, RunCurseType.HOLLOW -> Unit
        }
        RunPersistenceManager.markDirty()
        player.sendMessage("§4你背负了契约诅咒: §c${type.displayName}")
        return true
    }

    fun hasCurse(player: Player, type: RunCurseType): Boolean {
        return playerCurses[player.uniqueId]?.contains(type) == true
    }

    fun getCurses(player: Player): Set<RunCurseType> {
        return playerCurses[player.uniqueId]?.toSet() ?: emptySet()
    }

    fun getCurses(uuid: UUID): Set<RunCurseType> {
        return playerCurses[uuid]?.toSet() ?: emptySet()
    }

    fun removeCurse(player: Player, type: RunCurseType): Boolean {
        val curses = playerCurses[player.uniqueId] ?: return false
        if (!curses.remove(type)) {
            return false
        }
        when (type) {
            RunCurseType.FRAGILE -> removeFragile(player)
            RunCurseType.WITHERED, RunCurseType.VULNERABLE, RunCurseType.HOLLOW -> Unit
        }
        if (curses.isEmpty()) {
            playerCurses.remove(player.uniqueId)
        }
        RunPersistenceManager.markDirty()
        player.sendMessage("§a一条契约诅咒已被净化: §f${type.displayName}")
        return true
    }

    fun clear(player: Player) {
        val removed = playerCurses.remove(player.uniqueId) ?: return
        if (RunCurseType.FRAGILE in removed) {
            removeFragile(player)
        }
        RunPersistenceManager.markDirty()
    }

    fun restore(uuid: UUID, curses: Set<RunCurseType>) {
        if (curses.isEmpty()) {
            playerCurses.remove(uuid)
            fragileModifiers.remove(uuid)
            RunPersistenceManager.markDirty()
            return
        }
        playerCurses[uuid] = curses.toMutableSet()
        fragileModifiers.remove(uuid)
        RunPersistenceManager.markDirty()
    }

    fun reapply(player: Player) {
        if (hasCurse(player, RunCurseType.FRAGILE)) {
            applyFragile(player)
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onDamage(event: EntityDamageEvent) {
        val player = event.entity as? Player ?: return
        if (!DungeonManager.isInDungeon(player)) {
            return
        }
        if (!hasCurse(player, RunCurseType.VULNERABLE)) {
            return
        }
        val multiplier = config.getDouble("contract.vulnerable-damage-multiplier", 1.25).coerceAtLeast(1.0)
        event.damage *= multiplier
    }

    @SubscribeEvent(ignoreCancelled = true)
    fun onAttack(event: EntityDamageByEntityEvent) {
        val player = when (val damager = event.damager) {
            is Player -> damager
            is org.bukkit.entity.Projectile -> damager.shooter as? Player
            else -> null
        } ?: return
        if (!DungeonManager.isInDungeon(player)) {
            return
        }
        if (!hasCurse(player, RunCurseType.HOLLOW)) {
            return
        }
        val multiplier = config.getDouble("contract.hollow-damage-multiplier", 0.8).coerceIn(0.0, 1.0)
        event.damage *= multiplier
    }

    @SubscribeEvent(ignoreCancelled = true)
    fun onHeal(event: EntityRegainHealthEvent) {
        val player = event.entity as? Player ?: return
        if (!DungeonManager.isInDungeon(player)) {
            return
        }
        if (!hasCurse(player, RunCurseType.WITHERED)) {
            return
        }
        val multiplier = config.getDouble("contract.withered-heal-multiplier", 0.5).coerceIn(0.0, 1.0)
        event.amount *= multiplier
    }

    private fun applyFragile(player: Player) {
        val attribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH) ?: return
        removeFragile(player)
        val penalty = config.getDouble("contract.fragile-health-penalty", 6.0).coerceAtLeast(1.0)
        val modifier = AttributeModifier(
            UUID.randomUUID(),
            "rogue_contract_fragile",
            -penalty,
            AttributeModifier.Operation.ADD_NUMBER
        )
        attribute.addModifier(modifier)
        fragileModifiers[player.uniqueId] = modifier
        val maxHealth = attribute.value.coerceAtLeast(1.0)
        if (player.health > maxHealth) {
            player.health = maxHealth
        }
    }

    private fun removeFragile(player: Player) {
        val modifier = fragileModifiers.remove(player.uniqueId) ?: return
        val attribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH) ?: return
        attribute.removeModifier(modifier)
        val maxHealth = attribute.value.coerceAtLeast(1.0)
        if (player.health > maxHealth) {
            player.health = maxHealth
        }
    }
}
