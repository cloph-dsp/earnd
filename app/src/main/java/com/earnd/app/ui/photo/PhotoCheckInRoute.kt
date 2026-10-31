package com.earnd.app.ui.photo

import androidx.compose.runtime.Composable
import com.earnd.app.ServiceLocator
import com.earnd.app.photo.PhotoCaptureScreen
import com.earnd.app.ui.common.launchUi

/** Photo-required check-in: the photo module captures, we record the check-in with its path. */
@Composable
fun PhotoCheckInRoute(habitId: Long, onDone: () -> Unit) {
    PhotoCaptureScreen(
        onCaptured = { path ->
            launchUi { ServiceLocator.habitService.checkIn(habitId, photoPath = path) }
            onDone()
        },
        onCancel = onDone,
    )
}
