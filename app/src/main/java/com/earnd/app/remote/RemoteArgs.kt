package com.earnd.app.remote

import java.time.LocalDate
import java.util.Base64

/**
 * Command arguments. Keys ending in `_b64` carry base64 (UTF-8) free text and are exposed
 * under the key without the suffix, so `title_b64=SGk=` reads as `title=Hi`.
 */
class RemoteArgs(raw: Map<String, String>) {
    private val values: Map<String, String>

    init {
        if (raw.size > MAX_ARGS) bad("too many arguments (max $MAX_ARGS)")
        values = raw.entries.associate { (k, v) ->
            if (k.length > MAX_KEY_LEN || k.isEmpty() || !k.all { it.isLetterOrDigit() || it == '_' }) bad("bad key")
            if (v.length > MAX_RAW_VALUE_LEN) bad("$k is too long")
            val key = k.removeSuffix(B64)
            val value = if (k.endsWith(B64)) decode(k, v) else v
            val cap = TEXT_CAPS[key] ?: MAX_VALUE_LEN
            if (value.length > cap) bad("$key is too long (max $cap)")
            key to value
        }
    }

    val keys: Set<String> get() = values.keys
    fun has(key: String) = values.containsKey(key)
    fun str(key: String): String? = values[key]
    fun text(key: String): String? = values[key]?.trim()?.takeIf { it.isNotEmpty() }

    fun long(key: String): Long? = values[key]?.let { it.toLongOrNull() ?: bad("$key must be a number") }
    fun int(key: String): Int? = values[key]?.let { it.toIntOrNull() ?: bad("$key must be a number") }
    fun double(key: String): Double? = values[key]?.let { it.toDoubleOrNull() ?: bad("$key must be a number") }
    fun bool(key: String): Boolean? = values[key]?.lowercase()?.let {
        when (it) {
            "1", "true", "yes" -> true
            "0", "false", "no" -> false
            else -> bad("$key must be true/false")
        }
    }

    /** First present key of [keys] as an int in [range]; `none` gives a null value; null if absent. */
    fun intOrNone(keys: List<String>, range: IntRange): Field.Set<Int?>? {
        val k = keys.firstOrNull(::has) ?: return null
        val v = values.getValue(k)
        if (v.isBlank() || v.equals("none", true)) return Field.Set(null)
        return Field.Set(int(k, range))
    }

    fun requireLong(key: String): Long = long(key) ?: bad("missing $key")

    /** Like [int] but rejects values outside [range]. */
    fun int(key: String, range: IntRange): Int? =
        int(key)?.also { if (it !in range) bad("$key must be ${range.first}..${range.last}") }

    /** Like [double] but rejects non-finite values and values outside [range]. */
    fun double(key: String, range: ClosedFloatingPointRange<Double>): Double? =
        double(key)?.also { if (!it.isFinite() || it !in range) bad("$key must be ${range.start}..${range.endInclusive}") }

    /** Comma-separated ids, e.g. `3,1,2`. Duplicates are rejected. */
    fun ids(key: String): List<Long>? = str(key)?.let { v ->
        val ids = v.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            .map { it.toLongOrNull()?.takeIf { n -> n > 0 } ?: bad("bad $key: $it") }
        if (ids.isEmpty()) bad("empty $key")
        if (ids.size > MAX_IDS) bad("too many $key (max $MAX_IDS)")
        if (ids.toSet().size != ids.size) bad("duplicate $key")
        ids
    }

    inline fun <reified E : Enum<E>> enum(key: String): E? = str(key)?.let { v ->
        enumValues<E>().firstOrNull { it.name.equals(v, ignoreCase = true) } ?: bad("bad $key: $v")
    }

    /** `yyyy-MM-dd`, `today`, `tomorrow`; `none` clears. */
    fun date(key: String, today: LocalDate): Field<Long?> {
        val v = str(key) ?: return Field.Absent
        return Field.Set(
            when (v.lowercase()) {
                "", "none" -> null
                "today" -> today.toEpochDay()
                "tomorrow" -> today.plusDays(1).toEpochDay()
                else -> runCatching { LocalDate.parse(v).toEpochDay() }.getOrElse { bad("bad $key: $v") }
            },
        )
    }

    /** `HH:mm` -> minute of day; `none` clears. */
    fun time(key: String): Field<Int?> {
        val v = str(key) ?: return Field.Absent
        if (v.isEmpty() || v.equals("none", true)) return Field.Set(null)
        val p = v.split(":")
        val h = p.getOrNull(0)?.toIntOrNull()
        val m = p.getOrNull(1)?.toIntOrNull()
        if (p.size != 2 || h == null || m == null || h !in 0..23 || m !in 0..59) bad("bad $key: $v")
        return Field.Set(h * 60 + m)
    }

    /** `mon,wed,fri`, `weekdays`, `weekends` or `all` -> bit0=Mon..bit6=Sun. */
    fun weekdayMask(key: String): Int? {
        val v = str(key)?.lowercase() ?: return null
        return when (v) {
            "all", "daily" -> 0b1111111
            "weekdays" -> 0b0011111
            "weekends" -> 0b1100000
            else -> v.split(',').map { it.trim() }.filter { it.isNotEmpty() }.fold(0) { acc, d ->
                val i = DAY_PREFIXES.indexOfFirst { d.startsWith(it) }
                if (i < 0) bad("bad $key: $d")
                acc or (1 shl i)
            }.also { if (it == 0) bad("empty $key") }
        }
    }

    sealed interface Field<out T> {
        data object Absent : Field<Nothing>
        data class Set<T>(val value: T) : Field<T>
    }

    companion object {
        const val B64 = "_b64"
        const val MAX_ARGS = 24
        const val MAX_KEY_LEN = 40
        const val MAX_VALUE_LEN = 200
        const val MAX_IDS = 500
        /** Encoded size limit (base64 grows text by a third). */
        const val MAX_RAW_VALUE_LEN = 8_000
        /** Per-key decoded length caps; other keys use [MAX_VALUE_LEN]. */
        val TEXT_CAPS = mapOf(
            "title" to 200, "name" to 80, "unit" to 24, "notes" to 4_000,
            "message" to 200, "ids" to 4_000, "label" to 80,
        )
        private val DAY_PREFIXES = listOf("mo", "tu", "we", "th", "fr", "sa", "su")
        private val DAY_NAMES = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")

        private fun decode(key: String, v: String): String =
            runCatching { String(Base64.getDecoder().decode(v.trim()), Charsets.UTF_8) }
                .getOrElse { bad("$key is not valid base64") }

        fun maskToDays(mask: Int): List<String> = DAY_NAMES.filterIndexed { i, _ -> mask and (1 shl i) != 0 }
    }
}

open class BadArgs(message: String) : IllegalArgumentException(message)

fun bad(message: String): Nothing = throw BadArgs(message)
