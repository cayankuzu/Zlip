package com.zlip.app.ui.settings

import android.app.AppOpsManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsHintsTest {

    @Test
    fun pipTurnedOffInSystemSettingsIsNotedWhileTheMiniCoverIsOn() {
        assertTrue(pipBlocked(AppOpsManager.MODE_ERRORED, miniCoverOn = true))
        assertTrue(pipBlocked(AppOpsManager.MODE_IGNORED, miniCoverOn = true))
    }

    @Test
    fun noPipNoteWhenAllowedOrWhenTheMiniCoverIsOff() {
        assertFalse(pipBlocked(AppOpsManager.MODE_ALLOWED, miniCoverOn = true))
        assertFalse(pipBlocked(AppOpsManager.MODE_DEFAULT, miniCoverOn = true))
        assertFalse(pipBlocked(AppOpsManager.MODE_ERRORED, miniCoverOn = false))
    }

    @Test
    fun foldedSettingsAreIdleOnlyWithNeitherWidgetNorAutoOpen() {
        assertTrue(foldedSettingsIdle(widgetPlaced = false, autoShowOnCover = false))
        assertFalse(foldedSettingsIdle(widgetPlaced = true, autoShowOnCover = false))
        assertFalse(foldedSettingsIdle(widgetPlaced = false, autoShowOnCover = true))
        assertFalse(foldedSettingsIdle(widgetPlaced = true, autoShowOnCover = true))
    }
}
