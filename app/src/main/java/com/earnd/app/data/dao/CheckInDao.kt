package com.earnd.app.data.dao

import androidx.room.*
import com.earnd.app.data.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CheckInDao {
    @Query("SELECT * FROM habit_checkin WHERE epochDay = :epochDay ORDER BY at")
    fun observeForDay(epochDay: Long): Flow<List<CheckInEntity>>

    @Query("SELECT * FROM habit_checkin WHERE epochDay = :epochDay ORDER BY at")
    suspend fun forDay(epochDay: Long): List<CheckInEntity>

    @Query("SELECT * FROM habit_checkin WHERE habitId = :habitId AND epochDay BETWEEN :fromDay AND :toDay ORDER BY at")
    suspend fun forHabitBetween(habitId: Long, fromDay: Long, toDay: Long): List<CheckInEntity>

    @Query("SELECT COUNT(*) FROM habit_checkin WHERE habitId = :habitId AND epochDay = :epochDay")
    suspend fun countFor(habitId: Long, epochDay: Long): Int

    @Insert
    suspend fun insert(c: CheckInEntity): Long

    @Query("SELECT * FROM habit_checkin WHERE id = :id")
    suspend fun get(id: Long): CheckInEntity?

    @Query("DELETE FROM habit_checkin WHERE id = :id")
    suspend fun deleteById(id: Long)
}
