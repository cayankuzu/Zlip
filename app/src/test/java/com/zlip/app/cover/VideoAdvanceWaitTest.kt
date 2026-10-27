package com.zlip.app.cover

import com.zlip.app.cover.AdvanceWait.MIN_AFTER_START_MS
import com.zlip.app.cover.AdvanceWait.VideoWait
import com.zlip.app.data.CoverItem
import com.zlip.app.data.MediaImport
import com.zlip.app.data.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoAdvanceWaitTest {

    private val clip = CoverItem.Media(id = "clip", name = "Clip", fileName = "c.mp4", kind = MediaKind.VIDEO, durationMs = 240_000)

    @Test
    fun aVideoWaitsForItsWholeLengthNotThePhotoTime() {
        // Four minutes plus room; the photo time (seconds) plays no part.
        assertEquals(245_000L, AdvanceWait.videoCapMs(clip, seenMs = 0))
    }

    @Test
    fun anUnmeasuredVideoMayRunToTheImportCap() {
        assertEquals(MediaImport.MAX_VIDEO_MS + 5_000, AdvanceWait.videoCapMs(clip.copy(durationMs = 0), seenMs = 0))
    }

    @Test
    fun timeSeenInEarlierSessionsCounts() {
        assertEquals(125_000L, AdvanceWait.videoCapMs(clip, seenMs = 120_000))
        assertEquals(MIN_AFTER_START_MS, AdvanceWait.videoCapMs(clip, seenMs = 600_000))
    }

    @Test
    fun aShortLoopingClipHasRoomToFinishItsSecondPass() {
        // A 2.9 s clip: its first pass ends before the minimum, the second one at 5.8 s.
        val short = clip.copy(durationMs = 2_900)
        assertTrue(AdvanceWait.videoCapMs(short, seenMs = 0) >= 2 * 2_900)
    }

    @Test
    fun afterTheMinimumAPlayingClipWaitsForItsFirstPass() {
        assertEquals(VideoWait.FIRST_PASS, AdvanceWait.videoWait(failed = false, passSeen = false, holdsLastFrame = false))
    }

    @Test
    fun aLoopingClipShorterThanTheMinimumFinishesThePassItIsIn() {
        assertEquals(VideoWait.CURRENT_PASS, AdvanceWait.videoWait(failed = false, passSeen = true, holdsLastFrame = false))
    }

    @Test
    fun aPlayOnceClipThatEndedOrAFailedOneMovesOnAtTheMinimum() {
        assertEquals(VideoWait.DONE, AdvanceWait.videoWait(failed = false, passSeen = true, holdsLastFrame = true))
        assertEquals(VideoWait.DONE, AdvanceWait.videoWait(failed = true, passSeen = false, holdsLastFrame = false))
    }
}
