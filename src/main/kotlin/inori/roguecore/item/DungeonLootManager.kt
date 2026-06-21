package inori.roguecore.item

import inori.roguecore.affix.AffixManager
import inori.roguecore.balance.BalanceConfigManager
import inori.roguecore.data.PermanentMaterialManager
import inori.roguecore.dungeon.DungeonInstance
import inori.roguecore.dungeon.DungeonManager
import inori.roguecore.display.ContentDisplayNameResolver
import inori.roguecore.guide.GuideManager
import inori.roguecore.relic.RelicEffectHandler
import inori.roguecore.stats.PerfMonitor
import inori.roguecore.summary.RunSummaryManager
import inori.roguecore.unlock.UnlockManager
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.info
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.warning
import taboolib.library.configuration.ConfigurationSection
import taboolib.library.xseries.XMaterial
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration
import java.util.UUID
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * 地牢临时装备掉落管理器。
 *
 * 装备属性以 lore 输出给 AttributePlus 读取，不再写入 Bukkit 原生 AttributeModifier。
 */
object DungeonLootManager {

    private val lootIdKey = NamespacedKey("roguecore", "loot_id")
    private val forgeLevelKey = NamespacedKey("roguecore", "forge_level")
    private val scoreKey = NamespacedKey("roguecore", "loot_score")
    private val rarityKey = NamespacedKey("roguecore", "loot_rarity")
    private val affixKey = NamespacedKey("roguecore", "loot_affixes")
    private val baseLoreKey = NamespacedKey("roguecore", "loot_base_lore")
    private val lockedAffixKey = NamespacedKey("roguecore", "locked_affix")
    private val forgeHeatKey = NamespacedKey("roguecore", "forge_heat")
    private val permanentLootKey = NamespacedKey("roguecore", "permanent_loot")
    private val favoriteKey = NamespacedKey("roguecore", "favorite")
    private val ownerUuidKey = NamespacedKey("roguecore", "owner_uuid")
    private val ownerNameKey = NamespacedKey("roguecore", "owner_name")
    private val unidentifiedKey = NamespacedKey("roguecore", "unidentified_loot")
    private val unidentifiedLootIdKey = NamespacedKey("roguecore", "unidentified_loot_id")
    private val unidentifiedSourceKey = NamespacedKey("roguecore", "unidentified_source")
    private val unidentifiedFloorKey = NamespacedKey("roguecore", "unidentified_floor")
    private val forgeBookKey = NamespacedKey("roguecore", "forge_book")
    private val forgeBookQualityKey = NamespacedKey("roguecore", "forge_book_quality")
    private val forgeBookLootIdKey = NamespacedKey("roguecore", "forge_book_loot_id")
    private val forgeBookSourceKey = NamespacedKey("roguecore", "forge_book_source")
    private val forgeBookFloorKey = NamespacedKey("roguecore", "forge_book_floor")

    @Config("loot.yml")
    lateinit var config: Configuration
        private set

    private val definitions = mutableListOf<DungeonLootDefinition>()
    private val rarities = mutableListOf<DungeonLootRarity>()
    private val affixes = mutableListOf<DungeonLootAffix>()
    private val setBonuses = mutableListOf<DungeonLootSetBonus>()
    private val sourceAffixBonus = mutableMapOf<DungeonLootSource, Int>()
    private val sourceRarityUpgradeChance = mutableMapOf<DungeonLootSource, Double>()
    private val definitionScoreCache = mutableMapOf<String, Double>()

    private var chestChance = 0.4
    private var eliteCount = 1
    private var bossCount = 1
    private var apPercentageMark = "(%)"
    private var apPercentageMarkDisabledAttributes = emptySet<String>()
    private var apPercentageMarkDisabledKeywords = emptyList<String>()
    private var protectionEnabled = true
    private var protectionEmptySlotWeight = 2.1
    private var protectionDuplicatePenalty = 0.72
    private var protectionUpgradeScoreScale = 0.02
    private var protectionMaxUpgradeBonus = 0.75
    private var protectionDowngradePenaltyScale = 0.012
    private var protectionMinFactor = 0.25
    private var permanentGearBindOnConvert = true
    private var identificationEnabled = true
    private var identificationBasePrice = 80
    private var identificationFloorPrice = 6
    private var identificationBossExtraPrice = 120
    private var identificationHiddenExtraPrice = 80
    private val unidentifiedDropChance = mutableMapOf<DungeonLootSource, Double>()
    private val forgeBookDropChance = mutableMapOf<DungeonLootSource, Double>()
    private val forgeBookQualityWeights = mutableMapOf<String, Int>()
    private val forgeBookQualities = linkedMapOf<String, ForgeBookQuality>()
    private var forgeBooksEnabled = true

    data class EquippedLootView(
        val slot: EquipmentSlot,
        val definition: DungeonLootDefinition,
        val item: ItemStack,
        val forgeLevel: Int,
        val forgeHeat: Int,
        val score: Double
    )

    data class LootActionResult(
        val success: Boolean,
        val message: String
    )

    data class AdminGiveItemResult(
        val success: Boolean,
        val item: ItemStack? = null,
        val message: String
    )

    data class UnidentifiedLootInfo(
        val lootId: String,
        val source: DungeonLootSource,
        val floor: Int,
        val price: Int
    )

    data class ForgeBookQuality(
        val id: String,
        val name: String,
        val color: String,
        val rarityId: String,
        val timeMillis: Long,
        val soulShards: Int,
        val materials: Map<PermanentMaterialManager.MaterialType, Int>
    )

    data class ForgeBookInfo(
        val lootId: String,
        val source: DungeonLootSource,
        val floor: Int,
        val quality: ForgeBookQuality
    )

    data class IdentifyClaimResult(
        val success: Boolean,
        val item: ItemStack?,
        val message: String
    )

    data class LootSalvageResult(
        val success: Boolean,
        val reward: Int,
        val message: String
    )

    private data class EquippedDefinitionView(
        val slot: EquipmentSlot,
        val definition: DungeonLootDefinition,
        val item: ItemStack
    )

    private data class ActiveSetBonus(
        val bonus: DungeonLootSetBonus,
        val matchCount: Int,
        val carrierSlot: EquipmentSlot,
        val tiers: List<DungeonLootSetTier>
    )

    private data class RolledLoot(
        val item: ItemStack,
        val rarity: DungeonLootRarity,
        val affixes: List<DungeonLootAffix>,
        val score: Double
    )

    @Awake(LifeCycle.ENABLE)
    fun load() {
        definitions.clear()
        rarities.clear()
        affixes.clear()
        setBonuses.clear()
        sourceAffixBonus.clear()
        sourceRarityUpgradeChance.clear()
        definitionScoreCache.clear()
        unidentifiedDropChance.clear()
        forgeBookDropChance.clear()
        forgeBookQualityWeights.clear()
        forgeBookQualities.clear()

        chestChance = config.getDouble("rules.chest-chance", 0.4)
        eliteCount = config.getInt("rules.elite-count", 1).coerceAtLeast(1)
        bossCount = config.getInt("rules.boss-count", 1).coerceAtLeast(1)
        apPercentageMark = config.getString("rules.ap-percentage-mark") ?: "(%)"
        apPercentageMarkDisabledAttributes = config.getStringList("rules.ap-percentage-mark-disabled-attributes")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()
        apPercentageMarkDisabledKeywords = config.getStringList("rules.ap-percentage-mark-disabled-keywords")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
        loadDropProtectionRules()
        permanentGearBindOnConvert = config.getBoolean("permanent-gear.bind-on-convert", true)
        loadIdentificationRules()
        loadForgeBookRules()
        loadSourceBonuses()

        loadRarities()
        loadSetBonuses()
        loadAffixes()
        loadLootDefinitions()

        info("[RogueCore] 已加载 ${definitions.size} 个临时装备定义, ${rarities.size} 个稀有度, ${affixes.size} 个随机词条, ${setBonuses.size} 个装备联动")
    }

    fun getConfiguredAttributeNames(): Set<String> {
        val names = linkedSetOf<String>()
        definitions.flatMapTo(names) { definition -> definition.attributes.map { it.name } }
        affixes.flatMapTo(names) { affix -> affix.attributes.map { it.name } }
        setBonuses.flatMap { bonus -> bonus.tiers }
            .flatMapTo(names) { tier -> tier.attributes.map { it.name } }
        return names.filter { it.isNotBlank() }.toSet()
    }

    private fun loadDropProtectionRules() {
        protectionEnabled = config.getBoolean("rules.drop-protection.enabled", true)
        protectionEmptySlotWeight = config.getDouble("rules.drop-protection.empty-slot-weight", 2.1).coerceAtLeast(1.0)
        protectionDuplicatePenalty = config.getDouble("rules.drop-protection.duplicate-slot-penalty", 0.72)
            .coerceIn(0.05, 1.0)
        protectionUpgradeScoreScale = config.getDouble("rules.drop-protection.upgrade-score-scale", 0.02).coerceAtLeast(0.0)
        protectionMaxUpgradeBonus = config.getDouble("rules.drop-protection.max-upgrade-bonus", 0.75).coerceAtLeast(0.0)
        protectionDowngradePenaltyScale = config.getDouble("rules.drop-protection.downgrade-penalty-scale", 0.012)
            .coerceAtLeast(0.0)
        protectionMinFactor = config.getDouble("rules.drop-protection.min-weight-factor", 0.25).coerceIn(0.05, 1.0)
    }

    private fun loadIdentificationRules() {
        identificationEnabled = config.getBoolean("identification.enabled", true)
        identificationBasePrice = config.getInt("identification.base-price", 80).coerceAtLeast(0)
        identificationFloorPrice = config.getInt("identification.floor-price", 6).coerceAtLeast(0)
        identificationBossExtraPrice = config.getInt("identification.boss-extra-price", 120).coerceAtLeast(0)
        identificationHiddenExtraPrice = config.getInt("identification.hidden-extra-price", 80).coerceAtLeast(0)
        val section = config.getConfigurationSection("identification.drop-as-unidentified-chance") ?: return
        for (key in section.getKeys(false)) {
            val source = parseSource(key) ?: continue
            unidentifiedDropChance[source] = section.getDouble(key, 0.0).coerceIn(0.0, 1.0)
        }
    }

    private fun loadForgeBookRules() {
        forgeBooksEnabled = config.getBoolean("forge-books.enabled", true)
        val dropSection = config.getConfigurationSection("forge-books.drop-chance")
        if (dropSection != null) {
            for (key in dropSection.getKeys(false)) {
                val source = parseSource(key) ?: continue
                forgeBookDropChance[source] = dropSection.getDouble(key, 0.0).coerceIn(0.0, 1.0)
            }
        }
        val weightSection = config.getConfigurationSection("forge-books.quality-weight")
        if (weightSection != null) {
            for (key in weightSection.getKeys(false)) {
                forgeBookQualityWeights[key.lowercase()] = weightSection.getInt(key, 0).coerceAtLeast(0)
            }
        }
        val qualitySection = config.getConfigurationSection("forge-books.qualities") ?: return
        for (id in qualitySection.getKeys(false)) {
            val node = qualitySection.getConfigurationSection(id) ?: continue
            forgeBookQualities[id.lowercase()] = ForgeBookQuality(
                id = id.lowercase(),
                name = node.getString("name")?.takeUnless { it == id } ?: "未命名锻造书品质",
                color = node.getString("color") ?: "§f",
                rarityId = node.getString("rarity") ?: "common",
                timeMillis = node.getInt("time-seconds", 60).coerceAtLeast(1) * 1000L,
                soulShards = node.getInt("soul-shards", 100).coerceAtLeast(0),
                materials = PermanentMaterialManager.parseCost(node.getConfigurationSection("materials"))
            )
        }
    }

    private fun loadSourceBonuses() {
        val section = config.getConfigurationSection("rules.source-bonus") ?: return
        for (key in section.getKeys(false)) {
            val source = parseSource(key) ?: continue
            val node = section.getConfigurationSection(key) ?: continue
            sourceAffixBonus[source] = node.getInt("extra-affixes", 0).coerceAtLeast(0)
            sourceRarityUpgradeChance[source] = node.getDouble("rarity-upgrade-chance", 0.0).coerceIn(0.0, 1.0)
        }
    }

    private fun loadRarities() {
        val section = config.getConfigurationSection("rarities")
        if (section == null) {
            rarities += DungeonLootRarity("common", "普通", "§f", 70, 1.0, 0)
            return
        }

        for (id in section.getKeys(false)) {
            val node = section.getConfigurationSection(id) ?: continue
            val rarity = DungeonLootRarity(
                id = id,
                displayName = node.getString("name")?.takeUnless { it == id } ?: "未命名稀有度",
                color = node.getString("color") ?: "§f",
                weight = node.getInt("weight", 10).coerceAtLeast(1),
                multiplier = node.getDouble("multiplier", 1.0).coerceAtLeast(0.0),
                maxAffixes = node.getInt("max-affixes", 0).coerceAtLeast(0)
            )
            rarities += rarity
        }

        if (rarities.isEmpty()) {
            rarities += DungeonLootRarity("common", "普通", "§f", 70, 1.0, 0)
        }
    }

    private fun loadAffixes() {
        val section = config.getConfigurationSection("affixes") ?: return
        for (id in section.getKeys(false)) {
            val node = section.getConfigurationSection(id) ?: continue
            val type = parseAffixType(node.getString("type")) ?: continue
            val range = parseRange(node.getString("range") ?: "1-999")
            val slots = parseSlots(node.getStringList("slots"))
            val sources = node.getStringList("sources").mapNotNull { parseSource(it) }.toSet()

            affixes += DungeonLootAffix(
                id = id,
                name = node.getString("name")?.takeUnless { it == id } ?: "未命名词条",
                type = type,
                weight = node.getInt("weight", 10).coerceAtLeast(1),
                minFloor = range.first,
                maxFloor = range.last,
                group = node.getString("group")?.trim()?.lowercase()?.takeIf { it.isNotBlank() },
                excludes = node.getStringList("excludes")
                    .map { it.trim().lowercase() }
                    .filter { it.isNotEmpty() }
                    .toSet(),
                slots = slots,
                sources = sources,
                slotWeight = parseSlotWeight(node.getConfigurationSection("slot-weight")),
                sourceWeight = parseSourceWeight(node.getConfigurationSection("source-weight")),
                lore = node.getStringList("lore"),
                attributes = parseAttributes(node.getConfigurationSection("attributes"))
            )
        }
    }

    private fun loadSetBonuses() {
        val section = config.getConfigurationSection("sets") ?: return
        for (id in section.getKeys(false)) {
            val node = section.getConfigurationSection(id) ?: continue
            val bonusSection = node.getConfigurationSection("bonuses") ?: continue
            val tiers = bonusSection.getKeys(false).mapNotNull { key ->
                val count = key.toIntOrNull() ?: return@mapNotNull null
                val tierNode = bonusSection.getConfigurationSection(key) ?: return@mapNotNull null
                DungeonLootSetTier(
                    count = count.coerceAtLeast(1),
                    lore = tierNode.getStringList("lore"),
                    attributes = parseAttributes(tierNode.getConfigurationSection("attributes"))
                )
            }.sortedBy { it.count }
            if (tiers.isEmpty()) {
                continue
            }

            setBonuses += DungeonLootSetBonus(
                id = id,
                name = node.getString("name")?.takeUnless { it == id } ?: "未命名套装",
                themes = parseLowercaseSet(node.getStringList("themes")),
                tags = parseLowercaseSet(node.getStringList("tags")),
                tiers = tiers
            )
        }
    }

    private fun loadLootDefinitions() {
        val section = config.getConfigurationSection("loot")
        if (section == null) {
            warning("[RogueCore] loot.yml 中未找到 loot 节点")
            return
        }

        for (id in section.getKeys(false)) {
            val node = section.getConfigurationSection(id) ?: continue
            try {
                val name = node.getString("name")?.takeUnless { it == id } ?: "未命名装备"
                val material = XMaterial.matchXMaterial(node.getString("material") ?: "STONE_SWORD")
                    .orElse(XMaterial.STONE_SWORD)
                val sources = node.getStringList("sources").mapNotNull { parseSource(it) }.toSet()
                if (sources.isEmpty()) {
                    continue
                }

                val range = parseRange(node.getString("range") ?: "1-999")
                definitions += DungeonLootDefinition(
                    id = id,
                    name = name,
                    material = material,
                    lore = node.getStringList("lore"),
                    theme = node.getString("theme")?.trim()?.lowercase()?.takeIf { it.isNotBlank() },
                    tags = parseLowercaseSet(node.getStringList("tags")),
                    sources = sources,
                    weight = node.getInt("weight", 10).coerceAtLeast(1),
                    minFloor = range.first,
                    maxFloor = range.last,
                    equipmentSlot = parseSlot(node.getString("equipment-slot")),
                    attributes = parseAttributes(node.getConfigurationSection("attributes"))
                )
            } catch (ex: Exception) {
                warning("[RogueCore] 加载临时装备 '$id' 失败: ${ex.message}")
            }
        }
    }

    fun grantChestLoot(player: Player, instance: DungeonInstance): Boolean {
        val chance = (chestChance * AffixManager.getWeaponLuckMultiplier(instance)).coerceAtMost(1.0)
        if (Random.nextDouble() > chance) {
            return false
        }
        return grant(player, instance, DungeonLootSource.CHEST, 1)
    }

    fun grantEliteLoot(player: Player, instance: DungeonInstance): Boolean {
        val count = eliteCount + getExtraRolls(instance) + getRelicExtraRoll(player)
        return grant(player, instance, DungeonLootSource.ELITE, count)
    }

    fun grantBossLoot(player: Player, instance: DungeonInstance): Boolean {
        val count = bossCount + getExtraRolls(instance) + getRelicExtraRoll(player)
        return grant(player, instance, DungeonLootSource.BOSS, count)
    }

    fun grantHiddenLoot(player: Player, instance: DungeonInstance): Boolean {
        return grant(player, instance, DungeonLootSource.HIDDEN, 1 + getExtraRolls(instance))
    }

    private fun grant(player: Player, instance: DungeonInstance, source: DungeonLootSource, count: Int): Boolean {
        return PerfMonitor.measure("loot.grant") {
            val pool = definitions.filter { it.matches(source, instance.config.floorNumber) }
            if (pool.isEmpty()) {
                return@measure false
            }

            var granted = false
            repeat(count.coerceAtLeast(1)) {
                val definition = weightedRandom(pool) { candidate ->
                    effectiveLootWeight(player, candidate)
                } ?: return@repeat
                val item = if (shouldDropUnidentified(source)) {
                    buildUnidentifiedLoot(definition, source, instance.config.floorNumber)
                } else {
                    buildRolledLoot(definition, source, source.displayName(), instance.config.floorNumber, 0, 0, 0, 0.0)?.item
                } ?: return@repeat
                give(player, item)
                if (isUnidentifiedLoot(item)) {
                    RunSummaryManager.onLootGained(player.uniqueId, "unidentified_gear")
                    GuideManager.showOnce(player, GuideManager.UNIDENTIFIED_LOOT, listOf(
                        "§e你获得了未鉴定装备。",
                        "§7在 §6/rogue gear storage identify §7中鉴定后会变成绑定你的永久装备。"
                    ))
                } else {
                    RunSummaryManager.onLootGained(player.uniqueId, "temporary_gear")
                }
                GuideManager.showOnce(player, GuideManager.SALVAGE, listOf(
                    "§e不需要的装备、饰品和书类可以回收。",
                    "§7打开 §6/rogue gear storage salvage §7可分解低价值物品换材料。"
                ))
                if (shouldDropForgeBook(player, source)) {
                    val quality = rollForgeBookQuality(player)
                    val book = quality?.let { buildForgeBook(definition, source, instance.config.floorNumber, it) }
                    if (book != null) {
                        give(player, book)
                        RunSummaryManager.onLootGained(player.uniqueId, "forge_book")
                        GuideManager.showOnce(player, GuideManager.FORGE_BOOK, listOf(
                            "§e你获得了锻造书。",
                            "§7在 §6/rogue gear storage craft §7中消耗材料和时间打造永久装备。"
                        ))
                    }
                }
                granted = true
            }
            granted
        }
    }

    private fun shouldDropUnidentified(source: DungeonLootSource): Boolean {
        if (!identificationEnabled) {
            return false
        }
        val chance = unidentifiedDropChance[source] ?: return false
        return chance > 0.0 && Random.nextDouble() < chance
    }

    private fun shouldDropForgeBook(player: Player, source: DungeonLootSource): Boolean {
        if (!forgeBooksEnabled || forgeBookQualities.isEmpty()) {
            return false
        }
        val chance = ((forgeBookDropChance[source] ?: return false) * UnlockManager.getForgeBookDropMultiplier(player))
            .coerceIn(0.0, 1.0)
        return chance > 0.0 && Random.nextDouble() < chance
    }

    private fun rollForgeBookQuality(player: Player): ForgeBookQuality? {
        val candidates = forgeBookQualities.values.filter { quality ->
            getForgeBookQualityWeight(player, quality) > 0
        }
        return weightedRandom(candidates) { quality -> getForgeBookQualityWeight(player, quality) }
    }

    private fun getForgeBookQualityWeight(player: Player, quality: ForgeBookQuality): Int {
        return ((forgeBookQualityWeights[quality.id] ?: 0) + UnlockManager.getForgeBookQualityWeightBonus(player, quality.id))
            .coerceAtLeast(0)
    }

    private fun buildForgeBook(
        definition: DungeonLootDefinition,
        source: DungeonLootSource,
        floor: Int,
        quality: ForgeBookQuality
    ): ItemStack? {
        val item = XMaterial.ENCHANTED_BOOK.parseItem() ?: XMaterial.BOOK.parseItem() ?: return null
        val meta = item.itemMeta ?: return item
        meta.setDisplayName("${quality.color}${quality.displayName()}锻造书: §f${definition.name}")
        meta.persistentDataContainer.set(forgeBookKey, PersistentDataType.BYTE, 1)
        meta.persistentDataContainer.set(forgeBookQualityKey, PersistentDataType.STRING, quality.id)
        meta.persistentDataContainer.set(forgeBookLootIdKey, PersistentDataType.STRING, definition.id)
        meta.persistentDataContainer.set(forgeBookSourceKey, PersistentDataType.STRING, source.name)
        meta.persistentDataContainer.set(forgeBookFloorKey, PersistentDataType.INTEGER, floor)
        meta.lore = listOf(
            "",
            "§7记载着一件装备的锻造方法。",
            "§7品质: ${quality.color}${quality.displayName()}",
            "§7预计耗时: §f${ForgeBookTaskManager.formatDuration(quality.timeMillis)}",
            "§7灵魂碎片: §6${quality.soulShards}",
            "§7材料需求: ${PermanentMaterialManager.formatCost(quality.materials)}",
            "§7来源: §f${source.displayName()}",
            "§7层数: §f$floor",
            "",
            "§e使用 §6/rogue gear storage craft §e开始锻造"
        )
        applyLootMeta(item, meta)
        return item
    }

    private fun buildUnidentifiedLoot(definition: DungeonLootDefinition, source: DungeonLootSource, floor: Int): ItemStack? {
        val item = XMaterial.PAPER.parseItem() ?: return null
        val meta = item.itemMeta ?: return item
        val sourceName = source.displayName()
        meta.setDisplayName("§e未鉴定的${definition.name}")
        meta.persistentDataContainer.set(unidentifiedKey, PersistentDataType.BYTE, 1)
        meta.persistentDataContainer.set(unidentifiedLootIdKey, PersistentDataType.STRING, definition.id)
        meta.persistentDataContainer.set(unidentifiedSourceKey, PersistentDataType.STRING, source.name)
        meta.persistentDataContainer.set(unidentifiedFloorKey, PersistentDataType.INTEGER, floor)
        meta.lore = listOf(
            "",
            "§7这件装备被地牢气息遮蔽。",
            "§7需要在局外进行鉴定。",
            "§8鉴定后将揭示稀有度、词条和属性。",
            "",
            "§7来源: §f$sourceName",
            "§7层数: §f$floor",
            "§e使用 §6/rogue gear storage identify §e进行鉴定"
        )
        applyLootMeta(item, meta)
        return item
    }

    private fun buildRolledLoot(
        definition: DungeonLootDefinition,
        source: DungeonLootSource,
        sourceLabel: String,
        floor: Int,
        forgeLevel: Int,
        forgeHeat: Int,
        heatCap: Int,
        attributeBonusPerLevel: Double,
        forcedRarity: DungeonLootRarity? = null,
        forcedAffixes: List<DungeonLootAffix>? = null,
        fixedRoll: Boolean = false,
        lockedAffixId: String? = null
    ): RolledLoot? {
        val item = definition.material.parseItem() ?: return null
        val meta = item.itemMeta ?: return RolledLoot(item, fallbackRarity(), emptyList(), 0.0)
        val rarity = forcedRarity ?: rollRarity(source)
        val affixCap = rarity.maxAffixes + (sourceAffixBonus[source] ?: 0)
        val selectedAffixes = forcedAffixes ?: rollAffixes(definition, source, floor, affixCap, lockedAffixId)
        val activeLockedAffixId = lockedAffixId?.takeIf { locked ->
            selectedAffixes.any { it.id.equals(locked, ignoreCase = true) }
        }
        val multiplier = rarity.multiplier * (1.0 + forgeLevel * attributeBonusPerLevel)

        meta.setDisplayName("${rarity.color}${definition.name}")
        meta.persistentDataContainer.set(lootIdKey, PersistentDataType.STRING, definition.id)
        meta.persistentDataContainer.set(forgeLevelKey, PersistentDataType.INTEGER, forgeLevel)
        meta.persistentDataContainer.set(forgeHeatKey, PersistentDataType.INTEGER, forgeHeat)
        meta.persistentDataContainer.set(rarityKey, PersistentDataType.STRING, rarity.id)
        meta.persistentDataContainer.set(
            affixKey,
            PersistentDataType.STRING,
            selectedAffixes.joinToString(",") { it.id }
        )
        if (activeLockedAffixId != null) {
            meta.persistentDataContainer.set(lockedAffixKey, PersistentDataType.STRING, activeLockedAffixId)
        } else {
            meta.persistentDataContainer.remove(lockedAffixKey)
        }

        val lore = mutableListOf<String>()
        lore += "§8${rarity.displayName} · ${slotDisplayName(definition.equipmentSlot)}"
        if (definition.lore.isNotEmpty()) {
            lore += definition.lore
        }
        if (selectedAffixes.isNotEmpty()) {
            lore += ""
            lore += "§7随机词条:"
            for (affix in selectedAffixes) {
                lore += renderAffixTitle(affix, activeLockedAffixId)
                lore += affix.lore
            }
        }

        val allAttributes = definition.attributes + selectedAffixes.flatMap { it.attributes }
        if (allAttributes.isNotEmpty()) {
            lore += ""
            lore += "§7属性:"
            for (attribute in allAttributes) {
                val value = rollAttributeValue(attribute, multiplier, fixedRoll)
                lore += renderAttributeLine(attribute.name, value, attribute.percentage)
            }
        }

        lore += ""
        if (forgeLevel > 0) {
            lore += "§6锻造等级: +$forgeLevel"
        }
        if (heatCap > 0) {
            lore += "§c锻造热度: $forgeHeat/$heatCap"
        }
        lore += "§8来源: $sourceLabel"
        lore += "§c离开副本后消失"
        meta.persistentDataContainer.set(baseLoreKey, PersistentDataType.STRING, lore.joinToString("\n"))
        meta.lore = lore

        val score = score(definition, rarity, selectedAffixes, forgeLevel, attributeBonusPerLevel)
        meta.persistentDataContainer.set(scoreKey, PersistentDataType.DOUBLE, score)
        applyLootMeta(item, meta)

        return RolledLoot(DungeonBoundItem.mark(item) ?: item, rarity, selectedAffixes, score)
    }

    private fun give(player: Player, item: ItemStack) {
        val definition = getDefinition(item)
        if (definition != null && tryAutoEquip(player, item, definition)) {
            return
        }

        refreshEquippedSetBonuses(player)
        val leftovers = player.inventory.addItem(item)
        if (leftovers.isEmpty()) {
            player.sendMessage("§6获得临时装备: §f${item.itemMeta?.displayName ?: materialTypeName(item.type)}")
            return
        }

        leftovers.values.forEach { leftover ->
            player.world.dropItemNaturally(player.location, leftover)
        }
        player.sendMessage("§6获得临时装备，但背包已满，已掉落在脚边。")
    }

    private fun tryAutoEquip(player: Player, item: ItemStack, definition: DungeonLootDefinition): Boolean {
        val slot = definition.equipmentSlot ?: return false
        val current = getEquippedItem(player, slot)

        if (current == null || current.type == Material.AIR) {
            setEquippedItem(player, slot, item)
            refreshEquippedSetBonuses(player)
            player.sendMessage("§a已自动装备临时装备: §f${item.itemMeta?.displayName ?: definition.name} §7(${slot.displayName()})")
            return true
        }

        val currentDefinition = getDefinition(current)
        if (currentDefinition == null) {
            player.sendMessage("§e获得临时装备: §f${item.itemMeta?.displayName ?: definition.name} §7(${slot.displayName()})")
            player.sendMessage("§7检测到该槽位已有永久装备，未自动替换。")
            return false
        }

        val currentScore = getScore(current)
        val candidateScore = getScore(item)
        if (candidateScore <= currentScore) {
            player.sendMessage("§e获得临时装备: §f${item.itemMeta?.displayName ?: definition.name} §7(${slot.displayName()})")
            player.sendMessage("§7当前已装备更好的临时装备，已放入背包。")
            return false
        }

        setEquippedItem(player, slot, item)
        val leftovers = player.inventory.addItem(current)
        leftovers.values.forEach { leftover ->
            player.world.dropItemNaturally(player.location, leftover)
        }
        refreshEquippedSetBonuses(player)
        player.sendMessage(
            "§a已自动替换临时装备: §f${currentDefinition.name} §7→ §f${item.itemMeta?.displayName ?: definition.name} §7(${slot.displayName()})"
        )
        return true
    }

    fun getDefinition(item: ItemStack?): DungeonLootDefinition? {
        if (item == null || item.type == Material.AIR) {
            return null
        }
        val meta = item.itemMeta ?: return null
        val id = meta.persistentDataContainer.get(lootIdKey, PersistentDataType.STRING) ?: return null
        return definitions.firstOrNull { it.id == id }
    }

    fun getEquippedLoot(player: Player): List<EquippedLootView> {
        return listOf(
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET,
            EquipmentSlot.HAND,
            EquipmentSlot.OFF_HAND
        ).mapNotNull { getEquippedLoot(player, it) }
    }

    fun getEquippedLoot(player: Player, slot: EquipmentSlot): EquippedLootView? {
        val item = getEquippedItem(player, slot) ?: return null
        if (!DungeonBoundItem.hasTag(item)) {
            return null
        }
        val definition = getDefinition(item) ?: return null
        return EquippedLootView(
            slot = slot,
            definition = definition,
            item = item.clone(),
            forgeLevel = getForgeLevel(item),
            forgeHeat = getForgeHeat(item),
            score = getScore(item)
        )
    }

    fun reforgeEquipped(
        player: Player,
        instance: DungeonInstance,
        slot: EquipmentSlot,
        attributeBonusPerLevel: Double,
        heatCap: Int,
        heatGain: Int
    ): LootActionResult {
        val current = getEquippedLoot(player, slot)
            ?: return LootActionResult(false, "§c该部位没有可重铸的临时装备。")
        val nextHeat = nextForgeHeat(current.forgeHeat, heatGain, heatCap)
            ?: return LootActionResult(false, "§c这件装备已经过热，无法继续重铸。")

        val pool = definitions.filter { candidate ->
            candidate.id != current.definition.id &&
                candidate.equipmentSlot == current.definition.equipmentSlot &&
                instance.config.floorNumber in candidate.minFloor..candidate.maxFloor &&
                candidate.sources.any { it in current.definition.sources }
        }
        if (pool.isEmpty()) {
            return LootActionResult(false, "§c这件装备没有可用的重铸结果。")
        }

        val result = weightedRandom(pool)
            ?: return LootActionResult(false, "§c重铸失败，请稍后再试。")
        val source = result.sources.firstOrNull { it in current.definition.sources } ?: result.sources.first()
        val currentItem = getEquippedItem(player, slot)
        val currentRarity = getRarity(currentItem) ?: fallbackRarity()
        val lockedAffixId = getLockedAffixId(currentItem)
        val rolled = buildRolledLoot(
            result,
            source,
            "铁匠重铸",
            instance.config.floorNumber,
            current.forgeLevel,
            nextHeat,
            heatCap,
            attributeBonusPerLevel,
            forcedRarity = currentRarity,
            lockedAffixId = lockedAffixId
        )
            ?: return LootActionResult(false, "§c重铸失败，请检查装备配置。")
        setEquippedItem(player, slot, rolled.item)
        refreshEquippedSetBonuses(player)
        return LootActionResult(
            true,
            "§6⚒ 重铸完成: §f${current.definition.name} §7→ §f${result.name}"
        )
    }

    fun reforgeEquippedWithoutHeat(
        player: Player,
        instance: DungeonInstance,
        slot: EquipmentSlot,
        attributeBonusPerLevel: Double,
        heatCap: Int
    ): LootActionResult {
        val current = getEquippedLoot(player, slot)
            ?: return LootActionResult(false, "§c该部位没有可重铸的临时装备。")

        val pool = definitions.filter { candidate ->
            candidate.id != current.definition.id &&
                candidate.equipmentSlot == current.definition.equipmentSlot &&
                instance.config.floorNumber in candidate.minFloor..candidate.maxFloor &&
                candidate.sources.any { it in current.definition.sources }
        }
        if (pool.isEmpty()) {
            return LootActionResult(false, "§c这件装备没有可用的重铸结果。")
        }

        val result = weightedRandom(pool)
            ?: return LootActionResult(false, "§c无热重铸失败，请稍后再试。")
        val currentItem = getEquippedItem(player, slot)
        val source = result.sources.firstOrNull { it in current.definition.sources } ?: result.sources.first()
        val currentRarity = getRarity(currentItem) ?: fallbackRarity()
        val lockedAffixId = getLockedAffixId(currentItem)
        val rolled = buildRolledLoot(
            result,
            source,
            "精炼重铸",
            instance.config.floorNumber,
            current.forgeLevel,
            current.forgeHeat,
            heatCap,
            attributeBonusPerLevel,
            forcedRarity = currentRarity,
            lockedAffixId = lockedAffixId
        )
            ?: return LootActionResult(false, "§c无热重铸失败，请检查装备配置。")
        setEquippedItem(player, slot, rolled.item)
        refreshEquippedSetBonuses(player)
        return LootActionResult(
            true,
            "§6⚒ 精炼重铸完成: §f${current.definition.name} §7→ §f${result.name} §7(未增加热度)"
        )
    }

    fun upgradeEquipped(
        player: Player,
        slot: EquipmentSlot,
        maxUpgrades: Int,
        attributeBonusPerLevel: Double,
        heatCap: Int,
        heatGain: Int
    ): LootActionResult {
        val currentItem = getEquippedItem(player, slot)
        val current = getEquippedLoot(player, slot)
            ?: return LootActionResult(false, "§c该部位没有可升阶的临时装备。")
        if (current.forgeLevel >= maxUpgrades) {
            return LootActionResult(false, "§c这件装备已达到最大锻造等级。")
        }
        val nextHeat = nextForgeHeat(current.forgeHeat, heatGain, heatCap)
            ?: return LootActionResult(false, "§c这件装备已经过热，无法继续升阶。")

        val newLevel = current.forgeLevel + 1
        val source = current.definition.sources.first()
        val currentRarity = getRarity(currentItem) ?: fallbackRarity()
        val currentAffixes = getAffixes(currentItem)
        val lockedAffixId = getLockedAffixId(currentItem)
        val upgraded = buildRolledLoot(
            current.definition,
            source,
            "铁匠升阶",
            current.definition.minFloor,
            newLevel,
            nextHeat,
            heatCap,
            attributeBonusPerLevel,
            forcedRarity = currentRarity,
            forcedAffixes = currentAffixes,
            lockedAffixId = lockedAffixId
        ) ?: return LootActionResult(false, "§c升阶失败，请检查装备配置。")

        if (currentItem != null && currentItem.amount > 1) {
            upgraded.item.amount = currentItem.amount
        }
        setEquippedItem(player, slot, upgraded.item)
        refreshEquippedSetBonuses(player)
        return LootActionResult(
            true,
            "§6⚒ 升阶完成: §f${current.definition.name} §7已提升至 §6+$newLevel §7并重铸词条"
        )
    }

    fun temperEquipped(
        player: Player,
        instance: DungeonInstance,
        slot: EquipmentSlot,
        maxUpgrades: Int,
        attributeBonusPerLevel: Double,
        heatCap: Int,
        heatGain: Int,
        levelGain: Int
    ): LootActionResult {
        val current = getEquippedLoot(player, slot)
            ?: return LootActionResult(false, "§c该部位没有可淬火的临时装备。")
        if (current.forgeLevel >= maxUpgrades) {
            return LootActionResult(false, "§c这件装备已经无法继续淬火。")
        }
        val nextHeat = nextForgeHeat(current.forgeHeat, heatGain, heatCap)
            ?: return LootActionResult(false, "§c这件装备已经过热，无法继续淬火。")

        val pool = definitions.filter { candidate ->
            candidate.equipmentSlot == current.definition.equipmentSlot &&
                instance.config.floorNumber in candidate.minFloor..candidate.maxFloor &&
                candidate.sources.any { it in current.definition.sources }
        }
        if (pool.isEmpty()) {
            return LootActionResult(false, "§c没有可用于淬火的装备模板。")
        }

        val candidates = pool.filter { it.id != current.definition.id }
        val result = weightedRandom(if (candidates.isNotEmpty()) candidates else pool)
            ?: return LootActionResult(false, "§c淬火失败，请稍后再试。")
        val newLevel = (current.forgeLevel + levelGain.coerceAtLeast(1)).coerceAtMost(maxUpgrades)
        val source = result.sources.firstOrNull { it in current.definition.sources } ?: result.sources.first()
        val currentItem = getEquippedItem(player, slot)
        val currentRarity = getRarity(currentItem) ?: fallbackRarity()
        val lockedAffixId = getLockedAffixId(currentItem)
        val rolled = buildRolledLoot(
            result,
            source,
            "灵魂淬火",
            instance.config.floorNumber,
            newLevel,
            nextHeat,
            heatCap,
            attributeBonusPerLevel,
            forcedRarity = currentRarity,
            lockedAffixId = lockedAffixId
        )
            ?: return LootActionResult(false, "§c淬火失败，请检查装备配置。")
        setEquippedItem(player, slot, rolled.item)
        refreshEquippedSetBonuses(player)
        return LootActionResult(
            true,
            "§6⚒ 淬火完成: §f${current.definition.name} §7→ §f${result.name} §7并提升至 §6+$newLevel"
        )
    }

    fun getForgeLevel(item: ItemStack?): Int {
        if (item == null || item.type == Material.AIR) {
            return 0
        }
        val meta = item.itemMeta ?: return 0
        return meta.persistentDataContainer.get(forgeLevelKey, PersistentDataType.INTEGER) ?: 0
    }

    fun getForgeHeat(item: ItemStack?): Int {
        if (item == null || item.type == Material.AIR) {
            return 0
        }
        val meta = item.itemMeta ?: return 0
        return meta.persistentDataContainer.get(forgeHeatKey, PersistentDataType.INTEGER) ?: 0
    }

    private fun nextForgeHeat(currentHeat: Int, heatGain: Int, heatCap: Int): Int? {
        if (heatCap <= 0) {
            return currentHeat.coerceAtLeast(0)
        }
        if (currentHeat >= heatCap) {
            return null
        }
        val next = currentHeat + heatGain.coerceAtLeast(0)
        return if (next > heatCap) null else next
    }

    fun getSalvageReward(
        player: Player,
        slot: EquipmentSlot,
        baseReward: Int,
        scoreScale: Double,
        forgeBonusPerLevel: Int
    ): Int {
        val equipped = getEquippedLoot(player, slot) ?: return 0
        return getSalvageReward(equipped.score, equipped.forgeLevel, baseReward, scoreScale, forgeBonusPerLevel)
    }

    fun salvageEquipped(
        player: Player,
        slot: EquipmentSlot,
        baseReward: Int,
        scoreScale: Double,
        forgeBonusPerLevel: Int
    ): LootSalvageResult {
        val equipped = getEquippedLoot(player, slot)
            ?: return LootSalvageResult(false, 0, "§c该部位没有可分解的临时装备。")
        val reward = getSalvageReward(equipped.score, equipped.forgeLevel, baseReward, scoreScale, forgeBonusPerLevel)
        setEquippedItem(player, slot, ItemStack(Material.AIR))
        refreshEquippedSetBonuses(player)
        return LootSalvageResult(
            true,
            reward,
            "§6⚒ 分解完成: §f${equipped.definition.name} §7→ §6+$reward §e本局碎片"
        )
    }

    fun coolForgedItem(
        player: Player,
        slot: EquipmentSlot,
        heatCap: Int,
        heatReduce: Int
    ): LootActionResult {
        val item = getEquippedItem(player, slot)
            ?: return LootActionResult(false, "§c该部位没有可退火的临时装备。")
        val definition = getDefinition(item)
            ?: return LootActionResult(false, "§c该部位没有可退火的临时装备。")
        val currentHeat = getForgeHeat(item)
        if (currentHeat <= 0) {
            return LootActionResult(false, "§7这件装备当前没有锻造热度。")
        }
        val newHeat = (currentHeat - heatReduce.coerceAtLeast(1)).coerceAtLeast(0)
        val meta = item.itemMeta ?: return LootActionResult(false, "§c退火失败，请稍后再试。")
        meta.persistentDataContainer.set(forgeHeatKey, PersistentDataType.INTEGER, newHeat)
        applyLootMeta(item, meta)
        refreshSingleItemLore(item, heatCap)
        refreshEquippedSetBonuses(player)
        return LootActionResult(
            true,
            "§6⚒ 余烬退火完成: §f${definition.name} §7热度降低至 §c$newHeat/${heatCap.coerceAtLeast(0)}"
        )
    }

    private fun rollRarity(source: DungeonLootSource): DungeonLootRarity {
        val base = weightedRandom(rarities, DungeonLootRarity::weight) ?: fallbackRarity()
        val upgradeChance = sourceRarityUpgradeChance[source] ?: 0.0
        if (upgradeChance <= 0.0 || Random.nextDouble() > upgradeChance) {
            return base
        }
        val sorted = rarities.sortedBy { it.multiplier }
        val index = sorted.indexOfFirst { it.id.equals(base.id, ignoreCase = true) }
        if (index < 0 || index >= sorted.lastIndex) {
            return base
        }
        return sorted[index + 1]
    }

    private fun fallbackRarity(): DungeonLootRarity {
        return rarities.firstOrNull() ?: DungeonLootRarity("common", "普通", "§f", 1, 1.0, 0)
    }

    private fun rollAffixes(
        definition: DungeonLootDefinition,
        source: DungeonLootSource,
        floor: Int,
        maxAffixes: Int,
        lockedAffixId: String? = null
    ): List<DungeonLootAffix> {
        if (maxAffixes <= 0 || definition.equipmentSlot == null) {
            return emptyList()
        }
        val pool = affixes.filter { affix ->
            floor in affix.minFloor..affix.maxFloor &&
                definition.equipmentSlot in affix.slots &&
                (affix.sources.isEmpty() || source in affix.sources)
        }
        if (pool.isEmpty()) {
            return emptyList()
        }

        val selected = mutableListOf<DungeonLootAffix>()
        val mutablePool = pool.toMutableList()
        val lockedAffix = lockedAffixId?.let { locked ->
            mutablePool.firstOrNull { it.id.equals(locked, ignoreCase = true) }
        }?.takeIf { isAffixCompatible(it, selected) }
        if (lockedAffix != null) {
            selected += lockedAffix
            mutablePool.remove(lockedAffix)
        }
        repeat((maxAffixes - selected.size).coerceAtLeast(0).coerceAtMost(mutablePool.size)) {
            val candidates = mutablePool.filter { candidate -> isAffixCompatible(candidate, selected) }
            val next = weightedRandom(candidates) { candidate ->
                affixEffectiveWeight(candidate, definition.equipmentSlot, source)
            } ?: return@repeat
            selected += next
            mutablePool.remove(next)
        }
        return selected
    }

    private fun isAffixCompatible(candidate: DungeonLootAffix, selected: List<DungeonLootAffix>): Boolean {
        return selected.none { picked ->
            isAffixConflict(candidate, picked) || isAffixConflict(picked, candidate)
        }
    }

    private fun isAffixConflict(left: DungeonLootAffix, right: DungeonLootAffix): Boolean {
        if (left.id.equals(right.id, ignoreCase = true)) {
            return true
        }
        if (right.id.lowercase() in left.excludes) {
            return true
        }
        val rightGroup = right.group
        if (rightGroup != null && rightGroup.lowercase() in left.excludes) {
            return true
        }
        val leftGroup = left.group
        if (!leftGroup.isNullOrBlank() && leftGroup.equals(right.group, ignoreCase = true)) {
            return true
        }
        return false
    }

    private fun affixEffectiveWeight(
        affix: DungeonLootAffix,
        slot: EquipmentSlot?,
        source: DungeonLootSource
    ): Int {
        val slotMultiplier = slot?.let { affix.slotWeight[it] } ?: null
        val sourceMultiplier = affix.sourceWeight[source]
        val multiplier = (slotMultiplier ?: 1.0) * (sourceMultiplier ?: 1.0)
        return (affix.weight * multiplier).roundToInt().coerceAtLeast(0)
    }

    private fun effectiveLootWeight(player: Player, definition: DungeonLootDefinition): Int {
        if (!protectionEnabled) {
            return definition.weight
        }
        val slot = definition.equipmentSlot ?: return definition.weight
        val ownedLoot = getOwnedLootForSlot(player, slot)
        val ownedCount = ownedLoot.size

        var factor = when {
            ownedCount <= 0 -> protectionEmptySlotWeight
            ownedCount == 1 -> 1.0
            else -> protectionDuplicatePenalty.pow((ownedCount - 1).toDouble())
        }

        val bestOwnedScore = ownedLoot.maxOfOrNull(::getComparableScore) ?: 0.0
        val candidateScore = estimateDefinitionScore(definition)
        val scoreGap = candidateScore - bestOwnedScore
        if (scoreGap > 0.0) {
            factor *= 1.0 + (scoreGap * protectionUpgradeScoreScale).coerceAtMost(protectionMaxUpgradeBonus)
        } else if (ownedCount > 0 && scoreGap < 0.0) {
            val penalty = (-scoreGap * protectionDowngradePenaltyScale).coerceAtMost(1.0 - protectionMinFactor)
            factor *= (1.0 - penalty).coerceAtLeast(protectionMinFactor)
        }

        return (definition.weight * factor).roundToInt().coerceAtLeast(1)
    }

    private fun getOwnedLootForSlot(player: Player, slot: EquipmentSlot): List<ItemStack> {
        val items = mutableListOf<ItemStack>()
        items += player.inventory.storageContents.filterNotNull()
        items += listOf(
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET,
            EquipmentSlot.HAND,
            EquipmentSlot.OFF_HAND
        ).mapNotNull { equippedSlot -> getEquippedItem(player, equippedSlot) }

        return items.filter { item ->
            item.type != Material.AIR &&
                DungeonBoundItem.hasTag(item) &&
                getDefinition(item)?.equipmentSlot == slot
        }
    }

    fun refreshEquippedSetBonuses(player: Player) {
        val equipped = getEquippedDefinitions(player)
        val activeBonuses = resolveActiveSetBonuses(equipped)
        for (view in equipped) {
            applySetBonusLore(view, activeBonuses)
        }
    }

    fun cycleLockedAffix(player: Player, slot: EquipmentSlot): LootActionResult {
        val item = getEquippedItem(player, slot)
            ?: return LootActionResult(false, "§c该部位没有可锁定词条的临时装备。")
        val definition = getDefinition(item)
            ?: return LootActionResult(false, "§c该部位没有可锁定词条的临时装备。")
        val affixes = getAffixes(item)
        if (affixes.isEmpty()) {
            return LootActionResult(false, "§c这件装备没有随机词条，无法锁定。")
        }

        val currentLocked = getLockedAffixId(item)
        val nextIndex = affixes.indexOfFirst { it.id.equals(currentLocked, ignoreCase = true) } + 1
        val nextLocked = affixes.getOrNull(nextIndex)?.id

        val meta = item.itemMeta ?: return LootActionResult(false, "§c锁词失败，请稍后再试。")
        if (nextLocked == null) {
            meta.persistentDataContainer.remove(lockedAffixKey)
        } else {
            meta.persistentDataContainer.set(lockedAffixKey, PersistentDataType.STRING, nextLocked)
        }
        applyLootMeta(item, meta)
        refreshSingleItemLore(item)
        refreshEquippedSetBonuses(player)

        val message = if (nextLocked == null) {
            "§7已清除 §f${definition.name} §7的锁定词条。"
        } else {
            val affix = affixes.firstOrNull { it.id.equals(nextLocked, ignoreCase = true) }
            "§6已锁定词条: §f${affix?.name ?: nextLocked}"
        }
        return LootActionResult(true, message)
    }

    fun getLockedAffixName(player: Player, slot: EquipmentSlot): String? {
        val item = getEquippedItem(player, slot) ?: return null
        val lockedId = getLockedAffixId(item) ?: return null
        return getAffixes(item).firstOrNull { it.id.equals(lockedId, ignoreCase = true) }?.name
    }

    fun isForgeBook(item: ItemStack?): Boolean {
        if (item == null || item.type == Material.AIR) {
            return false
        }
        val meta = item.itemMeta ?: return false
        return meta.persistentDataContainer.has(forgeBookKey, PersistentDataType.BYTE)
    }

    fun getForgeBookInventorySlots(player: Player): List<Int> {
        return player.inventory.storageContents.mapIndexedNotNull { index, item ->
            if (isForgeBook(item)) index else null
        }
    }

    fun getForgeBookInfo(item: ItemStack?): ForgeBookInfo? {
        if (!isForgeBook(item)) {
            return null
        }
        val meta = item?.itemMeta ?: return null
        val lootId = meta.persistentDataContainer.get(forgeBookLootIdKey, PersistentDataType.STRING) ?: return null
        val qualityId = meta.persistentDataContainer.get(forgeBookQualityKey, PersistentDataType.STRING)?.lowercase() ?: return null
        val quality = forgeBookQualities[qualityId] ?: return null
        val source = meta.persistentDataContainer.get(forgeBookSourceKey, PersistentDataType.STRING)
            ?.let { runCatching { DungeonLootSource.valueOf(it.uppercase()) }.getOrNull() }
            ?: DungeonLootSource.CHEST
        val floor = meta.persistentDataContainer.get(forgeBookFloorKey, PersistentDataType.INTEGER) ?: 1
        return ForgeBookInfo(lootId, source, floor, quality)
    }

    fun getDefinitionIds(): List<String> = definitions.map { it.id }.sorted()

    fun getDefinitionName(id: String): String? = definitions.firstOrNull { it.id.equals(id, ignoreCase = true) }?.name

    fun getForgeBookQualityIds(): List<String> = forgeBookQualities.keys.sorted()

    fun getForgeBookQualityName(id: String): String? = forgeBookQualities[id.lowercase()]?.name

    fun buildAdminGiveItem(
        player: Player,
        kind: String,
        lootId: String,
        source: DungeonLootSource,
        floor: Int,
        extra: String
    ): AdminGiveItemResult {
        val definition = definitions.firstOrNull { it.id.equals(lootId, ignoreCase = true) }
            ?: return AdminGiveItemResult(false, null, "§c装备模板不存在: §f${ContentDisplayNameResolver.safeText(lootId, "未知装备")}")
        return when (kind.lowercase()) {
            "gear" -> {
                val gearOptions = parseAdminGearOptions(extra)
                val rarity = gearOptions.rarityId?.let { id ->
                    rarities.firstOrNull { it.id.equals(id, ignoreCase = true) }
                }
                if (gearOptions.rarityId != null && rarity == null) {
                    return AdminGiveItemResult(false, null, "§c装备稀有度不存在: §f${ContentDisplayNameResolver.safeText(gearOptions.rarityId, "未知稀有度")}")
                }
                val forcedAffixes = if (gearOptions.noAffix) emptyList<DungeonLootAffix>() else null
                val rolled = buildRolledLoot(
                    definition,
                    source,
                    "管理员给予",
                    floor.coerceAtLeast(1),
                    0,
                    0,
                    0,
                    0.0,
                    forcedRarity = rarity,
                    forcedAffixes = forcedAffixes,
                    fixedRoll = gearOptions.fixedRoll
                )
                    ?: return AdminGiveItemResult(false, null, "§c装备生成失败，请检查配置。")
                if (gearOptions.permanent) {
                    makePermanent(rolled.item, player)
                }
                AdminGiveItemResult(true, rolled.item, "§a已生成装备: §f${definition.name} §7(${if (isPermanentLoot(rolled.item)) "永久" else "临时"})")
            }
            "unidentified" -> {
                val item = buildUnidentifiedLoot(definition, source, floor.coerceAtLeast(1))
                    ?: return AdminGiveItemResult(false, null, "§c未鉴定装备生成失败。")
                AdminGiveItemResult(true, DungeonBoundItem.mark(item) ?: item, "§a已生成未鉴定装备: §f${definition.name}")
            }
            "forgebook" -> {
                val quality = forgeBookQualities[extra.lowercase()] ?: forgeBookQualities["rough"] ?: forgeBookQualities.values.firstOrNull()
                    ?: return AdminGiveItemResult(false, null, "§c没有可用锻造书品质。")
                val item = buildForgeBook(definition, source, floor.coerceAtLeast(1), quality)
                    ?: return AdminGiveItemResult(false, null, "§c锻造书生成失败。")
                AdminGiveItemResult(true, DungeonBoundItem.mark(item) ?: item, "§a已生成锻造书: §f${definition.name} §7(${quality.displayName()})")
            }
            else -> AdminGiveItemResult(false, null, "§c不支持的装备测试物品类型: §f$kind")
        }
    }

    private data class AdminGearOptions(
        val permanent: Boolean,
        val noAffix: Boolean,
        val rarityId: String?,
        val fixedRoll: Boolean
    )

    private fun parseAdminGearOptions(extra: String): AdminGearOptions {
        val lower = extra.trim().lowercase()
        val tokens = lower
            .split(",", "|", ";", " ", ":", "/")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        var permanent = false
        var noAffix = false
        var rarityId: String? = null
        var fixedRoll = false

        if ("temporary" in tokens || lower.contains("temporary")) {
            permanent = false
        }
        if ("permanent" in tokens || lower.contains("permanent")) {
            permanent = true
        }
        if (lower.contains("no-affix") || lower.contains("no_affix") || lower.contains("noaffix")) {
            noAffix = true
        }
        if (lower.contains("fixed-roll") || lower.contains("fixed_roll") || lower.contains("fixedroll")) {
            fixedRoll = true
        }

        rarityId = Regex("""rarity\s*=\s*([a-z_]+)""")
            .find(lower)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.isNotEmpty() }

        if (rarityId == null) {
            val candidates = listOf("common", "magic", "rare", "epic", "legendary")
            rarityId = candidates.firstOrNull { candidate ->
                tokens.contains(candidate) || lower.contains("_$candidate") || lower.contains("${candidate}_") || lower == candidate
            }
        }

        return AdminGearOptions(
            permanent = permanent,
            noAffix = noAffix,
            rarityId = rarityId,
            fixedRoll = fixedRoll
        )
    }

    fun buildForgeBookResultForPlayer(
        player: Player,
        lootId: String,
        source: DungeonLootSource,
        floor: Int,
        qualityId: String
    ): IdentifyClaimResult {
        val quality = forgeBookQualities[qualityId.lowercase()]
            ?: return IdentifyClaimResult(false, null, "§c锻造失败：锻造书品质不存在。")
        val definition = definitions.firstOrNull { it.id == lootId }
            ?: return IdentifyClaimResult(false, null, "§c锻造失败：装备模板不存在。")
        val rarity = rarities.firstOrNull { it.id.equals(quality.rarityId, ignoreCase = true) }
            ?: fallbackRarity()
        val rolled = buildRolledLoot(
            definition,
            source,
            "锻造书打造",
            floor,
            0,
            0,
            0,
            0.0,
            forcedRarity = rarity
        ) ?: return IdentifyClaimResult(false, null, "§c锻造失败，请检查装备配置。")
        makePermanent(rolled.item, player)
        return IdentifyClaimResult(true, rolled.item, "§6锻造完成: §f${definition.name}")
    }

    fun isUnidentifiedLoot(item: ItemStack?): Boolean {
        if (item == null || item.type == Material.AIR) {
            return false
        }
        val meta = item.itemMeta ?: return false
        return meta.persistentDataContainer.has(unidentifiedKey, PersistentDataType.BYTE)
    }

    fun getUnidentifiedInventorySlots(player: Player): List<Int> {
        return player.inventory.storageContents.mapIndexedNotNull { index, item ->
            if (isUnidentifiedLoot(item)) index else null
        }
    }

    fun getIdentificationPrice(item: ItemStack?): Int {
        return getUnidentifiedLootInfo(item)?.price ?: 0
    }

    fun getIdentificationPrice(player: Player, item: ItemStack?): Int {
        return (getIdentificationPrice(item) * UnlockManager.getIdentificationPriceMultiplier(player))
            .roundToInt()
            .coerceAtLeast(0)
    }

    fun getUnidentifiedLootInfo(item: ItemStack?): UnidentifiedLootInfo? {
        if (!isUnidentifiedLoot(item)) {
            return null
        }
        val meta = item?.itemMeta ?: return null
        val lootId = meta.persistentDataContainer.get(unidentifiedLootIdKey, PersistentDataType.STRING) ?: return null
        val floor = meta.persistentDataContainer.get(unidentifiedFloorKey, PersistentDataType.INTEGER) ?: 1
        val source = meta.persistentDataContainer.get(unidentifiedSourceKey, PersistentDataType.STRING)
            ?.let { runCatching { DungeonLootSource.valueOf(it.uppercase()) }.getOrNull() }
            ?: DungeonLootSource.CHEST
        val extra = when (source) {
            DungeonLootSource.BOSS -> identificationBossExtraPrice
            DungeonLootSource.HIDDEN -> identificationHiddenExtraPrice
            else -> 0
        }
        val price = (identificationBasePrice + floor.coerceAtLeast(1) * identificationFloorPrice + extra).coerceAtLeast(0)
        return UnidentifiedLootInfo(lootId, source, floor, price)
    }

    fun buildIdentifiedLootForPlayer(player: Player, lootId: String, source: DungeonLootSource, floor: Int): IdentifyClaimResult {
        val definition = definitions.firstOrNull { it.id == lootId }
            ?: return IdentifyClaimResult(false, null, "§c鉴定失败：装备模板不存在。")
        val rolled = buildRolledLoot(definition, source, "装备鉴定", floor, 0, 0, 0, 0.0)
            ?: return IdentifyClaimResult(false, null, "§c鉴定失败，请检查装备配置。")
        makePermanent(rolled.item, player)
        return IdentifyClaimResult(true, rolled.item, "§6鉴定完成: §f${definition.name}")
    }

    fun isPermanentLoot(item: ItemStack?): Boolean {
        if (item == null || item.type == Material.AIR) {
            return false
        }
        val meta = item.itemMeta ?: return false
        return meta.persistentDataContainer.has(permanentLootKey, PersistentDataType.BYTE)
    }

    fun getLootEquipmentSlot(item: ItemStack?): EquipmentSlot? {
        return getDefinition(item)?.equipmentSlot
    }

    fun isLootItem(item: ItemStack?): Boolean {
        return getDefinition(item) != null
    }

    fun isTemporaryLoot(item: ItemStack?): Boolean {
        return isLootItem(item) && DungeonBoundItem.hasTag(item) && !isPermanentLoot(item)
    }

    fun getLootFloor(item: ItemStack?): Int {
        return getDefinition(item)?.minFloor ?: getUnidentifiedLootInfo(item)?.floor ?: getForgeBookInfo(item)?.floor ?: 1
    }

    fun getLootRarityId(item: ItemStack?): String? {
        return getRarity(item)?.id
    }

    fun getLootRarityName(item: ItemStack?): String? {
        return getRarity(item)?.displayName
    }

    fun getLootTheme(item: ItemStack?): String? {
        return getDefinition(item)?.theme
    }

    fun isAtLeastRarity(item: ItemStack?, rarityId: String): Boolean {
        val rarity = getRarity(item) ?: return false
        val currentIndex = rarities.indexOfFirst { it.id.equals(rarity.id, ignoreCase = true) }
        val requiredIndex = rarities.indexOfFirst { it.id.equals(rarityId, ignoreCase = true) }
        return currentIndex >= 0 && requiredIndex >= 0 && currentIndex >= requiredIndex
    }

    fun isFavorite(item: ItemStack?): Boolean {
        if (!isPermanentLoot(item)) {
            return false
        }
        val meta = item?.itemMeta ?: return false
        return meta.persistentDataContainer.get(favoriteKey, PersistentDataType.BYTE)?.toInt() == 1
    }

    fun setFavorite(item: ItemStack?, favorite: Boolean): Boolean {
        if (item == null || item.type == Material.AIR || !isPermanentLoot(item)) {
            return false
        }
        val meta = item.itemMeta ?: return false
        if (favorite) {
            meta.persistentDataContainer.set(favoriteKey, PersistentDataType.BYTE, 1)
        } else {
            meta.persistentDataContainer.remove(favoriteKey)
        }
        applyLootMeta(item, meta)
        refreshPermanentLore(item, getPermanentOwnerName(item))
        return true
    }

    fun toggleFavorite(item: ItemStack?): Boolean? {
        if (item == null || item.type == Material.AIR || !isPermanentLoot(item)) {
            return null
        }
        val next = !isFavorite(item)
        setFavorite(item, next)
        return next
    }

    fun getPermanentOwnerUuid(item: ItemStack?): UUID? {
        if (item == null || item.type == Material.AIR) {
            return null
        }
        val raw = item.itemMeta?.persistentDataContainer?.get(ownerUuidKey, PersistentDataType.STRING) ?: return null
        return runCatching { UUID.fromString(raw) }.getOrNull()
    }

    fun getPermanentOwnerName(item: ItemStack?): String? {
        if (item == null || item.type == Material.AIR) {
            return null
        }
        return item.itemMeta?.persistentDataContainer?.get(ownerNameKey, PersistentDataType.STRING)
    }

    fun isPermanentLootOwnedBy(item: ItemStack?, player: Player): Boolean {
        if (!isPermanentLoot(item)) {
            return false
        }
        val owner = getPermanentOwnerUuid(item)
        return owner == null || owner == player.uniqueId
    }

    fun ensurePermanentOwner(item: ItemStack?, player: Player): Boolean {
        if (item == null || item.type == Material.AIR || !isPermanentLoot(item)) {
            return false
        }
        if (getPermanentOwnerUuid(item) != null) {
            return getPermanentOwnerUuid(item) == player.uniqueId
        }
        val meta = item.itemMeta ?: return false
        meta.persistentDataContainer.set(ownerUuidKey, PersistentDataType.STRING, player.uniqueId.toString())
        meta.persistentDataContainer.set(ownerNameKey, PersistentDataType.STRING, player.name)
        applyLootMeta(item, meta)
        refreshPermanentLore(item, player.name)
        return true
    }

    fun getPermanentEquippedLoot(player: Player, slot: EquipmentSlot): EquippedLootView? {
        val item = getEquippedItem(player, slot) ?: return null
        if (!isPermanentLoot(item) || DungeonBoundItem.hasTag(item)) {
            return null
        }
        if (!ensurePermanentOwner(item, player)) {
            return null
        }
        val definition = getDefinition(item) ?: return null
        return EquippedLootView(
            slot = slot,
            definition = definition,
            item = item.clone(),
            forgeLevel = getForgeLevel(item),
            forgeHeat = 0,
            score = getScore(item)
        )
    }

    fun convertEquippedToPermanent(player: Player, slot: EquipmentSlot): LootActionResult {
        val item = getEquippedItem(player, slot)
            ?: return LootActionResult(false, "§c该部位没有可铭刻的临时装备。")
        val definition = getDefinition(item)
            ?: return LootActionResult(false, "§c该部位没有可铭刻的临时装备。")
        if (!DungeonBoundItem.hasTag(item)) {
            return LootActionResult(false, "§c只有副本临时装备可以进行灵魂铭刻。")
        }

        makePermanent(item, player)
        refreshEquippedSetBonuses(player)
        return LootActionResult(true, "§6灵魂铭刻完成: §f${definition.name} §7已转为永久装备。")
    }

    fun upgradePermanentEquipped(
        player: Player,
        slot: EquipmentSlot,
        maxLevel: Int,
        attributeBonusPerLevel: Double
    ): LootActionResult {
        val currentItem = getEquippedItem(player, slot)
        val current = getPermanentEquippedLoot(player, slot)
            ?: return LootActionResult(false, "§c该部位没有可升阶的永久装备。")
        if (current.forgeLevel >= maxLevel) {
            return LootActionResult(false, "§c这件永久装备已达到局外锻造上限。")
        }

        val newLevel = current.forgeLevel + 1
        val source = current.definition.sources.first()
        val currentRarity = getRarity(currentItem) ?: fallbackRarity()
        val currentAffixes = getAffixes(currentItem)
        val lockedAffixId = getLockedAffixId(currentItem)
        val favorite = isFavorite(currentItem)
        val upgraded = buildRolledLoot(
            current.definition,
            source,
            "局外升阶",
            current.definition.minFloor,
            newLevel,
            0,
            0,
            attributeBonusPerLevel,
            forcedRarity = currentRarity,
            forcedAffixes = currentAffixes,
            lockedAffixId = lockedAffixId
        ) ?: return LootActionResult(false, "§c局外升阶失败，请检查装备配置。")
        makePermanent(upgraded.item, player)
        setFavorite(upgraded.item, favorite)
        if (currentItem != null && currentItem.amount > 1) {
            upgraded.item.amount = currentItem.amount
        }
        setEquippedItem(player, slot, upgraded.item)
        refreshEquippedSetBonuses(player)
        return LootActionResult(true, "§6局外升阶完成: §f${current.definition.name} §7已提升至 §6+$newLevel")
    }

    fun rerollPermanentAffixes(
        player: Player,
        slot: EquipmentSlot,
        attributeBonusPerLevel: Double
    ): LootActionResult {
        val currentItem = getEquippedItem(player, slot)
        val current = getPermanentEquippedLoot(player, slot)
            ?: return LootActionResult(false, "§c该部位没有可重铸的永久装备。")
        val source = current.definition.sources.first()
        val currentRarity = getRarity(currentItem) ?: fallbackRarity()
        val lockedAffixId = getLockedAffixId(currentItem)
        val favorite = isFavorite(currentItem)
        val rolled = buildRolledLoot(
            current.definition,
            source,
            "局外重铸",
            current.definition.minFloor,
            current.forgeLevel,
            0,
            0,
            attributeBonusPerLevel,
            forcedRarity = currentRarity,
            lockedAffixId = lockedAffixId
        ) ?: return LootActionResult(false, "§c局外重铸失败，请检查装备配置。")
        makePermanent(rolled.item, player)
        setFavorite(rolled.item, favorite)
        setEquippedItem(player, slot, rolled.item)
        refreshEquippedSetBonuses(player)
        return LootActionResult(true, "§6局外重铸完成: §f${current.definition.name} §7的词条已更新。")
    }

    fun clearPermanentLockedAffix(player: Player, slot: EquipmentSlot): LootActionResult {
        val item = getEquippedItem(player, slot)
            ?: return LootActionResult(false, "§c该部位没有永久装备。")
        val definition = getDefinition(item)
            ?: return LootActionResult(false, "§c该部位没有永久装备。")
        if (!isPermanentLoot(item)) {
            return LootActionResult(false, "§c只有永久装备可以进行局外清锁。")
        }
        val meta = item.itemMeta ?: return LootActionResult(false, "§c清锁失败，请稍后再试。")
        meta.persistentDataContainer.remove(lockedAffixKey)
        applyLootMeta(item, meta)
        refreshSingleItemLore(item)
        refreshEquippedSetBonuses(player)
        return LootActionResult(true, "§7已清除 §f${definition.name} §7的锁定词条。")
    }

    fun getPermanentRarityUpgradeStep(player: Player, slot: EquipmentSlot): Int? {
        val item = getEquippedItem(player, slot) ?: return null
        if (!isPermanentLoot(item)) return null
        val rarity = getRarity(item) ?: return null
        val index = rarities.indexOfFirst { it.id.equals(rarity.id, ignoreCase = true) }
        return index.takeIf { it >= 0 && it < rarities.lastIndex }
    }

    fun getPermanentRarityName(player: Player, slot: EquipmentSlot): String? {
        val item = getEquippedItem(player, slot) ?: return null
        if (!isPermanentLoot(item)) return null
        return getRarity(item)?.displayName
    }

    fun getNextPermanentRarityName(player: Player, slot: EquipmentSlot): String? {
        val step = getPermanentRarityUpgradeStep(player, slot) ?: return null
        return rarities.getOrNull(step + 1)?.displayName
    }

    fun getNextPermanentRarityId(player: Player, slot: EquipmentSlot): String? {
        val step = getPermanentRarityUpgradeStep(player, slot) ?: return null
        return rarities.getOrNull(step + 1)?.id
    }

    fun getPermanentRarityId(player: Player, slot: EquipmentSlot): String? {
        val item = getEquippedItem(player, slot) ?: return null
        if (!isPermanentLoot(item)) return null
        return getRarity(item)?.id
    }

    fun upgradePermanentRarity(
        player: Player,
        slot: EquipmentSlot,
        attributeBonusPerLevel: Double
    ): LootActionResult {
        val currentItem = getEquippedItem(player, slot)
        val current = getPermanentEquippedLoot(player, slot)
            ?: return LootActionResult(false, "§c该部位没有可升品的永久装备。")
        val step = getPermanentRarityUpgradeStep(player, slot)
            ?: return LootActionResult(false, "§c这件永久装备已经是最高稀有度。")
        val nextRarity = rarities.getOrNull(step + 1)
            ?: return LootActionResult(false, "§c这件永久装备已经是最高稀有度。")
        val source = current.definition.sources.first()
        val lockedAffixId = getLockedAffixId(currentItem)
        val favorite = isFavorite(currentItem)
        val upgraded = buildRolledLoot(
            current.definition,
            source,
            "局外升品",
            current.definition.minFloor,
            current.forgeLevel,
            0,
            0,
            attributeBonusPerLevel,
            forcedRarity = nextRarity,
            lockedAffixId = lockedAffixId
        ) ?: return LootActionResult(false, "§c局外升品失败，请检查装备配置。")
        makePermanent(upgraded.item, player)
        setFavorite(upgraded.item, favorite)
        setEquippedItem(player, slot, upgraded.item)
        refreshEquippedSetBonuses(player)
        return LootActionResult(true, "§6局外升品完成: §f${current.definition.name} §7已提升为 ${nextRarity.color}${nextRarity.displayName}")
    }

    fun getPermanentSalvageReward(
        player: Player,
        slot: EquipmentSlot,
        baseReturn: Int,
        scoreScale: Double,
        forgeLevelReturn: Int,
        rarityReturn: Map<String, Int>
    ): Int {
        val equipped = getPermanentEquippedLoot(player, slot) ?: return 0
        val item = getEquippedItem(player, slot)
        val rarityId = getRarity(item)?.id?.lowercase() ?: "common"
        val rarityBonus = rarityReturn[rarityId] ?: 0
        return (baseReturn + (equipped.score * scoreScale).roundToInt() + equipped.forgeLevel * forgeLevelReturn + rarityBonus)
            .coerceAtLeast(0)
    }

    fun salvagePermanentEquipped(
        player: Player,
        slot: EquipmentSlot,
        baseReturn: Int,
        scoreScale: Double,
        forgeLevelReturn: Int,
        rarityReturn: Map<String, Int>
    ): LootSalvageResult {
        val equipped = getPermanentEquippedLoot(player, slot)
            ?: return LootSalvageResult(false, 0, "§c该部位没有可分解的永久装备。")
        val reward = getPermanentSalvageReward(player, slot, baseReturn, scoreScale, forgeLevelReturn, rarityReturn)
        setEquippedItem(player, slot, ItemStack(Material.AIR))
        refreshEquippedSetBonuses(player)
        return LootSalvageResult(true, reward, "§6永久分解完成: §f${equipped.definition.name} §7→ §6+$reward §e灵魂碎片")
    }

    private fun makePermanent(item: ItemStack, owner: Player? = null) {
        val meta = item.itemMeta ?: return
        if (owner != null && permanentGearBindOnConvert) {
            meta.persistentDataContainer.set(ownerUuidKey, PersistentDataType.STRING, owner.uniqueId.toString())
            meta.persistentDataContainer.set(ownerNameKey, PersistentDataType.STRING, owner.name)
        }
        meta.persistentDataContainer.set(permanentLootKey, PersistentDataType.BYTE, 1)
        meta.persistentDataContainer.remove(forgeHeatKey)
        applyLootMeta(item, meta)
        refreshPermanentLore(item, owner?.name ?: getPermanentOwnerName(item))
        DungeonBoundItem.unmark(item)
    }

    private fun refreshPermanentLore(item: ItemStack, ownerName: String?) {
        val meta = item.itemMeta ?: return
        val baseLore = getBaseLore(item)
        val permanentLore = buildList {
            addAll(baseLore.filterNot { line ->
                line.contains("离开副本后消失") ||
                    line.startsWith("§c锻造热度: ") ||
                    line.startsWith("§6灵魂铭刻: ") ||
                    line.startsWith("§6★ ") ||
                    line.startsWith("§7绑定者: ") ||
                    line.contains("由一次副本冒险保留下来的战利品")
            })
            add("")
            add("§6灵魂铭刻: 永久装备")
            if (isFavorite(item)) {
                add("§6★ 已收藏")
            }
            if (!ownerName.isNullOrBlank()) {
                add("§7绑定者: §f$ownerName")
            }
            add("§8由一次副本冒险保留下来的战利品")
        }
        meta.persistentDataContainer.set(baseLoreKey, PersistentDataType.STRING, permanentLore.joinToString("\n"))
        meta.lore = permanentLore
        applyLootMeta(item, meta)
    }

    private fun getEquippedDefinitions(player: Player): List<EquippedDefinitionView> {
        return listOf(
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET,
            EquipmentSlot.HAND,
            EquipmentSlot.OFF_HAND
        ).mapNotNull { slot ->
            val item = getEquippedItem(player, slot) ?: return@mapNotNull null
            if (!DungeonBoundItem.hasTag(item) && !isPermanentLoot(item)) {
                return@mapNotNull null
            }
            val definition = getDefinition(item) ?: return@mapNotNull null
            EquippedDefinitionView(slot, definition, item)
        }
    }

    private fun resolveActiveSetBonuses(equipped: List<EquippedDefinitionView>): List<ActiveSetBonus> {
        if (equipped.isEmpty() || setBonuses.isEmpty()) {
            return emptyList()
        }
        return setBonuses.mapNotNull { bonus ->
            val matched = equipped.filter { bonus.matches(it.definition) }
            val tiers = bonus.tiers.filter { matched.size >= it.count }
            if (tiers.isEmpty()) {
                null
            } else {
                ActiveSetBonus(
                    bonus = bonus,
                    matchCount = matched.size,
                    carrierSlot = matched.minByOrNull { slotPriority(it.slot) }!!.slot,
                    tiers = tiers
                )
            }
        }
    }

    private fun applySetBonusLore(view: EquippedDefinitionView, activeBonuses: List<ActiveSetBonus>) {
        val meta = view.item.itemMeta ?: return
        val baseLore = getBaseLore(view.item)
        val bonusesForItem = activeBonuses.filter { bonus ->
            bonus.carrierSlot == view.slot && bonus.bonus.matches(view.definition)
        }
        val finalLore = buildList {
            addAll(baseLore)
            if (bonusesForItem.isNotEmpty()) {
                add("")
                add("§6联动属性:")
                for (bonus in bonusesForItem) {
                    add("§8- §f${bonus.bonus.name} §7(${bonus.matchCount}件)")
                    for (tier in bonus.tiers) {
                        add("§e${tier.count}件激活")
                        addAll(tier.lore)
                        for (attribute in tier.attributes) {
                            add(renderAttributeLine(attribute.name, fixedAttributeValue(attribute), attribute.percentage))
                        }
                    }
                }
            }
        }
        meta.lore = finalLore
        applyLootMeta(view.item, meta)
    }

    private fun refreshSingleItemLore(item: ItemStack, explicitHeatCap: Int? = null) {
        val meta = item.itemMeta ?: return
        val lockedAffixId = getLockedAffixId(item)
        val affixes = getAffixes(item)
        if (affixes.isEmpty()) {
            meta.persistentDataContainer.remove(baseLoreKey)
            applyLootMeta(item, meta)
            return
        }
        val lore = getBaseLore(item).toMutableList()
        val randomAffixHeader = lore.indexOf("§7随机词条:")
        if (randomAffixHeader >= 0) {
            var cursor = randomAffixHeader + 1
            for (affix in affixes) {
                if (cursor >= lore.size) {
                    break
                }
                lore[cursor] = renderAffixTitle(affix, lockedAffixId)
                cursor += 1 + affix.lore.size
            }
        }
        val heatCap = explicitHeatCap ?: getHeatCapFromLore(lore)
        val heatIndex = lore.indexOfFirst { it.startsWith("§c锻造热度: ") }
        if (heatCap > 0) {
            val heatLine = "§c锻造热度: ${getForgeHeat(item)}/$heatCap"
            if (heatIndex >= 0) {
                lore[heatIndex] = heatLine
            } else {
                val sourceIndex = lore.indexOfFirst { it.startsWith("§8来源: ") }
                if (sourceIndex >= 0) {
                    lore.add(sourceIndex, heatLine)
                } else {
                    lore += heatLine
                }
            }
        } else if (heatIndex >= 0) {
            lore.removeAt(heatIndex)
        }
        meta.persistentDataContainer.set(baseLoreKey, PersistentDataType.STRING, lore.joinToString("\n"))
        meta.lore = lore
        applyLootMeta(item, meta)
    }

    private fun getBaseLore(item: ItemStack): List<String> {
        val meta = item.itemMeta ?: return emptyList()
        return meta.persistentDataContainer.get(baseLoreKey, PersistentDataType.STRING)
            ?.split("\n")
            ?: meta.lore
            ?: emptyList()
    }

    private fun applyLootMeta(item: ItemStack, meta: ItemMeta) {
        meta.isUnbreakable = true
        meta.hideAll()
        item.itemMeta = meta
    }

    private fun ItemMeta.hideAll() {
        addItemFlags(*ItemFlag.values())
    }

    private fun getHeatCapFromLore(lore: List<String>): Int {
        val line = lore.firstOrNull { it.startsWith("§c锻造热度: ") } ?: return 0
        val raw = line.substringAfter("/").trim()
        return raw.toIntOrNull() ?: 0
    }

    private fun fixedAttributeValue(attribute: DungeonLootAttributeDefinition): Double {
        return if (attribute.max > attribute.min) {
            (attribute.min + attribute.max) / 2.0
        } else {
            attribute.min
        }
    }

    private fun getLockedAffixId(item: ItemStack?): String? {
        if (item == null || item.type == Material.AIR) {
            return null
        }
        val meta = item.itemMeta ?: return null
        val locked = meta.persistentDataContainer.get(lockedAffixKey, PersistentDataType.STRING) ?: return null
        return locked.takeIf { id -> getAffixes(item).any { it.id.equals(id, ignoreCase = true) } }
    }

    private fun renderAffixTitle(affix: DungeonLootAffix, lockedAffixId: String?): String {
        val locked = lockedAffixId != null && affix.id.equals(lockedAffixId, ignoreCase = true)
        return if (locked) {
            "§8- §6[锁定] §f${affix.name}"
        } else {
            "§8- §f${affix.name}"
        }
    }

    private fun slotPriority(slot: EquipmentSlot): Int {
        return when (slot) {
            EquipmentSlot.HAND -> 0
            EquipmentSlot.OFF_HAND -> 1
            EquipmentSlot.HEAD -> 2
            EquipmentSlot.CHEST -> 3
            EquipmentSlot.LEGS -> 4
            EquipmentSlot.FEET -> 5
        }
    }

    private fun getComparableScore(item: ItemStack): Double {
        val stored = getScore(item)
        if (stored > 0.0) {
            return stored
        }
        val definition = getDefinition(item) ?: return 0.0
        return estimateDefinitionScore(definition)
    }

    private fun estimateDefinitionScore(definition: DungeonLootDefinition): Double {
        return definitionScoreCache.getOrPut(definition.id) {
            val attrScore = definition.attributes.sumOf { attribute ->
                ((attribute.min + attribute.max) / 2.0) * if (attribute.percentage) 1.5 else 1.0
            }
            attrScore
        }
    }

    private fun rollAttributeValue(attribute: DungeonLootAttributeDefinition, multiplier: Double, fixedRoll: Boolean = false): Double {
        val base = if (fixedRoll) {
            if (attribute.max > attribute.min) (attribute.min + attribute.max) / 2.0 else attribute.min
        } else if (attribute.max > attribute.min) {
            Random.nextDouble(attribute.min, attribute.max)
        } else {
            attribute.min
        }
        return capGeneratedAttribute("gear", attribute.name, base * multiplier)
    }

    private fun capGeneratedAttribute(scope: String, name: String, value: Double): Double {
        val cap = BalanceConfigManager.getDouble("attribute-caps.$scope.$name", Double.NaN)
        return if (cap.isNaN() || cap <= 0.0) value else value.coerceAtMost(cap)
    }

    private fun renderAttributeLine(name: String, value: Double, percentage: Boolean): String {
        val formatted = formatNumber(value)
        val mark = getPercentageMarkSuffix(name, percentage)
        return "§7$name: §a+$formatted$mark"
    }

    fun getPercentageMarkSuffix(name: String, percentage: Boolean): String {
        if (!percentage) {
            return ""
        }
        return if (shouldAppendPercentageMark(name)) " $apPercentageMark" else ""
    }

    private fun shouldAppendPercentageMark(name: String): Boolean {
        if (apPercentageMark.isBlank()) {
            return false
        }
        val normalized = name.trim().lowercase()
        if (normalized.isEmpty()) {
            return true
        }
        if (normalized in apPercentageMarkDisabledAttributes) {
            return false
        }
        if (apPercentageMarkDisabledKeywords.any { normalized.contains(it) }) {
            return false
        }
        return true
    }

    private fun formatNumber(value: Double): String {
        val rounded = (value * 100.0).roundToInt() / 100.0
        return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
    }

    private fun score(
        definition: DungeonLootDefinition,
        rarity: DungeonLootRarity,
        rolledAffixes: List<DungeonLootAffix>,
        forgeLevel: Int,
        attributeBonusPerLevel: Double
    ): Double {
        val attributes = definition.attributes + rolledAffixes.flatMap { it.attributes }
        val attrScore = attributes.sumOf { attribute ->
            ((attribute.min + attribute.max) / 2.0) * if (attribute.percentage) 1.5 else 1.0
        }
        return (attrScore + rolledAffixes.size * 4.0) *
            rarity.multiplier *
            (1.0 + forgeLevel * attributeBonusPerLevel)
    }

    fun getScore(item: ItemStack?): Double {
        if (item == null || item.type == Material.AIR) {
            return 0.0
        }
        val meta = item.itemMeta ?: return 0.0
        return meta.persistentDataContainer.get(scoreKey, PersistentDataType.DOUBLE) ?: 0.0
    }

    fun getRarity(item: ItemStack?): DungeonLootRarity? {
        if (item == null || item.type == Material.AIR) {
            return null
        }
        val meta = item.itemMeta ?: return null
        val rarityId = meta.persistentDataContainer.get(rarityKey, PersistentDataType.STRING) ?: return null
        return rarities.firstOrNull { it.id.equals(rarityId, ignoreCase = true) }
    }

    private fun getAffixes(item: ItemStack?): List<DungeonLootAffix> {
        if (item == null || item.type == Material.AIR) {
            return emptyList()
        }
        val meta = item.itemMeta ?: return emptyList()
        val ids = meta.persistentDataContainer.get(affixKey, PersistentDataType.STRING)
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: return emptyList()
        return ids.mapNotNull { id -> affixes.firstOrNull { it.id.equals(id, ignoreCase = true) } }
    }

    private fun getSalvageReward(
        score: Double,
        forgeLevel: Int,
        baseReward: Int,
        scoreScale: Double,
        forgeBonusPerLevel: Int
    ): Int {
        val scaled = baseReward + (score * scoreScale).toInt() + forgeLevel * forgeBonusPerLevel
        return scaled.coerceAtLeast(1)
    }

    private fun getEquippedItem(player: Player, slot: EquipmentSlot): ItemStack? {
        return when (slot) {
            EquipmentSlot.HAND -> player.inventory.itemInMainHand
            EquipmentSlot.OFF_HAND -> player.inventory.itemInOffHand
            EquipmentSlot.HEAD -> player.inventory.helmet
            EquipmentSlot.CHEST -> player.inventory.chestplate
            EquipmentSlot.LEGS -> player.inventory.leggings
            EquipmentSlot.FEET -> player.inventory.boots
        }
    }

    private fun setEquippedItem(player: Player, slot: EquipmentSlot, item: ItemStack) {
        when (slot) {
            EquipmentSlot.HAND -> player.inventory.setItemInMainHand(item)
            EquipmentSlot.OFF_HAND -> player.inventory.setItemInOffHand(item)
            EquipmentSlot.HEAD -> player.inventory.helmet = item
            EquipmentSlot.CHEST -> player.inventory.chestplate = item
            EquipmentSlot.LEGS -> player.inventory.leggings = item
            EquipmentSlot.FEET -> player.inventory.boots = item
        }
    }

    private fun getExtraRolls(instance: DungeonInstance): Int {
        return maxOf(0, AffixManager.getWeaponLuckMultiplier(instance).toInt() - 1)
    }

    private fun getRelicExtraRoll(player: Player): Int {
        val chance = RelicEffectHandler.getBonusLootChance(player)
        if (chance <= 0.0) {
            return 0
        }
        val guaranteed = (chance / 100.0).toInt()
        val remainder = chance - guaranteed * 100.0
        return guaranteed + if (Random.nextDouble() * 100.0 < remainder) 1 else 0
    }

    private fun parseRange(range: String): IntRange {
        val parts = range.split("-")
        val min = parts.getOrNull(0)?.toIntOrNull() ?: 1
        val max = parts.getOrNull(1)?.toIntOrNull() ?: 999
        return min..max
    }

    private fun parseSource(source: String): DungeonLootSource? {
        return try {
            DungeonLootSource.valueOf(source.uppercase())
        } catch (_: Exception) {
            null
        }
    }

    private fun parseSlot(slot: String?): EquipmentSlot? {
        if (slot.isNullOrBlank()) {
            return null
        }
        return try {
            EquipmentSlot.valueOf(slot.uppercase())
        } catch (_: Exception) {
            null
        }
    }

    private fun parseSlots(slots: List<String>): Set<EquipmentSlot> {
        return slots.mapNotNull { parseSlot(it) }.toSet()
    }

    private fun parseLowercaseSet(values: List<String>): Set<String> {
        return values.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
    }

    private fun parseSlotWeight(section: ConfigurationSection?): Map<EquipmentSlot, Double> {
        if (section == null) {
            return emptyMap()
        }
        val result = mutableMapOf<EquipmentSlot, Double>()
        for (key in section.getKeys(false)) {
            val slot = parseSlot(key) ?: continue
            result[slot] = section.getDouble(key, 1.0).coerceAtLeast(0.0)
        }
        return result
    }

    private fun parseSourceWeight(section: ConfigurationSection?): Map<DungeonLootSource, Double> {
        if (section == null) {
            return emptyMap()
        }
        val result = mutableMapOf<DungeonLootSource, Double>()
        for (key in section.getKeys(false)) {
            val source = parseSource(key) ?: continue
            result[source] = section.getDouble(key, 1.0).coerceAtLeast(0.0)
        }
        return result
    }

    private fun parseAffixType(type: String?): DungeonLootAffixType? {
        if (type.isNullOrBlank()) {
            return null
        }
        return try {
            DungeonLootAffixType.valueOf(type.uppercase())
        } catch (_: Exception) {
            null
        }
    }

    private fun parseAttributes(section: ConfigurationSection?): List<DungeonLootAttributeDefinition> {
        if (section == null) {
            return emptyList()
        }
        val result = mutableListOf<DungeonLootAttributeDefinition>()
        for (key in section.getKeys(false)) {
            val raw = section.get(key)
            val percentage = key.endsWith("%") || key.endsWith("％")
            val name = key.removeSuffix("%").removeSuffix("％")
            val values = when (raw) {
                is List<*> -> {
                    val min = raw.getOrNull(0)?.toString()?.toDoubleOrNull() ?: 0.0
                    val max = raw.getOrNull(1)?.toString()?.toDoubleOrNull() ?: min
                    min to max
                }
                is Number -> raw.toDouble() to raw.toDouble()
                else -> raw?.toString()?.toDoubleOrNull()?.let { it to it } ?: (0.0 to 0.0)
            }
            result += DungeonLootAttributeDefinition(name, values.first, values.second, percentage)
        }
        return result
    }

    private fun <T> weightedRandom(pool: List<T>, weight: (T) -> Int): T? {
        if (pool.isEmpty()) {
            return null
        }
        val total = pool.sumOf { weight(it).coerceAtLeast(0) }
        if (total <= 0) {
            return pool.randomOrNull()
        }
        var roll = Random.nextInt(total)
        for (entry in pool) {
            roll -= weight(entry).coerceAtLeast(0)
            if (roll < 0) {
                return entry
            }
        }
        return pool.last()
    }

    private fun weightedRandom(pool: List<DungeonLootDefinition>): DungeonLootDefinition? {
        return weightedRandom(pool, DungeonLootDefinition::weight)
    }

    private fun scheduleSetBonusRefresh(player: Player) {
        submit(delay = 1L) {
            if (player.isOnline && DungeonManager.isInDungeon(player)) {
                refreshEquippedSetBonuses(player)
            }
        }
    }

    @SubscribeEvent(ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        if (!DungeonManager.isInDungeon(player)) {
            return
        }
        if (getEquippedDefinitions(player).isEmpty() &&
            !DungeonBoundItem.hasTag(event.currentItem) &&
            !DungeonBoundItem.hasTag(event.cursor)
        ) {
            return
        }
        scheduleSetBonusRefresh(player)
    }

    @SubscribeEvent(ignoreCancelled = true)
    fun onInventoryDrag(event: InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return
        if (!DungeonManager.isInDungeon(player)) {
            return
        }
        if (getEquippedDefinitions(player).isEmpty() && !DungeonBoundItem.hasTag(event.oldCursor)) {
            return
        }
        scheduleSetBonusRefresh(player)
    }

    @SubscribeEvent(ignoreCancelled = true)
    fun onItemHeld(event: PlayerItemHeldEvent) {
        if (!DungeonManager.isInDungeon(event.player)) {
            return
        }
        scheduleSetBonusRefresh(event.player)
    }

    @SubscribeEvent(ignoreCancelled = true)
    fun onSwapHand(event: PlayerSwapHandItemsEvent) {
        if (!DungeonManager.isInDungeon(event.player)) {
            return
        }
        scheduleSetBonusRefresh(event.player)
    }

    private fun DungeonLootSource.displayName(): String {
        return when (this) {
            DungeonLootSource.CHEST -> "共享宝箱"
            DungeonLootSource.ELITE -> "精英战利品"
            DungeonLootSource.BOSS -> "Boss 战利品"
            DungeonLootSource.HIDDEN -> "隐藏宝藏"
        }
    }

    private fun ForgeBookQuality.displayName(): String {
        return if (name.equals(id, ignoreCase = true)) {
            ContentDisplayNameResolver.safeText(id, "锻造书")
        } else {
            name
        }
    }

    private fun materialTypeName(type: Material): String {
        return ContentDisplayNameResolver.materialTypeName(type.name, "物品")
    }

    private fun slotDisplayName(slot: EquipmentSlot?): String {
        return slot?.displayName() ?: "未绑定"
    }

    private fun EquipmentSlot.displayName(): String {
        return when (this) {
            EquipmentSlot.HAND -> "主手"
            EquipmentSlot.OFF_HAND -> "副手"
            EquipmentSlot.HEAD -> "头盔"
            EquipmentSlot.CHEST -> "胸甲"
            EquipmentSlot.LEGS -> "护腿"
            EquipmentSlot.FEET -> "靴子"
        }
    }
}
