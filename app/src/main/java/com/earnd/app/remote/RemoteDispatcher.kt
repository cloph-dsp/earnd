package com.earnd.app.remote

import com.earnd.app.data.Settings
import com.earnd.app.data.SettingsCodec
import com.earnd.app.data.entity.AlarmConfigEntity
import com.earnd.app.data.entity.AlarmStyle
import com.earnd.app.data.entity.AppRule
import com.earnd.app.data.entity.CheckInMode
import com.earnd.app.data.entity.DaySegment
import com.earnd.app.data.entity.HabitEntity
import com.earnd.app.data.entity.NagMode
import com.earnd.app.data.entity.OwnerKind
import com.earnd.app.data.entity.Priority
import com.earnd.app.data.entity.RepeatKind
import com.earnd.app.data.entity.ScheduleKind
import com.earnd.app.data.entity.TaskEntity
import com.earnd.app.domain.HabitProgress
import com.earnd.app.domain.HabitSchedule
import com.earnd.app.domain.TaskBucket
import com.earnd.app.domain.TaskService
import com.earnd.app.ui.apps.normalizeHost
import com.earnd.app.ui.settings.SettingsChanges
import java.time.LocalDate

/**
 * Pure command dispatcher for the adb-only remote interface. Every call returns one JSON
 * object: `{"ok":true,...}` or `{"ok":false,"error":"<code>","message":...}`.
 *
 * Policy: creating things and strengthening blocking is allowed; weakening never is. Habits,
 * tasks, reminders and alarms can be configured; apps/sites can be blocked (never unblocked);
 * settings can only be tightened (classified by [SettingsChanges.isWeakening], the same rule the
 * phone UI uses). Raising rewards or dropping photo proof fails with `forbidden_weaken`. The time
 * bank, unlock sessions, vacation and remote access itself are refused as `forbidden`.
 */
class RemoteDispatcher(private val backend: RemoteBackend) {

    /**
     * @param callerUid the sending app's UID when the platform reports it, else null. Only the
     *   shell and root may call; the manifest's DUMP permission is the primary guard.
     */
    suspend fun handle(command: String?, raw: Map<String, String>, callerUid: Int? = null): String {
        val cmd = command?.trim()?.lowercase().orEmpty()
        val result: Map<String, Any?> = try {
            when {
                !RemoteCaller.isAllowed(callerUid) -> fail("forbidden_caller")
                cmd.isEmpty() -> fail("missing_command")
                !COMMAND_SHAPE.matches(cmd) -> fail("bad_command")
                isForbidden(cmd) -> fail("forbidden")
                cmd !in COMMANDS -> fail("unknown_command", "known: ${COMMANDS.sorted().joinToString(" ")}")
                !backend.remoteEnabled() -> fail("disabled", "Remote access is turned off in Earnd settings")
                else -> run(cmd, RemoteArgs(raw))
            }
        } catch (e: ForbiddenField) {
            fail("forbidden", e.message)
        } catch (e: WeakenForbidden) {
            fail("forbidden_weaken", e.message)
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
                    RemoteAudit.entry(backend.now(), cmd.takeIf { COMMAND_SHAPE.matches(it) } ?: "?", ok, result["id"] as? Long, result["error"] as? String),
                )
            }
        }
        return Json.encode(result)
    }

    private suspend fun run(cmd: String, a: RemoteArgs): Map<String, Any?> = when (cmd) {
        "help" -> ok("commands" to COMMANDS.sorted())
        "status" -> ok("status" to backend.status())
        "audit.list" -> ok("entries" to backend.auditLog().takeLast(a.int("limit", 1..500) ?: 50).map(::RawJson))
        "habits.list" -> habitsList(a)
        "habits.get" -> ok("habit" to habitJson(habitOr404(a.requireLong("id")), detailed = true))
        "habits.create" -> habitSave(a, null)
        "habits.update" -> habitSave(a, habitOr404(a.requireLong("id")))
        "habits.archive" -> {
            val h = habitOr404(a.requireLong("id"))
            backend.archiveHabit(h.id)
            ok("id" to h.id)
        }
        "habits.unarchive" -> {
            val h = habitOr404(a.requireLong("id"))
            if (h.archived) backend.saveHabit(h.copy(archived = false))
            ok("id" to h.id)
        }
        "habits.reorder" -> habitReorder(a)
        "habits.checkin" -> habitCheckIn(a)
        "habits.undo" -> habitUndo(a)
        "tasks.list" -> tasksList(a)
        "tasks.get" -> {
            val t = taskOr404(a.requireLong("id"))
            ok(
                "task" to taskJson(t) + ("alarm" to backend.alarmConfig(OwnerKind.TASK, t.id)?.let(::alarmJson)) +
                    ("subtasks" to backend.subtasks(t.id).map(::taskJson)),
            )
        }
        "rules.list" -> rulesList()
        "rules.block_app" -> blockApp(a)
        "rules.block_site" -> blockSite(a)
        "rules.blockable_apps" -> {
            val blocked = backend.appRules().filter { it.rule == AppRule.LEISURE && !it.isWebsite }.map { it.key }.toSet()
            ok("apps" to backend.launchableApps().map { (label, pkg) -> Json.obj("label" to label, "pkg" to pkg, "blocked" to (pkg in blocked)) })
        }
        "settings.get" -> ok(
            "settings" to SettingsCodec.encode(backend.settings()).toSortedMap().mapValues { it.value.ifEmpty { null } },
            "tightenable" to TIGHTEN_KEYS.sorted(),
        )
        "settings.tighten" -> settingsTighten(a)
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
            "order" to h.sortOrder,
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
                "rewardMinutes" to HabitProgress.rewardMinutes(h, backend.settings()),
                "photoRequired" to photoOn(h, backend.settings()),
                "remindDays" to if (h.reminderDays == 0) null else RemoteArgs.maskToDays(h.reminderDays),
                "message" to h.reminderMessage,
                "alarm" to backend.alarmConfig(OwnerKind.HABIT, h.id)?.let(::alarmJson),
                "checkInsToday" to done.map { Json.obj("id" to it.id, "at" to it.at, "quantity" to it.quantity) },
            )
        }
        return m
    }

    private suspend fun habitSave(a: RemoteArgs, existing: HabitEntity?): Map<String, Any?> {
        val s = backend.settings()
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
            timesPerDay = a.int("times", 1..20) ?: base.timesPerDay,
            scheduleKind = schedule,
            weekdayMask = a.weekdayMask("days") ?: base.weekdayMask,
            everyNDays = a.int("every", 1..365) ?: base.everyNDays,
            anchorEpochDay = (a.date("start", today) as? RemoteArgs.Field.Set)?.value ?: base.anchorEpochDay,
            segment = a.enum<DaySegment>("segment") ?: base.segment,
            targetQuantity = if (a.has("target")) a.str("target")?.takeIf { it != "none" }?.let { a.double("target", QTY_RANGE) } else base.targetQuantity,
            unit = if (a.has("unit")) a.text("unit") else base.unit,
            checkInMode = a.enum<CheckInMode>("mode") ?: base.checkInMode,
            reminderDays = when (a.str("remind_days")?.lowercase()) {
                null -> base.reminderDays
                "", "none", "all" -> 0
                else -> a.weekdayMask("remind_days")!!
            },
            reminderMessage = if (a.has("message")) a.text("message")?.takeIf { !it.equals("none", true) } else base.reminderMessage,
        ).let { h ->
            val reward = a.intOrNone(REWARD_KEYS, REWARD_RANGE)
            val photo = PHOTO_KEYS.firstNotNullOfOrNull(a::bool)
            h.copy(
                rewardMinutes = if (reward != null) reward.value else h.rewardMinutes,
                photoRequired = photo ?: h.photoRequired,
                checkInMode = when {
                    a.has("mode") || photo == null -> h.checkInMode
                    photo -> CheckInMode.PHOTO
                    else -> CheckInMode.TAP
                },
            )
        }
        if (existing != null) {
            // Earning more per check-in or dropping photo proof weakens the habit: phone only.
            if (HabitProgress.rewardMinutes(h, s) > HabitProgress.rewardMinutes(existing, s)) {
                throw WeakenForbidden("reward minutes can only be lowered remotely")
            }
            if (photoOn(existing, s) && !photoOn(h, s)) {
                throw WeakenForbidden("photo proof can only be turned off on the phone")
            }
        }
        val id = backend.saveHabit(h)
        saveAlarm(a, OwnerKind.HABIT, id, habit = true)
        return ok("id" to id)
    }

    private fun photoOn(h: HabitEntity, s: Settings) = h.checkInMode == CheckInMode.PHOTO || HabitProgress.photoRequired(h, s)

    private suspend fun habitCheckIn(a: RemoteArgs): Map<String, Any?> {
        val h = habitOr404(a.requireLong("id"))
        if (h.archived) return fail("archived")
        // Photo habits need proof taken on the phone itself.
        if (h.checkInMode == CheckInMode.PHOTO) return fail("photo_required", "This habit is checked in with a photo on the phone")
        val c = backend.checkIn(h.id, a.double("qty", QTY_RANGE))
        return ok("id" to h.id, "checkInId" to c.id, "minutesEarned" to c.minutesEarned)
    }

    private suspend fun habitUndo(a: RemoteArgs): Map<String, Any?> {
        val h = habitOr404(a.requireLong("id"))
        val last = backend.checkInsToday(h.id).maxByOrNull { it.at } ?: return fail("nothing_to_undo")
        backend.undoCheckIn(last.id)
        return ok("id" to h.id, "checkInId" to last.id)
    }

    /** `ids=3,1,2` puts those habits first, in that order; the rest keep their relative order. */
    private suspend fun habitReorder(a: RemoteArgs): Map<String, Any?> {
        val ids = a.ids("ids") ?: bad("missing ids")
        val all = backend.habits(includeArchived = true)
        val byId = all.associateBy { it.id }
        ids.firstOrNull { it !in byId }?.let { throw NotFound("habit $it") }
        val ordered = reorder(all.sortedWith(compareBy({ it.sortOrder }, { it.id })).map { it.id }, ids)
        ordered.forEachIndexed { i, id ->
            val h = byId.getValue(id)
            if (h.sortOrder != i) backend.saveHabit(h.copy(sortOrder = i))
        }
        return ok("order" to ordered)
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
            repeatInterval = a.int("interval", 1..365) ?: base.repeatInterval,
        ).let { t -> a.intOrNone(REWARD_KEYS, REWARD_RANGE)?.let { t.copy(rewardMinutes = it.value) } ?: t }
        if (existing != null && (t.rewardMinutes ?: 0) > (existing.rewardMinutes ?: 0)) {
            throw WeakenForbidden("task reward can only be lowered remotely")
        }
        val id = backend.saveTask(t)
        saveAlarm(a, OwnerKind.TASK, id, habit = false)
        return ok("id" to id)
    }

    // ---- helpers ----

    // ---- alarms / reminders ----

    /**
     * Applies alarm keys to the item's own config. For habits, `reminders=08:00,14:00,21:00` is
     * stored as a daily reminder at the latest time plus "minutes before" offsets for the others.
     */
    private suspend fun saveAlarm(a: RemoteArgs, kind: OwnerKind, id: Long, habit: Boolean) {
        if (ALARM_KEYS.none(a::has) && !(habit && a.has("reminders"))) return
        val base = backend.alarmConfig(kind, id) ?: AlarmConfigEntity(ownerKind = kind, ownerId = id)
        val nagMax = a.intOrNone(listOf("nag_max"), 1..100)
        var c = base.copy(
            style = a.enum<AlarmStyle>("alarm") ?: base.style,
            atDue = a.bool("at_due") ?: base.atDue,
            offsetsBeforeMin = a.str("offsets")?.let { parseOffsets(it) } ?: base.offsetsBeforeMin,
            nagMode = a.enum<NagMode>("nag") ?: base.nagMode,
            nagEveryMin = a.int("nag_every", 1..1440) ?: base.nagEveryMin,
            nagMaxCount = if (nagMax != null) nagMax.value else base.nagMaxCount,
            vibrate = a.bool("vibrate") ?: base.vibrate,
            snoozeMaxCount = a.int("snooze_max", 0..20) ?: base.snoozeMaxCount,
        )
        if (habit && a.has("reminders")) {
            if (a.has("offsets")) bad("use either reminders or offsets for a habit")
            val times = parseTimes(a.str("reminders").orEmpty())
            c = if (times.isEmpty()) {
                c.copy(habitReminderMinuteOfDay = null, offsetsBeforeMin = "")
            } else {
                c.copy(habitReminderMinuteOfDay = times.last(), offsetsBeforeMin = times.dropLast(1).map { times.last() - it }.joinToString(","))
            }
        }
        backend.saveAlarmConfig(c.copy(ownerKind = kind, ownerId = id))
    }

    private fun alarmJson(c: AlarmConfigEntity): Map<String, Any?> {
        val offsets = c.offsetsBeforeMin.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it > 0 }
        return Json.obj(
            "reminders" to c.habitReminderMinuteOfDay?.let { r -> (offsets.map { r - it }.filter { it >= 0 } + r).distinct().sorted().map(::hhmm) },
            "style" to c.style,
            "atDue" to c.atDue,
            "offsets" to offsets.ifEmpty { null },
            "nag" to c.nagMode,
            "nagEvery" to c.nagEveryMin,
            "nagMax" to c.nagMaxCount,
            "vibrate" to c.vibrate,
            "snoozeMax" to c.snoozeMaxCount,
        )
    }

    // ---- block rules ----

    private suspend fun rulesList(): Map<String, Any?> {
        val rules = backend.appRules().sortedBy { it.label.lowercase() }
        fun pick(rule: AppRule, web: Boolean) =
            rules.filter { it.rule == rule && it.isWebsite == web }.map { Json.obj("key" to it.key, "label" to it.label) }
        return ok("blockedApps" to pick(AppRule.LEISURE, false), "blockedSites" to pick(AppRule.LEISURE, true), "alwaysOpen" to pick(AppRule.ALLOWED, false))
    }

    private suspend fun blockApp(a: RemoteArgs): Map<String, Any?> {
        val pkg = a.text("pkg") ?: bad("missing pkg")
        if (!PACKAGE_SHAPE.matches(pkg)) bad("bad pkg: $pkg")
        val label = a.text("label") ?: backend.launchableApps().firstOrNull { it.second == pkg }?.first ?: pkg
        backend.blockRule(pkg, label, isWebsite = false)
        return ok("pkg" to pkg, "label" to label)
    }

    private suspend fun blockSite(a: RemoteArgs): Map<String, Any?> {
        val raw = a.text("domain") ?: bad("missing domain")
        val host = normalizeHost(raw)?.takeIf { HOST_SHAPE.matches(it) && it.length <= 253 } ?: bad("bad domain: $raw")
        backend.blockRule(host, host, isWebsite = true)
        return ok("domain" to host)
    }

    // ---- settings ----

    /** Applies key=value pairs only if the result is not weakening by the phone UI's own rule. */
    private suspend fun settingsTighten(a: RemoteArgs): Map<String, Any?> {
        if (a.keys.isEmpty()) bad("give key=value pairs; tightenable: ${TIGHTEN_KEYS.sorted().joinToString(" ")}")
        a.keys.firstOrNull { it !in TIGHTEN_KEYS }?.let { throw WeakenForbidden("$it cannot be changed remotely") }
        val old = backend.settings()
        val enc = SettingsCodec.encode(old).toMutableMap()
        val wanted = a.keys.associateWith { k -> normalizeSetting(k, a.str(k)!!.trim(), enc.getValue(k)) }
        enc += wanted
        val new = SettingsCodec.decode(enc)
        val got = SettingsCodec.encode(new)
        wanted.forEach { (k, v) -> if (got[k] != v) bad("bad $k: ${a.str(k)}") }
        checkSettingRanges(new)
        if (new == old) return ok("changed" to false)
        if (SettingsChanges.isWeakening(old, new)) throw WeakenForbidden("that change loosens blocking; only the phone can do it")
        if (!backend.saveSettings(old, new)) return fail("conflict", "settings changed meanwhile; retry")
        return ok("changed" to true, "settings" to wanted.keys.sorted().associateWith { got.getValue(it).ifEmpty { null } })
    }

    private fun normalizeSetting(key: String, v: String, current: String): String {
        val l = v.lowercase()
        return when {
            l.isEmpty() || l == "none" -> ""
            current == "true" || current == "false" -> when (l) {
                "on", "yes", "1" -> "true"
                "off", "no", "0" -> "false"
                else -> l
            }
            (key.endsWith("StartMin") || key.endsWith("EndMin")) && ':' in v ->
                ((RemoteArgs(mapOf(key to v)).time(key) as RemoteArgs.Field.Set).value ?: bad("bad $key")).toString()
            v.first().isLetter() -> v.uppercase()
            else -> v
        }
    }

    private fun checkSettingRanges(s: Settings) {
        fun chk(k: String, v: Int?, r: IntRange) { if (v != null && v !in r) bad("$k must be ${r.first}..${r.last}") }
        chk("unlockCapMin", s.unlockCapMin, 1..1440)
        chk("weakenCooldownMin", s.weakenCooldownMin, 0..1440)
        chk("bedtimeStartMin", s.bedtimeStartMin, 0..1439)
        chk("bedtimeEndMin", s.bedtimeEndMin, 0..1439)
        chk("bedtimeGraceMin", s.bedtimeGraceMin, 0..120)
        chk("defaultTaskRewardMin", s.defaultTaskRewardMin, REWARD_RANGE)
        chk("defaultHabitRewardMin", s.defaultHabitRewardMin, REWARD_RANGE)
    }

    private suspend fun habitOr404(id: Long) = backend.habit(id) ?: throw NotFound("habit $id")
    private suspend fun taskOr404(id: Long) = backend.task(id) ?: throw NotFound("task $id")

    private class NotFound(message: String) : Exception(message)
    private class ForbiddenField(message: String) : Exception(message)
    private class WeakenForbidden(message: String) : Exception(message)

    companion object {
        val COMMANDS = setOf(
            "help", "status", "audit.list",
            "habits.list", "habits.get", "habits.create", "habits.update", "habits.archive",
            "habits.unarchive", "habits.reorder",
            "habits.checkin", "habits.undo",
            "tasks.list", "tasks.get", "tasks.create", "tasks.update",
            "tasks.complete", "tasks.uncomplete", "tasks.delete",
            "rules.list", "rules.block_app", "rules.block_site", "rules.blockable_apps",
            "settings.get", "settings.tighten",
        )
        private val READ_ONLY = setOf(
            "help", "status", "audit.list", "habits.list", "habits.get", "tasks.list", "tasks.get",
            "rules.list", "rules.blockable_apps", "settings.get",
        )

        /** Command roots that are never available remotely, whatever follows them. */
        val FORBIDDEN_ROOTS = setOf(
            "unlock", "lock", "session", "sessions", "spend", "balance", "bank", "ledger",
            "apps", "app", "block", "blocking", "blocklist", "bedtime", "vacation",
            "setting", "hardmode", "remote", "unblock", "allow",
            "reward", "rewards", "segment", "segments", "guard", "notify",
        )

        /**
         * Roots with a fixed set of allowed commands: anything else under them (e.g. `rules.unblock`,
         * `settings.set`) is refused as forbidden rather than unknown.
         */
        private val GUARDED_ROOTS = setOf("rules", "settings")

        private val REWARD_KEYS = listOf("minutes", "rewardMinutes", "reward")
        private val PHOTO_KEYS = listOf("photo", "photoRequired")
        private val REWARD_RANGE = 0..240
        private val ALARM_KEYS = listOf("alarm", "at_due", "offsets", "nag", "nag_every", "nag_max", "vibrate", "snooze_max")

        /** Settings keys `settings.tighten` accepts (and then only for non-weakening values). */
        val TIGHTEN_KEYS = setOf(
            "hardMode", "blockingEnabled", "allDoneUnlock", "resetPeriod", "weakenCooldownMin",
            "bedtimeEnabled", "bedtimeStartMin", "bedtimeEndMin", "bedtimeGraceMin",
            "defaultTaskRewardMin", "defaultHabitRewardMin", "rewardMode", "photoRequiredDefault",
            "unlockCapMin", "removalPuzzle",
        )

        private val PACKAGE_SHAPE = Regex("[A-Za-z][A-Za-z0-9_]*([.][A-Za-z][A-Za-z0-9_]*)+")
        private val HOST_SHAPE = Regex("([a-z0-9]([a-z0-9-]*[a-z0-9])?[.])+[a-z]([a-z0-9-]*[a-z0-9])?")

        fun hhmm(m: Int) = "%02d:%02d".format(m / 60, m % 60)

        /** `08:00,14:00` -> sorted distinct minutes of day; `none` -> empty. */
        fun parseTimes(v: String): List<Int> {
            if (v.isBlank() || v.equals("none", true)) return emptyList()
            val list = v.split(',').map { it.trim() }.filter { it.isNotEmpty() }.map { t ->
                (RemoteArgs(mapOf("t" to t)).time("t") as? RemoteArgs.Field.Set)?.value ?: bad("bad reminders: $t")
            }.distinct().sorted()
            if (list.size > 12) bad("too many reminders (max 12)")
            return list
        }

        /** `60,15` -> "60,15"; `none` -> "". */
        fun parseOffsets(v: String): String {
            if (v.isBlank() || v.equals("none", true)) return ""
            return v.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                .map { it.toIntOrNull()?.takeIf { n -> n in 1..10_080 } ?: bad("bad offsets: $it") }
                .distinct().sortedDescending().joinToString(",")
        }

        private val COMMAND_SHAPE = Regex("[a-z][a-z0-9_]{0,23}([.][a-z][a-z0-9_]{0,23})?")
        private val QTY_RANGE = 0.0..1_000_000.0

        /** [wanted] first in the given order, then the remaining ids of [current] in their order. */
        fun reorder(current: List<Long>, wanted: List<Long>): List<Long> {
            val w = wanted.toSet()
            return wanted.filter { it in current } + current.filterNot { it in w }
        }

        fun isForbidden(cmd: String): Boolean {
            val root = cmd.substringBefore('.')
            return root in FORBIDDEN_ROOTS || (root in GUARDED_ROOTS && cmd !in COMMANDS)
        }

        private const val DEFAULT_COLOR = 0xFFFFB000.toInt()

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
