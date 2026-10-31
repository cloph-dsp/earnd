package com.earnd.app.data.dao

import androidx.room.*
import com.earnd.app.data.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppRuleDao {
    @Query("SELECT * FROM app_rule ORDER BY label")
    fun observeAll(): Flow<List<AppRuleEntity>>

    @Query("SELECT * FROM app_rule")
    suspend fun all(): List<AppRuleEntity>

    @Upsert
    suspend fun upsert(r: AppRuleEntity)

    @Query("DELETE FROM app_rule WHERE `key` = :key")
    suspend fun delete(key: String)
}
