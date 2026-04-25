package inori.roguecore.event

import inori.roguecore.boon.Boon
import inori.roguecore.boon.BoonSelectManager
import inori.roguecore.boon.PlayerBoonData
import inori.roguecore.data.ForgeMaterialManager
import inori.roguecore.data.ForgeMaterialType
import inori.roguecore.data.ShardRewardManager
import inori.roguecore.dungeon.DungeonInstance
import inori.roguecore.dungeon.room.RoomType
import inori.roguecore.item.DungeonLootManager
import inori.roguecore.relic.RelicSelectManager
import inori.roguecore.ui.DungeonGuiGuard
import inori.roguecore.unlock.UnlockManager
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.library.xseries.XMaterial
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest
import kotlin.random.Random

/**
 * 商店房事件 — 用灵魂碎片购买 Boon/回复
 */
object ShopEvent {

    @Config("events.yml")
    lateinit var config: Configuration
        private set

    fun trigger(player: Player, instance: DungeonInstance) {
        val boonPriceMin = EventScaling.price(instance, config.getInt("shop.boon-price-min", 20))
        val boonPriceMax = EventScaling.price(instance, config.getInt("shop.boon-price-max", 50)).coerceAtLeast(boonPriceMin)
        val healPrice = EventScaling.price(instance, config.getInt("shop.heal-price", 15))
        val shopPower = EventAffixManager.getFamilyPower(instance, RoomType.SHOP, "SHOP")
        val relicPrice = (EventScaling.price(instance, config.getInt("shop.relic-price", 48)) - shopPower * 3).coerceAtLeast(0)
        val marketFever = shopPower > 0
        val shopBoonCount = EventScaling.boonOfferCount(instance, 1 + shopPower.coerceAtMost(4))
        val blackMarketUnlocked = marketFever ||
            instance.config.floorNumber >= config.getInt("event-variants.shop.black-market-floor", 6)

        val goods = mutableListOf<ShopGood>()
        goods.add(ShopGood.Heal(healPrice))

        val boonCandidates = BoonSelectManager.rollBoons(shopBoonCount, player)
        if (boonCandidates.isNotEmpty()) {
            val price = Random.nextInt(boonPriceMin, boonPriceMax + 1)
            boonCandidates.forEachIndexed { index, boon ->
                goods.add(ShopGood.BoonGood(boon, price + index * (boonPriceMin / 3).coerceAtLeast(2)))
            }
        }

        if (UnlockManager.hasArcaneExchange(player)) {
            goods.add(ShopGood.RelicGood(relicPrice))
        }

        if (blackMarketUnlocked) {
            goods.add(
                ShopGood.BlackMarketGood(
                    price = (
                        EventScaling.price(instance, config.getInt("event-variants.shop.black-market-price", 72)) -
                            shopPower * 4
                        ).coerceAtLeast(0),
                    emberReward = config.getInt("event-variants.shop.black-market-ember-reward", 2).coerceAtLeast(0) +
                        shopPower.coerceAtMost(5),
                    sigilReward = config.getInt("event-variants.shop.black-market-sigil-reward", 1).coerceAtLeast(0) +
                        (shopPower / 2).coerceAtMost(4),
                    grantHiddenLoot = config.getBoolean("event-variants.shop.black-market-grant-hidden-loot", true)
                )
            )
        }

        openShopUI(player, instance, goods, ShardRewardManager.getRunShards(player.uniqueId))
    }

    private fun openShopUI(player: Player, instance: DungeonInstance, goods: List<ShopGood>, shards: Int) {
        val slots = displaySlots(goods.size)
        val title = "§6§l商店 §7(本局碎片: §e$shards§7)"
        DungeonGuiGuard.lock(player, title) { target ->
            openShopUI(target, instance, goods, ShardRewardManager.getRunShards(target.uniqueId))
        }

        player.openMenu<Chest>(title) {
            rows(3)
            handLocked(true)

            val glass = XMaterial.YELLOW_STAINED_GLASS_PANE.parseItem()!!.apply {
                itemMeta = itemMeta?.also { it.setDisplayName(" ") }
            }
            for (i in 0 until 27) {
                if (i !in slots) {
                    set(i, glass)
                }
            }

            for ((index, slot) in slots.withIndex()) {
                if (index >= goods.size) break
                set(slot, goods[index].toItemStack())
            }

            onClick { event ->
                event.isCancelled = true
                val goodIndex = slots.indexOf(event.rawSlot)
                if (goodIndex < 0 || goodIndex >= goods.size) return@onClick

                val good = goods[goodIndex]
                DungeonGuiGuard.unlock(player)
                player.closeInventory()

                if (!ShardRewardManager.takeRunShards(player.uniqueId, good.price)) {
                    player.sendMessage("§c本局碎片不足! 需要 §e${good.price} §c碎片")
                    return@onClick
                }

                when (good) {
                    is ShopGood.Heal -> {
                        val maxHp = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH)?.value ?: 20.0
                        player.health = maxHp
                        player.sendMessage("§a生命已回满! §7(-${good.price}本局碎片)")
                    }

                    is ShopGood.BoonGood -> {
                        PlayerBoonData.addBoon(player, good.boon)
                        player.sendMessage("§7(-${good.price}本局碎片)")
                    }

                    is ShopGood.RelicGood -> {
                        player.sendMessage("§7(-${good.price}本局碎片)")
                        if (!RelicSelectManager.offerRelicSelection(player, EventScaling.relicOfferCount(instance, 3, UnlockManager.getRelicOfferBonus(player)))) {
                            player.sendMessage("§7没有新的遗物可供交易，契据退化为一次神恩选择。")
                            BoonSelectManager.offerBoonSelection(player, EventScaling.boonOfferCount(instance))
                        }
                    }

                    is ShopGood.BlackMarketGood -> {
                        player.sendMessage("§7(-${good.price}本局碎片)")
                        if (good.emberReward > 0) {
                            ForgeMaterialManager.add(player.uniqueId, ForgeMaterialType.BOSS_EMBER, good.emberReward)
                            player.sendMessage("§6黑市补给送来 ${ForgeMaterialType.BOSS_EMBER.coloredName()} §ex${good.emberReward}")
                        }
                        if (good.sigilReward > 0) {
                            ForgeMaterialManager.add(player.uniqueId, ForgeMaterialType.HIDDEN_SIGIL, good.sigilReward)
                            player.sendMessage("§9黑市补给送来 ${ForgeMaterialType.HIDDEN_SIGIL.coloredName()} §bx${good.sigilReward}")
                        }
                        if (good.grantHiddenLoot && DungeonLootManager.grantHiddenLoot(player, instance)) {
                            player.sendMessage("§5黑市货柜中还藏着一件深层战利品。")
                        }
                    }
                }
            }
        }
    }

    private fun displaySlots(size: Int): List<Int> {
        return when (size) {
            1 -> listOf(13)
            2 -> listOf(11, 15)
            3 -> listOf(11, 13, 15)
            4 -> listOf(10, 12, 14, 16)
            5 -> listOf(9, 11, 13, 15, 17)
            else -> listOf(10, 11, 12, 14, 15, 16)
        }
    }

    sealed class ShopGood(val price: Int) {
        abstract fun toItemStack(): ItemStack

        class Heal(price: Int) : ShopGood(price) {
            override fun toItemStack(): ItemStack {
                return XMaterial.GOLDEN_APPLE.parseItem()!!.apply {
                    itemMeta = itemMeta?.also { meta ->
                        meta.setDisplayName("§a回复生命")
                        meta.lore = listOf("", "§7回满所有生命值", "", "§e价格: §6$price §e本局碎片", "§e点击购买")
                    }
                }
            }
        }

        class BoonGood(val boon: Boon, price: Int) : ShopGood(price) {
            override fun toItemStack(): ItemStack {
                return (boon.icon.parseItem() ?: XMaterial.PAPER.parseItem()!!).apply {
                    itemMeta = itemMeta?.also { meta ->
                        meta.setDisplayName("${boon.rarity.color}[${boon.rarity.displayName}] §f${boon.name}")
                        val lore = mutableListOf<String>()
                        lore.add("")
                        lore.addAll(boon.getPreviewLore(1))
                        lore.add("")
                        lore.add("§e价格: §6$price §e本局碎片")
                        lore.add("§e点击购买")
                        meta.lore = lore
                    }
                }
            }
        }

        class RelicGood(price: Int) : ShopGood(price) {
            override fun toItemStack(): ItemStack {
                return XMaterial.AMETHYST_SHARD.parseItem()!!.apply {
                    itemMeta = itemMeta?.also { meta ->
                        meta.setDisplayName("§d遗物契据")
                        meta.lore = listOf(
                            "",
                            "§7购买后可获得一次 §d遗物选择",
                            "§7若没有可选遗物，则退化为神恩选择",
                            "",
                            "§e价格: §6$price §e本局碎片",
                            "§e点击购买"
                        )
                    }
                }
            }
        }

        class BlackMarketGood(
            price: Int,
            val emberReward: Int,
            val sigilReward: Int,
            val grantHiddenLoot: Boolean
        ) : ShopGood(price) {
            override fun toItemStack(): ItemStack {
                return XMaterial.ENDER_CHEST.parseItem()!!.apply {
                    itemMeta = itemMeta?.also { meta ->
                        meta.setDisplayName("§5黑市货柜")
                        meta.lore = buildList {
                            add("")
                            if (grantHiddenLoot) {
                                add("§7开启后获得一件 §6隐藏宝藏级 §7战利品")
                            }
                            if (emberReward > 0) {
                                add("§7附带 ${ForgeMaterialType.BOSS_EMBER.coloredName()} §6x$emberReward")
                            }
                            if (sigilReward > 0) {
                                add("§7附带 ${ForgeMaterialType.HIDDEN_SIGIL.coloredName()} §bx$sigilReward")
                            }
                            add("")
                            add("§e价格: §6$price §e本局碎片")
                            add("§e点击购买")
                        }
                    }
                }
            }
        }
    }
}
