package com.earnd.app.data

import com.earnd.app.data.entity.ListDensity
import com.earnd.app.data.entity.PuzzleKind
import com.earnd.app.data.entity.ResetPeriod
import com.earnd.app.data.entity.RewardMode
import com.earnd.app.data.entity.ThemeMode
import com.earnd.app.domain.DateFormatPref
import com.earnd.app.domain.TimeFormatPref
import java.time.DayOfWeek

/** Maps [Settings] to and from flat string key/values stored in the `setting` table. */
internal object SettingsCodec {
    fun encode(s: Settings): Map<String, String> = buildMap {
        put("resetPeriod", s.resetPeriod.name)
        put("rollingExpiryHours", s.rollingExpiryHours.toString())
        put("weekStartsOn", s.weekStartsOn.name)
        put("allDoneUnlock", s.allDoneUnlock.toString())
        put("hardMode", s.hardMode.toString())
        put("weakenCooldownMin", s.weakenCooldownMin.toString())
        put("bedtimeEnabled", s.bedtimeEnabled.toString())
        put("bedtimeStartMin", s.bedtimeStartMin.toString())
        put("bedtimeEndMin", s.bedtimeEndMin.toString())
        put("bedtimeGraceMin", s.bedtimeGraceMin.toString())
        put("vacationFromDay", s.vacationFromDay?.toString() ?: "")
        put("vacationToDay", s.vacationToDay?.toString() ?: "")
        put("defaultTaskRewardMin", s.defaultTaskRewardMin?.toString() ?: "")
        put("quietStartMin", s.quietStartMin.toString())
        put("quietEndMin", s.quietEndMin.toString())
        put("blockingEnabled", s.blockingEnabled.toString())
        put("segmentMode", s.segmentMode.toString())
        put("morningStartMin", s.morningStartMin.toString())
        put("afternoonStartMin", s.afternoonStartMin.toString())
        put("eveningStartMin", s.eveningStartMin.toString())
        put("rewardMode", s.rewardMode.name)
        put("defaultHabitRewardMin", s.defaultHabitRewardMin.toString())
        put("photoRequiredDefault", s.photoRequiredDefault.toString())
        put("morningNudgeEnabled", s.morningNudgeEnabled.toString())
        put("morningNudgeMin", s.morningNudgeMin.toString())
        put("dailyReportEnabled", s.dailyReportEnabled.toString())
        put("dailyReportMin", s.dailyReportMin.toString())
        put("themeMode", s.themeMode.name)
        put("unlockCapMin", s.unlockCapMin?.toString() ?: "")
        put("removalPuzzle", s.removalPuzzle.name)
        put("listDensity", s.listDensity.name)
        put("dateFormat", s.dateFormat.name)
        put("timeFormat", s.timeFormat.name)
        put("comebackBonus", s.comebackBonus.toString())
        put("dayStartWeekdayMin", s.dayStartWeekdayMin.toString())
        put("dayStartWeekendMin", s.dayStartWeekendMin.toString())
        put("laterWeekendStart", s.laterWeekendStart.toString())
        put("ringSec", s.ringSec.toString())
        put("alarmVolumePct", s.alarmVolumePct.toString())
        put("gentleStart", s.gentleStart.toString())
        put("offDayEpochDay", s.offDayEpochDay?.toString() ?: "")
    }

    fun decode(m: Map<String, String>): Settings {
        val d = Settings()
        fun int(k: String, def: Int) = m[k]?.toIntOrNull() ?: def
        fun bool(k: String, def: Boolean) = m[k]?.toBooleanStrictOrNull() ?: def
        fun optLong(k: String, def: Long?) = m[k]?.let { it.toLongOrNull() } ?: if (k in m) null else def
        fun optInt(k: String, def: Int?) = m[k]?.let { it.toIntOrNull() } ?: if (k in m) null else def
        return Settings(
            resetPeriod = m["resetPeriod"]?.let { v -> ResetPeriod.entries.find { it.name == v } } ?: d.resetPeriod,
            rollingExpiryHours = int("rollingExpiryHours", d.rollingExpiryHours).coerceIn(1, 24 * 14),
            weekStartsOn = m["weekStartsOn"]?.let { v -> DayOfWeek.entries.find { it.name == v } } ?: d.weekStartsOn,
            allDoneUnlock = bool("allDoneUnlock", d.allDoneUnlock),
            hardMode = bool("hardMode", d.hardMode),
            weakenCooldownMin = int("weakenCooldownMin", d.weakenCooldownMin),
            bedtimeEnabled = bool("bedtimeEnabled", d.bedtimeEnabled),
            bedtimeStartMin = int("bedtimeStartMin", d.bedtimeStartMin),
            bedtimeEndMin = int("bedtimeEndMin", d.bedtimeEndMin),
            bedtimeGraceMin = int("bedtimeGraceMin", d.bedtimeGraceMin),
            vacationFromDay = optLong("vacationFromDay", d.vacationFromDay),
            vacationToDay = optLong("vacationToDay", d.vacationToDay),
            defaultTaskRewardMin = optInt("defaultTaskRewardMin", d.defaultTaskRewardMin),
            quietStartMin = int("quietStartMin", d.quietStartMin),
            quietEndMin = int("quietEndMin", d.quietEndMin),
            blockingEnabled = bool("blockingEnabled", d.blockingEnabled),
            segmentMode = bool("segmentMode", d.segmentMode),
            morningStartMin = int("morningStartMin", d.morningStartMin),
            afternoonStartMin = int("afternoonStartMin", d.afternoonStartMin),
            eveningStartMin = int("eveningStartMin", d.eveningStartMin),
            rewardMode = m["rewardMode"]?.let { v -> RewardMode.entries.find { it.name == v } } ?: d.rewardMode,
            defaultHabitRewardMin = int("defaultHabitRewardMin", d.defaultHabitRewardMin),
            photoRequiredDefault = bool("photoRequiredDefault", d.photoRequiredDefault),
            morningNudgeEnabled = bool("morningNudgeEnabled", d.morningNudgeEnabled),
            morningNudgeMin = int("morningNudgeMin", d.morningNudgeMin),
            dailyReportEnabled = bool("dailyReportEnabled", d.dailyReportEnabled),
            dailyReportMin = int("dailyReportMin", d.dailyReportMin),
            themeMode = m["themeMode"]?.let { v -> ThemeMode.entries.find { it.name == v } } ?: d.themeMode,
            unlockCapMin = optInt("unlockCapMin", d.unlockCapMin),
            removalPuzzle = m["removalPuzzle"]?.let { v -> PuzzleKind.entries.find { it.name == v } } ?: d.removalPuzzle,
            listDensity = m["listDensity"]?.let { v -> ListDensity.entries.find { it.name == v } } ?: d.listDensity,
            dateFormat = m["dateFormat"]?.let { v -> DateFormatPref.entries.find { it.name == v } } ?: d.dateFormat,
            timeFormat = m["timeFormat"]?.let { v -> TimeFormatPref.entries.find { it.name == v } } ?: d.timeFormat,
            comebackBonus = bool("comebackBonus", d.comebackBonus),
            dayStartWeekdayMin = int("dayStartWeekdayMin", d.dayStartWeekdayMin).coerceIn(0, 1439),
            dayStartWeekendMin = int("dayStartWeekendMin", d.dayStartWeekendMin).coerceIn(0, 1439),
            laterWeekendStart = bool("laterWeekendStart", d.laterWeekendStart),
            ringSec = int("ringSec", d.ringSec).coerceIn(1, 600),
            alarmVolumePct = int("alarmVolumePct", d.alarmVolumePct).coerceIn(10, 100),
            gentleStart = bool("gentleStart", d.gentleStart),
            offDayEpochDay = optLong("offDayEpochDay", d.offDayEpochDay),
        )
    }
}
