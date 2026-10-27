package com.zlip.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverSettingsTest {

    private fun roundTrip(settings: CoverSettings): CoverSettings =
        CoverSettings.fromJson(JSONObject(settings.toJson().toString()))

    @Test
    fun defaultsKeepTheCoverOnAndTheTipVisible() {
        val defaults = CoverSettings()
        assertTrue(defaults.keepCoverInOtherApps)
        assertEquals(10, defaults.autoOffMinutes)
        assertEquals(MirrorFit.FIT, defaults.mirrorFit)
        assertFalse(defaults.widgetTipDismissed)
    }

    @Test
    fun newerFieldsSurviveARoundTrip() {
        val changed = CoverSettings(
            keepCoverInOtherApps = false,
            autoOffMinutes = 30,
            mirrorFit = MirrorFit.FILL,
            widgetTipDismissed = true,
        )
        assertEquals(changed, roundTrip(changed))
    }

    @Test
    fun everyAutoOffChoiceSurvivesARoundTrip() {
        for (minutes in CoverSettings.AUTO_OFF_OPTIONS) {
            assertEquals(minutes, roundTrip(CoverSettings(autoOffMinutes = minutes)).autoOffMinutes)
        }
    }

    @Test
    fun defaultsSurviveARoundTrip() {
        assertEquals(CoverSettings(), roundTrip(CoverSettings()))
    }

    @Test
    fun olderFilesWithoutTheNewerFieldsGetTheDefaults() {
        // Written before the small window, the idle cutoff, mirroring and the dismissible widget tip existed.
        val old = JSONObject("""{"rotation":180,"autoStart":false,"brightness":0.5,"standbySeconds":60,"onboardingDone":true}""")
        val read = CoverSettings.fromJson(old)
        val defaults = CoverSettings()
        assertEquals(defaults.keepCoverInOtherApps, read.keepCoverInOtherApps)
        assertEquals(defaults.autoOffMinutes, read.autoOffMinutes)
        assertEquals(defaults.mirrorFit, read.mirrorFit)
        assertEquals(defaults.widgetTipDismissed, read.widgetTipDismissed)
        // The fields it did have are kept.
        assertEquals(180, read.rotation)
        assertFalse(read.autoStart)
        assertEquals(60, read.standbySeconds)
        assertTrue(read.onboardingDone)
    }

    @Test
    fun unknownValuesFallBackToTheDefaults() {
        val odd = JSONObject("""{"autoOffMinutes":7,"mirrorFit":"STRETCH"}""")
        val read = CoverSettings.fromJson(odd)
        assertEquals(CoverSettings().autoOffMinutes, read.autoOffMinutes)
        assertEquals(MirrorFit.FIT, read.mirrorFit)
    }

    @Test
    fun noFileAtAllGivesTheDefaults() {
        assertEquals(CoverSettings(), CoverSettings.fromJson(null))
    }
}
