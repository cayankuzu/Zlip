package com.zlip.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupOptionsJsonTest {

    private val photo = CoverItem.Media(id = "photo", name = "Photo", fileName = "p.webp")
    private val video = CoverItem.Media(id = "video", name = "Clip", fileName = "v.mp4", kind = MediaKind.VIDEO, durationMs = 42_500)
    private val unmeasured = video.copy(id = "old", fileName = "old.mp4", durationMs = 0)

    private fun roundTrip(item: CoverItem): CoverItem? = CoverItemJson.decode(JSONObject(CoverItemJson.encode(item).toString()))

    @Test
    fun aNewGroupHasTheFriendlyDefaults() {
        val group = CoverItem.Group(id = "g", name = "G")
        assertEquals(5, group.stepSeconds)
        assertTrue(group.videosToEnd)
        assertFalse(group.shuffle)
        assertTrue(group.repeat)
        assertTrue(group.crossfade)
    }

    @Test
    fun everyOptionSurvivesARoundTrip() {
        for (toEnd in listOf(false, true)) {
            for (shuffle in listOf(false, true)) {
                for (repeat in listOf(false, true)) {
                    for (crossfade in listOf(false, true)) {
                        val group = CoverItem.Group(
                            id = "g",
                            name = "G",
                            items = listOf(photo, video),
                            stepSeconds = 15,
                            videosToEnd = toEnd,
                            shuffle = shuffle,
                            repeat = repeat,
                            crossfade = crossfade,
                        )
                        assertEquals(group, roundTrip(group))
                    }
                }
            }
        }
    }

    @Test
    fun anOlderGroupReadsAsTheDefaults() {
        val old = CoverItemJson.decode(JSONObject("""{"id":"g","name":"G","type":"group","items":[],"step":10}""")) as CoverItem.Group
        assertEquals(10, old.stepSeconds)
        assertTrue(old.videosToEnd)
        assertFalse(old.shuffle)
        assertTrue(old.repeat)
        assertTrue(old.crossfade)
    }

    @Test
    fun theOptionsAreWrittenUnderTheirKeys() {
        val json = CoverItemJson.encode(CoverItem.Group(id = "g", name = "G", videosToEnd = false, shuffle = true, repeat = false, crossfade = false))
        assertFalse(json.getBoolean("videosToEnd"))
        assertTrue(json.getBoolean("shuffle"))
        assertFalse(json.getBoolean("repeat"))
        assertFalse(json.getBoolean("crossfade"))
    }

    @Test
    fun aVideosLengthSurvivesARoundTrip() {
        assertEquals(video, roundTrip(video))
        assertEquals(42_500L, CoverItemJson.encode(video).getLong("durationMs"))
    }

    @Test
    fun anUnknownLengthIsNotWrittenAndReadsAsZero() {
        assertFalse(CoverItemJson.encode(unmeasured).has("durationMs"))
        assertEquals(0L, (roundTrip(unmeasured) as CoverItem.Media).durationMs)
        val broken = CoverItemJson.decode(JSONObject("""{"id":"v","name":"V","type":"media","fileName":"v.mp4","kind":"VIDEO","durationMs":-5}"""))
        assertEquals(0L, (broken as CoverItem.Media).durationMs)
    }

    @Test
    fun videosWithoutALengthAreFoundEverywhere() {
        val collage = CoverItem.Collage(id = "c", name = "C", layout = CollageLayout.SIDE_BY_SIDE, cells = listOf(listOf(unmeasured), listOf(photo)))
        val group = CoverItem.Group(id = "g", name = "G", items = listOf(video, collage, unmeasured.copy(id = "other", fileName = "other.mp4")))
        assertEquals(setOf("old.mp4", "other.mp4"), group.videosWithoutDuration())
        assertEquals(emptySet<String>(), photo.videosWithoutDuration())
        assertEquals(emptySet<String>(), video.videosWithoutDuration())
    }

    @Test
    fun measuredLengthsReachEveryCopy() {
        val collage = CoverItem.Collage(id = "c", name = "C", layout = CollageLayout.SIDE_BY_SIDE, cells = listOf(listOf(unmeasured), listOf(photo)))
        val group = CoverItem.Group(id = "g", name = "G", items = listOf(unmeasured, collage, photo))
        val filled = group.withVideoDurations(mapOf("old.mp4" to 61_000L)) as CoverItem.Group
        assertEquals(61_000L, (filled.items[0] as CoverItem.Media).durationMs)
        assertEquals(61_000L, ((filled.items[1] as CoverItem.Collage).cells[0][0] as CoverItem.Media).durationMs)
        assertEquals(photo, filled.items[2])
    }

    @Test
    fun aKnownLengthIsNeverOverwrittenAndNothingChangedStaysTheSame() {
        assertSame(video, video.withVideoDurations(mapOf("v.mp4" to 1_000L)))
        val group = CoverItem.Group(id = "g", name = "G", items = listOf(photo, video))
        assertSame(group, group.withVideoDurations(mapOf("elsewhere.mp4" to 1_000L)))
        // A photo that happens to share a name is not a video.
        assertSame(photo, photo.withVideoDurations(mapOf("p.webp" to 1_000L)))
    }
}
