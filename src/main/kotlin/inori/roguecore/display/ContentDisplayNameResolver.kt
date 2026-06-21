package inori.roguecore.display

import inori.roguecore.accessory.AccessoryRegistry
import inori.roguecore.accessory.AccessorySlot
import inori.roguecore.affix.AffixRegistry
import inori.roguecore.affix.AffixType
import inori.roguecore.boon.BoonRegistry
import inori.roguecore.collection.CollectionManager
import inori.roguecore.curse.RunCurseType
import inori.roguecore.data.PermanentMaterialManager
import inori.roguecore.dungeon.room.RoomType
import inori.roguecore.dungeon.route.NextFloorRoute
import inori.roguecore.event.EventAffixManager
import inori.roguecore.item.DungeonLootManager
import inori.roguecore.relic.RelicRegistry
import org.bukkit.Material

/** 将内部 ID 转成玩家界面可读名称。 */
object ContentDisplayNameResolver {

    fun resolve(id: String): String {
        val normalized = id.trim()
        if (normalized.isBlank()) return normalized
        materialName(normalized)?.let { return it }
        lootName(normalized)?.let { return it }
        accessoryName(normalized)?.let { return it }
        BoonRegistry.get(normalized)?.let { return it.name }
        RelicRegistry.get(normalized)?.let { return it.name }
        AffixRegistry.get(normalized)?.let { return it.name }
        EventAffixManager.get(normalized)?.let { return it.name }
        slotName(normalized)?.let { return it }
        gearThemeName(normalized)?.let { return it }
        tagName(normalized)?.let { return it }
        collectionName(normalized)?.let { return it }
        lootCategoryName(normalized)?.let { return it }
        lootSourceName(normalized)?.let { return it }
        roomTypeName(normalized)?.let { return it }
        routeName(normalized)?.let { return it }
        modifierName(normalized)?.let { return it }
        curseName(normalized)?.let { return it }
        eventFamilyName(normalized)?.let { return it }
        return normalized
    }

    fun gearThemeName(id: String): String? {
        val key = id.trim().lowercase()
        if (key.isBlank()) return null
        if (key == "storm") return "雷鸣高塔"
        if (key == "hidden") return "隐藏秘藏"
        val name = CollectionManager.getThemeName(key)
        return name.takeIf { it != key }
    }

    fun tagName(id: String): String? = when (id.trim().lowercase()) {
        "assault" -> "突袭"
        "bulwark" -> "壁垒"
        "mobility" -> "机动"
        "relic" -> "遗物"
        "run" -> "冒险"
        "hidden" -> "隐藏"
        "necklace" -> "项链"
        "ring" -> "戒指"
        "charm" -> "护符"
        "trophy" -> "战利品"
        else -> gearThemeName(id)
    }

    fun lootCategoryName(id: String): String? = when (id.lowercase()) {
        "temporary_gear" -> "临时装备"
        "unidentified_gear" -> "未鉴定装备"
        "forge_book" -> "锻造书"
        "accessory" -> "饰品"
        "sealed_accessory" -> "密封饰品"
        "accessory_inscription" -> "饰品刻印书"
        else -> null
    }

    fun lootSourceName(id: String): String? = when (id.trim().uppercase()) {
        "CHEST" -> "宝箱"
        "ELITE" -> "精英"
        "BOSS" -> "Boss"
        "HIDDEN" -> "隐藏"
        else -> null
    }

    fun roomTypeName(id: String): String? {
        val type = runCatching { RoomType.valueOf(id.trim().uppercase()) }.getOrNull() ?: return null
        return type.displayName
    }

    fun routeName(id: String): String? {
        val route = runCatching { NextFloorRoute.valueOf(id.trim().uppercase()) }.getOrNull() ?: return null
        return route.displayName
    }

    fun curseName(id: String): String? {
        val type = runCatching { RunCurseType.valueOf(id.trim().uppercase()) }.getOrNull() ?: return null
        return type.displayName
    }

    fun safeText(raw: String, fallback: String = "未知"): String {
        val text = raw.trim()
        if (text.isBlank()) {
            return fallback
        }
        val resolved = resolve(text).trim()
        if (resolved.isBlank()) {
            return fallback
        }
        if (!resolved.equals(text, ignoreCase = false)) {
            return resolved
        }
        return if (looksLikeInternalId(text)) fallback else text
    }

    fun materialTypeName(raw: String, fallback: String = "物品"): String {
        val key = raw.trim()
        if (key.isBlank()) return fallback
        val material = runCatching { Material.valueOf(key.uppercase()) }.getOrNull() ?: return safeText(key, fallback)
        return runCatching {
            material.name.lowercase()
                .split('_')
                .filter { it.isNotBlank() }
                .joinToString(" ") { part ->
                    part.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                }
        }.getOrDefault(fallback)
    }

    fun materialName(id: String): String? = PermanentMaterialManager.MaterialType.fromId(id)?.displayName

    fun affixTypeName(type: AffixType): String = when (type.name) {
        "MOB_HP_MULTIPLY" -> "怪物生命"
        "MOB_COUNT_MULTIPLY" -> "怪物数量"
        "MOB_SPEED" -> "怪物速度"
        "FLOOR_FIRE" -> "燃烧地板"
        "NO_HEAL" -> "禁疗"
        "SHARD_MULTIPLY" -> "碎片倍率"
        "EXTRA_BOON" -> "额外神恩"
        "WEAPON_LUCK" -> "装备幸运"
        "MOB_DAMAGE_MULTIPLY" -> "怪物伤害"
        "COMBAT_SHARD_FLAT" -> "战斗碎片"
        "ELITE_KEY_CHANCE" -> "精英钥匙"
        "BOSS_EMBER_BONUS" -> "Boss 余烬"
        "LOW_HEALTH_PRESSURE" -> "低血压迫"
        "MOB_REGEN" -> "怪物恢复"
        "MOB_SPAWN_SHIELD" -> "怪物护盾"
        "MOB_LOW_HEALTH_RAGE" -> "怪物狂怒"
        "MOB_FIRE_ATTACK" -> "怪物点燃"
        "VOID_FIELD" -> "虚空脉冲"
        "HEALING_REDUCE" -> "治疗降低"
        "MOB_LIFESTEAL" -> "怪物吸血"
        "BOSS_DAMAGE_MULTIPLY" -> "Boss 伤害"
        "COMBAT_EMBER_FLAT" -> "战斗余烬"
        "HIDDEN_LOOT_CHANCE" -> "隐藏战利品"
        "BOON_LUCK" -> "神恩幸运"
        "BOSS_RELIC_CHANCE" -> "Boss 遗物"
        "CHEST_SHARD_BONUS" -> "宝箱碎片"
        "EXTRA_EVENT_ROOM_WEIGHT" -> "事件房权重"
        "EXTRA_CHEST_WEIGHT" -> "宝箱房权重"
        "EXTRA_SHRINE_WEIGHT" -> "神龛房权重"
        "EXTRA_FORGE_WEIGHT" -> "铁匠房权重"
        "EXTRA_HIDDEN_CHANCE" -> "隐藏房概率"
        "EXTRACTION_RATIO_MODIFY" -> "撤离结算"
        "SHOP_PRICE_MODIFY" -> "商店价格"
        "SEALED_CHEST_REWARD_MODIFY" -> "封印宝箱"
        "ELITE_SHARD_FLAT" -> "精英碎片"
        "BOSS_SHARD_FLAT" -> "Boss 碎片"
        "HIDDEN_SHARD_FLAT" -> "隐藏碎片"
        "CHEST_GEAR_CHANCE" -> "宝箱装备"
        "RELIC_OFFER_BONUS" -> "遗物候选"
        "BOON_OFFER_BONUS" -> "神恩候选"
        "FLOOR_PROPHECY" -> "楼层预言"
        else -> prettyEnum(type.name)
    }

    fun eventFamilyName(id: String): String? = when (id.uppercase()) {
        "CHEST", "LOOT", "TREASURE", "CHEST_SHARD", "CHEST_BOON", "CHEST_FORGE", "CHEST_GEAR" -> "宝箱系"
        "SHOP", "MARKET", "SHOP_DISCOUNT", "SHOP_RELIC", "SHOP_MATERIAL", "SHOP_BLACK" -> "商店系"
        "FORGE", "SMITH", "FORGE_SOUL" -> "铁匠系"
        "SHRINE", "SACRIFICE", "SHRINE_PURIFY", "SHRINE_RELIC" -> "神龛系"
        "REST", "SANCTUM", "REST_TRAINING" -> "休整系"
        "TRIAL", "CHALLENGE", "TRIAL_FORGE" -> "试炼系"
        "CONTRACT", "PACT" -> "契约系"
        "HIDDEN", "SECRET", "HIDDEN_RELIC" -> "隐藏系"
        "GAMBLE", "DICE", "GAMBLE_GEAR", "GAMBLE_SAFE" -> "赌局系"
        else -> null
    }

    fun modifierName(id: String): String? = when (id.uppercase()) {
        "SOUL_DEBT" -> "灵魂债务"
        "DELAYED_REWARD" -> "托管奖励"
        "ROOM_PROPHECY" -> "房间预言"
        "ROUTE_CHAIN" -> "路线连锁"
        "BOON_ECHO" -> "神恩回响"
        "BOON_MUTATION" -> "神恩变质"
        "RELIC_CHARGE_RULE" -> "遗物充能规则"
        "SEALED_FUTURE" -> "封存未来"
        else -> null
    }

    private fun lootName(id: String): String? = DungeonLootManager.getDefinitionName(id)

    private fun accessoryName(id: String): String? = AccessoryRegistry.get(id)?.name

    private fun slotName(id: String): String? = runCatching { AccessorySlot.valueOf(id.uppercase()).displayName }.getOrNull()

    private fun prettyEnum(id: String): String {
        return id.lowercase().split('_').filter { it.isNotBlank() }.joinToString(" ") { part ->
            part.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
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

    private fun collectionName(id: String): String? {
        val raw = id.trim()
        if (raw.startsWith("boss.", ignoreCase = true)) {
            val floor = raw.substringAfter('.').toIntOrNull()
            if (floor != null) return "第 ${floor} 层 Boss 首杀"
        }
        if (raw.startsWith("accessory.", ignoreCase = true)) {
            val slot = raw.substringAfter('.')
            return "${slotName(slot) ?: "饰品"}收藏"
        }
        if (raw.startsWith("gear.", ignoreCase = true)) {
            val id = raw.substringAfter('.')
            return "${lootName(id) ?: "装备"}收藏"
        }
        return null
    }
}
