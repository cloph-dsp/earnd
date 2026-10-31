package com.earnd.app.focus

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.earnd.app.ServiceLocator
import com.earnd.app.data.entity.FocusState
import kotlinx.coroutines.delay

/** Live "Focusing · 12:34 left" for a habit row, or null when that habit is not focusing. */
@Composable
fun focusLabel(habitId: Long): String? {
    val s by remember { ServiceLocator.focus.observe() }.collectAsState(initial = null)
    var now by remember { mutableLongStateOf(ServiceLocator.clock.now()) }
    val cur = s?.takeIf { it.habitId == habitId && it.state == FocusState.ACTIVE }
    LaunchedEffect(cur) { while (cur != null) { now = ServiceLocator.clock.now(); delay(1_000) } }
    return cur?.let { "Focusing · ${FocusMath.clock(FocusMath.remainingMs(it, now))} left" }
}

/** Starts the habit's focus session and opens the timer (or reopens the running one). */
suspend fun startFocus(context: Context, habitId: Long) {
    when (ServiceLocator.focus.start(habitId)) {
        is FocusStart.Started, is FocusStart.Busy -> context.startActivity(FocusActivity.intent(context))
        FocusStart.NotTimed -> Unit
    }
}
