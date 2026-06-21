package inori.roguecore.salvage

import inori.roguecore.accessory.AccessoryItemCodec
import inori.roguecore.balance.BalanceConfigManager
import inori.roguecore.data.PermanentMaterialManager
import inori.roguecore.data.PlayerDataManager
import inori.roguecore.data.ShardRewardManager
import inori.roguecore.display.ContentDisplayNameResolver
import inori.roguecore.item.DungeonBoundItem
import inori.roguecore.item.DungeonLootManager
import inori.roguecore.item.DungeonLootSource
import inori.roguecore.summary.RunSummaryManager
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import kotlin.math.roundToInt

object SalvageManager {

    enum class SalvageCategory(val displayName: String) {
        TEMPORARY_LOOT("临时装备"),
        PERMANENT_LOOT("永久装备"),
        UNIDENTIFIED_LOOT("未鉴定装备"),
        FORGE_BOOK("锻造书"),
        ACCESSORY("饰品"),
        SEALED_ACCESSORY("密封饰品"),
        ACCESSORY_INSCRIPTION("饰品刻印书")
    }

    data class SalvageRewards(
        val runShards: Int = 0,
        val soulShards: Int = 0,
        val materials: Map<PermanentMaterialManager.MaterialType, Int> = emptyMap()
    ) {
        fun isEmpty(): Boolean = runShards <= 0 && soulShards <= 0 && materials.values.all { it <= 0 }
    }

    data class SalvagePreview(
        val inventorySlot: Int,
        val item: ItemStack,
        val category: SalvageCategory,
        val displayName: String,
        val rewards: SalvageRewards,
        val autoEligible: Boolean,
        val blockedReason: String = ""
    ) {
        val blocked: Boolean get() = blockedReason.isNotBlank()
    }

    data class SalvageResult(
        val success: Boolean,
        val message: String,
        val rewards: SalvageRewards = SalvageRewards(),
        val count: Int = 0
    )

    fun scanInventory(player: Player): List<SalvagePreview> {
        return player.inventory.storageContents.mapIndexedNotNull { slot, item ->
            preview(player, slot, item)
        }
    }

    fun preview(player: Player, slot: Int, item: ItemStack?): SalvagePreview? {
        if (item == null || item.type == Material.AIR) return null
        return when {
            DungeonLootManager.isUnidentifiedLoot(item) -> previewUnidentified(slot, item)
            DungeonLootManager.isForgeBook(item) -> previewForgeBook(slot, item)
            DungeonLootManager.isLootItem(item) -> previewLoot(player, slot, item)
            AccessoryItemCodec.isAccessory(item) -> previewAccessory(slot, item)
            AccessoryItemCodec.isSealedAccessory(item) -> previewSealedAccessory(slot, item)
            AccessoryItemCodec.isInscriptionBook(item) -> previewAccessoryInscription(slot, item)
            else -> null
        }
    }

    fun salvageSlot(player: Player, slot: Int): SalvageResult {
        val item = player.inventory.getItem(slot) ?: return SalvageResult(false, "§c该槽位没有可分解物品。")
        val preview = preview(player, slot, item) ?: return SalvageResult(false, "§c这不是 RogueCore 可分解物品。")
        if (preview.blocked) return SalvageResult(false, preview.blockedReason)
        player.inventory.setItem(slot, null)
        grant(player, preview.rewards)
        RunSummaryManager.onSalvaged(player.uniqueId, 1, preview.rewards.runShards, preview.rewards.soulShards, preview.rewards.materials)
        return SalvageResult(true, "§a已分解 §f${preview.displayName} §a获得: ${formatRewards(preview.rewards)}", preview.rewards, 1)
    }

    fun salvageSameCategory(player: Player, category: SalvageCategory): SalvageResult {
        val previews = scanInventory(player).filter { it.category == category && !it.blocked }
        return salvagePreviews(player, previews, "§a已分解 §e${previews.size} §a件${category.displayName}，获得: ")
    }

    fun salvageAuto(player: Player): SalvageResult {
        val previews = scanInventory(player).filter { it.autoEligible && !it.blocked }
        return salvagePreviews(player, previews, "§a已一键分解 §e${previews.size} §a件低价值物品，获得: ")
    }

    private fun salvagePreviews(player: Player, previews: List<SalvagePreview>, prefix: String): SalvageResult {
        if (previews.isEmpty()) return SalvageResult(false, "§7没有符合条件的可分解物品。")
        val bySlot = previews.associateBy { it.inventorySlot }
        val actual = mutableListOf<SalvagePreview>()
        for ((slot, preview) in bySlot) {
            val current = player.inventory.getItem(slot)
            val fresh = preview(player, slot, current)
            if (fresh != null && !fresh.blocked && fresh.category == preview.category) {
                actual += fresh
            }
        }
        if (actual.isEmpty()) return SalvageResult(false, "§7没有符合条件的可分解物品。")
        for (preview in actual) {
            player.inventory.setItem(preview.inventorySlot, null)
        }
        val rewards = combine(actual.map { it.rewards })
        grant(player, rewards)
        RunSummaryManager.onSalvaged(player.uniqueId, actual.size, rewards.runShards, rewards.soulShards, rewards.materials)
        return SalvageResult(true, prefix + formatRewards(rewards), rewards, actual.size)
    }

    private fun previewLoot(player: Player, slot: Int, item: ItemStack): SalvagePreview {
        val definition = DungeonLootManager.getDefinition(item)
        val rarityId = DungeonLootManager.getLootRarityId(item) ?: "common"
        val rarityName = DungeonLootManager.getLootRarityName(item) ?: "未知"
        val score = DungeonLootManager.getScore(item)
        val floor = DungeonLootManager.getLootFloor(item)
        val name = item.itemMeta?.displayName ?: definition?.name ?: ContentDisplayNameResolver.materialTypeName(item.type.name, "装备")
        if (DungeonLootManager.isPermanentLoot(item)) {
            val blocked = when {
                DungeonLootManager.isFavorite(item) -> "§c已收藏的永久装备不能分解。"
                !DungeonLootManager.isPermanentLootOwnedBy(item, player) -> "§c这件永久装备不属于你，不能分解。"
                else -> ""
            }
            val materials = mutableMapOf<PermanentMaterialManager.MaterialType, Int>()
            val salvageScoreDivisor = BalanceConfigManager.getDouble("salvage.permanent-loot.salvage-score-divisor", 900.0).coerceAtLeast(1.0)
            val forgeLevelSoulIron = BalanceConfigManager.getInt("salvage.permanent-loot.forge-level-soul-iron", 2).coerceAtLeast(0)
            val scoreDustDivisor = BalanceConfigManager.getDouble("salvage.permanent-loot.score-dust-divisor", 500.0).coerceAtLeast(1.0)
            addAll(materials, PermanentMaterialManager.rollSalvageRewards(rarityId, (score / salvageScoreDivisor).roundToInt().coerceAtLeast(0)))
            add(materials, PermanentMaterialManager.MaterialType.SOUL_IRON, DungeonLootManager.getForgeLevel(item) * forgeLevelSoulIron)
            add(materials, PermanentMaterialManager.MaterialType.INSCRIPTION_DUST, (score / scoreDustDivisor).roundToInt().coerceAtLeast(0))
            return SalvagePreview(slot, item.clone(), SalvageCategory.PERMANENT_LOOT, name, SalvageRewards(materials = materials), autoEligible = false, blockedReason = blocked)
        }
        val materials = mutableMapOf<PermanentMaterialManager.MaterialType, Int>()
        val soulIronBase = BalanceConfigManager.getInt("salvage.temporary-loot.soul-iron-base", 1).coerceAtLeast(0)
        val soulIronFloorDivisor = BalanceConfigManager.getInt("salvage.temporary-loot.soul-iron-floor-divisor", 20).coerceAtLeast(1)
        val runShardBase = BalanceConfigManager.getInt("salvage.temporary-loot.run-shards-base", 20).coerceAtLeast(0)
        val runShardPerFloor = BalanceConfigManager.getInt("salvage.temporary-loot.run-shards-per-floor", 4).coerceAtLeast(0)
        val scoreDivisor = BalanceConfigManager.getDouble("salvage.temporary-loot.score-divisor", 80.0).coerceAtLeast(1.0)
        add(materials, PermanentMaterialManager.MaterialType.SOUL_IRON, soulIronBase + floor / soulIronFloorDivisor)
        add(materials, PermanentMaterialManager.MaterialType.INSCRIPTION_DUST, rarityDust(rarityId))
        val runShards = (runShardBase + floor * runShardPerFloor + score / scoreDivisor).roundToInt().coerceAtLeast(1)
        val auto = DungeonBoundItem.hasTag(item)
        return SalvagePreview(slot, item.clone(), SalvageCategory.TEMPORARY_LOOT, "$name §8($rarityName)", SalvageRewards(runShards = runShards, materials = materials), autoEligible = auto)
    }

    private fun previewUnidentified(slot: Int, item: ItemStack): SalvagePreview {
        val info = DungeonLootManager.getUnidentifiedLootInfo(item)
        val floor = info?.floor ?: 1
        val bonusValue = BalanceConfigManager.getInt("salvage.unidentified.boss-hidden-bonus", 2).coerceAtLeast(0)
        val bonus = if (info?.source == DungeonLootSource.BOSS || info?.source == DungeonLootSource.HIDDEN) bonusValue else 0
        val base = BalanceConfigManager.getInt("salvage.unidentified.dust-base", 2).coerceAtLeast(0)
        val divisor = BalanceConfigManager.getInt("salvage.unidentified.floor-divisor", 15).coerceAtLeast(1)
        val materials = mapOf(PermanentMaterialManager.MaterialType.INSCRIPTION_DUST to (base + floor / divisor + bonus).coerceAtLeast(1))
        return SalvagePreview(slot, item.clone(), SalvageCategory.UNIDENTIFIED_LOOT, item.itemMeta?.displayName ?: "未鉴定装备", SalvageRewards(materials = materials), autoEligible = true)
    }

    private fun previewForgeBook(slot: Int, item: ItemStack): SalvagePreview {
        val info = DungeonLootManager.getForgeBookInfo(item)
        val qualityId = info?.quality?.id ?: "rough"
        val rewards = SalvageRewards(materials = BalanceConfigManager.getMaterialMap("salvage.forge-book.$qualityId", defaultForgeBookRewards(qualityId)))
        val auto = info?.quality?.id in setOf("rough", "refined")
        return SalvagePreview(slot, item.clone(), SalvageCategory.FORGE_BOOK, item.itemMeta?.displayName ?: "锻造书", rewards, autoEligible = auto)
    }

    private fun previewAccessory(slot: Int, item: ItemStack): SalvagePreview {
        val instance = AccessoryItemCodec.parse(item)
        val rarityId = instance?.rarity?.id ?: "common"
        val floor = instance?.floor ?: 1
        val score = instance?.score ?: 0.0
        val materials = mutableMapOf<PermanentMaterialManager.MaterialType, Int>()
        when (rarityId) {
            "legendary" -> {
                add(materials, PermanentMaterialManager.MaterialType.CROWN_SHARD, balanceInt("salvage.accessory.legendary.crown_shard", 3) + floor / balanceDivisor("salvage.accessory.legendary.crown_shard_floor_divisor", 25))
                add(materials, PermanentMaterialManager.MaterialType.ASTRAL_CORE, balanceInt("salvage.accessory.legendary.astral_core", 1) + (score / balanceDoubleDivisor("salvage.accessory.legendary.astral_core_score_divisor", 120000.0)).roundToInt().coerceAtLeast(0))
            }
            "epic" -> {
                add(materials, PermanentMaterialManager.MaterialType.RELIC_FRAGMENT, balanceInt("salvage.accessory.epic.relic_fragment", 3) + floor / balanceDivisor("salvage.accessory.epic.relic_fragment_floor_divisor", 20))
                add(materials, PermanentMaterialManager.MaterialType.CROWN_SHARD, balanceInt("salvage.accessory.epic.crown_shard", 1) + (score / balanceDoubleDivisor("salvage.accessory.epic.crown_shard_score_divisor", 80000.0)).roundToInt().coerceAtLeast(0))
            }
            "rare" -> {
                add(materials, PermanentMaterialManager.MaterialType.INSCRIPTION_DUST, balanceInt("salvage.accessory.rare.inscription_dust", 5) + floor / balanceDivisor("salvage.accessory.rare.inscription_dust_floor_divisor", 12))
                add(materials, PermanentMaterialManager.MaterialType.RELIC_FRAGMENT, balanceInt("salvage.accessory.rare.relic_fragment", 1) + floor / balanceDivisor("salvage.accessory.rare.relic_fragment_floor_divisor", 35))
            }
            else -> {
                add(materials, PermanentMaterialManager.MaterialType.SOUL_IRON, balanceInt("salvage.accessory.common.soul_iron", 2) + floor / balanceDivisor("salvage.accessory.common.soul_iron_floor_divisor", 15))
                add(materials, PermanentMaterialManager.MaterialType.INSCRIPTION_DUST, balanceInt("salvage.accessory.common.inscription_dust", 3) + floor / balanceDivisor("salvage.accessory.common.inscription_dust_floor_divisor", 10))
            }
        }
        val auto = rarityId in setOf("common", "rare")
        return SalvagePreview(slot, item.clone(), SalvageCategory.ACCESSORY, item.itemMeta?.displayName ?: instance?.definition?.name ?: "饰品", SalvageRewards(materials = materials), autoEligible = auto)
    }

    private fun previewSealedAccessory(slot: Int, item: ItemStack): SalvagePreview {
        val info = AccessoryItemCodec.parseSealedAccessory(item)
        val floor = info?.floor ?: 1
        val bonusValue = BalanceConfigManager.getInt("salvage.sealed-accessory.boss-hidden-bonus", 2).coerceAtLeast(0)
        val bonus = if (info?.source == DungeonLootSource.BOSS || info?.source == DungeonLootSource.HIDDEN) bonusValue else 0
        val base = BalanceConfigManager.getInt("salvage.sealed-accessory.dust-base", 2).coerceAtLeast(0)
        val divisor = BalanceConfigManager.getInt("salvage.sealed-accessory.floor-divisor", 12).coerceAtLeast(1)
        val materials = mapOf(PermanentMaterialManager.MaterialType.INSCRIPTION_DUST to (base + floor / divisor + bonus).coerceAtLeast(1))
        return SalvagePreview(slot, item.clone(), SalvageCategory.SEALED_ACCESSORY, item.itemMeta?.displayName ?: "密封饰品", SalvageRewards(materials = materials), autoEligible = true)
    }

    private fun previewAccessoryInscription(slot: Int, item: ItemStack): SalvagePreview {
        val info = AccessoryItemCodec.parseInscriptionBook(item)
        val qualityId = info?.quality?.id ?: "rough"
        val rewards = SalvageRewards(materials = BalanceConfigManager.getMaterialMap("salvage.accessory-inscription.$qualityId", defaultAccessoryInscriptionRewards(qualityId)))
        val auto = info?.quality?.id in setOf("rough", "refined")
        return SalvagePreview(slot, item.clone(), SalvageCategory.ACCESSORY_INSCRIPTION, item.itemMeta?.displayName ?: "饰品刻印书", rewards, autoEligible = auto)
    }

    private fun grant(player: Player, rewards: SalvageRewards) {
        if (rewards.runShards > 0) ShardRewardManager.addRunShards(player.uniqueId, rewards.runShards)
        if (rewards.soulShards > 0) PlayerDataManager.addSoulShards(player.uniqueId, rewards.soulShards)
        if (rewards.materials.isNotEmpty()) PermanentMaterialManager.addAll(player, rewards.materials.filterValues { it > 0 })
    }

    fun formatRewards(rewards: SalvageRewards): String {
        val parts = mutableListOf<String>()
        if (rewards.runShards > 0) parts += "§6本局碎片 x${rewards.runShards}"
        if (rewards.soulShards > 0) parts += "§e灵魂碎片 x${rewards.soulShards}"
        for ((type, amount) in rewards.materials.filterValues { it > 0 }) {
            parts += "${type.coloredName()} §fx$amount"
        }
        return parts.ifEmpty { listOf("§8无奖励") }.joinToString(" §7+ ")
    }

    private fun combine(rewards: List<SalvageRewards>): SalvageRewards {
        val materials = mutableMapOf<PermanentMaterialManager.MaterialType, Int>()
        var runShards = 0
        var soulShards = 0
        for (reward in rewards) {
            runShards += reward.runShards
            soulShards += reward.soulShards
            addAll(materials, reward.materials)
        }
        return SalvageRewards(runShards, soulShards, materials)
    }

    private fun defaultForgeBookRewards(qualityId: String): Map<PermanentMaterialManager.MaterialType, Int> {
        return when (qualityId) {
            "legendary" -> mapOf(PermanentMaterialManager.MaterialType.CROWN_SHARD to 4, PermanentMaterialManager.MaterialType.ASTRAL_CORE to 1)
            "epic" -> mapOf(PermanentMaterialManager.MaterialType.RELIC_FRAGMENT to 4, PermanentMaterialManager.MaterialType.CROWN_SHARD to 1)
            "rare" -> mapOf(PermanentMaterialManager.MaterialType.SOUL_IRON to 8, PermanentMaterialManager.MaterialType.RELIC_FRAGMENT to 1)
            "refined" -> mapOf(PermanentMaterialManager.MaterialType.SOUL_IRON to 4, PermanentMaterialManager.MaterialType.INSCRIPTION_DUST to 3)
            else -> mapOf(PermanentMaterialManager.MaterialType.SOUL_IRON to 2, PermanentMaterialManager.MaterialType.INSCRIPTION_DUST to 1)
        }
    }

    private fun defaultAccessoryInscriptionRewards(qualityId: String): Map<PermanentMaterialManager.MaterialType, Int> {
        return when (qualityId) {
            "legendary" -> mapOf(PermanentMaterialManager.MaterialType.CROWN_SHARD to 3, PermanentMaterialManager.MaterialType.ASTRAL_CORE to 1)
            "epic" -> mapOf(PermanentMaterialManager.MaterialType.RELIC_FRAGMENT to 3, PermanentMaterialManager.MaterialType.CROWN_SHARD to 1)
            "rare" -> mapOf(PermanentMaterialManager.MaterialType.INSCRIPTION_DUST to 10, PermanentMaterialManager.MaterialType.RELIC_FRAGMENT to 1)
            "refined" -> mapOf(PermanentMaterialManager.MaterialType.INSCRIPTION_DUST to 7, PermanentMaterialManager.MaterialType.SOUL_IRON to 2)
            else -> mapOf(PermanentMaterialManager.MaterialType.INSCRIPTION_DUST to 3)
        }
    }

    private fun balanceInt(path: String, default: Int): Int = BalanceConfigManager.getInt(path, default).coerceAtLeast(0)
    private fun balanceDivisor(path: String, default: Int): Int = BalanceConfigManager.getInt(path, default).coerceAtLeast(1)
    private fun balanceDoubleDivisor(path: String, default: Double): Double = BalanceConfigManager.getDouble(path, default).coerceAtLeast(1.0)

    private fun add(map: MutableMap<PermanentMaterialManager.MaterialType, Int>, type: PermanentMaterialManager.MaterialType, amount: Int) {
        if (amount <= 0) return
        map[type] = (map[type] ?: 0) + amount
    }

    private fun addAll(map: MutableMap<PermanentMaterialManager.MaterialType, Int>, values: Map<PermanentMaterialManager.MaterialType, Int>) {
        for ((type, amount) in values) add(map, type, amount)
    }

    private fun rarityDust(rarityId: String): Int {
        return when (rarityId.lowercase()) {
            "legendary" -> BalanceConfigManager.getInt("salvage.temporary-loot.rarity-dust.legendary", 8)
            "epic" -> BalanceConfigManager.getInt("salvage.temporary-loot.rarity-dust.epic", 5)
            "rare" -> BalanceConfigManager.getInt("salvage.temporary-loot.rarity-dust.rare", 3)
            "magic", "refined" -> BalanceConfigManager.getInt("salvage.temporary-loot.rarity-dust.refined", 2)
            else -> BalanceConfigManager.getInt("salvage.temporary-loot.rarity-dust.common", 1)
        }
    }
}
