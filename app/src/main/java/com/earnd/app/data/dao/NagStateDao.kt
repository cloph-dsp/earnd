package com.earnd.app.data.dao

import androidx.room.*
import com.earnd.app.data.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NagStateDao {
    @Query("SELECT * FROM nag_state")
    fun observeAll(): Flow<List<NagStateEntity>>

    @Query("SELECT * FROM nag_state")
    suspend fun all(): List<NagStateEntity>

    @Query("SELECT * FROM nag_state WHERE ownerKind = :kind AND ownerId = :id")
    suspend fun get(kind: OwnerKind, id: Long): NagStateEntity?

    @Upsert
    suspend fun upsert(s: NagStateEntity)

    @Query("DELETE FROM nag_state WHERE ownerKind = :kind AND ownerId = :id")
    suspend fun delete(kind: OwnerKind, id: Long)
}
