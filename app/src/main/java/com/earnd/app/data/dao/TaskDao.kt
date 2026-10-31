package com.earnd.app.data.dao

import androidx.room.*
import com.earnd.app.data.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    /** Top-level, not completed. */
    @Query("SELECT * FROM task WHERE parentId IS NULL AND completedAt IS NULL ORDER BY dueEpochDay IS NULL, dueEpochDay, dueMinuteOfDay, sortOrder, id")
    fun observeOpen(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM task WHERE parentId IS NULL AND completedAt IS NOT NULL ORDER BY completedAt DESC LIMIT :limit")
    fun observeCompleted(limit: Int): Flow<List<TaskEntity>>

    @Query("SELECT * FROM task WHERE parentId = :parentId ORDER BY sortOrder, id")
    fun observeSubtasks(parentId: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM task WHERE parentId = :parentId ORDER BY sortOrder, id")
    suspend fun subtasks(parentId: Long): List<TaskEntity>

    @Query("SELECT * FROM task WHERE id = :id")
    suspend fun get(id: Long): TaskEntity?

    @Query("SELECT * FROM task WHERE parentId IS NULL AND completedAt IS NULL ORDER BY dueEpochDay IS NULL, dueEpochDay, dueMinuteOfDay, sortOrder, id")
    suspend fun open(): List<TaskEntity>

    @Query("SELECT * FROM task WHERE parentId IS NULL AND completedAt IS NULL AND repeatKind != 'NONE' AND dueEpochDay < :epochDay")
    suspend fun openRecurringDueBefore(epochDay: Long): List<TaskEntity>

    /** Open occurrence of a series due after [afterDay] (the one a completion spawned). */
    @Query("SELECT * FROM task WHERE seriesId = :seriesId AND completedAt IS NULL AND parentId IS NULL AND dueEpochDay > :afterDay ORDER BY dueEpochDay LIMIT 1")
    suspend fun nextOpenInSeries(seriesId: Long, afterDay: Long): TaskEntity?

    @Upsert
    suspend fun upsert(t: TaskEntity): Long

    /** Does not cascade; TaskService deletes subtasks first. */
    @Query("DELETE FROM task WHERE id = :id")
    suspend fun deleteById(id: Long)
}
