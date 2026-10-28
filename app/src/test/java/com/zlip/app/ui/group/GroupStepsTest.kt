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
    fun addedStepsPreserveAllDistinctItems() {
        val merged = withAddedSteps(ids(1..28), ids(29..40))
        assertEquals(ids(1..40), merged)
    }

    @Test
    fun roomLeftNeverGoesBelowZero() {
        assertEquals(max, roomLeft(0))
        assertEquals(2, roomLeft(max - 2))
        assertEquals(0, roomLeft(max))
    }

    @Test
    fun aGroupIsSavedWithTwoUpToTheLimit() {
        assertFalse(canSaveSteps(0))
        assertFalse(canSaveSteps(1))
        assertTrue(canSaveSteps(2))
        assertTrue(canSaveSteps(100))
        assertTrue(canSaveSteps(max))
    }

    @Test
    fun anUnnamedGroupIsNamedAfterItsFirstStepWithoutACountThatGoesStale() {
        assertEquals("Tatil", defaultGroupName(listOf("Tatil") + List(7) { "IMG_$it" }, "Grup"))
    }

    @Test
    fun aGroupWithoutStepsIsCalledByItsType() {
        assertEquals("Grup", defaultGroupName(emptyList(), "Grup"))
    }

    @Test
    fun aBlankFirstNameIsSkipped() {
        assertEquals("Deniz", defaultGroupName(listOf(" ", " Deniz ", "Kum"), "Grup"))
        assertEquals("Grup", defaultGroupName(listOf("", " "), "Grup"))
    }

    @Test
    fun aCameraFileNameIsNoNameForAGroup() {
        listOf("20261027_143022", "IMG_20261027_143022", "PXL_20261027_143022123.MP", "IMG_1234", "DSC01234", "VID-20261027-WA0001", "Screenshot_20261027_143022_Gallery")
            .forEach { assertEquals(it, "Grup", defaultGroupName(listOf(it, "Deniz"), "Grup")) }
        listOf("Tatil 2026", "Deniz", "Doğum günü", "Ali 2", "K2 zirve")
            .forEach { assertEquals(it, it, defaultGroupName(listOf(it), "Grup")) }
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
    fun anUndoneRemovalNeverAddsAStepTwice() {
        assertEquals(listOf("a", "b"), withStepRestored(listOf("a", "b"), "b", 0))
    }

    @Test
    fun thePreviewShowsThePickedStepWhereverItMoved() {
        assertEquals(2, shownStepIndex(listOf("a", "b", "c"), "c"))
        // Dragged to the top: the same step, now row one.
        assertEquals(0, shownStepIndex(listOf("c", "a", "b"), "c"))
    }

    @Test
    fun thePreviewFallsBackToTheFirstStepWhenNoneIsPickedOrItWasRemoved() {
        assertEquals(0, shownStepIndex(listOf("a", "b"), null))
        assertEquals(0, shownStepIndex(listOf("a", "b"), "gone"))
        assertEquals(0, shownStepIndex(emptyList(), "a"))
    }

    @Test
    fun theArrowsGoToTheRowBesideAndRoundTheEnds() {
        assertEquals(1, stepBeside(0, 3, forward = true))
        assertEquals(0, stepBeside(2, 3, forward = true))
        assertEquals(2, stepBeside(0, 3, forward = false))
        assertEquals(0, stepBeside(1, 3, forward = false))
        assertEquals(-1, stepBeside(0, 0, forward = true))
    }
}
