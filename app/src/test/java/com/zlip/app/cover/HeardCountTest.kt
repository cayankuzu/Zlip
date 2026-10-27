package com.zlip.app.cover

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeardCountTest {

    @Test
    fun aClipIsHeardUntilItGoesQuiet() {
        var counts = HeardCount.update(emptyMap(), "clip", audible = true)
        assertTrue(HeardCount.isHeard(counts, "clip"))
        counts = HeardCount.update(counts, "clip", audible = false)
        assertFalse(HeardCount.isHeard(counts, "clip"))
        assertEquals(emptyMap<String, Int>(), counts)
    }

    @Test
    fun theNextStepStartingFirstIsNotHiddenByTheLastOneEnding() {
        // A group cuts from clip to clip: the new step may report "heard" before the old one reports "quiet".
        var counts = HeardCount.update(emptyMap(), "group", audible = true)
        counts = HeardCount.update(counts, "group", audible = true)
        counts = HeardCount.update(counts, "group", audible = false)
        assertTrue(HeardCount.isHeard(counts, "group"))
    }

    @Test
    fun aStrayQuietNeverGoesBelowNothing() {
        var counts = HeardCount.update(emptyMap(), "clip", audible = false)
        counts = HeardCount.update(counts, "clip", audible = true)
        assertTrue(HeardCount.isHeard(counts, "clip"))
    }

    @Test
    fun onlyTheAskedItemCounts() {
        val counts = HeardCount.update(emptyMap(), "a", audible = true)
        assertFalse(HeardCount.isHeard(counts, "b"))
        assertFalse(HeardCount.isHeard(counts, null))
    }
}
