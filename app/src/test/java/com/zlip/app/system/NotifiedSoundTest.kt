package com.zlip.app.system

import com.zlip.app.data.CoverItem
import com.zlip.app.data.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotifiedSoundTest {

    private fun clip(muted: Boolean = false, hasAudio: Boolean = true, kind: MediaKind = MediaKind.VIDEO) =
        CoverItem.Media(id = "clip", name = "Clip", fileName = "clip.mp4", kind = kind, hasAudio = hasAudio, muted = muted)

    @Test
    fun showsTheCoversSoundForNow() {
        // The same as the cover's own button: on for this session (volume up included) or off.
        assertEquals(true, notifiedSound(clip(), soundOn = true))
        assertEquals(false, notifiedSound(clip(), soundOn = false))
    }

    @Test
    fun aGroupWithAClipWithSoundOffersTheSwitch() {
        val photo = CoverItem.Media(id = "photo", name = "Photo", fileName = "photo.webp")
        val group = CoverItem.Group(id = "group", name = "Group", items = listOf(photo, clip()))
        assertEquals(true, notifiedSound(group, soundOn = true))
        assertNull(notifiedSound(group.copy(items = listOf(photo, clip(muted = true))), soundOn = true))
    }

    @Test
    fun nothingToSwitchWithoutAClipWithSound() {
        assertNull(notifiedSound(null, soundOn = true))
        assertNull(notifiedSound(clip(hasAudio = false), soundOn = true))
        assertNull(notifiedSound(clip(muted = true), soundOn = true))
        assertNull(notifiedSound(clip(kind = MediaKind.ANIMATION), soundOn = true))
        assertNull(notifiedSound(CoverItem.Text(id = "t", name = "Text", text = "Hi"), soundOn = true))
        // Collage cells always play silent.
        assertNull(notifiedSound(CoverItem.Collage(id = "c", name = "Mix", cells = listOf(listOf(clip()))), soundOn = true))
    }
}
