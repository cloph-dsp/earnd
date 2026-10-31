package com.earnd.app.ui.habits

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.earnd.app.ServiceLocator
import com.earnd.app.data.entity.CheckInMode

/** Target of earnd://checkin?habitId=: tap habits are checked in at once, photo habits open the camera, timed habits start focus. */
@Composable
fun CheckInRoute(habitId: Long, onNeedsPhoto: () -> Unit, onDone: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(habitId) {
        val h = ServiceLocator.db.habitDao().get(habitId)
        when {
            h == null || h.archived -> onDone()
            (h.focusMinutes ?: 0) > 0 -> { com.earnd.app.focus.startFocus(context, habitId); onDone() }
            h.checkInMode == CheckInMode.PHOTO -> onNeedsPhoto()
            else -> { ServiceLocator.habitService.checkIn(habitId); onDone() }
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}
