package com.zlip.app.cover

import com.zlip.app.cover.BurnInShift.STEP_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class BurnInShiftTest {

    @Test
    fun stepsOnceAMinuteAndGoesRound() {
        assertEquals(0, BurnInShift.awakeStep(0, steps = 5))
        assertEquals(0, BurnInShift.awakeStep(STEP_MS - 1, steps = 5))
        assertEquals(1, BurnInShift.awakeStep(STEP_MS, steps = 5))
        assertEquals(4, BurnInShift.awakeStep(4 * STEP_MS + 30_000, steps = 5))
        assertEquals(0, BurnInShift.awakeStep(5 * STEP_MS, steps = 5))
    }

    @Test
    fun aWakeFromRestCarriesOnInsteadOfStartingAtTheFirstOffset() {
        // Always-on: a minute awake after each touch, then rest. Glances at different times land on
        // different offsets, not always on the first one.
        val glances = listOf(3 * STEP_MS + 5_000, 7 * STEP_MS + 40_000, 11 * STEP_MS + 1_000)
        val steps = glances.map { BurnInShift.awakeStep(it, steps = 5) }
        assertEquals(listOf(3, 2, 1), steps)
        steps.forEach { assertNotEquals(0, it) }
    }

    @Test
    fun theNextChangeLandsOnTheMinute() {
        assertEquals(STEP_MS, BurnInShift.msToNextStep(0))
        assertEquals(20_000L, BurnInShift.msToNextStep(2 * STEP_MS + 40_000))
        assertEquals(1L, BurnInShift.msToNextStep(STEP_MS - 1))
    }
}
