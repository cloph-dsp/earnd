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

    suspend fun update(transform: (Settings) -> Settings) {
        write(PREFIX, transform(decode(dao.all())))
        bus?.emit(ChangeKind.SETTINGS)
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
        val appliesAt = now + cur.weakenCooldownMin * 60_000L
        write(PENDING, transform(cur))
        dao.put(SettingEntity(PENDING_AT, appliesAt.toString()))
        return appliesAt
    }

    /** When a queued weakening will apply, or null if none is queued. */
    suspend fun pendingWeakeningAt(): Long? = dao.get(PENDING_AT)?.toLongOrNull()

    suspend fun cancelPendingWeakening() = dao.put(SettingEntity(PENDING_AT, ""))

    suspend fun applyDueWeakening(now: Long): Boolean {
        val at = pendingWeakeningAt() ?: return false
        if (now < at) return false
        val pending = dao.all().filter { it.key.startsWith(PENDING) }
            .associate { it.key.removePrefix(PENDING) to it.value }
        write(PREFIX, SettingsCodec.decode(pending))
        cancelPendingWeakening()
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
