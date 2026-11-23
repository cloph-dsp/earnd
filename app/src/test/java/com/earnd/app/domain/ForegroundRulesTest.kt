package com.earnd.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundRulesTest {
    private val app = ForegroundRules.WINDOW_TYPE_APPLICATION
    private val ime = ForegroundRules.WINDOW_TYPE_INPUT_METHOD
    private val system = 3 // AccessibilityWindowInfo.TYPE_SYSTEM

    private data class Case(val type: Int, val pkg: String?, val shade: Boolean, val kind: ForegroundRules.Kind, val why: String)

    @Test fun transientPackagesNeverReplaceTheForegroundApp() {
        listOf(
            "com.android.systemui",
            "com.android.intentresolver",
            "com.google.android.gms",
            "com.google.android.gms.auth.api.signin.internal.SignInHubActivity",
            "com.android.vending",
            "com.android.vending.billing.InAppBillingActivity",
            "com.google.android.inputmethod.latin",
            "com.foo.ime",
        ).forEach { assertTrue(it, ForegroundRules.isTransientSystemUi(it)) }
        listOf(
            "com.aspiro.tidal",
            "com.android.chrome",
            "com.foo.launcher",
            "com.android.intentresolverx", // prefix must be a package boundary
        ).forEach { assertFalse(it, ForegroundRules.isTransientSystemUi(it)) }
    }

    @Test fun activeWindowKindKeepsTransientAndCoversWithNonAppWindows() {
        listOf(
            Case(app, "com.aspiro.tidal", false, ForegroundRules.Kind.APP, "app in front"),
            Case(app, null, false, ForegroundRules.Kind.COVERING, "unknown app window"),
            Case(app, "com.android.systemui", false, ForegroundRules.Kind.COVERING, "recents as a systemui app"),
            Case(app, "com.android.systemui", true, ForegroundRules.Kind.TRANSIENT, "shade"),
            Case(system, "com.android.systemui", true, ForegroundRules.Kind.TRANSIENT, "shade system window"),
            Case(system, null, false, ForegroundRules.Kind.COVERING, "system dialog"),
            Case(ime, "com.google.android.inputmethod.latin", false, ForegroundRules.Kind.TRANSIENT, "keyboard"),
            Case(app, "com.android.intentresolver", false, ForegroundRules.Kind.TRANSIENT, "share sheet"),
            Case(app, "com.google.android.gms", false, ForegroundRules.Kind.TRANSIENT, "sign-in modal"),
            Case(app, "com.android.vending", false, ForegroundRules.Kind.TRANSIENT, "billing modal"),
        ).forEach { assertEquals(it.why, it.kind, ForegroundRules.activeWindowKind(it.type, it.pkg, it.shade)) }
    }
}
