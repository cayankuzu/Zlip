package com.zlip.app.cover

import com.zlip.app.data.CoverItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverNavTest {

    private val photo = CoverItem.Media(id = "photo", name = "Photo", fileName = "p.webp")
    private val note = CoverItem.Text(id = "note", name = "Note", text = "HI", scroll = false)
    private val group = CoverItem.Group(id = "group", name = "Group", items = listOf(photo.copy(id = "a"), photo.copy(id = "b")))

    @Test
    fun manualMovesNeverShuffle() {
        assertFalse(CoverNav.MANUAL_SHUFFLE)
    }

    @Test
    fun anItemMoveSkipsTheRestOfTheGroup() {
        var stepped = 0
        // Step 2 of 5: an item move must not visit steps 3 to 5.
        assertFalse(CoverNav.staysInGroup(byStep = false) { stepped++; true })
        assertEquals(0, stepped)
    }

    @Test
    fun aStepMoveStaysWhileTheGroupHasAStepThatWay() {
        assertTrue(CoverNav.staysInGroup(byStep = true) { true })
        // At the group's end it goes on to the next item.
        assertFalse(CoverNav.staysInGroup(byStep = true) { false })
    }

    @Test
    fun progressIsTheShownGroupsPlayOrderPosition() {
        val shown = StepShown(groupId = "group", child = group.items[1], position = 1, count = 2)
        assertEquals(GroupProgress("group", step = 1, count = 2), CoverNav.progress(group, shown))
    }

    @Test
    fun noProgressOnceSomethingElseIsSelected() {
        val shown = StepShown(groupId = "group", child = group.items[0], position = 2, count = 2)
        assertNull(CoverNav.progress(photo, shown))
        assertNull(CoverNav.progress(group.copy(id = "other"), shown))
        assertNull(CoverNav.progress(group, null))
        assertNull(CoverNav.progress(null, shown))
    }

    @Test
    fun itemSkipNeedsAGroupAndSomewhereElseToGo() {
        assertTrue(CoverNav.itemSkipAvailable(group, listOf(photo, group)))
        // Alone the group goes round: nothing to skip to.
        assertFalse(CoverNav.itemSkipAvailable(group, listOf(group)))
        // A single item already moves item by item.
        assertFalse(CoverNav.itemSkipAvailable(photo, listOf(photo, note)))
        assertFalse(CoverNav.itemSkipAvailable(null, listOf(photo)))
        // Picked by hand outside the rotation: the rotation is somewhere to go.
        assertTrue(CoverNav.itemSkipAvailable(group, listOf(photo)))
    }
}
