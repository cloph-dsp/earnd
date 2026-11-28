package com.earnd.app.alarms

import com.earnd.app.data.entity.OwnerKind

/** What a "Done" action must do after the save attempt. */
internal sealed interface DoneOutcome {
    data object COMPLETE : DoneOutcome
    data object OPEN_APP : DoneOutcome
}

/** Pure decision: only a habit that still needs a photo, focus session or amount opens the app. */
internal fun doneOutcome(kind: OwnerKind, doneSaved: Boolean, needsApp: Boolean): DoneOutcome =
    if (!doneSaved && kind == OwnerKind.HABIT && needsApp) DoneOutcome.OPEN_APP else DoneOutcome.COMPLETE
