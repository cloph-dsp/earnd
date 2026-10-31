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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.earnd.app.ServiceLocator
import com.earnd.app.data.entity.FocusSessionEntity
import com.earnd.app.data.entity.FocusState
import com.earnd.app.ui.common.launchUi
import com.earnd.app.ui.theme.Earnd
import com.earnd.app.ui.theme.EarndTheme
import com.earnd.app.ui.theme.Num
import com.earnd.app.ui.theme.NumberStyle
import com.earnd.app.ui.theme.OutlinedButton
import kotlinx.coroutines.delay

/**
 * Full-screen focus timer. Shown over the lock screen; Back does nothing, and the guard brings it
 * back whenever another app comes to the front. Cancel asks first and discards the progress.
 */
class FocusActivity : ComponentActivity() {
    private var askCancel by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ServiceLocator.init(applicationContext)
        enableEdgeToEdge()
        setShowWhenLocked(true)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
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

    Column(
        Modifier.fillMaxSize().background(p.bg).safeDrawingPadding().padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(if (done) "Done" else "Focusing", style = MaterialTheme.typography.labelLarge, color = p.secondary)
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
                drawArc(amber, -90f, 360f * progress, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Butt))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (done) "$total min" else FocusMath.clock(FocusMath.remainingMs(s, now)),
                    style = NumberStyle.copy(fontSize = 52.sp, lineHeight = 56.sp), color = if (done) p.amber else p.text,
                )
                Num(if (done) "checked in" else "of $total min", Modifier.padding(top = 4.dp))
            }
        }
        Spacer(Modifier.weight(1f))
        if (!done) {
            Text(
                "Other apps wait until the timer ends. Phone calls, SMS and maps still work; internet calls (e.g. WhatsApp) only if that app is always-open.",
                style = MaterialTheme.typography.bodySmall, color = p.muted, textAlign = TextAlign.Center,
            )
            TextButton(onClick = { onKeepOn(!keepOn) }) {
                Text(if (keepOn) "Screen stays on" else "Keep screen on", color = if (keepOn) p.amber else p.secondary)
            }
            OutlinedButton(onClick = { onAskCancel(true) }, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (askCancel && !done) {
        val lost = FocusMath.minutesLost(s, now)
        AlertDialog(
            onDismissRequest = { onAskCancel(false) },
            title = { Text("Cancel and lose $lost min of progress?") },
            text = { Text("Nothing is credited. Next time the timer starts from zero.") },
            confirmButton = { TextButton(onClick = { onAskCancel(false); onCancel() }) { Text("Cancel focus", color = p.red) } },
            dismissButton = { TextButton(onClick = { onAskCancel(false) }) { Text("Keep going") } },
        )
    }
}
