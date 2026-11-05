package com.earnd.app.i18n

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList

/**
 * In-app language choice: system default, English or Portuguese. Uses the platform per-app language
 * (Android 13+), which also appears in the system's own per-app language setting. Older versions follow the system.
 */
object AppLanguage {
    const val SYSTEM = ""
    val choices = listOf(SYSTEM, "en", "pt")

    val supported: Boolean get() = Build.VERSION.SDK_INT >= 33

    /** "" for the system language, otherwise the language tag chosen for this app. */
    fun current(context: Context): String {
        if (!supported) return SYSTEM
        val list = context.getSystemService(LocaleManager::class.java).applicationLocales
        return if (list.isEmpty) SYSTEM else list[0].language
    }

    fun set(context: Context, tag: String) {
        if (!supported) return
        val locales = if (tag == SYSTEM) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        context.getSystemService(LocaleManager::class.java).applicationLocales = locales
    }
}
