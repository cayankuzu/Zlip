package com.zlip.app

import com.zlip.app.cover.CoverStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchDecisionsTest {

    @Test
    fun anOrdinaryLaunchIsNoShare() {
        assertEquals(ShareLaunch.NOT_A_SHARE, shareLaunch(isShare = false, usable = false, isTaskRoot = false))
        assertEquals(ShareLaunch.NOT_A_SHARE, shareLaunch(isShare = false, usable = false, isTaskRoot = true))
    }

    @Test
    fun anUnusableShareNeverBuildsAZlip() {
        // In the sender's task or in a task of its own: either way there is nothing to open.
        assertEquals(ShareLaunch.UNSUPPORTED, shareLaunch(isShare = true, usable = false, isTaskRoot = false))
        assertEquals(ShareLaunch.UNSUPPORTED, shareLaunch(isShare = true, usable = false, isTaskRoot = true))
    }

    @Test
    fun aUsableShareInTheSendersTaskGoesToZlipsTask() {
        assertEquals(ShareLaunch.FORWARD, shareLaunch(isShare = true, usable = true, isTaskRoot = false))
    }

    @Test
    fun aUsableShareInZlipsOwnTaskOpens() {
        assertEquals(ShareLaunch.OPEN, shareLaunch(isShare = true, usable = true, isTaskRoot = true))
    }

    @Test
    fun aZlipInUseFullSizeStaysFullSize() {
        // The tile tapped from the shade over a Zlip the user is working in.
        assertFalse(returnsAfterStart(asked = true, coldStart = false, hiddenFromUser = false, fromPip = false))
    }

    @Test
    fun comingFromElsewhereShrinksBack() {
        assertTrue(returnsAfterStart(asked = true, coldStart = true, hiddenFromUser = false, fromPip = false))
        assertTrue(returnsAfterStart(asked = true, coldStart = false, hiddenFromUser = true, fromPip = false))
        // The small window's own button, even if the window is already full size when the intent arrives.
        assertTrue(returnsAfterStart(asked = true, coldStart = false, hiddenFromUser = false, fromPip = true))
    }

    @Test
    fun neverShrinksUnasked() {
        assertFalse(returnsAfterStart(asked = false, coldStart = true, hiddenFromUser = true, fromPip = true))
    }

    @Test
    fun autoStartsWhenOnTopAndFree() {
        assertTrue(start(autoStart = true))
    }

    @Test
    fun theIdleCutoffKeepsAutoStartFromTurningTheCoverStraightBackOn() {
        assertFalse(start(autoStart = true, idleStopped = true))
    }

    @Test
    fun anExplicitRequestStartsEvenAfterTheIdleCutoff() {
        assertTrue(start(autoStart = true, idleStopped = true, requested = true))
        assertTrue(start(autoStart = false, allowed = false, requested = true))
    }

    @Test
    fun noAutoStartAfterTheUserTurnedItOffOrWithAutoStartOff() {
        assertFalse(start(autoStart = true, allowed = false))
        assertFalse(start(autoStart = false))
    }

    @Test
    fun neverStartsUnlessOnTopLoadedAndAvailable() {
        assertFalse(start(autoStart = true, requested = true, top = false))
        assertFalse(start(autoStart = true, requested = true, loaded = false))
        listOf(CoverStatus.CHECKING, CoverStatus.ACTIVE).forEach { status ->
            assertFalse("status $status", start(autoStart = true, requested = true, status = status))
        }
    }

    private fun start(
        autoStart: Boolean,
        allowed: Boolean = true,
        idleStopped: Boolean = false,
        requested: Boolean = false,
        top: Boolean = true,
        loaded: Boolean = true,
        status: CoverStatus = CoverStatus.AVAILABLE,
    ) = shouldStartCover(top, loaded, status, autoStart, allowed, idleStopped, requested)
}
