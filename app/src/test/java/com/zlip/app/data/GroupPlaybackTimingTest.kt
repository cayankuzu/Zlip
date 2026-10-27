package com.zlip.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupPlaybackTimingTest {

    private val photo = CoverItem.Media(id = "photo", name = "Photo", fileName = "p.webp")
    private val gif = photo.copy(id = "gif", fileName = "g.anim.gif", kind = MediaKind.ANIMATION)
    private val clip = photo.copy(id = "clip", fileName = "c.mp4", kind = MediaKind.VIDEO, durationMs = 42_000)
    private val unmeasured = clip.copy(id = "old", fileName = "o.mp4", durationMs = 0)
    private val note = CoverItem.Text(id = "note", name = "Note", text = "Hi", scroll = false)
    private val marquee = note.copy(id = "marquee", scroll = true)
    private val animation = CoverItem.PixelArt(
        id = "animation",
        name = "A",
        width = 1,
        height = 1,
        pixels = listOf(1),
        extraFrames = listOf(listOf(2), listOf(3)),
        fps = 2,
    )

    private fun group(vararg items: CoverItem, step: Int = 5, toEnd: Boolean = true) =
        CoverItem.Group(id = "g", name = "G", items = items.toList(), stepSeconds = step, videosToEnd = toEnd)

    @Test
    fun aVideoToTheEndIgnoresThePhotoTime() {
        val toEnd = group(photo, clip, step = 30)
        assertTrue(GroupTiming.playsToEnd(toEnd, clip))
        assertEquals(GroupTiming.MIN_VIDEO_STEP_MS, GroupTiming.stepMinMs(toEnd, clip))
        assertTrue(GroupTiming.stepWaitsForPass(toEnd, clip))
        assertEquals(30_000L, GroupTiming.stepMinMs(toEnd, photo))
        assertFalse(GroupTiming.stepWaitsForPass(toEnd, photo))
    }

    @Test
    fun aCutVideoStaysThePhotoTimeAndNoLonger() {
        val cut = group(photo, clip, step = 10, toEnd = false)
        assertFalse(GroupTiming.playsToEnd(cut, clip))
        assertEquals(10_000L, GroupTiming.stepMinMs(cut, clip))
        assertFalse(GroupTiming.stepWaitsForPass(cut, clip))
        assertEquals(10_000L, GroupTiming.stepMaxMs(cut, clip))
        // GIFs still finish their pass either way.
        assertTrue(GroupTiming.stepWaitsForPass(cut, gif))
    }

    @Test
    fun aVideosBoundComesFromItsRealLength() {
        assertEquals(42_000L + 5_000, GroupTiming.videoPassMs(clip))
        assertEquals(GroupTiming.UNKNOWN_VIDEO_MS + 5_000, GroupTiming.videoPassMs(unmeasured))
        val toEnd = group(clip, photo)
        assertEquals(GroupTiming.MIN_VIDEO_STEP_MS + 47_000L, GroupTiming.stepMaxMs(toEnd, clip))
        assertEquals(GroupTiming.MIN_VIDEO_STEP_MS + 47_000L + 5_000L + GroupTiming.WRAP_GRACE_MS + 5_000, GroupTiming.maxPassMs(toEnd))
    }

    @Test
    fun theLongestPassCoversEveryStepPlayingInFull() {
        // Thirty hour-long clips: the bound must never cut the last one short.
        val hour = 60 * 60_000L
        val long = clip.copy(durationMs = hour)
        val group = CoverItem.Group(id = "g", name = "G", items = List(CoverItem.Group.MAX_ITEMS) { long.copy(id = "v$it") })
        assertTrue(GroupTiming.maxPassMs(group) > CoverItem.Group.MAX_ITEMS * hour)
    }

    @Test
    fun theEstimateAddsPhotoTimesAndClipLengths() {
        assertEquals(GroupTiming.Estimate(5_000L + 42_000L + 5_000L, atLeast = false), GroupTiming.estimate(group(photo, clip, note)))
        // Cut videos count the photo time.
        assertEquals(GroupTiming.Estimate(10_000L, atLeast = false), GroupTiming.estimate(group(photo, clip, toEnd = false)))
    }

    @Test
    fun anUnknownLengthMakesItAtLeast() {
        val estimate = GroupTiming.estimate(group(photo, unmeasured))
        assertTrue(estimate.atLeast)
        assertEquals(5_000L + GroupTiming.MIN_VIDEO_STEP_MS, estimate.ms)
        assertTrue(GroupTiming.estimate(group(photo, gif)).atLeast)
        assertTrue(GroupTiming.estimate(group(photo, marquee)).atLeast)
        // Cut, an unknown length does not matter.
        assertFalse(GroupTiming.estimate(group(photo, unmeasured, toEnd = false)).atLeast)
    }

    @Test
    fun anAnimationRoundsUpToWholePasses() {
        // Three frames at 2 fps: 1.5 s a pass; at least 5 s means four passes, 6 s.
        assertEquals(GroupTiming.Estimate(6_000L, atLeast = false), GroupTiming.estimate(group(animation)))
    }

    @Test
    fun aTinyClipCountsAtLeastItsMinimum() {
        val tiny = clip.copy(durationMs = 300)
        assertEquals(GroupTiming.MIN_VIDEO_STEP_MS, GroupTiming.estimate(group(tiny)).ms)
    }

    @Test
    fun withoutShuffleTheListOrderPlays() {
        assertEquals(listOf(0, 1, 2, 3), GroupTiming.playOrder(4, shuffle = false, seed = 7, pass = 3))
        assertEquals(emptyList<Int>(), GroupTiming.playOrder(0, shuffle = true, seed = 7, pass = 0))
        assertEquals(listOf(0), GroupTiming.playOrder(1, shuffle = true, seed = 7, pass = 5))
    }

    @Test
    fun aShuffledPassPlaysEveryStepOnce() {
        for (size in 2..CoverItem.Group.MAX_ITEMS) {
            for (pass in 0 until 20) {
                val order = GroupTiming.playOrder(size, shuffle = true, seed = 1234L + size, pass = pass)
                assertEquals((0 until size).toList(), order.sorted())
            }
        }
    }

    @Test
    fun theShuffleIsStableForASeedAndChangesWithIt() {
        val first = GroupTiming.playOrder(10, shuffle = true, seed = 42, pass = 3)
        assertEquals(first, GroupTiming.playOrder(10, shuffle = true, seed = 42, pass = 3))
        // Another seed (another showing) or another pass gives another order (for these values).
        assertNotEquals(first, GroupTiming.playOrder(10, shuffle = true, seed = 43, pass = 3))
        assertNotEquals(first, GroupTiming.playOrder(10, shuffle = true, seed = 42, pass = 4))
    }

    @Test
    fun aNewPassNeverRepeatsTheStepJustShown() {
        for (size in 2..12) {
            for (seed in 0L until 30L) {
                var previous = GroupTiming.playOrder(size, shuffle = true, seed = seed, pass = 0)
                for (pass in 1 until 15) {
                    val order = GroupTiming.playOrder(size, shuffle = true, seed = seed, pass = pass)
                    assertNotEquals("size $size seed $seed pass $pass", previous.last(), order.first())
                    previous = order
                }
            }
        }
    }
}
