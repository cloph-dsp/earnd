package com.earnd.app.remote

import com.earnd.app.data.Settings
import com.earnd.app.data.entity.AlarmConfigEntity
import com.earnd.app.data.entity.AppRule
import com.earnd.app.data.entity.AppRuleEntity
import com.earnd.app.data.entity.CheckInEntity
import com.earnd.app.data.entity.OwnerKind
import com.earnd.app.data.entity.CheckInMode
import com.earnd.app.data.entity.HabitEntity
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
        override suspend fun skipTask(id: Long): LocalDate? {
            val t = tasks.getValue(id)
            val next = com.earnd.app.domain.Recurrence.following(t, day) ?: run { tasks.remove(id); return null }
            tasks[id] = t.copy(dueEpochDay = next.toEpochDay(), repeatOccurrence = t.repeatOccurrence + 1)
            return next
        }
        val alarms = mutableMapOf<Pair<OwnerKind, Long>, AlarmConfigEntity>()
        var settings = Settings()
        val rules = mutableMapOf<String, AppRuleEntity>()
        override suspend fun alarmConfig(kind: OwnerKind, id: Long) = alarms[kind to id]
        override suspend fun saveAlarmConfig(cfg: AlarmConfigEntity) { alarms[cfg.ownerKind to cfg.ownerId] = cfg }
        val soundList = listOf(com.earnd.app.alarms.SoundOption("custom:gong.mp3", "gong.mp3", "file:///m/gong.mp3", "custom"))
        override suspend fun sounds() = soundList
        override suspend fun settings() = settings
        override suspend fun saveSettings(expected: Settings, new: Settings): Boolean {
            if (settings != expected) return false
            settings = new; return true
        }
        override suspend fun appRules() = rules.values.toList()
        override suspend fun blockRule(key: String, label: String, isWebsite: Boolean) {
            rules[key] = AppRuleEntity(key, AppRule.LEISURE, isWebsite, label)
        }
        override suspend fun setCharging(key: String, pct: Int?) { rules[key] = rules.getValue(key).copy(meterPct = pct) }
        override suspend fun launchableApps() = listOf("YouTube" to "com.google.android.youtube")
        override suspend fun appendAudit(entryJson: String) { audit += entryJson }
        override suspend fun auditLog() = audit
    }

    private val b = FakeBackend()

    @org.junit.Before fun installStrings() = com.earnd.app.i18n.TestStr.install()
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

    @Test fun taskRewardOnCreateThenOnlyLower() = runTest {
        assertTrue(d.handle("tasks.create", mapOf("title" to "x", "reward" to "500")).contains("bad_args"))
        assertTrue(d.handle("tasks.create", mapOf("title" to "x", "rewardMinutes" to "20", "offsets" to "60,15", "nag" to "fixed")).startsWith("{\"ok\":true"))
        val t = b.tasks.values.single()
        assertEquals(20, t.rewardMinutes)
        assertEquals("60,15", b.alarms.getValue(OwnerKind.TASK to t.id).offsetsBeforeMin)
        assertTrue(d.handle("tasks.update", mapOf("id" to "${t.id}", "reward" to "30")).contains("forbidden_weaken"))
        assertEquals(20, b.tasks.getValue(t.id).rewardMinutes)
        assertTrue(d.handle("tasks.update", mapOf("id" to "${t.id}", "reward" to "10")).startsWith("{\"ok\":true"))
        assertEquals(10, b.tasks.getValue(t.id).rewardMinutes)
    }

    @Test fun habitCreateWithRewardAndReminders() = runTest {
        val out = d.handle(
            "habits.create",
            mapOf("name" to "Water", "minutes" to "15", "times" to "3", "reminders" to "21:00,08:00,14:00", "remind_days" to "mon,tue", "message" to "Drink!", "photo" to "true"),
        )
        assertTrue(out, out.startsWith("{\"ok\":true"))
        val h = b.habits.values.single()
        assertEquals(15, h.rewardMinutes)
        assertEquals(0b11, h.reminderDays)
        assertEquals("Drink!", h.reminderMessage)
        assertEquals(CheckInMode.PHOTO, h.checkInMode)
        val cfg = b.alarms.getValue(OwnerKind.HABIT to h.id)
        assertEquals(21 * 60, cfg.habitReminderMinuteOfDay)
        assertEquals("780,420", cfg.offsetsBeforeMin)
        assertTrue(d.handle("habits.get", mapOf("id" to "${h.id}")).contains("\"reminders\":[\"08:00\",\"14:00\",\"21:00\"]"))
        // Raising the reward or dropping photo proof is weakening; lowering is fine.
        assertTrue(d.handle("habits.update", mapOf("id" to "${h.id}", "minutes" to "16")).contains("forbidden_weaken"))
        assertTrue(d.handle("habits.update", mapOf("id" to "${h.id}", "photo" to "false")).contains("forbidden_weaken"))
        assertTrue(d.handle("habits.update", mapOf("id" to "${h.id}", "minutes" to "5", "reminders" to "none", "nag" to "off")).startsWith("{\"ok\":true"))
        assertEquals(5, b.habits.getValue(h.id).rewardMinutes)
        assertEquals(null, b.alarms.getValue(OwnerKind.HABIT to h.id).habitReminderMinuteOfDay)
    }

    @Test fun blockingCanOnlyGrow() = runTest {
        assertTrue(d.handle("rules.block_app", mapOf("pkg" to "com.google.android.youtube")).contains("\"label\":\"YouTube\""))
        assertTrue(d.handle("rules.block_site", mapOf("domain" to "https://www.Reddit.com/r/all")).contains("\"domain\":\"reddit.com\""))
        assertTrue(d.handle("rules.block_site", mapOf("domain" to "not a host")).contains("bad_args"))
        assertTrue(d.handle("rules.block_app", mapOf("pkg" to "x; rm")).contains("bad_args"))
        assertEquals(setOf("com.google.android.youtube", "reddit.com"), b.rules.keys)
        val list = d.handle("rules.list", emptyMap())
        assertTrue(list, list.contains("reddit.com") && list.contains("\"alwaysOpen\":[]"))
        for (cmd in listOf("rules.unblock", "rules.remove", "rules.allow", "rules.always_open", "unblock.app", "apps.allow")) {
            assertTrue(cmd, d.handle(cmd, mapOf("pkg" to "com.google.android.youtube")).contains("\"error\":\"forbidden\""))
        }
        assertEquals(2, b.rules.size)
    }

    @Test fun settingsCanOnlyTighten() = runTest {
        assertTrue(d.handle("settings.get", emptyMap()).contains("\"unlockCapMin\":null"))
        assertTrue(d.handle("settings.tighten", mapOf("unlockCapMin" to "30", "hardMode" to "on")).contains("\"changed\":true"))
        assertEquals(30, b.settings.unlockCapMin)
        assertTrue(b.settings.hardMode)
        assertTrue(d.handle("settings.tighten", mapOf("unlockCapMin" to "20")).startsWith("{\"ok\":true"))
        for (weaken in listOf("unlockCapMin" to "45", "unlockCapMin" to "none", "hardMode" to "false", "blockingEnabled" to "false")) {
            assertTrue(weaken.toString(), d.handle("settings.tighten", mapOf(weaken)).contains("forbidden_weaken"))
        }
        assertTrue(d.handle("settings.tighten", mapOf("themeMode" to "DARK")).contains("forbidden_weaken"))
        assertTrue(d.handle("settings.tighten", mapOf("vacationToDay" to "1")).contains("forbidden_weaken"))
        assertTrue(d.handle("settings.tighten", mapOf("removalPuzzle" to "bogus")).contains("bad_args"))
        assertTrue(d.handle("settings.tighten", mapOf("bedtimeEnabled" to "true", "bedtimeStartMin" to "22:30")).contains("\"changed\":true"))
        assertEquals(22 * 60 + 30, b.settings.bedtimeStartMin)
        assertTrue(d.handle("settings.set", mapOf("hardMode" to "false")).contains("\"error\":\"forbidden\""))
        assertEquals(20, b.settings.unlockCapMin)
        assertTrue(b.settings.hardMode)
    }

    @Test fun weeklyTaskGetsDays() = runTest {
        d.handle("tasks.create", mapOf("title" to "Gym", "due" to "2026-03-10", "repeat" to "weekly", "days" to "mon,thu"))
        val t = b.tasks.values.single()
        assertEquals("FREQ=WEEKLY;BYDAY=MO,TH", t.repeatRule)
    }

    @Test fun yearlyAndBiweeklyTasks() = runTest {
        d.handle("tasks.create", mapOf("title" to "Bday", "due" to "2026-05-20", "repeat" to "YEARLY"))
        d.handle("tasks.create", mapOf("title" to "Bins", "due" to "2026-03-08", "repeat" to "WEEKLY", "interval" to "2", "days" to "sun"))
        val (y, w) = b.tasks.values.sortedBy { it.id }
        assertEquals("FREQ=YEARLY", y.repeatRule)
        assertEquals("FREQ=WEEKLY;INTERVAL=2;BYDAY=SU", w.repeatRule)
    }

    @Test fun rruleRepeatFromAndSummary() = runTest {
        val out = d.handle("tasks.create", mapOf("title" to "Report", "due" to "2026-03-27", "repeat" to "FREQ=MONTHLY;BYDAY=-1FR", "count" to "6"))
        assertTrue(out, out.startsWith("{\"ok\":true"))
        val t = b.tasks.values.single()
        assertEquals("FREQ=MONTHLY;BYDAY=-1FR;COUNT=6", t.repeatRule)
        val got = d.handle("tasks.get", mapOf("id" to "${t.id}"))
        assertTrue(got, got.contains("\"repeatSummary\":\"Monthly on the last Friday, 6 times\""))
        assertTrue(got, got.contains("\"repeatFrom\":\"due\""))
        d.handle("tasks.update", mapOf("id" to "${t.id}", "repeat" to "DAILY", "interval" to "3", "repeat_from" to "completion"))
        val u = b.tasks.getValue(t.id)
        assertEquals("FREQ=DAILY;INTERVAL=3", u.repeatRule)
        assertTrue(u.repeatFromCompletion)
        d.handle("tasks.update", mapOf("id" to "${t.id}", "repeat" to "MONTHLY", "nth" to "2tue", "repeat_from" to "due"))
        assertEquals("FREQ=MONTHLY;BYDAY=2TU", b.tasks.getValue(t.id).repeatRule)
        d.handle("tasks.update", mapOf("id" to "${t.id}", "monthday" to "last"))
        assertEquals("FREQ=MONTHLY;BYMONTHDAY=-1", b.tasks.getValue(t.id).repeatRule)
        assertTrue(d.handle("tasks.update", mapOf("id" to "${t.id}", "repeat" to "FREQ=HOURLY")).contains("bad_args"))
        d.handle("tasks.update", mapOf("id" to "${t.id}", "repeat" to "none"))
        assertEquals(null, b.tasks.getValue(t.id).repeatRule)
    }

    @Test fun skipMovesToNextOccurrence() = runTest {
        d.handle("tasks.create", mapOf("title" to "Gym", "due" to "2026-03-10", "repeat" to "WEEKLY", "days" to "tue", "count" to "2"))
        val t = b.tasks.values.single()
        val out = d.handle("tasks.skip", mapOf("id" to "${t.id}"))
        assertTrue(out, out.contains("\"due\":\"2026-03-17\""))
        assertTrue(d.handle("tasks.skip", mapOf("id" to "${t.id}")).contains("\"ended\":true"))
        assertTrue(b.tasks.isEmpty())
    }

    @Test fun habitFlexibleSchedules() = runTest {
        d.handle("habits.create", mapOf("name" to "Gym", "per_week" to "3"))
        d.handle("habits.create", mapOf("name" to "Plants", "weeks" to "2", "days" to "sun"))
        d.handle("habits.create", mapOf("name" to "Bills", "monthdays" to "1,last"))
        d.handle("habits.create", mapOf("name" to "Review", "rule" to "FREQ=MONTHLY;BYDAY=1MO"))
        val (gym, plants, bills, review) = b.habits.values.sortedBy { it.id }
        assertEquals(com.earnd.app.data.entity.ScheduleKind.TIMES_PER_WEEK, gym.scheduleKind)
        assertEquals(3, gym.timesPerWeek)
        assertEquals("FREQ=WEEKLY;INTERVAL=2;BYDAY=SU", plants.scheduleRule)
        assertEquals("FREQ=MONTHLY;BYMONTHDAY=1,-1", bills.scheduleRule)
        assertEquals(com.earnd.app.data.entity.ScheduleKind.RULE, review.scheduleKind)
        assertTrue(d.handle("habits.get", mapOf("id" to "${plants.id}")).contains("\"scheduleSummary\":\"Every 2 weeks on Sun\""))
    }

    @Test fun habitDaysWithoutScheduleMeansWeekdays() = runTest {
        d.handle("habits.create", mapOf("name" to "Run", "days" to "mon,wed"))
        val h = b.habits.values.single()
        assertEquals(com.earnd.app.data.entity.ScheduleKind.WEEKDAYS, h.scheduleKind)
        assertEquals(0b101, h.weekdayMask)
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

    @Test fun callerUidMustBeShellOrRoot() = runTest {
        assertTrue(d.handle("status", emptyMap(), callerUid = 10123).contains("forbidden_caller"))
        assertTrue(d.handle("status", emptyMap(), callerUid = 2000).startsWith("{\"ok\":true"))
        assertTrue(d.handle("status", emptyMap(), callerUid = 0).startsWith("{\"ok\":true"))
        assertTrue(d.handle("status", emptyMap(), callerUid = -1).startsWith("{\"ok\":true"))
    }

    @Test fun malformedCommandNamesAreRejected() = runTest {
        for (cmd in listOf("tasks list", "a".repeat(80), "tasks.list;rm", "x.y.z")) {
            assertTrue(cmd, d.handle(cmd, emptyMap()).contains("bad_command"))
        }
    }

    @Test fun argumentLimits() = runTest {
        val many = (1..40).associate { "k$it" to "v" }
        assertTrue(d.handle("tasks.create", many).contains("too many arguments"))
        assertTrue(d.handle("tasks.create", mapOf("title" to "x".repeat(201))).contains("bad_args"))
        assertTrue(d.handle("habits.create", mapOf("name" to "ok", "times" to "21")).contains("bad_args"))
        assertTrue(d.handle("audit.list", mapOf("limit" to "100000")).contains("bad_args"))
        assertTrue(d.handle("tasks.create", mapOf("title" to "x", "bad-key" to "1")).contains("bad_args"))
        assertTrue(b.tasks.isEmpty() && b.habits.isEmpty())
    }

    @Test fun quantityRangeIsChecked() = runTest {
        val id = b.saveHabit(HabitEntity(name = "w", iconKey = "x", colorArgb = 0, anchorEpochDay = 0, createdAt = 0))
        b.saveHabit(HabitEntity(name = "other", iconKey = "x", colorArgb = 0, anchorEpochDay = 0, createdAt = 0)) // not the last of the day
        assertTrue(d.handle("habits.checkin", mapOf("id" to "$id", "qty" to "NaN")).contains("bad_args"))
        assertTrue(d.handle("habits.checkin", mapOf("id" to "$id", "qty" to "-1")).contains("bad_args"))
        assertTrue(d.handle("habits.checkin", mapOf("id" to "$id", "qty" to "250")).startsWith("{\"ok\":true"))
    }

    @Test fun archiveUnarchiveAndReorder() = runTest {
        val a = b.saveHabit(HabitEntity(name = "a", iconKey = "x", colorArgb = 0, anchorEpochDay = 0, createdAt = 0, sortOrder = 0))
        val c = b.saveHabit(HabitEntity(name = "c", iconKey = "x", colorArgb = 0, anchorEpochDay = 0, createdAt = 0, sortOrder = 1))
        val e = b.saveHabit(HabitEntity(name = "e", iconKey = "x", colorArgb = 0, anchorEpochDay = 0, createdAt = 0, sortOrder = 2))
        assertTrue(d.handle("habits.archive", mapOf("id" to "$c")).contains("forbidden_weaken")) // due today and not done
        assertTrue(d.handle("habits.checkin", mapOf("id" to "$c")).startsWith("{\"ok\":true"))
        d.handle("habits.archive", mapOf("id" to "$c"))
        assertTrue(b.habits.getValue(c).archived)
        d.handle("habits.unarchive", mapOf("id" to "$c"))
        assertFalse(b.habits.getValue(c).archived)
        val out = d.handle("habits.reorder", mapOf("ids" to "$e,$a"))
        assertTrue(out, out.contains("\"order\":[$e,$a,$c]"))
        assertEquals(listOf(e, a, c), b.habits.values.sortedBy { it.sortOrder }.map { it.id })
        assertTrue(d.handle("habits.reorder", mapOf("ids" to "$a,$a")).contains("bad_args"))
        assertTrue(d.handle("habits.reorder", mapOf("ids" to "9999")).contains("not_found"))
    }

    @Test fun theCheckInThatCompletesTheDayIsPhoneOnly() = runTest {
        val a = b.saveHabit(HabitEntity(name = "a", iconKey = "x", colorArgb = 0, anchorEpochDay = 0, createdAt = 0))
        val c = b.saveHabit(HabitEntity(name = "c", iconKey = "x", colorArgb = 0, anchorEpochDay = 0, createdAt = 0))
        assertTrue(d.handle("habits.checkin", mapOf("id" to "$a")).startsWith("{\"ok\":true"))
        val last = d.handle("habits.checkin", mapOf("id" to "$c"))
        assertTrue(last, last.contains("forbidden"))
    }

    @Test fun aTinyAmountDoesNotMakeAQuantityHabitLooseable() = runTest {
        val id = b.saveHabit(HabitEntity(name = "w", iconKey = "x", colorArgb = 0, anchorEpochDay = 0, createdAt = 0, targetQuantity = 10.0))
        b.saveHabit(HabitEntity(name = "other", iconKey = "x", colorArgb = 0, anchorEpochDay = 0, createdAt = 0))
        assertTrue(d.handle("habits.checkin", mapOf("id" to "$id", "qty" to "0.01")).startsWith("{\"ok\":true"))
        assertTrue(d.handle("habits.update", mapOf("id" to "$id", "target" to "0.01")).contains("forbidden_weaken"))
        assertTrue(d.handle("habits.archive", mapOf("id" to "$id")).contains("forbidden_weaken"))
    }

    @Test fun withoutAnUnlockPathTheGuardsStayOut() = runTest {
        b.settings = b.settings.copy(allDoneUnlock = false, hardMode = false)
        val id = b.saveHabit(HabitEntity(name = "w", iconKey = "x", colorArgb = 0, anchorEpochDay = 0, createdAt = 0, timesPerDay = 3))
        assertTrue(d.handle("habits.update", mapOf("id" to "$id", "times" to "2")).startsWith("{\"ok\":true"))
        assertTrue(d.handle("habits.archive", mapOf("id" to "$id")).startsWith("{\"ok\":true"))
    }

    @Test fun whatIsOwedTodayCannotBeLoweredRemotely() = runTest {
        val id = b.saveHabit(HabitEntity(name = "p", iconKey = "x", colorArgb = 0, anchorEpochDay = 0, createdAt = 0, timesPerDay = 3))
        assertTrue(d.handle("habits.update", mapOf("id" to "$id", "times" to "2")).contains("forbidden_weaken"))
        assertTrue(d.handle("habits.update", mapOf("id" to "$id", "times" to "4")).startsWith("{\"ok\":true"))
    }

    @Test fun weakeningHabitFieldsIsForbidden() = runTest {
        val id = b.saveHabit(HabitEntity(name = "p", iconKey = "x", colorArgb = 0, anchorEpochDay = 0, createdAt = 0, checkInMode = CheckInMode.PHOTO))
        assertTrue(d.handle("habits.update", mapOf("id" to "$id", "mode" to "tap")).contains("forbidden"))
        assertTrue(d.handle("habits.update", mapOf("id" to "$id", "photoRequired" to "false")).contains("forbidden"))
        assertTrue(d.handle("habits.update", mapOf("id" to "$id", "rewardMinutes" to "60")).contains("forbidden"))
        for (cmd in listOf("reward.mode", "segment.enable", "guard.off")) {
            assertTrue(d.handle(cmd, emptyMap()).contains("forbidden"))
        }
        assertEquals(CheckInMode.PHOTO, b.habits.getValue(id).checkInMode)
    }

    @Test fun timedFieldsOnlyTightenRemotely() = runTest {
        val id = b.saveHabit(HabitEntity(name = "ex", iconKey = "x", colorArgb = 0, anchorEpochDay = 0, createdAt = 0, rewardMinutes = 5))
        suspend fun ok(m: Map<String, String>) = d.handle("habits.update", mapOf("id" to "$id") + m)
        // Not timed before: sessions may pay at most 1 earned minute per focused minute.
        assertTrue(ok(mapOf("focus_options" to "10:15")).contains("forbidden_weaken"))
        assertTrue(ok(mapOf("focus_min" to "10", "focus_options" to "10:5,20:10,30:15")).startsWith("{\"ok\":true"))
        assertEquals("10:5,20:10,30:15", b.habits.getValue(id).focusOptions)
        assertTrue(ok(mapOf("focus_options" to "10:5,20:12")).contains("forbidden_weaken"))
        assertTrue(ok(mapOf("focus_options" to "20:8")).startsWith("{\"ok\":true"))
        assertTrue(ok(mapOf("focus_min" to "none", "focus_options" to "none")).contains("forbidden_weaken"))

        val m = b.saveHabit(HabitEntity(name = "music", iconKey = "x", colorArgb = 0, anchorEpochDay = 0, createdAt = 0))
        suspend fun up(a: Map<String, String>) = d.handle("habits.update", mapOf("id" to "$m") + a)
        assertTrue(up(mapOf("stopwatch" to "on", "sw_rate" to "0.5", "sw_min" to "10")).startsWith("{\"ok\":true"))
        assertTrue(b.habits.getValue(m).stopwatch)
        assertTrue(up(mapOf("sw_rate" to "0.6")).contains("forbidden_weaken"))
        assertTrue(up(mapOf("sw_min" to "5")).contains("forbidden_weaken"))
        assertTrue(up(mapOf("stopwatch" to "off")).contains("forbidden_weaken"))
        assertTrue(up(mapOf("sw_cap" to "60")).startsWith("{\"ok\":true"))
        assertTrue(up(mapOf("sw_cap" to "none")).contains("forbidden_weaken"))
        assertTrue(d.handle("settings.tighten", mapOf("comebackBonus" to "off")).startsWith("{\"ok\":true"))
        assertTrue(d.handle("settings.tighten", mapOf("comebackBonus" to "on")).contains("forbidden_weaken"))
    }

    @Test fun reorderKeepsUnlistedIds() {
        assertEquals(listOf(3L, 1L, 2L, 4L), RemoteDispatcher.reorder(listOf(1, 2, 3, 4), listOf(3, 1)))
    }

    @Test fun soundsListAndSetOnTask() = kotlinx.coroutines.runBlocking {
        val b = FakeBackend(); val d = RemoteDispatcher(b)
        assertTrue(d.handle("sounds.list", emptyMap()).contains("custom:gong.mp3"))
        val out = d.handle("tasks.create", mapOf("title" to "x", "sound" to "custom:gong.mp3"))
        assertTrue(out, out.contains("\"ok\":true"))
        assertEquals("file:///m/gong.mp3", b.alarms.values.single().soundUri)
        d.handle("tasks.update", mapOf("id" to b.tasks.keys.first().toString(), "sound" to "default"))
        assertEquals(null, b.alarms.values.single().soundUri)
        val n = b.tasks.size
        assertTrue(d.handle("tasks.create", mapOf("title" to "y", "sound" to "custom:nope.mp3")).contains("bad_args"))
        assertEquals(n, b.tasks.size)
    }

    @Test fun chargingCanOnlyGetStricterRemotely() = runTest {
        d.handle("rules.block_app", mapOf("pkg" to "com.aspiro.tidal", "label" to "TIDAL"))
        b.rules["com.aspiro.tidal"] = b.rules.getValue("com.aspiro.tidal").copy(meterPct = 50)
        assertTrue(d.handle("rules.list", emptyMap()).contains("\"charging\":\"50\""))
        assertTrue(d.handle("rules.set_charging", mapOf("pkg" to "com.aspiro.tidal", "rate" to "25")).contains("forbidden_weaken"))
        assertEquals(50, b.rules.getValue("com.aspiro.tidal").meterPct)
        assertTrue(d.handle("rules.set_charging", mapOf("pkg" to "com.aspiro.tidal", "rate" to "100")).contains("\"changed\":true"))
        assertEquals(100, b.rules.getValue("com.aspiro.tidal").meterPct)
        assertTrue(d.handle("rules.set_charging", mapOf("pkg" to "com.aspiro.tidal", "rate" to "full")).contains("\"rate\":\"100\""))
        assertEquals(100, b.rules.getValue("com.aspiro.tidal").meterPct)
        assertTrue(d.handle("rules.set_charging", mapOf("pkg" to "com.aspiro.tidal", "rate" to "50")).contains("forbidden_weaken"))
        assertTrue(d.handle("rules.set_charging", mapOf("pkg" to "com.aspiro.tidal", "rate" to "33")).contains("bad_args"))
        assertTrue(d.handle("rules.set_charging", mapOf("pkg" to "com.nope.app", "rate" to "100")).contains("not_found"))
    }

    @Test fun rollingExpiryCanOnlyBeLoweredRemotely() = runTest {
        assertTrue(d.handle("settings.tighten", mapOf("rollingExpiryHours" to "15")).contains("\"changed\":true"))
        assertEquals(15, b.settings.rollingExpiryHours)
        assertTrue(d.handle("settings.tighten", mapOf("rollingExpiryHours" to "24")).contains("forbidden_weaken"))
        assertEquals(15, b.settings.rollingExpiryHours)
    }

    @Test fun overTargetRewardOffRemotelyOnlyOnPhone() = runTest {
        val out = d.handle("habits.create", mapOf("name" to "Pushups", "times" to "2", "over_target_reward" to "off"))
        assertTrue(out, out.startsWith("{\"ok\":true"))
        val h = b.habits.values.single()
        assertFalse(h.overTargetReward)
        assertTrue(d.handle("habits.update", mapOf("id" to "${h.id}", "over_target_reward" to "on")).contains("forbidden_weaken"))
        assertFalse(b.habits.getValue(h.id).overTargetReward)
        val g = b.saveHabit(HabitEntity(name = "g", iconKey = "x", colorArgb = 0, anchorEpochDay = 0, createdAt = 0))
        assertTrue(b.habits.getValue(g).overTargetReward)
        assertTrue(d.handle("habits.update", mapOf("id" to "$g", "over_target_reward" to "off")).startsWith("{\"ok\":true"))
        assertFalse(b.habits.getValue(g).overTargetReward)
    }
}
