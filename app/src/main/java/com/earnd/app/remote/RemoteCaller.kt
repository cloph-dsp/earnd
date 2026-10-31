package com.earnd.app.remote


/**
 * Caller check for the remote receiver. The manifest's `android.permission.DUMP` requirement is
 * the primary guard; this is defence in depth for when the platform reports the sender's UID
 * (API 34+ `BroadcastReceiver.getSentFromUid`, which `am broadcast` usually leaves unknown).
 */
object RemoteCaller {
    const val ROOT_UID = 0
    const val SHELL_UID = 2000

    /** Unknown (null or -1) is allowed: DUMP already restricts the sender. */
    fun isAllowed(uid: Int?): Boolean =
        uid == null || uid == INVALID_UID || uid == ROOT_UID || uid == SHELL_UID

    private const val INVALID_UID = -1 // Process.INVALID_UID, kept literal for JVM unit tests
}
