package com.zlip.app.cover.mirror

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.zlip.app.data.MirrorFit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max

class MirrorLayoutTest {

    // The Z Flip7 FE: cover 748 x 720 px, inner screen 1080 x 2640 px.
    private val cover = IntSize(748, 720)
    private val portrait = IntSize(1080, 2640)
    private val landscape = IntSize(2640, 1080)

    private fun fill(content: IntSize) = startView(MirrorPreset.FILL, content, cover)

    private fun layout(content: IntSize, view: MirrorView) = mirrorLayout(content, cover, view)

    @Test
    fun wholeScreenShowsAllOfItCenteredWithNothingToPan() {
        val tall = layout(portrait, MirrorView())
        assertEquals(IntSize(295, 720), tall.frame)
        assertEquals(IntOffset(226, 0), tall.offset)
        assertFalse(tall.canPanX || tall.canPanY)

        val wide = layout(landscape, MirrorView())
        assertEquals(IntSize(748, 306), wide.frame)
        assertEquals(IntOffset(0, 207), wide.offset)
        assertFalse(wide.canPanX || wide.canPanY)
    }

    @Test
    fun fillOfAPortraitScreenStartsAtTheTop() {
        val placed = layout(portrait, fill(portrait))
        assertEquals(IntSize(748, 1828), placed.frame)
        assertEquals(IntOffset.Zero, placed.offset)
        assertTrue(placed.canPanY)
        assertFalse(placed.canPanX)
        assertTrue(placed.atStart)
    }

    @Test
    fun fillOfALandscapeScreenStartsCentered() {
        val placed = layout(landscape, fill(landscape))
        assertEquals(IntSize(1760, 720), placed.frame)
        // Half of the 1012 px overflow to the left.
        assertEquals(IntOffset(-506, 0), placed.offset)
        assertTrue(placed.canPanX)
        assertFalse(placed.canPanY)
        assertFalse(placed.atStart)
    }

    @Test
    fun theStartViewFollowsTheSetting() {
        assertEquals(MirrorPreset.FIT, MirrorFit.FIT.preset())
        assertEquals(MirrorPreset.FILL, MirrorFit.FILL.preset())
        assertEquals(1f, startView(MirrorPreset.FIT, portrait, cover).zoom, 0f)
        assertEquals(Offset(0.5f, 0f), startView(MirrorPreset.FIT, portrait, cover).center)
        assertEquals(Offset(0.5f, 0.5f), startView(MirrorPreset.FIT, landscape, cover).center)
    }

    @Test
    fun aDragMovesTheScreenWithTheFinger() {
        val start = fill(portrait)
        // A finger moving up 500 px brings in what was below, exactly as far.
        assertEquals(IntOffset(0, -500), layout(portrait, start.panned(portrait, cover, 0f, -500f)).offset)
        // In small steps it ends up at the same place.
        var stepped = start
        repeat(50) { stepped = stepped.panned(portrait, cover, 0f, -10f) }
        assertEquals(IntOffset(0, -500), layout(portrait, stepped).offset)
    }

    @Test
    fun aDragStopsAtTheEdgesWithoutStoringTheExcess() {
        val bottom = fill(portrait).panned(portrait, cover, 0f, -5_000f)
        assertEquals(IntOffset(0, 720 - 1828), layout(portrait, bottom).offset)
        // Back up at once: dragging past the edge left nothing to undo first.
        assertEquals(IntOffset(0, 720 - 1828 + 100), layout(portrait, bottom.panned(portrait, cover, 0f, 100f)).offset)
        val top = bottom.panned(portrait, cover, 0f, 5_000f)
        assertEquals(IntOffset.Zero, layout(portrait, top).offset)
    }

    @Test
    fun anAxisWithNothingHiddenDoesNotMove() {
        val start = fill(portrait)
        val sideways = start.panned(portrait, cover, 300f, 0f)
        assertEquals(start.center.x, sideways.center.x, 0f)
        assertEquals(IntOffset.Zero, layout(portrait, sideways).offset)
        // The whole screen does not move at all.
        assertEquals(MirrorView(), MirrorView().panned(portrait, cover, 120f, -80f))
    }

    @Test
    fun aPinchKeepsThePointUnderTheFingers() {
        val start = fill(portrait).panned(portrait, cover, 0f, -400f)
        val before = layout(portrait, start)
        val focus = Offset(200f, 300f)
        val pointX = (focus.x - before.offset.x) / before.frame.width
        val pointY = (focus.y - before.offset.y) / before.frame.height
        val zoomed = start.zoomed(portrait, cover, 2f, focus)
        assertEquals(start.zoom * 2f, zoomed.zoom, 0.0001f)
        val after = layout(portrait, zoomed)
        assertEquals(focus.x, after.offset.x + pointX * after.frame.width, 1.5f)
        assertEquals(focus.y, after.offset.y + pointY * after.frame.height, 1.5f)
    }

    @Test
    fun aPinchInManySmallStepsLandsWhereOneBigStepDoes() {
        val start = fill(portrait).panned(portrait, cover, 0f, -300f)
        val focus = Offset(520f, 610f)
        var stepped = start
        repeat(60) { stepped = stepped.zoomed(portrait, cover, 1.01f, focus) }
        val once = start.zoomed(portrait, cover, 1.01f.pow60(), focus)
        val a = layout(portrait, stepped)
        val b = layout(portrait, once)
        assertEquals(b.frame, a.frame)
        assertTrue(kotlin.math.abs(a.offset.x - b.offset.x) <= 1 && kotlin.math.abs(a.offset.y - b.offset.y) <= 1)
    }

    @Test
    fun zoomStaysBetweenTheWholeScreenAndTheClosestView() {
        val start = fill(portrait)
        assertEquals(1f, start.zoomed(portrait, cover, 0.01f, Offset(374f, 360f)).zoom, 0f)
        assertEquals(maxZoom(portrait, cover), start.zoomed(portrait, cover, 100f, Offset(374f, 360f)).zoom, 0.0001f)
        // Zooming out of the whole screen changes nothing.
        assertEquals(MirrorView(), MirrorView().zoomed(portrait, cover, 0.5f, Offset(10f, 10f)))
    }

    @Test
    fun pinchingAllTheWayOutShowsTheWholeScreenAgain() {
        val zoomedOut = fill(portrait).panned(portrait, cover, 0f, -700f).zoomed(portrait, cover, 0.1f, Offset(600f, 100f))
        val placed = layout(portrait, zoomedOut)
        assertEquals(layout(portrait, MirrorView()).frame, placed.frame)
        assertEquals(layout(portrait, MirrorView()).offset, placed.offset)
    }

    @Test
    fun aPinchInABlackBarStillZooms() {
        val zoomed = MirrorView(center = Offset(0.5f, 0f)).zoomed(portrait, cover, 2f, Offset(20f, 700f))
        assertEquals(2f, zoomed.zoom, 0.0001f)
        assertTrue(zoomed.center.x in 0f..1f && zoomed.center.y in 0f..1f)
    }

    @Test
    fun theViewportNeverShowsBeyondTheScreen() {
        val oddCover = IntSize(747, 721)
        for (content in listOf(IntSize(1081, 2641), IntSize(2641, 1081), IntSize(999, 1001), IntSize(3, 7), portrait)) {
            val most = maxZoom(content, oddCover)
            for (zoom in listOf(1f, 1.3f, fillZoom(content, oddCover), 3.7f, most)) {
                for (center in listOf(Offset(-1f, -1f), Offset(0f, 0f), Offset(0.3f, 0.8f), Offset(1f, 1f), Offset(2f, 5f))) {
                    val placed = mirrorLayout(content, oddCover, MirrorView(zoom, center))
                    if (placed.canPanX) {
                        assertTrue(placed.offset.x <= 0 && placed.offset.x + placed.frame.width >= oddCover.width)
                    } else {
                        assertTrue(placed.offset.x >= 0 && placed.offset.x + placed.frame.width <= oddCover.width)
                    }
                    if (placed.canPanY) {
                        assertTrue(placed.offset.y <= 0 && placed.offset.y + placed.frame.height >= oddCover.height)
                    } else {
                        assertTrue(placed.offset.y >= 0 && placed.offset.y + placed.frame.height <= oddCover.height)
                    }
                    if (zoom >= fillZoom(content, oddCover)) {
                        // Filled or closer: the cover is covered edge to edge, no hairline from rounding.
                        assertTrue(placed.frame.width >= oddCover.width && placed.frame.height >= oddCover.height)
                    }
                }
            }
        }
    }

    @Test
    fun theBufferIsTheFilledSizeUpToFillAndCappedBeyond() {
        val filled = IntSize(748, 1828)
        assertEquals(filled, layout(portrait, MirrorView()).buffer)
        assertEquals(filled, layout(portrait, fill(portrait)).buffer)
        // At the closest view the frame is 1870 x 4570, but the buffer stops at 2048 px.
        assertEquals(IntSize(838, 2048), layout(portrait, MirrorView(maxZoom(portrait, cover))).buffer)
        assertEquals(IntSize(2048, 838), layout(landscape, MirrorView(maxZoom(landscape, cover))).buffer)
        var zoom = 1f
        while (zoom <= maxZoom(portrait, cover)) {
            val buffer = layout(portrait, MirrorView(zoom)).buffer
            assertTrue(max(buffer.width, buffer.height) <= MAX_BUFFER_PX)
            assertTrue(buffer.width >= filled.width && buffer.height >= filled.height)
            zoom += 0.25f
        }
    }

    @Test
    fun theBufferNeverExceedsTheScreenItself() {
        // Smaller than 2048 px: zoomed far in, the buffer stops at the screen's own pixels.
        val small = IntSize(900, 1600)
        assertEquals(small, layout(small, MirrorView(maxZoom(small, cover))).buffer)
    }

    @Test
    fun presetsAreFillRelative() {
        val fillZoom = fillZoom(portrait, cover)
        assertEquals(748f / 1080f / (720f / 2640f), fillZoom, 0.001f)
        assertEquals(1f, presetZoom(MirrorPreset.FIT, portrait, cover), 0f)
        assertEquals(fillZoom, presetZoom(MirrorPreset.FILL, portrait, cover), 0f)
        assertEquals(fillZoom * 2f, presetZoom(MirrorPreset.CLOSE, portrait, cover), 0.0001f)
        assertEquals(fillZoom * 2.5f, maxZoom(portrait, cover), 0.0001f)
        assertEquals(MirrorPreset.FIT, presetAt(1.005f, portrait, cover))
        assertEquals(MirrorPreset.FILL, presetAt(fillZoom * 1.01f, portrait, cover))
        assertEquals(MirrorPreset.CLOSE, presetAt(fillZoom * 1.99f, portrait, cover))
        assertNull(presetAt(1.7f, portrait, cover))
    }

    @Test
    fun zoomRoomShowsAtTheEnds() {
        val whole = layout(portrait, MirrorView())
        assertTrue(whole.canZoomIn)
        assertFalse(whole.canZoomOut)
        val closest = layout(portrait, MirrorView(maxZoom(portrait, cover)))
        assertFalse(closest.canZoomIn)
        assertTrue(closest.canZoomOut)
    }

    @Test
    fun aRotationKeepsThePresetAndTheSpot() {
        val filled = MirrorView(fillZoom(portrait, cover), Offset(0.5f, 0.7f))
        val turned = filled.rescaled(portrait, cover, landscape, cover)
        assertEquals(fillZoom(landscape, cover), turned.zoom, 0.0001f)
        assertEquals(filled.center, turned.center)
        // Turned back, it is the same view again.
        assertEquals(filled.zoom, turned.rescaled(landscape, cover, portrait, cover).zoom, 0.0001f)
        // Any other zoom stays as it is, within range.
        assertEquals(1.7f, MirrorView(1.7f).rescaled(portrait, cover, landscape, cover).zoom, 0f)
        assertEquals(
            maxZoom(IntSize(1496, 1440), cover),
            MirrorView(6f).rescaled(portrait, cover, IntSize(1496, 1440), cover).zoom,
            0.0001f,
        )
    }

    @Test
    fun aCoverShapedScreenIsFilledAsAWhole() {
        val square = IntSize(1496, 1440)
        assertEquals(1f, fillZoom(square, cover), 0.001f)
        val placed = layout(square, startView(MirrorPreset.FILL, square, cover))
        assertEquals(cover, placed.frame)
        assertEquals(IntOffset.Zero, placed.offset)
        assertFalse(placed.canPanX || placed.canPanY)
        assertEquals(2f, presetZoom(MirrorPreset.CLOSE, square, cover), 0.001f)
    }

    @Test
    fun anUnknownScreenSizeCountsAsTheCover() {
        val placed = mirrorLayout(IntSize.Zero, cover, MirrorView(3f))
        assertEquals(mirrorLayout(cover, cover, MirrorView(3f)), placed)
        val whole = mirrorLayout(IntSize.Zero, cover, MirrorView())
        assertEquals(cover, whole.frame)
        assertEquals(cover, whole.buffer)
        assertEquals(IntOffset.Zero, whole.offset)
    }

    private fun Float.pow60(): Float {
        var result = 1f
        repeat(60) { result *= this }
        return result
    }
}
