package com.earnd.app.ui.apps

/** "https://www.Example.com/path" -> "example.com". */
fun normalizeHost(input: String): String? {
    val h = input.trim().lowercase()
        .substringAfter("://")
        .substringBefore('/')
        .substringBefore(':')
        .removePrefix("www.")
    return h.takeIf { it.contains('.') && it.all { c -> c.isLetterOrDigit() || c == '.' || c == '-' } }
}
