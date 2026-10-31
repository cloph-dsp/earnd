package com.earnd.app.alarms

import com.earnd.app.data.Settings
import com.earnd.app.data.SettingsRepository
import com.earnd.app.data.dao.AlarmConfigDao
import com.earnd.app.data.dao.NagStateDao
import com.earnd.app.data.entity.AlarmConfigEntity
import com.earnd.app.data.entity.NagStateEntity
import com.earnd.app.data.entity.OwnerKind
import com.earnd.app.domain.Clock
import com.earnd.app.domain.DailyWindow

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
    @Volatile private var quiet: DailyWindow = quietOf(Settings())

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

    /** Alarm fired: bumps every item that is due for a nag; returns all pending, most-nagged first. */
    suspend fun onFired(now: Long): List<PendingItem> {
        refreshQuiet()
        for (s in nags.all()) {
            val at = s.nextNagAt ?: continue
            if (at > now) continue
            if (s.lastNagAt != null && now - s.lastNagAt < 30_000L) continue
            val bumped = s.copy(nagCount = s.nagCount + 1, lastNagAt = now)
            nags.upsert(bumped.copy(nextNagAt = nextNagAt(bumped, configs.get(s.ownerKind, s.ownerId), now)))
        }
        return pending(now)
    }

    /** Full-screen alarm snooze. Returns the new nag time, or null if the snooze limit is reached. */
    suspend fun snooze(ownerKind: OwnerKind, ownerId: Long, now: Long): Long? {
        val s = nags.get(ownerKind, ownerId) ?: return null
        if (!NagSchedule.canSnooze(s, configs.get(ownerKind, ownerId))) return null
        val at = now + NagSchedule.SNOOZE_MIN * 60_000L
        nags.upsert(s.copy(snoozeCount = s.snoozeCount + 1, nextNagAt = at))
        return at
    }

    suspend fun resolve(ownerKind: OwnerKind, ownerId: Long) = nags.delete(ownerKind, ownerId)

    suspend fun pending(now: Long): List<PendingItem> =
        NagSchedule.order(nags.all().filter { it.dueAt <= now })
            .map { PendingItem(it.ownerKind, it.ownerId, labels(it.ownerKind, it.ownerId), it.nagCount) }

    /** Earliest scheduled nag across all items, for arming one exact alarm. */
    suspend fun earliestNagAt(): Long? = nags.all().mapNotNull { it.nextNagAt }.minOrNull()

    private suspend fun refreshQuiet() {
        quiet = quietOf(settings.current())
    }

    private fun quietOf(s: Settings) = DailyWindow(s.quietStartMin, s.quietEndMin)
}
