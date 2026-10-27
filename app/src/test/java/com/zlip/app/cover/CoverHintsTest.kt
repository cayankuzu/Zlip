package com.zlip.app.cover

import com.zlip.app.data.CoverItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverHintsTest {

    private val photo = CoverItem.Media(id = "photo", name = "Beach", fileName = "p.webp")
    private val note = CoverItem.Text(id = "note", name = "", text = "HI", scroll = false)
    private val group = CoverItem.Group(
        id = "group",
        name = "Trip",
        items = listOf(photo.copy(id = "a"), photo.copy(id = "b"), photo.copy(id = "c")),
    )
    private val playlist = listOf(photo, group, note)

    @Test
    fun aStepInsideTheGroupSaysTheStep() {
        assertEquals(
            MoveHint.Step(step = 2, count = 3),
            CoverHints.afterMove(CoverMove.STEP, group, playlist, GroupProgress("group", step = 2, count = 3)),
        )
    }

    @Test
    fun aMoveToAnotherItemSaysItsNameAndPlace() {
        assertEquals(MoveHint.Item(name = "Beach", position = 1, count = 3), CoverHints.afterMove(CoverMove.ITEM, photo, playlist, null))
        // Entering a group by an item move names the group, not its first step.
        assertEquals(
            MoveHint.Item(name = "Trip", position = 2, count = 3),
            CoverHints.afterMove(CoverMove.ITEM, group, playlist, GroupProgress("group", step = 1, count = 3)),
        )
    }

    @Test
    fun aStepWhoseGroupIsNoLongerSelectedSaysTheItem() {
        // Stepped past the group's last step onto the next item: the old group's progress no longer applies.
        assertEquals(
            MoveHint.Item(name = "Beach", position = 1, count = 3),
            CoverHints.afterMove(CoverMove.STEP, photo, playlist, GroupProgress("group", step = 3, count = 3)),
        )
    }

    @Test
    fun aNamelessItemStillSaysItsPlace() {
        assertEquals(MoveHint.Item(name = null, position = 3, count = 3), CoverHints.afterMove(CoverMove.ITEM, note, playlist, null))
    }

    @Test
    fun nothingToSayGivesNoHint() {
        // Alone and without a name: no place to give either.
        assertNull(CoverHints.afterMove(CoverMove.ITEM, note, listOf(note), null))
        assertNull(CoverHints.afterMove(CoverMove.ITEM, null, playlist, null))
        // Alone with a name: the name, without "1/1".
        assertEquals(MoveHint.Item(name = "Beach", position = null, count = 1), CoverHints.afterMove(CoverMove.ITEM, photo, listOf(photo), null))
    }

    @Test
    fun theLockedCoversHintShowsAtMostEveryThirtySeconds() {
        assertTrue(CoverHints.lockedHintDue(nowMs = 1_000, lastShownMs = null))
        assertFalse(CoverHints.lockedHintDue(nowMs = 20_000, lastShownMs = 1_000))
        assertTrue(CoverHints.lockedHintDue(nowMs = 31_000, lastShownMs = 1_000))
    }
}
