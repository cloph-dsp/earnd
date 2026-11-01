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
import com.earnd.app.data.entity.ResetPeriod
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.combine

/**
 * [resetsAt] is the next daily/weekly reset, null in rolling mode. In rolling mode [nextExpiryAt] and
 * [minutesExpiringNext] describe the soonest lapse of banked minutes.
 */
data class Balance(
    val minutes: Int,
    val periodKey: Long,
    val resetsAt: Long?,
    val nextExpiryAt: Long? = null,
    val minutesExpiringNext: Int = 0,
)

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
    private val tx: Transactor = MutexTransactor(),
) {
    init {
        settings.periodChangeListener = { old, new -> onPeriodChanged(old, new) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeBalance(): Flow<Balance> =
        combine(settings.settings, bus.events.map { }.onStart { emit(Unit) }) { s, _ -> s }
            .flatMapLatest { s ->
                if (s.resetPeriod == ResetPeriod.ROLLING) {
                    flow { while (true) { emit(Unit); delay(TICK_MS) } }.map { balance(clock.now()) }
                } else {
                    val now = clock.now()
                    val key = periodKey(s, now)
                    ledger.observeSum(key).map { Balance(it ?: 0, key, resetsAt(s, now)) }
                }
            }

    suspend fun balance(now: Long = clock.now()): Balance {
        val s = settings.current()
        val key = periodKey(s, now)
        if (s.resetPeriod == ResetPeriod.ROLLING) {
            val lots = lots(now)
            val next = ExpiryLedger.nextExpiry(lots, now)
            return Balance(ExpiryLedger.balance(lots, now), key, null, next?.at, next?.minutes ?: 0)
        }
        return Balance(ledger.sum(key), key, resetsAt(s, now))
    }

    private suspend fun lots(now: Long): List<Lot> = ledger.unexpiredLots(now).map { it.toLot() }

    private fun LedgerEntryEntity.toLot() = Lot(id, at, expiresAt ?: Long.MAX_VALUE, remaining, sourceKind, sourceId)

    private suspend fun applyChanges(c: LotChanges) = c.remaining.forEach { (id, r) -> ledger.setRemaining(id, r) }

    suspend fun earn(minutes: Int, kind: LedgerKind, sourceKind: OwnerKind?, sourceId: Long?): Long = tx.run {
        val now = clock.now()
        val s = settings.current()
        val rolling = s.resetPeriod == ResetPeriod.ROLLING && minutes > 0
        val id = ledger.insert(
            LedgerEntryEntity(at = now, kind = kind, minutes = minutes, sourceKind = sourceKind,
                sourceId = sourceId, periodKey = periodKey(s, now),
                expiresAt = if (rolling) now + s.rollingExpiryHours * HOUR else null,
                remaining = if (rolling) minutes else 0),
        )
        bus.emit(ChangeKind.BALANCE)
        id
    }

    /**
     * Undoes what a check-in or task earned. Only entries in the current period are reversed;
     * minutes from a period that already reset are gone anyway.
     */
    suspend fun reverse(sourceKind: OwnerKind, sourceId: Long): Unit = tx.run {
        val now = clock.now()
        val s = settings.current()
        val key = periodKey(s, now)
        if (s.resetPeriod == ResetPeriod.ROLLING) {
            val entries = ledger.forSource(sourceKind, sourceId)
            val net = rollingNet(entries)
            if (net <= 0) return@run
            val sourceLots = entries.filter { it.expiresAt != null }.map { it.toLot() }
            val all = (lots(now) + sourceLots).distinctBy { it.id }
            applyChanges(ExpiryLedger.undo(all, now, sourceKind, sourceId, net))
            ledger.insert(LedgerEntryEntity(at = now, kind = LedgerKind.UNDO, minutes = -net,
                sourceKind = sourceKind, sourceId = sourceId, periodKey = key))
            bus.emit(ChangeKind.BALANCE)
            return@run
        }
        val net = ledger.forSource(sourceKind, sourceId).filter { it.periodKey == key }.sumOf { it.minutes }
        if (net == 0) return@run
        ledger.insert(LedgerEntryEntity(at = now, kind = LedgerKind.UNDO, minutes = -net,
            sourceKind = sourceKind, sourceId = sourceId, periodKey = key))
        bus.emit(ChangeKind.BALANCE)
    }

    /** Net minutes credited to a source in the current period. */
    suspend fun netFor(sourceKind: OwnerKind, sourceId: Long, now: Long = clock.now()): Int {
        val s = settings.current()
        val entries = ledger.forSource(sourceKind, sourceId)
        if (s.resetPeriod == ResetPeriod.ROLLING) return rollingNet(entries).coerceAtLeast(0)
        val key = periodKey(s, now)
        return entries.filter { it.periodKey == key }.sumOf { it.minutes }
    }

    /** Rolling mode: earned minutes of a source minus what was undone; entries from older modes don't count. */
    private fun rollingNet(entries: List<LedgerEntryEntity>): Int =
        entries.filter { it.expiresAt != null || it.kind == LedgerKind.UNDO }.sumOf { it.minutes }

    /**
     * Spends saved minutes on an unlock session. Refused in hard mode or without enough balance.
     * A request longer than the unlock cap is shortened to the cap; the rest stays in the balance.
     */
    suspend fun spend(requested: Int): SpendResult = tx.run {
        val s = settings.current()
        if (s.hardMode) return@run SpendResult.Refused(RefuseReason.HARD_MODE)
        val minutes = UnlockCap.clamp(requested, s.unlockCapMin)
        if (minutes <= 0) return@run SpendResult.Refused(RefuseReason.INVALID)
        val now = clock.now()
        val key = periodKey(s, now)
        if (s.resetPeriod == ResetPeriod.ROLLING) {
            val plan = ExpiryLedger.spend(lots(now), now, minutes) ?: return@run SpendResult.Refused(RefuseReason.NOT_ENOUGH)
            applyChanges(plan)
            // expiresAt on a SPEND row remembers the lot the last spent minutes came from, for refunds.
            ledger.insert(LedgerEntryEntity(at = now, kind = LedgerKind.SPEND, minutes = -minutes, periodKey = key,
                expiresAt = plan.lastExpiresAt))
        } else {
            if (ledger.sum(key) < minutes) return@run SpendResult.Refused(RefuseReason.NOT_ENOUGH)
            ledger.insert(LedgerEntryEntity(at = now, kind = LedgerKind.SPEND, minutes = -minutes, periodKey = key))
        }
        bus.emit(ChangeKind.BALANCE)
        startSessionLocked(minutes, SessionReason.SPEND)
    }

    /** Starts a session without touching the ledger; ends any running one first. */
    suspend fun startSession(minutes: Int, reason: String): SpendResult = tx.run { startSessionLocked(minutes, reason) }

    private suspend fun startSessionLocked(minutes: Int, reason: String): SpendResult {
        if (minutes <= 0) return SpendResult.Refused(RefuseReason.INVALID)
        val now = clock.now()
        endSessionLocked(now)
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
    suspend fun startBedtimeGrace(): SpendResult = tx.run {
        val s = settings.current()
        val now = clock.now()
        val windowStart = bedtimeWindow(s)?.startOf(now, clock.zone())
            ?: return@run SpendResult.Refused(RefuseReason.NOT_BEDTIME)
        val last = sessions.lastWithReason(SessionReason.BEDTIME_GRACE)
        if (last != null && last.startedAt >= windowStart) return@run SpendResult.Refused(RefuseReason.GRACE_USED)
        startSessionLocked(s.bedtimeGraceMin, SessionReason.BEDTIME_GRACE)
    }

    /** Ends the running session; refunds unused whole minutes of a spent session in the same period. */
    suspend fun endSession(now: Long = clock.now()): Unit = tx.run { endSessionLocked(now) }

    private suspend fun endSessionLocked(now: Long) {
        val active = sessions.active() ?: return
        val endedAt = minOf(now, active.plannedEndAt)
        sessions.update(active.copy(endedAt = endedAt))
        if (active.reason == SessionReason.SPEND && now < active.plannedEndAt) {
            val s = settings.current()
            val unused = ((active.plannedEndAt - now) / MINUTE).toInt()
            if (unused > 0 && s.resetPeriod == ResetPeriod.ROLLING) {
                // Back into the lot the spend drew from; gone if that lot has expired in the meantime.
                val exp = ExpiryLedger.refundExpiry(ledger.lastSpend()?.expiresAt, now)
                if (exp != null) {
                    ledger.insert(LedgerEntryEntity(at = now, kind = LedgerKind.ADJUST, minutes = unused,
                        periodKey = periodKey(s, now), expiresAt = exp, remaining = unused))
                    bus.emit(ChangeKind.BALANCE)
                }
            } else if (unused > 0 && periodKey(s, active.startedAt) == periodKey(s, now)) {
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
        return tx.run {
            val cur = sessions.active()
            when {
                cur == null -> null
                cur.plannedEndAt > now -> cur
                else -> {
                    sessions.update(cur.copy(endedAt = cur.plannedEndAt))
                    bus.emit(ChangeKind.SESSION)
                    null
                }
            }
        }
    }

    /** When the period has rolled, writes a RESET entry zeroing the previous period. */
    suspend fun resetIfPeriodRolled(now: Long = clock.now()): Unit = tx.run {
        val cur = settings.current()
        if (cur.resetPeriod == ResetPeriod.ROLLING) return@run
        val key = periodKey(cur, now)
        val last = settings.rawGet(LAST_PERIOD_KEY)?.toLongOrNull()
        if (last == key) return@run
        if (last != null && last < key) {
            val leftover = ledger.sum(last)
            if (leftover != 0) {
                ledger.insert(LedgerEntryEntity(at = now, kind = LedgerKind.RESET, minutes = -leftover, periodKey = last))
            }
        }
        settings.rawPut(LAST_PERIOD_KEY, key.toString())
        bus.emit(ChangeKind.BALANCE)
    }

    /**
     * The reset period or week start changed. Period keys of the two settings overlap (a weekly
     * key is the daily key of its first day), so the old period is closed with a RESET entry and
     * its balance carried into the new period with an ADJUST entry, after clearing anything the
     * new key already summed. The balance is unchanged; the next reset follows the new setting.
     */
    suspend fun onPeriodChanged(old: Settings, new: Settings, now: Long = clock.now()): Unit = tx.run {
        val oldKey = periodKey(old, now)
        val newKey = periodKey(new, now)
        val oldRolling = old.resetPeriod == ResetPeriod.ROLLING
        val newRolling = new.resetPeriod == ResetPeriod.ROLLING
        if (oldRolling || newRolling) {
            // Moving to or from rolling: carry the live balance across as one entry in the new mode.
            val live = if (oldRolling) lots(now) else emptyList()
            val carry = if (oldRolling) ExpiryLedger.balance(live, now) else ledger.sum(oldKey)
            if (oldRolling) live.forEach { ledger.setRemaining(it.id, 0) }
            else if (carry != 0) ledger.insert(LedgerEntryEntity(at = now, kind = LedgerKind.RESET, minutes = -carry, periodKey = oldKey))
            if (!newRolling) {
                val stale = ledger.sum(newKey)
                if (stale != 0) ledger.insert(LedgerEntryEntity(at = now, kind = LedgerKind.RESET, minutes = -stale, periodKey = newKey))
            }
            if (carry > 0) {
                ledger.insert(LedgerEntryEntity(at = now, kind = LedgerKind.ADJUST, minutes = carry, periodKey = newKey,
                    expiresAt = if (newRolling) now + new.rollingExpiryHours * HOUR else null,
                    remaining = if (newRolling) carry else 0))
            }
        } else if (oldKey != newKey) {
            val carry = ledger.sum(oldKey)
            if (carry != 0) {
                ledger.insert(LedgerEntryEntity(at = now, kind = LedgerKind.RESET, minutes = -carry, periodKey = oldKey))
            }
            val stale = ledger.sum(newKey)
            if (stale != 0) {
                ledger.insert(LedgerEntryEntity(at = now, kind = LedgerKind.RESET, minutes = -stale, periodKey = newKey))
            }
            if (carry != 0) {
                ledger.insert(LedgerEntryEntity(at = now, kind = LedgerKind.ADJUST, minutes = carry, periodKey = newKey))
            }
        }
        settings.rawPut(LAST_PERIOD_KEY, newKey.toString())
        bus.emit(ChangeKind.BALANCE)
        bus.emit(ChangeKind.SETTINGS)
    }

    /** Next balance reset under the current settings. */
    suspend fun nextResetAt(now: Long = clock.now()): Long? = resetsAt(settings.current(), now)

    fun periodKey(s: Settings, now: Long): Long =
        Periods.start(clock.localDate(now), s.resetPeriod, s.weekStartsOn).toEpochDay()

    private fun resetsAt(s: Settings, now: Long): Long? =
        if (s.resetPeriod == ResetPeriod.ROLLING) null else
        clock.startOfDay(Periods.nextStart(clock.localDate(now), s.resetPeriod, s.weekStartsOn))

    companion object {
        const val MINUTE = 60_000L
        const val HOUR = 3_600_000L
        private const val TICK_MS = 30_000L
        private const val LAST_PERIOD_KEY = "bank.lastPeriodKey"

        fun bedtimeWindow(s: Settings): DailyWindow? =
            if (s.bedtimeEnabled) DailyWindow(s.bedtimeStartMin, s.bedtimeEndMin) else null
    }
}
