package com.zlip.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class ToyLogicTest {

    @Test
    fun dieIsFair() {
        val random = Random(7)
        val counts = IntArray(7)
        repeat(60_000) { counts[ToyLogic.rollDie(random)]++ }
        assertEquals(0, counts[0])
        for (face in 1..6) assertTrue("face $face: ${counts[face]}", abs(counts[face] - 10_000) < 500)
    }

    @Test
    fun coinIsFair() {
        val random = Random(11)
        val heads = (1..40_000).count { ToyLogic.flipCoin(random) }
        assertTrue("heads: $heads", abs(heads - 20_000) < 600)
    }

    @Test
    fun tumbleEndsOnTheResultAndAlwaysChangesFace() {
        val random = Random(3)
        repeat(500) {
            val final = ToyLogic.rollDie(random)
            val faces = ToyLogic.tumble(final, steps = 7, random = random)
            assertEquals(8, faces.size)
            assertEquals(final, faces.last())
            assertTrue(faces.all { it in 1..6 })
            faces.zipWithNext().forEach { (a, b) -> assertNotEquals(a, b) }
        }
    }

    @Test
    fun everyFaceHasItsNumberOfPips() {
        for (face in 1..6) {
            val pips = ToyLogic.pips(face)
            assertEquals(face, pips.size)
            assertEquals(face, pips.toSet().size)
            assertTrue(pips.all { (x, y) -> x in -1..1 && y in -1..1 })
        }
        assertTrue(ToyLogic.pips(0).isEmpty())
        assertTrue(ToyLogic.pips(7).isEmpty())
    }

    @Test
    fun coinHalfTurnsLandOnTheRightSide() {
        val random = Random(5)
        repeat(200) {
            val from = random.nextBoolean()
            val to = random.nextBoolean()
            val halfTurns = ToyLogic.coinHalfTurns(from, to, random)
            assertTrue(halfTurns in 6..9)
            // An odd number of half turns shows the other side.
            assertEquals(from != to, halfTurns % 2 == 1)
        }
    }

    @Test
    fun optionsAreCleaned() {
        val long = "x".repeat(40)
        val cleaned = ToyLogic.cleanOptions(listOf("  Pizza ", "", "   ", "Two\nlines", long) + List(20) { "Option $it" })
        assertEquals(ToyLogic.MAX_OPTIONS, cleaned.size)
        assertEquals("Pizza", cleaned[0])
        assertEquals("Two lines", cleaned[1])
        assertEquals(ToyLogic.MAX_OPTION_CHARS, cleaned[2].length)
        // Never half an emoji.
        val emoji = "a".repeat(ToyLogic.MAX_OPTION_CHARS - 1) + "🍕"
        assertEquals("a".repeat(ToyLogic.MAX_OPTION_CHARS - 1), ToyLogic.cleanOptions(listOf(emoji)).single())
    }

    @Test
    fun builtInSlicesFillInForEmptyOrTooFewOptions() {
        assertNull(ToyLogic.customWheelSlices(emptyList()))
        assertNull(ToyLogic.customWheelSlices(listOf("One", " ")))
        assertEquals(listOf("A", "B"), ToyLogic.customWheelSlices(listOf("A", "", "B")))
    }

    @Test
    fun sliceGeometryRoundTrips() {
        for (count in ToyLogic.MIN_WHEEL_SLICES..ToyLogic.MAX_OPTIONS) {
            for (slice in 0 until count) {
                for (fraction in listOf(0.05f, 0.5f, 0.95f)) {
                    val rotation = ToyLogic.rotationFor(slice, fraction, count)
                    assertTrue(rotation >= 0f && rotation < 360f)
                    assertEquals(slice, ToyLogic.sliceAt(rotation, count))
                    // Whole extra turns change nothing.
                    assertEquals(slice, ToyLogic.sliceAt(rotation + 3 * 360f, count))
                    assertEquals(slice, ToyLogic.sliceAt(rotation - 720f, count))
                }
            }
            assertEquals(0, ToyLogic.sliceAt(ToyLogic.restingWheelRotation(count), count))
        }
        // Turning a 4-slice wheel a quarter clockwise brings the last slice under the pointer.
        assertEquals(3, ToyLogic.sliceAt(ToyLogic.restingWheelRotation(4) + 90f, 4))
    }

    @Test
    fun wheelSpinsAreFairAndLandInsideASlice() {
        val random = Random(21)
        val count = 6
        val hits = IntArray(count)
        var rotation = 123f
        repeat(30_000) {
            val spin = ToyLogic.spinWheel(rotation, count, random)
            assertEquals(ToyLogic.normalize(rotation), spin.start, 0.001f)
            assertTrue(spin.target - spin.start >= 4 * 360f)
            assertTrue(spin.target - spin.start < 7 * 360f)
            assertTrue(spin.durationMs in 3_200L..4_500L)
            // Never on a line: well inside the slice.
            val into = ToyLogic.normalize(-spin.target) % 60f / 60f
            assertTrue(into in 0.11f..0.89f)
            hits[ToyLogic.sliceAt(spin.target, count)]++
            rotation = ToyLogic.normalize(spin.target)
        }
        hits.forEach { assertTrue("hits $it", abs(it - 5_000) < 350) }
    }

    @Test
    fun neighbouringSlicesNeverShareATint() {
        for (count in 2..ToyLogic.MAX_OPTIONS) {
            for (index in 0 until count) {
                val next = (index + 1) % count
                assertNotEquals("count $count, slice $index", ToyLogic.sliceTint(index, count), ToyLogic.sliceTint(next, count))
            }
        }
    }

    @Test
    fun easingAndAnglesBehave() {
        assertEquals(0f, ToyLogic.decelerate(0f), 0f)
        assertEquals(1f, ToyLogic.decelerate(1f), 0f)
        assertEquals(1f, ToyLogic.decelerate(2f), 0f)
        assertTrue(ToyLogic.decelerate(0.5f) > 0.5f)
        assertEquals(350f, ToyLogic.normalize(-10f), 0.001f)
        assertEquals(10f, ToyLogic.normalize(730f), 0.001f)
        assertEquals(0, ToyLogic.nearestTick(359f))
        assertEquals(6, ToyLogic.nearestTick(90f))
        assertEquals(1, ToyLogic.nextCount(0))
        assertEquals(ToyLogic.MAX_COUNT, ToyLogic.nextCount(ToyLogic.MAX_COUNT))
    }

    @Test
    fun flapperBendsBeforeEachLineAndSnapsBack() {
        val count = 4
        // Just before the line between slice 0 and the last slice passes the pointer, it is bent most.
        val nearLine = ToyLogic.rotationFor(0, 0.01f, count)
        assertTrue(ToyLogic.flapperBend(nearLine, count) > 0.9f)
        assertEquals(0f, ToyLogic.flapperBend(ToyLogic.rotationFor(0, 0.5f, count), count), 0f)
        assertEquals(0f, ToyLogic.flapperBend(ToyLogic.rotationFor(1, 0.99f, count), count), 0f)
    }

    @Test
    fun timerAndStopwatchFormatsBehave() {
        assertEquals("25:00", ToyLogic.formatTimerMmSs(ToyLogic.POMODORO_DURATION_MS))
        assertEquals("00:01", ToyLogic.formatTimerMmSs(100L))
        assertEquals("00:00", ToyLogic.formatTimerMmSs(0L))
        assertEquals("00:00", ToyLogic.formatStopwatchMmSs(0L))
        assertEquals("01:05", ToyLogic.formatStopwatchMmSs(65_400L))
        assertEquals("01:05.4", ToyLogic.formatStopwatchTenths(65_400L))
    }
}
