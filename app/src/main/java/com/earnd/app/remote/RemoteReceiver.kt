package com.earnd.app.remote

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
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
 * On Android 14+ the sender UID is also checked when the platform reports it (see [RemoteCaller]).
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

        if (cmd != null && cmd.length > MAX_CMD_LEN) return
        val callerUid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) sentFromUid else null
        val pending = goAsync()
        scope.launch {
            val reply = withTimeoutOrNull(TIMEOUT_MS) { dispatcher(context).handle(cmd, args, callerUid) }
                ?: Json.encode(Json.obj("ok" to false, "error" to "timeout"))
            pending.resultCode = 0
            pending.resultData = reply
            pending.finish()
        }
    }

    companion object {
        const val ACTION = "com.earnd.app.REMOTE"
        const val EXTRA_CMD = "cmd"
        private const val MAX_CMD_LEN = 64
        private const val TIMEOUT_MS = 8_000L
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        @Volatile private var cached: RemoteDispatcher? = null

        private fun dispatcher(context: Context): RemoteDispatcher = cached ?: synchronized(this) {
            cached ?: with(ServiceLocator) {
                RemoteDispatcher(
                    ServiceRemoteBackend(
                        db, settings, timeBank, habitService, taskService, clock,
                        File(context.applicationContext.filesDir, "photos"),
                        context.applicationContext,
                    ),
                )
            }.also { cached = it }
        }
    }
}
