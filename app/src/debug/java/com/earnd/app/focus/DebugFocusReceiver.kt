package com.earnd.app.focus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.earnd.app.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Debug builds only. Starts or cancels a focus session from adb (needs the shell's DUMP permission):
 * adb shell am broadcast -n com.earnd.app/.focus.DebugFocusReceiver -a com.earnd.app.focus.DEBUG_START --el habit_id 3
 */
class DebugFocusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ServiceLocator.init(context.applicationContext)
        val done = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                when (intent.action) {
                    ACTION_START -> {
                        val r = ServiceLocator.focus.start(intent.getLongExtra("habit_id", 0L))
                        done.resultData = r.toString()
                        if (r is FocusStart.Started) context.startActivity(FocusActivity.intent(context))
                    }
                    ACTION_CANCEL -> done.resultData = "cancelled=" + ServiceLocator.focus.cancel()
                }
            } finally {
                done.finish()
            }
        }
    }

    private companion object {
        const val ACTION_START = "com.earnd.app.focus.DEBUG_START"
        const val ACTION_CANCEL = "com.earnd.app.focus.DEBUG_CANCEL"
    }
}
