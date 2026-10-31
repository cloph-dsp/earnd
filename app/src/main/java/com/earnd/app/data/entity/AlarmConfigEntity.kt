package com.earnd.app.data.entity

import androidx.room.Entity

@Entity(tableName = "alarm_config", primaryKeys = ["ownerKind", "ownerId"])
data class AlarmConfigEntity(
    val ownerKind: OwnerKind,
    val ownerId: Long,
    val atDue: Boolean = true,
    /** CSV of minutes before due, e.g. "60,15". */
    val offsetsBeforeMin: String = "",
    val habitReminderMinuteOfDay: Int? = null,
    val style: AlarmStyle = AlarmStyle.NOTIFICATION,
    val nagMode: NagMode = NagMode.PROGRESSIVE,
    val nagEveryMin: Int = 30,
    val nagMaxCount: Int? = null,
    val soundUri: String? = null,
    val vibrate: Boolean = true,
    val snoozeMaxCount: Int = 2,
)
