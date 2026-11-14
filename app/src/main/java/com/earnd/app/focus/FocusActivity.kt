package com.earnd.app.focus

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.earnd.app.R
import com.earnd.app.ServiceLocator
import com.earnd.app.data.entity.FocusSessionEntity
import com.earnd.app.data.entity.FocusState
import com.earnd.app.ui.common.launchUi
import com.earnd.app.ui.theme.Earnd
import com.earnd.app.ui.theme.EarndTheme
import com.earnd.app.ui.theme.Num
import com.earnd.app.ui.theme.NumberStyle
import kotlinx.coroutines.delay

/**
 * Full-screen focus timer. Shown over the lock screen; Back asks to stop, and the guard brings it
 * back whenever another app comes to the front. Cancel asks first and discards the progress.
 */
class FocusActivity : ComponentActivity() {
    private var askCancel by mutableStateOf(false)
    private var result by mutableStateOf<StopwatchResult?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ServiceLocator.init(applicationContext)
        enableEdgeToEdge()
        setShowWhenLocked(true)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { askCancel = true }
        })
        askCancel = intent.getBooleanExtra(EXTRA_ASK_CANCEL, false)
        setContent {
            EarndTheme {
                val session by remember { ServiceLocator.focus.observe() }.collectAsState(initial = LOADING)
                var keepOn by rememberSaveable { mutableStateOf(false) }
                LaunchedEffect(keepOn) {
                    if (keepOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                val s = session
                LaunchedEffect(s) {
                    if (s !== LOADING && (s == null || s.state == FocusState.CANCELLED)) finish()
                    if (s?.state == FocusState.DONE) { delay(2_500); finish() }
                }
                if (s != null && s !== LOADING) {
                    FocusScreen(
                        s, keepOn, onKeepOn = { keepOn = it },
                        askCancel = askCancel, onAskCancel = { askCancel = it },
                        onCancel = { launchUi { ServiceLocator.focus.cancel() } },
                        result = result,
                        onFinish = { launchUi { result = ServiceLocator.focus.finishStopwatch() } },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_ASK_CANCEL, false)) askCancel = true
    }

    companion object {
        private const val EXTRA_ASK_CANCEL = "com.earnd.app.focus.ASK_CANCEL"
        private val LOADING = FocusSessionEntity(id = -1, habitId = -1, startedAt = 0, durationMs = 0)

        fun intent(context: Context, askCancel: Boolean = false): Intent =
            Intent(context, FocusActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_ASK_CANCEL, askCancel)
    }
}

@Composable
private fun FocusScreen(
    s: FocusSessionEntity,
    keepOn: Boolean,
    onKeepOn: (Boolean) -> Unit,
    askCancel: Boolean,
    onAskCancel: (Boolean) -> Unit,
    onCancel: () -> Unit,
    result: StopwatchResult? = null,
    onFinish: () -> Unit = {},
) {
    val p = Earnd.palette
    val clock = ServiceLocator.clock
    var now by remember { mutableLongStateOf(clock.now()) }
    LaunchedEffect(s) {
        while (s.state == FocusState.ACTIVE) {
            now = clock.now()
            if (FocusMath.isDue(s, now)) { ServiceLocator.focus.completeIfDue(); break }
            delay(250)
        }
    }
    var name by remember { mutableStateOf("") }
    LaunchedEffect(s.habitId) { name = ServiceLocator.db.habitDao().get(s.habitId)?.name.orEmpty() }
    val done = s.state == FocusState.DONE
    val total = s.durationMs / 60_000L
    val progress = if (done) 1f else FocusMath.progress(s, now)
    var habit by remember { mutableStateOf<com.earnd.app.data.entity.HabitEntity?>(null) }
    LaunchedEffect(s.habitId) { habit = ServiceLocator.db.habitDao().get(s.habitId) }
    var showInfo by rememberSaveable { mutableStateOf(false) }
    val reward = s.rewardMinutes ?: habit?.minutesPerCheckIn
    var askFinish by remember { mutableStateOf(false) }
    val minSession = habit?.let { com.earnd.app.domain.TimedHabit.minSession(it) } ?: 0
    LaunchedEffect(s.stopwatch) { while (s.stopwatch && s.state == FocusState.ACTIVE) { now = clock.now(); delay(500) } }

    Column(
        Modifier.fillMaxSize().background(p.bg).safeDrawingPadding().padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(if (done) stringResource(R.string.focus_done) else if (s.stopwatch) stringResource(R.string.focus_practising) else stringResource(R.string.focus_focusing), style = MaterialTheme.typography.labelLarge, color = p.secondary)
        Text(
            name, style = MaterialTheme.typography.titleLarge, color = p.text, textAlign = TextAlign.Center,
            maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
            val track = p.line
            val amber = p.amber
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 6.dp.toPx()
                val topLeft = Offset(stroke / 2, stroke / 2)
                val arc = Size(size.width - stroke, size.height - stroke)
                drawArc(track, 0f, 360f, false, topLeft, arc, style = Stroke(stroke))
                val sweep = if (s.stopwatch || done) progress else (1f - progress).coerceIn(0f, 1f)
                drawArc(amber, -90f, 360f * sweep, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Butt))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    when {
                        done -> stringResource(R.string.focus_minutes, total)
                        s.stopwatch -> FocusMath.clock(FocusMath.elapsedMs(s, now))
                        else -> FocusMath.clock(FocusMath.remainingMs(s, now))
                    },
                    style = NumberStyle.copy(fontSize = 52.sp, lineHeight = 56.sp), color = if (done) p.amber else p.text,
                )
                Num(
                    when {
                        done && s.stopwatch -> when {
                            result == null -> stringResource(R.string.focus_result_finished)
                            result.earned == null -> stringResource(R.string.focus_result_too_short)
                            result.bonus > 0 -> stringResource(R.string.focus_result_bonus, result.earned, result.bonus)
                            else -> stringResource(R.string.focus_result_earned, result.earned)
                        }
                        done -> stringResource(R.string.focus_result_checked_in)
                        s.stopwatch -> habit?.let { h ->
                            val r = com.earnd.app.domain.TimedHabit.stopwatchReward(h, FocusMath.elapsedMs(s, now))
                            if (r == null) stringResource(R.string.focus_min_to_count, minSession) else stringResource(R.string.focus_so_far, r)
                        } ?: ""
                        else -> if (reward != null) stringResource(R.string.focus_when_done, reward) else stringResource(R.string.focus_of_total, total)
                    },
                    Modifier.padding(top = 4.dp),
                )
            }
        }
        if (!s.stopwatch && !done && total > 0) {
            com.earnd.app.ui.theme.DrainRuler(
                ((FocusMath.remainingMs(s, now) + 59_999L) / 60_000L).toInt(), total.toInt(),
                Modifier.padding(top = 24.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        if (!done) {
            TextButton(onClick = { showInfo = !showInfo }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.focus_still_works), color = p.secondary)
            }
            if (showInfo) {
                Text(
                    stringResource(R.string.focus_still_works_body),
                    style = MaterialTheme.typography.bodySmall, color = p.muted, textAlign = TextAlign.Center,
                )
            }
            TextButton(onClick = { onKeepOn(!keepOn) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (keepOn) stringResource(R.string.focus_screen_stays_on) else stringResource(R.string.focus_keep_screen_on), color = if (keepOn) p.amber else p.secondary)
            }
            if (s.stopwatch) {
                com.earnd.app.ui.theme.Button(
                    onClick = { if (FocusMath.elapsedMs(s, now) / 60_000L < minSession) askFinish = true else onFinish() },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.focus_finish)) }
            }
            TextButton(onClick = { onAskCancel(true) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.focus_cancel), color = p.secondary) }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (askFinish && !done) {
        AlertDialog(
            onDismissRequest = { askFinish = false },
            shape = RectangleShape,
            title = { Text(stringResource(R.string.focus_finish_early_title, minSession)) },
            text = { Text(stringResource(R.string.focus_finish_early_body, minSession)) },
            confirmButton = { TextButton(onClick = { askFinish = false; onFinish() }) { Text(stringResource(R.string.focus_finish_anyway)) } },
            dismissButton = { TextButton(onClick = { askFinish = false }) { Text(stringResource(R.string.focus_keep_going)) } },
        )
    }

    if (askCancel && !done && s.stopwatch) {
        AlertDialog(
            onDismissRequest = { onAskCancel(false) },
            shape = RectangleShape,
            title = { Text(stringResource(R.string.focus_stop_title)) },
            text = { Text(stringResource(R.string.focus_stop_stopwatch_body)) },
            confirmButton = { TextButton(onClick = { onAskCancel(false); onCancel() }) { Text(stringResource(R.string.focus_stop), color = p.text) } },
            dismissButton = { TextButton(onClick = { onAskCancel(false) }) { Text(stringResource(R.string.focus_keep_going)) } },
        )
    } else if (askCancel && !done) {
        AlertDialog(
            onDismissRequest = { onAskCancel(false) },
            shape = RectangleShape,
            title = { Text(stringResource(R.string.focus_stop_title)) },
            text = { Text(stringResource(R.string.focus_stop_body)) },
            confirmButton = { TextButton(onClick = { onAskCancel(false); onCancel() }) { Text(stringResource(R.string.focus_stop), color = p.text) } },
            dismissButton = { TextButton(onClick = { onAskCancel(false) }) { Text(stringResource(R.string.focus_keep_going), color = p.amber) } },
        )
    }
}
