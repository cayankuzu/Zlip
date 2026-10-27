package com.zlip.app.ui.pixel

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelCanvasTest {

    private val box = IntSize(320, 320)

    /** 16 x 16 cells of 20 px, filling the box exactly. */
    private val grid = PixelGrid(Size(320f, 320f), 16, 16)

    @Test
    fun cellAtSeesThroughTheZoom() {
        assertEquals(1 to 2, grid.cellAt(Offset(25f, 45f), PixelZoom.FIT))
        // Twice the size, scrolled 100 px right and 60 px down: the same spot on screen is a later cell.
        val zoom = PixelZoom(2f, Offset(-100f, -60f))
        assertEquals(3 to 2, grid.cellAt(Offset(25f, 45f), zoom))
        assertNull(grid.cellAt(Offset(-1f, 10f), PixelZoom.FIT))
    }

    @Test
    fun gridMatchesThePreviewLayout() {
        // 330 px for 48 cells: whole 6 px cells, centred in the 42 px left over.
        val wide = PixelGrid(Size(330f, 330f), 48, 48)
        assertEquals(6f, wide.cell)
        assertEquals(21f, wide.left)
        assertEquals(21f, wide.top)
    }

    @Test
    fun pinchKeepsTheSpotUnderTheFingers() {
        val fingers = Offset(160f, 120f)
        val zoomed = PixelZoom.FIT.pinched(fingers, zoom = 2f, pan = Offset.Zero, size = box)
        assertEquals(2f, zoomed.scale)
        assertEquals(fingers, zoomed.toCanvas(fingers))
        assertTrue(zoomed.zoomed)
        // Moving both fingers drags the canvas along with them.
        val dragged = zoomed.pinched(fingers + Offset(-30f, 0f), zoom = 1f, pan = Offset(-30f, 0f), size = box)
        assertEquals(zoomed.offset.x - 30f, dragged.offset.x)
    }

    @Test
    fun zoomStaysBetweenFitAndMaximumAndNeverShowsPastTheEdges() {
        val far = PixelZoom.FIT.pinched(Offset(160f, 160f), zoom = 20f, pan = Offset.Zero, size = box)
        assertEquals(PixelZoom.MAX_SCALE, far.scale)
        val back = far.pinched(Offset(160f, 160f), zoom = 0.01f, pan = Offset.Zero, size = box)
        assertEquals(PixelZoom.FIT, back)
        assertFalse(back.zoomed)
        // Dragged past the left edge: pulled back so the canvas still covers the box.
        val zoomed = PixelZoom(2f, Offset(-160f, -160f))
        assertEquals(0f, zoomed.pinched(Offset(160f, 160f), zoom = 1f, pan = Offset(500f, 0f), size = box).offset.x)
        assertEquals(-320f, PixelZoom(2f, Offset(-900f, 0f)).fitted(box).offset.x)
    }
}
