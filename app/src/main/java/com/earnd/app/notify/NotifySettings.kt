package com.earnd.app.notify

import com.earnd.app.data.SettingsRepository

/** User choices for the morning nudge and the daily report (both optional, both on by default). */
data class NotifyPrefs(
    val morningEnabled: Boolean = true,
    val morningMin: Int = 8 * 60,
    val reportEnabled: Boolean = true,
    val reportMin: Int = 21 * 60 + 30,
)

/** Stored as raw settings so this feature does not need schema changes. */
object NotifySettings {
    private const val MORNING_ON = "notify.morning.enabled"
    private const val MORNING_AT = "notify.morning.min"
    private const val REPORT_ON = "notify.report.enabled"
    private const val REPORT_AT = "notify.report.min"

    suspend fun get(settings: SettingsRepository): NotifyPrefs {
        val d = NotifyPrefs()
        return NotifyPrefs(
            morningEnabled = settings.rawGet(MORNING_ON)?.toBooleanStrictOrNull() ?: d.morningEnabled,
            morningMin = settings.rawGet(MORNING_AT)?.toIntOrNull()?.takeIf { it in 0 until 1440 } ?: d.morningMin,
            reportEnabled = settings.rawGet(REPORT_ON)?.toBooleanStrictOrNull() ?: d.reportEnabled,
            reportMin = settings.rawGet(REPORT_AT)?.toIntOrNull()?.takeIf { it in 0 until 1440 } ?: d.reportMin,
        )
    }

    /** Saves [p]. Use [Notify.update] to also re-arm the alarms. */
    suspend fun put(settings: SettingsRepository, p: NotifyPrefs) {
        require(p.morningMin in 0 until 1440 && p.reportMin in 0 until 1440)
        settings.rawPut(MORNING_ON, p.morningEnabled.toString())
        settings.rawPut(MORNING_AT, p.morningMin.toString())
        settings.rawPut(REPORT_ON, p.reportEnabled.toString())
        settings.rawPut(REPORT_AT, p.reportMin.toString())
    }
}
