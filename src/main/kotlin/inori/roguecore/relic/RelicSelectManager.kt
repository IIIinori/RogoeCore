package inori.roguecore.relic

import inori.roguecore.unlock.UnlockManager
import org.bukkit.entity.Player
import kotlin.random.Random

object RelicSelectManager {

    fun offerRelicSelection(player: Player, count: Int): Boolean {
        val selected = rollRelics(player, count)
        if (selected.isEmpty()) {
            player.sendMessage("§7没有可获得的新遗物了。")
            return false
        }
        inori.roguecore.ui.RelicSelectUI.open(player, selected)
        return true
    }

    fun rollRelics(player: Player, count: Int): List<Relic> {
        val pool = RelicRegistry.getAll()
            .filter { relic -> relic.requiredUnlock == null || UnlockManager.hasUnlock(player, relic.requiredUnlock) }
            .filterNot { PlayerRelicData.hasRelic(player, it.id) }
            .toMutableList()
        if (pool.isEmpty()) {
            return emptyList()
        }

        val result = mutableListOf<Relic>()
        repeat(count.coerceAtMost(pool.size)) {
            val relic = weightedRandom(pool) ?: return@repeat
            result += relic
            pool.remove(relic)
        }
        return result
    }

    private fun weightedRandom(pool: List<Relic>): Relic? {
        if (pool.isEmpty()) {
            return null
        }
        val total = pool.sumOf { it.rarity.weight }
        var roll = Random.nextInt(total)
        for (relic in pool) {
            roll -= relic.rarity.weight
            if (roll < 0) {
                return relic
            }
        }
        return pool.last()
    }
}
