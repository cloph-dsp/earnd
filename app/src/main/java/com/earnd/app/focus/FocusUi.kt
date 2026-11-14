package com.earnd.app.focus

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.earnd.app.R
import com.earnd.app.ServiceLocator
import com.earnd.app.i18n.Str
import com.earnd.app.data.entity.FocusState
import com.earnd.app.domain.FocusOption
import com.earnd.app.ui.common.launchUi
import com.earnd.app.ui.theme.Earnd
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

/** Live "Focusing · 12:34 left" / "Practising · 23:10" for a habit row, or null when that habit is not running. */
@Composable
fun focusLabel(habitId: Long): String? {
    val s by remember { ServiceLocator.focus.observe() }.collectAsState(initial = null)
    var now by remember { mutableLongStateOf(ServiceLocator.clock.now()) }
    val cur = s?.takeIf { it.habitId == habitId && it.state == FocusState.ACTIVE }
    LaunchedEffect(cur) { while (cur != null) { now = ServiceLocator.clock.now(); delay(1_000) } }
    return cur?.let {
        if (it.stopwatch) stringResource(R.string.focus_label_practising, FocusMath.clock(FocusMath.elapsedMs(it, now)))
        else stringResource(R.string.focus_label_focusing, FocusMath.clock(FocusMath.remainingMs(it, now)))
    }
}

/** Start button text: "Start" (stopwatch), "Start 20 min" (one length) or "Start…" (a choice). */
fun startLabel(h: com.earnd.app.data.entity.HabitEntity, s: com.earnd.app.data.Settings): String {
    val opts = com.earnd.app.domain.TimedHabit.options(h, s)
    return when {
        h.stopwatch -> Str.get(R.string.focus_start)
        opts.size == 1 -> Str.get(R.string.focus_start_min, opts[0].minutes)
        else -> Str.get(R.string.focus_start_choice)
    }
}

/** "+5 min · +3 comeback bonus" style confirmation, or null when there was no bonus. */
fun bonusText(r: com.earnd.app.domain.CheckInResult): String? =
    if (r.bonus > 0) Str.get(R.string.focus_bonus_text, r.checkIn.minutesEarned, r.bonus) else null

/** Pending duration choice, shown by [FocusPickerHost]. */
object FocusPicker {
    data class Request(val habitId: Long, val name: String, val options: List<FocusOption>)
    val pending = MutableStateFlow<Request?>(null)
}

/**
 * Starts the habit's focus session and opens the timer (or reopens the running one). With
 * several durations and no [minutes], asks via [FocusPickerHost] first.
 */
suspend fun startFocus(context: Context, habitId: Long, minutes: Int? = null) {
    when (val r = ServiceLocator.focus.start(habitId, minutes)) {
        is FocusStart.Started, is FocusStart.Busy -> context.startActivity(FocusActivity.intent(context))
        is FocusStart.Choose -> {
            val name = ServiceLocator.db.habitDao().get(habitId)?.name.orEmpty()
            FocusPicker.pending.value = FocusPicker.Request(habitId, name, r.options)
        }
        FocusStart.NotTimed -> Unit
    }
}

/** Quick picker of a habit's focus durations; place once at the app root. */
@Composable
fun FocusPickerHost() {
    val req by FocusPicker.pending.collectAsState()
    val r = req ?: return
    val context = LocalContext.current
    val p = Earnd.palette
    AlertDialog(
        onDismissRequest = { FocusPicker.pending.value = null },
        shape = RectangleShape,
        title = { Text(r.name.ifBlank { stringResource(R.string.focus_fallback_name) }) },
        text = {
            Column {
                r.options.forEach { o ->
                    TextButton(onClick = {
                        FocusPicker.pending.value = null
                        launchUi { startFocus(context, r.habitId, o.minutes) }
                    }) { Text(stringResource(R.string.focus_option, o.minutes, o.reward), color = p.amber) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { FocusPicker.pending.value = null }) { Text(stringResource(R.string.focus_not_now)) } },
    )
}
