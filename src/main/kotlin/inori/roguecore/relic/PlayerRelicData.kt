package inori.roguecore.relic

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
        player.sendMessage("§d获得遗物 ${relic.rarity.color}${relic.name}§d!")
        return true
    }

    fun clearRelics(player: Player) {
        playerRelics.remove(player.uniqueId)
    }
}
