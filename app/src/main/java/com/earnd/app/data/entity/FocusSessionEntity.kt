package com.earnd.app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

enum class FocusState { ACTIVE, DONE, CANCELLED }

/** The current (or last) focus session. One row only: [id] is always [SINGLE]. */
@Entity(tableName = "focus_session")
data class FocusSessionEntity(
    @PrimaryKey val id: Long = SINGLE,
    val habitId: Long,
    val startedAt: Long,
    val durationMs: Long,
    val state: FocusState = FocusState.ACTIVE,
    /** Count-up session: no end time; [durationMs] is set to the elapsed time on finish. */
    @ColumnInfo(defaultValue = "0") val stopwatch: Boolean = false,
    /** Reward of the chosen duration option; null = the habit's reward. */
    val rewardMinutes: Int? = null,
) {
    companion object { const val SINGLE = 1L }
}
