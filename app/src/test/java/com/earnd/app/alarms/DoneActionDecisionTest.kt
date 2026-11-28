package com.earnd.app.alarms

import com.earnd.app.data.entity.OwnerKind
import org.junit.Assert.assertEquals
import org.junit.Test

class DoneActionDecisionTest {
    @Test fun tapSavedCompletes() {
        assertEquals(DoneOutcome.COMPLETE, doneOutcome(OwnerKind.HABIT, doneSaved = true, needsApp = false))
    }

    @Test fun goneOrMissingCompletesWithoutOpeningApp() {
        assertEquals(DoneOutcome.COMPLETE, doneOutcome(OwnerKind.HABIT, doneSaved = false, needsApp = false))
        assertEquals(DoneOutcome.COMPLETE, doneOutcome(OwnerKind.TASK, doneSaved = false, needsApp = false))
    }

    @Test fun habitNeedingAppOpensCheckIn() {
        assertEquals(DoneOutcome.OPEN_APP, doneOutcome(OwnerKind.HABIT, doneSaved = false, needsApp = true))
    }
}
