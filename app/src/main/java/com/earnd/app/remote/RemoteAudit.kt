package com.earnd.app.remote

/** Audit log stored as newline-separated JSON entries, newest last, capped at [MAX]. */
object RemoteAudit {
    const val MAX = 200
    const val KEY = "remote.audit"

    fun append(existing: String?, entryJson: String, max: Int = MAX): String =
        (parse(existing) + entryJson).takeLast(max).joinToString("\n")

    fun parse(stored: String?): List<String> =
        stored?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()

    fun entry(at: Long, cmd: String, ok: Boolean, id: Long?, error: String?): String =
        Json.encode(Json.obj("at" to at, "cmd" to cmd, "ok" to ok, "id" to id, "error" to error))
}
