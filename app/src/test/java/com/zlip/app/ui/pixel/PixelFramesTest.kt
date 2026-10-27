package com.zlip.app.ui.pixel

import com.zlip.app.data.CoverItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelFramesTest {

    private val red = 0xFFFF0000.toInt()
    private val blue = 0xFF0000FF.toInt()

    private fun sheet(vararg frames: List<Int>, width: Int = 2, height: Int = 2) = PixelFrames(width, height, frames.toList())

    @Test
    fun insertCopyDuplicatesSelectedFrameRightAfterIt() {
        val a = listOf(1, 0, 0, 0)
        val b = listOf(2, 0, 0, 0)
        val result = sheet(a, b).insertCopy(0)
        assertEquals(listOf(a, a, b), result.frames)
    }

    @Test
    fun insertCopyStopsAtTheFrameLimit() {
        val full = PixelFrames(1, 1, List(CoverItem.PixelArt.MAX_FRAMES) { listOf(it) })
        assertFalse(full.canAdd)
        assertSame(full, full.insertCopy(3))
    }

    @Test
    fun removeKeepsTheLastFrame() {
        val single = sheet(listOf(1, 2, 3, 4))
        assertSame(single, single.remove(0))
        val two = sheet(listOf(1, 0, 0, 0), listOf(2, 0, 0, 0))
        assertEquals(listOf(listOf(2, 0, 0, 0)), two.remove(0).frames)
    }

    @Test
    fun moveReordersFrames() {
        val a = listOf(1, 0, 0, 0)
        val b = listOf(2, 0, 0, 0)
        val c = listOf(3, 0, 0, 0)
        assertEquals(listOf(b, a, c), sheet(a, b, c).move(0, 1).frames)
        assertEquals(listOf(a, c, b), sheet(a, b, c).move(2, 1).frames)
        val unchanged = sheet(a, b)
        assertSame(unchanged, unchanged.move(1, 2))
    }

    @Test
    fun resizeKeepsEveryFrameCentred() {
        val grown = sheet(listOf(1, 2, 3, 4), listOf(5, 6, 7, 8)).resized(4, 4)
        assertEquals(4, grown.width)
        assertEquals(
            listOf(
                0, 0, 0, 0,
                0, 1, 2, 0,
                0, 3, 4, 0,
                0, 0, 0, 0,
            ),
            grown.frames[0],
        )
        assertEquals(6, grown.frames[1][6])
        // Shrinking back crops the margin away again.
        assertEquals(listOf(listOf(1, 2, 3, 4), listOf(5, 6, 7, 8)), grown.resized(2, 2).frames)
    }

    @Test
    fun packRoundTripsMostlyEmptyDrawings() {
        val original = PixelFrames(16, 16, listOf(List(256) { if (it == 40) red else 0 }, List(256) { if (it == 41) blue else 0 }))
        val packed = original.pack()
        // Runs make a mostly transparent drawing far smaller than one int per pixel.
        assertTrue(packed.size < 40)
        assertEquals(original, PixelFrames.unpack(packed))
    }

    @Test
    fun packRoundTripsNoisyDrawingsRaw() {
        val original = PixelFrames(3, 3, listOf(List(9) { it }, List(9) { -it }))
        val packed = original.pack()
        assertEquals(4 + 18, packed.size)
        assertEquals(original, PixelFrames.unpack(packed))
    }

    @Test
    fun unpackRejectsDamagedData() {
        val packed = PixelFrames(4, 4, listOf(List(16) { 0 })).pack()
        assertNull(PixelFrames.unpack(packed.copyOf(packed.size - 1)))
        assertNull(PixelFrames.unpack(intArrayOf()))
        assertNull(PixelFrames.unpack(intArrayOf(9, 4, 4, 1)))
        // Runs that add up to more pixels than the canvas has.
        assertNull(PixelFrames.unpack(intArrayOf(2, 2, 2, 1, 5, red)))
        // More frames than an item may have.
        assertNull(PixelFrames.unpack(intArrayOf(2, 1, 1, CoverItem.PixelArt.MAX_FRAMES + 1, CoverItem.PixelArt.MAX_FRAMES + 1, 0)))
    }

    @Test
    fun ofTakesFirstFrameThenExtraFrames() {
        val art = CoverItem.PixelArt("id", "n", 1, 2, listOf(1, 2), extraFrames = listOf(listOf(3, 4)))
        assertEquals(listOf(listOf(1, 2), listOf(3, 4)), PixelFrames.of(art).frames)
    }

    @Test
    fun onionSkinShowsPreviousFrameOnlyWhereCurrentIsEmpty() {
        val black = CoverItem.BLACK
        val result = onionSkin(current = listOf(red, 0, 0), previous = listOf(blue, blue, 0), background = black)
        assertEquals(red, result[0])
        assertEquals(blend(black, blue, 0.32f), result[1])
        assertEquals(0, result[2])
    }

    @Test
    fun blendMixesChannelsAndIsOpaque() {
        assertEquals(CoverItem.BLACK, blend(CoverItem.BLACK, CoverItem.WHITE, 0f))
        assertEquals(CoverItem.WHITE, blend(CoverItem.BLACK, CoverItem.WHITE, 1f))
        assertEquals(0xFF808080.toInt(), blend(CoverItem.BLACK, CoverItem.WHITE, 0.5f))
    }
}
