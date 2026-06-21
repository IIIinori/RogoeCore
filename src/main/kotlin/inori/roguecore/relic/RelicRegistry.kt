package inori.roguecore.relic

import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.info
import taboolib.common.platform.function.warning
import taboolib.library.xseries.XMaterial
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

object RelicRegistry {

    @Config("relics.yml")
    lateinit var config: Configuration
        private set

    private val relics = mutableMapOf<String, Relic>()

    fun getAll(): Collection<Relic> = relics.values

    fun get(id: String): Relic? = relics[id]

    @Awake(LifeCycle.ENABLE)
    fun load() {
        relics.clear()
        val section = config.getConfigurationSection("relics") ?: run {
            warning("[RogueCore] relics.yml 中未找到 relics 节点")
            return
        }

        for (id in section.getKeys(false)) {
            val node = section.getConfigurationSection(id) ?: continue
            try {
                relics[id] = Relic(
                    id = id,
                    name = node.getString("name")?.takeUnless { it == id } ?: "未命名遗物",
                    description = node.getString("description") ?: "",
                    rarity = runCatching { RelicRarity.valueOf((node.getString("rarity") ?: "COMMON").uppercase()) }
                        .getOrDefault(RelicRarity.COMMON),
                    icon = XMaterial.matchXMaterial(node.getString("icon") ?: "PAPER").orElse(XMaterial.PAPER),
                    effectType = runCatching { RelicEffectType.valueOf((node.getString("effect-type") ?: "KILL_SHARD").uppercase()) }
                        .getOrDefault(RelicEffectType.KILL_SHARD),
                    value = node.getDouble("value", 0.0),
                    threshold = node.getDouble("threshold", 0.0),
                    requiredUnlock = node.getString("required-unlock")?.takeIf { it.isNotBlank() }
                )
            } catch (ex: Exception) {
                warning("[RogueCore] 加载遗物 '$id' 失败: ${ex.message}")
            }
        }

        info("[RogueCore] 已加载 ${relics.size} 个遗物")
    }
}
