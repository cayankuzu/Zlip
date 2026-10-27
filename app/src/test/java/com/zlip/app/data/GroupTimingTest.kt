package com.zlip.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupTimingTest {

    private val photo = CoverItem.Media(id = "photo", name = "Photo", fileName = "p.webp")
    private val gif = photo.copy(id = "gif", fileName = "g.anim.gif", kind = MediaKind.ANIMATION)
    private val video = photo.copy(id = "video", fileName = "v.mp4", kind = MediaKind.VIDEO, posterFileName = "v.jpg")
    private val marquee = CoverItem.Text(id = "marquee", name = "Marquee", text = "HELLO")
    private val note = marquee.copy(id = "note", scroll = false)
    private val drawing = CoverItem.PixelArt(id = "drawing", name = "Drawing", width = 2, height = 1, pixels = listOf(1, 0))
    private val animation = drawing.copy(id = "animation", extraFrames = listOf(listOf(0, 1)))

    @Test
    fun stillsWaitForTheTimerAndEverythingWithAPassFinishesIt() {
        assertFalse(GroupTiming.isCycleBased(photo))
        assertFalse(GroupTiming.isCycleBased(note))
        assertFalse(GroupTiming.isCycleBased(drawing))
        assertFalse(GroupTiming.isCycleBased(CoverItem.Countdown(id = "c", name = "C", title = "Trip", targetMillis = 0L)))
        assertFalse(GroupTiming.isCycleBased(CoverItem.Qr(id = "q", name = "Q", kind = QrKind.TEXT, payload = "hi")))
        assertFalse(GroupTiming.isCycleBased(CoverItem.Toy(id = "t", name = "T", kind = ToyKind.DICE)))

        assertTrue(GroupTiming.isCycleBased(video))
        assertTrue(GroupTiming.isCycleBased(gif))
        assertTrue(GroupTiming.isCycleBased(marquee))
        assertTrue(GroupTiming.isCycleBased(animation))
        assertTrue(GroupTiming.isCycleBased(CoverItem.Group(id = "g", name = "G", items = listOf(photo))))
    }

    @Test
    fun aCollageWaitsOnlyWhenACellRotates() {
        val single = CoverItem.Collage(id = "c", name = "C", layout = CollageLayout.SIDE_BY_SIDE, cells = listOf(listOf(photo), listOf(note)))
        val rotating = single.copy(cells = listOf(listOf(photo, gif), listOf(note)))
        assertFalse(GroupTiming.isCycleBased(single))
        assertTrue(GroupTiming.isCycleBased(rotating))
    }

    @Test
    fun extraWaitKeepsThePlaylistBounds() {
        // A video of unknown length gets a generous bound.
        assertEquals(GroupTiming.UNKNOWN_VIDEO_MS + 5_000, GroupTiming.extraWaitMs(video))
        assertEquals(GroupTiming.MAX_EXTRA_WAIT_MS, GroupTiming.extraWaitMs(marquee))
        // 300 characters of the largest text crawling at 10 dp/s take far longer than the default bound.
        val crawl = marquee.copy(text = "x".repeat(300), sizeSp = 160, speedDps = 10)
        assertEquals(((300 * 160 * 0.7f * 2f + 800f) / 10 * 1_000).toLong() + 5_000, GroupTiming.extraWaitMs(crawl))
        // Every cell finishes a full interval after the longest one (staggered start).
        val collage = CoverItem.Collage(
            id = "c",
            name = "C",
            layout = CollageLayout.SIDE_BY_SIDE,
            cells = listOf(listOf(photo, gif, note, drawing), listOf(note)),
            intervalSeconds = 30,
        )
        assertEquals(5 * 30_000L + 5_000, GroupTiming.extraWaitMs(collage))
    }

    @Test
    fun aWholePassAddsEveryStepAndItsBound() {
        val stills = CoverItem.Group(id = "g", name = "G", items = listOf(photo, note, drawing), stepSeconds = 10)
        assertEquals(3 * 10_000L + GroupTiming.WRAP_GRACE_MS + 5_000, GroupTiming.maxPassMs(stills))

        val mixed = stills.copy(items = listOf(photo, video))
        // The video plays to its end: a moment, then its whole (unknown, so bounded) length.
        assertEquals(
            10_000L + GroupTiming.MIN_VIDEO_STEP_MS + GroupTiming.UNKNOWN_VIDEO_MS + 5_000 + GroupTiming.WRAP_GRACE_MS + 5_000,
            GroupTiming.maxPassMs(mixed),
        )
        assertEquals(GroupTiming.maxPassMs(mixed), GroupTiming.extraWaitMs(mixed))
    }

    @Test
    fun theLastStepWrapsOnceAndOneItemWrapsEveryTime() {
        assertEquals(GroupTiming.Step(1, wrapped = false), GroupTiming.next(0, 3))
        assertEquals(GroupTiming.Step(2, wrapped = false), GroupTiming.next(1, 3))
        assertEquals(GroupTiming.Step(0, wrapped = true), GroupTiming.next(2, 3))
        assertEquals(GroupTiming.Step(0, wrapped = true), GroupTiming.next(0, 1))

        // Walking a group from the start wraps exactly once per pass.
        var index = 0
        var passes = 0
        repeat(9) {
            val next = GroupTiming.next(index, 3)
            if (next.wrapped) passes++
            index = next.index
        }
        assertEquals(3, passes)
    }

    @Test
    fun onlyAPassWhereEveryStepFailedCountsAsFailed() {
        assertTrue(GroupTiming.allFailed(setOf(0, 1, 2), 3))
        assertFalse(GroupTiming.allFailed(setOf(0, 2), 3))
        assertTrue(GroupTiming.allFailed(setOf(0), 1))
        assertFalse(GroupTiming.allFailed(emptySet(), 0))
    }

    @Test
    fun stepTimeComesFromTheGroup() {
        assertEquals(5_000L, GroupTiming.stepMs(CoverItem.Group(id = "g", name = "G")))
        assertEquals(30_000L, GroupTiming.stepMs(CoverItem.Group(id = "g", name = "G", stepSeconds = 30)))
    }
}
