package inori.roguecore.ui

import inori.roguecore.boon.Boon
import inori.roguecore.boon.BoonResonanceManager
import inori.roguecore.boon.PlayerBoonData
import inori.roguecore.data.ShardRewardManager
import inori.roguecore.dungeon.DungeonManager
import inori.roguecore.modifier.RunModifierManager
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest

/**
 * Boon 选择界面。
 */
object BoonSelectUI {

    /**
     * 打开 Boon 选择界面
     * @param player 目标玩家
     * @param boons 可选的 Boon 列表（最多 4 个）
     */
    fun open(player: Player, boons: List<Boon>) {
        if (boons.isEmpty()) return

        val title = "§6§l选择神恩"
        DungeonGuiGuard.lock(player, title) { target -> open(target, boons) }

        player.openMenu<Chest>(title) {
            rows(4)
            handLocked(true)

            val slots = when (boons.size) {
                1 -> listOf(13)
                2 -> listOf(12, 14)
                3 -> listOf(11, 13, 15)
                else -> listOf(10, 12, 14, 16)
            }
            val infoSlots = setOf(4, 22, 31)
            val availableSlots = slots.toSet() + infoSlots

            val glass = XMaterial.BLACK_STAINED_GLASS_PANE.parseItem()!!.apply {
                itemMeta = itemMeta?.also { it.setDisplayName(" ") }
            }
            for (i in 0 until 36) {
                if (i !in availableSlots) {
                    set(i, glass)
                }
            }

            set(4, buildBuildItem(player))
            set(22, buildCandidateInfoItem(player, boons))
            set(31, buildRunInfoItem(player))

            for ((index, slot) in slots.withIndex()) {
                if (index >= boons.size) break
                val boon = boons[index]
                val currentInstance = PlayerBoonData.getBoons(player).firstOrNull { it.boon.id == boon.id }
                val currentLevel = currentInstance?.level ?: 0
                val nextLevel = currentLevel + 1
                set(slot, buildBoonItem(player, boon, currentLevel, nextLevel))
            }

            onClick { event ->
                event.isCancelled = true
                val boonIndex = slots.indexOf(event.rawSlot)
                if (boonIndex < 0 || boonIndex >= boons.size) return@onClick

                val selectedBoon = boons[boonIndex]
                val echoCopies = RunModifierManager.consumeBoonEcho(player)
                val mutated = RunModifierManager.consumeBoonMutation(player)
                DungeonGuiGuard.unlock(player)
                player.closeInventory()
                PlayerBoonData.addBoon(player, selectedBoon)
                if (echoCopies > 0) {
                    repeat(echoCopies) {
                        PlayerBoonData.addBoon(player, selectedBoon, triggerOnAcquire = false)
                    }
                    player.sendMessage("§d神恩回响复制了本次选择。")
                }
                if (mutated) {
                    player.sendMessage("§5神恩变质已经消散。")
                }
            }
        }
    }

    private fun buildBuildItem(player: Player): ItemStack {
        val owned = PlayerBoonData.getBoons(player)
        val tagSummary = owned
            .flatMap { instance -> instance.boon.tags }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(3)
            .joinToString(" / ") { "${it.key}x${it.value}" }
            .ifBlank { "尚未成型" }

        return XMaterial.NETHER_STAR.parseItem()!!.apply {
            itemMeta = itemMeta?.also { meta ->
                meta.setDisplayName("§6当前构筑")
                meta.lore = buildList {
                    add("")
                    add("§7已持有神恩: §e${owned.size}")
                    add("§7主流派: §f$tagSummary")
                    val resonances = BoonResonanceManager.getActiveResonanceLines(player)
                    if (resonances.isNotEmpty()) {
                        add("§6已激活共鸣:")
                        addAll(resonances.take(4))
                    } else {
                        add("§7流派共鸣: §8同标签达到 3/5/7 激活")
                    }
                    add("§7同名神恩会直接升级")
                    add("")
                    add("§e本次选择会立刻生效")
                }
            }
        }
    }

    private fun buildCandidateInfoItem(player: Player, boons: List<Boon>): ItemStack {
        val candidateTags = boons.flatMap { it.tags }.distinct()
        val synergy = candidateTags.joinToString(" / ").ifBlank { "无明显流派" }
        val owned = PlayerBoonData.getBoons(player)
        val repeatable = boons.count { boon -> owned.any { it.boon.id == boon.id } }

        return XMaterial.WRITABLE_BOOK.parseItem()!!.apply {
            itemMeta = itemMeta?.also { meta ->
                meta.setDisplayName("§e候选摘要")
                meta.lore = buildList {
                    add("")
                    add("§7候选数量: §f${boons.size}")
                    add("§7覆盖流派: §f$synergy")
                    if (repeatable > 0) {
                        add("§7其中可直接升级: §a$repeatable 项")
                    } else {
                        add("§7本轮更偏向补全新构筑")
                    }
                    val mutationExtra = RunModifierManager.getBoonOfferExtra(player)
                    if (mutationExtra > 0) {
                        add("§5神恩变质: §d+$mutationExtra §5候选，选择后消耗")
                    }
                    add("")
                    add("§e建议优先选能补主流派的项")
                }
            }
        }
    }

    private fun buildRunInfoItem(player: Player): ItemStack {
        val dungeon = DungeonManager.getPlayerDungeon(player)
        val runShards = ShardRewardManager.getRunShards(player.uniqueId)
        return XMaterial.COMPASS.parseItem()!!.apply {
            itemMeta = itemMeta?.also { meta ->
                meta.setDisplayName("§b冒险状态")
                meta.lore = buildList {
                    add("")
                    if (dungeon != null) {
                        add("§7当前楼层: §f${dungeon.config.floorNumber}")
                        add("§7隐藏钥匙: §b${dungeon.getHiddenKeys()}")
                        add("§7本局碎片: §6$runShards")
                    } else {
                        add("§7当前不在副本中")
                    }
                    add("")
                    add("§7关闭界面会被重新拉回")
                }
            }
        }
    }

    /**
     * 构建 Boon 展示物品
     */
    private fun buildBoonItem(player: Player, boon: Boon, currentLevel: Int, nextLevel: Int): ItemStack {
        val item = boon.icon.parseItem() ?: XMaterial.PAPER.parseItem()!!
        val meta = item.itemMeta ?: return item

        val rarityTag = "${boon.rarity.color}[${boon.rarity.displayName}]"
        val levelTag = if (currentLevel > 0) " §eLv.$currentLevel → Lv.$nextLevel" else " §eLv.1"
        val tagCounts = boon.tags.associateWith { PlayerBoonData.getTagCount(player.uniqueId, it) }
        val bestSynergy = tagCounts.maxByOrNull { it.value }

        meta.setDisplayName("$rarityTag §f${boon.name}$levelTag")
        meta.lore = buildList {
            add("")
            addAll(boon.getPreviewLore(nextLevel))
            if (bestSynergy != null && bestSynergy.value > 0) {
                val afterPick = bestSynergy.value + 1
                val levelHint = when {
                    afterPick >= 7 -> "§6III"
                    afterPick >= 5 -> "§eII"
                    afterPick >= 3 -> "§aI"
                    else -> "§8未激活"
                }
                add("§7当前流派共鸣: §a${bestSynergy.key} x${bestSynergy.value} §7→ x$afterPick $levelHint")
            }
            val echo = RunModifierManager.getModifiers(player).firstOrNull { it.type == inori.roguecore.modifier.RunModifierType.BOON_ECHO }
            if (echo != null) {
                add("§d神恩回响: §7选择后额外复制一次")
            }
            add("")
            add(if (currentLevel > 0) "§e点击升级" else "§e点击选择")
        }
        item.itemMeta = meta
        return item
    }
}
