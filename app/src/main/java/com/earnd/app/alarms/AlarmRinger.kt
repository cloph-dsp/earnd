package com.earnd.app.alarms

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Loops alarm sound and vibration until [stop] is called (Done or Snooze). */
internal object AlarmRinger {
    private val lock = Any()
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    /** Bumped by every [stop]; a [start] whose generation is stale never leaves anything playing. */
    @Volatile var generation: Long = 0L
        private set

    /** Prepares outside the lock (may block), then installs only if [gen] is still current. */
    fun start(context: Context, soundUri: String?, vibrate: Boolean, gen: Long = generation) {
        if (gen != generation) return
        val mp = listOfNotNull(
            soundUri?.let(Uri::parse),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
        ).firstNotNullOfOrNull { tryPrepare(context, it) }
        synchronized(lock) {
            if (gen != generation) { mp?.release(); return }
            stopLocked()
            mp?.runCatching { start() }
            player = mp
            if (vibrate) vibrator = vibratorOf(context).also {
                it.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 500), 0))
            }
        }
    }

    private fun tryPrepare(context: Context, uri: Uri): MediaPlayer? {
        val mp = MediaPlayer()
        return runCatching {
            mp.apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                setDataSource(context, uri)
                isLooping = true
                prepare()
            }
        }.onFailure { mp.release() }.getOrNull()
    }

    /** Never waits on a prepare(): it only invalidates the generation and stops what is installed. */
    fun stop() {
        generation++
        synchronized(lock) { stopLocked() }
    }

    private fun stopLocked() {
        player?.runCatching { stop(); release() }
        player = null
        vibrator?.cancel()
        vibrator = null
    }

    private fun vibratorOf(context: Context): Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        else @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
}
