package com.earnd.app.alarms

import com.earnd.app.data.entity.NagMode
import com.earnd.app.domain.DailyWindow
import com.earnd.app.domain.ForegroundRules
import java.time.Instant
import java.time.ZoneId

/** Why a nag pass is being considered. Non-timer triggers never start a full-screen ring. */
enum class NagTrigger { TIMER, APP_SWITCH, SCREEN_ON, BLOCK_END, FOCUS_END, BALANCE_ZERO, DUE_NEAR }

/** Everything the decision needs, observable without blocking the caller. */
data class NagSignals(
    val now: Long,
    val dueAt: Long,
    val zone: ZoneId,
    val nagCount: Int,
    val lastNagAt: Long?,
    val nextNagAt: Long?,
    val mode: NagMode,
    val maxCount: Int?,
    val quietActive: Boolean,
    val quietEndsAt: Long?,
    val trigger: NagTrigger,
    val foregroundKind: ForegroundRules.Kind?,
    val interactive: Boolean,
    val inCall: Boolean,
    val focusRunning: Boolean,
    val selfForeground: Boolean,
    val balanceZero: Boolean,
    val nagsToday: Int,
    val capPerDay: Int?,
    val onVacation: Boolean,
    val progressedSinceLastNag: Boolean,
    /** Optional: lets a deferral skip a quiet window starting within the next ten minutes. */
    val quietWindow: DailyWindow? = null,
)

/** Ambient state recorded by the guard and refreshed by each nag pass; no sensors of its own. */
data class NagAmbient(
    val trigger: NagTrigger = NagTrigger.TIMER,
    val foregroundKind: ForegroundRules.Kind? = null,
    val interactive: Boolean = true,
    val inCall: Boolean = false,
    val selfForeground: Boolean = false,
    val focusRunning: Boolean = false,
    val balanceZero: Boolean = false,
    val progressedSinceLastNag: Boolean = false,
)

sealed interface NagDecision {
    data object None : NagDecision
    data class Defer(val until: Long) : NagDecision
    data object CapReached : NagDecision
    data class Prompt(val variant: Int) : NagDecision
}

private const val DAY_MS = 24 * 60 * 60_000L
private const val DEFER_MS = 10 * 60_000L

/** Pure, deterministic gate. Checks are ordered: terminal rules first, then receptivity, then copy. */
fun decide(s: NagSignals): NagDecision {
    if (s.mode == NagMode.OFF || (s.maxCount != null && s.nagCount >= s.maxCount)) return NagDecision.None
    if (s.onVacation || s.quietActive) return NagDecision.Defer(s.quietEndsAt ?: s.now + DAY_MS)
    if (s.capPerDay != null && s.nagsToday >= s.capPerDay) return NagDecision.CapReached
    if (s.foregroundKind == ForegroundRules.Kind.COVERING || !s.interactive || s.inCall) {
        return NagDecision.Defer(deferUntil(s))
    }
    if (s.selfForeground || s.focusRunning || s.progressedSinceLastNag) return NagDecision.None
    if (s.balanceZero && s.trigger != NagTrigger.BLOCK_END && s.trigger != NagTrigger.BALANCE_ZERO) {
        return NagDecision.Defer(deferUntil(s))
    }
    if (s.trigger == NagTrigger.TIMER && s.dueAt - s.now in 0..DEFER_MS) return NagDecision.Prompt(0)
    val day = Instant.ofEpochMilli(s.dueAt).atZone(s.zone).toLocalDate().toEpochDay()
    return NagDecision.Prompt(Math.floorMod(s.nagCount + day, 3))
}

private fun deferUntil(s: NagSignals): Long {
    val at = s.now + DEFER_MS
    return s.quietWindow?.let { NagSchedule.shiftOutOfQuiet(at, s.zone, it) } ?: at
}
