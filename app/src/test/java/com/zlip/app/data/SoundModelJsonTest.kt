package com.zlip.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The cover sound model as saved: "start with sound" in the settings, "always silent" on a clip. */
class SoundModelJsonTest {

    private val clip = CoverItem.Media(id = "clip", name = "Clip", fileName = "clip.mp4", kind = MediaKind.VIDEO, hasAudio = true)

    private fun decode(json: String): CoverItem.Media = CoverItemJson.decode(JSONObject(json)) as CoverItem.Media

    @Test
    fun theCoverStartsWithSoundByDefault() {
        assertTrue(CoverSettings().startWithSound)
        assertTrue(CoverSettings.fromJson(JSONObject("{}")).startWithSound)
    }

    @Test
    fun startWithSoundSurvivesARoundTrip() {
        for (on in listOf(true, false)) {
            val read = CoverSettings.fromJson(JSONObject(CoverSettings(startWithSound = on).toJson().toString()))
            assertEquals(on, read.startWithSound)
        }
    }

    @Test
    fun theOldImportDefaultDoesNotSilenceTheCover() {
        // "New videos start with sound" (off by default) only seeded imports; it must not become "start off".
        val old = JSONObject("""{"videoSoundDefault":false,"rotation":0}""")
        assertTrue(CoverSettings.fromJson(old).startWithSound)
        assertFalse(CoverSettings().toJson().has("videoSoundDefault"))
    }

    @Test
    fun aClipPlaysSoundUnlessMuted() {
        assertTrue(clip.playsSound)
        assertFalse(clip.copy(muted = true).playsSound)
        assertFalse(clip.copy(hasAudio = false).playsSound)
        assertFalse(clip.copy(kind = MediaKind.ANIMATION).playsSound)
    }

    @Test
    fun mutedSurvivesARoundTripAndIsOnlyWrittenWhenSet() {
        val plain = CoverItemJson.encode(clip)
        assertFalse(plain.has("muted"))
        assertFalse(plain.has("sound"))
        assertEquals(clip, decode(plain.toString()))
        val muted = clip.copy(muted = true)
        assertEquals(muted, decode(CoverItemJson.encode(muted).toString()))
    }

    @Test
    fun theOldSoundFlagIsIgnored() {
        // Saved by earlier versions, mostly as the import default: off no longer silences a clip.
        val old = """{"type":"media","id":"clip","name":"Clip","fileName":"clip.mp4","kind":"VIDEO","hasAudio":true,"sound":false}"""
        val read = decode(old)
        assertFalse(read.muted)
        assertTrue(read.playsSound)
    }
}
