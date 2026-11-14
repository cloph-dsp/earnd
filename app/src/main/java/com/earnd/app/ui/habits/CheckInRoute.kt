package com.earnd.app.ui.habits

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.earnd.app.ServiceLocator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.earnd.app.data.entity.HabitEntity
import com.earnd.app.domain.FocusOption
import com.earnd.app.domain.HabitService
import com.earnd.app.domain.TimedHabit
import kotlinx.coroutines.launch

/**
 * Target of earnd://checkin?habitId=: tap habits are checked in at once, photo habits open the camera,
 * timed habits ask how long it was. A tick records what was done; only Start runs the timer.
 */
@Composable
fun CheckInRoute(habitId: Long, onNeedsPhoto: () -> Unit, onDone: () -> Unit) {
    var askAmount by remember { mutableStateOf<HabitEntity?>(null) }
    var askDuration by remember { mutableStateOf<Pair<HabitEntity, List<FocusOption>>?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(habitId) {
        val h = ServiceLocator.db.habitDao().get(habitId)
        if (h == null || h.archived) return@LaunchedEffect onDone()
        val s = ServiceLocator.settings.current()
        when (ServiceLocator.habitService.checkInKind(h, s)) {
            HabitService.CheckInKind.FOCUS -> askDuration = h to TimedHabit.options(h, s)
            HabitService.CheckInKind.PHOTO -> onNeedsPhoto()
            HabitService.CheckInKind.AMOUNT -> askAmount = h
            HabitService.CheckInKind.TAP -> { ServiceLocator.habitService.checkIn(habitId); onDone() }
        }
    }
    askAmount?.let { h ->
        AmountDialog(h, onDismiss = onDone) { amount ->
            askAmount = null
            scope.launch { ServiceLocator.habitService.checkIn(h.id, quantity = amount); onDone() }
        }
    }
    askDuration?.let { (h, opts) ->
        DurationDialog(h, opts, onDismiss = onDone) { _, reward ->
            askDuration = null
            scope.launch {
                ServiceLocator.habitService.checkInWithBonus(h.id, rewardOverride = reward)
                onDone()
            }
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}
