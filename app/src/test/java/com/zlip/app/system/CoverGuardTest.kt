package com.zlip.app.system

import android.media.AudioManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverGuardTest {

    @Test
    fun armsAFoldedPhoneWithNoCall() {
        assertTrue(CoverGuard.shouldArm(AudioManager.MODE_NORMAL, needsArming = true, folded = true))
    }

    @Test
    fun neverOverACall() {
        listOf(
            AudioManager.MODE_RINGTONE,
            AudioManager.MODE_IN_CALL,
            AudioManager.MODE_IN_COMMUNICATION,
            AudioManager.MODE_CALL_SCREENING,
        ).forEach { mode ->
            assertFalse("mode $mode", CoverGuard.shouldArm(mode, needsArming = true, folded = true))
        }
    }

    @Test
    fun notWhenAlreadyOnTheCover() {
        assertFalse(CoverGuard.shouldArm(AudioManager.MODE_NORMAL, needsArming = false, folded = true))
    }

    @Test
    fun neverOnAnOpenPhoneUnlessAlwaysOn() {
        assertFalse(CoverGuard.shouldArm(AudioManager.MODE_NORMAL, needsArming = true, folded = false, alwaysOn = false))
        assertTrue(CoverGuard.shouldArm(AudioManager.MODE_NORMAL, needsArming = true, folded = false, alwaysOn = true))
    }
}
