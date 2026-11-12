package com.earnd.app.alarms

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.earnd.app.alarms.AlarmKeys.putOwner
import com.earnd.app.data.entity.OwnerKind
import com.earnd.app.ui.theme.EarndTheme
import com.earnd.app.ServiceLocator
import com.earnd.app.ui.habits.HabitRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

/** Full-screen ringing alarm over the lock screen: Stop, Snooze (while allowed) and Mark done. */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        current = WeakReference(this)
        // Back = Silence (same path as the Silence button).
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val o = AlarmKeys.ownerOf(intent) ?: return finish()
                act { AlarmsModule.actions.stop(o.first, o.second) }
            }
        })
        bind(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        bind(intent)
    }

    private fun bind(intent: Intent) {
        val o = AlarmKeys.ownerOf(intent) ?: return finish()
        val actions = AlarmsModule.actions
        var label by mutableStateOf(intent.getStringExtra(AlarmKeys.EXTRA_LABEL).orEmpty())
        var left by mutableStateOf(0)
        var ignored by mutableStateOf(0)
        var reward by mutableStateOf(0)
        // A habit that needs a number (amount or minutes): asked in a second pop-up after Done.
        var asking by mutableStateOf<com.earnd.app.data.entity.HabitEntity?>(null)
        // Show Stop immediately; label and snoozes fill in after the DB read.
        setContent {
            EarndTheme {
                AlarmScreen(
                    label = label,
                    snoozesLeft = left,
                    ignoredCount = ignored,
                    rewardMinutes = reward,
                    onStop = { act { actions.stop(o.first, o.second) } },
                    onSnooze = { minutes -> act { actions.snooze(o.first, o.second, minutes) } },
                    onDone = {
                        lifecycleScope.launch {
                            val h = if (o.first == OwnerKind.HABIT) runCatching { ServiceLocator.db.habitDao().get(o.second) }.getOrNull()?.takeIf { !it.archived } else null
                            val kind = h?.let { runCatching { ServiceLocator.habitService.checkInKind(it, ServiceLocator.settings.current()) }.getOrNull() }
                            when (kind) {
                                com.earnd.app.domain.HabitService.CheckInKind.AMOUNT, com.earnd.app.domain.HabitService.CheckInKind.FOCUS -> {
                                    AlarmRingService.silence() // quiet while typing; the item is not resolved yet
                                    asking = h
                                }
                                com.earnd.app.domain.HabitService.CheckInKind.PHOTO -> {
                                    act(null)
                                    startActivity(com.earnd.app.blocking.BlockingIntents.checkIn(this@AlarmActivity, o.second))
                                }
                                else -> act { actions.done(o.first, o.second) }
                            }
                        }
                    },
                )
                asking?.let { h ->
                    val timed = com.earnd.app.domain.TimedHabit.isTimed(h)
                    com.earnd.app.ui.habits.AmountDialog(
                        if (timed) h.copy(unit = getString(com.earnd.app.R.string.alarms_ring_minutes_unit)) else h,
                        onDismiss = { asking = null },
                    ) { amount -> asking = null; act { actions.doneWithAmount(h.id, amount) } }
                }
            }
        }
        lifecycleScope.launch {
            runCatching { actions.labelOf(o.first, o.second) }.getOrNull()?.takeIf { it.isNotBlank() }?.let { label = it }
            left = runCatching { actions.snoozesLeft(o.first, o.second) }.getOrDefault(0)
            withContext(Dispatchers.IO) {
                runCatching {
                    val sl = ServiceLocator
                    ignored = sl.db.nagStateDao().get(o.first, o.second)?.nagCount ?: 0
                    reward = when (o.first) {
                        OwnerKind.HABIT -> sl.db.habitDao().get(o.second)?.let { HabitRules.rewardMinutes(it, sl.settings.current()) } ?: 0
                        OwnerKind.TASK -> sl.db.taskDao().get(o.second)?.let { (it.rewardMinutes ?: sl.settings.current().defaultTaskRewardMin ?: 0).coerceAtLeast(0) } ?: 0
                    }
                }
            }
        }
    }

    /** Always silences first (even if the follow-up DB work fails), then finishes. */
    private fun act(block: (suspend () -> Unit)?) {
        Ringing.stopAll(this)
        // The work must outlive this screen: finishing cancels lifecycleScope, which used to cut the save/silence/snooze
        // off half-way (done not recorded, silence not kept, snooze lost).
        if (block != null) actScope.launch {
            try { block() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Throwable) {
                android.util.Log.e("Earnd", "Alarm action failed", e)
            }
        }
        finish()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
            keyCode == KeyEvent.KEYCODE_VOLUME_MUTE
        ) {
            AlarmRingService.silence()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        if (current?.get() === this) current = null
        super.onDestroy()
    }

    companion object {
        @Volatile private var current: WeakReference<AlarmActivity>? = null
        private val actScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Default)

        fun intent(context: Context, kind: OwnerKind, id: Long): Intent =
            Intent(context, AlarmActivity::class.java).setAction(AlarmKeys.ACTION_OPEN_RING)
                .putOwner(kind, id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        internal fun finishCurrent() {
            current?.get()?.let { it.runOnUiThread { it.finish() } }
        }
    }
}
