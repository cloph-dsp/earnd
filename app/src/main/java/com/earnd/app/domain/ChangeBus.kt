package com.earnd.app.domain

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** What changed; listeners (widgets, alarms, UI) refresh the parts they care about. */
enum class ChangeKind { HABITS, CHECKINS, TASKS, BALANCE, SESSION, SETTINGS, BLOCKLIST, ALARMS, FOCUS }

/** Process-wide fan-out of data changes for cross-module refresh. */
class ChangeBus {
    private val _events = MutableSharedFlow<ChangeKind>(extraBufferCapacity = 64)
    val events: SharedFlow<ChangeKind> = _events.asSharedFlow()

    fun emit(kind: ChangeKind) {
        _events.tryEmit(kind)
    }
}
