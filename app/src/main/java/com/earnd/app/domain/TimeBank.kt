package com.earnd.app.domain

import com.earnd.app.data.Settings
import com.earnd.app.data.SettingsRepository
import com.earnd.app.data.dao.LedgerDao
import com.earnd.app.data.dao.SessionDao
import com.earnd.app.data.entity.LedgerEntryEntity
import com.earnd.app.data.entity.LedgerKind
import com.earnd.app.data.entity.OwnerKind
import com.earnd.app.data.entity.SessionReason
import com.earnd.app.data.entity.UnlockSessionEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.combine

data class Balance(val minutes: Int, val periodKey: Long, val resetsAt: Long)

sealed interface SpendResult {
    data class Started(val session: UnlockSessionEntity) : SpendResult
    data class Refused(val reason: String) : SpendResult
}

/** Refusal reasons returned in [SpendResult.Refused]. */
object RefuseReason {
    const val HARD_MODE = "hard_mode"
    const val NOT_ENOUGH = "not_enough_minutes"
    const val INVALID = "invalid_minutes"
    const val NOT_BEDTIME = "not_bedtime"
    const val GRACE_USED = "grace_used"
}

/**
 * Earned-minutes ledger and unlock sessions. Each ledger entry is tagged with the period it
 * belongs to, so the balance is simply the sum of the current period; RESET entries close out
 * old periods for bookkeeping.
 */
class TimeBank(
    private val ledger: LedgerDao,
    private val sessions: SessionDao,
    private val settings: SettingsRepository,
    private val clock: Clock,
    private val bus: ChangeBus,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeBalance(): Flow<Balance> =
        combine(settings.settings, bus.events.map { }.onStart { emit(Unit) }) { s, _ -> s }
            .flatMapLatest { s ->
                val now = clock.now()
                val key = periodKey(s, now)
                ledger.observeSum(key).map { Balance(it ?: 0, key, resetsAt(s, now)) }
            }

    suspend fun balance(now: Long = clock.now()): Balance {
        val s = settings.current()
        val key = periodKey(s, now)
        return Balance(ledger.sum(key), key, resetsAt(s, now))
    }

    suspend fun earn(minutes: Int, kind: LedgerKind, sourceKind: OwnerKind?, sourceId: Long?): Long {
        val now = clock.now()
        val id = ledger.insert(
            LedgerEntryEntity(at = now, kind = kind, minutes = minutes, sourceKind = sourceKind,
                sourceId = sourceId, periodKey = periodKey(settings.current(), now)),
        )
        bus.emit(ChangeKind.BALANCE)
        return id
    }

    /**
     * Undoes what a check-in or task earned. Only entries in the current period are reversed;
     * minutes from a period that already reset are gone anyway.
     */
    suspend fun reverse(sourceKind: OwnerKind, sourceId: Long) {
        val now = clock.now()
        val key = periodKey(settings.current(), now)
        val net = ledger.forSource(sourceKind, sourceId).filter { it.periodKey == key }.sumOf { it.minutes }
        if (net == 0) return
        ledger.insert(LedgerEntryEntity(at = now, kind = LedgerKind.UNDO, minutes = -net,
            sourceKind = sourceKind, sourceId = sourceId, periodKey = key))
        bus.emit(ChangeKind.BALANCE)
    }

    /** Spends saved minutes on an unlock session. Refused in hard mode or without enough balance. */
    suspend fun spend(minutes: Int): SpendResult {
        val s = settings.current()
        if (s.hardMode) return SpendResult.Refused(RefuseReason.HARD_MODE)
        if (minutes <= 0) return SpendResult.Refused(RefuseReason.INVALID)
        val now = clock.now()
        val key = periodKey(s, now)
        if (ledger.sum(key) < minutes) return SpendResult.Refused(RefuseReason.NOT_ENOUGH)
        ledger.insert(LedgerEntryEntity(at = now, kind = LedgerKind.SPEND, minutes = -minutes, periodKey = key))
        bus.emit(ChangeKind.BALANCE)
        return startSession(minutes, SessionReason.SPEND)
    }

    /** Starts a session without touching the ledger; ends any running one first. */
    suspend fun startSession(minutes: Int, reason: String): SpendResult {
        if (minutes <= 0) return SpendResult.Refused(RefuseReason.INVALID)
        val now = clock.now()
        endSession(now)
        val s = UnlockSessionEntity(startedAt = now, plannedEndAt = now + minutes * MINUTE, reason = reason)
        val saved = s.copy(id = sessions.insert(s))
        bus.emit(ChangeKind.SESSION)
        return SpendResult.Started(saved)
    }

    /** Starts the "all done" unlock that lasts until the end of today. */
    suspend fun startAllDoneUnlock(): SpendResult {
        val now = clock.now()
        val end = clock.startOfDay(clock.localDate(now).plusDays(1))
        val minutes = ((end - now + MINUTE - 1) / MINUTE).toInt()
        return startSession(minutes, SessionReason.ALL_DONE)
    }

    /** One short grace unlock per bedtime night. */
    suspend fun startBedtimeGrace(): SpendResult {
        val s = settings.current()
        val now = clock.now()
        val windowStart = bedtimeWindow(s)?.startOf(now, clock.zone())
            ?: return SpendResult.Refused(RefuseReason.NOT_BEDTIME)
        val last = sessions.lastWithReason(SessionReason.BEDTIME_GRACE)
        if (last != null && last.startedAt >= windowStart) return SpendResult.Refused(RefuseReason.GRACE_USED)
        return startSession(s.bedtimeGraceMin, SessionReason.BEDTIME_GRACE)
    }

    /** Ends the running session; refunds unused whole minutes of a spent session in the same period. */
    suspend fun endSession(now: Long = clock.now()) {
        val active = sessions.active() ?: return
        val endedAt = minOf(now, active.plannedEndAt)
        sessions.update(active.copy(endedAt = endedAt))
        if (active.reason == SessionReason.SPEND && now < active.plannedEndAt) {
            val s = settings.current()
            val unused = ((active.plannedEndAt - now) / MINUTE).toInt()
            if (unused > 0 && periodKey(s, active.startedAt) == periodKey(s, now)) {
                ledger.insert(LedgerEntryEntity(at = now, kind = LedgerKind.ADJUST, minutes = unused,
                    periodKey = periodKey(s, now)))
                bus.emit(ChangeKind.BALANCE)
            }
        }
        bus.emit(ChangeKind.SESSION)
    }

    fun observeActiveSession(): Flow<UnlockSessionEntity?> = sessions.observeActive()

    /** Running session at [now], closing it out if it has already expired. */
    suspend fun activeSession(now: Long = clock.now()): UnlockSessionEntity? {
        val a = sessions.active() ?: return null
        if (a.plannedEndAt > now) return a
        sessions.update(a.copy(endedAt = a.plannedEndAt))
        bus.emit(ChangeKind.SESSION)
        return null
    }

    /** When the period has rolled, writes a RESET entry zeroing the previous period. */
    suspend fun resetIfPeriodRolled(now: Long = clock.now()) {
        val key = periodKey(settings.current(), now)
        val last = settings.rawGet(LAST_PERIOD_KEY)?.toLongOrNull()
        if (last == key) return
        if (last != null && last < key) {
            val leftover = ledger.sum(last)
            if (leftover != 0) {
                ledger.insert(LedgerEntryEntity(at = now, kind = LedgerKind.RESET, minutes = -leftover, periodKey = last))
            }
        }
        settings.rawPut(LAST_PERIOD_KEY, key.toString())
        bus.emit(ChangeKind.BALANCE)
    }

    fun periodKey(s: Settings, now: Long): Long =
        Periods.start(clock.localDate(now), s.resetPeriod, s.weekStartsOn).toEpochDay()

    private fun resetsAt(s: Settings, now: Long): Long =
        clock.startOfDay(Periods.nextStart(clock.localDate(now), s.resetPeriod, s.weekStartsOn))

    companion object {
        const val MINUTE = 60_000L
        private const val LAST_PERIOD_KEY = "bank.lastPeriodKey"

        fun bedtimeWindow(s: Settings): DailyWindow? =
            if (s.bedtimeEnabled) DailyWindow(s.bedtimeStartMin, s.bedtimeEndMin) else null
    }
}
