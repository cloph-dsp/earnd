package com.earnd.app.ui.stats

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads today's foreground events. Needs Usage Access; returns null without it. */
object UsageReport {
    suspend fun today(context: Context, dayStart: Long, now: Long): UsageSummary? = withContext(Dispatchers.IO) {
        runCatching {
            val usm = context.getSystemService(UsageStatsManager::class.java)
            val events = usm.queryEvents(dayStart, now)
            val list = ArrayList<UsageEvent>()
            val e = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                when (e.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> list += UsageEvent(e.packageName, e.timeStamp, true)
                    UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED ->
                        list += UsageEvent(e.packageName, e.timeStamp, false)
                    UsageEvents.Event.SCREEN_NON_INTERACTIVE -> list += UsageEvent(SCREEN_OFF, e.timeStamp, true)
                }
            }
            UsageMath.summarize(list, dayStart, now, exclude = setOf(SCREEN_OFF, context.packageName) + launchers(context))
        }.getOrNull()
    }

    fun label(context: Context, pkg: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    private fun launchers(context: Context): Set<String> {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return context.packageManager.queryIntentActivities(i, 0).map { it.activityInfo.packageName }.toSet()
    }

    /** Pseudo package that closes the app in front when the screen goes off. */
    private const val SCREEN_OFF = "#screen-off"
}
