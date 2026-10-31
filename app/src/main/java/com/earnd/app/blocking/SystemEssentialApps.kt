package com.earnd.app.blocking

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Telephony
import android.telecom.TelecomManager

/**
 * Resolves the device's current default dialer, SMS app, home launcher and maps handler, and
 * combines them with [CoreEssentials]. Call [refresh] when packages or role holders change.
 */
class SystemEssentialApps(private val context: Context) : EssentialApps {
    @Volatile private var cached: Set<String> = emptySet()

    init { refresh() }

    override fun isEssential(pkg: String): Boolean = pkg in CoreEssentials.packages || pkg in cached

    fun all(): Set<String> = CoreEssentials.packages + cached

    fun refresh() {
        val pm = context.packageManager
        val found = mutableSetOf(context.packageName)
        runCatching { context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage }
            .getOrNull()?.let(found::add)
        runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull()?.let(found::add)
        resolve(pm, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))?.let(found::add)
        resolve(pm, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=")))?.let(found::add)
        resolve(pm, Intent(Intent.ACTION_DIAL))?.let(found::add)
        found.remove("android") // resolver placeholder when no default is set; already in core
        cached = found
    }

    private fun resolve(pm: PackageManager, intent: Intent): String? =
        runCatching { pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName }
            .getOrNull()
}
