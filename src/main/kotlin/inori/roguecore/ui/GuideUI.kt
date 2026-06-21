package inori.roguecore.ui

import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest

object GuideUI {

    fun open(player: Player) {
        player.openMenu<Chest>("§6§lRogueCore §7引导手册") {
            rows(6)
            handLocked(true)
            map(
                "####H####",
                "#.......#",
                "#abcdefg#",
                "#hij....#",
                "#.......#",
                "####C####"
            )
            val glass = glass(); set('#', glass); set('.', glass)
            set('H', pageItem(XMaterial.KNOWLEDGE_BOOK, "§6§l引导手册", listOf("§7快速了解 RogueCore 的主要系统")))
            set('a', pageItem(XMaterial.IRON_SWORD, "§a基础冒险", listOf("§7/rogue run enter [floor] 开始冒险。", "§7清理战斗房后获得本局碎片和神恩选择。", "§7通关 Boss 房后可选择路线或离开结算。", "§7/rogue run build 可查看当前构筑。")))
            set('b', pageItem(XMaterial.DIAMOND_CHESTPLATE, "§e装备系统", listOf("§7临时装备副本内直接使用，离开后消失。", "§7未鉴定装备在 §6/rogue gear identify §7中鉴定。", "§7锻造书在 §6/rogue gear craft §7中打造永久装备。", "§7永久装备可放入 §6/rogue gear storage §7仓库。")))
            set('c', pageItem(XMaterial.AMETHYST_SHARD, "§d饰品系统", listOf("§7饰品先进入背包，放入 §6/rogue accessory box §7才生效。", "§7密封饰品在 §6/rogue accessory identify §7中鉴定。", "§7饰品刻印书在 §6/rogue accessory inscribe §7中刻印。", "§7饰品槽位: 项链、戒指、印记、护符、战利品。")))
            set('d', pageItem(XMaterial.HOPPER, "§6回收工坊", listOf("§7不需要的装备、饰品和书类可在 §6/rogue gear salvage §7回收。", "§7左键分解单件，Shift+左键分解同类。", "§7一键回收只处理低价值物品。", "§7收藏永久装备不会被分解。")))
            set('e', pageItem(XMaterial.LECTERN, "§5收藏馆", listOf("§7/rogue progress collection 打开收藏馆。", "§7提交史诗以上装备点亮主题收藏。", "§7提交传说饰品点亮槽位收藏。", "§7Boss 首杀会自动点亮并给一次奖励。")))
            set('f', pageItem(XMaterial.SPAWNER, "§c怪物配置", listOf("§7MythicMobs 配置: mythicmobs/mobs/RogueCore。", "§7复制到 plugins/MythicMobs/mobs/RogueCore。", "§7复制 attributeplus/script/RogueCore 到 AP 脚本目录。", "§7重载 AP/MM 后再执行 /rogue admin check。")))
            set('g', pageItem(XMaterial.COMPASS, "§b常用命令", listOf("§7/rogue menu §f主菜单", "§7/rogue progress guide §f引导手册", "§7/rogue accessory box §f饰品匣", "§7/rogue gear salvage §f回收工坊", "§7/rogue progress collection §f收藏馆")))
            set('h', pageItem(XMaterial.NETHER_STAR, "§6成长路线", listOf("§71. 进副本刷装备、饰品和书。", "§72. 鉴定、刻印、锻造提升装备。", "§73. 回收低价值物品换材料。", "§74. 收藏高品质物品点亮长期进度。", "§75. 用更强构筑挑战高层。")))
            set('i', commandItem("§e装备相关", "/rogue gear identify", "/rogue gear craft", "/rogue gear storage", "/rogue gear forge"))
            set('j', commandItem("§d饰品相关", "/rogue accessory box", "/rogue accessory workshop", "/rogue accessory identify", "/rogue accessory inscribe"))
            set('C', closeItem())
            onClick { event -> event.isCancelled = true }
            onClick('C') { player.closeInventory() }
        }
    }

    private fun pageItem(material: XMaterial, name: String, lore: List<String>): ItemStack = material.parseItem()!!.apply { itemMeta = itemMeta?.also { it.setDisplayName(name); it.lore = listOf("") + lore } }
    private fun commandItem(name: String, vararg commands: String): ItemStack = XMaterial.PAPER.parseItem()!!.apply { itemMeta = itemMeta?.also { it.setDisplayName(name); it.lore = listOf("") + commands.map { cmd -> "§7$cmd" } } }
    private fun closeItem(): ItemStack = XMaterial.BARRIER.parseItem()!!.apply { itemMeta = itemMeta?.also { it.setDisplayName("§c关闭"); it.lore = listOf("", "§7点击关闭引导手册") } }
    private fun glass(): ItemStack = XMaterial.GRAY_STAINED_GLASS_PANE.parseItem()!!.apply { itemMeta = itemMeta?.also { it.setDisplayName(" ") } }
}
