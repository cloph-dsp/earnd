package com.earnd.app.alarms

import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import com.earnd.app.ServiceLocator
import com.earnd.app.alarms.AlarmKeys.putOwner
import com.earnd.app.data.entity.OwnerKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Foreground service that rings an alarm: looped alarm-stream audio and vibration, a notification
 * with Stop / Snooze and a full-screen intent, a wake lock, and the alarm screen launched directly
 * when possible. Stops on Stop, Snooze, a STOP start command, or after [RingPolicy.timeoutMs].
 */
class AlarmRingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null
    private var owner: Pair<OwnerKind, Long>? = null
    private var label: String = ""
    private var ringJob: Job? = null
    @Volatile private var shortRing = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val o = intent?.let(AlarmKeys::ownerOf)
        if (intent?.action == AlarmKeys.ACTION_RING_STOP || o == null) {
            // Possibly started via startForegroundService: must call startForeground before stopping.
            runCatching { startForeground(AlarmKeys.NOTIF_RING, Ringing.placeholder(this), FGS_TYPE) }
            shutdown()
            return START_NOT_STICKY
        }
        owner = o
        label = intent.getStringExtra(AlarmKeys.EXTRA_LABEL).orEmpty()
        AlarmChannels.ensure(this)
        startForeground(AlarmKeys.NOTIF_RING, Ringing.build(this, o.first, o.second, label, snoozesLeft = 0), FGS_TYPE)
        acquireWakeLock()
        // Safety net until the config is loaded below, which replaces it with the effective duration.
        val timeout = RingPolicy.timeoutMs(intent.getLongExtra(AlarmKeys.EXTRA_TIMEOUT_MS, 0))
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ onTimeout() }, timeout)
        launchScreen(o)
        if (!AlarmPermissions.ringVisible(this)) {
            // No visible notification = no way to stop it: ring briefly, then record a missed alarm.
            handler.removeCallbacksAndMessages(null)
            handler.postDelayed({ onTimeout() }, INVISIBLE_TIMEOUT_MS)
        }
        AlarmRinger.stop() // invalidates any earlier start still preparing
        val gen = AlarmRinger.generation
        ringJob?.cancel()
        ringJob = scope.launch {
            val cfg = runCatching { ServiceLocator.db.alarmConfigDao().get(o.first, o.second) }.getOrNull()
            val st = runCatching { ServiceLocator.settings.current() }.getOrDefault(com.earnd.app.data.Settings())
            val ringMs = RingPolicy.effectiveRingMs(cfg?.ringSec, st.ringSec)
            val volume = RingPolicy.effectiveVolume(cfg?.volumePct, st.alarmVolumePct)
            val ramp = RingPolicy.usesRamp(st.gentleStart, ringMs)
            shortRing = RingPolicy.isShort(ringMs)
            if (owner == o && AlarmPermissions.ringVisible(this@AlarmRingService)) {
                handler.removeCallbacksAndMessages(null)
                handler.postDelayed({ onTimeout() }, ringMs)
            }
            val left = runCatching { AlarmsModule.actions.snoozesLeft(o.first, o.second) }.getOrDefault(0)
            if (owner == o) runCatching {
                getSystemService(NotificationManager::class.java)
                    .notify(AlarmKeys.NOTIF_RING, Ringing.build(this@AlarmRingService, o.first, o.second, label, left))
            }
            // prepare() on a content URI can block: keep it off the main thread.
            withContext(Dispatchers.IO) { AlarmRinger.start(this@AlarmRingService, cfg?.soundUri, cfg?.vibrate ?: true, gen, volume, ramp) }
        }
        return START_NOT_STICKY
    }

    /**
     * The full-screen intent only takes over when the device is locked/off and the permission is
     * held; otherwise it becomes a heads-up. Then also try opening the screen directly.
     */
    private fun launchScreen(o: Pair<OwnerKind, Long>) {
        val locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked
        if (locked && AlarmPermissions.canUseFullScreenIntent(this)) return
        // Unlocked, Android 14+ blocks background activity starts unless the app may draw overlays;
        // without it the full-screen intent shows as a heads-up with Stop / Snooze.
        runCatching { startActivity(AlarmActivity.intent(this, o.first, o.second)) }
    }

    private fun onTimeout() {
        val o = owner ?: return shutdown()
        // Very short rings are deliberate: the item just stays in the consolidated pending notification.
        if (!shortRing) Ringing.missed(this, o.first, o.second, label)
        AlarmActivity.finishCurrent()
        shutdown()
    }

    private fun shutdown() {
        owner = null
        handler.removeCallbacksAndMessages(null)
        AlarmRinger.stop()
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        runCatching { getSystemService(NotificationManager::class.java).cancel(AlarmKeys.NOTIF_RING) }
        stopSelf()
    }

    @Suppress("DEPRECATION")
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP, "earnd:alarm",
        ).apply {
            setReferenceCounted(false)
            acquire(MAX_WAKE_MS)
        }
    }

    override fun onDestroy() {
        owner = null
        handler.removeCallbacksAndMessages(null)
        AlarmRinger.stop()
        wakeLock?.runCatching { if (isHeld) release() }
        wakeLock = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val FGS_TYPE = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        private const val INVISIBLE_TIMEOUT_MS = 20_000L
        private const val MAX_WAKE_MS = 60 * 60_000L

        /** Starts ringing. Returns false if the system refused to start the service. */
        fun start(context: Context, kind: OwnerKind, id: Long, label: String, timeoutMs: Long = 0): Boolean = runCatching {
            val i = Intent(context, AlarmRingService::class.java).putOwner(kind, id)
                .putExtra(AlarmKeys.EXTRA_LABEL, label).putExtra(AlarmKeys.EXTRA_TIMEOUT_MS, timeoutMs)
            context.startForegroundService(i)
        }.isSuccess

        /** Silences immediately in this process and stops the service. Idempotent. */
        fun stop(context: Context) {
            AlarmRinger.stop()
            runCatching { context.stopService(Intent(context, AlarmRingService::class.java)) }
            runCatching { context.getSystemService(NotificationManager::class.java).cancel(AlarmKeys.NOTIF_RING) }
        }

        /** Volume key while ringing: silence sound and vibration but keep the screen up. */
        fun silence() = AlarmRinger.stop()
    }
}
