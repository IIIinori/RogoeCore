package inori.roguecore.accessory

import inori.roguecore.data.PermanentMaterialManager
import inori.roguecore.item.DungeonLootAttributeDefinition
import inori.roguecore.item.DungeonLootSource
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.info
import taboolib.common.platform.function.warning
import taboolib.library.configuration.ConfigurationSection
import taboolib.library.xseries.XMaterial
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

object AccessoryRegistry {

    @Config("accessories.yml")
    lateinit var config: Configuration
        private set

    private val definitions = mutableMapOf<String, AccessoryDefinition>()
    private val rarities = mutableMapOf<String, AccessoryRarity>()
    private val inscriptionQualities = linkedMapOf<String, AccessoryInscriptionQuality>()

    var chestChance: Double = 0.18
        private set
    var eliteChance: Double = 0.32
        private set
    var bossCount: Int = 1
        private set
    var hiddenCount: Int = 1
        private set

    var identificationEnabled: Boolean = true
        private set
    var identificationQueueSize: Int = 3
        private set
    var identificationBasePrice: Int = 70
        private set
    var identificationFloorPrice: Int = 5
        private set
    var identificationBossExtraPrice: Int = 100
        private set
    var identificationHiddenExtraPrice: Int = 70
        private set

    var identificationAccelerationEnabled: Boolean = true
        private set
    var identificationAccelerationSoulShards: Int = 55
        private set
    var identificationAccelerationReducePercent: Double = 0.35
        private set
    var identificationAccelerationMinReduceSeconds: Int = 5
        private set
    private var identificationAccelerationMaterials: Map<PermanentMaterialManager.MaterialType, Int> = emptyMap()

    var inscriptionEnabled: Boolean = true
        private set
    var inscriptionQueueSize: Int = 2
        private set
    var inscriptionAccelerationEnabled: Boolean = true
        private set
    var inscriptionAccelerationSoulShards: Int = 130
        private set
    var inscriptionAccelerationReducePercent: Double = 0.25
        private set
    var inscriptionAccelerationMinReduceSeconds: Int = 30
        private set
    private var inscriptionAccelerationMaterials: Map<PermanentMaterialManager.MaterialType, Int> = emptyMap()

    private val sourceRarityUpgradeChance = mutableMapOf<DungeonLootSource, Double>()
    private val sealedDropChance = mutableMapOf<DungeonLootSource, Double>()
    private val identifyTimeBySource = mutableMapOf<DungeonLootSource, Long>()
    private val inscriptionDropChance = mutableMapOf<DungeonLootSource, Double>()

    fun getAll(): Collection<AccessoryDefinition> = definitions.values

    fun get(id: String): AccessoryDefinition? = definitions[id]

    fun getRarity(id: String): AccessoryRarity? = rarities[id.lowercase()]

    fun getRarities(): List<AccessoryRarity> = rarities.values.toList()

    fun getInscriptionQuality(id: String): AccessoryInscriptionQuality? = inscriptionQualities[id.lowercase()]

    fun getInscriptionQualities(): List<AccessoryInscriptionQuality> = inscriptionQualities.values.toList()

    fun getSourceRarityUpgradeChance(source: DungeonLootSource): Double = sourceRarityUpgradeChance[source] ?: 0.0

    fun getSealedDropChance(source: DungeonLootSource): Double = sealedDropChance[source] ?: 0.0

    fun getIdentifyTimeMillis(source: DungeonLootSource): Long = identifyTimeBySource[source] ?: 15000L

    fun getInscriptionDropChance(source: DungeonLootSource): Double = inscriptionDropChance[source] ?: 0.0

    fun getIdentificationAccelerationMaterials(): Map<PermanentMaterialManager.MaterialType, Int> = identificationAccelerationMaterials

    fun getInscriptionAccelerationMaterials(): Map<PermanentMaterialManager.MaterialType, Int> = inscriptionAccelerationMaterials

    fun getConfiguredAttributeNames(): Set<String> {
        return definitions.values.flatMap { definition -> definition.attributes.map { it.name } }.toSet()
    }

    fun getPool(source: DungeonLootSource, floor: Int): List<AccessoryDefinition> {
        return definitions.values.filter { it.matches(source, floor) }
    }

    @Awake(LifeCycle.ENABLE)
    fun load() {
        definitions.clear()
        rarities.clear()
        inscriptionQualities.clear()
        sourceRarityUpgradeChance.clear()
        sealedDropChance.clear()
        identifyTimeBySource.clear()
        inscriptionDropChance.clear()

        loadRules()
        loadIdentificationSettings()
        loadInscriptionSettings()
        loadRarities()
        loadDefinitions()
        info("[RogueCore] 已加载 ${definitions.size} 个饰品，${inscriptionQualities.size} 个饰品刻印品质")
    }

    private fun loadRules() {
        val rules = config.getConfigurationSection("rules")
        chestChance = rules?.getDouble("chest-chance", 0.18)?.coerceIn(0.0, 1.0) ?: 0.18
        eliteChance = rules?.getDouble("elite-chance", 0.32)?.coerceIn(0.0, 1.0) ?: 0.32
        bossCount = rules?.getInt("boss-count", 1)?.coerceAtLeast(0) ?: 1
        hiddenCount = rules?.getInt("hidden-count", 1)?.coerceAtLeast(0) ?: 1
        rules?.getConfigurationSection("rarity-upgrade")?.let { section ->
            for (key in section.getKeys(false)) {
                parseSource(key)?.let { source ->
                    sourceRarityUpgradeChance[source] = section.getDouble(key, 0.0).coerceIn(0.0, 1.0)
                }
            }
        }
    }

    private fun loadIdentificationSettings() {
        val section = config.getConfigurationSection("identification")
        identificationEnabled = section?.getBoolean("enabled", true) ?: true
        identificationQueueSize = section?.getInt("queue-size", 3)?.coerceAtLeast(1) ?: 3
        identificationBasePrice = section?.getInt("base-price", 70)?.coerceAtLeast(0) ?: 70
        identificationFloorPrice = section?.getInt("floor-price", 5)?.coerceAtLeast(0) ?: 5
        identificationBossExtraPrice = section?.getInt("boss-extra-price", 100)?.coerceAtLeast(0) ?: 100
        identificationHiddenExtraPrice = section?.getInt("hidden-extra-price", 70)?.coerceAtLeast(0) ?: 70
        section?.getConfigurationSection("drop-as-sealed-chance")?.let { node ->
            for (key in node.getKeys(false)) {
                parseSource(key)?.let { source -> sealedDropChance[source] = node.getDouble(key, 0.0).coerceIn(0.0, 1.0) }
            }
        }
        section?.getConfigurationSection("time-by-source")?.let { node ->
            for (key in node.getKeys(false)) {
                parseSource(key)?.let { source -> identifyTimeBySource[source] = node.getInt(key, 15).coerceAtLeast(1) * 1000L }
            }
        }
        val acceleration = section?.getConfigurationSection("acceleration")
        identificationAccelerationEnabled = acceleration?.getBoolean("enabled", true) ?: true
        identificationAccelerationSoulShards = acceleration?.getInt("soul-shards", 55)?.coerceAtLeast(0) ?: 55
        identificationAccelerationReducePercent = acceleration?.getDouble("reduce-percent-of-remaining", 0.35)?.coerceIn(0.0, 1.0) ?: 0.35
        identificationAccelerationMinReduceSeconds = acceleration?.getInt("min-reduce-seconds", 5)?.coerceAtLeast(0) ?: 5
        identificationAccelerationMaterials = PermanentMaterialManager.parseCost(acceleration?.getConfigurationSection("materials"))
    }

    private fun loadInscriptionSettings() {
        val section = config.getConfigurationSection("inscription-books")
        inscriptionEnabled = section?.getBoolean("enabled", true) ?: true
        inscriptionQueueSize = section?.getInt("queue-size", 2)?.coerceAtLeast(1) ?: 2
        section?.getConfigurationSection("drop-chance")?.let { node ->
            for (key in node.getKeys(false)) {
                parseSource(key)?.let { source -> inscriptionDropChance[source] = node.getDouble(key, 0.0).coerceIn(0.0, 1.0) }
            }
        }
        val acceleration = section?.getConfigurationSection("acceleration")
        inscriptionAccelerationEnabled = acceleration?.getBoolean("enabled", true) ?: true
        inscriptionAccelerationSoulShards = acceleration?.getInt("soul-shards", 130)?.coerceAtLeast(0) ?: 130
        inscriptionAccelerationReducePercent = acceleration?.getDouble("reduce-percent-of-remaining", 0.25)?.coerceIn(0.0, 1.0) ?: 0.25
        inscriptionAccelerationMinReduceSeconds = acceleration?.getInt("min-reduce-seconds", 30)?.coerceAtLeast(0) ?: 30
        inscriptionAccelerationMaterials = PermanentMaterialManager.parseCost(acceleration?.getConfigurationSection("materials"))

        val qualities = section?.getConfigurationSection("qualities")
        if (qualities == null) {
            inscriptionQualities["rough"] = AccessoryInscriptionQuality("rough", "粗糙", "§f", 60, 60_000L, 120, emptyMap(), 0.0, 1.0)
            return
        }
        for (id in qualities.getKeys(false)) {
            val node = qualities.getConfigurationSection(id) ?: continue
            inscriptionQualities[id.lowercase()] = AccessoryInscriptionQuality(
                id = id.lowercase(),
                displayName = node.getString("name")?.takeUnless { it == id } ?: "未命名品质",
                color = node.getString("color") ?: "§f",
                weight = node.getInt("weight", 10).coerceAtLeast(1),
                timeMillis = node.getInt("time-seconds", 60).coerceAtLeast(1) * 1000L,
                soulShards = node.getInt("soul-shards", 120).coerceAtLeast(0),
                materials = PermanentMaterialManager.parseCost(node.getConfigurationSection("materials")),
                rarityLuck = node.getDouble("rarity-luck", 0.0).coerceAtLeast(0.0),
                valueMultiplier = node.getDouble("value-multiplier", 1.0).coerceAtLeast(0.01)
            )
        }
        if (inscriptionQualities.isEmpty()) {
            inscriptionQualities["rough"] = AccessoryInscriptionQuality("rough", "粗糙", "§f", 60, 60_000L, 120, emptyMap(), 0.0, 1.0)
        }
    }

    private fun loadRarities() {
        val section = config.getConfigurationSection("rarities")
        if (section == null) {
            rarities["common"] = AccessoryRarity("common", "普通", "§f", 60, 1.0)
            return
        }
        for (id in section.getKeys(false)) {
            val node = section.getConfigurationSection(id) ?: continue
            rarities[id.lowercase()] = AccessoryRarity(
                id = id.lowercase(),
                displayName = node.getString("name")?.takeUnless { it == id } ?: "未命名稀有度",
                color = node.getString("color") ?: "§f",
                weight = node.getInt("weight", 10).coerceAtLeast(1),
                multiplier = node.getDouble("multiplier", 1.0).coerceAtLeast(0.01)
            )
        }
        if (rarities.isEmpty()) {
            rarities["common"] = AccessoryRarity("common", "普通", "§f", 60, 1.0)
        }
    }

    private fun loadDefinitions() {
        val section = config.getConfigurationSection("accessories") ?: run {
            warning("[RogueCore] accessories.yml 中未找到 accessories 节点")
            return
        }
        for (id in section.getKeys(false)) {
            val node = section.getConfigurationSection(id) ?: continue
            try {
                val slot = AccessorySlot.parse(node.getString("slot")) ?: continue
                val sources = node.getStringList("sources").mapNotNull(::parseSource).toSet()
                if (sources.isEmpty()) continue
                val range = parseRange(node.getString("range") ?: "1-100")
                definitions[id] = AccessoryDefinition(
                    id = id,
                    name = node.getString("name")?.takeUnless { it == id } ?: "未命名饰品",
                    material = XMaterial.matchXMaterial(node.getString("material") ?: "AMETHYST_SHARD").orElse(XMaterial.AMETHYST_SHARD),
                    slot = slot,
                    tags = node.getStringList("tags").map { it.lowercase() }.toSet(),
                    sources = sources,
                    minFloor = range.first,
                    maxFloor = range.last,
                    weight = node.getInt("weight", 10).coerceAtLeast(1),
                    lore = node.getStringList("lore"),
                    attributes = parseAttributes(node.getConfigurationSection("attributes")),
                    effects = parseEffects(node.getConfigurationSection("effects"))
                )
            } catch (ex: Exception) {
                warning("[RogueCore] 加载饰品 '$id' 失败: ${ex.message}")
            }
        }
    }

    private fun parseEffects(section: ConfigurationSection?): List<AccessoryEffect> {
        if (section == null) return emptyList()
        val result = mutableListOf<AccessoryEffect>()
        for (key in section.getKeys(false)) {
            val node = section.getConfigurationSection(key) ?: continue
            val type = runCatching { AccessoryEffectType.valueOf((node.getString("type") ?: "").uppercase()) }.getOrNull() ?: continue
            result += AccessoryEffect(
                type = type,
                value = node.getDouble("value", 0.0),
                chance = node.getDouble("chance", 1.0).coerceIn(0.0, 1.0),
                tag = node.getString("tag") ?: ""
            )
        }
        return result
    }

    private fun parseAttributes(section: ConfigurationSection?): List<DungeonLootAttributeDefinition> {
        if (section == null) return emptyList()
        val result = mutableListOf<DungeonLootAttributeDefinition>()
        for (rawName in section.getKeys(false)) {
            val values = section.getDoubleList(rawName)
            if (values.size < 2) continue
            val percentage = rawName.endsWith("%") || rawName.endsWith("％")
            val name = rawName.removeSuffix("%").removeSuffix("％").trim()
            result += DungeonLootAttributeDefinition(name, values[0], values[1], percentage)
        }
        return result
    }

    private fun parseRange(range: String): IntRange {
        val parts = range.split("-")
        val min = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: 1
        val max = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: min
        return min.coerceAtLeast(1)..max.coerceAtLeast(min)
    }

    private fun parseSource(value: String?): DungeonLootSource? {
        if (value.isNullOrBlank()) return null
        return runCatching { DungeonLootSource.valueOf(value.trim().uppercase()) }.getOrNull()
    }
}
