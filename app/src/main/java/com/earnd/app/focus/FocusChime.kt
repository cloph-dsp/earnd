package com.earnd.app.focus

import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Short alarm-style chime and buzz when a focus session ends (a few seconds, never looping). */
object FocusChime {
    private const val PLAY_MS = 3_500L

    fun play(context: Context) {
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val r = RingtoneManager.getRingtone(context, uri) ?: return@runCatching
            r.audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            r.play()
            Handler(Looper.getMainLooper()).postDelayed({ runCatching { r.stop() } }, PLAY_MS)
        }
        runCatching {
            val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                context.getSystemService(VibratorManager::class.java).defaultVibrator
            else @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
            v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400, 200, 600), -1))
        }
    }
}
