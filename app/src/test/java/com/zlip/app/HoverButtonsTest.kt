package com.zlip.app

import com.zlip.app.data.CoverItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HoverButtonsTest {

    private val trio = listOf(HoverButton.PREVIOUS, HoverButton.PLAY_PAUSE, HoverButton.NEXT)

    @Test
    fun theTrioHoldsTheFirstThreeSlotsWhateverShows() {
        for (media in listOf(false, true)) {
            for (group in listOf(false, true)) {
                assertEquals(trio, hoverButtons(coverOn = true, mirroring = false, media = media, group = group, max = 3))
            }
        }
    }

    @Test
    fun roomForMoreAddsNextItemThenFitThenTurningOff() {
        assertEquals(
            trio + listOf(HoverButton.NEXT_ITEM, HoverButton.MEDIA_FIT),
            hoverButtons(coverOn = true, mirroring = false, media = true, group = true, max = 5),
        )
        assertEquals(
            trio + listOf(HoverButton.NEXT_ITEM, HoverButton.MEDIA_FIT, HoverButton.COVER_OFF),
            hoverButtons(coverOn = true, mirroring = false, media = true, group = true, max = 8),
        )
        assertEquals(
            trio + HoverButton.COVER_OFF,
            hoverButtons(coverOn = true, mirroring = false, media = false, group = false, max = 5),
        )
    }

    @Test
    fun mirroringOffersItsViewStopAndTurningTheCoverOff() {
        // A photo under the mirror is out of sight: no Fit / Fill for it.
        assertEquals(
            listOf(HoverButton.MIRROR_VIEW, HoverButton.MIRROR_STOP, HoverButton.COVER_OFF),
            hoverButtons(coverOn = true, mirroring = true, media = true, group = true, max = 3),
        )
    }

    @Test
    fun aCoverThatIsOffOffersToTurnItOn() {
        assertEquals(listOf(HoverButton.COVER_ON), hoverButtons(coverOn = false, mirroring = false, media = true, group = false, max = 3))
        // The mirror's state does not matter once the cover is off.
        assertEquals(listOf(HoverButton.COVER_ON), hoverButtons(coverOn = false, mirroring = true, media = false, group = true, max = 3))
    }

    @Test
    fun lessRoomKeepsWhatMattersMost() {
        assertEquals(
            listOf(HoverButton.PREVIOUS, HoverButton.PLAY_PAUSE),
            hoverButtons(coverOn = true, mirroring = false, media = true, group = false, max = 2),
        )
        assertEquals(listOf(HoverButton.MIRROR_VIEW), hoverButtons(coverOn = true, mirroring = true, media = false, group = false, max = 1))
    }

    @Test
    fun noRoomShowsNothing() {
        assertEquals(emptyList<HoverButton>(), hoverButtons(coverOn = true, mirroring = false, media = false, group = false, max = 0))
        assertEquals(emptyList<HoverButton>(), hoverButtons(coverOn = false, mirroring = false, media = false, group = false, max = -1))
    }

    @Test
    fun fitFillFollowsTheSelectedItemNotTheStep() {
        val photo = CoverItem.Media(id = "photo", name = "Photo", fileName = "p.webp")
        val note = CoverItem.Text(id = "note", name = "Note", text = "HI", scroll = false)
        assertTrue(hoverOffersFit(photo))
        assertFalse(hoverOffersFit(note))
        assertFalse(hoverOffersFit(null))
        // A group mixing photo and text steps offers it on every step, so stepping never moves the buttons.
        assertTrue(hoverOffersFit(CoverItem.Group(id = "mixed", name = "Mixed", items = listOf(note, photo))))
        assertFalse(hoverOffersFit(CoverItem.Group(id = "texts", name = "Texts", items = listOf(note, note.copy(id = "note2")))))
    }
}
