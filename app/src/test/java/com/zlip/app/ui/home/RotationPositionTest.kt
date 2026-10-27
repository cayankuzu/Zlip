package com.zlip.app.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RotationPositionTest {

    @Test
    fun theItemOnTheCoverIsCountedFromOne() {
        assertEquals(2 to 3, rotationPosition(listOf("a", "b", "c"), "b"))
        assertEquals(1 to 3, rotationPosition(listOf("a", "b", "c"), "a"))
    }

    @Test
    fun anItemOutsideTheRotationHasNoPlace() {
        // A group's step picked by hand: it is in the library, not in what the cover rotates.
        assertNull(rotationPosition(listOf("a", "g"), "step"))
        assertNull(rotationPosition(listOf("a", "g"), null))
    }

    @Test
    fun aRotationOfOneSaysNothing() {
        assertNull(rotationPosition(listOf("g"), "g"))
    }
}
