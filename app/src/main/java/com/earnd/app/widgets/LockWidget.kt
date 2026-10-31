package com.earnd.app.widgets

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.ColorFilter
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.layout.size
import com.earnd.app.R

/** 1x1 widget: locked / open / bedtime with remaining minutes; tap toggles. */
class LockWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = LockState.load()
        WidgetRefresher.scheduleSessionEnd(context, state.sessionEndsAt)
        provideContent {
            val live by LockState.observe().collectAsState(state)
            Content(context, live)
        }
    }

    @Composable
    private fun Content(context: Context, state: LockState) {
        // Graphite tile; the state lives in the icon tint and the big number, not a loud fill.
        val (icon, tint, label) = when (state.mode) {
            LockState.Mode.UNLOCKED -> Triple(R.drawable.widgets_ic_unlock, WidgetColors.primary, R.string.widgets_lock_open)
            LockState.Mode.LOCKED -> Triple(R.drawable.widgets_ic_lock, WidgetColors.locked, R.string.widgets_lock_locked)
            LockState.Mode.BEDTIME -> Triple(R.drawable.widgets_ic_moon, WidgetColors.muted, R.string.widgets_lock_bedtime)
        }
        // Unlocked: minutes left in the session. Locked: banked minutes, amber when there is something to spend.
        val minutesColor = when {
            state.mode == LockState.Mode.UNLOCKED -> WidgetColors.primary
            state.minutes > 0 -> WidgetColors.primary
            else -> WidgetColors.muted
        }
        Column(
            modifier = GlanceModifier.fillMaxSize().cornerRadius(16.dp).background(WidgetColors.tile)
                .padding(4.dp).clickable(actionRunCallback<LockToggleAction>()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                provider = ImageProvider(icon),
                contentDescription = context.getString(label),
                colorFilter = ColorFilter.tint(tint),
                modifier = GlanceModifier.size(20.dp),
            )
            Text(
                context.getString(R.string.widgets_minutes_compact, state.minutes),
                style = TextStyle(color = minutesColor, fontSize = 17.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
            )
            Text(
                context.getString(label).uppercase(),
                style = TextStyle(color = WidgetColors.muted, fontSize = 9.sp, fontWeight = FontWeight.Medium),
            )
        }
    }
}

class LockToggleAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        if (LockToggle.toggle()) context.startActivity(WidgetLinks.home(context))
        WidgetRefresher.updateAll(context)
    }
}

class LockWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LockWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefresher.schedulePeriodic(context)
    }
}
