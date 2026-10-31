package com.earnd.app.blocking

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Reads the address bar of common browsers from the accessibility tree.
 * Only address-bar view ids are read; page content is never inspected.
 */
object BrowserUrls {
    /** Browser package -> address bar view ids (without the package prefix). */
    private val BARS: Map<String, List<String>> = mapOf(
        "com.android.chrome" to listOf("url_bar"),
        "com.chrome.beta" to listOf("url_bar"),
        "com.chrome.dev" to listOf("url_bar"),
        "org.chromium.chrome" to listOf("url_bar"),
        "com.brave.browser" to listOf("url_bar"),
        "com.microsoft.emmx" to listOf("url_bar"),
        "com.vivaldi.browser" to listOf("url_bar"),
        "com.kiwibrowser.browser" to listOf("url_bar"),
        "org.mozilla.firefox" to listOf("mozac_browser_toolbar_url_view", "url_bar_title"),
        "org.mozilla.firefox_beta" to listOf("mozac_browser_toolbar_url_view"),
        "org.mozilla.focus" to listOf("mozac_browser_toolbar_url_view", "display_url"),
        "com.sec.android.app.sbrowser" to listOf("location_bar_edit_text"),
        "com.opera.browser" to listOf("url_field"),
        "com.opera.mini.native" to listOf("url_field"),
        "com.duckduckgo.mobile.android" to listOf("omnibarTextInput"),
    )

    fun isBrowser(pkg: String): Boolean = pkg in BARS

    /** Returns the visible URL (scheme added if missing), or null if not found. */
    fun extract(root: AccessibilityNodeInfo?, pkg: String): String? {
        root ?: return null
        val ids = BARS[pkg] ?: return null
        for (id in ids) {
            val nodes = root.findAccessibilityNodeInfosByViewId("$pkg:id/$id") ?: continue
            val text = nodes.firstNotNullOfOrNull { it.text?.toString()?.trim()?.takeIf(String::isNotEmpty) }
            if (text != null) return normalize(text)
        }
        return null
    }

    /** "youtube.com/watch" -> "https://youtube.com/watch"; search terms (spaces, no dot) -> null. */
    fun normalize(text: String): String? {
        if (' ' in text || '.' !in text) return null
        return if ("://" in text) text else "https://$text"
    }

    /** Lower-cased host of a URL, without "www.". */
    fun hostOf(url: String): String =
        url.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore(':')
            .lowercase().removePrefix("www.")
}
