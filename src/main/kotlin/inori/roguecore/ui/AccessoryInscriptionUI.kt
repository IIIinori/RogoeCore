package inori.roguecore.ui

import inori.roguecore.accessory.AccessoryInscriptionTaskManager
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

object AccessoryInscriptionUI {

    private val bookSlots = listOf(10, 11, 12, 19, 20, 21, 28, 29, 30)
    private val taskSlots = listOf(14, 15, 16, 23, 24, 25, 32, 33, 34)
    private const val CLAIM_ALL_SLOT = 39
    private const val CLOSE_SLOT = 40
    private const val WORKSHOP_SLOT = 41

    fun open(player: Player) {
        val shards = PlayerDataManager.get(player.uniqueId).soulShards
        val inventorySlots = getBookInventorySlots(player).take(bookSlots.size)
        val bookMapping = bookSlots.zip(inventorySlots).toMap()
        val tasks = AccessoryInscriptionTaskManager.getTasks(player.uniqueId)
        val completedCount = tasks.count { it.isDone() }
        val taskMapping = taskSlots.zip(tasks).toMap()

        player.openMenu<Chest>("§b§l饰品刻印 §7(灵魂碎片: §e$shards§7)") {
            rows(5)
            handLocked(true)

            val activeSlots = (bookSlots + taskSlots + 4 + CLAIM_ALL_SLOT + CLOSE_SLOT + WORKSHOP_SLOT).toSet()
            val glass = XMaterial.LIGHT_BLUE_STAINED_GLASS_PANE.parseItem()!!.apply {
                itemMeta = itemMeta?.also { it.setDisplayName(" ") }
            }
            for (slot in 0 until 45) {
                if (slot !in activeSlots) set(slot, glass)
            }

            set(4, infoItem(inventorySlots.size, tasks.size, completedCount, shards, player))
            set(CLAIM_ALL_SLOT, claimAllItem(completedCount))
            set(CLOSE_SLOT, closeItem())
            set(WORKSHOP_SLOT, workshopItem())

            for ((menuSlot, inventorySlot) in bookMapping) {
                val item = player.inventory.getItem(inventorySlot) ?: continue
                set(menuSlot, toBookItem(item, inventorySlot))
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
                        val result = AccessoryInscriptionTaskManager.claimAll(player)
                        player.sendMessage(result.message)
                        open(player)
                        return@onClick
                    }
                }

                val inventorySlot = bookMapping[event.rawSlot]
                if (inventorySlot != null) {
                    val item = player.inventory.getItem(inventorySlot)
                    val info = AccessoryItemCodec.parseInscriptionBook(item)
                    if (info == null) {
                        player.sendMessage("§c这不是有效的饰品刻印书。")
                        return@onClick
                    }
                    val quality = info.quality
                    if (!PermanentMaterialManager.takeCost(player, quality.materials)) {
                        player.sendMessage("§c刻印材料不足，需要 ${PermanentMaterialManager.formatCost(quality.materials)}")
                        return@onClick
                    }
                    if (!PlayerDataManager.takeSoulShards(player.uniqueId, quality.soulShards)) {
                        PermanentMaterialManager.addAll(player, quality.materials)
                        player.sendMessage("§c灵魂碎片不足，刻印需要 §e${quality.soulShards} §c碎片。")
                        return@onClick
                    }
                    val result = AccessoryInscriptionTaskManager.start(player, inventorySlot)
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
                        AccessoryInscriptionTaskManager.accelerate(player, task.id)
                    } else {
                        AccessoryInscriptionTaskManager.claim(player, task.id)
                    }
                    player.sendMessage(result.message)
                    open(player)
                }
            }
        }
    }

    private fun getBookInventorySlots(player: Player): List<Int> {
        return player.inventory.contents.mapIndexedNotNull { index, item ->
            if (AccessoryItemCodec.isInscriptionBook(item)) index else null
        }
    }

    private fun toBookItem(item: ItemStack, inventorySlot: Int): ItemStack {
        val info = AccessoryItemCodec.parseInscriptionBook(item)
        return item.clone().apply {
            itemMeta = itemMeta?.also { meta ->
                val lore = (meta.lore ?: emptyList()).toMutableList()
                lore += ""
                lore += "§7背包槽位: §f$inventorySlot"
                if (info != null) {
                    lore += "§7刻印耗时: §f${AccessoryInscriptionTaskManager.formatDuration(AccessoryInscriptionTaskManager.getInscriptionTimeMillis(info.quality))}"
                    lore += "§7灵魂碎片: §6${info.quality.soulShards}"
                    lore += "§7材料: ${PermanentMaterialManager.formatCost(info.quality.materials)}"
                }
                lore += "§e点击开始饰品刻印"
                meta.lore = lore
            }
        }
    }

    private fun toTaskItem(player: Player, task: AccessoryInscriptionTaskManager.InscriptionTask): ItemStack {
        val done = task.isDone()
        val definition = AccessoryRegistry.get(task.accessoryId)
        val quality = AccessoryRegistry.getInscriptionQuality(task.qualityId)
        val material = if (done) XMaterial.SMITHING_TABLE else XMaterial.CLOCK
        return material.parseItem()!!.apply {
            itemMeta = itemMeta?.also { meta ->
                meta.setDisplayName(if (done) "§a饰品刻印完成" else "§e饰品刻印中")
                meta.lore = buildList {
                    add("")
                    add("§7饰品: §f${definition?.name ?: task.accessoryId}")
                    add("§7槽位: §d${definition?.slot?.displayName ?: "未知"}")
                    add("§7品质: ${quality?.color ?: "§f"}${quality?.displayName ?: task.qualityId}")
                    add("§7来源: §f${task.source.name}")
                    add("§7层数: §f${task.floor}")
                    if (done) {
                        add("§a点击领取饰品")
                    } else {
                        add("§7剩余: §e${AccessoryInscriptionTaskManager.formatDuration(task.remainingMillis())}")
                        if (AccessoryInscriptionTaskManager.isAccelerationEnabled()) {
                            add("§7右键加速: §e-${AccessoryInscriptionTaskManager.formatDuration(AccessoryInscriptionTaskManager.getAccelerationReduceMillis(task))}")
                            add("§7消耗: §6${AccessoryInscriptionTaskManager.getAccelerationSoulShards()} §7灵魂碎片")
                            add("§7材料: ${PermanentMaterialManager.formatCost(AccessoryInscriptionTaskManager.getAccelerationMaterials())}")
                        }
                        add("§8离线期间也会继续刻印")
                    }
                }
            }
        }
    }

    private fun infoItem(count: Int, queueSize: Int, completedCount: Int, shards: Int, player: Player): ItemStack {
        return XMaterial.BLAST_FURNACE.parseItem()!!.apply {
            itemMeta = itemMeta?.also { meta ->
                meta.setDisplayName("§b饰品刻印说明")
                meta.lore = buildList {
                    add("")
                    add("§7背包中饰品刻印书: §f$count")
                    add("§7刻印队列: §f$queueSize/${AccessoryInscriptionTaskManager.getQueueLimit()}")
                    add("§7已完成待领取: §a$completedCount")
                    add("§7当前灵魂碎片: §e$shards")
                    add("")
                    add("§7材料库存:")
                    addAll(PermanentMaterialManager.formatOwned(player))
                    add("")
                    add("§7左侧点击饰品刻印书开始刻印。")
                    add("§7右侧完成后点击领取饰品。")
                    add("§7未完成任务可右键消耗材料和碎片加速。")
                    add("§8已提交任务不会随副本结束清理。")
                }
            }
        }
    }

    private fun claimAllItem(completedCount: Int): ItemStack {
        val enabled = completedCount > 0
        return (if (enabled) XMaterial.SMITHING_TABLE else XMaterial.GRAY_STAINED_GLASS_PANE).parseItem()!!.apply {
            itemMeta = itemMeta?.also { meta ->
                meta.setDisplayName(if (enabled) "§a领取全部饰品刻印" else "§8领取全部饰品刻印")
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
            meta.lore = listOf("", "§7关闭饰品刻印界面")
        }
    }
}
