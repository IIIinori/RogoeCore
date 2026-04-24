package inori.roguecore.boon

import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.info
import taboolib.common.platform.function.warning
import taboolib.library.xseries.XMaterial
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

/**
 * Boon 注册表 — 从 boons.yml 加载所有 Boon 定义
 */
object BoonRegistry {

    @Config("boons.yml")
    lateinit var config: Configuration
        private set

    private val boons = mutableMapOf<String, Boon>()

    /** 获取所有 Boon */
    fun getAll(): Collection<Boon> = boons.values

    /** 通过 ID 获取 Boon */
    fun get(id: String): Boon? = boons[id]

    /** 按稀有度获取 */
    fun getByRarity(rarity: BoonRarity): List<Boon> = boons.values.filter { it.rarity == rarity }

    @Awake(LifeCycle.ENABLE)
    fun load() {
        boons.clear()
        val section = config.getConfigurationSection("boons") ?: run {
            warning("[RogueCore] boons.yml 中未找到 boons 节点")
            return
        }

        for (id in section.getKeys(false)) {
            val boonSection = section.getConfigurationSection(id) ?: continue
            try {
                val name = boonSection.getString("name") ?: id
                val description = boonSection.getString("description") ?: ""
                val rarityStr = boonSection.getString("rarity") ?: "COMMON"
                val rarity = try { BoonRarity.valueOf(rarityStr.uppercase()) } catch (_: Exception) { BoonRarity.COMMON }
                val iconStr = boonSection.getString("icon") ?: "PAPER"
                val icon = XMaterial.matchXMaterial(iconStr).orElse(XMaterial.PAPER)
                val maxLevel = boonSection.getInt("max-level", 3)
                val tags = boonSection.getStringList("tags")

                // 解析属性
                val attributes = mutableMapOf<String, DoubleArray>()
                val attrSection = boonSection.getConfigurationSection("attributes")
                if (attrSection != null) {
                    for (attrName in attrSection.getKeys(false)) {
                        val values = attrSection.getDoubleList(attrName)
                        if (values.size >= 2) {
                            attributes[attrName] = doubleArrayOf(values[0], values[1])
                        }
                    }
                }

                val effects = mutableListOf<BoonEffect>()
                val effectSection = boonSection.getConfigurationSection("effects")
                if (effectSection != null) {
                    for (effectKey in effectSection.getKeys(false)) {
                        val sectionNode = effectSection.getConfigurationSection(effectKey) ?: continue
                        val typeName = sectionNode.getString("type") ?: continue
                        val type = try {
                            BoonEffectType.valueOf(typeName.uppercase())
                        } catch (_: Exception) {
                            continue
                        }
                        effects += BoonEffect(
                            type = type,
                            valuePerLevel = sectionNode.getDouble("value-per-level", 0.0),
                            threshold = sectionNode.getDouble("threshold", 0.0),
                            tag = sectionNode.getString("tag") ?: "",
                            perTag = sectionNode.getDouble("per-tag", 0.0)
                        )
                    }
                }

                val boon = Boon(
                    id = id,
                    name = name,
                    description = description,
                    rarity = rarity,
                    icon = icon,
                    maxLevel = maxLevel,
                    attributes = attributes,
                    tags = tags,
                    effects = effects
                )
                boons[id] = boon
            } catch (e: Exception) {
                warning("[RogueCore] 加载 Boon '$id' 失败: ${e.message}")
            }
        }

        info("[RogueCore] 已加载 ${boons.size} 个神恩")
    }
}
