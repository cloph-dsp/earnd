package com.earnd.app.domain

/** A window on screen as the guard sees it (accessibility windows, reduced to what the rules need). */
data class WinInfo(val pkg: String, val pip: Boolean, val application: Boolean = true)

/** A leisure target that would count: rule [key], package in front, [ratePct]. */
data class Counted(val key: String, val pkg: String, val ratePct: Int, val pip: Boolean = false, val primary: Boolean = false)

/** Pure rules for what counts as "using" a leisure app. */
object CountRules {
    const val MODE_IN_CALL = 2
    const val MODE_IN_COMMUNICATION = 3

    /** Phone call in progress: telephony state not idle (0), or the audio mode is a call/VoIP mode. */
    fun inCall(callState: Int?, audioMode: Int?): Boolean =
        (callState != null && callState != 0) || audioMode == MODE_IN_CALL || audioMode == MODE_IN_COMMUNICATION

    /** Time only counts while the screen is on and unlocked and no call is going on. */
    fun active(interactive: Boolean, keyguardLocked: Boolean, inCall: Boolean): Boolean =
        interactive && !keyguardLocked && !inCall

    /**
     * Leisure windows that are visible besides (or including) the foreground app: a picture-in-picture window of a
     * leisure package counts, and so does any leisure application window in split-screen. System windows (shade,
     * keyboard) are not application windows and never matter. [rateOf] returns the rule for a package or null.
     */
    fun visibleLeisure(wins: List<WinInfo>, rateOf: (String) -> Pair<String, Int>?): List<Counted> =
        wins.filter { it.application }.mapNotNull { w ->
            rateOf(w.pkg)?.let { (key, rate) -> Counted(key, w.pkg, rate, pip = w.pip) }
        }.distinctBy { it.key }

    /** One thing is charged at a time: the highest rate wins, a tie goes to the primary (foreground) target. */
    fun pick(primary: Counted?, extras: List<Counted>): Counted? =
        (listOfNotNull(primary?.copy(primary = true)) + extras.filter { e -> primary == null || e.key != primary.key })
            .maxWithOrNull(compareBy<Counted>({ it.ratePct }, { it.primary }))
}

/**
 * Counting grants: the user accepted the gate for a rule key; leaving the app keeps the grant for [graceMs], so
 * returning inside the window needs no new confirmation.
 */
class Grants(private val graceMs: Long = GRACE_MS) {
    /** key -> time the user left it (null while it is in front). */
    private val left = HashMap<String, Long?>()

    @Synchronized fun grant(key: String, now: Long) { left[key] = now }

    /** True when [key] holds a grant that has not run out at [now]. */
    @Synchronized fun active(key: String, now: Long): Boolean {
        if (!left.containsKey(key)) return false
        val at = left[key] ?: return true
        if (now - at <= graceMs) return true
        left.remove(key)
        return false
    }

    /** The key is in front: while it is, the grant does not run down. */
    @Synchronized fun enter(key: String) { if (left.containsKey(key)) left[key] = null }

    /** The key left the foreground (other app, screen off). Starts the grace window. */
    @Synchronized fun leave(key: String, now: Long) { if (left.containsKey(key) && left[key] == null) left[key] = now }

    @Synchronized fun clear() = left.clear()

    @Synchronized fun describe(now: Long): String =
        left.entries.joinToString { (k, at) -> "$k=" + if (at == null) "front" else "left ${(now - at) / 1000}s ago" }

    companion object { const val GRACE_MS = 120_000L }
}

/** Pure helpers for the on-screen counter. */
object OverlayMath {
    const val LOW_MS = 120_000L
    const val VIBRATE_AT_MS = 60_000L

    /** "23:41" (minutes:seconds, rounded up to the second), or "1:05:00" from an hour up. */
    fun format(ms: Long): String {
        val total = (ms.coerceAtLeast(0) + 999) / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    /** Short rate mark: nothing at 100 %, a vulgar fraction at 50/25. */
    fun rateMark(pct: Int): String = when (pct) {
        100 -> ""
        50 -> "½"
        25 -> "¼"
        else -> "$pct%"
    }

    fun text(ms: Long, pct: Int): String = format(ms) + rateMark(pct).let { if (it.isEmpty()) "" else " $it" }

    fun isLow(ms: Long): Boolean = ms <= LOW_MS

    /** True once as the countdown crosses the last minute. */
    fun crossedVibrate(prevMs: Long?, ms: Long): Boolean = prevMs != null && prevMs > VIBRATE_AT_MS && ms <= VIBRATE_AT_MS

    /** Edge the overlay snaps to: true = left. */
    fun snapLeft(centerX: Int, screenW: Int): Boolean = centerX < screenW / 2

    fun clampY(y: Int, viewH: Int, screenH: Int): Int = y.coerceIn(0, (screenH - viewH).coerceAtLeast(0))
}
