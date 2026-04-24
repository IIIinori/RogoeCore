package inori.roguecore.unlock

import inori.roguecore.data.DatabaseManager
import inori.roguecore.data.PlayerDataManager
import inori.roguecore.dungeon.route.NextFloorRoute
import inori.roguecore.dungeon.room.RoomType
import org.bukkit.entity.Player
import java.util.UUID

object UnlockManager {

    private const val UNLOCK_PREFIX = "unlock."

    fun unlockKey(id: String): String {
        return "$UNLOCK_PREFIX$id"
    }

    fun hasUnlock(uuid: UUID, id: String): Boolean {
        val container = DatabaseManager.getOrCreateContainer(uuid)
        return container[unlockKey(id)] == "true"
    }

    fun hasUnlock(player: Player, id: String): Boolean {
        return hasUnlock(player.uniqueId, id)
    }

    fun getUnlockedIds(uuid: UUID): Set<String> {
        val container = DatabaseManager.getOrCreateContainer(uuid)
        return container.values()
            .filterKeys { it.startsWith(UNLOCK_PREFIX) }
            .filterValues { it == "true" }
            .keys
            .mapTo(mutableSetOf()) { it.removePrefix(UNLOCK_PREFIX) }
    }

    fun canPurchase(player: Player, definition: UnlockDefinition): Boolean {
        if (hasUnlock(player, definition.id)) {
            return false
        }
        val data = PlayerDataManager.get(player.uniqueId)
        if (data.bestFloor < definition.requiredBestFloor) {
            return false
        }
        if (definition.requires.any { !hasUnlock(player, it) }) {
            return false
        }
        return data.soulShards >= definition.cost
    }

    fun purchase(player: Player, unlockId: String): Boolean {
        val definition = UnlockRegistry.get(unlockId) ?: return false
        if (hasUnlock(player, unlockId)) {
            player.sendMessage("§7该研究已经完成。")
            return false
        }

        val data = PlayerDataManager.get(player.uniqueId)
        if (data.bestFloor < definition.requiredBestFloor) {
            player.sendMessage("§c需要先到达 §e${definition.requiredBestFloor} §c层。")
            return false
        }
        val missing = definition.requires.firstOrNull { !hasUnlock(player, it) }
        if (missing != null) {
            val required = UnlockRegistry.get(missing)?.name ?: missing
            player.sendMessage("§c需要先完成前置研究: §e$required")
            return false
        }
        if (!PlayerDataManager.takeSoulShards(player.uniqueId, definition.cost)) {
            player.sendMessage("§c灵魂碎片不足! 需要 §e${definition.cost} §c碎片")
            return false
        }

        DatabaseManager.getOrCreateContainer(player.uniqueId)[unlockKey(unlockId)] = "true"
        player.sendMessage("§a已解锁研究: §f${definition.name}")
        return true
    }

    fun getRelicOfferBonus(player: Player): Int {
        return if (hasUnlock(player, "relic_transmutation")) 1 else 0
    }

    fun hasSoulTempering(player: Player): Boolean {
        return hasUnlock(player, "soul_tempering")
    }

    fun hasPrecisionLocking(player: Player): Boolean {
        return hasUnlock(player, "precision_locking")
    }

    fun hasEmberCooling(player: Player): Boolean {
        return hasUnlock(player, "ember_cooling")
    }

    fun hasRefinedReforge(player: Player): Boolean {
        return hasUnlock(player, "refined_reforge")
    }

    fun hasSteadyForging(player: Player): Boolean {
        return hasUnlock(player, "steady_forging")
    }

    fun hasVolcanicTempering(player: Player): Boolean {
        return hasUnlock(player, "volcanic_tempering")
    }

    fun hasInscriptionMastery(player: Player): Boolean {
        return hasUnlock(player, "inscription_mastery")
    }

    fun getReforgeHeatModifier(player: Player): Int {
        return if (hasSteadyForging(player)) -1 else 0
    }

    fun getCoolingHeatBonus(player: Player): Int {
        return if (hasSteadyForging(player)) 1 else 0
    }

    fun getTemperLevelBonus(player: Player): Int {
        return if (hasVolcanicTempering(player)) 1 else 0
    }

    fun getLockShardDiscount(player: Player): Int {
        return if (hasInscriptionMastery(player)) 2 else 0
    }

    fun getRefinedReforgeShardDiscount(player: Player): Int {
        return if (hasInscriptionMastery(player)) 8 else 0
    }

    fun getRefinedReforgeSigilDiscount(player: Player): Int {
        return if (hasInscriptionMastery(player)) 1 else 0
    }

    fun hasSealedVault(player: Player): Boolean {
        return hasUnlock(player, "sealed_vault")
    }

    fun hasAbyssalBargain(player: Player): Boolean {
        return hasUnlock(player, "abyssal_bargain")
    }

    fun hasArcaneExchange(player: Player): Boolean {
        return hasUnlock(player, "arcane_exchange")
    }

    fun hasForbiddenTrials(player: Player): Boolean {
        return hasUnlock(player, "forbidden_trials")
    }

    fun hasSanctifiedPrayer(player: Player): Boolean {
        return hasUnlock(player, "sanctified_prayer")
    }

    fun getRouteWeightBonus(uuid: UUID, route: NextFloorRoute): Map<RoomType, Int> {
        return when (route) {
            NextFloorRoute.BATTLE -> if (hasUnlock(uuid, "battle_doctrine")) {
                mapOf(RoomType.COMBAT to 10, RoomType.ELITE to 5, RoomType.CHEST to 2)
            } else emptyMap()

            NextFloorRoute.OPPORTUNITY -> if (hasUnlock(uuid, "black_market_network")) {
                mapOf(RoomType.SHOP to 5, RoomType.FORGE to 5, RoomType.CONTRACT to 4, RoomType.SHRINE to 2)
            } else emptyMap()

            NextFloorRoute.TREASURE -> if (hasUnlock(uuid, "treasure_cipher")) {
                mapOf(RoomType.CHEST to 8, RoomType.GAMBLE to 3, RoomType.REST to 2)
            } else emptyMap()

            NextFloorRoute.EXTREME -> emptyMap()
        }
    }

    fun getRouteHiddenBonus(uuid: UUID, route: NextFloorRoute): Double {
        return when (route) {
            NextFloorRoute.TREASURE -> if (hasUnlock(uuid, "treasure_cipher")) 0.2 else 0.0
            else -> 0.0
        }
    }
}
