package com.earnd.app.data.entity

import androidx.room.ColumnInfo
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
    /** Recurrence as an RRULE string (see [com.earnd.app.domain.RRule]); null => does not repeat. */
    val repeatRule: String? = null,
    /** True => the next due date counts from the completion day instead of the schedule. */
    @ColumnInfo(defaultValue = "0") val repeatFromCompletion: Boolean = false,
    /** First due day of the series (rule start); anchors intervals, ordinals and yearly dates. */
    val repeatAnchorEpochDay: Long? = null,
    /** 1-based number of this occurrence in its series, for COUNT. */
    @ColumnInfo(defaultValue = "1") val repeatOccurrence: Int = 1,
    /** Null => earns nothing. */
    val rewardMinutes: Int? = null,
    val completedAt: Long? = null,
    /** Shared by all occurrences of a recurring task. */
    val seriesId: Long? = null,
    val sortOrder: Int = 0,
    val createdAt: Long,
) {
    val repeats: Boolean get() = repeatRule != null
}
