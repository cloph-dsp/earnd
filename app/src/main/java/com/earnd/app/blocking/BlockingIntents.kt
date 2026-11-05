package com.earnd.app.blocking

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.earnd.app.ui.MainActivity

/** Intent contract between the lock screen and the rest of the app. */
object BlockingIntents {
    /** Sent to MainActivity to open the check-in flow for a habit (e.g. photo check-in). */
    const val ACTION_CHECK_IN = "com.earnd.app.action.CHECK_IN"
    /** Sent to MainActivity to open a task. */
    const val ACTION_OPEN_TASK = "com.earnd.app.action.OPEN_TASK"
    const val EXTRA_HABIT_ID = "com.earnd.app.extra.HABIT_ID"
    const val EXTRA_TASK_ID = "com.earnd.app.extra.TASK_ID"

    // Both open the nav deep links (earnd://checkin, earnd://task) that EarndNavHost handles. A custom action
    // with an extra matched nothing, and without data a PendingIntent for another habit looked identical.
    fun checkIn(context: Context, habitId: Long): Intent = deepLink(context, "earnd://checkin?habitId=$habitId")

    fun openTask(context: Context, taskId: Long): Intent = deepLink(context, "earnd://task?id=$taskId")

    private fun deepLink(context: Context, uri: String): Intent =
        Intent(Intent.ACTION_VIEW, android.net.Uri.parse(uri)).setPackage(context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    fun home(): Intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun accessibilitySettings(): Intent =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** True when the user has turned on the Earnd accessibility service. */
    fun isServiceEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = ComponentName(context, GuardAccessibilityService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }
}
