package com.earnd.app.alarms

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/** Status checks and settings intents for the UI (exact alarms, battery, full-screen, notifications). */
object AlarmPermissions {
    private val _exactAllowed = kotlinx.coroutines.flow.MutableStateFlow(true)

    /**
     * Whether exact alarms are allowed, for a status row in the UI. Refreshed on app start,
     * when the permission state changes (via [RearmReceiver]) and whenever [refresh] is called
     * (e.g. from onResume after returning from the settings screen).
     */
    val exactAllowed: kotlinx.coroutines.flow.StateFlow<Boolean> = _exactAllowed

    fun refresh(context: Context): Boolean = canScheduleExact(context).also { _exactAllowed.value = it }

    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun exactAlarmSettingsIntent(context: Context): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkgUri(context))
        else null

    fun isIgnoringBatteryOptimizations(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    /** Direct exemption prompt; falls back to the list screen via [batteryListIntent] if unavailable. */
    @SuppressLint("BatteryLife")
    fun batteryExemptionIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri(context))

    fun batteryListIntent(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    fun canUseFullScreenIntent(context: Context): Boolean =
        Build.VERSION.SDK_INT < 34 ||
            context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    fun fullScreenSettingsIntent(context: Context): Intent? =
        if (Build.VERSION.SDK_INT >= 34) Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkgUri(context))
        else null

    fun notificationsEnabled(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()

    fun notificationSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    /** Everything the alarm feature needs, for a single "setup" card in the UI. */
    fun allGood(context: Context): Boolean = canScheduleExact(context) && notificationsEnabled(context) &&
        isIgnoringBatteryOptimizations(context) && canUseFullScreenIntent(context)

    private fun pkgUri(context: Context) = Uri.parse("package:${context.packageName}")
}
