package com.earnd.app.blocking

import com.earnd.app.ServiceLocator
import com.earnd.app.domain.SpendResult

/** Summary of the lock for widgets, notifications and remote reads. */
data class LockState(
    val unlocked: Boolean,
    val sessionEndsAt: Long?,
    val sessionReason: String?,
    val balanceMinutes: Int,
)

/** Small facade over TimeBank for other features (widget, remote, UI). */
object LockControl {
    suspend fun state(now: Long = ServiceLocator.clock.now()): LockState {
        val bank = ServiceLocator.timeBank
        val s = bank.activeSession(now)
        return LockState(s != null, s?.plannedEndAt, s?.reason, bank.balance(now).minutes)
    }

    /** Starts a paid unlock session; respects hard mode and balance. */
    suspend fun unlockFor(minutes: Int): SpendResult = ServiceLocator.timeBank.spend(minutes)

    /** Ends the current session early (TimeBank refunds unused whole minutes). */
    suspend fun lockNow() = ServiceLocator.timeBank.endSession()
}
