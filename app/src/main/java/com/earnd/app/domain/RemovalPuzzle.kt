package com.earnd.app.domain

import com.earnd.app.data.entity.PuzzleKind
import kotlin.random.Random

/**
 * Friction before removing a blocked app/site or loosening blocking. It slows an impulse down;
 * it is not meant to be hard. Hard-mode cooldowns still apply after the puzzle is solved.
 */
object RemovalPuzzle {
    data class Challenge(val kind: PuzzleKind, val prompt: String, val answer: String)

    /** Short original sentences; typed back exactly (case and punctuation count). */
    val SENTENCES = listOf(
        "I chose to lock this app because my time matters to me.",
        "Later I will be glad that I kept this boundary today.",
        "Opening this app will not finish anything I care about.",
        "I can earn the minutes back by doing one small habit first.",
        "A short walk would feel better than another hour of scrolling.",
        "I am loosening a rule I set for myself on a calmer day.",
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
            val s = SENTENCES[random.nextInt(SENTENCES.size)]
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
