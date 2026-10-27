package com.zlip.app.cover

import com.zlip.app.data.CoverItem
import com.zlip.app.data.MediaKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When the cover offers its sound switch (controls, menu, notification, volume up). */
class SoundSwitchTest {

    private val clip = CoverItem.Media(id = "clip", name = "Clip", fileName = "clip.mp4", kind = MediaKind.VIDEO, hasAudio = true)
    private val photo = CoverItem.Media(id = "photo", name = "Photo", fileName = "photo.webp")

    @Test
    fun aClipWithSoundHasIt() {
        assertTrue(hasSoundToSwitch(clip))
    }

    @Test
    fun silentOrMutedClipsHaveNone() {
        assertFalse(hasSoundToSwitch(clip.copy(hasAudio = false)))
        assertFalse(hasSoundToSwitch(clip.copy(muted = true)))
        assertFalse(hasSoundToSwitch(clip.copy(kind = MediaKind.ANIMATION, hasAudio = true)))
        assertFalse(hasSoundToSwitch(photo))
        assertFalse(hasSoundToSwitch(null))
    }

    @Test
    fun aGroupOffersItOnEveryStepWhenOneClipHasSound() {
        val group = CoverItem.Group(id = "group", name = "Group", items = listOf(photo, clip))
        assertTrue(hasSoundToSwitch(group))
        assertFalse(hasSoundToSwitch(group.copy(items = listOf(photo, clip.copy(muted = true)))))
        assertFalse(hasSoundToSwitch(group.copy(items = emptyList())))
    }

    @Test
    fun aCollageNeverHasSound() {
        // Its cells always play silent, so a switch there would do nothing.
        assertFalse(hasSoundToSwitch(CoverItem.Collage(id = "mix", name = "Mix", cells = listOf(listOf(clip)))))
    }
}
