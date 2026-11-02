package com.earnd.app.alarms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AlarmSoundsTest {
    private val opts = listOf(
        SoundOption("system:Argon", "Argon", "content://media/internal/audio/media/7", "alarm"),
        SoundOption("custom:gong.mp3", "gong.mp3", "file:///sdcard/Android/media/com.earnd.app/sounds/gong.mp3", "custom"),
    )

    private fun rejects(v: String) = try { AlarmSounds.resolve(v, opts); fail("accepted $v") } catch (_: IllegalArgumentException) {}

    @Test fun parsesKeys() {
        assertEquals(AlarmSounds.Key.Default, AlarmSounds.parseKey("default"))
        assertEquals(AlarmSounds.Key.Default, AlarmSounds.parseKey(" DEFAULT "))
        assertEquals(AlarmSounds.Key.System("Argon"), AlarmSounds.parseKey("system:Argon"))
        assertEquals(AlarmSounds.Key.Custom("a b.ogg"), AlarmSounds.parseKey("custom:a b.ogg"))
        assertNull(AlarmSounds.parseKey("custom:../x.mp3"))
        assertNull(AlarmSounds.parseKey("custom:x.flac"))
        assertNull(AlarmSounds.parseKey("banana"))
    }

    @Test fun resolves() {
        assertNull(AlarmSounds.resolve("default", opts))
        assertEquals(opts[0].uri, AlarmSounds.resolve("system:argon", opts))
        assertEquals(opts[1].uri, AlarmSounds.resolve("custom:gong.mp3", opts))
        assertEquals(opts[1].uri, AlarmSounds.resolve(opts[1].uri, opts))
    }

    @Test fun rejectsUnknownOrUnsafe() {
        rejects("system:Nope"); rejects("custom:other.mp3"); rejects("content://evil/1")
        rejects("file:///etc/passwd"); rejects("custom:../gong.mp3"); rejects("nonsense")
    }

    @Test fun fileNamesAndPaths() {
        assertTrue(AlarmSounds.isSafeFileName("Gong 2.M4A"))
        assertTrue(!AlarmSounds.isSafeFileName(".hidden.mp3"))
        assertTrue(!AlarmSounds.isSafeFileName("a/b.mp3"))
        assertEquals("gong.mp3", AlarmSounds.filePath(opts[1].uri)?.name)
        assertNull(AlarmSounds.filePath("content://x"))
        assertEquals("gong.mp3", AlarmSounds.nameOf(opts[1].uri, opts))
        assertEquals("Default", AlarmSounds.nameOf(null, opts))
    }
}
