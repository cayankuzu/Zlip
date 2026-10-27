package com.zlip.app.ui.group

import com.zlip.app.data.CoverItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupStepsTest {

    private val max = CoverItem.Group.MAX_ITEMS

    private fun ids(range: IntRange) = range.map { "id$it" }

    @Test
    fun addedStepsGoAfterTheOnesThereInPickedOrder() {
        assertEquals(listOf("a", "b", "c", "d"), withAddedSteps(listOf("a", "b"), listOf("c", "d")))
    }

    @Test
    fun aStepAlreadyInTheGroupIsNotAddedTwice() {
        assertEquals(listOf("a", "b", "c"), withAddedSteps(listOf("a", "b"), listOf("b", "c", "c")))
    }

    @Test
    fun addedStepsStopAtWhatAGroupHolds() {
        val merged = withAddedSteps(ids(1..28), ids(29..40))
        assertEquals(ids(1..max), merged)
    }

    @Test
    fun roomLeftNeverGoesBelowZero() {
        assertEquals(max, roomLeft(0))
        assertEquals(2, roomLeft(max - 2))
        assertEquals(0, roomLeft(max))
        assertEquals(0, roomLeft(max + 5))
    }

    @Test
    fun aGroupIsSavedWithTwoUpToTheLimit() {
        assertFalse(canSaveSteps(0))
        assertFalse(canSaveSteps(1))
        assertTrue(canSaveSteps(2))
        assertTrue(canSaveSteps(max))
        // More would be cut without a word when the library is read again.
        assertFalse(canSaveSteps(max + 1))
    }

    private val format = { first: String, count: Int -> "$first · $count" }

    @Test
    fun anUnnamedGroupIsNamedAfterItsFirstStepAndItsSize() {
        assertEquals("Tatil · 8", defaultGroupName(listOf("Tatil") + List(7) { "IMG_$it" }, "Grup", format))
    }

    @Test
    fun aGroupWithoutStepsIsCalledByItsType() {
        assertEquals("Grup", defaultGroupName(emptyList(), "Grup", format))
    }

    @Test
    fun aBlankFirstNameIsSkippedButStillCounted() {
        assertEquals("Deniz · 3", defaultGroupName(listOf(" ", " Deniz ", "Kum"), "Grup", format))
        assertEquals("Grup", defaultGroupName(listOf("", " "), "Grup", format))
    }

    @Test
    fun anUndoneRemovalPutsTheStepBackWhereItWas() {
        assertEquals(listOf("a", "b", "c"), withStepRestored(listOf("a", "c"), "b", 1))
        assertEquals(listOf("b", "a", "c"), withStepRestored(listOf("a", "c"), "b", 0))
    }

    @Test
    fun anUndoneRemovalGoesLastWhenTheGroupGotShorterMeanwhile() {
        assertEquals(listOf("a", "z"), withStepRestored(listOf("a"), "z", 5))
    }

    @Test
    fun anUndoneRemovalNeverAddsAStepTwiceOrPastTheLimit() {
        assertEquals(listOf("a", "b"), withStepRestored(listOf("a", "b"), "b", 0))
        assertEquals(ids(1..max), withStepRestored(ids(1..max), "extra", 0))
    }
}
