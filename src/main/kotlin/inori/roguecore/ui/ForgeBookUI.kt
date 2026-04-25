package inori.roguecore.ui

import inori.roguecore.data.PermanentMaterialManager
import inori.roguecore.data.PlayerDataManager
import inori.roguecore.item.DungeonLootManager
import inori.roguecore.item.ForgeBookTaskManager
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest

/**
 * 锻造书打造界面。
 */
object ForgeBookUI {

    private val bookSlots = listOf(10, 11, 12, 19, 20, 21, 28, 29, 30)
    private val taskSlots = listOf(14, 15, 16, 23, 24, 25, 32, 33, 34)
    private const val CLAIM_ALL_SLOT = 39
    private const val CLOSE_SLOT = 40

    fun open(player: Player) {
        val shards = PlayerDataManager.get(player.uniqueId).soulShards
        val inventorySlots = DungeonLootManager.getForgeBookInventorySlots(player).take(bookSlots.size)
        val bookMapping = bookSlots.zip(inventorySlots).toMap()
        val tasks = ForgeBookTaskManager.getTasks(player.uniqueId)
        val completedCount = tasks.count { it.isDone() }
        val taskMapping = taskSlots.zip(tasks).toMap()

        player.openMenu<Chest>("§6§l装备打造 §7(灵魂碎片: §e$shards§7)") {
            rows(5)
            handLocked(true)

            val activeSlots = (bookSlots + taskSlots + 4 + CLAIM_ALL_SLOT + CLOSE_SLOT).toSet()
            val glass = XMaterial.GRAY_STAINED_GLASS_PANE.parseItem()!!.apply {
                itemMeta = itemMeta?.also { it.setDisplayName(" ") }
            }
            for (slot in 0 until 45) {
                if (slot !in activeSlots) {
                    set(slot, glass)
                }
            }

            set(4, infoItem(inventorySlots.size, tasks.size, completedCount, shards, player))
            set(CLAIM_ALL_SLOT, claimAllItem(completedCount))
            set(CLOSE_SLOT, closeItem())

            for ((menuSlot, inventorySlot) in bookMapping) {
                val item = player.inventory.getItem(inventorySlot) ?: continue
                set(menuSlot, toBookItem(player, item, inventorySlot))
            }

            for ((menuSlot, task) in taskMapping) {
                set(menuSlot, toTaskItem(player, task))
            }

            onClick { event ->
                event.isCancelled = true
                if (event.rawSlot == CLOSE_SLOT) {
                    player.closeInventory()
                    return@onClick
                }
                if (event.rawSlot == CLAIM_ALL_SLOT) {
                    val result = ForgeBookTaskManager.claimAll(player)
                    player.sendMessage(result.message)
                    open(player)
                    return@onClick
                }

                val inventorySlot = bookMapping[event.rawSlot]
                if (inventorySlot != null) {
                    val item = player.inventory.getItem(inventorySlot)
                    val info = DungeonLootManager.getForgeBookInfo(item)
                    if (info == null) {
                        player.sendMessage("§c这不是有效的锻造书。")
                        return@onClick
                    }
                    val quality = info.quality
                    if (!PermanentMaterialManager.takeCost(player, quality.materials)) {
                        player.sendMessage("§c锻造材料不足，需要 ${PermanentMaterialManager.formatCost(quality.materials)}")
                        return@onClick
                    }
                    if (!PlayerDataManager.takeSoulShards(player.uniqueId, quality.soulShards)) {
                        PermanentMaterialManager.addAll(player, quality.materials)
                        player.sendMessage("§c灵魂碎片不足，打造需要 §e${quality.soulShards} §c碎片。")
                        return@onClick
                    }
                    val result = ForgeBookTaskManager.start(player, inventorySlot)
                    if (!result.success) {
                        PlayerDataManager.addSoulShards(player.uniqueId, quality.soulShards)
                        PermanentMaterialManager.addAll(player, quality.materials)
                    }
                    player.sendMessage(result.message)
                    open(player)
                    return@onClick
                }

                val task = taskMapping[event.rawSlot]
                if (task != null) {
                    val result = if (!task.isDone() && event.clickEvent().click == ClickType.RIGHT) {
                        ForgeBookTaskManager.accelerate(player, task.id)
                    } else {
                        ForgeBookTaskManager.claim(player, task.id)
                    }
                    player.sendMessage(result.message)
                    open(player)
                }
            }
        }
    }

    private fun toBookItem(player: Player, item: ItemStack, inventorySlot: Int): ItemStack {
        val info = DungeonLootManager.getForgeBookInfo(item)
        return item.clone().apply {
            itemMeta = itemMeta?.also { meta ->
                val lore = (meta.lore ?: emptyList()).toMutableList()
                lore += ""
                lore += "§7背包槽位: §f$inventorySlot"
                if (info != null) {
                    lore += "§7打造耗时: §f${ForgeBookTaskManager.formatDuration(ForgeBookTaskManager.getForgeTimeMillis(player, info.quality))}"
                    lore += "§7灵魂碎片: §6${info.quality.soulShards}"
                    lore += "§7材料: ${PermanentMaterialManager.formatCost(info.quality.materials)}"
                }
                lore += "§e点击开始打造"
                meta.lore = lore
            }
        }
    }

    private fun toTaskItem(player: Player, task: ForgeBookTaskManager.ForgeTask): ItemStack {
        val done = task.isDone()
        val material = if (done) XMaterial.SMITHING_TABLE else XMaterial.CLOCK
        return material.parseItem()!!.apply {
            itemMeta = itemMeta?.also { meta ->
                meta.setDisplayName(if (done) "§a打造完成" else "§e打造中")
                meta.lore = buildList {
                    add("")
                    add("§7品质: §f${task.qualityId}")
                    add("§7来源: §f${task.source.name}")
                    add("§7层数: §f${task.floor}")
                    if (done) {
                        add("§a点击领取装备")
                    } else {
                        add("§7剩余: §e${ForgeBookTaskManager.formatDuration(task.remainingMillis())}")
                        if (ForgeBookTaskManager.isAccelerationEnabled()) {
                            add("§7右键加速: §e-${ForgeBookTaskManager.formatDuration(ForgeBookTaskManager.getAccelerationReduceMillis(task))}")
                            add("§7消耗: §6${ForgeBookTaskManager.getAccelerationSoulShards()} §7灵魂碎片")
                            add("§7材料: ${PermanentMaterialManager.formatCost(ForgeBookTaskManager.getAccelerationMaterials())}")
                        }
                        add("§8离线期间也会继续锻造")
                    }
                }
            }
        }
    }

    private fun infoItem(count: Int, queueSize: Int, completedCount: Int, shards: Int, player: Player): ItemStack {
        return XMaterial.BLAST_FURNACE.parseItem()!!.apply {
            itemMeta = itemMeta?.also { meta ->
                meta.setDisplayName("§6打造说明")
                meta.lore = buildList {
                    add("")
                    add("§7背包中锻造书: §f$count")
                    add("§7锻造队列: §f$queueSize/${ForgeBookTaskManager.getQueueLimit(player)}")
                    add("§7已完成待领取: §a$completedCount")
                    add("§7当前灵魂碎片: §e$shards")
                    add("")
                    add("§7材料库存:")
                    addAll(PermanentMaterialManager.formatOwned(player))
                    add("")
                    add("§7左侧点击锻造书开始打造。")
                    add("§7右侧完成后点击领取。")
                    add("§7未完成任务可右键消耗材料和碎片加速。")
                    add("§8下线期间会继续计算锻造时间。")
                }
            }
        }
    }

    private fun claimAllItem(completedCount: Int): ItemStack {
        val enabled = completedCount > 0
        return (if (enabled) XMaterial.SMITHING_TABLE else XMaterial.GRAY_STAINED_GLASS_PANE).parseItem()!!.apply {
            itemMeta = itemMeta?.also { meta ->
                meta.setDisplayName(if (enabled) "§a领取全部完成打造" else "§8领取全部完成打造")
                meta.lore = listOf(
                    "",
                    "§7已完成任务: §a$completedCount",
                    if (enabled) "§e点击领取全部已完成装备" else "§8暂无可领取任务",
                    "§8背包满时装备会掉落在脚下"
                )
            }
        }
    }

    private fun closeItem(): ItemStack {
        return XMaterial.BARRIER.parseItem()!!.apply {
            itemMeta = itemMeta?.also { meta ->
                meta.setDisplayName("§c关闭")
                meta.lore = listOf("", "§7关闭打造界面")
            }
        }
    }
}
