package com.earnd.app.ui.settings

import com.earnd.app.data.Settings
import com.earnd.app.data.entity.ResetPeriod
import com.earnd.app.domain.DailyWindow

/** Decides whether a settings edit loosens blocking (and so must wait out the hard-mode cooldown). */
object SettingsChanges {
    fun isWeakening(old: Settings, new: Settings): Boolean =
        (old.hardMode && !new.hardMode) ||
            (old.blockingEnabled && !new.blockingEnabled) ||
            (!old.allDoneUnlock && new.allDoneUnlock) ||
            (old.resetPeriod == ResetPeriod.DAILY && new.resetPeriod != ResetPeriod.DAILY) ||
            new.weakenCooldownMin < old.weakenCooldownMin ||
            new.bedtimeGraceMin > old.bedtimeGraceMin ||
            bedtimeLoosened(old, new) ||
            vacationLoosened(old, new) ||
            (new.defaultTaskRewardMin ?: 0) > (old.defaultTaskRewardMin ?: 0)

    private fun bedtimeLoosened(old: Settings, new: Settings): Boolean {
        if (!old.bedtimeEnabled) return false
        if (!new.bedtimeEnabled) return true
        // Any minute that was locked and no longer is counts as loosening.
        val o = DailyWindow(old.bedtimeStartMin, old.bedtimeEndMin)
        val n = DailyWindow(new.bedtimeStartMin, new.bedtimeEndMin)
        return (0 until 24 * 60 step 5).any { o.contains(it) && !n.contains(it) }
    }

    private fun vacationLoosened(old: Settings, new: Settings): Boolean {
        val nf = new.vacationFromDay ?: return false
        val nt = new.vacationToDay ?: return false
        if (nt - nf > 3660) return true
        return (nf..nt).any { !old.onVacation(it) }
    }
}
