package com.zlip.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoAdvanceSettingsTest {

    private fun read(json: String) = CoverSettings.fromJson(JSONObject(json))

    private fun roundTrip(settings: CoverSettings): CoverSettings = CoverSettings.fromJson(JSONObject(settings.toJson().toString()))

    @Test
    fun newSettingsStartOffWithVideosToTheEnd() {
        val defaults = CoverSettings()
        assertFalse(defaults.autoAdvance)
        assertTrue(defaults.videosToEnd)
        assertEquals(10, defaults.advanceSeconds)
        assertEquals(AdvanceMode.OFF, defaults.advanceMode)
    }

    @Test
    fun theModeFollowsTheSwitchAndTheVideosChoice() {
        assertEquals(AdvanceMode.OFF, CoverSettings(autoAdvance = false, videosToEnd = false).advanceMode)
        assertEquals(AdvanceMode.SMART, CoverSettings(autoAdvance = true, videosToEnd = true).advanceMode)
        assertEquals(AdvanceMode.TIMER, CoverSettings(autoAdvance = true, videosToEnd = false).advanceMode)
    }

    @Test
    fun everyCombinationSurvivesARoundTrip() {
        for (auto in listOf(false, true)) {
            for (toEnd in listOf(false, true)) {
                for (seconds in CoverSettings.ADVANCE_SECONDS_OPTIONS) {
                    val settings = CoverSettings(autoAdvance = auto, videosToEnd = toEnd, advanceSeconds = seconds)
                    assertEquals(settings, roundTrip(settings))
                }
            }
        }
    }

    @Test
    fun theOldModeIsCarriedOver() {
        read("""{"advanceMode":"OFF","advanceSeconds":15}""").let {
            assertFalse(it.autoAdvance)
            assertTrue(it.videosToEnd)
            assertEquals(15, it.advanceSeconds)
        }
        read("""{"advanceMode":"SMART","advanceSeconds":30}""").let {
            assertTrue(it.autoAdvance)
            assertTrue(it.videosToEnd)
            assertEquals(30, it.advanceSeconds)
        }
        read("""{"advanceMode":"TIMER","advanceSeconds":60}""").let {
            assertTrue(it.autoAdvance)
            assertFalse(it.videosToEnd)
            assertEquals(60, it.advanceSeconds)
        }
    }

    @Test
    fun theFirstVersionsSlideshowBecomesATimer() {
        val read = read("""{"slideshowSeconds":30}""")
        assertTrue(read.autoAdvance)
        assertFalse(read.videosToEnd)
        assertEquals(30, read.advanceSeconds)
    }

    @Test
    fun noAdvanceSettingsAtAllStayOff() {
        val read = read("""{"rotation":90}""")
        assertFalse(read.autoAdvance)
        assertTrue(read.videosToEnd)
        assertEquals(CoverSettings().advanceSeconds, read.advanceSeconds)
    }

    @Test
    fun theNewKeysWinOverAnOldMode() {
        val read = read("""{"advanceMode":"TIMER","autoAdvance":false,"videosToEnd":true}""")
        assertFalse(read.autoAdvance)
        assertTrue(read.videosToEnd)
    }

    @Test
    fun onlyTheNewKeysAreWritten() {
        val json = CoverSettings(autoAdvance = true, videosToEnd = false, advanceSeconds = 15).toJson()
        assertTrue(json.getBoolean("autoAdvance"))
        assertFalse(json.getBoolean("videosToEnd"))
        assertEquals(15, json.getInt("advanceSeconds"))
        assertFalse(json.has("advanceMode"))
    }

    @Test
    fun anUnknownPhotoTimeFallsBackToTheDefault() {
        assertEquals(CoverSettings().advanceSeconds, read("""{"autoAdvance":true,"advanceSeconds":7}""").advanceSeconds)
    }
}
