package com.zlip.app

import org.junit.Assert.assertEquals
import org.junit.Test

class HoverButtonsTest {

    @Test
    fun aPhotoOrVideoOffersFitOrFillThenNext() {
        assertEquals(
            listOf(HoverButton.PLAY_PAUSE, HoverButton.MEDIA_FIT, HoverButton.NEXT),
            hoverButtons(coverOn = true, mirroring = false, media = true, max = 3),
        )
    }

    @Test
    fun anythingElseOffersNextThenTurningTheCoverOff() {
        assertEquals(
            listOf(HoverButton.PLAY_PAUSE, HoverButton.NEXT, HoverButton.COVER_OFF),
            hoverButtons(coverOn = true, mirroring = false, media = false, max = 3),
        )
    }

    @Test
    fun mirroringOffersItsViewStopAndTurningTheCoverOff() {
        // A photo under the mirror is out of sight: no Fit / Fill for it.
        assertEquals(
            listOf(HoverButton.MIRROR_VIEW, HoverButton.MIRROR_STOP, HoverButton.COVER_OFF),
            hoverButtons(coverOn = true, mirroring = true, media = true, max = 3),
        )
    }

    @Test
    fun aCoverThatIsOffOffersToTurnItOn() {
        assertEquals(listOf(HoverButton.COVER_ON), hoverButtons(coverOn = false, mirroring = false, media = true, max = 3))
        // The mirror's state does not matter once the cover is off.
        assertEquals(listOf(HoverButton.COVER_ON), hoverButtons(coverOn = false, mirroring = true, media = false, max = 3))
    }

    @Test
    fun roomForMoreShowsTheRestInOrder() {
        assertEquals(
            listOf(HoverButton.PLAY_PAUSE, HoverButton.MEDIA_FIT, HoverButton.NEXT, HoverButton.COVER_OFF),
            hoverButtons(coverOn = true, mirroring = false, media = true, max = 5),
        )
    }

    @Test
    fun lessRoomKeepsWhatMattersMost() {
        assertEquals(listOf(HoverButton.PLAY_PAUSE, HoverButton.MEDIA_FIT), hoverButtons(coverOn = true, mirroring = false, media = true, max = 2))
        assertEquals(listOf(HoverButton.MIRROR_VIEW), hoverButtons(coverOn = true, mirroring = true, media = false, max = 1))
    }

    @Test
    fun noRoomShowsNothing() {
        assertEquals(emptyList<HoverButton>(), hoverButtons(coverOn = true, mirroring = false, media = false, max = 0))
        assertEquals(emptyList<HoverButton>(), hoverButtons(coverOn = false, mirroring = false, media = false, max = -1))
    }
}
