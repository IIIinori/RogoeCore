package inori.roguecore.relic

import inori.roguecore.dungeon.RunPersistenceManager
import inori.roguecore.milestone.RunMilestoneManager
import inori.roguecore.stats.BalanceStatsManager
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object PlayerRelicData {

    private val playerRelics = ConcurrentHashMap<UUID, MutableList<Relic>>()

    fun getRelics(player: Player): List<Relic> {
        return playerRelics[player.uniqueId]?.toList() ?: emptyList()
    }

    fun hasRelic(player: Player, relicId: String): Boolean {
        return playerRelics[player.uniqueId]?.any { it.id == relicId } == true
    }

    fun addRelic(player: Player, relic: Relic): Boolean {
        val list = playerRelics.getOrPut(player.uniqueId) { mutableListOf() }
        if (list.any { it.id == relic.id }) {
            return false
        }
        list += relic
        RunPersistenceManager.markDirty()
        BalanceStatsManager.recordRelicAcquired(relic)
        player.sendMessage("§d获得遗物 ${relic.rarity.color}${relic.name}§d!")
        RunMilestoneManager.onRelicChanged(player)
        return true
    }

    fun clearRelics(player: Player) {
        if (playerRelics.remove(player.uniqueId) != null) {
            RunPersistenceManager.markDirty()
        }
    }

    fun getRelics(uuid: UUID): List<Relic> {
        return playerRelics[uuid]?.toList() ?: emptyList()
    }

    fun restoreRelics(uuid: UUID, relics: List<Relic>) {
        if (relics.isEmpty()) {
            playerRelics.remove(uuid)
            RunPersistenceManager.markDirty()
            return
        }
        playerRelics[uuid] = relics.toMutableList()
        RunPersistenceManager.markDirty()
    }
}
