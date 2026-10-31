package com.earnd.app.data.entity

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
) {
    companion object { const val SINGLE = 1L }
}
