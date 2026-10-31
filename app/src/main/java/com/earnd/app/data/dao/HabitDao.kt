package com.earnd.app.data.dao

import androidx.room.*
import com.earnd.app.data.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {
    @Query("SELECT * FROM habit WHERE archived = 0 ORDER BY sortOrder, id")
    fun observeActive(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habit WHERE id = :id")
    suspend fun get(id: Long): HabitEntity?

    @Query("SELECT * FROM habit ORDER BY sortOrder, id")
    suspend fun all(): List<HabitEntity>

    @Query("SELECT * FROM habit WHERE archived = 0 ORDER BY sortOrder, id")
    suspend fun active(): List<HabitEntity>

    @Upsert
    suspend fun upsert(h: HabitEntity): Long

    @Query("UPDATE habit SET archived = 1 WHERE id = :id")
    suspend fun archive(id: Long)

    @Delete
    suspend fun delete(h: HabitEntity)
}
