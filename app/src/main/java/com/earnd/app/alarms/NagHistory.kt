package com.earnd.app.alarms

import com.earnd.app.ServiceLocator
import com.earnd.app.domain.localDate

/**
 * Local-only signal for the response rate to reminders: each day stores
 * `epochDay:prompts:responses`, keeping at most seven days. Parsing is defensive;
 * malformed lines are dropped and never throw. Nothing here touches balance or blocking.
 */
object NagHistory {
    private const val KEY = "nag.history"
    private const val MAX_DAYS = 7

    private data class Day(val prompts: Int, val responses: Int)

    suspend fun recordPrompt(now: Long, n: Int = 1) {
        val day = ServiceLocator.clock.localDate(now).toEpochDay()
        ServiceLocator.settings.rawPut(KEY, recordPrompt(ServiceLocator.settings.rawGet(KEY), day, n))
    }

    suspend fun recordResponse(now: Long) {
        val day = ServiceLocator.clock.localDate(now).toEpochDay()
        ServiceLocator.settings.rawPut(KEY, recordResponse(ServiceLocator.settings.rawGet(KEY), day))
    }

    suspend fun rateFor(days: List<Long>): Map<Long, Int> = rateFor(ServiceLocator.settings.rawGet(KEY), days)

    fun recordPrompt(history: String?, day: Long, n: Int = 1): String {
        val parsed = parse(history)
        if (n > 0) {
            val old = parsed[day] ?: Day(0, 0)
            parsed[day] = Day(old.prompts + n, old.responses)
        }
        return write(parsed)
    }

    /** Counts an explicit answer only; responses are capped at prompts so a rate stays in 0..100. */
    fun recordResponse(history: String?, day: Long): String {
        val parsed = parse(history)
        val old = parsed[day] ?: return write(parsed)
        parsed[day] = old.copy(responses = minOf(old.prompts, old.responses + 1))
        return write(parsed)
    }

    /** Whole percentage per requested day; a day with no prompts has no rate, so it reads 0. */
    fun rateFor(history: String?, days: List<Long>): Map<Long, Int> {
        val parsed = parse(history)
        return days.associateWith { day ->
            val d = parsed[day]
            if (d == null || d.prompts <= 0) 0 else ((d.responses * 100) / d.prompts).coerceIn(0, 100)
        }
    }

    private fun parse(history: String?): MutableMap<Long, Day> {
        val out = LinkedHashMap<Long, Day>()
        if (history.isNullOrBlank()) return out
        for (line in history.lineSequence()) {
            val parts = line.trim().split(':')
            if (parts.size != 3) continue
            val day = parts[0].toLongOrNull() ?: continue
            val prompts = parts[1].toIntOrNull() ?: continue
            val responses = parts[2].toIntOrNull() ?: continue
            if (prompts < 0 || responses < 0 || responses > prompts) continue
            out[day] = Day(prompts, responses)
        }
        return out
    }

    private fun write(days: Map<Long, Day>): String = days.toList()
        .sortedByDescending { it.first }
        .take(MAX_DAYS)
        .sortedBy { it.first }
        .joinToString("\n") { (day, d) -> "$day:${d.prompts}:${d.responses}" }
}
