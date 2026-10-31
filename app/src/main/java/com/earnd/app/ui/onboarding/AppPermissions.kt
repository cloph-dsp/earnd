package com.earnd.app.ui.onboarding

import android.Manifest
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat

/** One system permission or special access the app asks for, with a status check and a settings intent. */
data class PermissionItem(
    val key: String,
    val title: String,
    val why: String,
    val optional: Boolean,
    val granted: (Context) -> Boolean,
    /** Null when the permission is a runtime request handled by the caller (notifications). */
    val settingsIntent: ((Context) -> Intent)?,
)

object AppPermissions {
    const val NOTIFICATIONS = "notifications"

    fun all(): List<PermissionItem> = buildList {
        add(PermissionItem(
            "accessibility", "Blocking service",
            "Lets Earnd notice when a leisure app or site opens and show the lock screen. Nothing is recorded or sent anywhere.",
            optional = false, granted = ::accessibilityOn,
            settingsIntent = { Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS) },
        ))
        add(PermissionItem(
            "usage", "Usage access",
            "Optional backup for spotting the app in front. Earnd works without it.",
            optional = true, granted = ::usageAccessOn,
            settingsIntent = { Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS) },
        ))
        add(PermissionItem(
            NOTIFICATIONS, "Notifications",
            "Reminders and the single list of things still waiting for you.",
            optional = false, granted = ::notificationsOn, settingsIntent = null,
        ))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(PermissionItem(
            "exact", "Exact alarms",
            "So reminders ring on time instead of whenever the system gets round to it.",
            optional = false,
            granted = { c -> c.getSystemService(AlarmManager::class.java).canScheduleExactAlarms() },
            settingsIntent = { c -> Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkgUri(c)) },
        ))
        add(PermissionItem(
            "battery", "Run in the background",
            "Stops the system from putting Earnd to sleep, which would silence alarms and blocking.",
            optional = false,
            granted = { c -> c.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(c.packageName) },
            settingsIntent = { c -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri(c)) },
        ))
        if (Build.VERSION.SDK_INT >= 34) add(PermissionItem(
            "fullscreen", "Full-screen alarms",
            "Lets an alarm you chose to be loud fill the screen, even when the phone is locked.",
            optional = true,
            granted = { c -> c.getSystemService(NotificationManager::class.java).canUseFullScreenIntent() },
            settingsIntent = { c -> Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkgUri(c)) },
        ))
    }

    private fun pkgUri(c: Context) = Uri.parse("package:${c.packageName}")

    fun notificationsOn(c: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            c.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
        }

    fun accessibilityOn(c: Context): Boolean {
        val enabled = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.split(':').any { it.startsWith(c.packageName + "/") }
    }

    @Suppress("DEPRECATION")
    fun usageAccessOn(c: Context): Boolean {
        val ops = c.getSystemService(AppOpsManager::class.java)
        val mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
