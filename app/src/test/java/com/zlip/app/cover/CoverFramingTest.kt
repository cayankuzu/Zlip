package com.zlip.app.cover

import androidx.compose.ui.geometry.Offset
import com.zlip.app.data.CoverItem
import com.zlip.app.data.MediaFit
import com.zlip.app.data.MediaFocus
import com.zlip.app.data.MediaFraming
import com.zlip.app.data.MediaKind
import com.zlip.app.data.framed
import com.zlip.app.data.framing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverFramingTest {

    private val delta = 0.0001f

    /** The same point; -0 and 0 count as one, as they do on screen. */
    private fun assertPoint(expected: Offset, actual: Offset) {
        assertEquals(expected.x, actual.x, delta)
        assertEquals(expected.y, actual.y, delta)
    }
    private val photo = CoverItem.Media(id = "p", name = "Photo", fileName = "p.jpg")
    private val clip = CoverItem.Media(id = "v", name = "Clip", fileName = "v.mp4", kind = MediaKind.VIDEO)
    private val text = CoverItem.Text(id = "t", name = "Text", text = "Hi")
    private val group = CoverItem.Group(id = "g", name = "Group", items = listOf(text, photo, clip))

    @Test
    fun aPhotoOnTheCoverIsFramable() {
        assertSame(photo, mediaOnScreen(photo, step = null))
    }

    @Test
    fun aGroupIsFramableOnlyOnAPhotoOrVideoStep() {
        assertSame(clip, mediaOnScreen(group, step = clip))
        assertNull(mediaOnScreen(group, step = text))
        // Before the group reported its first step, and for anything that is no media at all.
        assertNull(mediaOnScreen(group, step = null))
        assertNull(mediaOnScreen(text, step = null))
        assertNull(mediaOnScreen(null, step = null))
    }

    @Test
    fun aGroupStepIsTakenAsTheGroupHoldsItNow() {
        // The step reported before its framing was saved: the group's own, newer copy counts.
        val zoomed = photo.framed(MediaFraming(MediaFit.FILL, zoom = 2f))
        val saved = group.copy(items = listOf(text, zoomed, clip))
        assertEquals(zoomed, mediaOnScreen(saved, step = photo))
    }

    @Test
    fun aStepNoLongerInTheGroupIsNotFramable() {
        assertNull(mediaOnScreen(group.copy(items = listOf(text)), step = photo))
    }

    @Test
    fun anEditShowsOverTheFramingItStartedFrom() {
        val shown = MediaFraming(MediaFit.FIT, zoom = 2f, focusX = 0.5f)
        val edit = FramingEdit("p", base = MediaFraming(), shown = shown)
        assertEquals(shown, edit.framingOf(photo))
    }

    @Test
    fun anEditStillShowsOnceSaved() {
        val shown = MediaFraming(MediaFit.FIT, zoom = 2f)
        val edit = FramingEdit("p", base = MediaFraming(), shown = shown)
        assertEquals(shown, edit.framingOf(photo.framed(shown)))
    }

    @Test
    fun aChangeFromElsewhereWinsOverAnEdit() {
        // The small window switched the photo to Fill while the cover still held a pinch.
        val edit = FramingEdit("p", base = MediaFraming(), shown = MediaFraming(MediaFit.FIT, zoom = 2f))
        val filled = photo.framed(MediaFraming(MediaFit.FILL))
        assertFalse(edit.standsOver(filled.framing))
        assertEquals(MediaFraming(MediaFit.FILL), edit.framingOf(filled))
    }

    @Test
    fun anEditOnlyAppliesToItsOwnItem() {
        val other = photo.copy(id = "q")
        val edit = FramingEdit("p", base = MediaFraming(), shown = MediaFraming(MediaFit.FIT, zoom = 3f))
        assertEquals(MediaFraming(), edit.framingOf(other))
        assertEquals(MediaFraming(), (null as FramingEdit?).framingOf(photo))
    }

    @Test
    fun anUnturnedCoverTakesTheFingersAsTheyAre() {
        assertPoint(Offset(3f, -4f), CoverFramingMath.unrotated(Offset(3f, -4f), 0))
    }

    @Test
    fun aTurnedCoverTakesTheFingersBackIntoTheContentsDirections() {
        // Content turned a quarter clockwise: its right points down on the cover, so a finger moving down moves it right.
        assertPoint(Offset(1f, 0f), CoverFramingMath.unrotated(Offset(0f, 1f), 90))
        assertPoint(Offset(0f, -1f), CoverFramingMath.unrotated(Offset(1f, 0f), 90))
        // Upside down: everything goes the other way.
        assertPoint(Offset(-3f, 4f), CoverFramingMath.unrotated(Offset(3f, -4f), 180))
        // Three quarters clockwise: its right points up on the cover.
        assertPoint(Offset(1f, 0f), CoverFramingMath.unrotated(Offset(0f, -1f), 270))
        assertPoint(Offset(0f, 1f), CoverFramingMath.unrotated(Offset(1f, 0f), 270))
    }

    @Test
    fun aQuarterTurnFourTimesIsNoTurnAtAll() {
        var v = Offset(2f, 5f)
        repeat(4) { v = CoverFramingMath.unrotated(v, 90) }
        assertPoint(Offset(2f, 5f), v)
        assertPoint(Offset(2f, 5f), CoverFramingMath.unrotated(Offset(2f, 5f), 360))
    }

    @Test
    fun movingOnAnUnturnedCoverIsAPlainZoomAround() {
        val framing = MediaFraming(MediaFit.FIT, zoom = 2f)
        val moved = CoverFramingMath.moved(framing, 1f, 360f, 360f, 0, 1.5f, Offset(100f, 200f), Offset(10f, -5f))
        assertEquals(MediaFocus.zoomedAround(framing, 1f, 360f, 360f, 1.5f, 100f, 200f, 10f, -5f), moved)
    }

    @Test
    fun aDragOnAnUpsideDownCoverMovesThePictureTheOtherWayInItsOwnTerms() {
        // A square photo, twice as close as its fit: it reaches past the cover on both axes, centred.
        val framing = MediaFraming(MediaFit.FIT, zoom = 2f)
        val upright = CoverFramingMath.moved(framing, 1f, 360f, 360f, 0, 1f, Offset(180f, 180f), Offset(40f, 0f))
        val upsideDown = CoverFramingMath.moved(framing, 1f, 360f, 360f, 180, 1f, Offset(180f, 180f), Offset(40f, 0f))
        // Dragged right, an upright picture shows more of its left; turned over, more of its right.
        assertTrue(upright.focusX < 0f)
        assertEquals(-upright.focusX, upsideDown.focusX, delta)
        assertEquals(0f, upsideDown.focusY, delta)
    }

    @Test
    fun pinchingAroundTheCentreOfATurnedCoverZoomsLikeAnUnturnedOne() {
        val framing = MediaFraming(MediaFit.FILL)
        val upright = CoverFramingMath.moved(framing, 4f / 3f, 374f, 360f, 0, 2f, Offset(187f, 180f), Offset.Zero)
        val turned = CoverFramingMath.moved(framing, 4f / 3f, 374f, 360f, 90, 2f, Offset(187f, 180f), Offset.Zero)
        assertEquals(upright.zoom, turned.zoom, delta)
        assertEquals(upright.focusX, turned.focusX, delta)
        assertEquals(upright.focusY, turned.focusY, delta)
    }
}
