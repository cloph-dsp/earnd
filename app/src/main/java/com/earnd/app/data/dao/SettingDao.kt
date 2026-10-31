package com.earnd.app.data.dao

import androidx.room.*
import com.earnd.app.data.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingDao {
    @Query("SELECT * FROM setting")
    fun observeAll(): Flow<List<SettingEntity>>

    @Query("SELECT * FROM setting")
    suspend fun all(): List<SettingEntity>

    @Query("SELECT value FROM setting WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Upsert
    suspend fun put(s: SettingEntity)

    @Query("DELETE FROM setting WHERE `key` = :key")
    suspend fun delete(key: String)
}
