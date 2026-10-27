package com.zlip.app

import com.zlip.app.data.CoverItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HoverButtonsTest {

    @Test
    fun playPauseAndNextAlwaysHoldTheFirstTwoSlots() {
        for (media in listOf(false, true)) {
            for (group in listOf(false, true)) {
                val shown = hoverButtons(coverOn = true, mirroring = false, media = media, group = group, canMove = true, max = 3)
                assertEquals(listOf(HoverButton.PLAY_PAUSE, HoverButton.NEXT), shown.take(2))
            }
        }
    }

    @Test
    fun aPhotoOrVideoKeepsFitOrFillInTheThirdSlot() {
        assertEquals(
            listOf(HoverButton.PLAY_PAUSE, HoverButton.NEXT, HoverButton.MEDIA_FIT),
            hoverButtons(coverOn = true, mirroring = false, media = true, group = false, canMove = true, max = 3),
        )
        // A group with a photo or video among its steps too: stepping never moves the buttons.
        assertEquals(
            listOf(HoverButton.PLAY_PAUSE, HoverButton.NEXT, HoverButton.MEDIA_FIT),
            hoverButtons(coverOn = true, mirroring = false, media = true, group = true, canMove = true, max = 3),
        )
    }

    @Test
    fun anythingElseOffersPreviousInTheThirdSlot() {
        assertEquals(
            listOf(HoverButton.PLAY_PAUSE, HoverButton.NEXT, HoverButton.PREVIOUS),
            hoverButtons(coverOn = true, mirroring = false, media = false, group = true, canMove = true, max = 3),
        )
    }

    @Test
    fun roomForMoreAddsPreviousThenNextItemThenTurningOff() {
        assertEquals(
            listOf(HoverButton.PLAY_PAUSE, HoverButton.NEXT, HoverButton.MEDIA_FIT, HoverButton.PREVIOUS, HoverButton.NEXT_ITEM, HoverButton.COVER_OFF),
            hoverButtons(coverOn = true, mirroring = false, media = true, group = true, canMove = true, max = 8),
        )
        assertEquals(
            listOf(HoverButton.PLAY_PAUSE, HoverButton.NEXT, HoverButton.PREVIOUS, HoverButton.COVER_OFF),
            hoverButtons(coverOn = true, mirroring = false, media = false, group = false, canMove = true, max = 5),
        )
    }

    @Test
    fun aSingleItemAloneSpendsNoSlotOnMovesThatGoNowhere() {
        // Fit / Fill stays in reach of a lone photo or video, and the cover can be turned off with the window kept.
        assertEquals(
            listOf(HoverButton.PLAY_PAUSE, HoverButton.MEDIA_FIT, HoverButton.COVER_OFF),
            hoverButtons(coverOn = true, mirroring = false, media = true, group = false, canMove = false, max = 3),
        )
        assertEquals(
            listOf(HoverButton.PLAY_PAUSE, HoverButton.COVER_OFF),
            hoverButtons(coverOn = true, mirroring = false, media = false, group = false, canMove = false, max = 3),
        )
    }

    @Test
    fun mirroringOffersItsViewStopAndTurningTheCoverOff() {
        // A photo under the mirror is out of sight: no Fit / Fill for it.
        assertEquals(
            listOf(HoverButton.MIRROR_VIEW, HoverButton.MIRROR_STOP, HoverButton.COVER_OFF),
            hoverButtons(coverOn = true, mirroring = true, media = true, group = true, canMove = true, max = 3),
        )
    }

    @Test
    fun aCoverThatIsOffOffersToTurnItOn() {
        assertEquals(listOf(HoverButton.COVER_ON), hoverButtons(coverOn = false, mirroring = false, media = true, group = false, canMove = true, max = 3))
        // The mirror's state does not matter once the cover is off.
        assertEquals(listOf(HoverButton.COVER_ON), hoverButtons(coverOn = false, mirroring = true, media = false, group = true, canMove = true, max = 3))
    }

    @Test
    fun lessRoomKeepsWhatMattersMost() {
        assertEquals(
            listOf(HoverButton.PLAY_PAUSE, HoverButton.NEXT),
            hoverButtons(coverOn = true, mirroring = false, media = true, group = false, canMove = true, max = 2),
        )
        assertEquals(listOf(HoverButton.MIRROR_VIEW), hoverButtons(coverOn = true, mirroring = true, media = false, group = false, canMove = true, max = 1))
    }

    @Test
    fun noRoomShowsNothing() {
        assertEquals(emptyList<HoverButton>(), hoverButtons(coverOn = true, mirroring = false, media = false, group = false, canMove = true, max = 0))
        assertEquals(emptyList<HoverButton>(), hoverButtons(coverOn = false, mirroring = false, media = false, group = false, canMove = true, max = -1))
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
