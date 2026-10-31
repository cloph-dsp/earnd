package com.earnd.app.data.dao

import androidx.room.*
import com.earnd.app.data.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusDao {
    @Query("SELECT * FROM focus_session WHERE id = 1")
    fun observe(): Flow<FocusSessionEntity?>

    @Query("SELECT * FROM focus_session WHERE id = 1")
    suspend fun get(): FocusSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(s: FocusSessionEntity)

    /** Moves an ACTIVE session to [to]; returns 1 only for the caller that won the race. */
    @Query("UPDATE focus_session SET state = :to WHERE id = 1 AND state = 'ACTIVE' AND startedAt = :startedAt")
    suspend fun finish(startedAt: Long, to: FocusState): Int
}
