package com.earnd.app.domain

import com.earnd.app.FakeClock
import com.earnd.app.FakeLedgerDao
import com.earnd.app.FakeSessionDao
import com.earnd.app.FakeSettingDao
import com.earnd.app.data.SettingsRepository
import com.earnd.app.data.dao.CheckInDao
import com.earnd.app.data.dao.HabitDao
import com.earnd.app.data.entity.CheckInEntity
import com.earnd.app.data.entity.HabitEntity
import com.earnd.app.data.entity.ScheduleKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Proxy
import java.time.LocalDateTime

class TodayHabitsTest {
    private val clock = FakeClock.at(LocalDateTime.of(2026, 11, 4, 13, 53)) // Wednesday
    private val hs = MutableStateFlow<List<HabitEntity>>(emptyList())

    private val habitDao: HabitDao = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(HabitDao::class.java)) { _, m, _ ->
        when (m.name) {
            "observeActive" -> hs.map { l -> l.filter { !it.archived } }
            "observeArchived" -> hs.map { l -> l.filter { it.archived } }
            else -> error("unexpected ${m.name}")
        }
    } as HabitDao

    private val checkInDao: CheckInDao = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(CheckInDao::class.java)) { _, m, _ ->
        when (m.name) {
            "observeForDay" -> MutableStateFlow(emptyList<CheckInEntity>())
            "between" -> emptyList<CheckInEntity>()
            else -> error("unexpected ${m.name}")
        }
    } as CheckInDao

    @Test fun dailyAndWeekdayHabitsAreDueOnAWednesday() = runBlocking {
        val day = clock.today().toEpochDay()
        fun h(name: String, kind: ScheduleKind = ScheduleKind.DAILY, perDay: Int = 1) = HabitEntity(
            id = name.hashCode().toLong(), name = name, iconKey = "x", colorArgb = 0, timesPerDay = perDay, scheduleKind = kind,
            weekdayMask = 0b0011111, anchorEpochDay = day, createdAt = clock.now())
        hs.value = listOf(h("Ler"), h("Medicacao"), h("Dentes", perDay = 3), h("Agua", perDay = 10), h("Arrumacao", ScheduleKind.WEEKDAYS))
        val settings = SettingsRepository(FakeSettingDao(), clock, ChangeBus())
        val bank = TimeBank(FakeLedgerDao(), FakeSessionDao(), settings, clock, ChangeBus())
        val svc = HabitService(habitDao, checkInDao, bank, clock, ChangeBus(), settings)
        assertEquals(5, svc.observeToday().first().size)
    }

    @Test fun todayRollsOverAtMidnightInsteadOfFreezing() = runBlocking {
        val day = clock.today().toEpochDay()
        clock.set(LocalDateTime.of(2026, 11, 6, 23, 59)) // Friday
        hs.value = listOf(HabitEntity(id = 1, name = "Fri", iconKey = "x", colorArgb = 0, scheduleKind = ScheduleKind.WEEKDAYS,
            weekdayMask = 0b0010000, anchorEpochDay = day, createdAt = clock.now()))
        val settings = SettingsRepository(FakeSettingDao(), clock, ChangeBus())
        val bank = TimeBank(FakeLedgerDao(), FakeSessionDao(), settings, clock, ChangeBus())
        val svc = HabitService(habitDao, checkInDao, bank, clock, ChangeBus(), settings, dayTickMs = 10)
        val seen = java.util.concurrent.CopyOnWriteArrayList<Int>()
        val job = kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Default) { svc.observeToday().collect { seen += it.size } }
        kotlinx.coroutines.delay(200)
        clock.set(LocalDateTime.of(2026, 11, 7, 0, 1)) // Saturday: Friday-only habit no longer due
        kotlinx.coroutines.delay(300)
        job.cancel()
        assertEquals(listOf(1, 0), seen.toList())
    }

    @Test fun observedRollingBalanceMatchesLedger() = runBlocking {
        val settings = SettingsRepository(FakeSettingDao(), clock, ChangeBus())
        val bank = TimeBank(FakeLedgerDao(), FakeSessionDao(), settings, clock, ChangeBus())
        bank.earn(50, com.earnd.app.data.entity.LedgerKind.EARN_HABIT, com.earnd.app.data.entity.OwnerKind.HABIT, 1)
        bank.earn(5, com.earnd.app.data.entity.LedgerKind.EARN_HABIT, com.earnd.app.data.entity.OwnerKind.HABIT, 2)
        assertEquals(55, bank.observeBalance().first().minutes)
        assertEquals(55, bank.balance().minutes)
    }
}
