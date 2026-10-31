package com.earnd.app.data.dao

import androidx.room.*
import com.earnd.app.data.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    /** Not ended; may be past plannedEndAt (callers compare with now). */
    @Query("SELECT * FROM unlock_session WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun observeActive(): Flow<UnlockSessionEntity?>

    @Query("SELECT * FROM unlock_session WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun active(): UnlockSessionEntity?

    @Query("SELECT * FROM unlock_session WHERE reason = :reason ORDER BY startedAt DESC LIMIT 1")
    suspend fun lastWithReason(reason: String): UnlockSessionEntity?

    @Insert
    suspend fun insert(s: UnlockSessionEntity): Long

    @Update
    suspend fun update(s: UnlockSessionEntity)
}
