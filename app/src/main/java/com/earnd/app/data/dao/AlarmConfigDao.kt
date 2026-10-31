package com.earnd.app.data.dao

import androidx.room.*
import com.earnd.app.data.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmConfigDao {
    @Query("SELECT * FROM alarm_config WHERE ownerKind = :kind AND ownerId = :id")
    suspend fun get(kind: OwnerKind, id: Long): AlarmConfigEntity?

    @Query("SELECT * FROM alarm_config")
    suspend fun all(): List<AlarmConfigEntity>

    @Upsert
    suspend fun upsert(c: AlarmConfigEntity)

    @Query("DELETE FROM alarm_config WHERE ownerKind = :kind AND ownerId = :id")
    suspend fun delete(kind: OwnerKind, id: Long)
}
