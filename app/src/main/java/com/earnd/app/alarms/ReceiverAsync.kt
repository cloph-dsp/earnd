package com.earnd.app.alarms

import android.content.BroadcastReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/** Runs suspending work from a receiver, keeping the broadcast alive until it finishes. */
internal fun BroadcastReceiver.launchAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    receiverScope.launch {
        try { block() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Throwable) {
            // A failing receiver must not take the whole process (and the blocking service with it) down.
        } finally { pending.finish() }
    }
}
