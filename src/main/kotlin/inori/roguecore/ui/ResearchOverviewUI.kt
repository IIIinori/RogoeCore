package inori.roguecore.ui

import inori.roguecore.dungeon.route.NextFloorRoute
import inori.roguecore.item.DungeonLootSource
import inori.roguecore.item.ForgeBookTaskManager
import inori.roguecore.item.GearStorageManager
import inori.roguecore.item.IdentificationTaskManager
import inori.roguecore.unlock.UnlockManager
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest

/**
 * 当前玩家研究效果总览。
 */
object ResearchOverviewUI {

    fun open(player: Player) {
        player.openMenu<Chest>("§d§l研究效果总览") {
            rows(5)
            handLocked(true)

            val activeSlots = setOf(10, 12, 14, 16, 28, 30, 32, 34, 40)
            val glass = XMaterial.PURPLE_STAINED_GLASS_PANE.parseItem()!!.apply {
                itemMeta = itemMeta?.also { it.setDisplayName(" ") }
            }
            for (slot in 0 until 45) {
                if (slot !in activeSlots) set(slot, glass)
            }

            set(10, storageItem(player))
            set(12, identifyItem(player))
            set(14, forgeBookItem(player))
            set(16, permanentForgeItem(player))
            set(28, routeItem(player))
            set(30, eventItem(player))
            set(32, relicItem(player))
            set(34, materialItem(player))
            set(40, closeItem())

            onClick { event ->
                event.isCancelled = true
                if (event.rawSlot == 40) {
                    player.closeInventory()
                    UnlockUI.open(player)
                }
            }
        }
    }

    private fun storageItem(player: Player): ItemStack {
        val base = GearStorageManager.config.getInt("gear-storage.base-size", 27).coerceIn(9, 54)
        val bonus = UnlockManager.getGearStorageBonus(player)
        return item(
            XMaterial.CHEST,
            "§6仓储与装备",
            listOf(
                "§7装备仓库容量: §f$base §7+ §a$bonus §7= §e${GearStorageManager.getCapacity(player)}",
                boolLine(player, "gear_storage_i", "灵械仓储 I"),
                boolLine(player, "gear_storage_ii", "灵械仓储 II"),
                boolLine(player, "gear_storage_iii", "灵械仓储 III")
            )
        )
    }

    private fun identifyItem(player: Player): ItemStack {
        val baseQueue = IdentificationTaskManager.getQueueLimit()
        val queueBonus = UnlockManager.getIdentificationQueueBonus(player)
        val timeReduction = percentReduction(UnlockManager.getIdentificationTimeMultiplier(player))
        val priceReduction = percentReduction(UnlockManager.getIdentificationPriceMultiplier(player))
        val example = IdentificationTaskManager.formatDuration(IdentificationTaskManager.getIdentifyTimeMillis(player, DungeonLootSource.BOSS))
        return item(
            XMaterial.SPYGLASS,
            "§e装备鉴定",
            listOf(
                "§7鉴定队列: §f$baseQueue §7+ §a$queueBonus §7= §e${IdentificationTaskManager.getQueueLimit(player)}",
                "§7鉴定耗时: §a-$timeReduction% §8(Boss 示例: $example)",
                "§7鉴定价格: §a-$priceReduction%",
                boolLine(player, "rapid_identification", "快速鉴定"),
                boolLine(player, "batch_identification", "批量鉴定"),
                boolLine(player, "identification_discount", "鉴定折扣")
            )
        )
    }

    private fun forgeBookItem(player: Player): ItemStack {
        val baseQueue = ForgeBookTaskManager.getQueueLimit()
        val queueBonus = UnlockManager.getForgeBookQueueBonus(player)
        val timeReduction = percentReduction(UnlockManager.getForgeBookTimeMultiplier(player))
        val dropBonus = percentIncrease(UnlockManager.getForgeBookDropMultiplier(player))
        val epicBonus = UnlockManager.getForgeBookQualityWeightBonus(player, "epic")
        val legendaryBonus = UnlockManager.getForgeBookQualityWeightBonus(player, "legendary")
        return item(
            XMaterial.BLAST_FURNACE,
            "§6锻造书",
            listOf(
                "§7锻造队列: §f$baseQueue §7+ §a$queueBonus §7= §e${ForgeBookTaskManager.getQueueLimit(player)}",
                "§7锻造耗时: §a-$timeReduction%",
                "§7锻造书掉率: §a+$dropBonus%",
                "§7史诗图谱权重: §a+$epicBonus",
                "§7传说图谱权重: §a+$legendaryBonus",
                boolLine(player, "forge_queue_expansion", "炉位扩容"),
                boolLine(player, "furnace_acceleration", "炉火加速"),
                boolLine(player, "forge_book_hunt", "图谱搜寻"),
                boolLine(player, "high_tier_schematics", "高阶图谱")
            )
        )
    }

    private fun permanentForgeItem(player: Player): ItemStack {
        val baseMax = PermanentForgeUI.config.getInt("permanent-forge.upgrade.max-level", 10).coerceAtLeast(1)
        val maxBonus = UnlockManager.getPermanentForgeMaxLevelBonus(player)
        val forgeDiscount = percentReduction(UnlockManager.getPermanentForgePriceMultiplier(player))
        val rarityDiscount = percentReduction(UnlockManager.getRarityUpgradePriceMultiplier(player))
        val salvageBonus = UnlockManager.getSalvageMaterialBonus(player)
        return item(
            XMaterial.SMITHING_TABLE,
            "§6局外锻造",
            listOf(
                "§7锻造等级上限: §f+$baseMax §7+ §a$maxBonus §7= §e+${baseMax + maxBonus}",
                "§7升阶/重铸/锁词价格: §a-$forgeDiscount%",
                "§7永久升品价格: §a-$rarityDiscount%",
                "§7分解材料返还: §a+$salvageBonus",
                boolLine(player, "permanent_forge_cap_i", "局外锻造上限 I"),
                boolLine(player, "permanent_forge_cap_ii", "局外锻造上限 II"),
                boolLine(player, "inscription_saving", "铭刻节省"),
                boolLine(player, "rarity_upgrade_discount", "升品折扣"),
                boolLine(player, "salvage_accounting", "回收精算")
            )
        )
    }

    private fun routeItem(player: Player): ItemStack {
        return item(
            XMaterial.FILLED_MAP,
            "§b路线强化",
            listOf(
                boolLine(player, "extreme_route", "极境路线"),
                boolLine(player, "battle_doctrine", "征伐路线强化"),
                boolLine(player, "treasure_cipher", "藏宝路线强化"),
                boolLine(player, "black_market_network", "黑市/机遇路线强化"),
                boolLine(player, "sanctified_prayer", "神龛巡礼强化"),
                "§7藏宝隐藏房加成: §a+${formatPercent(UnlockManager.getRouteHiddenBonus(player.uniqueId, NextFloorRoute.TREASURE))}",
                "§7神龛隐藏房加成: §a+${formatPercent(UnlockManager.getRouteHiddenBonus(player.uniqueId, NextFloorRoute.PILGRIMAGE))}"
            )
        )
    }

    private fun eventItem(player: Player): ItemStack {
        return item(
            XMaterial.END_CRYSTAL,
            "§d事件选项",
            listOf(
                boolLine(player, "void_contract", "虚空契约: 契约第四选项"),
                boolLine(player, "abyssal_bargain", "深渊交易: 王冠契约"),
                boolLine(player, "arcane_exchange", "秘法交易: 商店遗物契据"),
                boolLine(player, "forbidden_trials", "禁忌试炼: 超越试炼"),
                boolLine(player, "sanctified_prayer", "圣谕净祷: 神龛净化"),
                boolLine(player, "sealed_vault", "封印秘库: 隐藏房暗格")
            )
        )
    }

    private fun relicItem(player: Player): ItemStack {
        return item(
            XMaterial.NETHER_STAR,
            "§5遗物与候选",
            listOf(
                boolLine(player, "advanced_relics", "古遗物学: 高阶遗物池"),
                boolLine(player, "legendary_relics", "神话残章: 传说遗物池"),
                "§7遗物候选加成: §a+${UnlockManager.getRelicOfferBonus(player)}",
                boolLine(player, "relic_transmutation", "遗物转化学")
            )
        )
    }

    private fun materialItem(player: Player): ItemStack {
        return item(
            XMaterial.RAW_IRON,
            "§6材料与经济",
            listOf(
                "§7鉴定价格折扣: §a-${percentReduction(UnlockManager.getIdentificationPriceMultiplier(player))}%",
                "§7局外锻造价格折扣: §a-${percentReduction(UnlockManager.getPermanentForgePriceMultiplier(player))}%",
                "§7升品价格折扣: §a-${percentReduction(UnlockManager.getRarityUpgradePriceMultiplier(player))}%",
                "§7分解材料返还: §a+${UnlockManager.getSalvageMaterialBonus(player)}",
                "§8材料库存请在主菜单或 /rogue gear storage materials 查看"
            )
        )
    }

    private fun closeItem(): ItemStack {
        return item(XMaterial.BARRIER, "§c返回研究所", listOf("§7点击返回研究所"))
    }

    private fun item(material: XMaterial, name: String, lore: List<String>): ItemStack {
        return material.parseItem()!!.apply {
            itemMeta = itemMeta?.also { meta ->
                meta.setDisplayName(name)
                meta.lore = listOf("") + lore
            }
        }
    }

    private fun boolLine(player: Player, unlockId: String, label: String): String {
        return if (UnlockManager.hasUnlock(player, unlockId)) "§a✔ $label" else "§7✘ $label"
    }

    private fun percentReduction(multiplier: Double): Int {
        return ((1.0 - multiplier.coerceAtMost(1.0)) * 100.0).toInt().coerceAtLeast(0)
    }

    private fun percentIncrease(multiplier: Double): Int {
        return ((multiplier.coerceAtLeast(1.0) - 1.0) * 100.0).toInt().coerceAtLeast(0)
    }

    private fun formatPercent(value: Double): String {
        return "${(value * 100.0).toInt()}%"
    }
}
