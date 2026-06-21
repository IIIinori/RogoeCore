package inori.roguecore.ui

import inori.roguecore.display.ContentDisplayNameResolver
import inori.roguecore.dungeon.DungeonInstance
import inori.roguecore.dungeon.room.Room
import inori.roguecore.dungeon.room.RoomType
import inori.roguecore.event.DungeonEventAffix
import org.bukkit.Sound
import org.bukkit.entity.Player

/**
 * 副本场景提示管理器。
 *
 * 负责统一发送：
 * - Title / Subtitle
 * - 音效
 * - 配套 ActionBar
 */
object DungeonSceneCueManager {

    fun showDungeonEntry(player: Player, instance: DungeonInstance, routeName: String? = null) {
        val themeName = ContentDisplayNameResolver.safeText(instance.config.theme.name, "未知主题")
        val subtitle = buildString {
            append("§7$themeName")
            append(" §8· §f第${instance.config.floorNumber}层")
            if (!routeName.isNullOrBlank()) {
                append(" §8· §b$routeName")
            }
        }
        show(
            player = player,
            title = "§b§l深入地牢",
            subtitle = subtitle,
            sound = Sound.ITEM_GOAT_HORN_SOUND_0,
            actionBar = "§b进入第${instance.config.floorNumber}层：$themeName",
            stay = 35
        )
    }

    fun broadcastCombatStart(instance: DungeonInstance, room: Room) {
        val players = instance.getOnlinePlayers()
        for (player in players) {
            when (room.type) {
                RoomType.BOSS -> show(
                    player = player,
                    title = "§5§lBoss 讨伐开始",
                    subtitle = "§d封锁已生效 · 击溃核心敌人",
                    sound = Sound.ENTITY_ENDER_DRAGON_GROWL,
                    actionBar = "§5Boss 房已锁定，准备迎战",
                    stay = 45
                )
                RoomType.ELITE -> show(
                    player = player,
                    title = "§c§l精英交战",
                    subtitle = "§6高压目标出现 · 清剿威胁",
                    sound = Sound.ENTITY_WITHER_SPAWN,
                    actionBar = "§c精英房已激活，尽快集火目标",
                    stay = 35
                )
                else -> show(
                    player = player,
                    title = "§6§l战斗爆发",
                    subtitle = "§e封锁已生效 · 消灭所有怪物",
                    sound = Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR,
                    actionBar = "§6战斗房已封锁，清除全部怪物",
                    stay = 30
                )
            }
        }
    }

    fun broadcastRoomCleared(instance: DungeonInstance, room: Room) {
        val players = instance.getOnlinePlayers()
        for (player in players) {
            when (room.type) {
                RoomType.BOSS -> show(
                    player = player,
                    title = "§6§lBoss 击破",
                    subtitle = "§e封锁解除 · 战利品已结算",
                    sound = Sound.UI_TOAST_CHALLENGE_COMPLETE,
                    actionBar = "§6Boss 房已清空，准备进入结算阶段",
                    stay = 35
                )
                RoomType.ELITE -> show(
                    player = player,
                    title = "§a§l精英肃清",
                    subtitle = "§7高压目标已被移除",
                    sound = Sound.ENTITY_PLAYER_LEVELUP,
                    actionBar = "§a精英房已清空",
                    stay = 25
                )
                else -> show(
                    player = player,
                    title = "§a§l战斗结束",
                    subtitle = "§7房间已净空，继续推进",
                    sound = Sound.BLOCK_AMETHYST_BLOCK_BREAK,
                    actionBar = "§a${room.type.displayName}房已通关",
                    stay = 20
                )
            }
        }
    }

    fun broadcastDungeonComplete(instance: DungeonInstance) {
        for (player in instance.getOnlinePlayers()) {
            show(
                player = player,
                title = "§6§l全副本通关",
                subtitle = "§e选择下一层路线，或结算离开",
                sound = Sound.UI_TOAST_CHALLENGE_COMPLETE,
                actionBar = "§6副本已通关，等待下一步决策",
                stay = 50
            )
        }
    }

    fun broadcastEventRoom(instance: DungeonInstance, trigger: Player, roomType: RoomType) {
        val (title, subtitle, sound, actionBar) = when (roomType) {
            RoomType.SHOP -> Quadruple("§e§l发现商店", "§7补给与交易机会已开放", Sound.BLOCK_NOTE_BLOCK_CHIME, "§e商店已开放，全队可购买")
            RoomType.FORGE -> Quadruple("§6§l发现铁匠铺", "§7调整装备，重整战线", Sound.BLOCK_ANVIL_USE, "§6铁匠铺已开放，全队可锻造")
            RoomType.CHEST -> Quadruple("§6§l共享宝箱", "§7搜刮战利品，补强构筑", Sound.BLOCK_CHEST_OPEN, "§6共享宝箱已开启")
            RoomType.REST -> Quadruple("§b§l休息点", "§7短暂喘息，重整状态", Sound.BLOCK_BREWING_STAND_BREW, "§b休息点可供全队恢复")
            RoomType.EXTRACTION -> Quadruple("§3§l撤离点", "§7你可以兑现收益或继续深入", Sound.BLOCK_BEACON_POWER_SELECT, "§3撤离点已激活")
            RoomType.CONTRACT -> Quadruple("§4§l契约祭坛", "§7代价与回报同时摆上台面", Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, "§4契约祭坛正在呼唤")
            RoomType.HIDDEN -> Quadruple("§9§l隐藏秘库", "§7暗格已被撬开，搜刮战利品", Sound.BLOCK_ENDER_CHEST_OPEN, "§9隐藏宝藏房已开启")
            RoomType.TRIAL -> Quadruple("§5§l试炼之室", "§7抉择即代价，代价即奖赏", Sound.BLOCK_BELL_RESONATE, "§5试炼开始，全队准备抉择")
            RoomType.GAMBLE -> Quadruple("§2§l赌局桌", "§7命运翻牌，祸福同席", Sound.ENTITY_EXPERIENCE_ORB_PICKUP, "§2赌局桌已开启")
            RoomType.SHRINE -> Quadruple("§f§l古老神龛", "§7献上代价，换取神谕", Sound.BLOCK_ENCHANTMENT_TABLE_USE, "§f神龛已苏醒")
            else -> return
        }

        for (player in instance.getOnlinePlayers()) {
            val who = if (player.uniqueId == trigger.uniqueId) "§f你" else "§f${trigger.name}"
            show(
                player = player,
                title = title,
                subtitle = "$who §7触发了该房间事件",
                sound = sound,
                actionBar = actionBar,
                stay = 30
            )
        }
    }

    fun showEventAffixHint(player: Player, affixes: List<DungeonEventAffix>) {
        if (affixes.isEmpty()) {
            return
        }
        val names = affixes.take(2).joinToString(" / ") { it.name }
        val extra = affixes.size - 2
        val suffix = if (extra > 0) " §8+${extra}" else ""
        show(
            player = player,
            title = "§d§l事件词缀生效",
            subtitle = "$names$suffix",
            sound = Sound.BLOCK_BEACON_AMBIENT,
            actionBar = "§d本房受到事件词缀影响",
            stay = 25
        )
    }

    private fun show(
        player: Player,
        title: String,
        subtitle: String,
        sound: Sound,
        actionBar: String,
        stay: Int = 30
    ) {
        player.sendTitle(title, subtitle, 5, stay, 10)
        player.playSound(player.location, sound, 0.8f, 1.0f)
        DungeonHudManager.pushActionBar(player, actionBar, 40L)
    }

    private data class Quadruple(
        val title: String,
        val subtitle: String,
        val sound: Sound,
        val actionBar: String
    )
}
