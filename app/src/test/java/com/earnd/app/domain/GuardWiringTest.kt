package com.earnd.app.domain

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The JVM tests cover the pure clock helpers, but a regression that swaps the
 * arguments back at the Guard call sites would stay green. Pin the wiring text.
 */
class GuardWiringTest {
    private val source = run {
        val cwd = java.io.File(System.getProperty("user.dir"))
        // Gradle runs unit tests with the module dir as cwd; IDEs may use the repo root.
        val repo = if (java.io.File(cwd, "app").isDirectory) cwd else cwd.parentFile
        java.io.File(repo, "app/src/main/java/com/earnd/app/blocking/GuardAccessibilityService.kt").readText()
    }

    @Test
    fun guardWiresWallAndElapsedSeparately() {
        assertTrue("meterActiveFor must pass wall then elapsed", source.contains("meterActiveFor(it, now, elapsed)"))
        assertTrue("streak must use elapsed with sinceElapsed", source.contains("streakDelta(elapsed, a)"))
        assertTrue("COVERING pause must use elapsed grace", source.contains("shouldPause(since, elapsed, NON_APP_GRACE_MS)"))
        assertTrue("APP branch must keep target on transient", source.contains("keepTargetOnTransient(foregroundPkg, p)"))
    }
}
