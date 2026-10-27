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
    fun aNewFramingReachesTheItemAndEveryCopy() {
        val framing = MediaFraming(MediaFit.FILL, zoom = 2f, focusX = 0.5f, focusY = -1f)
        val framed = photo.framed(framing)
        assertEquals(framing, framed.framing)
        assertEquals(framed, photo.withMediaFraming("photo", framing))
        assertEquals(listOf(listOf(note), listOf(framed)), (collage.withMediaFraming("photo", framing) as CoverItem.Collage).cells)
        val updated = group.withMediaFraming("photo", framing) as CoverItem.Group
        assertEquals(framed, updated.items[2])
        assertEquals(listOf(framed), (updated.items[1] as CoverItem.Collage).cells[1])
        assertSame(note, updated.items[0])
    }

    @Test
    fun aFramingIsKeptInRange() {
        val framed = photo.framed(MediaFraming(MediaFit.FIT, zoom = 50f, focusX = Float.NaN, focusY = -9f))
        assertEquals(MediaFraming(MediaFit.FIT, zoom = MediaFocus.MAX_ZOOM, focusX = 0f, focusY = -1f), framed.framing)
    }

    @Test
    fun aFramingOfAnotherIdOrTheSameFramingChangesNothing() {
        val framing = MediaFraming(MediaFit.FILL, zoom = 3f)
        assertSame(group, group.withMediaFraming("elsewhere", framing))
        assertSame(note, note.withMediaFraming("note", framing))
        assertSame(group, group.withMediaFraming("photo", photo.framing))
    }

    @Test
    fun aDeletedItemsCopiesAreFramedWithoutBringingItBack() {
        // The library as ContentRepository.setMediaFraming maps it: the photo itself is gone, a group still holds it.
        val library = listOf<CoverItem>(note, group)
        val framing = MediaFraming(MediaFit.FILL, zoom = 2f)
        val updated = library.map { it.withMediaFraming("photo", framing) }
        assertEquals(listOf("note", "group"), updated.map { it.id })
        assertEquals(photo.framed(framing), (updated[1] as CoverItem.Group).items[2])
    }

    @Test
    fun otherItemsStayAsTheyAre() {
        assertSame(note, note.replacingCopies(note.copy(text = "x")))
        assertSame(collage, collage.replacingCopies(group.copy(name = "B")))
        assertSame(group, group.replacingCopies(group.copy(name = "B")))
    }
}
