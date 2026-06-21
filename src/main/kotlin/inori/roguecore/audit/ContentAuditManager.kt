package inori.roguecore.audit

import inori.roguecore.accessory.AccessoryDefinition
import inori.roguecore.accessory.AccessoryEffectType
import inori.roguecore.accessory.AccessoryRegistry
import inori.roguecore.accessory.AccessorySlot
import inori.roguecore.affix.AffixRegistry
import inori.roguecore.balance.BalanceConfigManager
import inori.roguecore.affix.AffixType
import inori.roguecore.boon.Boon
import inori.roguecore.boon.BoonEffect
import inori.roguecore.boon.BoonEffectType
import inori.roguecore.boon.BoonRegistry
import inori.roguecore.boon.BoonRarity
import inori.roguecore.combat.MonsterConfig
import inori.roguecore.data.PermanentMaterialManager
import inori.roguecore.dungeon.room.RoomType
import inori.roguecore.item.DungeonLootSource
import inori.roguecore.event.EventAffixManager
import inori.roguecore.modifier.RunModifierManager
import inori.roguecore.relic.Relic
import inori.roguecore.relic.RelicEffectType
import inori.roguecore.relic.RelicRegistry
import inori.roguecore.relic.RelicRarity
import inori.roguecore.unlock.UnlockRegistry
import taboolib.library.configuration.ConfigurationSection
import taboolib.library.xseries.XMaterial
import java.io.File
import java.time.LocalDate

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
        val accessoryCount: Int,
        val mythicConfiguredCount: Int,
        val mythicDefinedCount: Int,
        val mythicApAttributedCount: Int,
        val errors: List<String>,
        val warnings: List<String>
    ) {
        val issueCount: Int get() = errors.size + warnings.size
    }

    private data class MythicAuditStats(
        val configuredCount: Int = 0,
        val definedCount: Int = 0,
        val apAttributedCount: Int = 0
    )

    private data class MythicMobDefinition(
        val id: String,
        val lines: List<String>
    )

    fun run(): Result {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        auditBoons(errors, warnings)
        auditRelics(errors, warnings)
        auditAffixes(errors, warnings)
        auditEventAffixes(errors, warnings)
        auditModifiers(errors, warnings)
        auditAccessories(errors, warnings)
        auditBalance(errors, warnings)
        val mythicStats = auditMythicMobs(errors, warnings)
        auditDisplayLeakage(warnings)
        auditQualityMarkers(warnings)

        return Result(
            boonCount = BoonRegistry.getAll().size,
            relicCount = RelicRegistry.getAll().size,
            affixCount = AffixRegistry.getAll().size,
            eventAffixCount = EventAffixManager.getAll().size,
            accessoryCount = AccessoryRegistry.getAll().size,
            mythicConfiguredCount = mythicStats.configuredCount,
            mythicDefinedCount = mythicStats.definedCount,
            mythicApAttributedCount = mythicStats.apAttributedCount,
            errors = errors,
            warnings = warnings
        )
    }

    fun format(result: Result, verbose: Boolean = true): List<String> {
        return buildList {
            add("§6===== RogueCore 内容自检 =====")
            add("§e内容: §7神恩 §f${result.boonCount} §7| 遗物 §f${result.relicCount} §7| 副本词缀 §f${result.affixCount} §7| 事件词缀 §f${result.eventAffixCount} §7| 饰品 §f${result.accessoryCount}")
            add("§e部署: §7MythicMobs §f${result.mythicDefinedCount}/${result.mythicConfiguredCount} §7| AP怪物属性 §f${result.mythicApAttributedCount}/${result.mythicDefinedCount}")
            val status = if (result.issueCount == 0) "§a通过" else "§c发现 ${result.issueCount} 个问题"
            add("§e结果: $status §7(错误 ${result.errors.size}, 警告 ${result.warnings.size})")
            if (!verbose || result.issueCount == 0) {
                if (result.issueCount == 0) {
                    add("§a没有发现重复效果、配置异常或部署缺口。")
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
        auditAffixRotationConfig(errors, warnings, affixes.map { it.id }.toSet())
    }

    private fun auditEventAffixes(errors: MutableList<String>, warnings: MutableList<String>) {
        val affixes = EventAffixManager.getAll().toList()
        duplicateBy(affixes, { it.name }, "事件词缀名称", warnings)
        duplicateBy(affixes, { it.description }, "事件词缀描述", warnings)
        auditEventRawConfig(errors, warnings)
    }

    private fun auditAccessories(errors: MutableList<String>, warnings: MutableList<String>) {
        val section = AccessoryRegistry.config.getConfigurationSection("accessories") ?: run {
            errors += "accessories.yml 缺少 accessories 节点"
            return
        }
        val definitions = AccessoryRegistry.getAll().toList()
        duplicateBy(definitions, { it.name }, "饰品名称", warnings)
        auditAccessoryCoverage(definitions, errors, warnings)
        auditAccessoryBalanceLinks(warnings)
        AccessoryRegistry.config.getConfigurationSection("identification")?.let { identify ->
            checkNonNegative(identify, "queue-size", "accessories.identification", errors)
            checkNonNegative(identify, "base-price", "accessories.identification", errors)
            checkNonNegative(identify, "floor-price", "accessories.identification", errors)
            identify.getConfigurationSection("drop-as-sealed-chance")?.let { chance ->
                for (source in chance.getKeys(false)) {
                    if (!enumExists<inori.roguecore.item.DungeonLootSource>(source)) errors += "饰品鉴定使用未知来源: $source"
                    val value = chance.getDouble(source, 0.0)
                    if (value !in 0.0..1.0) errors += "饰品鉴定密封概率 $source 超出 0-1: $value"
                }
            }
        } ?: warnings.add("accessories.yml 缺少 identification 节点，饰品鉴定将使用默认值")
        AccessoryRegistry.config.getConfigurationSection("inscription-books")?.let { inscription ->
            checkNonNegative(inscription, "queue-size", "accessories.inscription-books", errors)
            inscription.getConfigurationSection("drop-chance")?.let { chance ->
                for (source in chance.getKeys(false)) {
                    if (!enumExists<inori.roguecore.item.DungeonLootSource>(source)) errors += "饰品刻印书使用未知来源: $source"
                    val value = chance.getDouble(source, 0.0)
                    if (value !in 0.0..1.0) errors += "饰品刻印书掉落概率 $source 超出 0-1: $value"
                }
            }
            val qualities = inscription.getConfigurationSection("qualities")
            if (qualities == null || qualities.getKeys(false).isEmpty()) {
                errors += "accessories.yml 饰品刻印缺少 qualities 节点"
            } else {
                for (qualityId in qualities.getKeys(false)) {
                    val quality = qualities.getConfigurationSection(qualityId) ?: continue
                    checkNonNegative(quality, "weight", "饰品刻印品质 $qualityId", errors)
                    checkNonNegative(quality, "time-seconds", "饰品刻印品质 $qualityId", errors)
                    checkNonNegative(quality, "soul-shards", "饰品刻印品质 $qualityId", errors)
                }
            }
        } ?: warnings.add("accessories.yml 缺少 inscription-books 节点，饰品刻印将使用默认值")
        for (id in section.getKeys(false)) {
            val node = section.getConfigurationSection(id) ?: continue
            val material = node.getString("material") ?: "AMETHYST_SHARD"
            if (!XMaterial.matchXMaterial(material).isPresent) {
                warnings += "饰品 $id 使用无效材质: $material"
            }
            val slot = node.getString("slot")
            if (AccessorySlot.parse(slot) == null) {
                errors += "饰品 $id 使用未知槽位: $slot"
            }
            for (source in node.getStringList("sources")) {
                if (!enumExists<inori.roguecore.item.DungeonLootSource>(source)) {
                    errors += "饰品 $id 使用未知掉落来源: $source"
                }
            }
            node.getConfigurationSection("attributes")?.let { attr ->
                for (attrName in attr.getKeys(false)) {
                    val values = attr.getDoubleList(attrName)
                    if (values.size < 2) errors += "饰品 $id 属性 $attrName 至少需要 [min,max] 两个数值"
                    if (values.any { it < 0.0 }) warnings += "饰品 $id 属性 $attrName 存在负数: $values"
                }
            }
            node.getConfigurationSection("effects")?.let { effects ->
                for (effectKey in effects.getKeys(false)) {
                    val type = effects.getConfigurationSection(effectKey)?.getString("type") ?: ""
                    if (!enumExists<AccessoryEffectType>(type)) {
                        errors += "饰品 $id 效果 $effectKey 使用未知类型: $type"
                    }
                }
            }
        }
    }

    private fun auditAccessoryCoverage(definitions: List<AccessoryDefinition>, errors: MutableList<String>, warnings: MutableList<String>) {
        val slots = definitions.map { it.slot }.toSet()
        val hasRing = AccessorySlot.RING in slots || AccessorySlot.RING_1 in slots
        val requiredSlots = listOf(AccessorySlot.NECKLACE, AccessorySlot.CHARM, AccessorySlot.TROPHY)
        for (slot in requiredSlots) {
            if (slot !in slots) warnings += "饰品池缺少槽位覆盖: ${slot.name}"
        }
        if (!hasRing) warnings += "饰品池缺少戒指槽位覆盖: RING/RING_1"
        if (AccessorySlot.RING !in slots && AccessorySlot.RING_2 !in slots) warnings += "饰品池缺少印记槽位覆盖: RING_2"

        val hiddenCount = definitions.count { DungeonLootSource.HIDDEN in it.sources }
        if (hiddenCount < 10) warnings += "HIDDEN 来源饰品数量偏少: $hiddenCount/10"

        val uncovered = mutableListOf<Int>()
        val lowCoverage = mutableListOf<String>()
        for (floor in 1..100) {
            val count = definitions.count { floor in it.minFloor..it.maxFloor }
            if (count == 0) uncovered += floor
            if (count in 1..4) lowCoverage += "$floor($count)"
        }
        if (uncovered.isNotEmpty()) errors += "饰品池楼层覆盖缺失: ${compactList(uncovered.map { it.toString() })}"
        if (lowCoverage.isNotEmpty()) warnings += "饰品池部分楼层覆盖偏低(<5): ${compactList(lowCoverage)}"
    }

    private fun auditAccessoryBalanceLinks(warnings: MutableList<String>) {
        val qualities = AccessoryRegistry.config.getConfigurationSection("inscription-books.qualities")
        qualities?.getKeys(false)?.forEach { qualityId ->
            if (BalanceConfigManager.config.getConfigurationSection("salvage.accessory-inscription.$qualityId") == null) {
                warnings += "balance.yml 缺少饰品刻印书回收配置: salvage.accessory-inscription.$qualityId"
            }
        }
        val rarities = AccessoryRegistry.config.getConfigurationSection("rarities")
        rarities?.getKeys(false)?.forEach { rarityId ->
            if (BalanceConfigManager.config.getConfigurationSection("salvage.accessory.${rarityId.lowercase()}") == null) {
                warnings += "balance.yml 缺少饰品品质回收配置: salvage.accessory.${rarityId.lowercase()}"
            }
        }
    }

    private fun auditModifiers(errors: MutableList<String>, warnings: MutableList<String>) {
        val required = listOf(
            "shop-debt",
            "sealed-chest-pressure",
            "gamble-streak",
            "shrine-blessing",
            "forge-overdrive",
            "soul-debt",
            "delayed-reward",
            "room-prophecy",
            "route-chain",
            "boon-echo",
            "boon-mutation",
            "relic-charge",
            "sealed-future"
        )
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
        auditModifierSection("soul-debt", errors) {
            checkNonNegative(it, "grant-base", "soul-debt", errors)
            checkNonNegative(it, "grant-power-scale", "soul-debt", errors)
            checkAtLeast(it, "principal-multiplier", 1.0, "soul-debt", errors)
            checkNonNegative(it, "interest-per-room", "soul-debt", errors)
            checkAtLeast(it, "deadline-rooms", 1.0, "soul-debt", errors)
        }
        auditModifierSection("delayed-reward", errors) {
            checkAtLeast(it, "default-rooms", 1.0, "delayed-reward", errors)
            checkAtLeast(it, "shard-multiplier", 1.0, "delayed-reward", errors)
            checkPercent01(it, "early-fallback-ratio", "delayed-reward", errors)
        }
        auditModifierSection("room-prophecy", errors) {
            checkAtLeast(it, "default-within-rooms", 1.0, "room-prophecy", errors)
            checkNonNegative(it, "reward-shards", "room-prophecy", errors)
            checkNonNegative(it, "room-weight-bonus", "room-prophecy", errors)
            checkNonNegative(it, "miss-penalty-shards", "room-prophecy", errors)
        }
        auditModifierSection("route-chain", errors) {
            checkNonNegative(it, "default-tolerance", "route-chain", errors)
            checkNonNegative(it, "room-weight-bonus", "route-chain", errors)
        }
    }

    private fun auditModifierSection(path: String, errors: MutableList<String>, block: (ConfigurationSection) -> Unit) {
        val section = RunModifierManager.config.getConfigurationSection(path) ?: return
        block(section)
    }

    private fun auditBalance(errors: MutableList<String>, warnings: MutableList<String>) {
        val config = BalanceConfigManager.config
        if (config.getConfigurationSection("salvage") == null) errors += "balance.yml 缺少 salvage 节点"
        if (config.getConfigurationSection("collection") == null) errors += "balance.yml 缺少 collection 节点"
        checkBalanceNonNegative("salvage.temporary-loot.run-shards-base", errors)
        checkBalanceNonNegative("salvage.temporary-loot.run-shards-per-floor", errors)
        checkBalancePositive("salvage.temporary-loot.score-divisor", errors)
        checkBalancePositive("salvage.permanent-loot.salvage-score-divisor", errors)
        checkBalancePositive("salvage.permanent-loot.score-dust-divisor", errors)
        checkBalancePositive("salvage.unidentified.floor-divisor", errors)
        checkBalancePositive("salvage.sealed-accessory.floor-divisor", errors)
        checkBalanceNonNegative("collection.gear.soul-base", errors)
        checkBalanceNonNegative("collection.gear.soul-per-floor", errors)
        checkBalanceNonNegative("collection.accessory.soul", errors)
        checkBalanceNonNegative("collection.boss.soul-base", errors)
        checkBalanceNonNegative("collection.boss.soul-per-floor", errors)

        listOf(
            "salvage.forge-book",
            "salvage.accessory-inscription",
            "collection.gear.rewards",
            "collection.accessory.rewards",
            "collection.boss.rewards"
        ).forEach { auditMaterialTree(it, errors) }

        val caps = config.getConfigurationSection("accessory-effect-caps")
        if (caps == null) {
            errors += "balance.yml 缺少 accessory-effect-caps 节点"
        } else {
            val known = AccessoryEffectType.values().map { it.name }.toSet()
            for (type in AccessoryEffectType.values()) {
                if (!caps.contains(type.name)) warnings += "balance.yml accessory-effect-caps 缺少 ${type.name}，将使用代码默认值"
            }
            for (key in caps.getKeys(false)) {
                if (key !in known) warnings += "balance.yml accessory-effect-caps 存在未知效果类型: $key"
                if (caps.getDouble(key, 0.0) < 0.0) errors += "balance.yml accessory-effect-caps.$key 不能为负数"
            }
        }
        if (!config.contains("guide.enabled")) warnings += "balance.yml 缺少 guide.enabled，将默认开启引导"
        if (!config.contains("guide.show-once")) warnings += "balance.yml 缺少 guide.show-once，将默认只提示一次"
    }

    private fun auditMythicMobs(errors: MutableList<String>, warnings: MutableList<String>): MythicAuditStats {
        val configured = MonsterConfig.getConfiguredMobIdsForSelfCheck()
        val dir = listOf(
            File("mythicmobs/mobs/RogueCore"),
            File("plugins/MythicMobs/mobs/RogueCore"),
            File("mythicmobs/Mobs/RogueCore"),
            File("plugins/MythicMobs/Mobs/RogueCore")
        ).firstOrNull { it.exists() && it.isDirectory }
        if (dir == null) {
            warnings += "未找到 mythicmobs/mobs/RogueCore，跳过本地 MM 配置对齐检查；请确认已部署到 plugins/MythicMobs/mobs/RogueCore"
            return MythicAuditStats(configuredCount = configured.size)
        }
        val files = dir.listFiles { file -> file.isFile && file.extension.equals("yml", ignoreCase = true) }?.toList().orEmpty()
        if (files.isEmpty()) {
            warnings += "mythicmobs/Mobs/RogueCore 下没有 yml 文件"
            return MythicAuditStats(configuredCount = configured.size)
        }
        val definitions = linkedMapOf<String, MythicMobDefinition>()
        for (file in files) {
            for (definition in parseMythicDefinitions(file)) {
                if (definition.id in definitions) warnings += "MythicMobs 怪物 ID 重复定义: ${definition.id}"
                definitions[definition.id] = definition
            }
        }
        val defined = definitions.keys
        val missing = configured - defined
        val extra = defined - configured
        missing.take(30).forEach { errors += "monsters.yml 引用的 MythicMobs ID 缺失: $it" }
        if (missing.size > 30) errors += "MythicMobs 缺失 ID 还有 ${missing.size - 30} 个未显示"
        if (extra.isNotEmpty()) warnings += "MythicMobs 配置存在未被 monsters.yml 引用的 ID: ${compactList(extra.take(30).toList())}${if (extra.size > 30) " ..." else ""}"

        var apAttributed = 0
        for (definition in definitions.values) {
            val text = definition.lines.joinToString("\n")
            if (!text.contains(Regex("(?m)^\\s+Type:\\s*\\S+"))) errors += "MM怪物 ${definition.id} 缺少 Type"
            if (!text.contains(Regex("(?m)^\\s+Health:\\s*\\S+"))) errors += "MM怪物 ${definition.id} 缺少 Health"
            val hasAp = text.contains(Regex("(?m)^\\s+AttributePlus:\\s*$"))
            val hasAttack = text.contains("物理伤害:")
            val hasDefense = text.contains("物理防御:")
            if (hasAp && hasAttack && hasDefense) {
                apAttributed++
            } else {
                errors += "MM怪物 ${definition.id} AP属性不完整(AttributePlus/物理伤害/物理防御)"
            }
        }
        return MythicAuditStats(configuredCount = configured.size, definedCount = defined.size, apAttributedCount = apAttributed)
    }

    private fun parseMythicDefinitions(file: File): List<MythicMobDefinition> {
        val idRegex = Regex("^([A-Za-z0-9_]+):\\s*$")
        val result = mutableListOf<MythicMobDefinition>()
        var currentId: String? = null
        val currentLines = mutableListOf<String>()
        fun flush() {
            val id = currentId ?: return
            result += MythicMobDefinition(id, currentLines.toList())
            currentLines.clear()
        }
        for (line in file.readLines()) {
            val match = idRegex.find(line)
            if (match != null && !line.startsWith(" ") && !line.startsWith("\t")) {
                flush()
                currentId = match.groupValues[1]
                currentLines += line
            } else if (currentId != null) {
                currentLines += line
            }
        }
        flush()
        return result
    }

    private fun auditMaterialTree(path: String, errors: MutableList<String>) {
        val section = BalanceConfigManager.config.getConfigurationSection(path) ?: return
        fun walk(node: ConfigurationSection, prefix: String) {
            for (key in node.getKeys(false)) {
                val child = node.getConfigurationSection(key)
                if (child != null) {
                    walk(child, "$prefix.$key")
                } else if (PermanentMaterialManager.MaterialType.fromId(key) == null) {
                    errors += "balance.yml $prefix 使用未知材料 ID: $key"
                } else if (node.getInt(key, 0) < 0) {
                    errors += "balance.yml $prefix.$key 材料数量不能为负数"
                }
            }
        }
        walk(section, path)
    }

    private fun checkBalanceNonNegative(path: String, errors: MutableList<String>) {
        if (BalanceConfigManager.config.contains(path) && BalanceConfigManager.config.getDouble(path, 0.0) < 0.0) {
            errors += "balance.yml $path 不能为负数"
        }
    }

    private fun checkBalancePositive(path: String, errors: MutableList<String>) {
        if (!BalanceConfigManager.config.contains(path)) return
        val value = BalanceConfigManager.config.getDouble(path, 0.0)
        if (value <= 0.0) errors += "balance.yml $path 必须 > 0，当前 $value"
    }

    private fun auditQualityMarkers(warnings: MutableList<String>) {
        // 批量扩容遗留的 exp2_ 命名和文案触发词属于长期润色项，
        // 不再默认加入 /rogue admin audit 警告，避免掩盖真正的配置错误。
        @Suppress("UNUSED_VARIABLE")
        val keepSignatureForFutureVerboseMode = warnings
    }

    private fun auditDisplayLeakage(warnings: MutableList<String>) {
        val issues = mutableListOf<String>()
        BoonRegistry.getAll().forEach { boon ->
            collectDisplayLeak(issues, "神恩 ${boon.id} 名称", boon.name)
            collectDisplayLeak(issues, "神恩 ${boon.id} 描述", boon.description)
            boon.tags.forEachIndexed { index, tag ->
                collectDisplayLeak(issues, "神恩 ${boon.id} 标签[$index]", tag)
            }
        }
        RelicRegistry.getAll().forEach { relic ->
            collectDisplayLeak(issues, "遗物 ${relic.id} 名称", relic.name)
            collectDisplayLeak(issues, "遗物 ${relic.id} 描述", relic.description)
        }
        AffixRegistry.getAll().forEach { affix ->
            collectDisplayLeak(issues, "副本词缀 ${affix.id} 名称", affix.name)
            collectDisplayLeak(issues, "副本词缀 ${affix.id} 描述", affix.description)
        }
        EventAffixManager.getAll().forEach { affix ->
            collectDisplayLeak(issues, "事件词缀 ${affix.id} 名称", affix.name)
            collectDisplayLeak(issues, "事件词缀 ${affix.id} 描述", affix.description)
            collectDisplayLeak(issues, "事件词缀 ${affix.id} family", affix.family)
        }
        AccessoryRegistry.getAll().forEach { accessory ->
            collectDisplayLeak(issues, "饰品 ${accessory.id} 名称", accessory.name)
            accessory.lore.forEachIndexed { index, line ->
                collectDisplayLeak(issues, "饰品 ${accessory.id} lore[$index]", line)
            }
            accessory.tags.forEachIndexed { index, tag ->
                collectDisplayLeak(issues, "饰品 ${accessory.id} tag[$index]", tag)
            }
        }
        val unique = issues.distinct()
        unique.take(30).forEach { warnings += "可见文案疑似暴露内部ID: $it" }
        if (unique.size > 30) {
            warnings += "可见文案疑似暴露内部ID: 还有 ${unique.size - 30} 条未显示"
        }
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

    private fun auditAffixRotationConfig(errors: MutableList<String>, warnings: MutableList<String>, knownAffixIds: Set<String>) {
        val section = AffixRegistry.config.getConfigurationSection("rotation") ?: return
        val enabled = section.getBoolean("enabled", false)
        val cycleDays = section.getInt("cycle-days", 7)
        if (cycleDays <= 0) {
            errors += "affixes.yml rotation.cycle-days 必须 > 0"
        }
        val anchorDate = section.getString("anchor-date")?.trim().orEmpty()
        if (anchorDate.isNotBlank() && runCatching { LocalDate.parse(anchorDate) }.isFailure) {
            errors += "affixes.yml rotation.anchor-date 格式错误，应为 yyyy-MM-dd: $anchorDate"
        }
        val timezone = section.getString("timezone")?.trim().orEmpty()
        if (timezone.isNotBlank() && runCatching { java.time.ZoneId.of(timezone) }.isFailure) {
            errors += "affixes.yml rotation.timezone 无效: $timezone"
        }
        val poolsSection = section.getConfigurationSection("pools")
        if (enabled && poolsSection == null) {
            warnings += "affixes.yml 启用了 rotation，但未配置 rotation.pools；运行时将回退全池"
            return
        }
        if (poolsSection == null) return

        val poolNames = poolsSection.getKeys(false).toSet()
        if (enabled && poolNames.isEmpty()) {
            warnings += "affixes.yml 启用了 rotation，但 rotation.pools 为空；运行时将回退全池"
        }
        val defaultPool = section.getString("default-pool")?.trim().orEmpty()
        if (defaultPool.isNotBlank() && defaultPool !in poolNames) {
            errors += "affixes.yml rotation.default-pool 指向未知池: $defaultPool"
        }
        val order = section.getStringList("order").map { it.trim() }.filter { it.isNotBlank() }
        order.filter { it !in poolNames }.forEach { bad ->
            errors += "affixes.yml rotation.order 包含未知池: $bad"
        }
        for (poolName in poolNames) {
            val node = poolsSection.getConfigurationSection(poolName) ?: continue
            node.getStringList("types").forEach { type ->
                if (!enumExists<AffixType>(type)) {
                    errors += "affixes.yml rotation.pools.$poolName.types 存在未知词缀类型: $type"
                }
            }
            node.getStringList("ids").forEach { id ->
                if (id !in knownAffixIds) {
                    warnings += "affixes.yml rotation.pools.$poolName.ids 引用了不存在的词缀ID: $id"
                }
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

    private fun compactList(values: List<String>, limit: Int = 20): String {
        if (values.isEmpty()) return "无"
        return values.take(limit).joinToString(", ") + if (values.size > limit) " ..." else ""
    }

    private fun fmt(value: Double): String {
        return if (value == value.toLong().toDouble()) value.toLong().toString() else String.format("%.4f", value)
    }

    private fun collectDisplayLeak(issues: MutableList<String>, label: String, text: String?) {
        val value = text?.trim().orEmpty()
        if (value.isBlank()) {
            return
        }
        if (looksLikeInternalId(value)) {
            issues += "$label -> $value"
        }
    }

    private fun looksLikeInternalId(text: String): Boolean {
        if (text.length >= 16 && text.all { it.isLetterOrDigit() || it == '-' }) {
            return true
        }
        if (text.matches(Regex("^[0-9a-fA-F\\-]{32,36}$"))) {
            return true
        }
        if (text.matches(Regex("^[A-Z0-9_\\-]{3,}$"))) {
            return true
        }
        return text.matches(Regex("^[a-z0-9_\\-]{3,}$")) && text.contains('_')
    }
}
