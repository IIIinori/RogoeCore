package inori.roguecore.stats

import org.bukkit.command.CommandSender
import java.util.concurrent.ConcurrentHashMap

/**
 * 轻量性能采样器。
 *
 * 用于记录关键逻辑耗时（调用次数、平均耗时、最大耗时、总耗时）。
 */
object PerfMonitor {

    private data class Stat(
        var count: Long = 0L,
        var totalNanos: Long = 0L,
        var maxNanos: Long = 0L
    )

    data class Snapshot(
        val key: String,
        val count: Long,
        val totalNanos: Long,
        val maxNanos: Long
    ) {
        val totalMillis: Double get() = totalNanos / 1_000_000.0
        val avgMillis: Double get() = if (count <= 0L) 0.0 else totalNanos.toDouble() / count.toDouble() / 1_000_000.0
        val maxMillis: Double get() = maxNanos / 1_000_000.0
    }

    private val stats = ConcurrentHashMap<String, Stat>()

    inline fun <T> measure(key: String, block: () -> T): T {
        val start = System.nanoTime()
        try {
            return block()
        } finally {
            record(key, System.nanoTime() - start)
        }
    }

    fun record(key: String, elapsedNanos: Long) {
        if (key.isBlank() || elapsedNanos <= 0L) {
            return
        }
        val stat = stats.computeIfAbsent(key) { Stat() }
        synchronized(stat) {
            stat.count += 1L
            stat.totalNanos += elapsedNanos
            if (elapsedNanos > stat.maxNanos) {
                stat.maxNanos = elapsedNanos
            }
        }
    }

    fun reset() {
        stats.clear()
    }

    fun snapshots(): List<Snapshot> {
        return stats.entries.map { (key, stat) ->
            synchronized(stat) {
                Snapshot(
                    key = key,
                    count = stat.count,
                    totalNanos = stat.totalNanos,
                    maxNanos = stat.maxNanos
                )
            }
        }.sortedWith(
            compareByDescending<Snapshot> { it.totalNanos }
                .thenByDescending { it.maxNanos }
                .thenBy { it.key }
        )
    }

    fun formatSummary(limit: Int = 12): List<String> {
        val rows = snapshots()
        if (rows.isEmpty()) {
            return listOf(
                "§6===== RogueCore 性能采样 =====",
                "§7暂无采样数据。",
                "§7提示: §e/rogue admin perf reset §7可重置后再观察。"
            )
        }
        return buildList {
            add("§6===== RogueCore 性能采样 =====")
            add("§7按总耗时排序，单位: 毫秒(ms)")
            rows.take(limit).forEachIndexed { index, row ->
                add(
                    "§e${index + 1}. §f${row.key} §7| 次数 §f${row.count}" +
                        " §7| 平均 §f${fmt(row.avgMillis)}" +
                        " §7| 最大 §f${fmt(row.maxMillis)}" +
                        " §7| 总计 §f${fmt(row.totalMillis)}"
                )
            }
            if (rows.size > limit) {
                add("§8... 还有 ${rows.size - limit} 项未显示")
            }
            add("§7重置采样: §e/rogue admin perf reset")
        }
    }

    fun sendSummary(sender: CommandSender, limit: Int = 12) {
        formatSummary(limit).forEach(sender::sendMessage)
    }

    private fun fmt(value: Double): String = String.format("%.3f", value)
}
