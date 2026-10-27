package com.zlip.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaFocusTest {

    private val delta = 0.0001f

    @Test
    fun aPortraitVideoOverflowsTheCoverVertically() {
        // 9:16 on the 374 x 360 cover: as wide as the cover, 665 tall, so 305 reach past it.
        val (x, y) = MediaFocus.overflow(9f / 16f, 374f, 360f)
        assertEquals(0f, x, delta)
        assertEquals(374f * 16f / 9f - 360f, y, delta)
    }

    @Test
    fun aWidePhotoOverflowsTheCoverSideways() {
        val (x, y) = MediaFocus.overflow(4f / 3f, 374f, 360f)
        assertEquals(360f * 4f / 3f - 374f, x, delta)
        assertEquals(0f, y, delta)
    }

    @Test
    fun anUnknownShapeOrEmptyBoxHasNothingToMove() {
        assertEquals(0f to 0f, MediaFocus.overflow(0f, 374f, 360f))
        assertEquals(0f to 0f, MediaFocus.overflow(1.5f, 0f, 360f))
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
    fun clampRejectsNaN() {
        assertEquals(0f, MediaFocus.clamp(Float.NaN), delta)
        assertEquals(1f, MediaFocus.clamp(Float.POSITIVE_INFINITY), delta)
    }
}
