package com.earnd.app.backup

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import android.os.Environment
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.earnd.app.ServiceLocator
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Weekly copy of the manual export into public Downloads, newest [KEEP] kept. Silent: nothing is
 * shown to the user, so a failure is only logged (and retried by next week's run).
 */
private const val WORK_NAME = "weekly_backup"
private const val PREFIX = "earnd-backup-"
private const val KEEP = 4

object WeeklyBackup {
    fun ensure(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<BackupWorker>(7, TimeUnit.DAYS).build(),
        )
    }

    /** Writes one dated backup, then prunes our own older ones. */
    suspend fun export(context: Context, store: BackupStore, name: String) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "application/json")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1) // hidden until the whole file is written
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("cannot create $name")
        runCatching { store.export(resolver.openOutputStream(uri) ?: throw IOException("cannot open $name")) }
            .onSuccess { resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null) }
            .onFailure {
                resolver.delete(uri, null, null) // no half-written backup left in Downloads
                throw it
            }
        prune(resolver, context.packageName)
    }

    /** Removes the oldest of our backups beyond [KEEP]; only rows this app owns. */
    private fun prune(resolver: ContentResolver, owner: String) {
        val selection = "${MediaStore.Downloads.DISPLAY_NAME} LIKE ? AND ${MediaStore.Downloads.OWNER_PACKAGE_NAME} = ?"
        val args = arrayOf("$PREFIX%", owner)
        val ids = resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Downloads._ID), selection, args,
            "${MediaStore.Downloads.DATE_ADDED} DESC, ${MediaStore.Downloads._ID} DESC",
        )?.use { c -> buildList<Long> { while (c.moveToNext()) add(c.getLong(0)) } } ?: return
        ids.drop(KEEP).forEach { id ->
            resolver.delete(MediaStore.Downloads.EXTERNAL_CONTENT_URI, "${MediaStore.Downloads._ID} = ?", arrayOf("$id"))
        }
    }
}

class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val name = "$PREFIX${LocalDate.now()}.json"
        // Next week's run is the retry; failing silently beats crashing or nagging.
        runCatching { WeeklyBackup.export(applicationContext, ServiceLocator.backup, name) }
            .onFailure { Log.w("WeeklyBackup", "backup to Downloads failed", it) }
        return Result.success()
    }
}
