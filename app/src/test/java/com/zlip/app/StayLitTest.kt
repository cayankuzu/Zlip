package com.zlip.app

import com.zlip.app.StayLit.WATCH_CAP_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StayLitTest {

    private val minute = 60_000L

    @Test
    fun aWakeOnItsOwnNeverHoldsTheCover() {
        // A notification lights the cover in a pocket: the clip plays with sound, nobody touched it.
        val visibleSince = 100 * minute
        val touchedBefore = 40 * minute
        assertFalse(StayLit.watching(heard = true, touchedAt = touchedBefore, visibleSince = visibleSince))
        // The time still counts from the last touch, so the cover follows its own timeout.
        assertEquals(41 * minute, StayLit.deadline(since = touchedBefore, awakeMs = minute, watching = false, touchedAt = touchedBefore))
    }

    @Test
    fun aClipWatchedAfterATouchHoldsUpToTheLimit() {
        val visibleSince = 100 * minute
        val touched = visibleSince + 5_000
        assertTrue(StayLit.watching(heard = true, touchedAt = touched, visibleSince = visibleSince))
        assertEquals(
            touched + WATCH_CAP_MS + minute,
            StayLit.deadline(since = touched, awakeMs = minute, watching = true, touchedAt = touched),
        )
    }

    @Test
    fun nothingHeardIsNothingWatched() {
        assertFalse(StayLit.watching(heard = false, touchedAt = 10, visibleSince = 5))
    }

    @Test
    fun aClipThatEndsBeforeTheLimitRestartsTheTime() {
        val visibleSince = 0L
        val touched = 1_000L
        val ended = touched + 3 * minute
        assertTrue(StayLit.endCountsAsTouch(ended, touchedAt = touched, visibleSince = visibleSince))
        // The end then is the new start: the normal stay-lit time from there.
        assertEquals(ended + minute, StayLit.deadline(since = ended, awakeMs = minute, watching = false, touchedAt = touched))
    }

    @Test
    fun aPlaylistOfClipsCannotExtendItPastTheLimit() {
        // Auto-advance from clip to clip: each change is a short silence. After the limit it no longer counts.
        val touched = 1_000L
        val late = touched + WATCH_CAP_MS + 5_000
        assertFalse(StayLit.endCountsAsTouch(late, touchedAt = touched, visibleSince = 0))
        // The furthest any clip end can move the start is the limit, so the hold is bounded by it.
        val lastCountedEnd = touched + WATCH_CAP_MS - 1
        assertEquals(
            touched + WATCH_CAP_MS + minute,
            StayLit.deadline(since = lastCountedEnd, awakeMs = minute, watching = true, touchedAt = touched),
        )
    }

    @Test
    fun anEndAfterAWakeWithoutTouchDoesNotCount() {
        assertFalse(StayLit.endCountsAsTouch(now = 50 * minute, touchedAt = 10 * minute, visibleSince = 40 * minute))
    }
}
