package com.earnd.app.data

import com.earnd.app.data.dao.SettingDao
import com.earnd.app.data.entity.SettingEntity
import com.earnd.app.domain.ChangeBus
import com.earnd.app.domain.ChangeKind
import com.earnd.app.domain.Clock
import com.earnd.app.domain.SystemTimeClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Typed facade over the `setting` table. Settings live under "s.", a queued hard-mode
 * weakening under "pending.", and internal bookkeeping values under "raw.".
 */
class SettingsRepository(
    private val dao: SettingDao,
    private val clock: Clock = SystemTimeClock,
    private val bus: ChangeBus? = null,
) {
    val settings: Flow<Settings> = dao.observeAll().map { rows -> decode(rows) }

    /** Current settings; applies a queued weakening first if its cooldown has passed. */
    suspend fun current(): Settings {
        applyDueWeakening(clock.now())
        return decode(dao.all())
    }

    /**
     * Called after the reset period or week start changed, with the old and new settings.
     * TimeBank registers itself here to close the old period.
     */
    @Volatile var periodChangeListener: (suspend (old: Settings, new: Settings) -> Unit)? = null

    suspend fun update(transform: (Settings) -> Settings) {
        val old = decode(dao.all())
        val new = transform(old)
        write(PREFIX, new)
        notifyPeriodChange(old, new)
        bus?.emit(ChangeKind.SETTINGS)
    }

    private suspend fun notifyPeriodChange(old: Settings, new: Settings) {
        if (old.resetPeriod != new.resetPeriod || old.weekStartsOn != new.weekStartsOn) {
            periodChangeListener?.invoke(old, new)
        }
    }

    /**
     * For changes that weaken blocking. Outside hard mode it applies immediately; in hard mode
     * the result is queued and applied after `weakenCooldownMin`. Returns when it applies.
     */
    suspend fun requestWeakening(transform: (Settings) -> Settings): Long {
        val now = clock.now()
        val cur = current()
        if (!cur.hardMode) {
            update(transform)
            return now
        }
        // Queue only the keys this change touches, merged with an earlier queued change: applying later then
        // overlays them on the live settings and cannot undo anything tightened in between.
        val before = SettingsCodec.encode(cur)
        val changes = SettingsCodec.encode(transform(cur)).filter { (k, v) -> before[k] != v }
        val earlier = pendingWeakeningAt()
        val carried = if (earlier != null) pendingChanges() else emptyMap()
        clearPendingRows()
        (carried + changes).forEach { (k, v) -> dao.put(SettingEntity(PENDING + k, v)) }
        val appliesAt = maxOf(now + cur.weakenCooldownMin * 60_000L, earlier ?: 0L)
        dao.put(SettingEntity(PENDING_AT, appliesAt.toString()))
        return appliesAt
    }

    private suspend fun pendingChanges(): Map<String, String> = dao.all().filter { it.key.startsWith(PENDING) }
        .associate { it.key.removePrefix(PENDING) to it.value }

    private suspend fun clearPendingRows() = dao.all().filter { it.key.startsWith(PENDING) }.forEach { dao.delete(it.key) }

    /** When a queued weakening will apply, or null if none is queued. */
    suspend fun pendingWeakeningAt(): Long? = dao.get(PENDING_AT)?.toLongOrNull()

    suspend fun cancelPendingWeakening() {
        clearPendingRows()
        dao.put(SettingEntity(PENDING_AT, ""))
    }

    suspend fun applyDueWeakening(now: Long): Boolean {
        val at = pendingWeakeningAt() ?: return false
        if (now < at) return false
        val old = decode(dao.all())
        val new = SettingsCodec.decode(SettingsCodec.encode(old) + pendingChanges())
        write(PREFIX, new)
        cancelPendingWeakening()
        notifyPeriodChange(old, new)
        bus?.emit(ChangeKind.SETTINGS)
        return true
    }

    suspend fun rawGet(key: String): String? = dao.get(RAW + key)
    suspend fun rawPut(key: String, value: String) = dao.put(SettingEntity(RAW + key, value))

    private suspend fun write(prefix: String, s: Settings) {
        SettingsCodec.encode(s).forEach { (k, v) -> dao.put(SettingEntity(prefix + k, v)) }
    }

    private fun decode(rows: List<SettingEntity>): Settings = SettingsCodec.decode(
        rows.filter { it.key.startsWith(PREFIX) }.associate { it.key.removePrefix(PREFIX) to it.value },
    )

    private companion object {
        const val PREFIX = "s."
        const val PENDING = "pending."
        const val PENDING_AT = "pendingAt"
        const val RAW = "raw."
    }
}
