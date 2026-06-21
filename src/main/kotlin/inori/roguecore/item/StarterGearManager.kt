package inori.roguecore.item

import inori.roguecore.data.DatabaseManager
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.warning
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

/**
 * 新手装备发放管理。
 *
 * 规则：
 * 1. 默认仅首次进服触发；
 * 2. 每个玩家只发一次（数据库标记）；
 * 3. 装备通过 DungeonLootManager 生成并转为永久装备。
 */
object StarterGearManager {

    @Config("config.yml")
    lateinit var config: Configuration
        private set

    private var enabled = true
    private var firstJoinOnly = true
    private var markKey = "starter_gear_granted"
    private var source = DungeonLootSource.CHEST
    private var floor = 1
    private var gearExtra = "permanent_common_noaffix_fixedroll"
    private var gearIds = listOf(
        "crypt_blade",
        "crypt_crown",
        "crypt_guard",
        "crypt_greaves",
        "crypt_sabatons",
        "crypt_lantern"
    )
    private var messageTitle = "§6已发放新手装备"
    private var messageLines = listOf(
        "§7你获得了一套永久新手装备。",
        "§7可使用 §e/rogue gear storage §7管理装备。"
    )

    @Awake(LifeCycle.ENABLE)
    fun load() {
        enabled = config.getBoolean("starter-gear.enabled", true)
        firstJoinOnly = config.getBoolean("starter-gear.first-join-only", true)
        markKey = config.getString("starter-gear.mark-key")?.trim().takeUnless { it.isNullOrBlank() }
            ?: "starter_gear_granted"
        source = config.getString("starter-gear.source")
            ?.let { runCatching { DungeonLootSource.valueOf(it.uppercase()) }.getOrNull() }
            ?: DungeonLootSource.CHEST
        floor = config.getInt("starter-gear.floor", 1).coerceAtLeast(1)
        gearExtra = config.getString("starter-gear.extra")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "permanent_common_noaffix_fixedroll"
        gearIds = config.getStringList("starter-gear.items")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .ifEmpty {
                listOf(
                    "crypt_blade",
                    "crypt_crown",
                    "crypt_guard",
                    "crypt_greaves",
                    "crypt_sabatons",
                    "crypt_lantern"
                )
            }
        messageTitle = config.getString("starter-gear.messages.title") ?: "§6已发放新手装备"
        messageLines = config.getStringList("starter-gear.messages.lines").ifEmpty {
            listOf(
                "§7你获得了一套永久新手装备。",
                "§7可使用 §e/rogue gear storage §7管理装备。"
            )
        }
    }

    fun tryGrantOnJoin(player: Player) {
        if (!enabled) {
            return
        }
        if (firstJoinOnly && player.hasPlayedBefore()) {
            return
        }

        val container = DatabaseManager.getOrCreateContainer(player.uniqueId)
        if (container[markKey] == "true") {
            return
        }

        val items = mutableListOf<ItemStack>()
        for (id in gearIds) {
            val result = DungeonLootManager.buildAdminGiveItem(
                player = player,
                kind = "gear",
                lootId = id,
                source = source,
                floor = floor,
                extra = gearExtra
            )
            if (result.success && result.item != null) {
                items += result.item
            } else {
                warning("[RogueCore] 新手装备生成失败: $id -> ${result.message}")
            }
        }

        if (items.isEmpty()) {
            return
        }

        container[markKey] = "true"
        items.forEach { giveItem(player, it) }
        player.sendMessage(messageTitle)
        messageLines.forEach(player::sendMessage)
    }

    private fun giveItem(player: Player, item: ItemStack) {
        val leftovers = player.inventory.addItem(item)
        leftovers.values.forEach { player.world.dropItemNaturally(player.location, it) }
    }
}
