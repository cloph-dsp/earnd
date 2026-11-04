package com.earnd.app.ui.habits

import com.earnd.app.ui.theme.Earnd
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import com.earnd.app.ui.theme.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.earnd.app.ui.theme.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.earnd.app.ServiceLocator
import com.earnd.app.data.Settings
import androidx.compose.runtime.collectAsState
import com.earnd.app.data.entity.AlarmConfigEntity
import com.earnd.app.data.entity.CheckInMode
import com.earnd.app.data.entity.DaySegment
import com.earnd.app.data.entity.HabitEntity
import com.earnd.app.data.entity.OwnerKind
import com.earnd.app.data.entity.ScheduleKind
import com.earnd.app.domain.HabitSchedule
import com.earnd.app.domain.RRule
import com.earnd.app.domain.today
import com.earnd.app.ui.alarms.AlarmConfigEditor
import com.earnd.app.ui.alarms.AlarmConfigs
import com.earnd.app.ui.common.ChoiceChips
import com.earnd.app.ui.common.NumberField
import com.earnd.app.ui.common.SectionTitle
import com.earnd.app.ui.common.SwitchRow
import com.earnd.app.ui.common.WeekdayPicker
import com.earnd.app.ui.common.launchUi

@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HabitEditScreen(habitId: Long, onDone: () -> Unit) {
    val sl = ServiceLocator
    var habit by remember { mutableStateOf<HabitEntity?>(null) }
    var alarm by remember { mutableStateOf<AlarmConfigEntity?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(habitId) {
        habit = sl.db.habitDao().get(habitId) ?: HabitEntity(
            name = "", iconKey = "star", colorArgb = HabitLook.colors.first(),
            anchorEpochDay = sl.clock.today().toEpochDay(), createdAt = sl.clock.now(),
            sortOrder = sl.db.habitDao().nextSortOrder(), rewardMinutes = null,
        )
        alarm = AlarmConfigs.custom(OwnerKind.HABIT, habitId)
    }
    val settings by remember { sl.settings.settings }.collectAsState(initial = Settings())
    val h = habit ?: return
    val isNew = h.id == 0L
    val set: (HabitEntity) -> Unit = { habit = it }

    var more by remember(h.id) { mutableStateOf(false) }
    val save: () -> Unit = {
        launchUi {
            val id = sl.habitService.save(
                h.copy(name = h.name.trim(), minutesPerCheckIn = HabitRules.rewardMinutes(h, sl.settings.current())),
            )
            AlarmConfigs.apply(OwnerKind.HABIT, id, alarm)
            onDone()
        }
    }
    val summary = moreSummary(h)
    androidx.compose.material3.Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text(if (isNew) "New habit" else "Edit habit") },
                navigationIcon = { androidx.compose.material3.IconButton(onClick = onDone) { Icon(androidx.compose.material.icons.Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
            )
        },
        bottomBar = {
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).navigationBarsPadding()) {
                Button(onClick = save, enabled = h.name.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Save") }
            }
        },
    ) { pad ->
    androidx.compose.foundation.layout.Column(
        Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        OutlinedTextField(h.name, { set(h.copy(name = it)) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())

        SectionTitle("When")
        ChoiceChips(ScheduleKind.values().toList(), h.scheduleKind, {
            when (it) {
                ScheduleKind.DAILY -> "Every day"; ScheduleKind.WEEKDAYS -> "Some days"; ScheduleKind.EVERY_N_DAYS -> "Every few days"
                ScheduleKind.TIMES_PER_WEEK -> "Times per week"; ScheduleKind.RULE -> "Custom"
            }
        }) { k ->
            set(h.copy(
                scheduleKind = k,
                timesPerWeek = if (k == ScheduleKind.TIMES_PER_WEEK && h.timesPerWeek == 0) 3 else h.timesPerWeek,
                scheduleRule = if (k == ScheduleKind.RULE && h.scheduleRule == null) RRule.weekly(listOf(java.time.LocalDate.ofEpochDay(h.anchorEpochDay).dayOfWeek), 2).toString() else h.scheduleRule,
            ))
        }
        when (h.scheduleKind) {
            ScheduleKind.WEEKDAYS -> WeekdayPicker(h.weekdayMask) { set(h.copy(weekdayMask = it)) }
            ScheduleKind.EVERY_N_DAYS -> NumberField("Every how many days", h.everyNDays, { set(h.copy(everyNDays = (it ?: 1).coerceAtLeast(1))) })
            ScheduleKind.TIMES_PER_WEEK -> NumberField("Days per week (any days)", h.timesPerWeek, { set(h.copy(timesPerWeek = (it ?: 1).coerceIn(1, 7))) })
            ScheduleKind.RULE -> HabitRuleEditor(h, set)
            ScheduleKind.DAILY -> Unit
        }
        SectionTitle("Reminder")
        SwitchRow(
            "Remind only on some days", h.reminderDays != 0,
            { on -> set(h.copy(reminderDays = if (on) h.weekdayMask.takeIf { it != 0 } ?: 0b1111111 else 0)) },
            hint = if (h.reminderDays == 0) "Reminds on every day the habit is scheduled." else null,
        )
        if (h.reminderDays != 0) WeekdayPicker(h.reminderDays) { m -> set(h.copy(reminderDays = m.takeIf { it != 0 } ?: h.reminderDays)) }
        OutlinedTextField(
            h.reminderMessage.orEmpty(), { set(h.copy(reminderMessage = it.take(120).ifBlank { null })) },
            label = { Text("Reminder message (optional)") }, placeholder = { Text(h.name.ifBlank { "Uses the habit name" }) },
            singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        )
        SwitchRow("Custom reminder for this habit", alarm != null, { on ->
            if (!on) alarm = null else launchUi { alarm = AlarmConfigs.defaults(OwnerKind.HABIT).copy(ownerId = h.id) }
        }, hint = if (alarm == null) "Uses your default reminder settings." else null)
        alarm?.let { a -> AlarmConfigEditor(a) { alarm = it } }

        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth().padding(top = 20.dp).heightIn(min = 48.dp).clickable { more = !more },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                Text("More options", style = MaterialTheme.typography.titleSmall)
                if (!more && summary.isNotEmpty()) Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(if (more) "Hide" else "Show", style = MaterialTheme.typography.labelLarge, color = Earnd.palette.amber)
        }
        androidx.compose.animation.AnimatedVisibility(more) {
            androidx.compose.foundation.layout.Column {
        SectionTitle("Look")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HabitLook.icons.forEach { (key, icon) ->
                val on = key == h.iconKey
                Box(
                    Modifier.size(40.dp)
                        .border(1.dp, if (on) Earnd.palette.amber else Earnd.palette.line, RectangleShape)
                        .clickable { set(h.copy(iconKey = key)) },
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, key, tint = if (on) Color(h.colorArgb) else Earnd.palette.muted, modifier = Modifier.size(20.dp)) }
            }
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HabitLook.colors.forEach { c ->
                Box(
                    Modifier.size(28.dp).background(Color(c), RectangleShape)
                        .then(if (c == h.colorArgb) Modifier.border(2.dp, MaterialTheme.colorScheme.onBackground, RectangleShape) else Modifier)
                        .clickable { set(h.copy(colorArgb = c)) },
                )
            }
        }

        Text("Part of the day", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
        ChoiceChips(DaySegment.values().toList(), h.segment, HabitLook::segmentLabel) { set(h.copy(segment = it)) }
        NumberField("Times per day", h.timesPerDay, { set(h.copy(timesPerDay = (it ?: 1).coerceIn(1, 50))) }, Modifier.padding(top = 8.dp))

        SectionTitle("Amount (optional)")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = h.targetQuantity?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "",
                onValueChange = { t -> set(h.copy(targetQuantity = t.toDoubleOrNull())) },
                label = { Text("Target") }, singleLine = true, modifier = Modifier.weight(1f),
            )
            OutlinedTextField(h.unit.orEmpty(), { set(h.copy(unit = it.ifBlank { null })) }, label = { Text("Unit") }, singleLine = true, modifier = Modifier.weight(1f))
        }

        SectionTitle("Focus timer")
        SwitchRow(
            "Timed focus", h.focusMinutes != null,
            { on -> set(h.copy(focusMinutes = if (on) 20 else null)) },
            hint = if (h.focusMinutes == null) "Check-in becomes a timer that keeps other apps closed until it ends."
            else "Cancelling loses the progress. Calls, messages and maps stay open.",
        )
        h.focusMinutes?.let { m ->
            NumberField("Minutes", m, { set(h.copy(focusMinutes = (it ?: 1).coerceIn(com.earnd.app.focus.FocusMath.MINUTES_RANGE))) })
            FocusDurations(h, settings, set)
        }
        SwitchRow(
            "Open-ended (stopwatch)", h.stopwatch, { on -> set(h.copy(stopwatch = on)) },
            hint = if (!h.stopwatch) "Count up instead, e.g. music practice. Other apps stay closed until you tap Finish."
            else "Earns minutes x rate on Finish. Shorter than the minimum: no reward, no check-in.",
        )
        if (h.stopwatch) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                var rateText by androidx.compose.runtime.remember(h.id) {
                    androidx.compose.runtime.mutableStateOf((h.stopwatchRate ?: com.earnd.app.domain.TimedHabit.DEFAULT_RATE).toString())
                }
                OutlinedTextField(
                    rateText,
                    { t -> rateText = t.take(5); t.toDoubleOrNull()?.takeIf { it in com.earnd.app.domain.TimedHabit.RATE_RANGE }?.let { set(h.copy(stopwatchRate = it)) } },
                    label = { Text("Earned per min") }, singleLine = true, modifier = Modifier.weight(1f),
                )
                NumberField("Minimum min", h.stopwatchMinMin ?: com.earnd.app.domain.TimedHabit.DEFAULT_MIN_SESSION,
                    { set(h.copy(stopwatchMinMin = (it ?: 0).coerceIn(0, 240))) }, Modifier.weight(1f))
                NumberField("Cap min", h.stopwatchCapMin, { set(h.copy(stopwatchCapMin = it?.coerceIn(1, 1440))) }, Modifier.weight(1f))
            }
        }

        SectionTitle("Reward and check-in")
        SwitchRow(
            "Use the default reward", h.rewardMinutes == null,
            { on -> set(h.copy(rewardMinutes = if (on) null else settings.defaultHabitRewardMin)) },
            hint = if (h.rewardMinutes == null) "${settings.defaultHabitRewardMin} min per check-in, set in Settings." else null,
        )
        h.rewardMinutes?.let { r ->
            NumberField("Minutes earned per check-in", r, { set(h.copy(rewardMinutes = (it ?: 0).coerceIn(0, 600))) })
        }
        SwitchRow(
            "Reward beyond target", h.overTargetReward, { on -> set(h.copy(overTargetReward = on)) },
            hint = if (h.overTargetReward) "Check-ins beyond the daily target earn a little extra (up to +50%)."
            else "Check-ins beyond the daily target are recorded but earn 0 minutes.",
        )
        Text("Check in with", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
        ChoiceChips(listOf(null, true, false), h.photoRequired, {
            when (it) {
                null -> if (settings.photoRequiredDefault) "Default (photo)" else "Default (tap)"
                true -> "A photo"
                false -> "A tap"
            }
        }) { v -> set(h.copy(photoRequired = v, checkInMode = if (v == true) CheckInMode.PHOTO else CheckInMode.TAP)) }
        Text(
            "Taking the picture is the commitment. Photos stay on this phone.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

            }
        }

        if (!isNew) {
            SectionTitle("Manage")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { launchUi { sl.habitService.archive(h.id); onDone() } }) { Text("Archive") }
                Button(onClick = { confirmDelete = true }) { Text("Delete") }
            }
        }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${h.name}?") },
            text = { Text("Its history goes too. Archiving keeps the history instead.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    launchUi { AlarmConfigs.clear(OwnerKind.HABIT, h.id); sl.habitService.delete(h.id); onDone() }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
        )
    }
}

/** Custom habit schedule: every N weeks on chosen days, or chosen days of the month. */
@Composable
private fun HabitRuleEditor(h: HabitEntity, set: (HabitEntity) -> Unit) {
    val rule = RRule.parseOrNull(h.scheduleRule) ?: RRule.weekly(listOf(java.time.DayOfWeek.MONDAY))
    val save = { r: RRule -> set(h.copy(scheduleRule = r.toString())) }
    ChoiceChips(listOf(RRule.Freq.WEEKLY, RRule.Freq.MONTHLY), if (rule.freq == RRule.Freq.MONTHLY) rule.freq else RRule.Freq.WEEKLY, {
        if (it == RRule.Freq.WEEKLY) "Weeks" else "Days of month"
    }) { f ->
        save(if (f == RRule.Freq.WEEKLY) RRule.weekly(listOf(java.time.DayOfWeek.MONDAY)) else RRule(RRule.Freq.MONTHLY, byMonthDay = listOf(1)))
    }
    if (rule.freq == RRule.Freq.MONTHLY) {
        var text by remember(h.id) { mutableStateOf(rule.byMonthDay.joinToString(", ") { if (it == -1) "last" else "$it" }) }
        OutlinedTextField(text, { v ->
            text = v
            val days = v.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
                .mapNotNull { if (it == "last") -1 else it.toIntOrNull()?.takeIf { d -> d in 1..31 } }.distinct()
            if (days.isNotEmpty()) save(RRule(RRule.Freq.MONTHLY, byMonthDay = days))
        }, label = { Text("Days of month (e.g. 1, 15, last)") }, singleLine = true)
    } else {
        NumberField("Every how many weeks", rule.interval, { save(rule.copy(interval = (it ?: 1).coerceIn(1, 52))) })
        WeekdayPicker(RRule.daysToMask(rule.byDay.map { it.day })) { m ->
            if (m != 0) save(RRule.weekly(RRule.maskToDays(m), rule.interval))
        }
    }
    Text(HabitSchedule.summary(h), style = MaterialTheme.typography.bodySmall)
}

/** "Durations" chips: extra focus lengths, each with its own (editable) reward. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FocusDurations(h: HabitEntity, settings: com.earnd.app.data.Settings, set: (HabitEntity) -> Unit) {
    val extra = com.earnd.app.domain.TimedHabit.parseOrEmpty(h.focusOptions)
    Text("Durations", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(10, 15, 20, 30, 45, 60).filter { it != h.focusMinutes }.forEach { m ->
            val on = extra.any { it.first == m }
            androidx.compose.material3.FilterChip(
                selected = on,
                onClick = {
                    val next = if (on) extra.filterNot { it.first == m }
                    else extra + (m to com.earnd.app.domain.TimedHabit.linearReward(h, settings, m))
                    set(h.copy(focusOptions = com.earnd.app.domain.TimedHabit.format(next)))
                },
                label = { Text("$m min") },
            )
        }
    }
    extra.forEach { (m, r) ->
        NumberField("Reward for $m min", r ?: com.earnd.app.domain.TimedHabit.linearReward(h, settings, m), { v ->
            set(h.copy(focusOptions = com.earnd.app.domain.TimedHabit.format(extra.map { if (it.first == m) m to (v ?: 0).coerceIn(0, 600) else it })))
        }, Modifier.fillMaxWidth())
    }
}

/** One-line summary of non-default "More options" values. */
private fun moreSummary(h: HabitEntity): String = buildList {
    if (h.segment != DaySegment.NONE) add(HabitLook.segmentLabel(h.segment))
    if (h.timesPerDay > 1) add("${h.timesPerDay}x a day")
    if (h.targetQuantity != null) add("target")
    if (h.focusMinutes != null) add("${h.focusMinutes} min focus")
    if (h.stopwatch) add("stopwatch")
    if (h.rewardMinutes != null) add("${h.rewardMinutes} min reward")
    if (!h.overTargetReward) add("no bonus")
    if (h.photoRequired != null) add(if (h.photoRequired == true) "photo" else "tap")
}.joinToString(" · ")
