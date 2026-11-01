package com.earnd.app.data.dao

import androidx.room.*
import com.earnd.app.data.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerDao {
    @Query("SELECT COALESCE(SUM(minutes), 0) FROM ledger WHERE periodKey = :periodKey")
    fun observeSum(periodKey: Long): Flow<Int?>

    @Query("SELECT COALESCE(SUM(minutes), 0) FROM ledger WHERE periodKey = :periodKey")
    suspend fun sum(periodKey: Long): Int

    @Insert
    suspend fun insert(e: LedgerEntryEntity): Long

    @Query("SELECT * FROM ledger WHERE sourceKind = :kind AND sourceId = :id ORDER BY at")
    suspend fun forSource(kind: OwnerKind, id: Long): List<LedgerEntryEntity>

    /** Earned entries that still have unspent minutes and have not expired at [now], oldest first. */
    @Query("SELECT * FROM ledger WHERE expiresAt IS NOT NULL AND remaining > 0 AND expiresAt > :now ORDER BY at, id")
    suspend fun unexpiredLots(now: Long): List<LedgerEntryEntity>

    @Query("UPDATE ledger SET remaining = :remaining WHERE id = :id")
    suspend fun setRemaining(id: Long, remaining: Int)

    @Query("SELECT * FROM ledger WHERE kind = 'SPEND' ORDER BY id DESC LIMIT 1")
    suspend fun lastSpend(): LedgerEntryEntity?

    @Query("SELECT * FROM ledger ORDER BY at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<LedgerEntryEntity>>
}
