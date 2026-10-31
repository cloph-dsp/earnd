package com.earnd.app.focus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.earnd.app.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** End-of-focus alarm, plus re-arming after boot / app update. */
class FocusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ServiceLocator.init(context.applicationContext)
        val pending = goAsync()
        scope.launch {
            try {
                if (intent.action == ACTION_END) ServiceLocator.focus.completeIfDue()
                ServiceLocator.focus.restore()
            } finally { pending.finish() }
        }
    }

    companion object {
        const val ACTION_END = "com.earnd.app.focus.END"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
