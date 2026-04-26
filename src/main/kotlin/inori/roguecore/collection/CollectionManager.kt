package inori.roguecore.collection

import inori.roguecore.accessory.AccessoryItemCodec
import inori.roguecore.balance.BalanceConfigManager
import inori.roguecore.accessory.AccessorySlot
import inori.roguecore.data.DatabaseManager
import inori.roguecore.guide.GuideManager
import inori.roguecore.data.PermanentMaterialManager
import inori.roguecore.data.PlayerDataManager
import inori.roguecore.item.DungeonLootManager
import inori.roguecore.summary.RunSummaryManager
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

object CollectionManager {

    private val gearThemes = listOf(
        "crypt", "nether", "end", "frost", "forge", "astral", "crimson", "core", "rotwood", "stormspire",
        "sunken", "bonewaste", "mirror", "mire", "dune", "theater", "machine", "eclipse", "chaos", "throne"
    )
    private val themeNames = mapOf(
        "crypt" to "墓窟", "nether" to "熔狱", "end" to "逐界", "frost" to "冰封深窟", "forge" to "黑曜熔炉",
        "astral" to "星界残垣", "crimson" to "猩红王庭", "core" to "永夜核心", "rotwood" to "腐化林海", "stormspire" to "雷鸣高塔",
        "sunken" to "沉没神殿", "bonewaste" to "白骨荒原", "mirror" to "镜影迷宫", "mire" to "毒雾沼泽", "dune" to "黄金沙丘",
        "theater" to "灵魂剧场", "machine" to "机械圣所", "eclipse" to "月蚀庭院", "chaos" to "混沌裂隙", "throne" to "终焉王座"
    )
    private val accessorySlots = listOf(AccessorySlot.NECKLACE, AccessorySlot.RING_1, AccessorySlot.RING_2, AccessorySlot.CHARM, AccessorySlot.TROPHY)

    data class Progress(
        val gearCollected: Int,
        val gearTotal: Int,
        val accessoryCollected: Int,
        val accessoryTotal: Int,
        val bossCollected: Int,
        val bossTotal: Int
    )

    data class SubmitResult(val success: Boolean, val message: String)

    fun getProgress(player: Player): Progress {
        return Progress(
            gearCollected = gearThemes.count { isGearCollected(player, it) },
            gearTotal = gearThemes.size,
            accessoryCollected = accessorySlots.count { isAccessoryCollected(player, it) },
            accessoryTotal = accessorySlots.size,
            bossCollected = (1..100).count { isBossCollected(player, it) },
            bossTotal = 100
        )
    }

    fun getGearThemes(): List<String> = gearThemes
    fun getThemeName(theme: String): String = themeNames[theme] ?: theme
    fun getAccessorySlots(): List<AccessorySlot> = accessorySlots

    fun isGearCollected(player: Player, theme: String): Boolean = getFlag(player, gearKey(theme))
    fun isAccessoryCollected(player: Player, slot: AccessorySlot): Boolean = getFlag(player, accessoryKey(slot))
    fun isBossCollected(player: Player, floor: Int): Boolean = getFlag(player, bossKey(floor))

    fun submitFromInventory(player: Player, slot: Int): SubmitResult {
        GuideManager.showOnce(player, GuideManager.COLLECTION_READY, listOf(
            "§e高品质物品可以提交收藏馆。",
            "§7史诗以上装备和传说饰品可在 §6/rogue progress collection §7中提交，换取长期奖励。"
        ))
        val item = player.inventory.getItem(slot) ?: return SubmitResult(false, "§c该槽位没有物品。")
        if (item.type == Material.AIR) return SubmitResult(false, "§c该槽位没有物品。")
        val gear = trySubmitGear(player, item)
        if (gear != null) {
            if (gear.success) player.inventory.setItem(slot, null)
            return gear
        }
        val accessory = trySubmitAccessory(player, item)
        if (accessory != null) {
            if (accessory.success) player.inventory.setItem(slot, null)
            return accessory
        }
        return SubmitResult(false, "§c只能提交史诗以上 RogueCore 装备或传说饰品。")
    }

    fun recordBossKill(player: Player, floor: Int) {
        val safeFloor = floor.coerceIn(1, 100)
        if (isBossCollected(player, safeFloor)) return
        setFlag(player, bossKey(safeFloor))
        val soul = BalanceConfigManager.getInt("collection.boss.soul-base", 150).coerceAtLeast(0) + safeFloor * BalanceConfigManager.getInt("collection.boss.soul-per-floor", 20).coerceAtLeast(0)
        PlayerDataManager.addSoulShards(player.uniqueId, soul)
        val materials = bossRewards(safeFloor)
        PermanentMaterialManager.addAll(player, materials)
        RunSummaryManager.onBossFirstKill(player.uniqueId, safeFloor)
        player.sendMessage("§6收藏馆点亮: §e第 ${safeFloor} 层 Boss 首杀 §7奖励 §e$soul §7灵魂碎片${formatMaterialsSuffix(materials)}")
    }

    private fun trySubmitGear(player: Player, item: ItemStack): SubmitResult? {
        if (!DungeonLootManager.isLootItem(item)) return null
        if (!DungeonLootManager.isAtLeastRarity(item, "epic")) return SubmitResult(false, "§c装备收藏需要史诗及以上品质。")
        if (DungeonLootManager.isPermanentLoot(item) && !DungeonLootManager.isPermanentLootOwnedBy(item, player)) {
            return SubmitResult(false, "§c这件永久装备不属于你，不能收藏。")
        }
        val theme = DungeonLootManager.getLootTheme(item) ?: return SubmitResult(false, "§c这件装备没有主题，不能加入主题收藏。")
        if (isGearCollected(player, theme)) return SubmitResult(false, "§7${getThemeName(theme)} 装备主题已经点亮。")
        setFlag(player, gearKey(theme))
        val minFloor = DungeonLootManager.getDefinition(item)?.minFloor ?: 1
        val soul = BalanceConfigManager.getInt("collection.gear.soul-base", 300).coerceAtLeast(0) + minFloor * BalanceConfigManager.getInt("collection.gear.soul-per-floor", 25).coerceAtLeast(0)
        val materials = gearRewards(minFloor)
        PlayerDataManager.addSoulShards(player.uniqueId, soul)
        PermanentMaterialManager.addAll(player, materials)
        RunSummaryManager.onCollectionUnlocked(player.uniqueId, "${getThemeName(theme)} 装备主题")
        return SubmitResult(true, "§6收藏馆点亮: §e${getThemeName(theme)} 装备主题 §7奖励 §e$soul §7灵魂碎片${formatMaterialsSuffix(materials)}")
    }

    private fun trySubmitAccessory(player: Player, item: ItemStack): SubmitResult? {
        val instance = AccessoryItemCodec.parse(item) ?: return null
        if (!instance.rarity.id.equals("legendary", ignoreCase = true)) {
            return SubmitResult(false, "§c饰品槽位收藏需要传说饰品。")
        }
        val slot = normalizeSlot(instance.definition.slot)
        if (isAccessoryCollected(player, slot)) return SubmitResult(false, "§7${slot.displayName} 饰品收藏已经点亮。")
        setFlag(player, accessoryKey(slot))
        val soul = BalanceConfigManager.getInt("collection.accessory.soul", 1000).coerceAtLeast(0)
        val materials = accessoryRewards(slot)
        PlayerDataManager.addSoulShards(player.uniqueId, soul)
        PermanentMaterialManager.addAll(player, materials)
        RunSummaryManager.onCollectionUnlocked(player.uniqueId, "${slot.displayName} 饰品收藏")
        return SubmitResult(true, "§6收藏馆点亮: §d${slot.displayName} 饰品收藏 §7奖励 §e$soul §7灵魂碎片${formatMaterialsSuffix(materials)}")
    }

    private fun normalizeSlot(slot: AccessorySlot): AccessorySlot {
        return if (slot == AccessorySlot.RING) AccessorySlot.RING_1 else slot
    }

    private fun gearRewards(minFloor: Int): Map<PermanentMaterialManager.MaterialType, Int> {
        val tier = when {
            minFloor >= 91 -> "end"
            minFloor >= 61 -> "high"
            minFloor >= 31 -> "mid"
            else -> "low"
        }
        return BalanceConfigManager.getMaterialMap("collection.gear.rewards.$tier", defaultGearRewards(tier))
    }

    private fun accessoryRewards(slot: AccessorySlot): Map<PermanentMaterialManager.MaterialType, Int> {
        return BalanceConfigManager.getMaterialMap("collection.accessory.rewards.${slot.name}", defaultAccessoryRewards(slot))
    }

    private fun bossRewards(floor: Int): Map<PermanentMaterialManager.MaterialType, Int> {
        val tier = when {
            floor % 10 == 0 && floor >= 90 -> "end-milestone"
            floor % 10 == 0 -> "milestone"
            floor >= 60 -> "high"
            floor >= 30 -> "mid"
            else -> "low"
        }
        return BalanceConfigManager.getMaterialMap("collection.boss.rewards.$tier", defaultBossRewards(tier))
    }

    private fun defaultGearRewards(tier: String): Map<PermanentMaterialManager.MaterialType, Int> {
        return when (tier) {
            "end" -> mapOf(PermanentMaterialManager.MaterialType.ASTRAL_CORE to 2, PermanentMaterialManager.MaterialType.CROWN_SHARD to 4)
            "high" -> mapOf(PermanentMaterialManager.MaterialType.CROWN_SHARD to 3, PermanentMaterialManager.MaterialType.RELIC_FRAGMENT to 4)
            "mid" -> mapOf(PermanentMaterialManager.MaterialType.RELIC_FRAGMENT to 4, PermanentMaterialManager.MaterialType.INSCRIPTION_DUST to 10)
            else -> mapOf(PermanentMaterialManager.MaterialType.SOUL_IRON to 8, PermanentMaterialManager.MaterialType.INSCRIPTION_DUST to 12)
        }
    }

    private fun defaultAccessoryRewards(slot: AccessorySlot): Map<PermanentMaterialManager.MaterialType, Int> {
        return when (slot) {
            AccessorySlot.NECKLACE -> mapOf(PermanentMaterialManager.MaterialType.RELIC_FRAGMENT to 6, PermanentMaterialManager.MaterialType.CROWN_SHARD to 2)
            AccessorySlot.RING, AccessorySlot.RING_1 -> mapOf(PermanentMaterialManager.MaterialType.INSCRIPTION_DUST to 24, PermanentMaterialManager.MaterialType.RELIC_FRAGMENT to 4)
            AccessorySlot.RING_2 -> mapOf(PermanentMaterialManager.MaterialType.CROWN_SHARD to 3, PermanentMaterialManager.MaterialType.RELIC_FRAGMENT to 3)
            AccessorySlot.CHARM -> mapOf(PermanentMaterialManager.MaterialType.INSCRIPTION_DUST to 18, PermanentMaterialManager.MaterialType.CROWN_SHARD to 2)
            AccessorySlot.TROPHY -> mapOf(PermanentMaterialManager.MaterialType.ASTRAL_CORE to 1, PermanentMaterialManager.MaterialType.CROWN_SHARD to 5)
        }
    }

    private fun defaultBossRewards(tier: String): Map<PermanentMaterialManager.MaterialType, Int> {
        return when (tier) {
            "end-milestone" -> mapOf(PermanentMaterialManager.MaterialType.ASTRAL_CORE to 2, PermanentMaterialManager.MaterialType.CROWN_SHARD to 5)
            "milestone" -> mapOf(PermanentMaterialManager.MaterialType.CROWN_SHARD to 3, PermanentMaterialManager.MaterialType.RELIC_FRAGMENT to 3)
            "high" -> mapOf(PermanentMaterialManager.MaterialType.RELIC_FRAGMENT to 2)
            "mid" -> mapOf(PermanentMaterialManager.MaterialType.INSCRIPTION_DUST to 6)
            else -> mapOf(PermanentMaterialManager.MaterialType.SOUL_IRON to 4)
        }
    }

    private fun formatMaterialsSuffix(materials: Map<PermanentMaterialManager.MaterialType, Int>): String {
        if (materials.isEmpty()) return ""
        return " §7+ " + PermanentMaterialManager.formatCost(materials)
    }

    private fun getFlag(player: Player, key: String): Boolean {
        return DatabaseManager.getOrCreateContainer(player.uniqueId)[key] == "true"
    }

    private fun setFlag(player: Player, key: String) {
        DatabaseManager.getOrCreateContainer(player.uniqueId)[key] = "true"
    }

    private fun gearKey(theme: String) = "collection.gear.$theme"
    private fun accessoryKey(slot: AccessorySlot) = "collection.accessory.${slot.name.lowercase()}"
    private fun bossKey(floor: Int) = "collection.boss.${floor.coerceIn(1, 100)}"
}
