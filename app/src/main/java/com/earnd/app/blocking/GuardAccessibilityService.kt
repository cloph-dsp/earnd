package com.earnd.app.blocking

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.earnd.app.ServiceLocator
import com.earnd.app.domain.BlockDecision
import com.earnd.app.domain.ChangeKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Watches which app (and, in browsers, which site) is in front and shows the
 * lock screen when [com.earnd.app.domain.BlockPolicy] says so.
 */
class GuardAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var foregroundPkg: String? = null
    private var lastUrl: String? = null
    private var lastCheckKey: String? = null
    private var lastCheckAt = 0L
    private var lastUrlScanAt = 0L
    private var pending: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        ServiceLocator.init(applicationContext)
        scope.launch {
            ServiceLocator.changeBus.events.collect { kind ->
                if (kind == ChangeKind.BLOCKLIST) ServiceLocator.essentialApps.refresh()
                if (kind in RECHECK_ON) recheck(force = true)
            }
        }
        // Sessions expire silently; tick so an expired session re-locks the current app.
        scope.launch {
            while (isActive) { delay(TICK_MS); recheck(force = true) }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
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

    /** Notification shade and keyboards briefly take focus without changing the foreground app. */
    private fun isTransientSystemUi(pkg: String) =
        pkg == "com.android.systemui" || pkg.contains("inputmethod") || pkg.endsWith(".ime")

    private fun schedule() {
        pending?.cancel()
        pending = scope.launch { delay(DEBOUNCE_MS); recheck(force = false) }
    }

    private suspend fun recheck(force: Boolean) {
        val pkg = foregroundPkg ?: return
        if (pkg == packageName) return
        val url = lastUrl
        val key = "$pkg|${url?.let { BrowserUrls.hostOf(it) }}"
        val now = System.currentTimeMillis()
        if (!force && key == lastCheckKey && now - lastCheckAt < SAME_KEY_MS) return
        lastCheckKey = key
        lastCheckAt = now
        val decision = runCatching { ServiceLocator.blockPolicy.decide(pkg, url, now) }
            .getOrDefault(BlockDecision.Allow)
        if (decision is BlockDecision.Block && foregroundPkg == pkg) showLock(pkg, url, decision)
    }

    private fun showLock(pkg: String, url: String?, d: BlockDecision.Block) {
        val i = LockedActivity.intent(this, pkg, url?.let { BrowserUrls.hostOf(it) }, d.cause, d.until)
        runCatching { startActivity(i) }.onFailure { performGlobalAction(GLOBAL_ACTION_HOME) }
        // The lock screen now covers the blocked app; forget it until it is opened again.
        foregroundPkg = null
        lastCheckKey = null
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        scope.cancel()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val DEBOUNCE_MS = 250L
        const val SAME_KEY_MS = 1_500L
        const val URL_SCAN_MS = 700L
        const val TICK_MS = 30_000L
        val RECHECK_ON = setOf(ChangeKind.SESSION, ChangeKind.BLOCKLIST, ChangeKind.SETTINGS)
    }
}
