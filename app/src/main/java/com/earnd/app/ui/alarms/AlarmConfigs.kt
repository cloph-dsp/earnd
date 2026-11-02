package com.earnd.app.ui.alarms

import com.earnd.app.ServiceLocator
import com.earnd.app.data.entity.AlarmConfigEntity
import com.earnd.app.data.entity.OwnerKind
import com.earnd.app.domain.ChangeKind

/**
 * Alarm config persistence for the UI. The app-wide defaults per owner kind are stored as an
 * AlarmConfigEntity with [DEFAULT_OWNER_ID] (real ids start at 1).
 */
object AlarmConfigs {
    const val DEFAULT_OWNER_ID = 0L

    private val dao get() = ServiceLocator.db.alarmConfigDao()

    suspend fun defaults(kind: OwnerKind): AlarmConfigEntity =
        dao.get(kind, DEFAULT_OWNER_ID) ?: AlarmConfigEntity(ownerKind = kind, ownerId = DEFAULT_OWNER_ID, style = initialStyle(kind))

    /** Style of a config nobody customised yet: habit reminders are real alarms, tasks stay notifications. */
    fun initialStyle(kind: OwnerKind): com.earnd.app.data.entity.AlarmStyle =
        if (kind == OwnerKind.HABIT) com.earnd.app.data.entity.AlarmStyle.FULL_SCREEN
        else com.earnd.app.data.entity.AlarmStyle.NOTIFICATION

    /** The item's own config, or null when it follows the defaults. */
    suspend fun custom(kind: OwnerKind, id: Long): AlarmConfigEntity? = if (id <= 0) null else dao.get(kind, id)

    suspend fun save(cfg: AlarmConfigEntity) {
        dao.upsert(cfg)
        ServiceLocator.changeBus.emit(ChangeKind.ALARMS)
    }

    suspend fun clear(kind: OwnerKind, id: Long) {
        dao.delete(kind, id)
        ServiceLocator.changeBus.emit(ChangeKind.ALARMS)
    }

    /** Saves an item's config, or removes it when [cfg] is null (use defaults). */
    suspend fun apply(kind: OwnerKind, id: Long, cfg: AlarmConfigEntity?) {
        if (cfg == null) clear(kind, id) else save(cfg.copy(ownerKind = kind, ownerId = id))
    }
}
