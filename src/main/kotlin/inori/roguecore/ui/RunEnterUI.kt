package inori.roguecore.ui

import inori.roguecore.data.PlayerDataManager
import inori.roguecore.display.ContentDisplayNameResolver
import inori.roguecore.dungeon.DungeonManager
import inori.roguecore.dungeon.floor.FloorManager
import inori.roguecore.party.PartyManager
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest

/** 副本进入层数选择面板。 */
object RunEnterUI {

    private const val TOTAL_FLOORS = 100
    private const val PAGE_SIZE = 28
    private val floorKeys = "abcdefghijklmnopqrstuvwxyz12".toList()

    fun open(player: Player, page: Int = 0) {
        val bestFloor = PlayerDataManager.get(player.uniqueId).bestFloor
        val recommendedFloor = recommendedFloor(bestFloor)
        val party = PartyManager.getParty(player)
        val canRejoin = DungeonManager.canRejoinDungeon(player.uniqueId)
        val maxPage = ((TOTAL_FLOORS - 1) / PAGE_SIZE).coerceAtLeast(0)
        val safePage = page.coerceIn(0, maxPage)
        val firstFloor = safePage * PAGE_SIZE + 1
        val visibleFloors = (firstFloor until firstFloor + PAGE_SIZE).filter { it <= TOTAL_FLOORS }

        player.openMenu<Chest>("§a§l选择副本层数") {
            rows(6)
            handLocked(true)
            map(
                "####H####",
                "#abcdefg#",
                "#hijklmn#",
                "#opqrstu#",
                "#vwxyz12#",
                "#P#Q#B#N#"
            )

            val glass = glass()
            set('#', glass)
            floorKeys.forEach { set(it, emptyFloorSlot()) }
            set('H', summaryItem(player, bestFloor, recommendedFloor, safePage, maxPage, canRejoin))
            visibleFloors.forEachIndexed { index, floor ->
                val key = floorKeys[index]
                set(key, floorItem(floor, bestFloor, recommendedFloor, party != null))
                onClick(key) { player.performCommand("rogue run enter $floor") }
            }
            set('P', if (safePage > 0) pageItem("§e上一页", "§7查看更低层数") else glass)
            set('N', if (safePage < maxPage) pageItem("§e下一页", "§7查看更高层数") else glass)
            set('Q', quickItem(bestFloor, recommendedFloor, canRejoin))
            set('B', backItem())

            onClick { event -> event.isCancelled = true }
            onClick('P') { if (safePage > 0) open(player, safePage - 1) }
            onClick('N') { if (safePage < maxPage) open(player, safePage + 1) }
            onClick('Q') {
                if (canRejoin) {
                    player.closeInventory()
                    player.performCommand("rogue run rejoin")
                } else {
                    player.performCommand("rogue run enter $recommendedFloor")
                }
            }
            onClick('B') { RogueMenuUI.open(player) }
        }
    }

    private fun recommendedFloor(bestFloor: Int): Int {
        return (bestFloor + 1).coerceIn(1, TOTAL_FLOORS)
    }

    private fun summaryItem(
        player: Player,
        bestFloor: Int,
        recommendedFloor: Int,
        page: Int,
        maxPage: Int,
        canRejoin: Boolean
    ): ItemStack {
        val party = PartyManager.getParty(player)
        val partyLine = when {
            party == null -> "§8当前为单人冒险"
            party.isLeader(player.uniqueId) -> "§e队伍模式: §f${party.size}/${party.maxSize} §7(队长开启)"
            else -> "§c队伍模式: 只有队长可以开启副本"
        }
        return XMaterial.BEACON.parseItem()!!.apply {
            itemMeta = itemMeta?.also { meta ->
                meta.setDisplayName("§a§l选择副本层数")
                meta.lore = buildList {
                    add("")
                    add("§7选择一个楼层作为本次冒险起点。")
                    add("§7最高通关: §f$bestFloor")
                    add("§7推荐挑战: §e第 $recommendedFloor 层")
                    add(partyLine)
                    if (canRejoin) add("§b你有未结束副本，可使用底部按钮重连。")
                    add("")
                    add("§8第 ${page + 1}/${maxPage + 1} 页")
                }
            }
        }
    }

    private fun floorItem(floor: Int, bestFloor: Int, recommendedFloor: Int, inParty: Boolean): ItemStack {
        val config = FloorManager.getFloorConfig(floor)
        val status = floorStatus(floor, bestFloor, recommendedFloor)
        val item = config.theme.accent.parseItem()
            ?: config.theme.floor.parseItem()
            ?: XMaterial.FILLED_MAP.parseItem()!!
        val themeName = ContentDisplayNameResolver.safeText(config.theme.name, "未知主题")
        return item.apply {
            itemMeta = itemMeta?.also { meta ->
                meta.setDisplayName("${status.nameColor}第 $floor 层 §7- §f$themeName")
                meta.lore = buildList {
                    add("")
                    add("§7主题: §f$themeName")
                    add("§7规模: §f${config.dungeonWidth}x${config.dungeonDepth} §8(布局深度 ${config.maxBSPDepth})")
                    add("§7难度段: ${difficultyLabel(floor)}")
                    add("§7状态: ${status.line}")
                    if (inParty) add("§7队伍: §e将为全队生成同一副本")
                    add("")
                    add("§e左键进入这一层")
                }
            }
        }
    }

    private fun floorStatus(floor: Int, bestFloor: Int, recommendedFloor: Int): FloorStatus {
        return when {
            floor <= bestFloor -> FloorStatus("§a", "§a已通关记录")
            floor == recommendedFloor -> FloorStatus("§e", "§e推荐下一层")
            else -> FloorStatus("§7", "§7可直接挑战")
        }
    }

    private fun difficultyLabel(floor: Int): String {
        return when (floor) {
            in 1..25 -> "§a入门"
            in 26..50 -> "§e进阶"
            in 51..75 -> "§6深层"
            else -> "§c终局"
        }
    }

    private fun quickItem(bestFloor: Int, recommendedFloor: Int, canRejoin: Boolean): ItemStack {
        return if (canRejoin) {
            XMaterial.ENDER_PEARL.parseItem()!!.apply {
                itemMeta = itemMeta?.also { meta ->
                    meta.setDisplayName("§b重连未结束副本")
                    meta.lore = listOf("", "§7你有可重连的冒险。", "", "§e左键重连")
                }
            }
        } else {
            XMaterial.EMERALD.parseItem()!!.apply {
                itemMeta = itemMeta?.also { meta ->
                    val label = if (bestFloor >= TOTAL_FLOORS) "最高层" else "推荐下一层"
                    meta.setDisplayName("§a$label: §f第 $recommendedFloor 层")
                    meta.lore = listOf("", "§7最高通关: §f$bestFloor", "§7点击快速进入推荐层数", "", "§e左键进入")
                }
            }
        }
    }

    private fun pageItem(name: String, lore: String): ItemStack = XMaterial.ARROW.parseItem()!!.apply {
        itemMeta = itemMeta?.also { meta ->
            meta.setDisplayName(name)
            meta.lore = listOf("", lore)
        }
    }

    private fun backItem(): ItemStack = XMaterial.OAK_DOOR.parseItem()!!.apply {
        itemMeta = itemMeta?.also { meta ->
            meta.setDisplayName("§e返回主菜单")
            meta.lore = listOf("", "§7返回 RogueCore 控制台")
        }
    }

    private fun glass(): ItemStack = XMaterial.LIME_STAINED_GLASS_PANE.parseItem()!!.apply {
        itemMeta = itemMeta?.also { it.setDisplayName(" ") }
    }

    private fun emptyFloorSlot(): ItemStack = XMaterial.GRAY_STAINED_GLASS_PANE.parseItem()!!.apply {
        itemMeta = itemMeta?.also { it.setDisplayName(" ") }
    }

    private data class FloorStatus(val nameColor: String, val line: String)
}
