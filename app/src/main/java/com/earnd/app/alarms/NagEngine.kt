package com.earnd.app.alarms

import com.earnd.app.data.Settings
import com.earnd.app.data.SettingsRepository
import com.earnd.app.data.dao.AlarmConfigDao
import com.earnd.app.data.dao.NagStateDao
import com.earnd.app.data.entity.AlarmConfigEntity
import com.earnd.app.data.entity.NagMode
import com.earnd.app.data.entity.NagStateEntity
import com.earnd.app.data.entity.OwnerKind
import com.earnd.app.domain.Clock
import com.earnd.app.domain.DailyWindow
import com.earnd.app.domain.localDate

data class PendingItem(val ownerKind: OwnerKind, val ownerId: Long, val label: String, val nagCount: Int)

/**
 * Tracks overdue items and when to nag about them. Android-free; [AlarmPlanner] turns
 * `nextNagAt` values into exact alarms. [labels] resolves a display label for an item.
 */
class NagEngine(
    private val nags: NagStateDao,
    private val configs: AlarmConfigDao,
    private val settings: SettingsRepository,
    private val clock: Clock,
    private val labels: suspend (OwnerKind, Long) -> String = { k, id -> "${k.name.lowercase()} $id" },
) {
    @Volatile private var quiet: (java.time.LocalDate) -> DailyWindow = quietOf(Settings())
    /** Written by the guard and by each nag pass; never by [NagDecision]. */
    @Volatile var ambient: NagAmbient = NagAmbient()

    fun nextNagAt(state: NagStateEntity, cfg: AlarmConfigEntity?, now: Long): Long? =
        NagSchedule.next(state, cfg, now, clock.zone(), quiet)

    /** An item became due and is not done yet. Keeps counts if it was already tracked. */
    suspend fun onDue(ownerKind: OwnerKind, ownerId: Long, dueAt: Long) {
        refreshQuiet()
        val existing = nags.get(ownerKind, ownerId)
        val base = if (existing != null && existing.dueAt == dueAt) existing
        else NagStateEntity(ownerKind, ownerId, dueAt)
        val cfg = configs.get(ownerKind, ownerId)
        nags.upsert(base.copy(nextNagAt = nextNagAt(base, cfg, clock.now())))
    }

    /** Alarm fired: gates each due item, bumps the ones that get a prompt, returns all pending. */
    suspend fun onFired(now: Long, trigger: NagTrigger = NagTrigger.TIMER): List<PendingItem> {
        val settingsNow = refreshQuiet()
        val zone = clock.zone()
        val window = quiet(clock.localDate(now))
        val quietActive = window.contains(now, zone)
        val quietEndsAt = window.endOf(now, zone)
        var todayCount = nagsToday(now)
        for (s in nags.all()) {
            val at = s.nextNagAt ?: continue
            if (at > now) continue
            if (s.lastNagAt != null && now - s.lastNagAt < 30_000L) continue
            val cfg = configs.get(s.ownerKind, s.ownerId)
            val decision = decide(NagSignals(
                now = now, dueAt = s.dueAt, zone = zone, nagCount = s.nagCount,
                lastNagAt = s.lastNagAt, nextNagAt = s.nextNagAt,
                mode = cfg?.nagMode ?: NagMode.PROGRESSIVE, maxCount = cfg?.nagMaxCount,
                quietActive = quietActive, quietEndsAt = quietEndsAt, trigger = trigger,
                foregroundKind = ambient.foregroundKind, interactive = ambient.interactive, inCall = ambient.inCall,
                focusRunning = ambient.focusRunning, selfForeground = ambient.selfForeground,
                balanceZero = ambient.balanceZero, nagsToday = todayCount, capPerDay = settingsNow.nagCapPerDay,
                onVacation = settingsNow.onVacation(clock.localDate(now).toEpochDay()),
                progressedSinceLastNag = ambient.progressedSinceLastNag, quietWindow = window,
            ))
            when (decision) {
                is NagDecision.Prompt -> {
                    val bumped = s.copy(nagCount = s.nagCount + 1, lastNagAt = now)
                    nags.upsert(bumped.copy(nextNagAt = nextNagAt(bumped, cfg, now)))
                    todayCount++
                    recordNag(now, todayCount)
                }
                is NagDecision.Defer -> nags.upsert(s.copy(nextNagAt = maxOf(decision.until, now + 60_000L)))
                NagDecision.CapReached -> {
                    // Tomorrow's daily slot: never leave nextNagAt at now (spin) or null (Stop-only).
                    val slot = NagSchedule.nextDigestSlot(now + 1, zone, { d -> quiet(d) }, onlyAfterLast = now)
                    nags.upsert(s.copy(nextNagAt = maxOf(slot, now + 60_000L)))
                }
                NagDecision.None -> nags.upsert(s.copy(nextNagAt = nextNagAt(s, cfg, now)?.let { maxOf(it, now + 60_000L) }))
            }
        }
        return pending(now)
    }

    /** How many real prompts went out today. The counter is only written on a prompt. */
    suspend fun nagsToday(now: Long): Int {
        val day = clock.localDate(now).toEpochDay().toString()
        return if (settings.rawGet("nag.day") == day) settings.rawGet("nag.count")?.toIntOrNull() ?: 0 else 0
    }

    private suspend fun recordNag(now: Long, count: Int) {
        val day = clock.localDate(now).toEpochDay().toString()
        if (settings.rawGet("nag.day") != day) settings.rawPut("nag.day", day)
        settings.rawPut("nag.count", count.toString())
        NagHistory.recordPrompt(now, 1)
    }

    /** Full-screen alarm snooze. Returns the new nag time, or null if the snooze limit is reached. */
    suspend fun snooze(ownerKind: OwnerKind, ownerId: Long, now: Long, minutes: Int = NagSchedule.SNOOZE_MIN): Long? {
        val s = nags.get(ownerKind, ownerId) ?: return null
        if (!NagSchedule.canSnooze(s, configs.get(ownerKind, ownerId))) return null
        val at = now + minutes * 60_000L
        nags.upsert(s.copy(snoozeCount = s.snoozeCount + 1, nextNagAt = at))
        return at
    }

    suspend fun resolve(ownerKind: OwnerKind, ownerId: Long) = nags.delete(ownerKind, ownerId)

    /**
     * Partial progress on a still-pending item (e.g. 6 of 10 glasses): the escalation starts over from [at], so the next
     * nag is a first, gentle one instead of the 5th.
     */
    suspend fun restart(ownerKind: OwnerKind, ownerId: Long, at: Long) {
        refreshQuiet()
        val s = nags.get(ownerKind, ownerId) ?: return
        val base = s.copy(nagCount = 0, snoozeCount = 0, lastNagAt = at)
        nags.upsert(base.copy(nextNagAt = nextNagAt(base, configs.get(ownerKind, ownerId), clock.now())))
    }

    /**
     * Stop: the item stays pending (listed, not done) but stops nagging and ringing until its next
     * due/reminder time creates a fresh state.
     */
    suspend fun silence(ownerKind: OwnerKind, ownerId: Long) {
        val s = nags.get(ownerKind, ownerId) ?: return
        nags.upsert(s.copy(nextNagAt = null))
    }

    suspend fun pending(now: Long): List<PendingItem> =
        NagSchedule.order(nags.all().filter { it.dueAt <= now })
            .map { PendingItem(it.ownerKind, it.ownerId, labels(it.ownerKind, it.ownerId), it.nagCount) }

    /** Earliest scheduled nag across all items, for arming one exact alarm. */
    suspend fun earliestNagAt(): Long? = nags.all().mapNotNull { it.nextNagAt }.minOrNull()

    private suspend fun refreshQuiet(): Settings {
        val s = settings.current()
        quiet = quietOf(s)
        return s
    }

    private fun quietOf(s: Settings): (java.time.LocalDate) -> DailyWindow = { day -> DayStart.quietWindow(s, day) }
}
