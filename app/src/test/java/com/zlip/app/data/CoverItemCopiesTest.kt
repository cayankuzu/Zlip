package com.zlip.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class CoverItemCopiesTest {

    private val note = CoverItem.Text(id = "note", name = "Note", text = "Toplantıdayım")
    private val photo = CoverItem.Media(id = "photo", name = "Beach", fileName = "a.webp")
    private val collage = CoverItem.Collage(id = "collage", name = "Mix", layout = CollageLayout.SIDE_BY_SIDE, cells = listOf(listOf(note), listOf(photo)))
    private val group = CoverItem.Group(id = "group", name = "A", items = listOf(note, collage, photo))

    @Test
    fun anEditReachesCopiesInCollagesAndGroups() {
        val fixed = note.copy(text = "Toplantıdayım!")
        assertEquals(listOf(listOf(fixed), listOf(photo)), (collage.replacingCopies(fixed) as CoverItem.Collage).cells)
        val updated = group.replacingCopies(fixed) as CoverItem.Group
        assertEquals(fixed, updated.items[0])
        // Also the copy inside the collage that is a step of the group.
        assertEquals(listOf(fixed), (updated.items[1] as CoverItem.Collage).cells[0])
        assertEquals(photo, updated.items[2])
    }

    @Test
    fun anEditedCollageReplacesItsCopyInAGroup() {
        val renamed = collage.copy(intervalSeconds = 30)
        assertEquals(renamed, (group.replacingCopies(renamed) as CoverItem.Group).items[1])
    }

    @Test
    fun otherItemsStayAsTheyAre() {
        assertSame(note, note.replacingCopies(note.copy(text = "x")))
        assertSame(collage, collage.replacingCopies(group.copy(name = "B")))
        assertSame(group, group.replacingCopies(group.copy(name = "B")))
    }
}
