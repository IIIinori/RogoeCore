package inori.roguecore

import inori.roguecore.data.DatabaseManager
import inori.roguecore.dungeon.DungeonManager
import taboolib.common.platform.Plugin
import taboolib.common.platform.function.info

object RogueCore : Plugin() {

    override fun onEnable() {
        info("RogueCore 已启动!")
    }

    override fun onDisable() {
        DatabaseManager.markShuttingDown()
        DungeonManager.destroyAll()
        info("RogueCore 已关闭!")
    }
}
