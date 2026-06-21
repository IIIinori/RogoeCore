package inori.roguecore.ui

import inori.roguecore.data.PermanentMaterialManager
import inori.roguecore.data.PlayerDataManager
import inori.roguecore.display.ContentDisplayNameResolver
import inori.roguecore.item.DungeonLootManager
import inori.roguecore.item.IdentificationTaskManager
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest

/**
 * 未鉴定装备鉴定界面。
 */
object IdentifyUI {

    private val sourceSlots = listOf(10, 11, 12, 19, 20, 21, 28, 29, 30)
    private val taskSlots = listOf(14, 15, 16, 23, 24, 25, 32, 33, 34)
    private const val CLAIM_ALL_SLOT = 39
    private const val CLOSE_SLOT = 40

    fun open(player: Player) {
        val shards = PlayerDataManager.get(player.uniqueId).soulShards
        val inventorySlots = DungeonLootManager.getUnidentifiedInventorySlots(player).take(sourceSlots.size)
        val sourceMapping = sourceSlots.zip(inventorySlots).toMap()
        val tasks = IdentificationTaskManager.getTasks(player.uniqueId)
        val completedCount = tasks.count { it.isDone() }
        val taskMapping = taskSlots.zip(tasks).toMap()

        player.openMenu<Chest>("§6§l装备鉴定 §7(灵魂碎片: §e$shards§7)") {
            rows(5)
            handLocked(true)

            val activeSlots = (sourceSlots + taskSlots + 4 + CLAIM_ALL_SLOT + CLOSE_SLOT).toSet()
            val glass = XMaterial.GRAY_STAINED_GLASS_PANE.parseItem()!!.apply {
                itemMeta = itemMeta?.also { it.setDisplayName(" ") }
            }
            for (slot in 0 until 45) {
                if (slot !in activeSlots) {
                    set(slot, glass)
                }
            }

            set(4, infoItem(player, inventorySlots.size, tasks.size, completedCount, shards))
            set(CLAIM_ALL_SLOT, claimAllItem(completedCount))
            set(CLOSE_SLOT, closeItem())

            for ((menuSlot, inventorySlot) in sourceMapping) {
                val item = player.inventory.getItem(inventorySlot) ?: continue
                set(menuSlot, toSourceItem(player, item, inventorySlot))
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
                    val result = IdentificationTaskManager.claimAll(player)
                    player.sendMessage(result.message)
                    open(player)
                    return@onClick
                }
                val inventorySlot = sourceMapping[event.rawSlot]
                if (inventorySlot != null) {
                    val item = player.inventory.getItem(inventorySlot)
                    val price = DungeonLootManager.getIdentificationPrice(player, item)
                    if (!PlayerDataManager.takeSoulShards(player.uniqueId, price)) {
                        player.sendMessage("§c灵魂碎片不足，开始鉴定需要 §e$price §c碎片。")
                        return@onClick
                    }
                    val result = IdentificationTaskManager.start(player, inventorySlot)
                    if (!result.success) {
                        PlayerDataManager.addSoulShards(player.uniqueId, price)
                    }
                    player.sendMessage(result.message)
                    open(player)
                    return@onClick
                }

                val task = taskMapping[event.rawSlot]
                if (task != null) {
                    val result = if (!task.isDone() && event.clickEvent().click == ClickType.RIGHT) {
                        IdentificationTaskManager.accelerate(player, task.id)
                    } else {
                        IdentificationTaskManager.claim(player, task.id)
                    }
                    player.sendMessage(result.message)
                    open(player)
                }
            }
        }
    }

    private fun toSourceItem(player: Player, item: ItemStack, inventorySlot: Int): ItemStack {
        val price = DungeonLootManager.getIdentificationPrice(player, item)
        val info = DungeonLootManager.getUnidentifiedLootInfo(item)
        val time = info?.source?.let { IdentificationTaskManager.formatDuration(IdentificationTaskManager.getIdentifyTimeMillis(player, it)) } ?: "未知"
        return item.clone().apply {
            itemMeta = itemMeta?.also { meta ->
                val lore = (meta.lore ?: emptyList()).toMutableList()
                lore += ""
                lore += "§7背包槽位: §f$inventorySlot"
                lore += "§e鉴定价格: §6$price §e灵魂碎片"
                lore += "§e预计耗时: §f$time"
                lore += "§e点击开始鉴定"
                meta.lore = lore
            }
        }
    }

    private fun toTaskItem(player: Player, task: IdentificationTaskManager.IdentifyTask): ItemStack {
        val done = task.isDone()
        val material = if (done) XMaterial.CHEST else XMaterial.CLOCK
        return material.parseItem()!!.apply {
            itemMeta = itemMeta?.also { meta ->
                meta.setDisplayName(if (done) "§a鉴定完成" else "§e鉴定中")
                meta.lore = buildList {
                    add("")
                    add("§7装备: §f${IdentificationTaskManager.taskDisplayName(task)}")
                    add("§7来源: §f${ContentDisplayNameResolver.lootSourceName(task.source.name) ?: "未知来源"}")
                    add("§7层数: §f${task.floor}")
                    if (done) {
                        add("§a点击领取装备")
                    } else {
                        add("§7剩余: §e${IdentificationTaskManager.formatDuration(task.remainingMillis())}")
                        if (IdentificationTaskManager.isAccelerationEnabled()) {
                            add("§7右键加速: §e-${IdentificationTaskManager.formatDuration(IdentificationTaskManager.getAccelerationReduceMillis(task))}")
                            add("§7消耗: §6${IdentificationTaskManager.getAccelerationSoulShards(player)} §7灵魂碎片")
                            add("§7材料: ${PermanentMaterialManager.formatCost(IdentificationTaskManager.getAccelerationMaterials())}")
                        }
                        add("§8离线期间也会继续倒计时")
                    }
                }
            }
        }
    }

    private fun infoItem(player: Player, count: Int, queueSize: Int, completedCount: Int, shards: Int): ItemStack {
        return XMaterial.SPYGLASS.parseItem()!!.apply {
            itemMeta = itemMeta?.also { meta ->
                meta.setDisplayName("§6鉴定说明")
                meta.lore = listOf(
                    "",
                    "§7背包中未鉴定装备: §f$count",
                    "§7鉴定队列: §f$queueSize/${IdentificationTaskManager.getQueueLimit(player)}",
                    "§7已完成待领取: §a$completedCount",
                    "§7当前灵魂碎片: §e$shards",
                    "",
                    "§7左侧点击未鉴定装备开始鉴定。",
                    "§7右侧完成后点击领取。",
                    "§7未完成任务可右键消耗材料和碎片加速。",
                    "§8下线期间会继续计算鉴定时间。"
                )
            }
        }
    }

    private fun claimAllItem(completedCount: Int): ItemStack {
        val enabled = completedCount > 0
        return (if (enabled) XMaterial.CHEST else XMaterial.GRAY_STAINED_GLASS_PANE).parseItem()!!.apply {
            itemMeta = itemMeta?.also { meta ->
                meta.setDisplayName(if (enabled) "§a领取全部完成鉴定" else "§8领取全部完成鉴定")
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
                meta.lore = listOf("", "§7关闭鉴定界面")
            }
        }
    }
}
