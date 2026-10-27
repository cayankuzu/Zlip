package com.zlip.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnlockCloseTest {

    @Test
    fun anUnlockWithASecureLockCloses() {
        val unlock = UnlockClose()
        unlock.unlocked()
        assertTrue(unlock.shown(secure = true, locked = false))
    }

    @Test
    fun noUnlockNeverCloses() {
        assertFalse(UnlockClose().shown(secure = true, locked = false))
    }

    @Test
    fun aPhoneWithoutALockNeverCloses() {
        // None or Swipe: a plain wake can count as an unlock.
        val unlock = UnlockClose()
        unlock.unlocked()
        assertFalse(unlock.shown(secure = false, locked = false))
    }

    @Test
    fun anUnlockHeardAfterThePhoneLockedAgainIsDropped() {
        val unlock = UnlockClose()
        unlock.unlocked()
        assertFalse(unlock.shown(secure = true, locked = true))
        // Used up: the next look on an unlocked phone does not close on it either.
        assertFalse(unlock.shown(secure = true, locked = false))
    }

    @Test
    fun aLateUnlockDoesNotCloseAWidgetTapOnAnUnlockedPhone() {
        val unlock = UnlockClose()
        unlock.stopped()
        // Held for a frozen Zlip, delivered before or after the tap's new intent.
        unlock.unlocked()
        unlock.openedByUser(locked = false)
        assertFalse(unlock.shown(secure = true, locked = false))
        unlock.unlocked()
        assertFalse(unlock.shown(secure = true, locked = false))
    }

    @Test
    fun anUnlockAfterTheCoverWentDarkClosesAWidgetOpenedScreenSaver() {
        // Opened on an unlocked cover, then the side key locks it and a fingerprint wakes and unlocks it.
        val unlock = UnlockClose()
        unlock.openedByUser(locked = false)
        unlock.stopped()
        unlock.unlocked()
        assertTrue(unlock.shown(secure = true, locked = false))
    }

    @Test
    fun aWidgetTapOnALockedCoverStillClosesOnUnlock() {
        val unlock = UnlockClose()
        unlock.openedByUser(locked = true)
        unlock.unlocked()
        assertTrue(unlock.shown(secure = true, locked = false))
    }
}
