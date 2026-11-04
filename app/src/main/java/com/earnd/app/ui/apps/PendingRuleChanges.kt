package com.earnd.app.ui.apps

import com.earnd.app.ServiceLocator
import com.earnd.app.data.entity.AppRule
import com.earnd.app.data.entity.AppRuleEntity
import com.earnd.app.domain.ChangeKind
import com.earnd.app.domain.Charging

/**
 * Block-list edits. Changes that make blocking stronger apply at once; changes that weaken it
 * (unlocking a leisure app or site, marking an app always-available) wait out the hard-mode
 * cooldown. Queued edits are kept in raw settings as "key|rule|isWebsite|label|appliesAt" lines.
 */
object PendingRuleChanges {
    private const val KEY = "ui.pendingRuleChanges"
    private const val REMOVE = "-"
    private const val CHARGE = "CHARGE:"

    data class Pending(val key: String, val rule: String, val isWebsite: Boolean, val label: String, val appliesAt: Long)

    private val dao get() = ServiceLocator.db.appRuleDao()

    /** Mark as leisure (always strengthening). An existing charging rate is kept. */
    suspend fun addLeisure(key: String, label: String, isWebsite: Boolean) {
        dropPending(key)
        dao.upsert(AppRuleEntity(key, AppRule.LEISURE, isWebsite, label, meterPct = dao.get(key)?.takeIf { it.rule == AppRule.LEISURE }?.meterPct ?: Charging.DEFAULT_RATE))
        ServiceLocator.changeBus.emit(ChangeKind.BLOCKLIST)
    }

    /** Remove a leisure rule. Returns when it applies (now, or after the cooldown). */
    suspend fun removeLeisure(key: String, label: String, isWebsite: Boolean): Long =
        weaken(Pending(key, REMOVE, isWebsite, label, 0))

    /**
     * Sets how a blocked app is charged ([pct] null = FULL). Stricter or equal changes apply at once; loosening
     * (FULL -> METERED, lower rate) is a weakening: it waits out the hard-mode cooldown like the other weakenings
     * (callers put the puzzle gate in front). Returns when it applies.
     */
    suspend fun setCharging(key: String, label: String, pct: Int?): Long {
        val cur = dao.get(key)?.takeIf { it.rule == AppRule.LEISURE } ?: return ServiceLocator.clock.now()
        if (Charging.loosens(cur.meterPct, pct)) return weaken(Pending(key, CHARGE + (pct ?: Charging.DEFAULT_RATE).toString(), false, label, 0))
        read().firstOrNull { it.key == key && it.rule.startsWith(CHARGE) }?.let { dropPending(key) }
        dao.upsert(cur.copy(meterPct = pct))
        ServiceLocator.changeBus.emit(ChangeKind.BLOCKLIST)
        return ServiceLocator.clock.now()
    }

    /** Mark an app as always available. */
    suspend fun allow(key: String, label: String): Long = weaken(Pending(key, AppRule.ALLOWED.name, false, label, 0))

    /** Undo always-available (strengthening). */
    suspend fun disallow(key: String) {
        dropPending(key)
        dao.delete(key)
        ServiceLocator.changeBus.emit(ChangeKind.BLOCKLIST)
    }

    suspend fun pending(): List<Pending> = read()

    suspend fun cancel(key: String) = dropPending(key)

    suspend fun applyDue(now: Long) {
        val (due, later) = read().partition { it.appliesAt <= now }
        if (due.isEmpty()) return
        due.forEach { apply(it) }
        write(later)
        ServiceLocator.changeBus.emit(ChangeKind.BLOCKLIST)
    }

    private suspend fun weaken(p: Pending): Long {
        val s = ServiceLocator.settings.current()
        val now = ServiceLocator.clock.now()
        if (!s.hardMode) {
            dropPending(p.key)
            apply(p)
            ServiceLocator.changeBus.emit(ChangeKind.BLOCKLIST)
            return now
        }
        val at = now + s.weakenCooldownMin * 60_000L
        write(read().filter { it.key != p.key } + p.copy(appliesAt = at))
        return at
    }

    private suspend fun apply(p: Pending) {
        if (p.rule == REMOVE) dao.delete(p.key)
        else if (p.rule.startsWith(CHARGE)) dao.get(p.key)?.let {
            dao.upsert(it.copy(meterPct = p.rule.removePrefix(CHARGE).toIntOrNull()))
        }
        else dao.upsert(AppRuleEntity(p.key, AppRule.valueOf(p.rule), p.isWebsite, p.label))
    }

    private suspend fun dropPending(key: String) = write(read().filter { it.key != key })

    private suspend fun read(): List<Pending> =
        ServiceLocator.settings.rawGet(KEY).orEmpty().lines().mapNotNull { line ->
            val f = line.split('|')
            if (f.size != 5) null else Pending(f[0], f[1], f[2] == "1", f[3], f[4].toLongOrNull() ?: return@mapNotNull null)
        }

    private suspend fun write(list: List<Pending>) {
        val text = list.joinToString("\n") {
            listOf(it.key, it.rule, if (it.isWebsite) "1" else "0", it.label.replace('|', ' ').replace('\n', ' '), it.appliesAt).joinToString("|")
        }
        ServiceLocator.settings.rawPut(KEY, text)
    }
}
