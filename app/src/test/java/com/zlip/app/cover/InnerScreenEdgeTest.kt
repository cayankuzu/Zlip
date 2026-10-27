package com.zlip.app.cover

import android.view.Display
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InnerScreenEdgeTest {

    @Test
    fun firstReadingNeverStartsOver() {
        assertFalse(InnerScreenEdge().onState(Display.STATE_ON))
        assertFalse(InnerScreenEdge().onState(Display.STATE_OFF))
    }

    @Test
    fun foldingStartsOver() {
        val edge = InnerScreenEdge()
        edge.onState(Display.STATE_ON)
        assertTrue(edge.onState(Display.STATE_OFF))
    }

    @Test
    fun openingStartsOver() {
        val edge = InnerScreenEdge()
        edge.onState(Display.STATE_DOZE)
        assertTrue(edge.onState(Display.STATE_ON))
    }

    @Test
    fun staysDarkWithoutStartingOver() {
        val edge = InnerScreenEdge()
        edge.onState(Display.STATE_OFF)
        assertFalse(edge.onState(Display.STATE_OFF))
        assertFalse(edge.onState(Display.STATE_DOZE))
        assertFalse(edge.onState(Display.STATE_DOZE_SUSPEND))
    }

    @Test
    fun staysLitWithoutStartingOver() {
        val edge = InnerScreenEdge()
        edge.onState(Display.STATE_ON)
        assertFalse(edge.onState(Display.STATE_ON))
        assertFalse(edge.onState(Display.STATE_VR))
        assertFalse(edge.onState(Display.STATE_ON_SUSPEND))
    }

    @Test
    fun unknownStateIsIgnored() {
        val edge = InnerScreenEdge()
        edge.onState(Display.STATE_ON)
        assertFalse(edge.onState(Display.STATE_UNKNOWN))
        // Still remembered as lit: going dark now is a fold.
        assertTrue(edge.onState(Display.STATE_OFF))
    }

    @Test
    fun everyCrossingCountsOnce() {
        val edge = InnerScreenEdge()
        edge.onState(Display.STATE_ON)
        assertTrue(edge.onState(Display.STATE_OFF))
        assertFalse(edge.onState(Display.STATE_OFF))
        assertTrue(edge.onState(Display.STATE_ON))
        assertFalse(edge.onState(Display.STATE_ON))
    }
}
