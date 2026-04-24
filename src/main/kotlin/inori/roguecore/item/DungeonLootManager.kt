package inori.roguecore.item

import inori.roguecore.affix.AffixManager
import inori.roguecore.dungeon.DungeonInstance
import inori.roguecore.dungeon.DungeonManager
import inori.roguecore.relic.RelicEffectHandler
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
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
    private var protectionEnabled = true
    private var protectionEmptySlotWeight = 2.1
    private var protectionDuplicatePenalty = 0.72
    private var protectionUpgradeScoreScale = 0.02
    private var protectionMaxUpgradeBonus = 0.75
    private var protectionDowngradePenaltyScale = 0.012
    private var protectionMinFactor = 0.25

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

        chestChance = config.getDouble("rules.chest-chance", 0.4)
        eliteCount = config.getInt("rules.elite-count", 1).coerceAtLeast(1)
        bossCount = config.getInt("rules.boss-count", 1).coerceAtLeast(1)
        apPercentageMark = config.getString("rules.ap-percentage-mark") ?: "(%)"
        loadDropProtectionRules()
        loadSourceBonuses()

        loadRarities()
        loadSetBonuses()
        loadAffixes()
        loadLootDefinitions()

        info("[RogueCore] 已加载 ${definitions.size} 个临时装备定义, ${rarities.size} 个稀有度, ${affixes.size} 个随机词条, ${setBonuses.size} 个装备联动")
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
                displayName = node.getString("name") ?: id,
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
                name = node.getString("name") ?: id,
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
                name = node.getString("name") ?: id,
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
                val name = node.getString("name") ?: id
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
        val pool = definitions.filter { it.matches(source, instance.config.floorNumber) }
        if (pool.isEmpty()) {
            return false
        }

        var granted = false
        repeat(count.coerceAtLeast(1)) {
            val definition = weightedRandom(pool) { candidate ->
                effectiveLootWeight(player, candidate)
            } ?: return@repeat
            val rolled = buildRolledLoot(definition, source, source.displayName(), instance.config.floorNumber, 0, 0, 0, 0.0)
                ?: return@repeat
            give(player, rolled.item)
            granted = true
        }
        return granted
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
                val value = rollAttributeValue(attribute, multiplier)
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
        item.itemMeta = meta

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
            player.sendMessage("§6获得临时装备: §f${item.itemMeta?.displayName ?: item.type.name}")
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

    private fun getDefinition(item: ItemStack?): DungeonLootDefinition? {
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
            currentRarity,
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
            currentRarity,
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
            currentRarity,
            currentAffixes,
            lockedAffixId
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
            currentRarity,
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
        item.itemMeta = meta
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
        item.itemMeta = meta
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
            if (!DungeonBoundItem.hasTag(item)) {
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
        view.item.itemMeta = meta
    }

    private fun refreshSingleItemLore(item: ItemStack, explicitHeatCap: Int? = null) {
        val meta = item.itemMeta ?: return
        val lockedAffixId = getLockedAffixId(item)
        val affixes = getAffixes(item)
        if (affixes.isEmpty()) {
            meta.persistentDataContainer.remove(baseLoreKey)
            item.itemMeta = meta
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
        item.itemMeta = meta
    }

    private fun getBaseLore(item: ItemStack): List<String> {
        val meta = item.itemMeta ?: return emptyList()
        return meta.persistentDataContainer.get(baseLoreKey, PersistentDataType.STRING)
            ?.split("\n")
            ?: meta.lore
            ?: emptyList()
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

    private fun rollAttributeValue(attribute: DungeonLootAttributeDefinition, multiplier: Double): Double {
        val base = if (attribute.max > attribute.min) {
            Random.nextDouble(attribute.min, attribute.max)
        } else {
            attribute.min
        }
        return base * multiplier
    }

    private fun renderAttributeLine(name: String, value: Double, percentage: Boolean): String {
        val formatted = formatNumber(value)
        val mark = if (percentage) " $apPercentageMark" else ""
        return "§7$name: §a+$formatted$mark"
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

    private fun getScore(item: ItemStack?): Double {
        if (item == null || item.type == Material.AIR) {
            return 0.0
        }
        val meta = item.itemMeta ?: return 0.0
        return meta.persistentDataContainer.get(scoreKey, PersistentDataType.DOUBLE) ?: 0.0
    }

    private fun getRarity(item: ItemStack?): DungeonLootRarity? {
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
            val percentage = key.endsWith("%")
            val name = key.removeSuffix("%")
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
