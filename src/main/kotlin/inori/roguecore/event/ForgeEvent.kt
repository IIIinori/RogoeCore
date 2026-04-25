package inori.roguecore.event

import inori.roguecore.data.ForgeMaterialManager
import inori.roguecore.data.ForgeMaterialType
import inori.roguecore.data.ShardRewardManager
import inori.roguecore.dungeon.DungeonInstance
import inori.roguecore.dungeon.room.RoomType
import inori.roguecore.item.DungeonLootManager
import inori.roguecore.ui.DungeonGuiGuard
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

/**
 * 铁匠房事件。
 *
 * 左键重铸当前已装备的临时装备，右键升阶，Shift+左键分解。
 */
object ForgeEvent {

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

    fun trigger(player: Player, instance: DungeonInstance) {
        openForgeUI(player, instance)
    }

    private fun openForgeUI(player: Player, instance: DungeonInstance) {
        val forgePower = EventAffixManager.getFamilyPower(instance, RoomType.FORGE, "FORGE")
        val shards = ShardRewardManager.getRunShards(player.uniqueId)
        val rerollPrice = (config.getInt("forge.reroll-price", 18) - forgePower * 2).coerceAtLeast(0)
        val lockPrice = (getLockPrice(player) - forgePower).coerceAtLeast(0)
        val lockSigilCost = (getLockSigilCost() - forgePower / 4).coerceAtLeast(0)
        val coolingEmberCost = (getCoolingEmberCost() - forgePower / 5).coerceAtLeast(0)
        val coolingHeatReduce = getCoolingHeatReduce(player) + forgePower.coerceAtMost(6)
        val refinePrice = (getNoHeatReforgePrice(player) - forgePower * 3).coerceAtLeast(0)
        val refineEmberCost = (getNoHeatReforgeEmberCost() - forgePower / 5).coerceAtLeast(0)
        val refineSigilCost = (getNoHeatReforgeSigilCost(player) - forgePower / 5).coerceAtLeast(0)
        val temperEmberCost = (getTemperEmberCost() - forgePower / 6).coerceAtLeast(0)
        val temperLevelGain = getTemperLevelGain(player) + (forgePower / 7).coerceAtMost(2)
        val temperUnlocked = UnlockManager.hasSoulTempering(player)
        val lockingUnlocked = UnlockManager.hasPrecisionLocking(player)
        val coolingUnlocked = UnlockManager.hasEmberCooling(player)
        val refinedReforgeUnlocked = UnlockManager.hasRefinedReforge(player)
        val title = "§6§l铁匠铺 §7(本局碎片: §e$shards§7)"

        DungeonGuiGuard.lock(player, title) { target ->
            val latest = inori.roguecore.dungeon.DungeonManager.getPlayerDungeon(target) ?: return@lock
            openForgeUI(target, latest)
        }

        player.openMenu<Chest>(title) {
            rows(5)
            handLocked(true)

            val gearSlots = displaySlots.values.toSet()
            val infoSlots = mutableSetOf(10, 13, 16, 22, 24, 40)
            if (temperUnlocked) {
                infoSlots += 25
            }
            val glass = XMaterial.GRAY_STAINED_GLASS_PANE.parseItem()!!.apply {
                itemMeta = itemMeta?.also { it.setDisplayName(" ") }
            }
            for (slot in 0 until 45) {
                if (slot !in gearSlots && slot !in infoSlots) {
                    set(slot, glass)
                }
            }

            set(10, XMaterial.ANVIL.parseItem()!!.apply {
                itemMeta = itemMeta?.also { meta ->
                    meta.setDisplayName("§6重铸")
                    meta.lore = listOf(
                        "",
                        "§7左键装备位上的临时装备",
                        "§7会重铸为同部位的新装备",
                        "",
                        "§e价格: §6$rerollPrice §e本局碎片"
                    )
                }
            })

            set(13, XMaterial.GOLD_INGOT.parseItem()!!.apply {
                itemMeta = itemMeta?.also { meta ->
                    meta.setDisplayName("§e本局碎片")
                    meta.lore = listOf(
                        "",
                        "§7当前持有: §6$shards",
                        "§7${ForgeMaterialType.BOSS_EMBER.coloredName()}: §6${ForgeMaterialManager.get(player.uniqueId, ForgeMaterialType.BOSS_EMBER)}",
                        "§7${ForgeMaterialType.HIDDEN_SIGIL.coloredName()}: §b${ForgeMaterialManager.get(player.uniqueId, ForgeMaterialType.HIDDEN_SIGIL)}",
                        "§7离开副本时才会结算为永久碎片"
                    )
                }
            })

            set(16, XMaterial.SMITHING_TABLE.parseItem()!!.apply {
                itemMeta = itemMeta?.also { meta ->
                    meta.setDisplayName("§6升阶")
                    meta.lore = listOf(
                        "",
                        "§7右键装备位上的临时装备",
                        "§7可提升这件装备的属性强度",
                        "",
                        "§e基础价格: §6${config.getInt("forge.upgrade-price", 22)} §e本局碎片"
                    )
                }
            })

            set(22, XMaterial.BOOK.parseItem()!!.apply {
                itemMeta = itemMeta?.also { meta ->
                    meta.setDisplayName("§f操作说明")
                    meta.lore = buildList {
                        add("")
                        add("§7只可锻造当前已装备的临时装备")
                        add("§7左键: 重铸为同部位新装备")
                        add("§7右键: 升阶当前装备")
                        add(if (lockingUnlocked) "§7中键: 循环锁定 1 条随机词条" else "§8中键: 需要研究“精密锁词”")
                        add(if (coolingUnlocked) "§7Q键: 余烬退火，降低装备热度" else "§8Q键: 需要研究“余烬退火”")
                        add(if (refinedReforgeUnlocked) "§7Ctrl+Q: 精炼无热重铸" else "§8Ctrl+Q: 需要研究“精炼重铸”")
                        add("§7Shift+左键: 分解换本局碎片")
                        add(if (temperUnlocked) "§7Shift+右键: 灵魂淬火" else "§8研究“灵魂淬火”可开启高级工艺")
                        add("")
                        add(if (lockingUnlocked) {
                            "§7锁词后，重铸/淬火会保留该词条"
                        } else {
                            "§8研究“精密锁词”后可保留核心词条"
                        })
                        add(if (lockingUnlocked) {
                            "§7每次切换锁词消耗 §6$lockPrice §7碎片 + ${ForgeMaterialType.HIDDEN_SIGIL.coloredName()} §bx$lockSigilCost"
                        } else {
                            "§8精密锁词未解锁"
                        })
                        add(if (coolingUnlocked) {
                            "§7余烬退火消耗 ${ForgeMaterialType.BOSS_EMBER.coloredName()} §6x$coolingEmberCost §7并降低 §c$coolingHeatReduce §7点热度"
                        } else {
                            "§8余烬退火未解锁"
                        })
                        add(if (refinedReforgeUnlocked) {
                            "§7无热重铸消耗 §6$refinePrice §7碎片 + ${ForgeMaterialType.BOSS_EMBER.coloredName()} §6x$refineEmberCost §7+ ${ForgeMaterialType.HIDDEN_SIGIL.coloredName()} §bx$refineSigilCost"
                        } else {
                            "§8精炼重铸未解锁"
                        })
                        add("§7重铸热度: §c+${getReforgeHeatGain(player)}")
                        add("§7升阶热度: §c+${getUpgradeHeatGain()}")
                        add(if (temperUnlocked) {
                            "§7淬火热度: §c+${getTemperHeatGain()} §7且提升 §6+${temperLevelGain} §7级，消耗 ${ForgeMaterialType.BOSS_EMBER.coloredName()} §6x$temperEmberCost"
                        } else {
                            "§8淬火会累积更高热度"
                        })
                        add("")
                        add("§7当前工艺偏好:")
                        add(if (UnlockManager.hasSteadyForging(player)) "§a稳锻节律: 重铸热度降低，退火更强" else "§8稳锻节律: 未研究")
                        add(if (UnlockManager.hasVolcanicTempering(player)) "§a炽火淬锋: 淬火额外提升 1 级" else "§8炽火淬锋: 未研究")
                        add(if (UnlockManager.hasInscriptionMastery(player)) "§a精修铭刻: 锁词与精炼重铸更便宜" else "§8精修铭刻: 未研究")
                        add("§7装备热度达到 §c${getHeatCap()} §7后将无法继续锻造")
                        add("")
                        add("§7Boss / 隐藏装备的重铸池也更高级")
                    }
                }
            })

            set(24, XMaterial.BLAST_FURNACE.parseItem()!!.apply {
                itemMeta = itemMeta?.also { meta ->
                    meta.setDisplayName("§6分解")
                    meta.lore = listOf(
                        "",
                        "§7Shift+左键装备位上的临时装备",
                        "§7可将其拆解成本局碎片",
                        "",
                        "§e锻造等级越高，返还越多"
                    )
                }
            })

            if (temperUnlocked) {
                set(25, XMaterial.FIRE_CHARGE.parseItem()!!.apply {
                    itemMeta = itemMeta?.also { meta ->
                        meta.setDisplayName("§6灵魂淬火")
                        meta.lore = listOf(
                            "",
                            "§7Shift+右键装备位上的临时装备",
                            "§7会重铸为同部位新装备",
                            "§7并额外提升 ${temperLevelGain} 级锻造等级",
                            "",
                            "§e基础价格: §6${config.getInt("forge.temper-price", 28)} §e本局碎片"
                        )
                    }
                })
            }

            set(40, XMaterial.BARRIER.parseItem()!!.apply {
                itemMeta = itemMeta?.also { meta ->
                    meta.setDisplayName("§c离开铁匠铺")
                    meta.lore = listOf("", "§7不进行任何锻造，直接关闭界面")
                }
            })

            for ((equipmentSlot, menuSlot) in displaySlots) {
                set(menuSlot, toDisplayItem(player, equipmentSlot, temperUnlocked))
            }

            onClick { event ->
                event.isCancelled = true

                if (event.rawSlot == 40) {
                    DungeonGuiGuard.unlock(player)
                    player.closeInventory()
                    return@onClick
                }

                val equipmentSlot = displaySlots.entries.firstOrNull { it.value == event.rawSlot }?.key ?: return@onClick
                val equipped = DungeonLootManager.getEquippedLoot(player, equipmentSlot)
                if (equipped == null) {
                    player.sendMessage("§7该部位没有可锻造的临时装备。")
                    return@onClick
                }

                if (event.clickEvent().click == ClickType.MIDDLE) {
                    if (!lockingUnlocked) {
                        player.sendMessage("§c需要先完成研究: §e精密锁词")
                        return@onClick
                    }
                    if (!ShardRewardManager.takeRunShards(player.uniqueId, lockPrice)) {
                        player.sendMessage("§c本局碎片不足，锁词需要 §e$lockPrice §c碎片。")
                        return@onClick
                    }
                    if (!ForgeMaterialManager.take(player.uniqueId, ForgeMaterialType.HIDDEN_SIGIL, lockSigilCost)) {
                        ShardRewardManager.addRunShards(player.uniqueId, lockPrice)
                        player.sendMessage("§c缺少 ${ForgeMaterialType.HIDDEN_SIGIL.coloredName()} §c，锁词需要 §bx$lockSigilCost")
                        return@onClick
                    }
                    val result = DungeonLootManager.cycleLockedAffix(player, equipmentSlot)
                    if (!result.success) {
                        ShardRewardManager.addRunShards(player.uniqueId, lockPrice)
                        ForgeMaterialManager.add(player.uniqueId, ForgeMaterialType.HIDDEN_SIGIL, lockSigilCost)
                    }
                    player.sendMessage(result.message)
                    openForgeUI(player, instance)
                    return@onClick
                }

                if (event.clickEvent().click == ClickType.DROP) {
                    if (!coolingUnlocked) {
                        player.sendMessage("§c需要先完成研究: §e余烬退火")
                        return@onClick
                    }
                    if (!ForgeMaterialManager.take(player.uniqueId, ForgeMaterialType.BOSS_EMBER, coolingEmberCost)) {
                        player.sendMessage("§c缺少 ${ForgeMaterialType.BOSS_EMBER.coloredName()} §c，退火需要 §6x$coolingEmberCost")
                        return@onClick
                    }
                    val result = DungeonLootManager.coolForgedItem(
                        player,
                        equipmentSlot,
                        getHeatCap(),
                        coolingHeatReduce
                    )
                    if (!result.success) {
                        ForgeMaterialManager.add(player.uniqueId, ForgeMaterialType.BOSS_EMBER, coolingEmberCost)
                    }
                    player.sendMessage(result.message)
                    openForgeUI(player, instance)
                    return@onClick
                }

                if (event.clickEvent().click == ClickType.CONTROL_DROP) {
                    if (!refinedReforgeUnlocked) {
                        player.sendMessage("§c需要先完成研究: §e精炼重铸")
                        return@onClick
                    }
                    if (!ShardRewardManager.takeRunShards(player.uniqueId, refinePrice)) {
                        player.sendMessage("§c本局碎片不足，精炼重铸需要 §e$refinePrice §c碎片。")
                        return@onClick
                    }
                    if (!ForgeMaterialManager.take(player.uniqueId, ForgeMaterialType.BOSS_EMBER, refineEmberCost)) {
                        ShardRewardManager.addRunShards(player.uniqueId, refinePrice)
                        player.sendMessage("§c缺少 ${ForgeMaterialType.BOSS_EMBER.coloredName()} §c，精炼重铸需要 §6x$refineEmberCost")
                        return@onClick
                    }
                    if (!ForgeMaterialManager.take(player.uniqueId, ForgeMaterialType.HIDDEN_SIGIL, refineSigilCost)) {
                        ShardRewardManager.addRunShards(player.uniqueId, refinePrice)
                        ForgeMaterialManager.add(player.uniqueId, ForgeMaterialType.BOSS_EMBER, refineEmberCost)
                        player.sendMessage("§c缺少 ${ForgeMaterialType.HIDDEN_SIGIL.coloredName()} §c，精炼重铸需要 §bx$refineSigilCost")
                        return@onClick
                    }
                    val result = DungeonLootManager.reforgeEquippedWithoutHeat(
                        player,
                        instance,
                        equipmentSlot,
                        config.getDouble("forge.attribute-bonus-per-level", 0.18),
                        getHeatCap()
                    )
                    if (!result.success) {
                        ShardRewardManager.addRunShards(player.uniqueId, refinePrice)
                        ForgeMaterialManager.add(player.uniqueId, ForgeMaterialType.BOSS_EMBER, refineEmberCost)
                        ForgeMaterialManager.add(player.uniqueId, ForgeMaterialType.HIDDEN_SIGIL, refineSigilCost)
                    }
                    player.sendMessage(result.message)
                    openForgeUI(player, instance)
                    return@onClick
                }

                if (event.clickEvent().click == ClickType.SHIFT_LEFT) {
                    val result = DungeonLootManager.salvageEquipped(
                        player,
                        equipmentSlot,
                        config.getInt("forge.salvage-base", 8).coerceAtLeast(0),
                        config.getDouble("forge.salvage-score-scale", 0.35).coerceAtLeast(0.0),
                        config.getInt("forge.salvage-forge-bonus", 6).coerceAtLeast(0)
                    )
                    if (result.success && result.reward > 0) {
                        ShardRewardManager.addRunShards(player.uniqueId, result.reward)
                    }
                    player.sendMessage(result.message)
                    openForgeUI(player, instance)
                    return@onClick
                }

                if (temperUnlocked && event.clickEvent().click == ClickType.SHIFT_RIGHT) {
                    val price = getTemperPrice(equipped.forgeLevel)
                    if (!ShardRewardManager.takeRunShards(player.uniqueId, price)) {
                        player.sendMessage("§c本局碎片不足，淬火需要 §e$price §c碎片。")
                        return@onClick
                    }
                    if (!ForgeMaterialManager.take(player.uniqueId, ForgeMaterialType.BOSS_EMBER, temperEmberCost)) {
                        ShardRewardManager.addRunShards(player.uniqueId, price)
                        player.sendMessage("§c缺少 ${ForgeMaterialType.BOSS_EMBER.coloredName()} §c，淬火需要 §6x$temperEmberCost")
                        return@onClick
                    }
                    val result = DungeonLootManager.temperEquipped(
                        player,
                        instance,
                        equipmentSlot,
                        config.getInt("forge.max-upgrades", 3).coerceAtLeast(1),
                        config.getDouble("forge.attribute-bonus-per-level", 0.18),
                        getHeatCap(),
                        getTemperHeatGain(),
                        getTemperLevelGain(player)
                    )
                    if (!result.success) {
                        ShardRewardManager.addRunShards(player.uniqueId, price)
                        ForgeMaterialManager.add(player.uniqueId, ForgeMaterialType.BOSS_EMBER, temperEmberCost)
                    }
                    player.sendMessage(result.message)
                    openForgeUI(player, instance)
                    return@onClick
                }

                if (event.clickEvent().click == ClickType.RIGHT) {
                    val price = getUpgradePrice(equipped.forgeLevel)
                    if (!ShardRewardManager.takeRunShards(player.uniqueId, price)) {
                        player.sendMessage("§c本局碎片不足，升阶需要 §e$price §c碎片。")
                        return@onClick
                    }
                    val result = DungeonLootManager.upgradeEquipped(
                        player,
                        equipmentSlot,
                        config.getInt("forge.max-upgrades", 3).coerceAtLeast(1),
                        config.getDouble("forge.attribute-bonus-per-level", 0.18),
                        getHeatCap(),
                        getUpgradeHeatGain()
                    )
                    if (!result.success) {
                        ShardRewardManager.addRunShards(player.uniqueId, price)
                    }
                    player.sendMessage(result.message)
                    openForgeUI(player, instance)
                    return@onClick
                }

                if (!ShardRewardManager.takeRunShards(player.uniqueId, rerollPrice)) {
                    player.sendMessage("§c本局碎片不足，重铸需要 §e$rerollPrice §c碎片。")
                    return@onClick
                }
                val result = DungeonLootManager.reforgeEquipped(
                    player,
                    instance,
                    equipmentSlot,
                    config.getDouble("forge.attribute-bonus-per-level", 0.18),
                    getHeatCap(),
                    getReforgeHeatGain(player)
                )
                if (!result.success) {
                    ShardRewardManager.addRunShards(player.uniqueId, rerollPrice)
                }
                player.sendMessage(result.message)
                openForgeUI(player, instance)
            }
        }
    }

    private fun getUpgradePrice(forgeLevel: Int): Int {
        val base = config.getInt("forge.upgrade-price", 22).coerceAtLeast(0)
        val extra = config.getInt("forge.upgrade-price-per-level", 10).coerceAtLeast(0)
        return base + (forgeLevel * extra)
    }

    private fun getTemperPrice(forgeLevel: Int): Int {
        val base = config.getInt("forge.temper-price", 28).coerceAtLeast(0)
        val extra = config.getInt("forge.temper-price-per-level", 12).coerceAtLeast(0)
        return base + (forgeLevel * extra)
    }

    private fun getLockPrice(player: Player): Int {
        val base = config.getInt("forge.lock-affix-price", 6)
        return (base - UnlockManager.getLockShardDiscount(player)).coerceAtLeast(0)
    }

    private fun getLockSigilCost(): Int {
        return config.getInt("forge.materials.hidden-sigil.lock-cost", 1).coerceAtLeast(0)
    }

    private fun getTemperEmberCost(): Int {
        return config.getInt("forge.materials.boss-ember.temper-cost", 1).coerceAtLeast(0)
    }

    private fun getCoolingEmberCost(): Int {
        return config.getInt("forge.cooling.ember-cost", 1).coerceAtLeast(0)
    }

    private fun getCoolingHeatReduce(player: Player): Int {
        return (config.getInt("forge.cooling.heat-reduce", 2) + UnlockManager.getCoolingHeatBonus(player)).coerceAtLeast(1)
    }

    private fun getNoHeatReforgePrice(player: Player): Int {
        val base = config.getInt("forge.no-heat-reforge.shard-price", 28)
        return (base - UnlockManager.getRefinedReforgeShardDiscount(player)).coerceAtLeast(0)
    }

    private fun getNoHeatReforgeEmberCost(): Int {
        return config.getInt("forge.no-heat-reforge.ember-cost", 1).coerceAtLeast(0)
    }

    private fun getNoHeatReforgeSigilCost(player: Player): Int {
        val base = config.getInt("forge.no-heat-reforge.sigil-cost", 1)
        return (base - UnlockManager.getRefinedReforgeSigilDiscount(player)).coerceAtLeast(0)
    }

    private fun getHeatCap(): Int {
        return config.getInt("forge.heat.max", 6).coerceAtLeast(0)
    }

    private fun getReforgeHeatGain(player: Player): Int {
        return (config.getInt("forge.heat.reforge", 1) + UnlockManager.getReforgeHeatModifier(player)).coerceAtLeast(0)
    }

    private fun getUpgradeHeatGain(): Int {
        return config.getInt("forge.heat.upgrade", 2).coerceAtLeast(0)
    }

    private fun getTemperHeatGain(): Int {
        return config.getInt("forge.heat.temper", 3).coerceAtLeast(0)
    }

    private fun getTemperLevelGain(player: Player): Int {
        return 1 + UnlockManager.getTemperLevelBonus(player)
    }

    private fun toDisplayItem(player: Player, equipmentSlot: EquipmentSlot, temperUnlocked: Boolean): ItemStack {
        val equipped = DungeonLootManager.getEquippedLoot(player, equipmentSlot)
        if (equipped == null) {
            return placeholder(equipmentSlot)
        }
        val temperLevelGain = getTemperLevelGain(player)

        return equipped.item.clone().apply {
            itemMeta = itemMeta?.also { meta ->
                val lore = (meta.lore ?: emptyList()).toMutableList()
                val lockedAffixName = DungeonLootManager.getLockedAffixName(player, equipmentSlot)
                lore += ""
                lore += "§7当前锻造等级: §6+${equipped.forgeLevel}"
                lore += if (getHeatCap() > 0) {
                    "§7锻造热度: §c${equipped.forgeHeat}/${getHeatCap()}"
                } else {
                    "§8锻造热度: 未启用"
                }
                lore += if (lockedAffixName != null) "§6当前锁词: §f$lockedAffixName" else "§8当前锁词: 无"
                lore += "§e左键: 重铸"
                lore += "§e右键: 升阶 (${getUpgradePrice(equipped.forgeLevel)} 本局碎片)"
                lore += if (UnlockManager.hasPrecisionLocking(player)) {
                    "§e中键: 锁词循环 (${getLockPrice(player)} 碎片 + ${ForgeMaterialType.HIDDEN_SIGIL.displayName} x${getLockSigilCost()})"
                } else {
                    "§8中键: 需要研究“精密锁词”"
                }
                lore += if (UnlockManager.hasEmberCooling(player)) {
                    "§eQ键: 余烬退火 (${ForgeMaterialType.BOSS_EMBER.displayName} x${getCoolingEmberCost()}, -${getCoolingHeatReduce(player)} 热度)"
                } else {
                    "§8Q键: 需要研究“余烬退火”"
                }
                lore += if (UnlockManager.hasRefinedReforge(player)) {
                    "§eCtrl+Q: 精炼重铸 (${getNoHeatReforgePrice(player)} 碎片 + ${ForgeMaterialType.BOSS_EMBER.displayName} x${getNoHeatReforgeEmberCost()} + ${ForgeMaterialType.HIDDEN_SIGIL.displayName} x${getNoHeatReforgeSigilCost(player)})"
                } else {
                    "§8Ctrl+Q: 需要研究“精炼重铸”"
                }
                lore += "§eShift+左键: 分解 (+${getSalvageReward(player, equipmentSlot)} 本局碎片)"
                if (temperUnlocked) {
                    lore += "§eShift+右键: 淬火 (${getTemperPrice(equipped.forgeLevel)} 碎片 + ${ForgeMaterialType.BOSS_EMBER.displayName} x${getTemperEmberCost()}, +${temperLevelGain}级)"
                }
                meta.lore = lore
            }
        }
    }

    private fun getSalvageReward(player: Player, equipmentSlot: EquipmentSlot): Int {
        return DungeonLootManager.getSalvageReward(
            player,
            equipmentSlot,
            config.getInt("forge.salvage-base", 8).coerceAtLeast(0),
            config.getDouble("forge.salvage-score-scale", 0.35).coerceAtLeast(0.0),
            config.getInt("forge.salvage-forge-bonus", 6).coerceAtLeast(0)
        )
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
                meta.lore = listOf("", "§8未装备可锻造的临时装备")
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
}
