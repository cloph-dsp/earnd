package com.earnd.app.domain

import com.earnd.app.data.entity.PuzzleKind
import com.earnd.app.R
import com.earnd.app.i18n.Str
import kotlin.random.Random

/**
 * Friction before removing a blocked app/site or loosening blocking. It slows an impulse down;
 * it is not meant to be hard. Hard-mode cooldowns still apply after the puzzle is solved.
 */
object RemovalPuzzle {
    data class Challenge(val kind: PuzzleKind, val prompt: String, val answer: String)

    /** Short original sentences; typed back exactly (case and punctuation count). */
    val SENTENCES: List<String>
        get() = listOf(
            Str.get(R.string.home_puzzle_sentence_1),
            Str.get(R.string.home_puzzle_sentence_2),
            Str.get(R.string.home_puzzle_sentence_3),
            Str.get(R.string.home_puzzle_sentence_4),
            Str.get(R.string.home_puzzle_sentence_5),
            Str.get(R.string.home_puzzle_sentence_6),
        )

    /** Returns null for [PuzzleKind.NONE]. */
    fun create(kind: PuzzleKind, random: Random = Random.Default): Challenge? = when (kind) {
        PuzzleKind.NONE -> null
        PuzzleKind.ARITHMETIC -> {
            val a = random.nextInt(12, 50)
            val b = random.nextInt(3, 10)
            val c = random.nextInt(11, 90)
            Challenge(kind, "$a × $b + $c", (a * b + c).toString())
        }
        PuzzleKind.SENTENCE -> {
            val all = SENTENCES
            val s = all[random.nextInt(all.size)]
            Challenge(kind, s, s)
        }
    }

    /** Arithmetic ignores surrounding spaces; sentences must match exactly apart from outer whitespace. */
    fun check(challenge: Challenge, input: String): Boolean = when (challenge.kind) {
        PuzzleKind.NONE -> true
        PuzzleKind.ARITHMETIC -> input.trim() == challenge.answer
        PuzzleKind.SENTENCE -> input.trim() == challenge.answer
    }
}
