package inori.roguecore.dependency

import inori.roguecore.accessory.AccessoryRegistry
import inori.roguecore.boon.BoonRegistry
import inori.roguecore.combat.MonsterConfig
import inori.roguecore.combat.MythicMobBridge
import inori.roguecore.item.DungeonLootManager
import inori.roguecore.talent.TalentEffectType
import inori.roguecore.talent.TalentRegistry
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.serverct.ersha.api.AttributeAPI
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.info
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.warning
import java.util.concurrent.ConcurrentHashMap

/**
 * 外部依赖自检。
 *
 * 覆盖 MythicMobs 怪物 ID 与 AttributePlus 属性名，避免运行时才发现配置缺失。
 */
object DependencySelfCheckManager {

    data class CheckResult(
        val mythicAvailable: Boolean,
        val missingMobIds: Set<String>,
        val attributePlusAvailable: Boolean,
        val missingAttributeNames: Set<String>
    )

    @Volatile
    private var lastResult: CheckResult? = null

    private val warnedUnavailableContexts = ConcurrentHashMap.newKeySet<String>()

    @Awake(LifeCycle.ENABLE)
    fun scheduleInitialCheck() {
        submit(delay = 80L) {
            runCheck()
        }
    }

    fun getLastResult(): CheckResult? {
        return lastResult
    }

    fun isAttributePlusAvailable(): Boolean {
        if (!Bukkit.getPluginManager().isPluginEnabled("AttributePlus")) {
            return false
        }
        return runCatching { AttributeAPI.allServerKey(); true }.getOrDefault(false)
    }

    fun warnAttributePlusUnavailable(context: String) {
        if (warnedUnavailableContexts.add(context)) {
            warning("[RogueCore] AttributePlus 不可用，已跳过 $context 的属性应用。")
        }
    }

    fun runCheck(reportTo: CommandSender? = null): CheckResult {
        warnedUnavailableContexts.clear()
        val mythicResult = checkMythicMobs()
        val attributeResult = checkAttributePlus()
        val result = CheckResult(
            mythicAvailable = mythicResult.available,
            missingMobIds = mythicResult.missingIds,
            attributePlusAvailable = attributeResult.available,
            missingAttributeNames = attributeResult.missingNames
        )
        lastResult = result
        reportTo?.sendMessage("§aRogueCore 外部依赖自检完成，详情见控制台。")
        return result
    }

    private fun checkMythicMobs(): MythicCheck {
        val configuredIds = MonsterConfig.getConfiguredMobIdsForSelfCheck()
        if (!MythicMobBridge.isAvailable()) {
            warning("[RogueCore] MythicMobs 未加载或 UM 桥接不可用，战斗房将无法刷怪。配置怪物 ID 数: ${configuredIds.size}")
            return MythicCheck(available = false, missingIds = configuredIds)
        }

        val missing = configuredIds.filterNot { MythicMobBridge.mobExists(it) }.toSortedSet()
        if (missing.isEmpty()) {
            info("[RogueCore] MythicMobs 自检通过: ${configuredIds.size} 个怪物 ID 可用")
        } else {
            warning("[RogueCore] MythicMobs 缺失 ${missing.size}/${configuredIds.size} 个怪物 ID: ${missing.joinToString(", ")}")
        }
        return MythicCheck(available = true, missingIds = missing)
    }

    private fun checkAttributePlus(): AttributeCheck {
        val configuredNames = collectConfiguredAttributeNames()
        if (!Bukkit.getPluginManager().isPluginEnabled("AttributePlus")) {
            warning("[RogueCore] AttributePlus 未加载，神恩/天赋/AP 装备属性将不会生效。配置属性名数: ${configuredNames.size}")
            return AttributeCheck(available = false, missingNames = configuredNames)
        }

        RogueAttributePlusRegistrar.registerConfiguredAttributes(configuredNames)

        val knownKeys = runCatching {
            (AttributeAPI.allServerKey() + AttributeAPI.allDefaultKey())
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()
        }.getOrElse {
            warning("[RogueCore] 读取 AttributePlus 属性列表失败: ${it.message}")
            emptySet()
        }

        val missing = configuredNames.filterNot { attributeExists(it, knownKeys) }.toSortedSet()
        if (missing.isEmpty()) {
            info("[RogueCore] AttributePlus 自检通过: ${configuredNames.size} 个属性名可用")
        } else {
            warning("[RogueCore] AttributePlus 缺失 ${missing.size}/${configuredNames.size} 个属性名: ${missing.joinToString(", ")}")
        }
        return AttributeCheck(available = true, missingNames = missing)
    }

    private fun collectConfiguredAttributeNames(): Set<String> {
        val names = linkedSetOf<String>()
        for (boon in BoonRegistry.getAll()) {
            names += boon.attributes.keys
        }
        for (talent in TalentRegistry.getAll()) {
            if (talent.effectType == TalentEffectType.ATTRIBUTE && talent.attribute.isNotBlank()) {
                names += talent.attribute
            }
        }
        names += DungeonLootManager.getConfiguredAttributeNames()
        names += AccessoryRegistry.getConfiguredAttributeNames()
        return names.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    private fun attributeExists(name: String, knownKeys: Set<String>): Boolean {
        if (name in knownKeys) {
            return true
        }
        return runCatching { AttributeAPI.getAttribute(name) != null }.getOrDefault(false)
    }

    private data class MythicCheck(
        val available: Boolean,
        val missingIds: Set<String>
    )

    private data class AttributeCheck(
        val available: Boolean,
        val missingNames: Set<String>
    )
}
