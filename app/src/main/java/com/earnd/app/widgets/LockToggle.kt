package com.earnd.app.widgets

import com.earnd.app.ServiceLocator
import com.earnd.app.domain.SpendResult
import com.earnd.app.domain.today

/**
 * Tap behaviour of the lock widget: lock when unlocked; otherwise start a session (all-done
 * unlock, or spend the balance) when allowed; else ask to open the app so the user can earn.
 */
internal object LockToggle {
    /** Returns true when the app should be opened instead. */
    suspend fun toggle(): Boolean {
        val state = LockState.load()
        return when (state.mode) {
            LockState.Mode.UNLOCKED -> { ServiceLocator.timeBank.endSession(); false }
            LockState.Mode.BEDTIME -> true
            LockState.Mode.LOCKED -> !tryUnlock(state.minutes)
        }
    }

    private suspend fun tryUnlock(balance: Int): Boolean {
        val s = ServiceLocator.settings.current()
        val bank = ServiceLocator.timeBank
        if (s.allDoneUnlock && ServiceLocator.habitService.allDone(ServiceLocator.clock.today())) {
            return bank.startAllDoneUnlock() is SpendResult.Started
        }
        if (s.hardMode || balance <= 0) return false
        // Spend the whole balance; locking early refunds the unused minutes.
        return bank.spend(balance) is SpendResult.Started
    }
}
