package com.earnd.app.remote

import com.earnd.app.data.SettingsRepository

/**
 * On/off switch for remote access. Off by default; it can only be changed from the app's
 * settings screen (there is deliberately no remote command for it).
 */
object RemoteSettings {
    private const val KEY = "remote.enabled"

    suspend fun isEnabled(settings: SettingsRepository): Boolean = settings.rawGet(KEY) == "true"

    suspend fun setEnabled(settings: SettingsRepository, enabled: Boolean) =
        settings.rawPut(KEY, enabled.toString())

    /** Recent remote actions as JSON lines, newest last (for a settings/audit screen). */
    suspend fun auditLog(settings: SettingsRepository): List<String> =
        RemoteAudit.parse(settings.rawGet(RemoteAudit.KEY))
}
