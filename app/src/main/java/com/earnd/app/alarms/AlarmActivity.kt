package com.earnd.app.alarms

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.earnd.app.ServiceLocator
import com.earnd.app.alarms.AlarmKeys.putOwner
import com.earnd.app.data.entity.OwnerKind
import com.earnd.app.ui.theme.EarndTheme
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/** Full-screen ringing alarm: rings until the user taps Done or (a limited number of times) Snooze. */
class AlarmActivity : ComponentActivity() {
    private var owner: Pair<OwnerKind, Long>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        current = WeakReference(this)
        bind(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        bind(intent)
    }

    private fun bind(intent: Intent) {
        val o = AlarmKeys.ownerOf(intent) ?: return finish()
        owner = o
        lifecycleScope.launch {
            val actions = AlarmsModule.actions
            val label = actions.labelOf(o.first, o.second)
            val left = actions.snoozesLeft(o.first, o.second)
            val cfg = ServiceLocator.db.alarmConfigDao().get(o.first, o.second)
            AlarmRinger.start(this@AlarmActivity, cfg?.soundUri, cfg?.vibrate ?: true)
            setContent {
                EarndTheme {
                    AlarmScreen(
                        label = label,
                        snoozesLeft = left,
                        snoozeMinutes = NagSchedule.SNOOZE_MIN,
                        onDone = { act { actions.done(o.first, o.second) } },
                        onSnooze = { act { actions.snooze(o.first, o.second) } },
                    )
                }
            }
        }
    }

    private fun act(block: suspend () -> Unit) {
        AlarmRinger.stop()
        lifecycleScope.launch {
            block()
            finish()
        }
    }

    override fun onDestroy() {
        if (current?.get() === this) current = null
        super.onDestroy()
    }

    companion object {
        @Volatile private var current: WeakReference<AlarmActivity>? = null

        fun intent(context: Context, kind: OwnerKind, id: Long): Intent =
            Intent(context, AlarmActivity::class.java).setAction(AlarmKeys.ACTION_OPEN_RING)
                .putOwner(kind, id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        internal fun finishCurrent() {
            current?.get()?.let { it.runOnUiThread { it.finish() } }
        }
    }
}
