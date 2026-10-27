package com.zlip.app

import org.junit.Assert.assertEquals
import org.junit.Test

class HoverButtonsTest {

    @Test
    fun aPhotoOrVideoOffersFitOrFillBeforeClose() {
        assertEquals(
            listOf(HoverButton.PLAY_PAUSE, HoverButton.MEDIA_FIT, HoverButton.CLOSE),
            hoverButtons(coverOn = true, mirroring = false, media = true, max = 3),
        )
    }

    @Test
    fun anythingElseOffersNextBeforeClose() {
        assertEquals(
            listOf(HoverButton.PLAY_PAUSE, HoverButton.NEXT, HoverButton.CLOSE),
            hoverButtons(coverOn = true, mirroring = false, media = false, max = 3),
        )
    }

    @Test
    fun mirroringOffersItsViewStopAndClose() {
        // A photo under the mirror is out of sight: no Fit / Fill for it.
        assertEquals(
            listOf(HoverButton.MIRROR_VIEW, HoverButton.MIRROR_STOP, HoverButton.CLOSE),
            hoverButtons(coverOn = true, mirroring = true, media = true, max = 3),
        )
    }

    @Test
    fun aCoverThatIsOffOffersToTurnItOnOrClose() {
        assertEquals(listOf(HoverButton.COVER_ON, HoverButton.CLOSE), hoverButtons(coverOn = false, mirroring = false, media = true, max = 3))
        // The mirror's state does not matter once the cover is off.
        assertEquals(listOf(HoverButton.COVER_ON, HoverButton.CLOSE), hoverButtons(coverOn = false, mirroring = true, media = false, max = 3))
    }

    @Test
    fun roomForMoreShowsTheRestInOrder() {
        assertEquals(
            listOf(HoverButton.PLAY_PAUSE, HoverButton.MEDIA_FIT, HoverButton.CLOSE, HoverButton.NEXT, HoverButton.COVER_OFF),
            hoverButtons(coverOn = true, mirroring = false, media = true, max = 5),
        )
        assertEquals(
            listOf(HoverButton.MIRROR_VIEW, HoverButton.MIRROR_STOP, HoverButton.CLOSE, HoverButton.COVER_OFF),
            hoverButtons(coverOn = true, mirroring = true, media = false, max = 8),
        )
    }

    @Test
    fun closeAlwaysFits() {
        assertEquals(listOf(HoverButton.PLAY_PAUSE, HoverButton.CLOSE), hoverButtons(coverOn = true, mirroring = false, media = true, max = 2))
        assertEquals(listOf(HoverButton.CLOSE), hoverButtons(coverOn = true, mirroring = true, media = false, max = 1))
        assertEquals(listOf(HoverButton.CLOSE), hoverButtons(coverOn = false, mirroring = false, media = false, max = 1))
    }

    @Test
    fun noRoomShowsNothing() {
        assertEquals(emptyList<HoverButton>(), hoverButtons(coverOn = true, mirroring = false, media = false, max = 0))
    }
}
