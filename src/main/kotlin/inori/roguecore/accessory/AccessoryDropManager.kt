package inori.roguecore.accessory

import inori.roguecore.dungeon.DungeonInstance
import inori.roguecore.balance.BalanceConfigManager
import inori.roguecore.guide.GuideManager
import inori.roguecore.item.DungeonLootSource
import inori.roguecore.summary.RunSummaryManager
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import kotlin.random.Random

object AccessoryDropManager {

    data class AccessoryBuildResult(
        val success: Boolean,
        val message: String,
        val item: ItemStack? = null
    )

    fun tryGrantChest(player: Player, instance: DungeonInstance) {
        val chance = (AccessoryRegistry.chestChance + AccessoryEffectHandler.getAccessoryDropChance(player) / 100.0).coerceIn(0.0, 1.0)
        if (Random.nextDouble() <= chance) {
            grant(player, instance, DungeonLootSource.CHEST, 1)
        }
    }

    fun tryGrantElite(player: Player, instance: DungeonInstance) {
        val chance = (AccessoryRegistry.eliteChance + AccessoryEffectHandler.getAccessoryDropChance(player) / 100.0).coerceIn(0.0, 1.0)
        if (Random.nextDouble() <= chance) {
            grant(player, instance, DungeonLootSource.ELITE, 1)
        }
    }

    fun grantBoss(player: Player, instance: DungeonInstance) {
        grant(player, instance, DungeonLootSource.BOSS, AccessoryRegistry.bossCount.coerceAtLeast(0))
    }

    fun grantHidden(player: Player, instance: DungeonInstance) {
        val bonus = if (Random.nextDouble() * 100.0 < AccessoryEffectHandler.getHiddenAccessoryBonus(player)) 1 else 0
        grant(player, instance, DungeonLootSource.HIDDEN, AccessoryRegistry.hiddenCount.coerceAtLeast(0) + bonus)
    }

    fun grant(player: Player, instance: DungeonInstance, source: DungeonLootSource, count: Int): Boolean {
        if (count <= 0) return false
        val pool = AccessoryRegistry.getPool(source, instance.config.floorNumber)
        if (pool.isEmpty()) return false
        var granted = false
        repeat(count) {
            val definition = weightedRandom(pool, AccessoryDefinition::weight) ?: return@repeat
            val item = buildMainDrop(player, definition, source, instance.config.floorNumber) ?: return@repeat
            give(player, item)
            val displayName = item.itemMeta?.displayName ?: definition.name
            val hint = when {
                AccessoryItemCodec.isSealedAccessory(item) -> "打开 §e/rogue aid §7鉴定"
                else -> "打开 §e/rogue accessory §7装备"
            }
            player.sendMessage("§d获得饰品物品: §f$displayName §7($hint)")
            if (AccessoryItemCodec.isSealedAccessory(item)) {
                RunSummaryManager.onLootGained(player.uniqueId, "sealed_accessory")
                GuideManager.showOnce(player, GuideManager.SEALED_ACCESSORY, listOf(
                    "§e你获得了密封饰品。",
                    "§7在 §6/rogue aid §7中鉴定后才会变成可装备饰品。"
                ))
            } else {
                RunSummaryManager.onLootGained(player.uniqueId, "accessory")
                GuideManager.showOnce(player, GuideManager.ACCESSORY, listOf(
                    "§e你获得了饰品。",
                    "§7打开 §6/rogue accessory §7放入饰品匣后才会生效。"
                ))
            }
            granted = true
        }
        tryGrantInscriptionBook(player, pool, source, instance.config.floorNumber)
        return granted
    }

    fun buildIdentifiedItemForPlayer(
        player: Player,
        accessoryId: String,
        source: DungeonLootSource,
        floor: Int,
        extraRarityLuck: Double = 0.0,
        valueMultiplier: Double = 1.0
    ): AccessoryBuildResult {
        val definition = AccessoryRegistry.get(accessoryId)
            ?: return AccessoryBuildResult(false, "§c饰品模板不存在: $accessoryId")
        val item = roll(definition, source, floor, player, extraRarityLuck, valueMultiplier)
            ?.let(AccessoryItemCodec::toItemStack)
            ?: return AccessoryBuildResult(false, "§c饰品生成失败。")
        return AccessoryBuildResult(true, "§d获得饰品: §f${item.itemMeta?.displayName ?: definition.name}", item)
    }

    fun rollInstance(
        definition: AccessoryDefinition,
        source: DungeonLootSource,
        floor: Int,
        player: Player,
        extraRarityLuck: Double = 0.0,
        valueMultiplier: Double = 1.0
    ): AccessoryInstance? = roll(definition, source, floor, player, extraRarityLuck, valueMultiplier)

    private fun buildMainDrop(player: Player, definition: AccessoryDefinition, source: DungeonLootSource, floor: Int): ItemStack? {
        val sealedChance = if (AccessoryRegistry.identificationEnabled) AccessoryRegistry.getSealedDropChance(source) else 0.0
        return if (Random.nextDouble() < sealedChance) {
            AccessoryItemCodec.buildSealedAccessory(definition, source, floor)
        } else {
            roll(definition, source, floor, player)?.let(AccessoryItemCodec::toItemStack)
        }
    }

    private fun tryGrantInscriptionBook(player: Player, pool: List<AccessoryDefinition>, source: DungeonLootSource, floor: Int) {
        if (!AccessoryRegistry.inscriptionEnabled) return
        val chance = AccessoryRegistry.getInscriptionDropChance(source).coerceIn(0.0, 1.0)
        if (chance <= 0.0 || Random.nextDouble() >= chance) return
        val definition = weightedRandom(pool, AccessoryDefinition::weight) ?: return
        val quality = weightedRandom(AccessoryRegistry.getInscriptionQualities(), AccessoryInscriptionQuality::weight) ?: return
        val item = AccessoryItemCodec.buildInscriptionBook(definition, source, floor, quality) ?: return
        give(player, item)
        player.sendMessage("§b获得饰品刻印书: §f${item.itemMeta?.displayName ?: definition.name} §7(打开 §e/rogue inscribe §7刻印)")
        RunSummaryManager.onLootGained(player.uniqueId, "accessory_inscription")
        GuideManager.showOnce(player, GuideManager.ACCESSORY_INSCRIPTION, listOf(
            "§e你获得了饰品刻印书。",
            "§7在 §6/rogue inscribe §7中刻印后会生成指定饰品。"
        ))
    }

    private fun give(player: Player, item: ItemStack) {
        val leftovers = player.inventory.addItem(item)
        leftovers.values.forEach { player.world.dropItemNaturally(player.location, it) }
    }

    private fun roll(
        definition: AccessoryDefinition,
        source: DungeonLootSource,
        floor: Int,
        player: Player,
        extraRarityLuck: Double = 0.0,
        valueMultiplier: Double = 1.0
    ): AccessoryInstance? {
        val rarity = rollRarity(source, player, extraRarityLuck)
        val multiplier = rarity.multiplier * valueMultiplier.coerceAtLeast(0.01)
        val attrs = linkedMapOf<String, Double>()
        for (attribute in definition.attributes) {
            val base = if (attribute.max > attribute.min) Random.nextDouble(attribute.min, attribute.max) else attribute.min
            attrs[attribute.name] = base * multiplier
        }
        val effects = definition.effects.map { effect ->
            effect.copy(value = capEffectValue(effect.type, effect.value * multiplier))
        }
        val score = attrs.values.sumOf { it.coerceAtLeast(0.0) } + effects.sumOf { it.value.coerceAtLeast(0.0) * 2.0 } + floor * 0.5
        return AccessoryInstance(definition, rarity, source, floor, attrs, effects, score)
    }

    private fun rollRarity(source: DungeonLootSource, player: Player, extraRarityLuck: Double = 0.0): AccessoryRarity {
        val rarities = AccessoryRegistry.getRarities()
        val base = weightedRandom(rarities, AccessoryRarity::weight) ?: rarities.first()
        val upgradeChance = (AccessoryRegistry.getSourceRarityUpgradeChance(source) * 100.0 + AccessoryEffectHandler.getAccessoryRarityLuck(player) + extraRarityLuck).coerceAtLeast(0.0)
        if (upgradeChance <= 0.0 || Random.nextDouble() * 100.0 >= upgradeChance) {
            return base
        }
        val index = rarities.indexOfFirst { it.id == base.id }
        return rarities.getOrNull(index + 1) ?: base
    }

    private fun capEffectValue(type: AccessoryEffectType, value: Double): Double {
        val cap = when (type) {
            AccessoryEffectType.SOUL_DEBT_INTEREST_REDUCTION -> 35.0
            AccessoryEffectType.SOUL_DEBT_DEADLINE_BONUS -> 28.0
            AccessoryEffectType.SOUL_DEBT_PENALTY_SHIELD_CHANCE -> 250.0
            AccessoryEffectType.DELAYED_REWARD_BONUS_PERCENT -> 900.0
            AccessoryEffectType.DELAYED_REWARD_ROOM_REDUCTION -> 18.0
            AccessoryEffectType.DELAYED_REWARD_EARLY_KEEP_PERCENT -> 650.0
            AccessoryEffectType.PROPHECY_REWARD_BONUS_PERCENT -> 900.0
            AccessoryEffectType.PROPHECY_MISS_REDUCTION -> 420.0
            AccessoryEffectType.ROUTE_CHAIN_TOLERANCE_BONUS -> 18.0
            AccessoryEffectType.ROUTE_CHAIN_REWARD_BONUS -> 36.0
            AccessoryEffectType.BOON_OFFER_BONUS -> 8.0
            AccessoryEffectType.BOON_ECHO_CHANCE -> 250.0
            AccessoryEffectType.BOON_MUTATION_CHANCE -> 250.0
            AccessoryEffectType.ACCESSORY_DROP_CHANCE -> 320.0
            AccessoryEffectType.ACCESSORY_RARITY_LUCK -> 1200.0
            AccessoryEffectType.HIDDEN_ACCESSORY_BONUS -> 320.0
        }
        val configuredCap = BalanceConfigManager.getDouble("accessory-effect-caps.${type.name}", cap).coerceAtLeast(0.0)
        return value.coerceIn(0.0, configuredCap)
    }

    private fun <T> weightedRandom(values: List<T>, weight: (T) -> Int): T? {
        val total = values.sumOf { weight(it).coerceAtLeast(0) }
        if (total <= 0) return values.randomOrNull()
        var cursor = Random.nextInt(total)
        for (value in values) {
            cursor -= weight(value).coerceAtLeast(0)
            if (cursor < 0) return value
        }
        return values.lastOrNull()
    }
}
