package com.earnd.app.domain

import com.earnd.app.data.entity.PuzzleKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.earnd.app.i18n.TestStr
import kotlin.random.Random

class RemovalPuzzleTest {
    @Before fun strings() = TestStr.install()

    @Test fun noneHasNoChallenge() = assertNull(RemovalPuzzle.create(PuzzleKind.NONE))

    @Test fun arithmeticAnswerMatchesPrompt() {
        repeat(50) { seed ->
            val c = RemovalPuzzle.create(PuzzleKind.ARITHMETIC, Random(seed))!!
            val (a, b, d) = Regex("""(\d+) × (\d+) \+ (\d+)""").matchEntire(c.prompt)!!.destructured
            val expected = (a.toInt() * b.toInt() + d.toInt()).toString()
            assertEquals(expected, c.answer)
            assertTrue(RemovalPuzzle.check(c, " $expected "))
            assertFalse(RemovalPuzzle.check(c, (expected.toInt() + 1).toString()))
            assertFalse(RemovalPuzzle.check(c, ""))
        }
    }

    @Test fun sentenceMustMatchExactly() {
        val c = RemovalPuzzle.create(PuzzleKind.SENTENCE, Random(3))!!
        assertTrue(c.prompt in RemovalPuzzle.SENTENCES)
        assertTrue(RemovalPuzzle.check(c, c.prompt))
        assertTrue(RemovalPuzzle.check(c, "  ${c.prompt}\n"))
        assertFalse(RemovalPuzzle.check(c, c.prompt.lowercase()))
        assertFalse(RemovalPuzzle.check(c, c.prompt.dropLast(1)))
    }
}
