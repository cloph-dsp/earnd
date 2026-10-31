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

    @Query("SELECT * FROM habit WHERE archived = 1 ORDER BY sortOrder, id")
    fun observeArchived(): Flow<List<HabitEntity>>

    @Query("UPDATE habit SET archived = 0 WHERE id = :id")
    suspend fun unarchive(id: Long)

    @Query("UPDATE habit SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun setSortOrder(id: Long, sortOrder: Int)

    @Transaction
    suspend fun reorder(idsInOrder: List<Long>) {
        idsInOrder.forEachIndexed { i, id -> setSortOrder(id, i) }
    }

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM habit")
    suspend fun nextSortOrder(): Int

    @Delete
    suspend fun delete(h: HabitEntity)
}
