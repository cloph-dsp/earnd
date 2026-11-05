package com.earnd.app.backup

import com.earnd.app.i18n.TestStr
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class BackupCodecTest {
    @Before fun english() = TestStr.install()

    private val sample = BackupData(
        schemaVersion = 2,
        exportedAt = 1_790_000_000_000L,
        tables = mapOf(
            "habit" to listOf(
                linkedMapOf("id" to 1L, "name" to "Read \"20\" pages\nslowly ção ☕", "targetQuantity" to 2.5, "unit" to null),
                linkedMapOf("id" to 2L, "name" to "Walk", "targetQuantity" to null, "unit" to "km"),
            ),
            "setting" to listOf(linkedMapOf("key" to "s.hardMode", "value" to "true")),
            "ledger_entry" to emptyList(),
        ),
    )
    private val columns = mapOf(
        "habit" to setOf("id", "name", "targetQuantity", "unit"),
        "setting" to setOf("key", "value"),
        "ledger_entry" to setOf("id", "minutes"),
    )

    @Test fun roundTripPreservesEverything() {
        val back = BackupCodec.decode(BackupCodec.encode(sample))
        assertEquals(sample, back)
        assertEquals(3, back.rowCount())
        assertNull(BackupCodec.validate(back, 2, columns))
    }

    @Test fun rejectsForeignOrBrokenFiles() {
        listOf("", "{", "[]", """{"format":"other"}""", """{"format":"earnd-backup","formatVersion":99,"schemaVersion":1,"tables":{}}""")
            .forEach { text ->
                try { BackupCodec.decode(text); fail("accepted: $text") } catch (_: BackupFormatException) {}
            }
    }

    @Test fun validateFlagsSchemaMismatch() {
        assertTrue(BackupCodec.validate(sample, 1, columns)!!.contains("newer"))
        assertTrue(BackupCodec.validate(sample, 2, columns - "setting")!!.contains("setting"))
        val extra = sample.copy(tables = mapOf("setting" to listOf(mapOf("key" to "a", "bogus" to 1L))))
        assertTrue(BackupCodec.validate(extra, 2, columns)!!.contains("bogus"))
    }
}
