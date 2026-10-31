package com.earnd.app.alarms

import android.content.Intent
import android.net.Uri
import com.earnd.app.data.entity.OwnerKind

/** Intent actions, extras and notification ids shared by the alarm components. */
internal object AlarmKeys {
    const val ACTION_FIRE = "com.earnd.app.alarms.FIRE"
    const val ACTION_TICK = "com.earnd.app.alarms.TICK"
    const val ACTION_DONE = "com.earnd.app.alarms.DONE"
    const val ACTION_OPEN_RING = "com.earnd.app.alarms.OPEN_RING"

    const val EXTRA_KIND = "kind"
    const val EXTRA_ID = "id"
    const val EXTRA_SLOT = "slot"
    const val EXTRA_AT = "at"

    const val NOTIF_PENDING = 4100
    const val NOTIF_RING = 4101
    const val NOTIF_UPCOMING_BASE = 4200

    /** Distinct data URI per alarm slot so PendingIntents never collide. */
    fun uri(kind: OwnerKind?, id: Long, slot: String): Uri =
        Uri.parse("earnd-alarm://${kind?.name ?: "ANY"}/$id/$slot")

    fun ownerOf(intent: Intent): Pair<OwnerKind, Long>? {
        val kind = intent.getStringExtra(EXTRA_KIND)?.let { runCatching { OwnerKind.valueOf(it) }.getOrNull() }
        val id = intent.getLongExtra(EXTRA_ID, -1)
        return if (kind != null && id >= 0) kind to id else null
    }

    fun Intent.putOwner(kind: OwnerKind, id: Long): Intent =
        putExtra(EXTRA_KIND, kind.name).putExtra(EXTRA_ID, id)
}
