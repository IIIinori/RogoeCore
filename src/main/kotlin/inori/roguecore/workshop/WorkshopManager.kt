package inori.roguecore.workshop

import inori.roguecore.data.PermanentMaterialManager
import inori.roguecore.data.PlayerDataManager
import org.bukkit.entity.Player
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.warning
import taboolib.library.configuration.ConfigurationSection
import taboolib.library.xseries.XMaterial
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

/**
 * 局外材料工坊。
 */
object WorkshopManager {

    @Config("workshop.yml")
    lateinit var config: Configuration
        private set

    private val recipes = linkedMapOf<String, WorkshopRecipe>()

    @Awake(LifeCycle.ENABLE)
    fun load() {
        recipes.clear()
        val section = config.getConfigurationSection("recipes") ?: run {
            warning("[RogueCore] workshop.yml 缺少 recipes 节点")
            return
        }
        for (id in section.getKeys(false)) {
            val node = section.getConfigurationSection(id) ?: continue
            val type = runCatching { WorkshopRecipeType.valueOf(node.getString("type", "CRAFT")!!.uppercase()) }
                .getOrElse {
                    warning("[RogueCore] 工坊配方 $id 使用未知类型: ${node.getString("type")}")
                    continue
                }
            val icon = XMaterial.matchXMaterial(node.getString("icon", "PAPER")!!).orElse(XMaterial.PAPER)
            val recipe = WorkshopRecipe(
                id = id,
                name = node.getString("name", id)!!,
                description = node.getString("description", "")!!,
                type = type,
                icon = icon,
                slot = node.getInt("slot", -1),
                soulShards = node.getInt("soul-shards", 0).coerceAtLeast(0),
                cost = parseMaterials(node.getConfigurationSection("cost")),
                reward = parseMaterials(node.getConfigurationSection("reward"))
            )
            if (recipe.reward.isEmpty()) {
                warning("[RogueCore] 工坊配方 $id 没有 reward，已跳过")
                continue
            }
            recipes[id] = recipe
        }
    }

    fun getAll(): List<WorkshopRecipe> = recipes.values.toList()

    fun get(id: String): WorkshopRecipe? = recipes[id]

    fun canExecute(player: Player, recipe: WorkshopRecipe): Boolean {
        if (recipe.soulShards > 0 && PlayerDataManager.get(player.uniqueId).soulShards < recipe.soulShards) {
            return false
        }
        return PermanentMaterialManager.hasCost(player, recipe.cost)
    }

    fun execute(player: Player, recipeId: String): String {
        val recipe = get(recipeId) ?: return "§c工坊配方不存在。"
        if (recipe.soulShards > 0 && !PlayerDataManager.takeSoulShards(player.uniqueId, recipe.soulShards)) {
            return "§c灵魂碎片不足，需要 §e${recipe.soulShards} §c碎片。"
        }
        if (!PermanentMaterialManager.takeCost(player, recipe.cost)) {
            if (recipe.soulShards > 0) {
                PlayerDataManager.addSoulShards(player.uniqueId, recipe.soulShards)
            }
            return "§c材料不足，需要 ${PermanentMaterialManager.formatCost(recipe.cost)}"
        }
        PermanentMaterialManager.addAll(player, recipe.reward)
        return "§a工坊兑换完成: §f${recipe.name} §7→ ${PermanentMaterialManager.formatCost(recipe.reward)}"
    }

    private fun parseMaterials(section: ConfigurationSection?): Map<PermanentMaterialManager.MaterialType, Int> {
        if (section == null) return emptyMap()
        return section.getKeys(false).mapNotNull { key ->
            val type = PermanentMaterialManager.MaterialType.fromId(key) ?: run {
                warning("[RogueCore] 工坊配置使用未知材料: $key")
                return@mapNotNull null
            }
            val amount = section.getInt(key, 0).coerceAtLeast(0)
            if (amount <= 0) null else type to amount
        }.toMap()
    }
}
