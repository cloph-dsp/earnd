package com.earnd.app.ui.home

import android.content.Context
import android.media.AudioManager
import com.earnd.app.ui.onboarding.AppPermissions

/** What is silently stopping the app from doing its job right now (a revoked permission, a muted alarm stream). */
data class Health(val missing: List<String>, val alarmMuted: Boolean) {
    val ok: Boolean get() = missing.isEmpty() && !alarmMuted
}

object HealthCheck {
    /** Titles of the required (non-optional) permissions that are off, and whether the alarm volume is zero. */
    fun read(context: Context): Health {
        val missing = runCatching {
            AppPermissions.all().filter { !it.optional && !it.granted(context) }.map { it.title }
        }.getOrDefault(emptyList())
        val muted = runCatching {
            context.getSystemService(AudioManager::class.java).getStreamVolume(AudioManager.STREAM_ALARM) == 0
        }.getOrDefault(false)
        return Health(missing, muted)
    }
}
