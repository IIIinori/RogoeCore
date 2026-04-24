package inori.roguecore.dungeon.floor

import taboolib.common.platform.function.warning
import taboolib.library.xseries.XMaterial

/**
 * 楼层主题 — 定义地牢每层的方块材质
 */
data class FloorTheme(
    val name: String,
    val floor: XMaterial,
    val wall: XMaterial,
    val ceiling: XMaterial,
    val accent: XMaterial,
    val light: XMaterial
) {
    companion object {
        /** 默认主题，配置加载失败时使用 */
        val DEFAULT = FloorTheme(
            "默认",
            XMaterial.STONE_BRICKS,
            XMaterial.STONE_BRICKS,
            XMaterial.STONE_BRICKS,
            XMaterial.MOSSY_STONE_BRICKS,
            XMaterial.SEA_LANTERN
        )

        fun parseMaterial(name: String, fallback: XMaterial): XMaterial {
            return XMaterial.matchXMaterial(name).orElseGet {
                warning("[RogueCore] 未知材质: $name, 使用默认: $fallback")
                fallback
            }
        }
    }
}
