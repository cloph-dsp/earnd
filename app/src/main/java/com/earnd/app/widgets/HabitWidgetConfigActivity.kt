package com.earnd.app.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.earnd.app.R
import com.earnd.app.ServiceLocator
import com.earnd.app.ui.theme.EarndTheme
import kotlinx.coroutines.launch

/** Picks the habit shown by one 1x1 habit widget. Opened on placement and from "tap to choose". */
class HabitWidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Backing out of the picker on placement cancels the widget.
        setResult(RESULT_CANCELED, result(appWidgetId))
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        setContent {
            EarndTheme {
                val habits by ServiceLocator.db.habitDao().observeActive().collectAsState(emptyList())
                val scope = rememberCoroutineScope()
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.fillMaxSize().padding(16.dp)) {
                        Text(
                            stringResource(R.string.widgets_habit_pick_title),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                        if (habits.isEmpty()) {
                            Text(
                                stringResource(R.string.widgets_habit_pick_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        LazyColumn {
                            items(habits, key = { it.id }) { h ->
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .clickable { scope.launch { choose(appWidgetId, h.id) } }
                                        .padding(vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(Modifier.size(12.dp).background(Color(h.colorArgb).copy(alpha = 1f), CircleShape))
                                    Spacer(Modifier.width(12.dp))
                                    Text(h.name, style = MaterialTheme.typography.bodyLarge)
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun choose(appWidgetId: Int, habitId: Long) {
        val ctx = applicationContext
        val glanceId = GlanceAppWidgetManager(ctx).getGlanceIdBy(appWidgetId)
        updateAppWidgetState(ctx, PreferencesGlanceStateDefinition, glanceId) { prefs: Preferences ->
            prefs.toMutablePreferences().apply { this[HabitWidget.HABIT_ID] = habitId }
        }
        HabitWidget().update(ctx, glanceId)
        setResult(RESULT_OK, result(appWidgetId))
        finish()
    }

    private fun result(appWidgetId: Int) = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

    companion object {
        fun intent(context: Context, appWidgetId: Int): Intent =
            Intent(context, HabitWidgetConfigActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
}
