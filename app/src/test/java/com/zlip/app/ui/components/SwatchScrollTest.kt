package com.zlip.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class SwatchScrollTest {

    @Test
    fun theFirstTwoColoursNeedNoScroll() {
        assertEquals(0, swatchScrollTarget(0, stride = 52))
        assertEquals(0, swatchScrollTarget(1, stride = 52))
    }

    @Test
    fun aLaterColourStartsInViewWithOneSwatchBeforeIt() {
        // Blue is the 12th colour: scrolled so the 11th is the first one fully shown.
        assertEquals(10 * 52, swatchScrollTarget(11, stride = 52))
    }

    @Test
    fun aColourNotInThePaletteLeavesTheRowAtItsStart() {
        assertEquals(0, swatchScrollTarget(-1, stride = 52))
    }
}
