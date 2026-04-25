package inori.roguecore.ui

import inori.roguecore.accessory.AccessoryIdentificationTaskManager
import inori.roguecore.accessory.AccessoryItemCodec
import inori.roguecore.accessory.AccessoryRegistry
import inori.roguecore.data.PermanentMaterialManager
import inori.roguecore.data.PlayerDataManager
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest

object AccessoryIdentifyUI {

    private val sourceSlots = listOf(10, 11, 12, 19, 20, 21, 28, 29, 30)
    private val taskSlots = listOf(14, 15, 16, 23, 24, 25, 32, 33, 34)
    private const val CLAIM_ALL_SLOT = 39
    private const val CLOSE_SLOT = 40
    private const val WORKSHOP_SLOT = 41

    fun open(player: Player) {
        val shards = PlayerDataManager.get(player.uniqueId).soulShards
        val inventorySlots = getSealedInventorySlots(player).take(sourceSlots.size)
        val sourceMapping = sourceSlots.zip(inventorySlots).toMap()
        val tasks = AccessoryIdentificationTaskManager.getTasks(player.uniqueId)
        val completedCount = tasks.count { it.isDone() }
        val taskMapping = taskSlots.zip(tasks).toMap()

        player.openMenu<Chest>("§d§l饰品鉴定 §7(灵魂碎片: §e$shards§7)") {
            rows(5)
            handLocked(true)

            val activeSlots = (sourceSlots + taskSlots + 4 + CLAIM_ALL_SLOT + CLOSE_SLOT + WORKSHOP_SLOT).toSet()
            val glass = XMaterial.PURPLE_STAINED_GLASS_PANE.parseItem()!!.apply {
                itemMeta = itemMeta?.also { it.setDisplayName(" ") }
            }
            for (slot in 0 until 45) {
                if (slot !in activeSlots) set(slot, glass)
            }

            set(4, infoItem(player, inventorySlots.size, tasks.size, completedCount, shards))
            set(CLAIM_ALL_SLOT, claimAllItem(completedCount))
            set(CLOSE_SLOT, closeItem())
            set(WORKSHOP_SLOT, workshopItem())

            for ((menuSlot, inventorySlot) in sourceMapping) {
                val item = player.inventory.getItem(inventorySlot) ?: continue
                set(menuSlot, toSourceItem(item, inventorySlot))
            }

            for ((menuSlot, task) in taskMapping) {
                set(menuSlot, toTaskItem(player, task))
            }

            onClick { event ->
                event.isCancelled = true
                when (event.rawSlot) {
                    CLOSE_SLOT -> {
                        player.closeInventory()
                        return@onClick
                    }
                    WORKSHOP_SLOT -> {
                        AccessoryWorkshopUI.open(player)
                        return@onClick
                    }
                    CLAIM_ALL_SLOT -> {
                        val result = AccessoryIdentificationTaskManager.claimAll(player)
                        player.sendMessage(result.message)
                        open(player)
                        return@onClick
                    }
                }

                val inventorySlot = sourceMapping[event.rawSlot]
                if (inventorySlot != null) {
                    val item = player.inventory.getItem(inventorySlot)
                    val info = AccessoryItemCodec.parseSealedAccessory(item)
                    val price = AccessoryIdentificationTaskManager.getPrice(info)
                    if (!PlayerDataManager.takeSoulShards(player.uniqueId, price)) {
                        player.sendMessage("§c灵魂碎片不足，饰品鉴定需要 §e$price §c碎片。")
                        return@onClick
                    }
                    val result = AccessoryIdentificationTaskManager.start(player, inventorySlot)
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
                        AccessoryIdentificationTaskManager.accelerate(player, task.id)
                    } else {
                        AccessoryIdentificationTaskManager.claim(player, task.id)
                    }
                    player.sendMessage(result.message)
                    open(player)
                }
            }
        }
    }

    private fun getSealedInventorySlots(player: Player): List<Int> {
        return player.inventory.contents.mapIndexedNotNull { index, item ->
            if (AccessoryItemCodec.isSealedAccessory(item)) index else null
        }
    }

    private fun toSourceItem(item: ItemStack, inventorySlot: Int): ItemStack {
        val info = AccessoryItemCodec.parseSealedAccessory(item)
        val price = AccessoryIdentificationTaskManager.getPrice(info)
        val time = info?.source?.let { AccessoryIdentificationTaskManager.formatDuration(AccessoryIdentificationTaskManager.getIdentifyTimeMillis(it)) } ?: "未知"
        return item.clone().apply {
            itemMeta = itemMeta?.also { meta ->
                val lore = (meta.lore ?: emptyList()).toMutableList()
                lore += ""
                lore += "§7背包槽位: §f$inventorySlot"
                lore += "§e鉴定价格: §6$price §e灵魂碎片"
                lore += "§e预计耗时: §f$time"
                lore += "§e点击开始饰品鉴定"
                meta.lore = lore
            }
        }
    }

    private fun toTaskItem(player: Player, task: AccessoryIdentificationTaskManager.IdentifyTask): ItemStack {
        val done = task.isDone()
        val definition = AccessoryRegistry.get(task.accessoryId)
        val material = if (done) XMaterial.ENDER_CHEST else XMaterial.CLOCK
        return material.parseItem()!!.apply {
            itemMeta = itemMeta?.also { meta ->
                meta.setDisplayName(if (done) "§a饰品鉴定完成" else "§e饰品鉴定中")
                meta.lore = buildList {
                    add("")
                    add("§7饰品: §f${definition?.name ?: task.accessoryId}")
                    add("§7槽位: §d${definition?.slot?.displayName ?: "未知"}")
                    add("§7来源: §f${task.source.name}")
                    add("§7层数: §f${task.floor}")
                    if (done) {
                        add("§a点击领取饰品")
                    } else {
                        add("§7剩余: §e${AccessoryIdentificationTaskManager.formatDuration(task.remainingMillis())}")
                        if (AccessoryIdentificationTaskManager.isAccelerationEnabled()) {
                            add("§7右键加速: §e-${AccessoryIdentificationTaskManager.formatDuration(AccessoryIdentificationTaskManager.getAccelerationReduceMillis(task))}")
                            add("§7消耗: §6${AccessoryIdentificationTaskManager.getAccelerationSoulShards()} §7灵魂碎片")
                            add("§7材料: ${PermanentMaterialManager.formatCost(AccessoryIdentificationTaskManager.getAccelerationMaterials())}")
                        }
                        add("§8离线期间也会继续鉴定")
                    }
                }
            }
        }
    }

    private fun infoItem(player: Player, count: Int, queueSize: Int, completedCount: Int, shards: Int): ItemStack {
        return XMaterial.SPYGLASS.parseItem()!!.apply {
            itemMeta = itemMeta?.also { meta ->
                meta.setDisplayName("§d饰品鉴定说明")
                meta.lore = listOf(
                    "",
                    "§7背包中密封饰品: §f$count",
                    "§7鉴定队列: §f$queueSize/${AccessoryIdentificationTaskManager.getQueueLimit()}",
                    "§7已完成待领取: §a$completedCount",
                    "§7当前灵魂碎片: §e$shards",
                    "",
                    "§7左侧点击密封饰品开始鉴定。",
                    "§7右侧完成后点击领取。",
                    "§7未完成任务可右键消耗材料和碎片加速。",
                    "§8已提交任务不会随副本结束清理。"
                )
            }
        }
    }

    private fun claimAllItem(completedCount: Int): ItemStack {
        val enabled = completedCount > 0
        return (if (enabled) XMaterial.ENDER_CHEST else XMaterial.GRAY_STAINED_GLASS_PANE).parseItem()!!.apply {
            itemMeta = itemMeta?.also { meta ->
                meta.setDisplayName(if (enabled) "§a领取全部饰品鉴定" else "§8领取全部饰品鉴定")
                meta.lore = listOf("", "§7已完成任务: §a$completedCount", if (enabled) "§e点击领取全部饰品" else "§8暂无可领取任务")
            }
        }
    }

    private fun workshopItem(): ItemStack = XMaterial.CRAFTING_TABLE.parseItem()!!.apply {
        itemMeta = itemMeta?.also { meta ->
            meta.setDisplayName("§d饰品工坊")
            meta.lore = listOf("", "§7返回饰品工坊", "§e点击打开")
        }
    }

    private fun closeItem(): ItemStack = XMaterial.BARRIER.parseItem()!!.apply {
        itemMeta = itemMeta?.also { meta ->
            meta.setDisplayName("§c关闭")
            meta.lore = listOf("", "§7关闭饰品鉴定界面")
        }
    }
}
