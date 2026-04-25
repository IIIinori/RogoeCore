package inori.roguecore.audit

import inori.roguecore.affix.AffixRegistry
import inori.roguecore.affix.AffixType
import inori.roguecore.boon.Boon
import inori.roguecore.boon.BoonEffect
import inori.roguecore.boon.BoonEffectType
import inori.roguecore.boon.BoonRegistry
import inori.roguecore.boon.BoonRarity
import inori.roguecore.dungeon.room.RoomType
import inori.roguecore.event.EventAffixManager
import inori.roguecore.modifier.RunModifierManager
import inori.roguecore.relic.Relic
import inori.roguecore.relic.RelicEffectType
import inori.roguecore.relic.RelicRegistry
import inori.roguecore.relic.RelicRarity
import inori.roguecore.unlock.UnlockRegistry
import taboolib.library.configuration.ConfigurationSection
import taboolib.library.xseries.XMaterial

/**
 * 配置内容自检。
 *
 * 重点检查内容池是否再次出现：名字/描述重复、效果签名重复、未知枚举、异常数值。
 */
object ContentAuditManager {

    data class Result(
        val boonCount: Int,
        val relicCount: Int,
        val affixCount: Int,
        val eventAffixCount: Int,
        val errors: List<String>,
        val warnings: List<String>
    ) {
        val issueCount: Int get() = errors.size + warnings.size
    }

    fun run(): Result {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        auditBoons(errors, warnings)
        auditRelics(errors, warnings)
        auditAffixes(errors, warnings)
        auditEventAffixes(errors, warnings)
        auditModifiers(errors, warnings)

        return Result(
            boonCount = BoonRegistry.getAll().size,
            relicCount = RelicRegistry.getAll().size,
            affixCount = AffixRegistry.getAll().size,
            eventAffixCount = EventAffixManager.getAll().size,
            errors = errors,
            warnings = warnings
        )
    }

    fun format(result: Result, verbose: Boolean = true): List<String> {
        return buildList {
            add("§6===== RogueCore 内容自检 =====")
            add("§e神恩: §f${result.boonCount} §7| 遗物: §f${result.relicCount} §7| 副本词缀: §f${result.affixCount} §7| 事件词缀: §f${result.eventAffixCount}")
            val status = if (result.issueCount == 0) "§a通过" else "§c发现 ${result.issueCount} 个问题"
            add("§e结果: $status §7(错误 ${result.errors.size}, 警告 ${result.warnings.size})")
            if (!verbose || result.issueCount == 0) {
                if (result.issueCount == 0) {
                    add("§a没有发现重复效果或配置异常。")
                }
                return@buildList
            }
            if (result.errors.isNotEmpty()) {
                add("§c错误:")
                result.errors.take(30).forEach { add("  §c- $it") }
                if (result.errors.size > 30) {
                    add("  §7... 还有 ${result.errors.size - 30} 条错误未显示")
                }
            }
            if (result.warnings.isNotEmpty()) {
                add("§e警告:")
                result.warnings.take(40).forEach { add("  §e- $it") }
                if (result.warnings.size > 40) {
                    add("  §7... 还有 ${result.warnings.size - 40} 条警告未显示")
                }
            }
        }
    }

    private fun auditBoons(errors: MutableList<String>, warnings: MutableList<String>) {
        val boons = BoonRegistry.getAll().toList()
        duplicateBy(boons, { it.name }, "神恩名称", warnings)
        duplicateBy(boons, { it.description }, "神恩描述", warnings)
        duplicateBy(boons, ::boonFullSignature, "神恩完整效果签名", warnings)
        duplicateBoonEffectSignatures(boons, warnings)
        auditBoonRawConfig(errors, warnings)
    }

    private fun auditRelics(errors: MutableList<String>, warnings: MutableList<String>) {
        val relics = RelicRegistry.getAll().toList()
        duplicateBy(relics, { it.name }, "遗物名称", warnings)
        duplicateBy(relics, { it.description }, "遗物描述", warnings)
        duplicateBy(relics, ::relicSignature, "遗物效果签名", warnings)
        auditRelicRawConfig(errors, warnings)
    }

    private fun auditAffixes(errors: MutableList<String>, warnings: MutableList<String>) {
        val affixes = AffixRegistry.getAll().toList()
        duplicateBy(affixes, { it.name }, "副本词缀名称", warnings)
        duplicateBy(affixes, { it.description }, "副本词缀描述", warnings)
        duplicateBy(affixes, { "${it.type.name}|${fmt(it.value)}" }, "副本词缀效果签名", warnings)
        auditAffixRawConfig(errors, warnings)
    }

    private fun auditEventAffixes(errors: MutableList<String>, warnings: MutableList<String>) {
        val affixes = EventAffixManager.getAll().toList()
        duplicateBy(affixes, { it.name }, "事件词缀名称", warnings)
        duplicateBy(affixes, { it.description }, "事件词缀描述", warnings)
        auditEventRawConfig(errors, warnings)
    }

    private fun auditModifiers(errors: MutableList<String>, warnings: MutableList<String>) {
        val required = listOf("shop-debt", "sealed-chest-pressure", "gamble-streak", "shrine-blessing", "forge-overdrive")
        for (path in required) {
            if (RunModifierManager.config.getConfigurationSection(path) == null) {
                errors += "modifiers.yml 缺少节点: $path"
            }
        }

        auditModifierSection("shop-debt", errors) {
            checkNonNegative(it, "grant-base", "shop-debt", errors)
            checkNonNegative(it, "grant-power-scale", "shop-debt", errors)
            checkNonNegative(it, "debt-per-room", "shop-debt", errors)
            checkNonNegative(it, "duration-rooms", "shop-debt", errors)
        }
        auditModifierSection("sealed-chest-pressure", errors) {
            checkNonNegative(it, "reward-base", "sealed-chest-pressure", errors)
            checkNonNegative(it, "reward-power-scale", "sealed-chest-pressure", errors)
            checkNonNegative(it, "duration-rooms", "sealed-chest-pressure", errors)
            checkNonNegative(it, "charges", "sealed-chest-pressure", errors)
        }
        auditModifierSection("gamble-streak", errors) {
            checkAtLeast(it, "base-multiplier", 1.0, "gamble-streak", errors)
            checkNonNegative(it, "high-power-scale", "gamble-streak", errors)
            val base = it.getDouble("base-multiplier", 1.5)
            val max = it.getDouble("max-multiplier", 2.5)
            if (max < base) {
                errors += "gamble-streak max-multiplier 不能小于 base-multiplier"
            }
            if (max > 5.0) {
                warnings += "gamble-streak max-multiplier 当前为 $max，可能导致赌局收益/惩罚过高"
            }
        }
        auditModifierSection("shrine-blessing", errors) {
            checkNonNegative(it, "blessing-duration", "shrine-blessing", errors)
            checkNonNegative(it, "twin-duration", "shrine-blessing", errors)
            checkNonNegative(it, "blessing-reward-base", "shrine-blessing", errors)
            checkNonNegative(it, "twin-reward-base", "shrine-blessing", errors)
            checkNonNegative(it, "reward-power-scale", "shrine-blessing", errors)
            checkNonNegative(it, "twin-relic-scale", "shrine-blessing", errors)
            checkPercent01(it, "heal-percent", "shrine-blessing", errors)
        }
        auditModifierSection("forge-overdrive", errors) {
            checkNonNegative(it, "price", "forge-overdrive", errors)
            checkPercent01(it, "discount", "forge-overdrive", errors)
        }
    }

    private fun auditModifierSection(path: String, errors: MutableList<String>, block: (ConfigurationSection) -> Unit) {
        val section = RunModifierManager.config.getConfigurationSection(path) ?: return
        block(section)
    }

    private fun auditBoonRawConfig(errors: MutableList<String>, warnings: MutableList<String>) {
        val section = BoonRegistry.config.getConfigurationSection("boons") ?: run {
            errors += "boons.yml 缺少 boons 节点"
            return
        }
        for (id in section.getKeys(false)) {
            val node = section.getConfigurationSection(id) ?: continue
            val rarity = node.getString("rarity") ?: "COMMON"
            if (!enumExists<BoonRarity>(rarity)) {
                errors += "神恩 $id 使用未知稀有度: $rarity"
            }
            val icon = node.getString("icon") ?: "PAPER"
            if (!XMaterial.matchXMaterial(icon).isPresent) {
                warnings += "神恩 $id 使用无效图标: $icon"
            }
            val maxLevel = node.getInt("max-level", 3)
            if (maxLevel <= 0) {
                errors += "神恩 $id max-level 必须 > 0"
            }
            node.getConfigurationSection("attributes")?.let { attr ->
                for (attrName in attr.getKeys(false)) {
                    val values = attr.getDoubleList(attrName)
                    if (values.size < 2) {
                        errors += "神恩 $id 属性 $attrName 至少需要 [min,max] 两个数值"
                    }
                    if (values.any { it < 0.0 }) {
                        warnings += "神恩 $id 属性 $attrName 存在负数: $values"
                    }
                }
            }
            node.getConfigurationSection("effects")?.let { effects ->
                for (effectKey in effects.getKeys(false)) {
                    val effect = effects.getConfigurationSection(effectKey) ?: continue
                    val type = effect.getString("type")
                    if (type.isNullOrBlank()) {
                        errors += "神恩 $id/$effectKey 缺少 type"
                    } else if (!enumExists<BoonEffectType>(type)) {
                        errors += "神恩 $id/$effectKey 使用未知效果类型: $type"
                    }
                    checkPercent01(effect, "chance", "神恩 $id/$effectKey", errors)
                    checkNonNegative(effect, "cooldown-seconds", "神恩 $id/$effectKey", errors)
                    checkNonNegative(effect, "duration-seconds", "神恩 $id/$effectKey", errors)
                    checkNonNegative(effect, "radius", "神恩 $id/$effectKey", errors)
                    if (effect.contains("limit") && effect.getInt("limit", 0) < 0) {
                        errors += "神恩 $id/$effectKey limit 不能为负数"
                    }
                }
            }
        }
    }

    private fun auditRelicRawConfig(errors: MutableList<String>, warnings: MutableList<String>) {
        val section = RelicRegistry.config.getConfigurationSection("relics") ?: run {
            errors += "relics.yml 缺少 relics 节点"
            return
        }
        val unlockIds = UnlockRegistry.getAll().map { it.id }.toSet()
        for (id in section.getKeys(false)) {
            val node = section.getConfigurationSection(id) ?: continue
            val rarity = node.getString("rarity") ?: "COMMON"
            if (!enumExists<RelicRarity>(rarity)) {
                errors += "遗物 $id 使用未知稀有度: $rarity"
            }
            val icon = node.getString("icon") ?: "PAPER"
            if (!XMaterial.matchXMaterial(icon).isPresent) {
                warnings += "遗物 $id 使用无效图标: $icon"
            }
            val type = node.getString("effect-type") ?: ""
            if (!enumExists<RelicEffectType>(type)) {
                errors += "遗物 $id 使用未知 effect-type: $type"
            }
            if (node.getDouble("value", 0.0) < 0.0) {
                errors += "遗物 $id value 不能为负数"
            }
            val threshold = node.getDouble("threshold", 0.0)
            if (threshold < 0.0 || threshold > 100.0) {
                errors += "遗物 $id threshold 应在 0-100 之间"
            }
            val requiredUnlock = node.getString("required-unlock")?.takeIf { it.isNotBlank() }
            if (requiredUnlock != null && requiredUnlock !in unlockIds) {
                warnings += "遗物 $id required-unlock 指向不存在的研究: $requiredUnlock"
            }
        }
    }

    private fun auditAffixRawConfig(errors: MutableList<String>, warnings: MutableList<String>) {
        val section = AffixRegistry.config.getConfigurationSection("affixes") ?: run {
            errors += "affixes.yml 缺少 affixes 节点"
            return
        }
        for (id in section.getKeys(false)) {
            val node = section.getConfigurationSection(id) ?: continue
            val type = node.getString("type") ?: ""
            val parsed = runCatching { AffixType.valueOf(type.uppercase()) }.getOrNull()
            if (parsed == null) {
                errors += "副本词缀 $id 使用未知 type: $type"
            }
            if (node.getInt("weight", 10) <= 0) {
                errors += "副本词缀 $id weight 必须 > 0"
            }
            if (node.getInt("min-floor", 1) < 1) {
                errors += "副本词缀 $id min-floor 必须 >= 1"
            }
            val value = node.getDouble("value", 1.0)
            if (value < 0.0) {
                errors += "副本词缀 $id value 不能为负数"
            }
            if (parsed in setOf(AffixType.ELITE_KEY_CHANCE, AffixType.HIDDEN_LOOT_CHANCE, AffixType.BOSS_RELIC_CHANCE, AffixType.LOW_HEALTH_PRESSURE, AffixType.HEALING_REDUCE, AffixType.MOB_LIFESTEAL, AffixType.MOB_REGEN) && value > 1.0) {
                warnings += "副本词缀 $id 的 ${parsed?.name} 通常应使用 0-1 小数概率/比例，当前为 $value"
            }
        }
    }

    private fun auditEventRawConfig(errors: MutableList<String>, warnings: MutableList<String>) {
        val section = EventAffixManager.config.getConfigurationSection("event-affixes.affixes") ?: run {
            warnings += "events.yml 缺少 event-affixes.affixes 节点"
            return
        }
        for (id in section.getKeys(false)) {
            val node = section.getConfigurationSection(id) ?: continue
            val rooms = node.getStringList("rooms")
            if (rooms.isEmpty()) {
                errors += "事件词缀 $id 缺少 rooms"
            }
            for (room in rooms) {
                if (!enumExists<RoomType>(room)) {
                    errors += "事件词缀 $id 使用未知房间类型: $room"
                }
            }
            if ((node.getString("family") ?: "").isBlank()) {
                warnings += "事件词缀 $id 未设置 family，将使用房间名回退"
            }
            if (node.getInt("weight", 10) <= 0) {
                errors += "事件词缀 $id weight 必须 > 0"
            }
            if (node.getInt("power", 1) <= 0) {
                errors += "事件词缀 $id power 必须 > 0"
            }
            if (node.getInt("min-floor", 1) < 1) {
                errors += "事件词缀 $id min-floor 必须 >= 1"
            }
        }
    }

    private fun duplicateBoonEffectSignatures(boons: List<Boon>, warnings: MutableList<String>) {
        val signatures = mutableListOf<Pair<String, String>>()
        for (boon in boons) {
            for (effect in boon.effects) {
                signatures += boon.id to boonEffectSignature(effect)
            }
        }
        duplicatePairs(signatures, "神恩单个触发效果签名", warnings)
    }

    private fun <T : Any> duplicateBy(items: List<T>, selector: (T) -> String, label: String, warnings: MutableList<String>) {
        val signatures = items.mapNotNull { item ->
            val value = selector(item).takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val id = itemId(item)
            id to value
        }
        duplicatePairs(signatures, label, warnings)
    }

    private fun duplicatePairs(items: List<Pair<String, String>>, label: String, warnings: MutableList<String>) {
        items.groupBy { it.second }
            .filterValues { it.size > 1 }
            .forEach { (signature, group) ->
                warnings += "$label 重复: ${group.joinToString(", ") { it.first }} §8[$signature]"
            }
    }

    private fun itemId(item: Any): String {
        return when (item) {
            is Boon -> item.id
            is Relic -> item.id
            is inori.roguecore.affix.DungeonAffix -> item.id
            is inori.roguecore.event.DungeonEventAffix -> item.id
            else -> item.toString()
        }
    }

    private fun boonFullSignature(boon: Boon): String {
        val attrs = boon.attributes.entries
            .sortedBy { it.key }
            .joinToString(";") { "${it.key}=${fmt(it.value.getOrElse(0) { 0.0 })},${fmt(it.value.getOrElse(1) { 0.0 })}" }
        val effects = boon.effects.joinToString(";") { boonEffectSignature(it) }
        return "attrs{$attrs}|effects{$effects}|tags=${boon.tags.sorted().joinToString("+")}" 
    }

    private fun boonEffectSignature(effect: BoonEffect): String {
        return listOf(
            effect.type.name,
            fmt(effect.valuePerLevel),
            fmt(effect.threshold),
            effect.tag,
            fmt(effect.perTag),
            fmt(effect.chance),
            fmt(effect.cooldownSeconds),
            fmt(effect.durationSeconds),
            fmt(effect.radius),
            effect.limit.toString()
        ).joinToString("|")
    }

    private fun relicSignature(relic: Relic): String {
        return "${relic.effectType.name}|${fmt(relic.value)}|${fmt(relic.threshold)}|${relic.requiredUnlock ?: ""}"
    }

    private fun checkPercent01(config: ConfigurationSection, key: String, label: String, errors: MutableList<String>) {
        if (!config.contains(key)) {
            return
        }
        val value = config.getDouble(key, 0.0)
        if (value < 0.0 || value > 1.0) {
            errors += "$label $key 应在 0-1 之间，当前 $value"
        }
    }

    private fun checkNonNegative(config: ConfigurationSection, key: String, label: String, errors: MutableList<String>) {
        if (!config.contains(key)) {
            return
        }
        val value = config.getDouble(key, 0.0)
        if (value < 0.0) {
            errors += "$label $key 不能为负数，当前 $value"
        }
    }

    private fun checkAtLeast(config: ConfigurationSection, key: String, min: Double, label: String, errors: MutableList<String>) {
        if (!config.contains(key)) {
            return
        }
        val value = config.getDouble(key, min)
        if (value < min) {
            errors += "$label $key 必须 >= $min，当前 $value"
        }
    }

    private inline fun <reified E : Enum<E>> enumExists(value: String): Boolean {
        return runCatching { enumValueOf<E>(value.uppercase()) }.isSuccess
    }

    private fun fmt(value: Double): String {
        return if (value == value.toLong().toDouble()) value.toLong().toString() else String.format("%.4f", value)
    }
}
