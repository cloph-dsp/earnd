package com.earnd.app.ui.apps

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledApp(val pkg: String, val label: String, val icon: Drawable?)

/** Launchable apps, sorted by label. Needs the LAUNCHER <queries> entry from manifest/ui.xml. */
object InstalledApps {
    suspend fun load(context: Context): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .map { InstalledApp(it.packageName, it.loadLabel(pm).toString(), runCatching { it.loadIcon(pm) }.getOrNull()) }
            .sortedBy { it.label.lowercase() }
    }
}
