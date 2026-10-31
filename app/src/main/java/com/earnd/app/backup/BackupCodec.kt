package com.earnd.app.backup

import com.earnd.app.remote.Json

/**
 * Whole-database backup: every table as a list of rows (column name to value). Settings live
 * in the `setting` table, so they travel with the data. Values are null, Long, Double or String.
 */
data class BackupData(
    val schemaVersion: Int,
    val exportedAt: Long,
    val tables: Map<String, List<Map<String, Any?>>>,
) {
    fun rowCount(): Int = tables.values.sumOf { it.size }
}

class BackupFormatException(message: String) : Exception(message)

object BackupCodec {
    const val FORMAT = "earnd-backup"
    const val FORMAT_VERSION = 1

    fun encode(data: BackupData): String = Json.encode(
        Json.obj(
            "format" to FORMAT,
            "formatVersion" to FORMAT_VERSION,
            "schemaVersion" to data.schemaVersion,
            "exportedAt" to data.exportedAt,
            "tables" to data.tables.mapValues { (_, rows) -> rows.map { LinkedHashMap(it) } },
        ),
    )

    /** Parses and checks the envelope; throws [BackupFormatException] on anything unexpected. */
    fun decode(text: String): BackupData {
        val root = try { JsonParser(text).parseDocument() } catch (e: IllegalArgumentException) {
            throw BackupFormatException("Not a valid JSON file: ${e.message}")
        }
        if (root !is Map<*, *>) throw BackupFormatException("Not an Earnd backup")
        if (root["format"] != FORMAT) throw BackupFormatException("Not an Earnd backup")
        val fv = (root["formatVersion"] as? Long)?.toInt() ?: throw BackupFormatException("Missing format version")
        if (fv > FORMAT_VERSION) throw BackupFormatException("Backup is from a newer version of Earnd")
        val schema = (root["schemaVersion"] as? Long)?.toInt() ?: throw BackupFormatException("Missing schema version")
        val at = root["exportedAt"] as? Long ?: 0L
        val tablesRaw = root["tables"] as? Map<*, *> ?: throw BackupFormatException("Missing tables")
        val tables = tablesRaw.entries.associate { (k, v) ->
            val rows = (v as? List<*>) ?: throw BackupFormatException("Table $k is not a list")
            k.toString() to rows.map { r ->
                val m = r as? Map<*, *> ?: throw BackupFormatException("Row in $k is not an object")
                m.entries.associate { (c, value) ->
                    if (value is Map<*, *> || value is List<*> || value is Boolean) {
                        throw BackupFormatException("Unsupported value in $k.$c")
                    }
                    c.toString() to value
                }
            }
        }
        return BackupData(schema, at, tables)
    }

    /**
     * Checks a decoded backup against the live schema ([columns]: table to its column names).
     * Returns a user-facing problem, or null when it can be imported.
     */
    fun validate(data: BackupData, currentSchema: Int, columns: Map<String, Set<String>>): String? {
        if (data.schemaVersion > currentSchema) return "Backup is from a newer version of Earnd"
        if (data.schemaVersion < 1) return "Unknown backup schema"
        for ((table, rows) in data.tables) {
            val known = columns[table] ?: return "Unknown table \"$table\""
            rows.forEach { row ->
                val extra = row.keys - known
                if (extra.isNotEmpty()) return "Unknown column \"${extra.first()}\" in $table"
            }
        }
        return null
    }
}

/** Minimal JSON reader producing Map/List/String/Long/Double/Boolean/null. */
internal class JsonParser(private val s: String) {
    private var i = 0

    fun parseDocument(): Any? {
        val v = value()
        ws()
        require(i == s.length) { "trailing content at $i" }
        return v
    }

    private fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }

    private fun value(): Any? {
        ws()
        require(i < s.length) { "unexpected end" }
        return when (val c = s[i]) {
            '{' -> obj()
            '[' -> arr()
            '"' -> str()
            't' -> lit("true", true)
            'f' -> lit("false", false)
            'n' -> lit("null", null)
            else -> if (c == '-' || c.isDigit()) num() else throw IllegalArgumentException("unexpected '$c' at $i")
        }
    }

    private fun lit(word: String, v: Any?): Any? {
        require(s.startsWith(word, i)) { "bad literal at $i" }
        i += word.length
        return v
    }

    private fun obj(): Map<String, Any?> {
        i++
        val m = LinkedHashMap<String, Any?>()
        ws()
        if (peek() == '}') { i++; return m }
        while (true) {
            ws()
            require(peek() == '"') { "expected key at $i" }
            val k = str()
            ws()
            require(peek() == ':') { "expected ':' at $i" }
            i++
            m[k] = value()
            ws()
            when (peek()) {
                ',' -> i++
                '}' -> { i++; return m }
                else -> throw IllegalArgumentException("expected ',' or '}' at $i")
            }
        }
    }

    private fun arr(): List<Any?> {
        i++
        val l = ArrayList<Any?>()
        ws()
        if (peek() == ']') { i++; return l }
        while (true) {
            l.add(value())
            ws()
            when (peek()) {
                ',' -> i++
                ']' -> { i++; return l }
                else -> throw IllegalArgumentException("expected ',' or ']' at $i")
            }
        }
    }

    private fun peek(): Char = if (i < s.length) s[i] else throw IllegalArgumentException("unexpected end")

    private fun str(): String {
        i++
        val sb = StringBuilder()
        while (true) {
            val c = peek()
            i++
            when (c) {
                '"' -> return sb.toString()
                '\\' -> {
                    when (val e = peek()) {
                        '"', '\\', '/' -> sb.append(e)
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            require(i + 4 < s.length) { "bad escape" }
                            sb.append(s.substring(i + 1, i + 5).toInt(16).toChar())
                            i += 4
                        }
                        else -> throw IllegalArgumentException("bad escape at $i")
                    }
                    i++
                }
                else -> sb.append(c)
            }
        }
    }

    private fun num(): Any {
        val start = i
        if (peek() == '-') i++
        while (i < s.length && (s[i].isDigit() || s[i] in ".eE+-")) i++
        val t = s.substring(start, i)
        return t.toLongOrNull() ?: t.toDoubleOrNull() ?: throw IllegalArgumentException("bad number '$t'")
    }
}
