package com.earnd.app.remote

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.earnd.app.ServiceLocator
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * adb-only entry point. The manifest guards it with `android.permission.DUMP`, which only the
 * shell and system hold, and callers must address it by explicit component:
 *
 *   am broadcast -n com.earnd.app/.remote.RemoteReceiver -a com.earnd.app.REMOTE \
 *       --es cmd tasks.list
 *
 * The JSON reply is returned through the ordered-broadcast result data.
 */
class RemoteReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val extras = intent.extras
        val cmd = extras?.getString(EXTRA_CMD)
        val args = extras?.keySet().orEmpty()
            .filter { it != EXTRA_CMD }
            .associateWith { key -> @Suppress("DEPRECATION") extras?.get(key)?.toString().orEmpty() }

        val pending = goAsync()
        scope.launch {
            val reply = withTimeoutOrNull(TIMEOUT_MS) { dispatcher(context).handle(cmd, args) }
                ?: Json.encode(Json.obj("ok" to false, "error" to "timeout"))
            pending.resultCode = 0
            pending.resultData = reply
            pending.finish()
        }
    }

    companion object {
        const val ACTION = "com.earnd.app.REMOTE"
        const val EXTRA_CMD = "cmd"
        private const val TIMEOUT_MS = 8_000L
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        @Volatile private var cached: RemoteDispatcher? = null

        private fun dispatcher(context: Context): RemoteDispatcher = cached ?: synchronized(this) {
            cached ?: with(ServiceLocator) {
                RemoteDispatcher(
                    ServiceRemoteBackend(
                        db, settings, timeBank, habitService, taskService, clock,
                        File(context.applicationContext.filesDir, "photos"),
                    ),
                )
            }.also { cached = it }
        }
    }
}
