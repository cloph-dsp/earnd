package com.earnd.app.alarms

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import com.earnd.app.ServiceLocator
import com.earnd.app.alarms.AlarmKeys.putOwner
import com.earnd.app.data.entity.OwnerKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps a full-screen alarm ringing: looped alarm-stream audio and
 * vibration, an insistent notification and a wake lock that turns the screen on. It keeps
 * ringing even if the ringing screen cannot open or is closed; Done and Snooze stop it.
 */
class AlarmRingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val owner = intent?.let(AlarmKeys::ownerOf)
        if (owner == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        val label = intent.getStringExtra(EXTRA_LABEL).orEmpty()
        val n = Ringing.build(this, owner.first, owner.second, label).apply {
            this.flags = this.flags or Notification.FLAG_INSISTENT
        }
        startForeground(AlarmKeys.NOTIF_RING, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        acquireWakeLock()
        scope.launch {
            val cfg = runCatching { ServiceLocator.db.alarmConfigDao().get(owner.first, owner.second) }.getOrNull()
            AlarmRinger.start(this@AlarmRingService, cfg?.soundUri, cfg?.vibrate ?: true)
        }
        return START_NOT_STICKY
    }

    @Suppress("DEPRECATION")
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP, "earnd:alarm",
        ).apply {
            setReferenceCounted(false)
            acquire(MAX_RING_MS)
        }
    }

    override fun onDestroy() {
        AlarmRinger.stop()
        wakeLock?.runCatching { if (isHeld) release() }
        wakeLock = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_LABEL = "label"
        /** Safety cap so a forgotten alarm cannot drain the battery. */
        private const val MAX_RING_MS = 10 * 60_000L

        /** Starts ringing. Returns false if the system refused to start the service. */
        fun start(context: Context, kind: OwnerKind, id: Long, label: String): Boolean = runCatching {
            val i = Intent(context, AlarmRingService::class.java).putOwner(kind, id).putExtra(EXTRA_LABEL, label)
            context.startForegroundService(i)
        }.isSuccess

        fun stop(context: Context) {
            context.stopService(Intent(context, AlarmRingService::class.java))
            AlarmRinger.stop()
        }
    }
}
