package com.earnd.app.widgets

import com.earnd.app.ServiceLocator
import com.earnd.app.domain.TimeBank
import com.earnd.app.domain.today
import com.earnd.app.domain.ChangeKind
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

/** What the lock widget shows: mode plus remaining session minutes or the balance. */
data class LockState(val mode: Mode, val minutes: Int, val sessionEndsAt: Long?) {
    enum class Mode { LOCKED, UNLOCKED, BEDTIME }

    companion object {
        private val relevant = setOf(ChangeKind.BALANCE, ChangeKind.SESSION, ChangeKind.SETTINGS, ChangeKind.CHECKINS)

        /** Reloads on time-bank or settings changes and once a minute for the countdown. */
        fun observe(): Flow<LockState> = merge(
            ServiceLocator.changeBus.events.filter { it in relevant }.map { },
            flow { while (true) { emit(Unit); delay(TimeBank.MINUTE) } },
        ).map { load() }

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
