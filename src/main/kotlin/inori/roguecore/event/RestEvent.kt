package inori.roguecore.event

import inori.roguecore.boon.BoonInstance
import inori.roguecore.boon.PlayerBoonData
import inori.roguecore.dungeon.DungeonInstance
import inori.roguecore.ui.DungeonGuiGuard
import org.bukkit.entity.Player
import taboolib.library.xseries.XMaterial
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest

/**
 * 休息房事件 — 二选一：回复生命 或 升级已有 Boon
 */
object RestEvent {

    @Config("events.yml")
    lateinit var config: Configuration
        private set

    fun trigger(player: Player, instance: DungeonInstance) {
        val healPercent = config.getDouble("rest.heal-percent", 1.0)
        val boons = PlayerBoonData.getBoons(player)
        val upgradeable = boons.firstOrNull { it.canUpgrade }
        val optionSlots = setOf(11, 15)
        val title = "§b§l休息点"
        DungeonGuiGuard.lock(player, title) { target -> trigger(target, instance) }

        player.openMenu<Chest>(title) {
            rows(3)
            handLocked(true)

            // 背景
            val glass = XMaterial.LIGHT_BLUE_STAINED_GLASS_PANE.parseItem()!!.apply {
                itemMeta = itemMeta?.also { it.setDisplayName(" ") }
            }
            for (i in 0 until 27) {
                if (i !in optionSlots) {
                    set(i, glass)
                }
            }

            // 选项1: 回复生命
            val healItem = XMaterial.GOLDEN_APPLE.parseItem()!!.apply {
                itemMeta = itemMeta?.also { meta ->
                    meta.setDisplayName("§a回复生命")
                    val percent = (healPercent * 100).toInt()
                    meta.lore = listOf("", "§7回复 §a${percent}% §7生命值", "", "§e点击选择")
                }
            }
            set(11, healItem)

            // 选项2: 升级 Boon
            val boonItem = if (upgradeable != null) {
                (upgradeable.boon.icon.parseItem() ?: XMaterial.PAPER.parseItem()!!).apply {
                    itemMeta = itemMeta?.also { meta ->
                        meta.setDisplayName("§6升级神恩")
                        meta.lore = listOf(
                            "",
                            "§7升级 ${upgradeable.boon.rarity.color}${upgradeable.boon.name}",
                            "§7Lv.${upgradeable.level} → Lv.${upgradeable.level + 1}",
                            "",
                            "§e点击选择"
                        )
                    }
                }
            } else {
                XMaterial.BARRIER.parseItem()!!.apply {
                    itemMeta = itemMeta?.also { meta ->
                        meta.setDisplayName("§7升级神恩")
                        meta.lore = listOf("", "§c没有可升级的神恩")
                    }
                }
            }
            set(15, boonItem)

            onClick { event ->
                event.isCancelled = true
                when (event.rawSlot) {
                    11 -> {
                        // 回复生命
                        DungeonGuiGuard.unlock(player)
                        player.closeInventory()
                        val maxHp = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH)?.value ?: 20.0
                        val healAmount = maxHp * healPercent
                        player.health = (player.health + healAmount).coerceAtMost(maxHp)
                        player.sendMessage("§a生命已回复!")
                    }
                    15 -> {
                        // 升级 Boon
                        DungeonGuiGuard.unlock(player)
                        player.closeInventory()
                        if (upgradeable != null && upgradeable.canUpgrade) {
                            PlayerBoonData.addBoon(player, upgradeable.boon)
                        } else {
                            player.sendMessage("§c没有可升级的神恩!")
                        }
                    }
                }
            }
        }
    }
}
