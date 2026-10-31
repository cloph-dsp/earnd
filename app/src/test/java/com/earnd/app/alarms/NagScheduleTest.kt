package com.earnd.app.alarms

import com.earnd.app.data.entity.AlarmConfigEntity
import com.earnd.app.data.entity.NagMode
import com.earnd.app.data.entity.NagStateEntity
import com.earnd.app.data.entity.OwnerKind
import com.earnd.app.domain.DailyWindow
import com.earnd.app.utc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class NagScheduleTest {
    private val z = ZoneOffset.UTC
    private val quiet = DailyWindow(0, 8 * 60)
    private val due = utc(LocalDateTime.of(2026, 3, 4, 12, 0))
    private fun st(count: Int = 0, last: Long? = null, dueAt: Long = due) =
        NagStateEntity(OwnerKind.TASK, 1, dueAt, nagCount = count, lastNagAt = last)
    private fun min(m: Int) = m * 60_000L

    @Test fun progressiveCadence() {
        assertEquals(due + min(10), NagSchedule.next(st(), null, due, z, quiet))
        val last = due + min(10)
        assertEquals(last + min(15), NagSchedule.next(st(1, last), null, last, z, quiet))
        assertEquals(last + min(240), NagSchedule.next(st(20, last), null, last, z, quiet))
    }

    @Test fun fixedOffAndMax() {
        val fixed = AlarmConfigEntity(OwnerKind.TASK, 1, nagMode = NagMode.FIXED, nagEveryMin = 20, nagMaxCount = 2)
        assertEquals(due + min(20), NagSchedule.next(st(), fixed, due, z, quiet))
        assertNull(NagSchedule.next(st(2, due), fixed, due, z, quiet))
        assertNull(NagSchedule.next(st(), fixed.copy(nagMode = NagMode.OFF), due, z, quiet))
    }

    @Test fun quietHoursShiftToEnd() {
        val late = utc(LocalDateTime.of(2026, 3, 4, 23, 55))
        assertEquals(utc(LocalDateTime.of(2026, 3, 5, 8, 0)), NagSchedule.next(st(dueAt = late), null, late, z, quiet))
    }

    @Test fun weekOverdueCollapsesToDailyDigest() {
        val now = due + 8L * 24 * 60 * 60_000
        val a = NagSchedule.next(st(5, now - min(30)), null, now, z, quiet)
        val b = NagSchedule.next(st(1, null, dueAt = due - min(600)), null, now, z, quiet)
        assertEquals(utc(LocalDateTime.of(2026, 3, 13, 8, 0)), a)
        assertEquals(a, b) // both items share the digest slot
    }

    @Test fun orderingAndSnooze() {
        val ordered = NagSchedule.order(listOf(st(1).copy(ownerId = 1), st(4).copy(ownerId = 2), st(1, dueAt = due - 1).copy(ownerId = 3)))
        assertEquals(listOf(2L, 3L, 1L), ordered.map { it.ownerId })
        assertTrue(NagSchedule.canSnooze(st().copy(snoozeCount = 1), null))
        assertFalse(NagSchedule.canSnooze(st().copy(snoozeCount = 2), null))
        assertFalse(NagSchedule.canSnooze(st(), AlarmConfigEntity(OwnerKind.TASK, 1, snoozeMaxCount = 0)))
    }
}
