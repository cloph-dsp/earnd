package com.earnd.app.blocking

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.earnd.app.ServiceLocator
import com.earnd.app.domain.BlockCause
import com.earnd.app.ui.theme.EarndTheme
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Full-screen lock shown over a leisure app or site. Leaving it always goes home,
 * never back into the blocked app.
 */
class LockedActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ServiceLocator.init(applicationContext)
        enableEdgeToEdge()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })
        render()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        render()
    }

    private fun render() {
        val cause = intent.getStringExtra(EXTRA_CAUSE)
            ?.let { runCatching { BlockCause.valueOf(it) }.getOrNull() } ?: BlockCause.NO_BALANCE
        val target = intent.getStringExtra(EXTRA_HOST) ?: appLabel(intent.getStringExtra(EXTRA_PKG))
        val until = intent.getLongExtra(EXTRA_UNTIL, 0L).takeIf { it > 0 }?.let(::formatTime)
        val gateKey = intent.getStringExtra(EXTRA_KEY)
        val rate = intent.getIntExtra(EXTRA_RATE, 100)
        val gate = if (cause == BlockCause.CONFIRM) {
            val bal = intent.getIntExtra(EXTRA_BAL, 0)
            GateInfo(if (rate <= 0) null else (bal * 100L / rate).toInt(), rate, bal)
        } else null
        setContent {
            EarndTheme {
                val vm: LockedViewModel = viewModel(key = cause.name) { LockedViewModel(cause) }
                val ui by vm.ui.collectAsStateWithLifecycle()
                val message by vm.message.collectAsStateWithLifecycle()
                val unlocked by vm.unlocked.collectAsStateWithLifecycle()
                LaunchedEffect(unlocked) { if (unlocked) finish() }
                LockedScreen(
                    cause = cause, target = target, until = until, ui = ui, message = message,
                    onCheckIn = { item ->
                        if (item.needsApp) openInApp(BlockingIntents.checkIn(this, item.day.habit.id))
                        else vm.checkIn(item.day.habit.id)
                    },
                    onTask = { vm.completeTask(it.id) },
                    onStartSession = vm::startSession,
                    onGrace = vm::startGrace,
                    onHome = ::goHome,
                    gate = gate,
                    onOpen = {
                        // The leisure app is still underneath; it counts from now, and again for 2 min after leaving it.
                        gateKey?.let { ServiceLocator.grants.grant(it, ServiceLocator.clock.now()) }
                        finish()
                    },
                )
            }
        }
    }

    private fun openInApp(i: Intent) {
        startActivity(i)
        finish()
    }

    private fun goHome() {
        startActivity(BlockingIntents.home())
        finish()
    }

    private fun appLabel(pkg: String?): String {
        pkg ?: return ""
        return runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
    }

    private fun formatTime(at: Long): String =
        com.earnd.app.domain.DateTimeFormats.current.formatTime(at, ServiceLocator.clock.zone())

    companion object {
        private const val EXTRA_PKG = "com.earnd.app.blocking.PKG"
        private const val EXTRA_HOST = "com.earnd.app.blocking.HOST"
        private const val EXTRA_CAUSE = "com.earnd.app.blocking.CAUSE"
        private const val EXTRA_UNTIL = "com.earnd.app.blocking.UNTIL"
        private const val EXTRA_KEY = "com.earnd.app.blocking.KEY"
        private const val EXTRA_RATE = "com.earnd.app.blocking.RATE"
        private const val EXTRA_BAL = "com.earnd.app.blocking.BAL"

        /** Confirmation before a metered leisure app starts counting. [key] is the rule key the grant is for. */
        fun gateIntent(context: Context, pkg: String, host: String?, key: String, ratePct: Int, balanceMin: Int): Intent =
            intent(context, pkg, host, BlockCause.CONFIRM, null)
                .putExtra(EXTRA_KEY, key).putExtra(EXTRA_RATE, ratePct).putExtra(EXTRA_BAL, balanceMin)

        fun intent(context: Context, pkg: String, host: String?, cause: BlockCause, until: Long?): Intent =
            Intent(context, LockedActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                .putExtra(EXTRA_PKG, pkg)
                .putExtra(EXTRA_HOST, host)
                .putExtra(EXTRA_CAUSE, cause.name)
                .putExtra(EXTRA_UNTIL, until ?: 0L)
    }
}
