package com.zlip.app.ui.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StandbyHintTest {

    @Test
    fun aCentredSpotLeavesTheWholeWidthBarTheMargins() {
        assertEquals(411.dp - 24.dp, hintWidth(411.dp, 0f))
    }

    @Test
    fun anOffCentreHintStaysInsideBothEdges() {
        val screen = 411.dp
        for (fraction in listOf(-0.2f, 0.18f, -0.15f, 0.2f)) {
            val width = hintWidth(screen, fraction)
            // Centred on the spot, so half of it reaches either way from there.
            val centre = screen / 2 + screen * fraction
            assertTrue("right edge at $fraction", centre + width / 2 <= screen - 12.dp + 0.01.dp)
            assertTrue("left edge at $fraction", centre - width / 2 >= 12.dp - 0.01.dp)
        }
    }

    @Test
    fun neverNegative() {
        assertEquals(0.dp, hintWidth(20.dp, 0.45f))
    }
}
