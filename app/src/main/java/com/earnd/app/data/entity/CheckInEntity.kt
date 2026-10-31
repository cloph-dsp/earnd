package com.earnd.app.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "habit_checkin",
    indices = [Index("habitId", "epochDay")],
    foreignKeys = [ForeignKey(
        entity = HabitEntity::class, parentColumns = ["id"], childColumns = ["habitId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class CheckInEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    val epochDay: Long,
    val at: Long,
    val quantity: Double? = null,
    /** Relative to files/photos. */
    val photoPath: String? = null,
    val minutesEarned: Int,
)
