package com.earnd.app.domain

import com.earnd.app.blocking.EssentialApps
import com.earnd.app.data.SettingsRepository
import com.earnd.app.data.dao.AppRuleDao
import com.earnd.app.data.dao.SessionDao
import com.earnd.app.data.entity.AppRule
import com.earnd.app.data.entity.AppRuleEntity
import com.earnd.app.data.entity.SessionReason
import com.earnd.app.focus.FocusRules

sealed interface BlockDecision {
    data object Allow : BlockDecision
    data class Block(val cause: BlockCause, val until: Long?) : BlockDecision
    /** Open because the app is metered and the balance is positive; the guard charges [ratePct] of foreground time. */
    data class Metered(val ratePct: Int) : BlockDecision
}

/** FOCUS: a timed focus habit is running; the guard shows the focus timer instead of the lock screen. */
enum class BlockCause { NO_BALANCE, BEDTIME, HARD_MODE, FOCUS }

/** Everything [BlockRules.decide] needs, already resolved. */
data class BlockFacts(
    val blockingEnabled: Boolean,
    val onVacation: Boolean,
    val essential: Boolean,
    val leisure: Boolean,
    val inBedtime: Boolean,
    val bedtimeEndsAt: Long?,
    val graceActive: Boolean,
    val sessionActive: Boolean,
    val allDoneUnlock: Boolean,
    val nothingScheduled: Boolean,
    /** Segment mode: every habit of the current day segment is done. */
    val segmentUnlock: Boolean = false,
    /** Metered charging rate of the app (null = FULL, or hard mode, which disables metering). */
    val meteredPct: Int? = null,
    val balancePositive: Boolean = false,
)

/** Pure decision table; the order here is the contract. */
object BlockRules {
    fun decide(f: BlockFacts): BlockDecision = when {
        !f.blockingEnabled || f.onVacation -> BlockDecision.Allow
        f.essential -> BlockDecision.Allow
        !f.leisure -> BlockDecision.Allow
        f.inBedtime && !f.graceActive -> BlockDecision.Block(BlockCause.BEDTIME, f.bedtimeEndsAt)
        f.sessionActive -> BlockDecision.Allow
        f.meteredPct != null && f.balancePositive -> BlockDecision.Metered(f.meteredPct)
        f.allDoneUnlock -> BlockDecision.Allow
        f.segmentUnlock -> BlockDecision.Allow
        f.nothingScheduled -> BlockDecision.Allow
        else -> BlockDecision.Block(BlockCause.NO_BALANCE, null)
    }

    /** Leisure match on package, or on website host (exact or any subdomain). */
    fun isLeisure(rules: List<AppRuleEntity>, pkg: String, urlHost: String?): Boolean {
        val host = urlHost?.lowercase()?.removePrefix("www.")
        return rules.any { r ->
            r.rule == AppRule.LEISURE && if (r.isWebsite) {
                host != null && r.key.lowercase().removePrefix("www.").let { host == it || host.endsWith(".$it") }
            } else r.key == pkg
        }
    }

    /** Metered rate of a blocked app (packages only); null = FULL or not a blocked app. */
    fun meterRate(rules: List<AppRuleEntity>, pkg: String): Int? =
        rules.firstOrNull { it.rule == AppRule.LEISURE && !it.isWebsite && it.key == pkg }?.meterPct

    /** User-extended whitelist: packages marked ALLOWED. */
    fun isUserAllowed(rules: List<AppRuleEntity>, pkg: String): Boolean =
        rules.any { it.rule == AppRule.ALLOWED && !it.isWebsite && it.key == pkg }
}

class BlockPolicy(
    private val rules: AppRuleDao,
    private val sessions: SessionDao,
    private val habits: HabitService,
    private val settings: SettingsRepository,
    private val essentials: EssentialApps,
    private val clock: Clock = SystemTimeClock,
    /** True while a focus session runs at [now]; then only focus-safe apps stay open (sessions are suspended). */
    private val focusActive: suspend (now: Long) -> Boolean = { false },
    private val ownPackage: String = "com.earnd.app",
    /** Spendable balance in minutes at [now]; only consulted for metered apps. */
    private val balance: suspend (now: Long) -> Int = { 0 },
) {
    /** [pkg] is the foreground package; [url] is the host (or URL) from a browser bar, or null. */
    suspend fun decide(pkg: String, url: String?, now: Long): BlockDecision {
        if (focusActive(now)) {
            val ok = FocusRules.allowed(pkg, ownPackage, essentials.isEssential(pkg), essentials.isLauncher(pkg),
                BlockRules.isUserAllowed(rules.all(), pkg))
            return if (ok) BlockDecision.Allow else BlockDecision.Block(BlockCause.FOCUS, null)
        }
        val s = settings.current()
        val today = clock.localDate(now)
        val all = rules.all()
        val zone = clock.zone()
        val bedtime = TimeBank.bedtimeWindow(s)
        val session = sessions.active()?.takeIf { it.plannedEndAt > now && it.startedAt <= now }
        // Cheap facts first; habit lookups only matter at the end of the table.
        val metered = if (s.hardMode) null else BlockRules.meterRate(all, pkg)
        val f = BlockFacts(
            blockingEnabled = s.blockingEnabled,
            onVacation = s.onVacation(today.toEpochDay()),
            essential = essentials.isEssential(pkg) || BlockRules.isUserAllowed(all, pkg),
            leisure = BlockRules.isLeisure(all, pkg, url?.let(::hostOf)),
            inBedtime = bedtime?.contains(now, zone) == true,
            bedtimeEndsAt = bedtime?.endOf(now, zone),
            graceActive = session?.reason == SessionReason.BEDTIME_GRACE,
            sessionActive = session != null,
            allDoneUnlock = false,
            nothingScheduled = false,
            meteredPct = metered,
            balancePositive = metered != null && session == null && balance(now) > 0,
        )
        val first = BlockRules.decide(f)
        if (first !is BlockDecision.Block || first.cause != BlockCause.NO_BALANCE) return first
        return BlockRules.decide(f.copy(
            allDoneUnlock = s.allDoneUnlock && habits.allDone(today),
            nothingScheduled = !habits.anythingScheduled(today),
            segmentUnlock = s.segmentMode &&
                habits.segmentDone(today, Segments.current(clock.minuteOfDay(now), s)),
        ))
    }

    private fun hostOf(url: String): String =
        url.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore(':')
}
