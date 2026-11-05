package com.earnd.app.i18n

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

/**
 * Localized text for code that has no Compose or Context at hand (domain summaries, notifications, services).
 * Composables should call stringResource() instead. The application context follows the system or per-app
 * language, so a change of language is picked up on the next call.
 */
object Str {
    private var appContext: Context? = null

    /** Test hook: lets JVM tests resolve ids without Android (see TestStr in src/test). */
    @Volatile var testResolver: ((Int, Array<out Any>) -> String)? = null
    @Volatile var testPluralResolver: ((Int, Int, Array<out Any>) -> String)? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun get(@StringRes id: Int, vararg args: Any): String {
        testResolver?.let { return it(id, args) }
        val c = requireNotNull(appContext) { "Str.init was not called" }
        return if (args.isEmpty()) c.getString(id) else c.getString(id, *args)
    }

    /** Quantity string; [n] picks the plural form and is passed as the first format argument. */
    fun plural(@PluralsRes id: Int, n: Int, vararg args: Any): String {
        testPluralResolver?.let { return it(id, n, args) }
        val c = requireNotNull(appContext) { "Str.init was not called" }
        return c.resources.getQuantityString(id, n, n, *args)
    }
}
