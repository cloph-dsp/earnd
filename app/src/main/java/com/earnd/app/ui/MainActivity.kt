package com.earnd.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.earnd.app.ServiceLocator
import com.earnd.app.domain.today
import com.earnd.app.ui.apps.PendingRuleChanges
import com.earnd.app.ui.nav.EarndNavHost
import com.earnd.app.ui.nav.Routes
import com.earnd.app.ui.onboarding.Onboarding
import com.earnd.app.ui.theme.EarndTheme

class MainActivity : ComponentActivity() {
    private var nav: NavHostController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EarndTheme {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    val start by produceState<String?>(null) {
                        value = if (Onboarding.isDone()) Routes.TODAY else Routes.ONBOARDING
                    }
                    LaunchedEffect(Unit) { housekeeping() }
                    start?.let { s ->
                        val controller = rememberNavController()
                        LaunchedEffect(controller) { nav = controller }
                        EarndNavHost(controller, s)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        nav?.handleDeepLink(intent)
    }

    /** Light upkeep whenever the UI opens: period reset, recurring roll-forward, queued changes. */
    private suspend fun housekeeping() {
        val sl = ServiceLocator
        runCatching { sl.timeBank.resetIfPeriodRolled() }
        runCatching { sl.taskService.rollForward(sl.clock.today()) }
        runCatching { sl.settings.applyDueWeakening(sl.clock.now()) }
        runCatching { PendingRuleChanges.applyDue(sl.clock.now()) }
    }
}
