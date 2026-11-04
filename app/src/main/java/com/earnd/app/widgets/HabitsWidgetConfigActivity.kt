package com.earnd.app.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.earnd.app.R
import com.earnd.app.ServiceLocator
import com.earnd.app.ui.theme.EarndTheme
import kotlinx.coroutines.launch

/** Chooses which habits the Habits strip shows, and their order. Opened on placement and on reconfigure. */
class HabitsWidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED, result(appWidgetId))
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        setContent {
            EarndTheme {
                val habits by ServiceLocator.db.habitDao().observeActive().collectAsState(emptyList())
                val scope = rememberCoroutineScope()
                // Selected ids in display order; null until the saved choice has loaded.
                var selected by remember { mutableStateOf<List<Long>?>(null) }
                LaunchedEffect(Unit) {
                    val glanceId = runCatching { GlanceAppWidgetManager(applicationContext).getGlanceIdBy(appWidgetId) }.getOrNull()
                    selected = glanceId?.let {
                        HabitsWidget.parseIds(getAppWidgetState<Preferences>(applicationContext, PreferencesGlanceStateDefinition, it)[HabitsWidget.HABIT_IDS])
                    }.orEmpty()
                }
                val sel = selected.orEmpty().filter { id -> habits.any { it.id == id } }
                val rowsOrder = sel.mapNotNull { id -> habits.firstOrNull { it.id == id } } + habits.filter { it.id !in sel }
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.fillMaxSize().padding(16.dp)) {
                        Text(stringResource(R.string.widgets_habits_pick_title), style = MaterialTheme.typography.titleLarge)
                        Text(
                            stringResource(R.string.widgets_habits_pick_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                        )
                        LazyColumn(Modifier.weight(1f)) {
                            items(rowsOrder, key = { it.id }) { h ->
                                val idx = sel.indexOf(h.id)
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = idx >= 0,
                                        onCheckedChange = { on -> selected = if (on) sel + h.id else sel - h.id },
                                    )
                                    Text(h.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                    if (idx >= 0) {
                                        TextButton(enabled = idx > 0, onClick = { selected = swap(sel, idx, idx - 1) }) { Text("▲") }
                                        TextButton(enabled = idx < sel.size - 1, onClick = { selected = swap(sel, idx, idx + 1) }) { Text("▼") }
                                    }
                                }
                                HorizontalDivider()
                            }
                        }
                        Button(
                            onClick = { scope.launch { save(appWidgetId, sel) } },
                            enabled = selected != null,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) { Text(stringResource(R.string.widgets_habits_pick_save)) }
                    }
                }
            }
        }
    }

    private fun swap(l: List<Long>, a: Int, b: Int): List<Long> =
        l.toMutableList().also { val t = it[a]; it[a] = it[b]; it[b] = t }

    private suspend fun save(appWidgetId: Int, ids: List<Long>) {
        val ctx = applicationContext
        val glanceId = runCatching { GlanceAppWidgetManager(ctx).getGlanceIdBy(appWidgetId) }.getOrNull()
        if (glanceId == null) { setResult(RESULT_CANCELED, result(appWidgetId)); finish(); return }
        updateAppWidgetState(ctx, PreferencesGlanceStateDefinition, glanceId) { prefs: Preferences ->
            prefs.toMutablePreferences().apply { this[HabitsWidget.HABIT_IDS] = HabitsWidget.encodeIds(ids) }
        }
        HabitsWidget().update(ctx, glanceId)
        setResult(RESULT_OK, result(appWidgetId))
        finish()
    }

    private fun result(appWidgetId: Int) = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

    companion object {
        fun intent(context: Context, appWidgetId: Int): Intent =
            Intent(context, HabitsWidgetConfigActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
}
