package com.earnd.app.ui.alarms

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.earnd.app.alarms.AlarmSounds
import com.earnd.app.alarms.SoundOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Lists system and custom sounds (see /sdcard/Android/media/<pkg>/sounds) with preview play/stop. */
@Composable
fun SoundPickerDialog(current: String?, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var options by remember { mutableStateOf<List<SoundOption>>(emptyList()) }
    var playing by remember { mutableStateOf<String?>(null) }
    val player = remember { arrayOfNulls<MediaPlayer>(1) }
    LaunchedEffect(Unit) { options = withContext(Dispatchers.IO) { AlarmSounds.list(context) } }
    fun stop() { player[0]?.runCatching { stop(); release() }; player[0] = null; playing = null }
    DisposableEffect(Unit) { onDispose { stop() } }
    fun preview(uri: String?) {
        val was = playing
        stop()
        if (uri == null || was == uri) return
        val mp = MediaPlayer()
        runCatching {
            mp.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            val f = AlarmSounds.filePath(uri)
            if (f != null) mp.setDataSource(f.path) else mp.setDataSource(context, Uri.parse(uri))
            mp.setOnCompletionListener { if (player[0] === mp) stop() }
            mp.prepare(); mp.start()
            player[0] = mp; playing = uri
        }.onFailure { mp.release() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Alarm sound") },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                item { SoundRow("Default", current == null, playing == "", null, { onPick(null) }) { stop() } }
                items(options) { o ->
                    SoundRow(o.title + if (o.type == "custom") "  (custom)" else "  (${o.type})", current == o.uri,
                        playing == o.uri, o.uri, { onPick(o.uri) }) { preview(o.uri) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun SoundRow(label: String, selected: Boolean, playing: Boolean, uri: String?, onPick: () -> Unit, onPreview: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onPick, modifier = Modifier.weight(1f)) {
            Text((if (selected) "✓ " else "") + label, modifier = Modifier.fillMaxWidth())
        }
        if (uri != null) TextButton(onClick = onPreview) { Text(if (playing) "Stop" else "Play") }
    }
}
