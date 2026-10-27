package com.zlip.app.cover.mirror

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.zlip.app.data.MirrorFit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MirrorLayoutTest {

    private val cover = IntSize(748, 720)
    private val portrait = IntSize(1080, 2640)
    private val landscape = IntSize(2640, 1080)

    @Test
    fun fitUsesTheCoverSizeForPortraitAndLandscape() {
        for (content in listOf(portrait, landscape)) {
            val layout = mirrorLayout(content, cover, MirrorFit.FIT, pan = 0f)
            assertEquals(cover, layout.display)
            assertEquals(IntOffset.Zero, layout.offset)
            assertEquals(PanAxis.NONE, layout.panAxis)
        }
    }

    @Test
    fun fitIgnoresPan() {
        assertEquals(mirrorLayout(portrait, cover, MirrorFit.FIT, 0f), mirrorLayout(portrait, cover, MirrorFit.FIT, 1f))
    }

    @Test
    fun fillPortraitKeepsTheWidthAndOverflowsDownwards() {
        val layout = mirrorLayout(portrait, cover, MirrorFit.FILL, pan = 0f)
        assertEquals(IntSize(748, 1828), layout.display)
        assertEquals(PanAxis.VERTICAL, layout.panAxis)
        // Centered: half of the 1108 px overflow sits above the cover.
        assertEquals(IntOffset(0, -554), layout.offset)
    }

    @Test
    fun fillLandscapeKeepsTheHeightAndOverflowsSideways() {
        val layout = mirrorLayout(landscape, cover, MirrorFit.FILL, pan = 0f)
        assertEquals(IntSize(1760, 720), layout.display)
        assertEquals(PanAxis.HORIZONTAL, layout.panAxis)
        assertEquals(IntOffset(-506, 0), layout.offset)
    }

    @Test
    fun panReachesBothEdges() {
        assertEquals(IntOffset(0, 0), mirrorLayout(portrait, cover, MirrorFit.FILL, pan = -1f).offset)
        assertEquals(IntOffset(0, -1108), mirrorLayout(portrait, cover, MirrorFit.FILL, pan = 1f).offset)
        assertEquals(IntOffset(0, 0), mirrorLayout(landscape, cover, MirrorFit.FILL, pan = -1f).offset)
        assertEquals(IntOffset(-1012, 0), mirrorLayout(landscape, cover, MirrorFit.FILL, pan = 1f).offset)
    }

    @Test
    fun panIsClampedToTheEdges() {
        assertEquals(mirrorLayout(portrait, cover, MirrorFit.FILL, 1f), mirrorLayout(portrait, cover, MirrorFit.FILL, 7f))
        assertEquals(mirrorLayout(landscape, cover, MirrorFit.FILL, -1f), mirrorLayout(landscape, cover, MirrorFit.FILL, -3f))
    }

    @Test
    fun fillAlwaysCoversTheCoverForOddSizes() {
        val oddCover = IntSize(747, 721)
        for (content in listOf(IntSize(1081, 2641), IntSize(2641, 1081), IntSize(999, 1001), IntSize(3, 7))) {
            for (pan in listOf(-1f, -0.3f, 0f, 0.6f, 1f)) {
                val layout = mirrorLayout(content, oddCover, MirrorFit.FILL, pan)
                assertTrue(layout.display.width >= oddCover.width && layout.display.height >= oddCover.height)
                // Placed so that the view still spans the whole cover.
                assertTrue(layout.offset.x <= 0 && layout.offset.x + layout.display.width >= oddCover.width)
                assertTrue(layout.offset.y <= 0 && layout.offset.y + layout.display.height >= oddCover.height)
            }
        }
    }

    @Test
    fun fillOfACoverShapedScreenNeedsNoPan() {
        val layout = mirrorLayout(IntSize(1496, 1440), cover, MirrorFit.FILL, pan = 1f)
        assertEquals(cover, layout.display)
        assertEquals(IntOffset.Zero, layout.offset)
        assertEquals(PanAxis.NONE, layout.panAxis)
    }

    @Test
    fun anUnknownScreenSizeFallsBackToFit() {
        assertEquals(mirrorLayout(portrait, cover, MirrorFit.FIT, 0f), mirrorLayout(IntSize.Zero, cover, MirrorFit.FILL, 0f))
    }
}
