package com.earnd.app.remote

import com.earnd.app.data.entity.CheckInMode
import com.earnd.app.data.entity.DaySegment
import com.earnd.app.data.entity.HabitEntity
import com.earnd.app.data.entity.Priority
import com.earnd.app.data.entity.RepeatKind
import com.earnd.app.data.entity.ScheduleKind
import com.earnd.app.data.entity.TaskEntity
import com.earnd.app.domain.HabitSchedule
import com.earnd.app.domain.TaskBucket
import com.earnd.app.domain.TaskService
import java.time.LocalDate

/**
 * Pure command dispatcher for the adb-only remote interface. Every call returns one JSON
 * object: `{"ok":true,...}` or `{"ok":false,"error":"<code>","message":...}`.
 *
 * Only habit/task management and read-only status are reachable. Anything touching the time
 * bank, unlock sessions, blocking, bedtime, vacation or settings is refused as `forbidden`.
 */
class RemoteDispatcher(private val backend: RemoteBackend) {

    suspend fun handle(command: String?, raw: Map<String, String>): String {
        val cmd = command?.trim()?.lowercase().orEmpty()
        val result: Map<String, Any?> = try {
            when {
                cmd.isEmpty() -> fail("missing_command")
                isForbidden(cmd) -> fail("forbidden")
                cmd !in COMMANDS -> fail("unknown_command", "known: ${COMMANDS.sorted().joinToString(" ")}")
                !backend.remoteEnabled() -> fail("disabled", "Remote access is turned off in Earnd settings")
                else -> run(cmd, RemoteArgs(raw))
            }
        } catch (e: ForbiddenField) {
            fail("forbidden", e.message)
        } catch (e: BadArgs) {
            fail("bad_args", e.message)
        } catch (e: NotFound) {
            fail("not_found", e.message)
        } catch (e: Exception) {
            fail("internal", e.javaClass.simpleName)
        }
        val ok = result["ok"] == true
        // Read-only commands are not audited; writes and refusals are.
        if (cmd !in READ_ONLY || !ok) {
            runCatching {
                backend.appendAudit(
                    RemoteAudit.entry(backend.now(), cmd.ifEmpty { "?" }, ok, result["id"] as? Long, result["error"] as? String),
                )
            }
        }
        return Json.encode(result)
    }

    private suspend fun run(cmd: String, a: RemoteArgs): Map<String, Any?> = when (cmd) {
        "help" -> ok("commands" to COMMANDS.sorted())
        "status" -> ok("status" to backend.status())
        "audit.list" -> ok("entries" to backend.auditLog().takeLast(a.int("limit") ?: 50).map(::RawJson))
        "habits.list" -> habitsList(a)
        "habits.get" -> ok("habit" to habitJson(habitOr404(a.requireLong("id")), detailed = true))
        "habits.create" -> habitSave(a, null)
        "habits.update" -> habitSave(a, habitOr404(a.requireLong("id")))
        "habits.archive" -> {
            val h = habitOr404(a.requireLong("id"))
            backend.archiveHabit(h.id)
            ok("id" to h.id)
        }
        "habits.checkin" -> habitCheckIn(a)
        "habits.undo" -> habitUndo(a)
        "tasks.list" -> tasksList(a)
        "tasks.get" -> {
            val t = taskOr404(a.requireLong("id"))
            ok("task" to taskJson(t) + ("subtasks" to backend.subtasks(t.id).map(::taskJson)))
        }
        "tasks.create" -> taskSave(a, null)
        "tasks.update" -> taskSave(a, taskOr404(a.requireLong("id")))
        "tasks.complete" -> {
            val t = taskOr404(a.requireLong("id"))
            if (t.completedAt != null) fail("already_done") else {
                val next = backend.completeTask(t.id)
                ok("id" to t.id, "next" to next?.let(::taskJson))
            }
        }
        "tasks.uncomplete" -> {
            val t = taskOr404(a.requireLong("id"))
            if (t.completedAt == null) fail("not_done") else {
                backend.uncompleteTask(t.id)
                ok("id" to t.id)
            }
        }
        "tasks.delete" -> {
            val t = taskOr404(a.requireLong("id"))
            backend.deleteTask(t.id)
            ok("id" to t.id)
        }
        else -> fail("unknown_command")
    }

    // ---- habits ----

    private suspend fun habitsList(a: RemoteArgs): Map<String, Any?> {
        val today = backend.today()
        val list = backend.habits(includeArchived = a.bool("all") == true)
        return ok("today" to today.toString(), "habits" to list.map { habitJson(it, detailed = false) })
    }

    private suspend fun habitJson(h: HabitEntity, detailed: Boolean): Map<String, Any?> {
        val today = backend.today()
        val done = backend.checkInsToday(h.id)
        val m = Json.obj(
            "id" to h.id,
            "name" to h.name,
            "archived" to h.archived,
            "scheduledToday" to HabitSchedule.isScheduled(h, today),
            "doneToday" to done.size,
            "timesPerDay" to h.timesPerDay,
            "complete" to (done.size >= h.timesPerDay),
            "mode" to h.checkInMode,
            "minutesPerCheckIn" to h.minutesPerCheckIn,
        )
        if (detailed) {
            m += Json.obj(
                "icon" to h.iconKey,
                "color" to colorHex(h.colorArgb),
                "schedule" to h.scheduleKind,
                "days" to RemoteArgs.maskToDays(h.weekdayMask),
                "every" to h.everyNDays,
                "anchor" to LocalDate.ofEpochDay(h.anchorEpochDay).toString(),
                "segment" to h.segment,
                "target" to h.targetQuantity,
                "unit" to h.unit,
                "streak" to backend.streak(h.id),
                "checkInsToday" to done.map { Json.obj("id" to it.id, "at" to it.at, "quantity" to it.quantity) },
            )
        }
        return m
    }

    private suspend fun habitSave(a: RemoteArgs, existing: HabitEntity?): Map<String, Any?> {
        rejectRewardFields(a, "minutes", "minutesPerCheckIn")
        val today = backend.today()
        val name = a.text("name") ?: existing?.name ?: bad("missing name")
        val base = existing ?: HabitEntity(
            name = name,
            iconKey = "spark",
            colorArgb = DEFAULT_COLOR,
            anchorEpochDay = today.toEpochDay(),
            createdAt = backend.now(),
        )
        val schedule = a.enum<ScheduleKind>("schedule") ?: base.scheduleKind
        val h = base.copy(
            name = name,
            iconKey = a.text("icon") ?: base.iconKey,
            colorArgb = a.str("color")?.let(::parseColor) ?: base.colorArgb,
            timesPerDay = (a.int("times") ?: base.timesPerDay).also { if (it !in 1..20) bad("times must be 1..20") },
            scheduleKind = schedule,
            weekdayMask = a.weekdayMask("days") ?: base.weekdayMask,
            everyNDays = (a.int("every") ?: base.everyNDays).also { if (it !in 1..365) bad("every must be 1..365") },
            anchorEpochDay = (a.date("start", today) as? RemoteArgs.Field.Set)?.value ?: base.anchorEpochDay,
            segment = a.enum<DaySegment>("segment") ?: base.segment,
            targetQuantity = if (a.has("target")) a.str("target")?.takeIf { it != "none" }?.let { a.double("target") } else base.targetQuantity,
            unit = if (a.has("unit")) a.text("unit") else base.unit,
            checkInMode = a.enum<CheckInMode>("mode") ?: base.checkInMode,
        )
        val id = backend.saveHabit(h)
        return ok("id" to id)
    }

    private suspend fun habitCheckIn(a: RemoteArgs): Map<String, Any?> {
        val h = habitOr404(a.requireLong("id"))
        if (h.archived) return fail("archived")
        // Photo habits need proof taken on the phone itself.
        if (h.checkInMode == CheckInMode.PHOTO) return fail("photo_required", "This habit is checked in with a photo on the phone")
        val c = backend.checkIn(h.id, a.double("qty"))
        return ok("id" to h.id, "checkInId" to c.id, "minutesEarned" to c.minutesEarned)
    }

    private suspend fun habitUndo(a: RemoteArgs): Map<String, Any?> {
        val h = habitOr404(a.requireLong("id"))
        val last = backend.checkInsToday(h.id).maxByOrNull { it.at } ?: return fail("nothing_to_undo")
        backend.undoCheckIn(last.id)
        return ok("id" to h.id, "checkInId" to last.id)
    }

    // ---- tasks ----

    private suspend fun tasksList(a: RemoteArgs): Map<String, Any?> {
        val today = backend.today().toEpochDay()
        val want = a.enum<TaskBucket>("bucket")
        val top = backend.openTasks().filter { it.parentId == null }
            .map { it to TaskService.bucketOf(it, today) }
            .filter { want == null || it.second == want }
            .sortedWith(compareBy({ it.second.ordinal }, { it.first.dueEpochDay ?: Long.MAX_VALUE }, { it.first.sortOrder }))
        return ok("tasks" to top.map { (t, b) -> taskJson(t) + ("bucket" to b) })
    }

    private fun taskJson(t: TaskEntity): Map<String, Any?> = Json.obj(
        "id" to t.id,
        "parentId" to t.parentId,
        "title" to t.title,
        "notes" to t.notes.ifEmpty { null },
        "due" to t.dueEpochDay?.let { LocalDate.ofEpochDay(it).toString() },
        "time" to t.dueMinuteOfDay?.let { "%02d:%02d".format(it / 60, it % 60) },
        "priority" to t.priority,
        "repeat" to t.repeatKind,
        "repeatDays" to if (t.repeatKind == RepeatKind.WEEKLY) RemoteArgs.maskToDays(t.repeatWeekdayMask) else null,
        "repeatInterval" to t.repeatInterval,
        "rewardMinutes" to t.rewardMinutes,
        "done" to (t.completedAt != null),
    )

    private suspend fun taskSave(a: RemoteArgs, existing: TaskEntity?): Map<String, Any?> {
        rejectRewardFields(a, "reward", "rewardMinutes")
        val today = backend.today()
        val title = a.text("title") ?: existing?.title ?: bad("missing title")
        val parent = a.long("parent")?.let { pid ->
            val p = taskOr404(pid)
            if (p.parentId != null) bad("subtasks can only be one level deep")
            pid
        }
        val base = existing ?: TaskEntity(title = title, parentId = parent, createdAt = backend.now())
        val repeat = a.enum<RepeatKind>("repeat") ?: base.repeatKind
        val due = when (val d = a.date("due", today)) {
            is RemoteArgs.Field.Set -> d.value
            RemoteArgs.Field.Absent -> base.dueEpochDay
        }
        val time = when (val t = a.time("time")) {
            is RemoteArgs.Field.Set -> t.value
            RemoteArgs.Field.Absent -> base.dueMinuteOfDay
        }
        var mask = a.weekdayMask("days") ?: base.repeatWeekdayMask
        if (repeat == RepeatKind.WEEKLY && mask == 0) {
            mask = 1 shl (LocalDate.ofEpochDay(due ?: today.toEpochDay()).dayOfWeek.value - 1)
        }
        if (repeat != RepeatKind.NONE && due == null && base.parentId == null) bad("repeating tasks need a due date")
        val t = base.copy(
            title = title,
            notes = if (a.has("notes")) a.str("notes").orEmpty() else base.notes,
            dueEpochDay = due,
            dueMinuteOfDay = if (due == null) null else time,
            priority = a.enum<Priority>("priority") ?: base.priority,
            repeatKind = repeat,
            repeatWeekdayMask = mask,
            repeatInterval = (a.int("interval") ?: base.repeatInterval).also { if (it !in 1..365) bad("interval must be 1..365") },
        )
        return ok("id" to backend.saveTask(t))
    }

    // ---- helpers ----

    /** Rewards feed the time bank, so they can only be changed on the phone. */
    private fun rejectRewardFields(a: RemoteArgs, vararg keys: String) {
        if (keys.any(a::has)) throw ForbiddenField()
    }

    private suspend fun habitOr404(id: Long) = backend.habit(id) ?: throw NotFound("habit $id")
    private suspend fun taskOr404(id: Long) = backend.task(id) ?: throw NotFound("task $id")

    private class NotFound(message: String) : Exception(message)
    private class ForbiddenField : Exception("reward minutes can only be changed on the phone")

    companion object {
        val COMMANDS = setOf(
            "help", "status", "audit.list",
            "habits.list", "habits.get", "habits.create", "habits.update", "habits.archive",
            "habits.checkin", "habits.undo",
            "tasks.list", "tasks.get", "tasks.create", "tasks.update",
            "tasks.complete", "tasks.uncomplete", "tasks.delete",
        )
        private val READ_ONLY = setOf("help", "status", "audit.list", "habits.list", "habits.get", "tasks.list", "tasks.get")

        /** Command roots that are never available remotely, whatever follows them. */
        val FORBIDDEN_ROOTS = setOf(
            "unlock", "lock", "session", "sessions", "spend", "balance", "bank", "ledger",
            "apps", "app", "rules", "block", "blocking", "blocklist", "bedtime", "vacation",
            "settings", "setting", "hardmode", "remote",
        )

        fun isForbidden(cmd: String): Boolean = cmd.substringBefore('.') in FORBIDDEN_ROOTS

        private const val DEFAULT_COLOR = 0xFF0FA3A3.toInt()

        fun parseColor(s: String): Int {
            val hex = s.removePrefix("#")
            val v = hex.toLongOrNull(16) ?: bad("bad color: $s")
            return when (hex.length) {
                6 -> (0xFF000000 or v).toInt()
                8 -> v.toInt()
                else -> bad("bad color: $s")
            }
        }

        fun colorHex(argb: Int) = "#%06X".format(argb and 0xFFFFFF)

        private fun ok(vararg pairs: Pair<String, Any?>): Map<String, Any?> = Json.obj("ok" to true, *pairs)
        private fun fail(code: String, message: String? = null): Map<String, Any?> =
            Json.obj("ok" to false, "error" to code, "message" to message)
    }
}
