package com.earnd.app.ui.habits

import com.earnd.app.ServiceLocator
import java.io.File

/** Small UI-side helpers around HabitService. */
object HabitActions {
    /** Undoes a check-in and removes its photo, if it had one. */
    suspend fun undo(checkInId: Long) {
        val path = ServiceLocator.habitService.undoCheckIn(checkInId) ?: return
        val f = File(path).let { if (it.isAbsolute) it else File(File(ServiceLocator.appContext.filesDir, "photos"), path) }
        runCatching { f.delete() }
    }
}
