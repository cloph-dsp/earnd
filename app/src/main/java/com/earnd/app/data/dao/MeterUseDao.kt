package com.earnd.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.earnd.app.data.entity.MeterUseEntity
import kotlinx.coroutines.flow.Flow

data class AppUse(val appKey: String, val minutes: Int)

@Dao
interface MeterUseDao {
    @Insert
    suspend fun insert(e: MeterUseEntity)

    @Query("SELECT appKey, SUM(minutes) AS minutes FROM meter_use WHERE at >= :since GROUP BY appKey ORDER BY minutes DESC")
    fun observeTotals(since: Long): Flow<List<AppUse>>

    @Query("DELETE FROM meter_use WHERE at < :before")
    suspend fun prune(before: Long)
}
