package com.earnd.app.blocking

/** Packages that must never be blocked, whatever the user configured. */
interface EssentialApps {
    fun isEssential(pkg: String): Boolean
}

/** Fixed system packages that are always essential, independent of device defaults. */
object CoreEssentials {
    val packages: Set<String> = setOf(
        "com.earnd.app",
        "android",
        "com.android.systemui",
        "com.android.settings",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.android.emergency",
        "com.google.android.apps.safetyhub",
        "com.android.phone",
        "com.android.server.telecom",
        "com.android.dialer",
        "com.google.android.dialer",
    )
}
