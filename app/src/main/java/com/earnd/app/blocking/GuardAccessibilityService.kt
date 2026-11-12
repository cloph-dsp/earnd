package com.earnd.app.blocking

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.telephony.TelephonyManager
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import androidx.core.content.ContextCompat
import com.earnd.app.ServiceLocator
import com.earnd.app.alarms.GuardReminder
import com.earnd.app.domain.BlockDecision
import com.earnd.app.domain.BlockRules
import com.earnd.app.domain.ChangeKind
import com.earnd.app.domain.Charging
import com.earnd.app.domain.CountRules
import com.earnd.app.domain.Counted
import com.earnd.app.domain.MeterActive
import com.earnd.app.domain.OverlayMath
import com.earnd.app.domain.WinInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Watches which app (and, in browsers, which site) is in front. Shows the lock screen when
 * [com.earnd.app.domain.BlockPolicy] says so, the confirmation gate when a metered leisure app is opened without a
 * counting grant, and charges the balance while a leisure app is actually in use (see
 * [com.earnd.app.domain.MeterEngine]), with a small counter overlay.
 */
class GuardAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var foregroundPkg: String? = null
    private var lastUrl: String? = null
    private var lastCheckKey: String? = null
    private var lastCheckAt = 0L
    private var lastUrlScanAt = 0L
    private var lastWinAt = 0L
    private var pending: Job? = null

    private var overlay: MeterOverlay? = null
    private var ticker: Job? = null
    private var zeroJob: Job? = null
    /** Rule key of the leisure target in front (its grant stops running down while it is). */
    private var grantKey: String? = null
    /** A leisure target is in front (counting, or paused by a call). */
    private var armed = false
    private var label = ""
    private var expiryAt: Long? = null
    private var prevRemaining: Long? = null
    private var prevKey: String? = null
    private var prevElapsed: Long? = null
    private var lastTickAdvance = 0L
    private var lastExtraLockAt = 0L
    private var screenReceiver: BroadcastReceiver? = null

    private class Env(val screenUsable: Boolean, val inCall: Boolean) {
        val allowed get() = screenUsable && !inCall
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        ServiceLocator.init(applicationContext)
        GuardWatch.clear(this)
        overlay = MeterOverlay(this, getSystemService(WindowManager::class.java))
        registerScreenReceiver()
        // After a rebind (process killed, service toggled) no window event has arrived yet: read the app in front now.
        val root = runCatching { rootInActiveWindow }.getOrNull()
        try {
            root?.packageName?.toString()?.takeIf { !isTransientSystemUi(it) }?.let { pkg ->
                foregroundPkg = pkg
                if (BrowserUrls.isBrowser(pkg)) lastUrl = runCatching { BrowserUrls.extract(root, pkg) }.getOrNull()
            }
        } finally {
            @Suppress("DEPRECATION")
            runCatching { root?.recycle() }
        }
        schedule(force = true)
        scope.launch {
            ServiceLocator.changeBus.events.collect { kind ->
                if (kind == ChangeKind.BLOCKLIST) ServiceLocator.essentialApps.refresh()
                if (kind in RECHECK_ON) recheck(force = true)
                if (kind == ChangeKind.BALANCE && armed) attempt {
                    val now = System.currentTimeMillis()
                    syncBalance(now)
                    scheduleZero(now)
                }
            }
        }
        // Sessions expire silently; tick so an expired session re-locks the current app.
        scope.launch {
            while (isActive) {
                delay(TICK_MS)
                attempt { ServiceLocator.focus.completeIfDue() } // backstop if the end alarm was late
                attempt { recheck(force = true) }
            }
        }
    }

    /** Like runCatching for suspend code, but a cancelled job stays cancelled instead of carrying on with stale state. */
    private suspend inline fun <T> attempt(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }

    private fun registerScreenReceiver() {
        val r = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_SCREEN_OFF) scope.launch { pauseCounting(System.currentTimeMillis()) }
                else scope.launch { recheck(force = true) }
            }
        }
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(this, r, f, ContextCompat.RECEIVER_NOT_EXPORTED)
        screenReceiver = r
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            // A picture-in-picture or split-screen window may have appeared or gone.
            val now = System.currentTimeMillis()
            if (now - lastWinAt >= WINDOWS_MS) { lastWinAt = now; schedule(force = true) }
            return
        }
        val pkg = event.packageName?.toString() ?: return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (isTransientSystemUi(pkg)) return
                if (pkg != foregroundPkg) { foregroundPkg = pkg; lastUrl = null }
                if (BrowserUrls.isBrowser(pkg)) lastUrl = BrowserUrls.extract(rootInActiveWindow, pkg)
                schedule()
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (pkg != foregroundPkg || !BrowserUrls.isBrowser(pkg)) return
                val now = System.currentTimeMillis()
                if (now - lastUrlScanAt < URL_SCAN_MS) return
                lastUrlScanAt = now
                val url = BrowserUrls.extract(rootInActiveWindow, pkg) ?: return
                if (url != lastUrl) { lastUrl = url; schedule() }
            }
        }
    }

    /** Notification shade and keyboards briefly take focus without changing the foreground app (they keep counting). */
    private fun isTransientSystemUi(pkg: String) =
        pkg == "com.android.systemui" || pkg.contains("inputmethod") || pkg.endsWith(".ime")

    private fun schedule(force: Boolean = false) {
        pending?.cancel()
        pending = scope.launch { delay(DEBOUNCE_MS); recheck(force = force) }
    }

    private fun env(): Env {
        val pm = getSystemService(PowerManager::class.java)
        val km = getSystemService(KeyguardManager::class.java)
        val usable = pm?.isInteractive != false && km?.isKeyguardLocked != true
        @Suppress("DEPRECATION")
        val call = runCatching { getSystemService(TelephonyManager::class.java)?.callState }.getOrNull()
        val mode = runCatching { getSystemService(AudioManager::class.java)?.mode }.getOrNull()
        return Env(usable, CountRules.inCall(call, mode))
    }

    /** The package of the focused application window, or null when the system does not say (secure window, none). */
    private fun activeAppPackage(): String? = runCatching {
        windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isActive }?.root?.let { r ->
            val p = r.packageName?.toString()
            @Suppress("DEPRECATION")
            runCatching { r.recycle() }
            p
        }
    }.getOrNull()

    private suspend fun recheck(force: Boolean) {
        val now = System.currentTimeMillis()
        // A dialog, chooser or bubble from another package can take the last window event and leave the app that is
        // really in front forgotten (counter gone until the next switch): trust the focused window over the last event.
        activeAppPackage()?.takeIf { it != foregroundPkg && !isTransientSystemUi(it) }?.let { foregroundPkg = it; lastUrl = null }
        val pkg = foregroundPkg
        if (pkg == null || pkg == packageName) { pauseCounting(now); return }
        val url = lastUrl
        val key = "$pkg|${url?.let { BrowserUrls.hostOf(it) }}"
        if (!force && key == lastCheckKey && now - lastCheckAt < SAME_KEY_MS) return
        lastCheckKey = key
        lastCheckAt = now
        // A cancelled pass applied nothing, so the next one must not be skipped as a duplicate of it.
        try {
            val env = env()
            if (!env.screenUsable) { pauseCounting(now); return }
            // Charge the time since the last check at the previous rate before deciding with the new balance.
            attempt { ServiceLocator.meterEngine.advance(now, env.allowed) }
            val decision = attempt { ServiceLocator.blockPolicy.decide(pkg, url, now) }
                .getOrDefault(BlockDecision.Allow)
            val ruleKey = (decision as? BlockDecision.Metered)?.key
            grantKey?.let { if (it != ruleKey) ServiceLocator.grants.leave(it, now) }
            grantKey = ruleKey
            var primary: Counted? = null
            when (decision) {
                is BlockDecision.Block -> {
                    pauseCounting(now)
                    if (foregroundPkg == pkg) showLock(pkg, url, decision)
                    return
                }
                is BlockDecision.Metered -> {
                    if (ServiceLocator.grants.active(decision.key, now)) {
                        ServiceLocator.grants.enter(decision.key)
                        primary = Counted(decision.key, pkg, decision.ratePct, primary = true)
                    } else {
                        pauseCounting(now)
                        if (foregroundPkg == pkg) showGate(pkg, url, decision)
                        return
                    }
                }
                BlockDecision.Allow -> Unit
            }
            // A failing look at the windows beside the app must not stop the app in front from being counted.
            val extras = attempt { extraTargets(pkg, now) }.getOrDefault(emptyList())
            applyCounting(CountRules.pick(primary, extras), env, now)
        } catch (e: kotlinx.coroutines.CancellationException) {
            lastCheckKey = null
            throw e
        }
    }

    /** Leisure apps visible beside the foreground one (picture-in-picture, split-screen); exhausted ones get the lock. */
    private suspend fun extraTargets(pkg: String, now: Long): List<Counted> {
        val wins = visibleWindows().filter { it.pkg != pkg && it.pkg != packageName }
        if (wins.isEmpty()) return emptyList()
        val rules = ServiceLocator.db.appRuleDao().all()
        val cand = CountRules.visibleLeisure(wins) { p ->
            BlockRules.leisureRule(rules, p, null)?.let { it.key to Charging.effective(it.meterPct) }
        }
        val out = ArrayList<Counted>()
        for (c in cand) {
            when (val d = ServiceLocator.blockPolicy.decide(c.pkg, null, now)) {
                is BlockDecision.Metered -> out += c.copy(ratePct = d.ratePct)
                is BlockDecision.Block -> if (now - lastExtraLockAt > EXTRA_LOCK_MS) {
                    lastExtraLockAt = now
                    showLock(c.pkg, null, d)
                }
                BlockDecision.Allow -> Unit
            }
        }
        return out
    }

    private fun visibleWindows(): List<WinInfo> = runCatching {
        windows.mapNotNull { w ->
            if (w.type != AccessibilityWindowInfo.TYPE_APPLICATION) return@mapNotNull null
            val root = w.root ?: return@mapNotNull null
            val p = root.packageName?.toString()
            @Suppress("DEPRECATION")
            runCatching { root.recycle() }
            p?.let { WinInfo(it, w.isInPictureInPictureMode) }
        }
    }.getOrDefault(emptyList())

    private suspend fun applyCounting(pick: Counted?, env: Env, now: Long) {
        val engine = ServiceLocator.meterEngine
        val active = pick?.takeIf { env.allowed }?.let { MeterActive(it.key, it.pkg, it.ratePct, now) }
        attempt { engine.switchTo(active, now) }
        armed = pick != null
        if (pick == null) { stopTicker(); hideOverlay(); return }
        label = if (pick.key == pick.pkg) labelOf(pick.pkg) else pick.key
        attempt { syncBalance(now) }
        startTicker()
        renderOverlay(now)
        scheduleZero(now)
    }

    private suspend fun syncBalance(now: Long) {
        val b = ServiceLocator.timeBank.balance(now)
        ServiceLocator.meterEngine.syncBalance(b.minutes)
        expiryAt = b.nextExpiryAt
    }

    /** Predicted moment the balance reaches zero (or a lot expires): re-check exactly then, no polling lag. */
    private fun scheduleZero(now: Long) {
        zeroJob?.cancel()
        val rem = ServiceLocator.meterEngine.remainingMs(now) ?: return
        val wait = minOf(rem, expiryAt?.let { it - now } ?: Long.MAX_VALUE).coerceAtLeast(0) + 50
        zeroJob = scope.launch {
            delay(wait)
            val t = System.currentTimeMillis()
            attempt { ServiceLocator.meterEngine.advance(t, env().allowed) }
            // Separate job: the re-check cancels and re-creates this one.
            scope.launch { recheck(force = true) }
        }
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            var wasAllowed = env().allowed
            lastTickAdvance = System.currentTimeMillis()
            while (isActive) {
                delay(1_000)
                val now = System.currentTimeMillis()
                val e = env()
                if (e.allowed != wasAllowed || !e.screenUsable) { wasAllowed = e.allowed; scope.launch { recheck(force = true) }; continue }
                if (now - lastTickAdvance >= TICK_MS) {
                    lastTickAdvance = now
                    attempt { ServiceLocator.meterEngine.advance(now, e.allowed) }
                }
                renderOverlay(now)
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel(); ticker = null
        zeroJob?.cancel(); zeroJob = null
    }

    private fun renderOverlay(now: Long) {
        val a = ServiceLocator.meterEngine.current()
        val rem = ServiceLocator.meterEngine.remainingMs(now)
        if (a == null || rem == null || a.ratePct == 0) { hideOverlay(); return }
        if (OverlayMath.crossedVibrate(prevRemaining, rem)) vibrate()
        prevRemaining = rem
        overlay?.show(a.pkg, label, rem, a.ratePct)
        // Unbroken use of the same app: a gentle buzz and a note every 20 minutes.
        val elapsed = now - a.since
        OverlayMath.crossedStreak(prevElapsed.takeIf { prevKey == a.key }, elapsed)?.let { min ->
            vibrate()
            overlay?.nudge(getString(com.earnd.app.R.string.meter_streak_note, min))
        }
        prevKey = a.key
        prevElapsed = elapsed
    }

    private fun hideOverlay() { overlay?.hide(); prevRemaining = null; prevKey = null; prevElapsed = null }

    private fun vibrate() {
        runCatching {
            getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    /** Stops counting (charging up to [now]) and removes the overlay: leaving the app, screen off, lock/gate shown. */
    private suspend fun pauseCounting(now: Long) {
        attempt { ServiceLocator.meterEngine.stop(now) }
        armed = false
        stopTicker()
        hideOverlay()
        grantKey?.let { ServiceLocator.grants.leave(it, now) }
        grantKey = null
    }

    private fun labelOf(pkg: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    private fun showLock(pkg: String, url: String?, d: BlockDecision.Block) {
        val i = if (d.cause == com.earnd.app.domain.BlockCause.FOCUS) com.earnd.app.focus.FocusActivity.intent(this)
        else LockedActivity.intent(this, pkg, url?.let { BrowserUrls.hostOf(it) }, d.cause, d.until)
        runCatching { startActivity(i) }.onFailure { performGlobalAction(GLOBAL_ACTION_HOME) }
        // The lock screen now covers the blocked app; forget it until it is opened again.
        foregroundPkg = null
        lastCheckKey = null
    }

    private suspend fun showGate(pkg: String, url: String?, d: BlockDecision.Metered) {
        val bal = runCatching { ServiceLocator.timeBank.balance(System.currentTimeMillis()).minutes }.getOrDefault(0)
        val host = url?.let { BrowserUrls.hostOf(it) }
        val i = LockedActivity.gateIntent(this, pkg, host, d.key, d.ratePct, bal)
        runCatching { startActivity(i) }.onFailure { performGlobalAction(GLOBAL_ACTION_HOME) }
        foregroundPkg = null
        lastCheckKey = null
    }

    override fun onInterrupt() = Unit

    private fun teardown() {
        runCatching { screenReceiver?.let { unregisterReceiver(it) } }
        screenReceiver = null
        overlay?.hide()
        val now = System.currentTimeMillis()
        CoroutineScope(Dispatchers.Default).launch { runCatching { ServiceLocator.meterEngine.stop(now) } }
        scope.cancel()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        teardown()
        // Switched off: let the safeguard re-check shortly, once the system setting has settled.
        runCatching { GuardReminder.schedule(this, System.currentTimeMillis() + 5_000L, replace = true) }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    private companion object {
        const val DEBOUNCE_MS = 250L
        const val SAME_KEY_MS = 1_500L
        const val URL_SCAN_MS = 700L
        const val TICK_MS = 30_000L
        const val WINDOWS_MS = 1_500L
        const val EXTRA_LOCK_MS = 60_000L
        val RECHECK_ON = setOf(ChangeKind.SESSION, ChangeKind.BLOCKLIST, ChangeKind.SETTINGS, ChangeKind.FOCUS)
    }
}
