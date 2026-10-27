package com.zlip.app.system

import com.zlip.app.data.CoverItem
import com.zlip.app.data.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotifiedSoundTest {

    private fun clip(sound: Boolean = false, hasAudio: Boolean = true, kind: MediaKind = MediaKind.VIDEO) =
        CoverItem.Media(id = "clip", name = "Clip", fileName = "clip.mp4", kind = kind, hasAudio = hasAudio, sound = sound)

    @Test
    fun showsTheSavedSound() {
        assertEquals(false, notifiedSound(clip(sound = false), emptySet()))
        assertEquals(true, notifiedSound(clip(sound = true), emptySet()))
    }

    @Test
    fun soundTurnedOnForNowCountsAsOn() {
        // Volume up on the cover: the notification must offer "Sound off", like the cover's own button.
        assertEquals(true, notifiedSound(clip(sound = false), setOf("clip")))
    }

    @Test
    fun soundOnForAnotherClipDoesNotCount() {
        assertEquals(false, notifiedSound(clip(sound = false), setOf("other")))
    }

    @Test
    fun nothingToSwitchWithoutAClipWithSound() {
        assertNull(notifiedSound(null, emptySet()))
        assertNull(notifiedSound(clip(hasAudio = false), setOf("clip")))
        assertNull(notifiedSound(clip(kind = MediaKind.ANIMATION), setOf("clip")))
        assertNull(notifiedSound(CoverItem.Text(id = "t", name = "Text", text = "Hi"), emptySet()))
    }
}
