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
            "com.google.android.gsf",
            "com.android.keyguard",
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
            Case(app, "com.google.android.gsf", false, ForegroundRules.Kind.TRANSIENT, "account sign-in"),
            Case(app, "com.foo.oauth", false, ForegroundRules.Kind.APP, "unknown dialog is not guessed transient"),
        ).forEach { assertEquals(it.why, it.kind, ForegroundRules.activeWindowKind(it.type, it.pkg, it.shade)) }
    }

    @Test fun coveringPausesOnlyAfterTheGrace() {
        // Cover seen at 9_000, grace 1_000: a quick peek is free, staying covered is not.
        assertFalse(ForegroundRules.shouldPause(null, 10_000L, 1_000L))
        assertFalse(ForegroundRules.shouldPause(9_000L, 9_999L, 1_000L))
        assertTrue(ForegroundRules.shouldPause(9_000L, 10_000L, 1_000L))
        assertTrue(ForegroundRules.shouldPause(9_000L, 10_500L, 1_000L))
        assertFalse(ForegroundRules.shouldPause(9_000L, 8_000L, 1_000L)) // clock went backwards
    }

    @Test fun transientCandidatesNeverReplaceTheMeterTarget() {
        val current = "com.aspiro.tidal"
        assertTrue(ForegroundRules.keepTargetOnTransient(current, null))
        assertTrue(ForegroundRules.keepTargetOnTransient(current, current))
        listOf(
            "com.android.intentresolver", "com.google.android.gms.auth.api.signin.internal.SignInHubActivity",
            "com.google.android.gsf", "com.android.keyguard", "com.google.android.inputmethod.latin",
        ).forEach { assertTrue(it, ForegroundRules.keepTargetOnTransient(current, it)) }
        assertFalse(ForegroundRules.keepTargetOnTransient(current, "com.foo.otherapp"))
        assertFalse(ForegroundRules.keepTargetOnTransient(null, "com.foo.otherapp"))
    }
}
