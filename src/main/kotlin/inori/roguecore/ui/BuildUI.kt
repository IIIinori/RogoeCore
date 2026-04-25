package inori.roguecore.ui

import inori.roguecore.accessory.PlayerAccessoryData
import inori.roguecore.affix.DungeonAffix
import inori.roguecore.boon.BoonInstance
import inori.roguecore.boon.BoonResonanceManager
import inori.roguecore.boon.PlayerBoonData
import inori.roguecore.curse.RunCurseManager
import inori.roguecore.data.ShardRewardManager
import inori.roguecore.dungeon.DungeonInstance
import inori.roguecore.dungeon.DungeonManager
import inori.roguecore.event.DungeonEventAffix
import inori.roguecore.item.DungeonLootManager
import inori.roguecore.modifier.RunModifierManager
import inori.roguecore.modifier.RunModifierType
import inori.roguecore.relic.PlayerRelicData
import inori.roguecore.relic.Relic
import org.bukkit.entity.Player
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest

/**
 * 局内构筑面板。
 */
object BuildUI {

    private val resonanceTags = listOf("狩猎", "壁垒", "鲜血", "风暴", "霜寒", "深渊", "圣辉", "财宝")

    fun open(player: Player) {
        val instance = DungeonManager.getPlayerDungeon(player)
        val title = "§6§l当前构筑"

        player.openMenu<Chest>(title) {
            rows(6)
            handLocked(true)

            val contentSlots = setOf(4, 10, 11, 12, 14, 15, 16, 19, 20, 21, 23, 24, 25, 28, 29, 30, 32, 33, 34, 40, 49)
            val glass = XMaterial.BLACK_STAINED_GLASS_PANE.parseItem()!!.apply {
                itemMeta = itemMeta?.also { it.setDisplayName(" ") }
            }
            for (slot in 0 until 54) {
                if (slot !in contentSlots) {
                    set(slot, glass)
                }
            }

            set(4, buildOverviewItem(player, instance))
            set(10, buildResonanceItem(player))
            set(11, buildBoonSummaryItem(player))
            set(12, buildBoonListItem(player, 0))
            set(14, buildRelicSummaryItem(player))
            set(15, buildRelicListItem(player, 0))
            set(16, buildCurseItem(player))
            set(19, buildAffixSummaryItem(instance))
            set(20, buildDungeonAffixItem(instance))
            set(21, buildEventAffixItem(instance))
            set(23, buildGearSummaryItem(player))
            set(24, buildGearListItem(player, 0))
            set(25, buildGearSetItem(player))
            set(28, buildRouteHintItem(player, instance))
            set(29, buildEconomyItem(player, instance))
            set(30, buildCombatHintItem(player))
            set(32, buildRiskItem(player, instance))
            set(33, buildNextGoalItem(player))
            set(34, buildCommandHintItem())
            set(40, buildRefreshItem())
            set(49, buildCloseItem())

            onClick { event ->
                event.isCancelled = true
                when (event.rawSlot) {
                    40 -> open(player)
                    49 -> player.closeInventory()
                }
            }
        }
    }

    private fun buildOverviewItem(player: Player, instance: DungeonInstance?): ItemStack = XMaterial.NETHER_STAR.parseItem()!!.apply {
        val boons = PlayerBoonData.getBoons(player)
        val relics = PlayerRelicData.getRelics(player)
        val curses = RunCurseManager.getCurses(player)
        val activeResonanceCount = resonanceTags.count { BoonResonanceManager.getLevel(player, it) > 0 }
        val modifiers = RunModifierManager.getModifiers(player)
        itemMeta = itemMeta?.also { meta ->
            meta.setDisplayName("§6构筑总览")
            meta.lore = buildList {
                add("")
                if (instance != null) {
                    add("§7楼层: §f${instance.config.floorNumber} §8(${instance.config.theme.name})")
                    add("§7队伍人数: §f${instance.players.size}")
                    add("§7隐藏钥匙: §b${instance.getHiddenKeys()}")
                } else {
                    add("§7当前不在副本中")
                }
                add("§7本局碎片: §6${ShardRewardManager.getRunShards(player.uniqueId)}")
                add("")
                add("§7神恩: §d${boons.size} §7| 共鸣: §6$activeResonanceCount")
                add("§7遗物: §d${relics.size} §7| 诅咒: §c${curses.size}")
                add("§7临时修正: §b${modifiers.size}")
                add("§7临时装备: §b${DungeonLootManager.getEquippedLoot(player).size}/6")
                add("§7饰品: §d${PlayerAccessoryData.getEquipped(player).size}/5")
            }
        }
    }

    private fun buildResonanceItem(player: Player): ItemStack = XMaterial.BEACON.parseItem()!!.apply {
        itemMeta = itemMeta?.also { meta ->
            meta.setDisplayName("§6流派共鸣")
            meta.lore = buildList {
                add("")
                for (tag in resonanceTags) {
                    val count = PlayerBoonData.getTagCount(player.uniqueId, tag)
                    val level = BoonResonanceManager.getLevel(player, tag)
                    val next = when {
                        count < 3 -> 3
                        count < 5 -> 5
                        count < 7 -> 7
                        else -> null
                    }
                    val color = if (level > 0) "§a" else "§7"
                    val tier = if (level > 0) roman(level) else "未激活"
                    val nextText = if (next != null) " §8(距下阶 ${next - count})" else " §6(满共鸣)"
                    add("$color$tag §7$count §8/ §f$tier$nextText")
                }
                add("")
                add("§7同标签达到 §f3/5/7 §7个激活 I/II/III 阶。")
            }
        }
    }

    private fun buildBoonSummaryItem(player: Player): ItemStack = XMaterial.ENCHANTED_BOOK.parseItem()!!.apply {
        val boons = PlayerBoonData.getBoons(player)
        val tagSummary = boons.flatMap { it.boon.tags }.groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.take(5)
        itemMeta = itemMeta?.also { meta ->
            meta.setDisplayName("§d神恩摘要")
            meta.lore = buildList {
                add("")
                add("§7已持有: §d${boons.size}")
                add("§7可升级: §a${boons.count { it.canUpgrade }}")
                add("")
                if (tagSummary.isEmpty()) {
                    add("§8尚未获得神恩")
                } else {
                    add("§7标签分布:")
                    tagSummary.forEach { add("§f${it.key} §7x${it.value}") }
                }
            }
        }
    }

    private fun buildBoonListItem(player: Player, page: Int): ItemStack = XMaterial.WRITABLE_BOOK.parseItem()!!.apply {
        val boons = PlayerBoonData.getBoons(player).sortedWith(compareByDescending<BoonInstance> { it.boon.rarity.ordinal }.thenBy { it.boon.name })
        itemMeta = itemMeta?.also { meta ->
            meta.setDisplayName("§d本局神恩")
            meta.lore = buildList {
                add("")
                if (boons.isEmpty()) {
                    add("§8尚未获得神恩")
                } else {
                    boons.drop(page * 14).take(14).forEach { instance ->
                        val tags = if (instance.boon.tags.isEmpty()) "" else " §8(${instance.boon.tags.joinToString("/")})"
                        val upgrade = if (instance.canUpgrade) "§a↑" else "§8满"
                        add("${instance.boon.rarity.color}${instance.boon.name} §eLv.${instance.level} $upgrade$tags")
                    }
                    if (boons.size > 14) {
                        add("§8仅显示前 14 项，共 ${boons.size} 项")
                    }
                }
            }
        }
    }

    private fun buildRelicSummaryItem(player: Player): ItemStack = XMaterial.AMETHYST_CLUSTER.parseItem()!!.apply {
        val relics = PlayerRelicData.getRelics(player)
        itemMeta = itemMeta?.also { meta ->
            meta.setDisplayName("§d遗物摘要")
            meta.lore = buildList {
                add("")
                add("§7已持有: §d${relics.size}")
                val byRarity = relics.groupingBy { it.rarity }.eachCount()
                if (byRarity.isEmpty()) {
                    add("§8尚未获得遗物")
                } else {
                    byRarity.entries.sortedBy { it.key.ordinal }.forEach { add("${it.key.color}${it.key.displayName} §7x${it.value}") }
                }
            }
        }
    }

    private fun buildRelicListItem(player: Player, page: Int): ItemStack = XMaterial.ECHO_SHARD.parseItem()!!.apply {
        val relics = PlayerRelicData.getRelics(player).sortedWith(compareByDescending<Relic> { it.rarity.ordinal }.thenBy { it.name })
        itemMeta = itemMeta?.also { meta ->
            meta.setDisplayName("§d本局遗物")
            meta.lore = buildList {
                add("")
                if (relics.isEmpty()) {
                    add("§8尚未获得遗物")
                } else {
                    relics.drop(page * 12).take(12).forEach { relic ->
                        add("${relic.rarity.color}${relic.name} §8- §7${relic.description}")
                    }
                    if (relics.size > 12) {
                        add("§8仅显示前 12 项，共 ${relics.size} 项")
                    }
                }
            }
        }
    }

    private fun buildCurseItem(player: Player): ItemStack = XMaterial.CRYING_OBSIDIAN.parseItem()!!.apply {
        val curses = RunCurseManager.getCurses(player).toList()
        itemMeta = itemMeta?.also { meta ->
            meta.setDisplayName("§4契约诅咒")
            meta.lore = buildList {
                add("")
                if (curses.isEmpty()) {
                    add("§a没有契约诅咒")
                } else {
                    curses.forEach { curse -> add("§c${curse.displayName} §7- ${curse.description}") }
                }
            }
        }
    }

    private fun buildAffixSummaryItem(instance: DungeonInstance?): ItemStack = XMaterial.BLAZE_POWDER.parseItem()!!.apply {
        itemMeta = itemMeta?.also { meta ->
            meta.setDisplayName("§c词缀摘要")
            meta.lore = buildList {
                add("")
                if (instance == null) {
                    add("§7当前不在副本中")
                } else {
                    add("§7副本词缀: §c${instance.affixes.size}")
                    add("§7事件词缀: §d${instance.eventAffixes.size}")
                    add("§7危险词缀: §c${instance.affixes.count { it.difficulty }}")
                    add("§7奖励词缀: §6${instance.affixes.count { !it.difficulty }}")
                }
            }
        }
    }

    private fun buildDungeonAffixItem(instance: DungeonInstance?): ItemStack = XMaterial.REDSTONE.parseItem()!!.apply {
        itemMeta = itemMeta?.also { meta ->
            meta.setDisplayName("§c副本词缀")
            meta.lore = buildAffixLore(instance?.affixes ?: emptyList())
        }
    }

    private fun buildEventAffixItem(instance: DungeonInstance?): ItemStack = XMaterial.ENDER_EYE.parseItem()!!.apply {
        itemMeta = itemMeta?.also { meta ->
            meta.setDisplayName("§d事件词缀")
            meta.lore = buildEventAffixLore(instance?.eventAffixes ?: emptyList())
        }
    }

    private fun buildGearSummaryItem(player: Player): ItemStack = XMaterial.DIAMOND_CHESTPLATE.parseItem()!!.apply {
        val equipped = DungeonLootManager.getEquippedLoot(player)
        itemMeta = itemMeta?.also { meta ->
            meta.setDisplayName("§b装备摘要")
            meta.lore = buildList {
                add("")
                add("§7临时装备: §b${equipped.size}/6")
                if (equipped.isNotEmpty()) {
                    add("§7总评分: §f${equipped.sumOf { it.score }.toInt()}")
                    add("§7平均锻造: §a${String.format("%.1f", equipped.map { it.forgeLevel }.average())}")
                }
            }
        }
    }

    private fun buildGearListItem(player: Player, page: Int): ItemStack = XMaterial.ANVIL.parseItem()!!.apply {
        val equipped = DungeonLootManager.getEquippedLoot(player)
        itemMeta = itemMeta?.also { meta ->
            meta.setDisplayName("§b当前装备")
            meta.lore = buildList {
                add("")
                if (equipped.isEmpty()) {
                    add("§8尚未装备临时装备")
                } else {
                    equipped.drop(page * 8).take(8).forEach { view ->
                        val slot = slotName(view.slot)
                        val theme = view.definition.theme?.let { " §8[$it]" } ?: ""
                        add("§f$slot §7- §b${view.definition.name}$theme")
                        add("§8  评分 ${view.score.toInt()} / 锻造 +${view.forgeLevel} / 热度 ${view.forgeHeat}")
                    }
                }
            }
        }
    }

    private fun buildGearSetItem(player: Player): ItemStack = XMaterial.ARMOR_STAND.parseItem()!!.apply {
        val equipped = DungeonLootManager.getEquippedLoot(player)
        val themes = equipped.mapNotNull { it.definition.theme }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
        val tags = equipped.flatMap { it.definition.tags }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
        itemMeta = itemMeta?.also { meta ->
            meta.setDisplayName("§b装备联动")
            meta.lore = buildList {
                add("")
                if (equipped.isEmpty()) {
                    add("§8没有可统计的装备联动")
                } else {
                    add("§7主题:")
                    if (themes.isEmpty()) add("§8无主题") else themes.take(5).forEach { add("§f${it.key} §7x${it.value}") }
                    add("")
                    add("§7标签:")
                    if (tags.isEmpty()) add("§8无标签") else tags.take(6).forEach { add("§f${it.key} §7x${it.value}") }
                }
            }
        }
    }

    private fun buildRouteHintItem(player: Player, instance: DungeonInstance?): ItemStack = XMaterial.FILLED_MAP.parseItem()!!.apply {
        itemMeta = itemMeta?.also { meta ->
            meta.setDisplayName("§a路线建议")
            meta.lore = buildList {
                add("")
                val top = resonanceTags.map { it to PlayerBoonData.getTagCount(player.uniqueId, it) }.maxByOrNull { it.second }
                if (top == null || top.second <= 0) {
                    add("§7尚未形成流派，优先选择能凑 3 个同标签的神恩。")
                } else {
                    add("§7主标签: §f${top.first} x${top.second}")
                    add(routeAdvice(top.first))
                }
                if (instance != null && instance.getHiddenKeys() > 0) {
                    add("§9你有隐藏钥匙，优先寻找隐藏房。")
                }
            }
        }
    }

    private fun buildEconomyItem(player: Player, instance: DungeonInstance?): ItemStack = XMaterial.GOLD_INGOT.parseItem()!!.apply {
        itemMeta = itemMeta?.also { meta ->
            meta.setDisplayName("§6资源")
            meta.lore = buildList {
                add("")
                add("§7本局碎片: §6${ShardRewardManager.getRunShards(player.uniqueId)}")
                add("§7结算预览: §e${ShardRewardManager.getSettlementPreview(player.uniqueId)} 灵魂碎片")
                val debt = RunModifierManager.getSoulDebtTotal(player)
                if (debt > 0) add("§7灵魂债务: §c$debt")
                val delayed = RunModifierManager.getModifiers(player).count { it.type == RunModifierType.DELAYED_REWARD }
                if (delayed > 0) add("§7托管奖励: §d$delayed 项等待兑现")
                if (instance != null) {
                    add("§7隐藏钥匙: §b${instance.getHiddenKeys()}")
                    add("§7当前楼层: §f${instance.config.floorNumber}")
                }
            }
        }
    }

    private fun buildCombatHintItem(player: Player): ItemStack = XMaterial.IRON_SWORD.parseItem()!!.apply {
        itemMeta = itemMeta?.also { meta ->
            meta.setDisplayName("§c战斗倾向")
            meta.lore = buildList {
                add("")
                val active = BoonResonanceManager.getActiveResonanceLines(player)
                if (active.isEmpty()) {
                    add("§7尚未激活共鸣，当前战力主要来自装备与单个神恩。")
                } else {
                    add("§7已激活:")
                    active.take(6).forEach { add(it) }
                }
            }
        }
    }

    private fun buildRiskItem(player: Player, instance: DungeonInstance?): ItemStack = XMaterial.SHIELD.parseItem()!!.apply {
        val curses = RunCurseManager.getCurses(player)
        itemMeta = itemMeta?.also { meta ->
            meta.setDisplayName("§e风险状态")
            meta.lore = buildList {
                add("")
                if (instance != null) {
                    val danger = instance.affixes.filter { it.difficulty }
                    add("§7危险词缀: §c${danger.size}")
                    danger.take(5).forEach { add("§c${stripColor(it.name)} §7- ${it.description}") }
                }
                add("§7契约诅咒: §c${curses.size}")
                val debt = RunModifierManager.getSoulDebtTotal(player)
                if (debt > 0) add("§7未偿灵魂债务: §c$debt")
                val prophecy = RunModifierManager.getModifiers(player).firstOrNull { it.type == RunModifierType.ROOM_PROPHECY }
                if (prophecy != null) add("§7进行中的预言: §d${RunModifierManager.payloadString(prophecy, "target", "未知")}")
                if (curses.isEmpty() && debt <= 0) add("§a当前风险较低")
            }
        }
    }

    private fun buildNextGoalItem(player: Player): ItemStack = XMaterial.COMPASS.parseItem()!!.apply {
        itemMeta = itemMeta?.also { meta ->
            meta.setDisplayName("§e下一目标")
            meta.lore = buildList {
                add("")
                val closest = resonanceTags
                    .map { it to PlayerBoonData.getTagCount(player.uniqueId, it) }
                    .filter { it.second < 7 }
                    .minByOrNull { (_, count) -> when { count < 3 -> 3 - count; count < 5 -> 5 - count; else -> 7 - count } }
                if (closest != null) {
                    val need = when {
                        closest.second < 3 -> 3 - closest.second
                        closest.second < 5 -> 5 - closest.second
                        else -> 7 - closest.second
                    }
                    add("§7优先补 §f${closest.first} §7标签，还差 §e$need §7个到下一阶。")
                } else {
                    add("§6所有主要标签都已达满共鸣目标。")
                }
                if (DungeonLootManager.getEquippedLoot(player).size < 6) {
                    add("§b装备未满，精英/Boss/宝箱房收益更高。")
                }
            }
        }
    }

    private fun buildCommandHintItem(): ItemStack = XMaterial.WRITABLE_BOOK.parseItem()!!.apply {
        itemMeta = itemMeta?.also { meta ->
            meta.setDisplayName("§f面板说明")
            meta.lore = listOf(
                "",
                "§7此界面汇总当前 run 的关键构筑状态。",
                "§7可用命令: §e/rogue build",
                "§7临时修正: §e/rogue modifiers",
                "§7如果发现内容异常，可让管理员执行:",
                "§c/rogue admin audit"
            )
        }
    }

    private fun buildRefreshItem(): ItemStack = XMaterial.CLOCK.parseItem()!!.apply {
        itemMeta = itemMeta?.also { meta ->
            meta.setDisplayName("§a刷新面板")
            meta.lore = listOf("", "§7点击刷新当前构筑数据")
        }
    }

    private fun buildCloseItem(): ItemStack = XMaterial.BARRIER.parseItem()!!.apply {
        itemMeta = itemMeta?.also { meta ->
            meta.setDisplayName("§c关闭")
            meta.lore = listOf("", "§7点击关闭界面")
        }
    }

    private fun buildAffixLore(affixes: List<DungeonAffix>): List<String> {
        return buildList {
            add("")
            if (affixes.isEmpty()) {
                add("§8无副本词缀")
            } else {
                affixes.take(12).forEach { affix ->
                    val color = if (affix.difficulty) "§c" else "§6"
                    add("$color${stripColor(affix.name)} §7- ${affix.description}")
                }
            }
        }
    }

    private fun buildEventAffixLore(affixes: List<DungeonEventAffix>): List<String> {
        return buildList {
            add("")
            if (affixes.isEmpty()) {
                add("§8无事件词缀")
            } else {
                affixes.take(12).forEach { affix ->
                    val rooms = affix.rooms.joinToString("/") { it.displayName }
                    add("§d${stripColor(affix.name)} §8[$rooms] §7- ${affix.description}")
                }
            }
        }
    }

    private fun routeAdvice(tag: String): String {
        return when (tag) {
            "财宝" -> "§6建议优先宝箱、隐藏房和商店路线。"
            "狩猎" -> "§c建议优先精英/Boss，利用击杀与爆发收益。"
            "壁垒", "霜寒", "圣辉" -> "§b续航较强，可考虑高风险路线或契约收益。"
            "鲜血", "深渊" -> "§5适合风险收益，但注意低血容错。"
            "风暴" -> "§d适合多怪战斗房，连锁收益更高。"
            else -> "§7继续补主标签，优先激活下一阶共鸣。"
        }
    }

    private fun slotName(slot: EquipmentSlot): String {
        return when (slot) {
            EquipmentSlot.HAND -> "主手"
            EquipmentSlot.OFF_HAND -> "副手"
            EquipmentSlot.HEAD -> "头盔"
            EquipmentSlot.CHEST -> "胸甲"
            EquipmentSlot.LEGS -> "护腿"
            EquipmentSlot.FEET -> "靴子"
        }
    }

    private fun stripColor(text: String): String {
        return text.replace(Regex("§."), "")
    }

    private fun roman(level: Int): String {
        return when (level) {
            1 -> "I"
            2 -> "II"
            else -> "III"
        }
    }
}
