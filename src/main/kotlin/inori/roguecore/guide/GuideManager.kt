package inori.roguecore.guide

import inori.roguecore.balance.BalanceConfigManager
import inori.roguecore.data.DatabaseManager
import org.bukkit.entity.Player

object GuideManager {

    const val UNIDENTIFIED_LOOT = "unidentified_loot"
    const val FORGE_BOOK = "forge_book"
    const val ACCESSORY = "accessory"
    const val SEALED_ACCESSORY = "sealed_accessory"
    const val ACCESSORY_INSCRIPTION = "accessory_inscription"
    const val COLLECTION_READY = "collection_ready"
    const val SALVAGE = "salvage"

    fun showOnce(player: Player, key: String, lines: List<String>) {
        if (!BalanceConfigManager.getBoolean("guide.enabled", true)) return
        val showOnce = BalanceConfigManager.getBoolean("guide.show-once", true)
        if (showOnce && hasSeen(player, key)) return
        if (showOnce) markSeen(player, key)
        player.sendMessage("§6===== RogueCore 提示 =====")
        for (line in lines) {
            player.sendMessage(line)
        }
        player.sendMessage("§7更多说明可输入 §f/rogue guide")
    }

    fun hasSeen(player: Player, key: String): Boolean {
        return DatabaseManager.getOrCreateContainer(player.uniqueId)[dataKey(key)] == "true"
    }

    fun markSeen(player: Player, key: String) {
        DatabaseManager.getOrCreateContainer(player.uniqueId)[dataKey(key)] = "true"
    }

    private fun dataKey(key: String): String = "guide.seen.$key"
}
