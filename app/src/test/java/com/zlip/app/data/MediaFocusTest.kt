package com.zlip.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaFocusTest {

    private val delta = 0.0001f

    @Test
    fun aPortraitVideoOverflowsTheCoverVertically() {
        // 9:16 filling the 374 x 360 cover: as wide as the cover, 665 tall, so 305 reach past it.
        val (x, y) = MediaFocus.overflow(9f / 16f, 374f, 360f, MediaFit.FILL, 1f)
        assertEquals(0f, x, delta)
        assertEquals(374f * 16f / 9f - 360f, y, delta)
    }

    @Test
    fun aWidePhotoOverflowsTheCoverSideways() {
        val (x, y) = MediaFocus.overflow(4f / 3f, 374f, 360f, MediaFit.FILL, 1f)
        assertEquals(360f * 4f / 3f - 374f, x, delta)
        assertEquals(0f, y, delta)
    }

    @Test
    fun aFittedPictureOnlyOverflowsOnceZoomedIn() {
        // 16:9 fitted: 374 wide, 210 tall, nothing past the cover.
        assertEquals(0f to 0f, MediaFocus.overflow(16f / 9f, 374f, 360f, MediaFit.FIT, 1f))
        // Twice as close: 748 x 421, past the cover both ways.
        val (x, y) = MediaFocus.overflow(16f / 9f, 374f, 360f, MediaFit.FIT, 2f)
        assertEquals(374f, x, delta)
        assertEquals(374f * 2f * 9f / 16f - 360f, y, delta)
    }

    @Test
    fun zoomScalesTheFittedOrFilledSize() {
        val (fitW, fitH) = MediaFocus.size(2f, 400f, 400f, MediaFit.FIT, 1f)
        assertEquals(400f, fitW, delta)
        assertEquals(200f, fitH, delta)
        val (fillW, fillH) = MediaFocus.size(2f, 400f, 400f, MediaFit.FILL, 3f)
        assertEquals(2400f, fillW, delta)
        assertEquals(1200f, fillH, delta)
        // Zoom is kept in range.
        assertEquals(400f * MediaFocus.MAX_ZOOM, MediaFocus.size(2f, 400f, 400f, MediaFit.FIT, 50f).first, delta)
        assertEquals(400f, MediaFocus.size(2f, 400f, 400f, MediaFit.FIT, 0.1f).first, delta)
    }

    @Test
    fun anUnknownShapeOrEmptyBoxHasNothingToMove() {
        assertEquals(0f to 0f, MediaFocus.overflow(0f, 374f, 360f, MediaFit.FILL, 1f))
        assertEquals(0f to 0f, MediaFocus.overflow(1.5f, 0f, 360f, MediaFit.FILL, 2f))
        assertEquals(0f to 0f, MediaFocus.overflow(Float.NaN, 374f, 360f, MediaFit.FIT, 2f))
        assertNull(MediaFocus.place(0f, 374f, 360f, MediaFraming()))
    }

    @Test
    fun theFocusPlacesTheLeadingEdge() {
        assertEquals(0f, MediaFocus.offset(300f, -1f), delta)
        assertEquals(-150f, MediaFocus.offset(300f, 0f), delta)
        assertEquals(-300f, MediaFocus.offset(300f, 1f), delta)
        // Out of range never pushes the picture past its box.
        assertEquals(-300f, MediaFocus.offset(300f, 4f), delta)
        assertEquals(0f, MediaFocus.offset(-20f, 0.5f), delta)
    }

    @Test
    fun aPictureThatFitsIsCentredWhateverTheFocus() {
        assertEquals(50f, MediaFocus.leading(300f, 400f, 1f), delta)
        assertEquals(-100f, MediaFocus.leading(600f, 400f, 0f), delta)
        // A fitted wide picture: full width, centred vertically.
        val placed = MediaFocus.place(2f, 400f, 400f, MediaFraming(MediaFit.FIT, focusX = 1f, focusY = -1f))!!
        assertEquals(MediaPlacement(0f, 100f, 400f, 200f), placed)
    }

    @Test
    fun aZoomedFitMovesWithItsFocus() {
        // Fitted 2:1 picture twice as close: 800 x 400 in a 400 box, so it reaches 400 past it sideways only.
        val placed = MediaFocus.place(2f, 400f, 400f, MediaFraming(MediaFit.FIT, zoom = 2f, focusX = 1f, focusY = 1f))!!
        assertEquals(-400f, placed.left, delta)
        assertEquals(0f, placed.top, delta)
        assertEquals(800f, placed.width, delta)
    }

    @Test
    fun aDragMovesThePictureExactlyWithTheFinger() {
        val overflow = 300f
        val before = 0f
        // Finger 60 px down: the picture's top edge must come 60 px down too.
        val after = MediaFocus.dragged(before, 60f, overflow)
        assertEquals(60f, MediaFocus.offset(overflow, after) - MediaFocus.offset(overflow, before), delta)
    }

    @Test
    fun aDragStopsAtTheEdges() {
        assertEquals(-1f, MediaFocus.dragged(-0.9f, 500f, 300f), delta)
        assertEquals(1f, MediaFocus.dragged(0.9f, -500f, 300f), delta)
    }

    @Test
    fun anAxisWithoutOverflowKeepsItsFocus() {
        assertEquals(0.4f, MediaFocus.dragged(0.4f, 80f, 0f), delta)
    }

    @Test
    fun zoomingKeepsThePointUnderTheFingers() {
        val box = 400f
        val before = MediaFraming(MediaFit.FILL, zoom = 1.5f, focusX = 0.2f, focusY = -0.3f)
        val pivotX = 120f
        val pivotY = 260f
        val after = MediaFocus.zoomedAround(before, 1f, box, box, 2f, pivotX, pivotY)
        assertEquals(3f, after.zoom, delta)
        // The picture point under the fingers, as a fraction of the picture, is the same before and after.
        val a = MediaFocus.place(1f, box, box, before)!!
        val b = MediaFocus.place(1f, box, box, after)!!
        assertEquals((pivotX - a.left) / a.width, (pivotX - b.left) / b.width, delta)
        assertEquals((pivotY - a.top) / a.height, (pivotY - b.top) / b.height, delta)
    }

    @Test
    fun aPinchAlsoMovesThePictureWithTheFingers() {
        val before = MediaFraming(MediaFit.FILL, zoom = 2f)
        val after = MediaFocus.zoomedAround(before, 1f, 400f, 400f, 1f, 200f, 200f, panX = 50f, panY = -30f)
        val a = MediaFocus.place(1f, 400f, 400f, before)!!
        val b = MediaFocus.place(1f, 400f, 400f, after)!!
        assertEquals(50f, b.left - a.left, delta)
        assertEquals(-30f, b.top - a.top, delta)
    }

    @Test
    fun zoomStaysInRangeAndTheEdgesHold() {
        val start = MediaFraming(MediaFit.FIT, zoom = 3f)
        assertEquals(MediaFocus.MAX_ZOOM, MediaFocus.zoomedAround(start, 1f, 400f, 400f, 10f, 0f, 0f).zoom, delta)
        // Zooming in at the very corner pins the picture to that corner, never past it.
        val corner = MediaFocus.zoomedAround(MediaFraming(MediaFit.FIT), 1f, 400f, 400f, 2f, 0f, 0f)
        assertEquals(-1f, corner.focusX, delta)
        assertEquals(-1f, corner.focusY, delta)
        // A wild pan stops at the edge.
        val panned = MediaFocus.zoomedAround(start, 1f, 400f, 400f, 1f, 200f, 200f, panX = 1e6f)
        assertEquals(-1f, panned.focusX, delta)
        // A broken pinch changes nothing.
        val broken = MediaFocus.zoomedAround(start, 1f, 400f, 400f, Float.NaN, 200f, 200f)
        assertEquals(start.zoom, broken.zoom, delta)
        assertEquals(start.focusX, broken.focusX, delta)
        assertEquals(start.focusY, broken.focusY, delta)
    }

    @Test
    fun zoomingAllTheWayOutCentresWhatFits() {
        val zoomed = MediaFraming(MediaFit.FIT, zoom = 2f, focusX = 0.6f, focusY = -0.4f)
        val out = MediaFocus.zoomedAround(zoomed, 1f, 400f, 400f, 0.1f, 50f, 300f)
        assertEquals(MediaFraming(MediaFit.FIT), out)
        assertFalse(out.adjusted)
        assertTrue(zoomed.adjusted)
    }

    @Test
    fun theTalkBackZoomStepReachesTheEndsInFourSteps() {
        var zoom = MediaFocus.MIN_ZOOM
        repeat(4) { zoom = MediaFocus.clampZoom(zoom * MediaFocus.ZOOM_STEP) }
        assertEquals(MediaFocus.MAX_ZOOM, zoom, delta)
        repeat(4) { zoom = MediaFocus.clampZoom(zoom / MediaFocus.ZOOM_STEP) }
        assertEquals(MediaFocus.MIN_ZOOM, zoom, delta)
    }

    @Test
    fun clampRejectsNaN() {
        assertEquals(0f, MediaFocus.clamp(Float.NaN), delta)
        assertEquals(1f, MediaFocus.clamp(Float.POSITIVE_INFINITY), delta)
        assertEquals(1f, MediaFocus.clampZoom(Float.NaN), delta)
        assertEquals(MediaFocus.MAX_ZOOM, MediaFocus.clampZoom(Float.POSITIVE_INFINITY), delta)
    }
}
