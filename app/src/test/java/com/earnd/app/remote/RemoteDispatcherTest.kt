package com.earnd.app.remote

import com.earnd.app.data.entity.CheckInEntity
import com.earnd.app.data.entity.CheckInMode
import com.earnd.app.data.entity.HabitEntity
import com.earnd.app.data.entity.RepeatKind
import com.earnd.app.data.entity.TaskEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Base64

class RemoteDispatcherTest {

    private class FakeBackend : RemoteBackend {
        var enabled = true
        val habits = mutableMapOf<Long, HabitEntity>()
        val checkIns = mutableListOf<CheckInEntity>()
        val tasks = mutableMapOf<Long, TaskEntity>()
        val audit = mutableListOf<String>()
        private var seq = 100L
        val day: LocalDate = LocalDate.of(2026, 3, 10)

        override fun now() = 1_000L
        override fun today() = day
        override suspend fun remoteEnabled() = enabled
        override suspend fun status() = mapOf<String, Any?>("balanceMinutes" to 12)
        override suspend fun habits(includeArchived: Boolean) = habits.values.filter { includeArchived || !it.archived }
        override suspend fun habit(id: Long) = habits[id]
        override suspend fun checkInsToday(habitId: Long) = checkIns.filter { it.habitId == habitId }
        override suspend fun streak(habitId: Long) = 3
        override suspend fun saveHabit(h: HabitEntity): Long {
            val id = if (h.id == 0L) ++seq else h.id
            habits[id] = h.copy(id = id); return id
        }
        override suspend fun archiveHabit(id: Long) { habits[id] = habits[id]!!.copy(archived = true) }
        override suspend fun checkIn(habitId: Long, quantity: Double?) =
            CheckInEntity(id = ++seq, habitId = habitId, epochDay = day.toEpochDay(), at = seq, minutesEarned = 5)
                .also { checkIns += it }
        override suspend fun undoCheckIn(checkInId: Long) { checkIns.removeAll { it.id == checkInId } }
        override suspend fun openTasks() = tasks.values.filter { it.completedAt == null }
        override suspend fun task(id: Long) = tasks[id]
        override suspend fun subtasks(parentId: Long) = tasks.values.filter { it.parentId == parentId }
        override suspend fun saveTask(t: TaskEntity): Long {
            val id = if (t.id == 0L) ++seq else t.id
            tasks[id] = t.copy(id = id); return id
        }
        override suspend fun completeTask(id: Long): TaskEntity? { tasks[id] = tasks[id]!!.copy(completedAt = 1); return null }
        override suspend fun uncompleteTask(id: Long) { tasks[id] = tasks[id]!!.copy(completedAt = null) }
        override suspend fun deleteTask(id: Long) { tasks.remove(id) }
        override suspend fun appendAudit(entryJson: String) { audit += entryJson }
        override suspend fun auditLog() = audit
    }

    private val b = FakeBackend()
    private val d = RemoteDispatcher(b)
    private fun b64(s: String) = Base64.getEncoder().encodeToString(s.toByteArray())

    @Test fun forbiddenCommandsAreRefusedEvenWhenDisabled() = runTest {
        b.enabled = false
        for (cmd in listOf("unlock", "balance.add", "apps.allow", "blocking.disable", "bedtime.off", "vacation.set", "settings.set")) {
            assertEquals("""{"ok":false,"error":"forbidden","message":null}""", d.handle(cmd, emptyMap()))
        }
        assertEquals(7, b.audit.size)
    }

    @Test fun disabledToggleBlocksEverything() = runTest {
        b.enabled = false
        assertTrue(d.handle("status", emptyMap()).contains("\"error\":\"disabled\""))
    }

    @Test fun createTaskDecodesBase64AndAudits() = runTest {
        val out = d.handle("tasks.create", mapOf("title_b64" to b64("Buy milk \"2%\" ✓"), "due" to "today"))
        assertTrue(out, out.startsWith("""{"ok":true,"id":"""))
        val t = b.tasks.values.single()
        assertEquals("Buy milk \"2%\" ✓", t.title)
        assertEquals(b.day.toEpochDay(), t.dueEpochDay)
        assertEquals(1, b.audit.size)
        assertTrue(d.handle("tasks.list", emptyMap()).contains("\\u2713"))
    }

    @Test fun rewardFieldsAreForbidden() = runTest {
        val out = d.handle("tasks.create", mapOf("title" to "x", "reward" to "500"))
        assertTrue(out, out.contains("\"error\":\"forbidden\""))
        assertTrue(b.tasks.isEmpty())
    }

    @Test fun weeklyTaskGetsDays() = runTest {
        d.handle("tasks.create", mapOf("title" to "Gym", "due" to "2026-03-10", "repeat" to "weekly", "days" to "mon,thu"))
        val t = b.tasks.values.single()
        assertEquals(RepeatKind.WEEKLY, t.repeatKind)
        assertEquals(0b1001, t.repeatWeekdayMask)
    }

    @Test fun completeAndUncomplete() = runTest {
        val id = b.saveTask(TaskEntity(title = "a", createdAt = 0))
        assertTrue(d.handle("tasks.complete", mapOf("id" to "$id")).startsWith("{\"ok\":true"))
        assertTrue(d.handle("tasks.complete", mapOf("id" to "$id")).contains("already_done"))
        assertTrue(d.handle("tasks.uncomplete", mapOf("id" to "$id")).startsWith("{\"ok\":true"))
    }

    @Test fun habitCheckInAndUndo() = runTest {
        val out = d.handle("habits.create", mapOf("name_b64" to b64("Read"), "times" to "2", "color" to "#FF7F50"))
        assertTrue(out, out.startsWith("{\"ok\":true"))
        val h = b.habits.values.single()
        assertEquals(0xFFFF7F50.toInt(), h.colorArgb)
        d.handle("habits.checkin", mapOf("id" to "${h.id}"))
        assertTrue(d.handle("habits.get", mapOf("id" to "${h.id}")).contains("\"doneToday\":1"))
        d.handle("habits.undo", mapOf("id" to "${h.id}"))
        assertTrue(b.checkIns.isEmpty())
        assertTrue(d.handle("habits.undo", mapOf("id" to "${h.id}")).contains("nothing_to_undo"))
    }

    @Test fun photoHabitNeedsPhone() = runTest {
        val id = b.saveHabit(HabitEntity(name = "p", iconKey = "x", colorArgb = 0, anchorEpochDay = 0, createdAt = 0, checkInMode = CheckInMode.PHOTO))
        assertTrue(d.handle("habits.checkin", mapOf("id" to "$id")).contains("photo_required"))
    }

    @Test fun badArgsAndNotFound() = runTest {
        assertTrue(d.handle("tasks.get", mapOf("id" to "999")).contains("not_found"))
        assertTrue(d.handle("tasks.get", mapOf("id" to "abc")).contains("bad_args"))
        assertTrue(d.handle("tasks.create", mapOf("title_b64" to "%%%")).contains("bad_args"))
        assertTrue(d.handle("nope", emptyMap()).contains("unknown_command"))
    }

    @Test fun readsAreNotAudited() = runTest {
        d.handle("status", emptyMap())
        d.handle("habits.list", emptyMap())
        assertTrue(b.audit.isEmpty())
    }

    @Test fun auditIsCapped() {
        var s: String? = null
        repeat(5) { s = RemoteAudit.append(s, "{\"n\":$it}", max = 3) }
        assertEquals(listOf("{\"n\":2}", "{\"n\":3}", "{\"n\":4}"), RemoteAudit.parse(s))
        assertFalse(RemoteDispatcher.isForbidden("tasks.list"))
    }
}
