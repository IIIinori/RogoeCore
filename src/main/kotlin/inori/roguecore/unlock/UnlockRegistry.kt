package inori.roguecore.unlock

import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.info
import taboolib.common.platform.function.warning
import taboolib.library.xseries.XMaterial
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

object UnlockRegistry {

    @Config("unlocks.yml")
    lateinit var config: Configuration
        private set

    private val unlocks = mutableMapOf<String, UnlockDefinition>()

    fun getAll(): Collection<UnlockDefinition> = unlocks.values

    fun getAllSorted(): List<UnlockDefinition> = unlocks.values.sortedBy { it.slot }

    fun get(id: String): UnlockDefinition? = unlocks[id]

    @Awake(LifeCycle.ENABLE)
    fun load() {
        unlocks.clear()
        val section = config.getConfigurationSection("unlocks") ?: run {
            warning("[RogueCore] unlocks.yml 中未找到 unlocks 节点")
            return
        }

        for (id in section.getKeys(false)) {
            val node = section.getConfigurationSection(id) ?: continue
            try {
                unlocks[id] = UnlockDefinition(
                    id = id,
                    name = node.getString("name")?.takeUnless { it == id } ?: "未命名研究",
                    description = node.getString("description") ?: "",
                    icon = XMaterial.matchXMaterial(node.getString("icon") ?: "PAPER").orElse(XMaterial.PAPER),
                    cost = node.getInt("cost", 100).coerceAtLeast(1),
                    slot = node.getInt("slot", 13).coerceAtLeast(0),
                    requiredBestFloor = node.getInt("required-best-floor", 0).coerceAtLeast(0),
                    requires = node.getStringList("requires")
                )
            } catch (ex: Exception) {
                warning("[RogueCore] 加载解锁节点 '$id' 失败: ${ex.message}")
            }
        }

        info("[RogueCore] 已加载 ${unlocks.size} 个局外解锁节点")
    }
}
