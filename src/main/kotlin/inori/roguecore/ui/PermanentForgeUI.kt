package inori.roguecore.ui

import inori.roguecore.data.PermanentMaterialManager
import inori.roguecore.data.PlayerDataManager
import inori.roguecore.item.DungeonLootManager
import inori.roguecore.unlock.UnlockManager
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import taboolib.library.xseries.XMaterial
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest
import java.util.UUID
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * 局外永久装备锻造界面。
 */
object PermanentForgeUI {

    @Config("events.yml")
    lateinit var config: Configuration
        private set

    private val displaySlots = linkedMapOf(
        EquipmentSlot.HEAD to 28,
        EquipmentSlot.CHEST to 29,
        EquipmentSlot.LEGS to 30,
        EquipmentSlot.FEET to 31,
        EquipmentSlot.HAND to 33,
        EquipmentSlot.OFF_HAND to 34
    )
    private val salvageConfirmAt = mutableMapOf<Pair<UUID, EquipmentSlot>, Long>()
    private const val SALVAGE_CONFIRM_WINDOW_MS = 10_000L

    fun open(player: Player) {
        if (!config.getBoolean("permanent-forge.enabled", true)) {
            player.sendMessage("§c当前服务器未启用局外锻造。")
            return
        }

        val shards = PlayerDataManager.get(player.uniqueId).soulShards
        val title = "§6§l局外锻造 §7(灵魂碎片: §e$shards§7)"
        player.openMenu<Chest>(title) {
            rows(5)
            handLocked(true)

            val gearSlots = displaySlots.values.toSet()
            val infoSlots = setOf(4, 10, 13, 16, 22, 40)
            val glass = XMaterial.GRAY_STAINED_GLASS_PANE.parseItem()!!.apply {
                itemMeta = itemMeta?.also { it.setDisplayName(" ") }
            }
            for (slot in 0 until 45) {
                if (slot !in gearSlots && slot !in infoSlots) {
                    set(slot, glass)
                }
            }

            set(4, infoItem(
                XMaterial.CHEST,
                "§6锻造材料",
                PermanentMaterialManager.formatOwned(player)
            ))

            set(10, infoItem(
                XMaterial.SMITHING_TABLE,
                "§6永久升阶",
                listOf(
                    "§7左键装备位上的永久装备",
                    "§7提升锻造等级并重建属性",
                    "§7上限: §6+${getUpgradeMaxLevel(player)}"
                )
            ))
            set(13, infoItem(
                XMaterial.ANVIL,
                "§6永久重铸",
                listOf(
                    "§7右键装备位上的永久装备",
                    "§7重抽随机词条",
                    "§7锁定词条会被保留"
                )
            ))
            set(16, infoItem(
                XMaterial.NAME_TAG,
                "§6永久锁词 / 升品 / 回收",
                listOf(
                    "§7中键: 循环锁定词条",
                    "§7Shift+左键: 清除锁定词条",
                    "§7Shift+右键: 提升稀有度",
                    "§7Q键: 分解回收永久装备",
                    "§7锁词价格: §6${getLockPrice(player)} §7灵魂碎片"
                )
            ))
            set(22, infoItem(
                XMaterial.NETHER_STAR,
                "§f操作说明",
                listOf(
                    "§7仅能锻造已装备的永久装备",
                    "§7临时装备请使用副本内铁匠铺",
                    "§7消耗的是永久灵魂碎片",
                    "§7局外锻造不会产生热度",
                    "§7淘汰装备可分解回收碎片"
                )
            ))
            set(40, infoItem(
                XMaterial.BARRIER,
                "§c关闭",
                listOf("§7关闭局外锻造界面")
            ))

            for ((equipmentSlot, menuSlot) in displaySlots) {
                set(menuSlot, toDisplayItem(player, equipmentSlot))
            }

            onClick { event ->
                event.isCancelled = true
                if (event.rawSlot == 40) {
                    player.closeInventory()
                    return@onClick
                }

                val equipmentSlot = displaySlots.entries.firstOrNull { it.value == event.rawSlot }?.key ?: return@onClick
                val equipped = DungeonLootManager.getPermanentEquippedLoot(player, equipmentSlot)
                if (equipped == null) {
                    player.sendMessage("§7该部位没有可进行局外锻造的永久装备。")
                    return@onClick
                }

                when (event.clickEvent().click) {
                    ClickType.RIGHT -> {
                        val price = getRerollPrice(player, equipped.forgeLevel)
                        val materialCost = getMaterialCost("permanent-forge.materials.reroll")
                        if (!PermanentMaterialManager.takeCost(player, materialCost)) {
                            player.sendMessage("§c锻造材料不足，永久重铸需要 ${PermanentMaterialManager.formatCost(materialCost)}")
                            return@onClick
                        }
                        if (!PlayerDataManager.takeSoulShards(player.uniqueId, price)) {
                            PermanentMaterialManager.addAll(player, materialCost)
                            player.sendMessage("§c灵魂碎片不足，永久重铸需要 §e$price §c碎片。")
                            return@onClick
                        }
                        val result = DungeonLootManager.rerollPermanentAffixes(
                            player,
                            equipmentSlot,
                            getAttributeBonusPerLevel()
                        )
                        if (!result.success) {
                            PlayerDataManager.addSoulShards(player.uniqueId, price)
                            PermanentMaterialManager.addAll(player, materialCost)
                        }
                        player.sendMessage(result.message)
                        open(player)
                    }
                    ClickType.MIDDLE -> {
                        val price = getLockPrice(player)
                        val materialCost = getMaterialCost("permanent-forge.materials.lock")
                        if (!PermanentMaterialManager.takeCost(player, materialCost)) {
                            player.sendMessage("§c锻造材料不足，永久锁词需要 ${PermanentMaterialManager.formatCost(materialCost)}")
                            return@onClick
                        }
                        if (!PlayerDataManager.takeSoulShards(player.uniqueId, price)) {
                            PermanentMaterialManager.addAll(player, materialCost)
                            player.sendMessage("§c灵魂碎片不足，永久锁词需要 §e$price §c碎片。")
                            return@onClick
                        }
                        val result = DungeonLootManager.cycleLockedAffix(player, equipmentSlot)
                        if (!result.success) {
                            PlayerDataManager.addSoulShards(player.uniqueId, price)
                            PermanentMaterialManager.addAll(player, materialCost)
                        }
                        player.sendMessage(result.message)
                        open(player)
                    }
                    ClickType.SHIFT_LEFT -> {
                        val result = DungeonLootManager.clearPermanentLockedAffix(player, equipmentSlot)
                        player.sendMessage(result.message)
                        open(player)
                    }
                    ClickType.SHIFT_RIGHT -> {
                        if (!config.getBoolean("permanent-forge.rarity-upgrade.enabled", true)) {
                            player.sendMessage("§c当前服务器未启用永久装备升品。")
                            return@onClick
                        }
                        val step = DungeonLootManager.getPermanentRarityUpgradeStep(player, equipmentSlot)
                        if (step == null) {
                            player.sendMessage("§c这件永久装备已经是最高稀有度。")
                            return@onClick
                        }
                        val price = getRarityUpgradePrice(player, step, equipped.forgeLevel)
                        val nextRarityId = DungeonLootManager.getNextPermanentRarityId(player, equipmentSlot)
                        val materialCost = getMaterialCost("permanent-forge.materials.rarity-upgrade.$nextRarityId")
                        if (!PermanentMaterialManager.takeCost(player, materialCost)) {
                            player.sendMessage("§c锻造材料不足，永久升品需要 ${PermanentMaterialManager.formatCost(materialCost)}")
                            return@onClick
                        }
                        if (!PlayerDataManager.takeSoulShards(player.uniqueId, price)) {
                            PermanentMaterialManager.addAll(player, materialCost)
                            player.sendMessage("§c灵魂碎片不足，永久升品需要 §e$price §c碎片。")
                            return@onClick
                        }
                        val result = DungeonLootManager.upgradePermanentRarity(
                            player,
                            equipmentSlot,
                            getAttributeBonusPerLevel()
                        )
                        if (!result.success) {
                            PlayerDataManager.addSoulShards(player.uniqueId, price)
                            PermanentMaterialManager.addAll(player, materialCost)
                        }
                        player.sendMessage(result.message)
                        open(player)
                    }
                    ClickType.DROP -> {
                        if (!config.getBoolean("permanent-forge.salvage.enabled", true)) {
                            player.sendMessage("§c当前服务器未启用永久装备分解。")
                            return@onClick
                        }
                        if (DungeonLootManager.isFavorite(equipped.item)) {
                            player.sendMessage("§c这件永久装备已收藏，无法直接分解。")
                            player.sendMessage("§7请先在 §e/rogue gear storage §7装备仓库中取消收藏后再分解。")
                            return@onClick
                        }
                        val reward = getSalvageReward(player, equipmentSlot)
                        val key = player.uniqueId to equipmentSlot
                        val now = System.currentTimeMillis()
                        val confirmed = salvageConfirmAt[key]?.let { now - it <= SALVAGE_CONFIRM_WINDOW_MS } == true
                        if (!confirmed) {
                            salvageConfirmAt[key] = now
                            player.sendMessage("§e再次按 §6Q键 §e确认分解该永久装备，可返还 §6$reward §e灵魂碎片。")
                            player.sendMessage("§7分解会永久移除该装备。")
                            return@onClick
                        }
                        salvageConfirmAt.remove(key)
                        val rarityId = DungeonLootManager.getPermanentRarityId(player, equipmentSlot) ?: "common"
                        val result = DungeonLootManager.salvagePermanentEquipped(
                            player,
                            equipmentSlot,
                            getSalvageBaseReturn(),
                            getSalvageScoreScale(),
                            getSalvageForgeLevelReturn(),
                            getSalvageRarityReturn()
                        )
                        if (result.success && result.reward > 0) {
                            PlayerDataManager.addSoulShards(player.uniqueId, result.reward)
                            val materialRewards = PermanentMaterialManager.rollSalvageRewards(
                                rarityId,
                                UnlockManager.getSalvageMaterialBonus(player)
                            )
                            PermanentMaterialManager.addAll(player, materialRewards)
                            if (materialRewards.isNotEmpty()) {
                                player.sendMessage("§7材料返还: ${PermanentMaterialManager.formatCost(materialRewards)}")
                            }
                        }
                        player.sendMessage(result.message)
                        open(player)
                    }
                    else -> {
                        val maxLevel = getUpgradeMaxLevel(player)
                        if (equipped.forgeLevel >= maxLevel) {
                            player.sendMessage("§c这件永久装备已达到局外锻造上限。")
                            return@onClick
                        }
                        val price = getUpgradePrice(player, equipped.forgeLevel)
                        val materialCost = getMaterialCost("permanent-forge.materials.upgrade")
                        if (!PermanentMaterialManager.takeCost(player, materialCost)) {
                            player.sendMessage("§c锻造材料不足，永久升阶需要 ${PermanentMaterialManager.formatCost(materialCost)}")
                            return@onClick
                        }
                        if (!PlayerDataManager.takeSoulShards(player.uniqueId, price)) {
                            PermanentMaterialManager.addAll(player, materialCost)
                            player.sendMessage("§c灵魂碎片不足，永久升阶需要 §e$price §c碎片。")
                            return@onClick
                        }
                        val result = DungeonLootManager.upgradePermanentEquipped(
                            player,
                            equipmentSlot,
                            maxLevel,
                            getAttributeBonusPerLevel()
                        )
                        if (!result.success) {
                            PlayerDataManager.addSoulShards(player.uniqueId, price)
                            PermanentMaterialManager.addAll(player, materialCost)
                        }
                        player.sendMessage(result.message)
                        open(player)
                    }
                }
            }
        }
    }

    private fun toDisplayItem(player: Player, slot: EquipmentSlot): ItemStack {
        val equipped = DungeonLootManager.getPermanentEquippedLoot(player, slot)
            ?: return placeholder(slot)
        val upgradePrice = getUpgradePrice(player, equipped.forgeLevel)
        val rerollPrice = getRerollPrice(player, equipped.forgeLevel)
        val rarityStep = DungeonLootManager.getPermanentRarityUpgradeStep(player, slot)
        val nextRarity = DungeonLootManager.getNextPermanentRarityName(player, slot)
        val rarityPrice = rarityStep?.let { getRarityUpgradePrice(player, it, equipped.forgeLevel) }
        val nextRarityId = DungeonLootManager.getNextPermanentRarityId(player, slot)
        val upgradeMaterials = getMaterialCost("permanent-forge.materials.upgrade")
        val rerollMaterials = getMaterialCost("permanent-forge.materials.reroll")
        val lockMaterials = getMaterialCost("permanent-forge.materials.lock")
        val rarityMaterials = nextRarityId?.let { getMaterialCost("permanent-forge.materials.rarity-upgrade.$it") } ?: emptyMap()
        val salvageReward = getSalvageReward(player, slot)
        val favorite = DungeonLootManager.isFavorite(equipped.item)
        return equipped.item.clone().apply {
            itemMeta = itemMeta?.also { meta ->
                val lore = (meta.lore ?: emptyList()).toMutableList()
                lore += ""
                lore += "§7局外锻造等级: §6+${equipped.forgeLevel}/${getUpgradeMaxLevel(player)}"
                if (favorite) {
                    lore += "§6★ 已收藏"
                    lore += "§c收藏装备不可分解"
                }
                lore += "§e左键: 永久升阶 (${upgradePrice} 灵魂碎片, ${PermanentMaterialManager.formatCost(upgradeMaterials)})"
                lore += "§e右键: 永久重铸 (${rerollPrice} 灵魂碎片, ${PermanentMaterialManager.formatCost(rerollMaterials)})"
                lore += "§e中键: 循环锁词 (${getLockPrice(player)} 灵魂碎片, ${PermanentMaterialManager.formatCost(lockMaterials)})"
                lore += "§eShift+左键: 清除锁词"
                lore += if (rarityPrice != null && nextRarity != null) {
                    "§eShift+右键: 升品至 $nextRarity (${rarityPrice} 灵魂碎片, ${PermanentMaterialManager.formatCost(rarityMaterials)})"
                } else {
                    "§8Shift+右键: 已是最高稀有度"
                }
                lore += if (favorite) {
                    "§8Q键: 已收藏，禁止分解"
                } else {
                    "§eQ键: 分解回收 (+${salvageReward} 灵魂碎片)"
                }
                lore += "§8局外锻造不会产生热度"
                meta.lore = lore
            }
        }
    }

    private fun placeholder(slot: EquipmentSlot): ItemStack {
        val material = when (slot) {
            EquipmentSlot.HEAD -> XMaterial.CHAINMAIL_HELMET
            EquipmentSlot.CHEST -> XMaterial.CHAINMAIL_CHESTPLATE
            EquipmentSlot.LEGS -> XMaterial.CHAINMAIL_LEGGINGS
            EquipmentSlot.FEET -> XMaterial.CHAINMAIL_BOOTS
            EquipmentSlot.HAND -> XMaterial.IRON_SWORD
            EquipmentSlot.OFF_HAND -> XMaterial.SHIELD
        }
        return material.parseItem()!!.apply {
            itemMeta = itemMeta?.also { meta ->
                meta.setDisplayName("§7${slotDisplayName(slot)}")
                meta.lore = listOf("", "§8未装备可局外锻造的永久装备")
            }
        }
    }

    private fun infoItem(material: XMaterial, name: String, lore: List<String>): ItemStack {
        return material.parseItem()!!.apply {
            itemMeta = itemMeta?.also { meta ->
                meta.setDisplayName(name)
                meta.lore = listOf("") + lore
            }
        }
    }

    private fun slotDisplayName(slot: EquipmentSlot): String {
        return when (slot) {
            EquipmentSlot.HAND -> "主手"
            EquipmentSlot.OFF_HAND -> "副手"
            EquipmentSlot.HEAD -> "头盔"
            EquipmentSlot.CHEST -> "胸甲"
            EquipmentSlot.LEGS -> "护腿"
            EquipmentSlot.FEET -> "靴子"
        }
    }

    private fun getUpgradeMaxLevel(player: Player): Int {
        return (config.getInt("permanent-forge.upgrade.max-level", 10).coerceAtLeast(1) +
            UnlockManager.getPermanentForgeMaxLevelBonus(player)).coerceAtLeast(1)
    }

    private fun getAttributeBonusPerLevel(): Double {
        return config.getDouble("permanent-forge.upgrade.attribute-bonus-per-level", 0.22).coerceAtLeast(0.0)
    }

    private fun getUpgradePrice(player: Player, currentLevel: Int): Int {
        val base = config.getInt("permanent-forge.upgrade.base-price", 300).coerceAtLeast(0)
        val perLevel = config.getInt("permanent-forge.upgrade.price-per-level", 220).coerceAtLeast(0)
        return applyPriceMultiplier(base + currentLevel.coerceAtLeast(0) * perLevel, UnlockManager.getPermanentForgePriceMultiplier(player))
    }

    private fun getRerollPrice(player: Player, currentLevel: Int): Int {
        val base = config.getInt("permanent-forge.reroll.base-price", 420).coerceAtLeast(0)
        val perLevel = config.getInt("permanent-forge.reroll.price-per-forge-level", 80).coerceAtLeast(0)
        return applyPriceMultiplier(base + currentLevel.coerceAtLeast(0) * perLevel, UnlockManager.getPermanentForgePriceMultiplier(player))
    }

    private fun getLockPrice(player: Player): Int {
        return applyPriceMultiplier(
            config.getInt("permanent-forge.lock.price", 260).coerceAtLeast(0),
            UnlockManager.getPermanentForgePriceMultiplier(player)
        )
    }

    private fun getMaterialCost(path: String): Map<PermanentMaterialManager.MaterialType, Int> {
        return PermanentMaterialManager.parseCost(config.getConfigurationSection(path))
    }

    private fun getRarityUpgradePrice(player: Player, step: Int, forgeLevel: Int): Int {
        val base = config.getInt("permanent-forge.rarity-upgrade.base-price", 1200).coerceAtLeast(0)
        val multiplier = config.getDouble("permanent-forge.rarity-upgrade.price-multiplier", 1.8).coerceAtLeast(1.0)
        val forgeLevelPrice = config.getInt("permanent-forge.rarity-upgrade.forge-level-price", 160).coerceAtLeast(0)
        val raw = (base * multiplier.pow(step.toDouble())).roundToInt() + forgeLevel.coerceAtLeast(0) * forgeLevelPrice
        return applyPriceMultiplier(raw, UnlockManager.getRarityUpgradePriceMultiplier(player))
    }

    private fun applyPriceMultiplier(value: Int, multiplier: Double): Int {
        return (value.coerceAtLeast(0) * multiplier).roundToInt().coerceAtLeast(0)
    }

    private fun getSalvageReward(player: Player, slot: EquipmentSlot): Int {
        return DungeonLootManager.getPermanentSalvageReward(
            player,
            slot,
            getSalvageBaseReturn(),
            getSalvageScoreScale(),
            getSalvageForgeLevelReturn(),
            getSalvageRarityReturn()
        )
    }

    private fun getSalvageBaseReturn(): Int {
        return config.getInt("permanent-forge.salvage.base-return", 80).coerceAtLeast(0)
    }

    private fun getSalvageScoreScale(): Double {
        return config.getDouble("permanent-forge.salvage.score-scale", 0.8).coerceAtLeast(0.0)
    }

    private fun getSalvageForgeLevelReturn(): Int {
        return config.getInt("permanent-forge.salvage.forge-level-return", 90).coerceAtLeast(0)
    }

    private fun getSalvageRarityReturn(): Map<String, Int> {
        val section = config.getConfigurationSection("permanent-forge.salvage.rarity-return") ?: return emptyMap()
        return section.getKeys(false).associate { key -> key.lowercase() to section.getInt(key, 0).coerceAtLeast(0) }
    }
}
