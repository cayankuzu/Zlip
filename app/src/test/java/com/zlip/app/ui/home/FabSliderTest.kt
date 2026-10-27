package com.zlip.app.ui.home

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FabSliderTest {

    /** A 56 px round button at the bottom end of a 1080 x 2400 window. */
    private val fab = Rect(left = 980f, top = 2200f, right = 1036f, bottom = 2256f)

    @Test
    fun theSliderUnderTheButtonHidesIt() {
        val slider = Rect(left = 60f, top = 2190f, right = 1020f, bottom = 2238f)
        assertTrue(fabCoversSlider(fab, slider, margin = 0f))
    }

    @Test
    fun aSliderJustAboveCountsWithinTheMargin() {
        val slider = Rect(left = 60f, top = 2150f, right = 1020f, bottom = 2195f)
        assertFalse(fabCoversSlider(fab, slider, margin = 0f))
        assertTrue(fabCoversSlider(fab, slider, margin = 8f))
    }

    @Test
    fun aSliderHigherUpLeavesTheButtonAlone() {
        val slider = Rect(left = 60f, top = 1200f, right = 1020f, bottom = 1248f)
        assertFalse(fabCoversSlider(fab, slider, margin = 21f))
    }

    @Test
    fun aSliderScrolledOutOfViewNeverCounts() {
        // Clipped away entirely: an empty rectangle, even one that happens to lie inside the button.
        assertFalse(fabCoversSlider(fab, Rect.Zero, margin = 21f))
        assertFalse(fabCoversSlider(fab, Rect(1000f, 2220f, 1000f, 2220f), margin = 21f))
    }

    @Test
    fun nothingMeasuredYetShowsTheButton() {
        val slider = Rect(left = 60f, top = 2190f, right = 1020f, bottom = 2238f)
        assertFalse(fabCoversSlider(null, slider, margin = 21f))
        assertFalse(fabCoversSlider(fab, null, margin = 21f))
    }
}
