package com.earnd.app.widgets

import com.earnd.app.ServiceLocator
import com.earnd.app.domain.TimeBank
import com.earnd.app.domain.today

/** What the lock widget shows: mode plus remaining session minutes or the balance. */
data class LockState(val mode: Mode, val minutes: Int, val sessionEndsAt: Long?) {
    enum class Mode { LOCKED, UNLOCKED, BEDTIME }

    companion object {
        suspend fun load(): LockState {
            val clock = ServiceLocator.clock
            val bank = ServiceLocator.timeBank
            val now = clock.now()
            bank.resetIfPeriodRolled(now)
            val session = bank.activeSession(now)
            if (session != null) {
                val left = ((session.plannedEndAt - now + TimeBank.MINUTE - 1) / TimeBank.MINUTE).toInt()
                return LockState(Mode.UNLOCKED, left.coerceAtLeast(0), session.plannedEndAt)
            }
            val s = ServiceLocator.settings.current()
            val balance = bank.balance(now).minutes
            val bedtime = s.blockingEnabled && !s.onVacation(clock.today().toEpochDay()) &&
                TimeBank.bedtimeWindow(s)?.contains(now, clock.zone()) == true
            return LockState(if (bedtime) Mode.BEDTIME else Mode.LOCKED, balance, null)
        }
    }
}
