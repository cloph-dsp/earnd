package com.earnd.app.backup

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.sqlite.db.SupportSQLiteDatabase
import com.earnd.app.R
import com.earnd.app.ServiceLocator
import com.earnd.app.data.EarndDatabase
import com.earnd.app.domain.ChangeKind
import com.earnd.app.i18n.Str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads and writes whole-database backups through Storage Access Framework URIs. */
class BackupStore(private val db: EarndDatabase) {
    private val sql: SupportSQLiteDatabase get() = db.openHelper.writableDatabase

    /** User tables and their columns, excluding Room/SQLite bookkeeping. */
    fun columns(): Map<String, Set<String>> {
        val tables = sql.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' " +
                "AND name NOT IN ('android_metadata', 'room_master_table')",
        ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
        return tables.associateWith { t ->
            sql.query("PRAGMA table_info(`$t`)").use { c ->
                val idx = c.getColumnIndexOrThrow("name")
                buildSet { while (c.moveToNext()) add(c.getString(idx)) }
            }
        }
    }

    suspend fun snapshot(): BackupData = withContext(Dispatchers.IO) {
        val tables = columns().keys.sorted().associateWith { t ->
            sql.query("SELECT * FROM `$t`").use { c -> buildList { while (c.moveToNext()) add(row(c)) } }
        }
        BackupData(db.openHelper.readableDatabase.version, System.currentTimeMillis(), tables)
    }

    suspend fun export(context: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        val data = snapshot()
        context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(BackupCodec.encode(data)) }
            ?: throw java.io.IOException(Str.get(R.string.backup_err_open))
        data.rowCount()
    }

    /** Reads and validates a backup without changing anything. */
    suspend fun read(context: Context, uri: Uri): BackupData = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: throw java.io.IOException(Str.get(R.string.backup_err_open))
        val data = BackupCodec.decode(text)
        BackupCodec.validate(data, sql.version, columns())?.let { throw BackupFormatException(it) }
        data
    }

    /** Replaces everything with [data] in one transaction. */
    suspend fun restore(data: BackupData) = withContext(Dispatchers.IO) {
        val known = columns()
        BackupCodec.validate(data, sql.version, known)?.let { throw BackupFormatException(it) }
        db.runInTransaction {
            known.keys.forEach { sql.delete(it, null, null) }
            data.tables.forEach { (t, rows) ->
                rows.forEach { r -> sql.insert(t, SQLiteDatabase.CONFLICT_REPLACE, values(r)) }
            }
        }
        announce()
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        db.clearAllTables()
        announce()
    }

    private suspend fun announce() {
        ChangeKind.entries.forEach { ServiceLocator.changeBus.emit(it) }
        runCatching { com.earnd.app.alarms.AlarmsModule.planner.rearmAll() }
    }

    private fun row(c: Cursor): Map<String, Any?> = LinkedHashMap<String, Any?>().apply {
        for (i in 0 until c.columnCount) {
            put(c.getColumnName(i), when (c.getType(i)) {
                Cursor.FIELD_TYPE_NULL -> null
                Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
                Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
                Cursor.FIELD_TYPE_BLOB -> null
                else -> c.getString(i)
            })
        }
    }

    private fun values(r: Map<String, Any?>) = ContentValues().apply {
        r.forEach { (k, v) ->
            when (v) {
                null -> putNull(k)
                is Long -> put(k, v)
                is Double -> put(k, v)
                else -> put(k, v.toString())
            }
        }
    }
}
