package com.earnd.app.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "task", indices = [Index("dueEpochDay"), Index("parentId"), Index("seriesId")])
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Non-null => subtask (one level deep). */
    val parentId: Long? = null,
    val title: String,
    val notes: String = "",
    val dueEpochDay: Long? = null,
    /** Null => all-day. */
    val dueMinuteOfDay: Int? = null,
    val priority: Priority = Priority.NONE,
    val repeatKind: RepeatKind = RepeatKind.NONE,
    val repeatWeekdayMask: Int = 0,
    /** N for EVERY_N_DAYS, months for MONTHLY, weeks for WEEKLY. */
    val repeatInterval: Int = 1,
    /** First due day of the series; anchors WEEKLY intervals and YEARLY dates. Null => use due. */
    val repeatAnchorEpochDay: Long? = null,
    /** Null => earns nothing. */
    val rewardMinutes: Int? = null,
    val completedAt: Long? = null,
    /** Shared by all occurrences of a recurring task. */
    val seriesId: Long? = null,
    val sortOrder: Int = 0,
    val createdAt: Long,
)
