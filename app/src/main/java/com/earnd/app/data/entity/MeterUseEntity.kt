package com.earnd.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Whole minutes the meter charged for one leisure rule (package or site host) at [at], and the foreground [rawMs] it saw. */
@Entity(tableName = "meter_use")
data class MeterUseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val appKey: String,
    val minutes: Int,
    val rawMs: Long = 0,
)
