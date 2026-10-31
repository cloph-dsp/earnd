package com.earnd.app.ui.stats

/** A foreground change for one app, as read from UsageStatsManager events. */
data class UsageEvent(val pkg: String, val at: Long, val foreground: Boolean)

data class UsageSummary(
    /** Foreground milliseconds per package, largest first. */
    val perApp: List<Pair<String, Long>>,
    /** Foreground milliseconds per hour of the day, 24 entries. */
    val perHour: LongArray,
) {
    val totalMs: Long get() = perApp.sumOf { it.second }
}

/** Pure arithmetic over usage events, so it can be tested off-device. */
object UsageMath {
    private const val HOUR = 3_600_000L

    /**
     * Sums foreground time per app and per hour between [dayStart] and [now].
     * An app still in front at [now] counts up to [now]; one already open at [dayStart] counts from it.
     * Only one app is in front at a time, so a new foreground event closes the previous one.
     */
    fun summarize(events: List<UsageEvent>, dayStart: Long, now: Long, exclude: Set<String> = emptySet()): UsageSummary {
        val perApp = HashMap<String, Long>()
        val perHour = LongArray(24)
        var openPkg: String? = null
        var openAt = dayStart

        fun close(end: Long) {
            val pkg = openPkg ?: return
            val from = openAt.coerceAtLeast(dayStart)
            val to = end.coerceAtMost(now)
            openPkg = null
            if (to <= from || pkg in exclude) return
            perApp[pkg] = (perApp[pkg] ?: 0L) + (to - from)
            var t = from
            while (t < to) {
                val hour = ((t - dayStart) / HOUR).toInt()
                val hourEnd = dayStart + (hour + 1) * HOUR
                val chunk = minOf(to, hourEnd) - t
                if (hour in 0..23) perHour[hour] += chunk
                t += chunk
            }
        }

        events.sortedBy { it.at }.forEach { e ->
            if (e.foreground) {
                if (openPkg != e.pkg) { close(e.at); openPkg = e.pkg; openAt = e.at }
            } else if (openPkg == e.pkg) {
                close(e.at)
            }
        }
        close(now)
        return UsageSummary(perApp.entries.sortedByDescending { it.value }.map { it.key to it.value }, perHour)
    }
}
