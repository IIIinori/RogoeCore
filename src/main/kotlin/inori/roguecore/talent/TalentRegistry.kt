package inori.roguecore.talent

import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.info
import taboolib.common.platform.function.warning
import taboolib.library.xseries.XMaterial
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

/**
 * 天赋注册表 — 从 talents.yml 加载
 */
object TalentRegistry {

    @Config("talents.yml")
    lateinit var config: Configuration
        private set

    private val talents = mutableMapOf<String, Talent>()

    fun getAll(): Collection<Talent> = talents.values

    fun get(id: String): Talent? = talents[id]

    @Awake(LifeCycle.ENABLE)
    fun load() {
        talents.clear()
        val section = config.getConfigurationSection("talents") ?: return

        for (id in section.getKeys(false)) {
            val s = section.getConfigurationSection(id) ?: continue
            try {
                val name = s.getString("name") ?: id
                val description = s.getString("description") ?: ""
                val iconStr = s.getString("icon") ?: "PAPER"
                val icon = XMaterial.matchXMaterial(iconStr).orElse(XMaterial.PAPER)
                val maxLevel = s.getInt("max-level", 3)
                val cost = s.getIntegerList("cost").ifEmpty { listOf(100, 200, 400) }
                val effectTypeStr = s.getString("effect-type") ?: "ATTRIBUTE"
                val effectType = try { TalentEffectType.valueOf(effectTypeStr.uppercase()) } catch (_: Exception) { TalentEffectType.ATTRIBUTE }
                val attribute = s.getString("attribute") ?: ""
                val valuePerLevel = s.getDouble("value-per-level", 0.0)

                talents[id] = Talent(id, name, description, icon, maxLevel, cost, effectType, attribute, valuePerLevel)
            } catch (e: Exception) {
                warning("[RogueCore] 加载天赋 '$id' 失败: ${e.message}")
            }
        }

        info("[RogueCore] 已加载 ${talents.size} 个天赋")
    }
}
