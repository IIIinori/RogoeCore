package inori.roguecore.ui

import inori.roguecore.data.ShardRewardManager
import inori.roguecore.dungeon.DungeonManager
import inori.roguecore.relic.PlayerRelicData
import inori.roguecore.relic.Relic
import inori.roguecore.relic.RelicEffectType
import org.bukkit.entity.Player
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest

object RelicSelectUI {

    fun open(player: Player, relics: List<Relic>) {
        if (relics.isEmpty()) {
            return
        }

        val title = "§d§l选择遗物"
        DungeonGuiGuard.lock(player, title) { target -> open(target, relics) }

        player.openMenu<Chest>(title) {
            rows(4)
            handLocked(true)

            val slots = when (relics.size) {
                1 -> listOf(13)
                2 -> listOf(12, 14)
                3 -> listOf(11, 13, 15)
                else -> listOf(10, 12, 14, 16)
            }
            val infoSlots = setOf(4, 22, 31)
            val activeSlots = slots.toSet() + infoSlots

            val glass = XMaterial.PURPLE_STAINED_GLASS_PANE.parseItem()!!.apply {
                itemMeta = itemMeta?.also { it.setDisplayName(" ") }
            }
            for (slot in 0 until 36) {
                if (slot !in activeSlots) {
                    set(slot, glass)
                }
            }

            set(4, buildOwnedRelicInfo(player))
            set(22, buildCandidateRelicInfo(relics))
            set(31, buildRunInfo(player))

            for ((index, slot) in slots.withIndex()) {
                if (index >= relics.size) {
                    break
                }
                val relic = relics[index]
                set(slot, buildRelicItem(player, relic))
            }

            onClick { event ->
                event.isCancelled = true
                val index = slots.indexOf(event.rawSlot)
                if (index < 0 || index >= relics.size) {
                    return@onClick
                }
                DungeonGuiGuard.unlock(player)
                player.closeInventory()
                PlayerRelicData.addRelic(player, relics[index])
            }
        }
    }

    private fun buildOwnedRelicInfo(player: Player) = XMaterial.AMETHYST_CLUSTER.parseItem()!!.apply {
        val owned = PlayerRelicData.getRelics(player)
        val raritySummary = owned.groupBy { it.rarity }
            .entries
            .sortedBy { it.key.ordinal }
            .joinToString(" / ") { "${it.key.displayName}x${it.value.size}" }
            .ifBlank { "尚未拥有遗物" }

        itemMeta = itemMeta?.also { meta ->
            meta.setDisplayName("§d遗物收纳")
            meta.lore = listOf(
                "",
                "§7当前遗物数: §f${owned.size}",
                "§7稀有度分布: §f$raritySummary",
                "§7遗物不会重复获得",
                "",
                "§e慎选与当前构筑契合的效果"
            )
        }
    }

    private fun buildCandidateRelicInfo(relics: List<Relic>) = XMaterial.BOOK.parseItem()!!.apply {
        val typeSummary = relics.groupBy { describeEffectType(it.effectType) }
            .entries
            .joinToString(" / ") { "${it.key}x${it.value.size}" }

        itemMeta = itemMeta?.also { meta ->
            meta.setDisplayName("§5候选遗物")
            meta.lore = listOf(
                "",
                "§7候选数量: §f${relics.size}",
                "§7效果分布: §f$typeSummary",
                "§7更高稀有度通常更偏构筑核心",
                "",
                "§e点击后立刻纳入本局遗物池"
            )
        }
    }

    private fun buildRunInfo(player: Player) = XMaterial.COMPASS.parseItem()!!.apply {
        val dungeon = DungeonManager.getPlayerDungeon(player)
        val runShards = ShardRewardManager.getRunShards(player.uniqueId)
        itemMeta = itemMeta?.also { meta ->
            meta.setDisplayName("§b冒险状态")
            meta.lore = buildList {
                add("")
                if (dungeon != null) {
                    add("§7当前楼层: §f${dungeon.config.floorNumber}")
                    add("§7本局碎片: §6$runShards")
                    add("§7隐藏钥匙: §b${dungeon.getHiddenKeys()}")
                } else {
                    add("§7当前不在副本中")
                }
                add("")
                add("§7关闭界面会被重新拉回")
            }
        }
    }

    private fun buildRelicItem(player: Player, relic: Relic) = (relic.icon.parseItem() ?: XMaterial.PAPER.parseItem()!!).apply {
        itemMeta = itemMeta?.also { meta ->
            meta.setDisplayName("${relic.rarity.color}[${relic.rarity.displayName}] §f${relic.name}")
            meta.lore = buildList {
                add("")
                add("§7类别: §f${describeEffectType(relic.effectType)}")
                add("§7${relic.description}")
                if (relic.threshold > 0) {
                    add("§7触发阈值: §c${relic.threshold.toInt()}% 生命")
                }
                val sameRarity = PlayerRelicData.getRelics(player).count { it.rarity == relic.rarity }
                add("§7当前同稀有度遗物: §f$sameRarity")
                add("")
                add("§e点击选择")
            }
        }
    }

    private fun describeEffectType(type: RelicEffectType): String {
        return when (type) {
            RelicEffectType.KILL_SHARD -> "击杀收益"
            RelicEffectType.ROOM_HEAL -> "战后续航"
            RelicEffectType.LOW_HEALTH_DAMAGE -> "残血爆发"
            RelicEffectType.BONUS_LOOT_CHANCE -> "额外掉落"
            RelicEffectType.SHOP_DISCOUNT -> "商店折扣"
            RelicEffectType.BOSS_EMBER_BONUS -> "Boss 材料"
            RelicEffectType.HIDDEN_KEY_CHANCE -> "隐藏钥匙"
            RelicEffectType.COMBAT_START_SHIELD -> "开战护盾"
            RelicEffectType.ELITE_LOOT_CHANCE -> "精英战利品"
            RelicEffectType.SHOP_HEAL_DISCOUNT -> "治疗折扣"
            RelicEffectType.SHOP_MATERIAL_BONUS -> "商店材料"
            RelicEffectType.SHRINE_RELIC_BOOST -> "祈愿强化"
            RelicEffectType.GAMBLE_INSURANCE -> "赌局保险"
            RelicEffectType.TRIAL_FORGE_BONUS -> "试炼材料"
            RelicEffectType.CHEST_MATERIAL_BONUS -> "宝箱材料"
            RelicEffectType.HIDDEN_LOOT_BONUS -> "隐藏战利品"
            RelicEffectType.BOON_OFFER_BONUS -> "神恩候选"
            RelicEffectType.ROOM_UPGRADE_CHANCE -> "清房顿悟"
            RelicEffectType.BOON_RARITY_LUCK -> "神恩幸运"
            RelicEffectType.BOSS_LOOT_CHANCE -> "Boss 战利品"
            RelicEffectType.HIDDEN_RELIC_BOOST -> "隐藏祈愿"
            RelicEffectType.ELITE_RELIC_CHANCE -> "精英遗物"
            RelicEffectType.BLACK_MARKET_DISCOUNT -> "黑市折扣"
            RelicEffectType.CHEST_EXTRA_LOOT_CHANCE -> "宝箱装备"
        }
    }
}
