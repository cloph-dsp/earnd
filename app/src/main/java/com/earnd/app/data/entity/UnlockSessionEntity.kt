package com.earnd.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "unlock_session")
data class UnlockSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val plannedEndAt: Long,
    val endedAt: Long? = null,
    /** See [SessionReason]. */
    val reason: String,
)

object SessionReason {
    const val SPEND = "spend"
    const val ALL_DONE = "all_done"
    const val BEDTIME_GRACE = "bedtime_grace"
}
