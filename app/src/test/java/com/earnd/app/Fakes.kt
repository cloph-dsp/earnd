package com.earnd.app

import com.earnd.app.data.dao.LedgerDao
import com.earnd.app.data.dao.SessionDao
import com.earnd.app.data.dao.SettingDao
import com.earnd.app.data.entity.LedgerEntryEntity
import com.earnd.app.data.entity.OwnerKind
import com.earnd.app.data.entity.SettingEntity
import com.earnd.app.data.entity.UnlockSessionEntity
import com.earnd.app.domain.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class FakeClock(var nowMs: Long, private val zone: ZoneId = ZoneOffset.UTC) : Clock {
    override fun now() = nowMs
    override fun zone() = zone
    fun set(t: LocalDateTime) { nowMs = t.toInstant(ZoneOffset.UTC).toEpochMilli() }
    fun advanceMin(m: Int) { nowMs += m * 60_000L }
    companion object { fun at(t: LocalDateTime) = FakeClock(t.toInstant(ZoneOffset.UTC).toEpochMilli()) }
}

fun utc(t: LocalDateTime): Long = t.toInstant(ZoneOffset.UTC).toEpochMilli()

class FakeLedgerDao : LedgerDao {
    val rows = MutableStateFlow<List<LedgerEntryEntity>>(emptyList())
    override fun observeSum(periodKey: Long): Flow<Int?> =
        rows.map { l -> l.filter { it.periodKey == periodKey }.sumOf { it.minutes } }
    override suspend fun sum(periodKey: Long) = rows.value.filter { it.periodKey == periodKey }.sumOf { it.minutes }
    override suspend fun insert(e: LedgerEntryEntity): Long {
        val id = rows.value.size + 1L
        rows.value = rows.value + e.copy(id = id)
        return id
    }
    override suspend fun forSource(kind: OwnerKind, id: Long) =
        rows.value.filter { it.sourceKind == kind && it.sourceId == id }
    override fun observeRecent(limit: Int) = rows.map { it.reversed().take(limit) }
}

class FakeSessionDao : SessionDao {
    val rows = MutableStateFlow<List<UnlockSessionEntity>>(emptyList())
    override fun observeActive() = rows.map { l -> l.lastOrNull { it.endedAt == null } }
    override suspend fun active() = rows.value.lastOrNull { it.endedAt == null }
    override suspend fun lastWithReason(reason: String) = rows.value.lastOrNull { it.reason == reason }
    override suspend fun insert(s: UnlockSessionEntity): Long {
        val id = rows.value.size + 1L
        rows.value = rows.value + s.copy(id = id)
        return id
    }
    override suspend fun update(s: UnlockSessionEntity) {
        rows.value = rows.value.map { if (it.id == s.id) s else it }
    }
}

class FakeSettingDao : SettingDao {
    val rows = MutableStateFlow<Map<String, String>>(emptyMap())
    override fun observeAll() = rows.map { m -> m.map { SettingEntity(it.key, it.value) } }
    override suspend fun all() = rows.value.map { SettingEntity(it.key, it.value) }
    override suspend fun get(key: String) = rows.value[key]
    override suspend fun put(s: SettingEntity) { rows.value = rows.value + (s.key to s.value) }
    override suspend fun delete(key: String) { rows.value = rows.value - key }
}
