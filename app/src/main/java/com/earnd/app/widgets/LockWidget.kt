package com.earnd.app.widgets

import android.content.Context
import androidx.compose.runtime.Composable
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
import com.earnd.app.R

/** 1x1 widget: locked / open / bedtime with remaining minutes; tap toggles. */
class LockWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = LockState.load()
        WidgetRefresher.scheduleSessionEnd(context, state.sessionEndsAt)
        provideContent { Content(context, state) }
    }

    @Composable
    private fun Content(context: Context, state: LockState) {
        val (bg, label) = when (state.mode) {
            LockState.Mode.UNLOCKED -> WidgetColors.primary to R.string.widgets_lock_open
            LockState.Mode.LOCKED -> WidgetColors.locked to R.string.widgets_lock_locked
            LockState.Mode.BEDTIME -> WidgetColors.bedtime to R.string.widgets_lock_bedtime
        }
        val fg = if (state.mode == LockState.Mode.BEDTIME) WidgetColors.onBedtime else WidgetColors.onPrimary
        Column(
            modifier = GlanceModifier.fillMaxSize().cornerRadius(4.dp).background(bg).padding(6.dp)
                .clickable(actionRunCallback<LockToggleAction>()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(context.getString(label), style = TextStyle(color = fg, fontSize = 12.sp, fontWeight = FontWeight.Medium))
            Text(
                context.getString(R.string.widgets_minutes_short, state.minutes),
                style = TextStyle(color = fg, fontSize = 16.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace),
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
