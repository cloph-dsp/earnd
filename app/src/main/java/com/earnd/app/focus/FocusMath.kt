package com.earnd.app.focus

import com.earnd.app.data.entity.FocusSessionEntity
import com.earnd.app.data.entity.FocusState

/** Pure timer math for a focus session. All times are epoch millis. */
object FocusMath {
    val MINUTES_RANGE = 1..240

    fun endAt(s: FocusSessionEntity): Long = s.startedAt + s.durationMs

    /** Time left, never negative; a non-active session has none. */
    fun remainingMs(s: FocusSessionEntity, now: Long): Long =
        if (s.state != FocusState.ACTIVE || s.stopwatch) 0L else (endAt(s) - now).coerceIn(0L, s.durationMs)

    /** Active and the full duration has elapsed: ready to be credited. */
    fun isDue(s: FocusSessionEntity, now: Long): Boolean = s.state == FocusState.ACTIVE && !s.stopwatch && now >= endAt(s)

    /** Time a session has run (a finished stopwatch keeps its recorded length). */
    fun elapsedMs(s: FocusSessionEntity, now: Long): Long =
        if (s.stopwatch && s.state != FocusState.ACTIVE) s.durationMs else (now - s.startedAt).coerceAtLeast(0L)

    /** Active and still counting down. */
    fun isRunning(s: FocusSessionEntity?, now: Long): Boolean =
        s != null && s.state == FocusState.ACTIVE && (s.stopwatch || now < endAt(s))

    /** 0..1 elapsed fraction, for the progress ring. */
    fun progress(s: FocusSessionEntity, now: Long): Float =
        if (s.stopwatch) ((elapsedMs(s, now) % 3_600_000L) / 3_600_000f)
        else if (s.durationMs <= 0) 1f else (1f - remainingMs(s, now).toFloat() / s.durationMs).coerceIn(0f, 1f)

    /** Whole minutes of progress that a cancel would throw away (no partial credit). */
    fun minutesLost(s: FocusSessionEntity, now: Long): Int =
        ((if (s.stopwatch) elapsedMs(s, now) else s.durationMs - remainingMs(s, now)) / 60_000L).toInt()

    /** mm:ss, or h:mm:ss from one hour up. Rounds up so the last second shows 00:01, not 00:00. */
    fun clock(ms: Long): String {
        val total = (ms + 999) / 1000
        val h = total / 3600; val m = (total % 3600) / 60; val sec = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
    }
}

/** What may stay in front while a focus session runs; everything else goes back to the timer. */
object FocusRules {
    /** Essential system packages that still redirect during focus (they are escape hatches, not needs). */
    val REDIRECT_ANYWAY = setOf(
        "com.android.settings",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
    )

    /**
     * Allowed: Earnd itself, the user's always-open apps, and essentials (dialer and in-call UI, SMS,
     * emergency, maps, system UI) except the home launcher and [REDIRECT_ANYWAY].
     */
    fun allowed(pkg: String, ownPkg: String, essential: Boolean, launcher: Boolean, userAllowed: Boolean): Boolean = when {
        pkg == ownPkg -> true
        userAllowed -> true
        launcher || pkg in REDIRECT_ANYWAY -> false
        else -> essential
    }
}
