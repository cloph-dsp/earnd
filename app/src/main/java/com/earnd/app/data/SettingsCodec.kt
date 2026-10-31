package com.earnd.app.data

import com.earnd.app.data.entity.ResetPeriod
import java.time.DayOfWeek

/** Maps [Settings] to and from flat string key/values stored in the `setting` table. */
internal object SettingsCodec {
    fun encode(s: Settings): Map<String, String> = buildMap {
        put("resetPeriod", s.resetPeriod.name)
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
    }

    fun decode(m: Map<String, String>): Settings {
        val d = Settings()
        fun int(k: String, def: Int) = m[k]?.toIntOrNull() ?: def
        fun bool(k: String, def: Boolean) = m[k]?.toBooleanStrictOrNull() ?: def
        fun optLong(k: String, def: Long?) = m[k]?.let { it.toLongOrNull() } ?: if (k in m) null else def
        fun optInt(k: String, def: Int?) = m[k]?.let { it.toIntOrNull() } ?: if (k in m) null else def
        return Settings(
            resetPeriod = m["resetPeriod"]?.let { v -> ResetPeriod.entries.find { it.name == v } } ?: d.resetPeriod,
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
        )
    }
}
