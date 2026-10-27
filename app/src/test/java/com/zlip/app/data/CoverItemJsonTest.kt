package com.zlip.app.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverItemJsonTest {

    private val drawing = CoverItem.PixelArt(
        id = "drawing",
        name = "Heart",
        width = 2,
        height = 2,
        pixels = listOf(0xFFFF0000.toInt(), 0, 0, 0xFF00FF00.toInt()),
        background = 0xFF101010.toInt(),
        extraFrames = listOf(listOf(0, 1, 2, 3)),
        fps = 12,
    )
    private val photo = CoverItem.Media(id = "photo", name = "Beach", fileName = "a.webp", fit = MediaFit.FILL, crisp = false)
    private val video = CoverItem.Media(
        id = "video",
        name = "Clip",
        fileName = "b.mp4",
        kind = MediaKind.VIDEO,
        loop = false,
        posterFileName = "b.jpg",
        hasAudio = true,
        sound = true,
    )
    private val text = CoverItem.Text(id = "text", name = "Note", text = "Hello ✦", textColor = 0xFF9DB4FF.toInt(), sizeSp = 72, scroll = false, speedDps = 30, bold = false)
    private val countdown = CoverItem.Countdown(id = "countdown", name = "Trip", title = "Holiday", targetMillis = 1_800_000_000_000L, countUp = true, dotMatrix = false)
    private val qr = CoverItem.Qr(id = "qr", name = "Wi-Fi", kind = QrKind.WIFI, payload = "WIFI:S:home;;", fields = mapOf("ssid" to "home"), caption = "Scan me")
    private val toy = CoverItem.Toy(id = "toy", name = "Wheel", kind = ToyKind.WHEEL, options = listOf("Pizza", "Sushi"))
    private val collage = CoverItem.Collage(
        id = "collage",
        name = "Mix",
        layout = CollageLayout.BIG_LEFT,
        cells = listOf(listOf(photo, video), listOf(qr), emptyList()),
        intervalSeconds = 10,
        gapDp = 0,
        rounded = false,
    )
    private val group = CoverItem.Group(id = "group", name = "Group A", items = listOf(photo, video, collage, text), stepSeconds = 15)

    private fun roundTrip(item: CoverItem): CoverItem? = CoverItemJson.decode(JSONObject(CoverItemJson.encode(item).toString()))

    @Test
    fun everyTypeSurvivesARoundTrip() {
        for (item in listOf(drawing, photo, video, text, collage, countdown, qr, toy, group)) {
            assertEquals(item, roundTrip(item))
        }
    }

    @Test
    fun unknownOrBrokenEntriesDecodeToNull() {
        assertNull(CoverItemJson.decode(JSONObject("""{"id":"x","name":"X","type":"hologram"}""")))
        assertNull(CoverItemJson.decode(JSONObject("""{"id":"x","type":"media"}""")))
    }

    @Test
    fun aGroupNeverHoldsAnotherGroup() {
        val json = CoverItemJson.encode(group)
        json.getJSONArray("items").put(CoverItemJson.encode(group.copy(id = "inner")))
        val decoded = CoverItemJson.decode(json) as CoverItem.Group
        assertEquals(group.items, decoded.items)
    }

    @Test
    fun aCollageCellNeverHoldsAGroup() {
        val json = CoverItemJson.encode(collage)
        json.getJSONArray("cells").getJSONArray(2).put(CoverItemJson.encode(group))
        val decoded = CoverItemJson.decode(json) as CoverItem.Collage
        assertEquals(emptyList<CoverItem>(), decoded.cells[2])
    }

    @Test
    fun aCollageCanBeAGroupStep() {
        val decoded = roundTrip(CoverItem.Group(id = "g", name = "G", items = listOf(collage, photo))) as CoverItem.Group
        assertEquals(collage, decoded.items.first())
    }

    @Test
    fun stepDefaultsAndIsClamped() {
        fun stepOf(json: String) = (CoverItemJson.decode(JSONObject(json)) as CoverItem.Group).stepSeconds
        assertEquals(CoverItem.Group.DEFAULT_STEP_SECONDS, stepOf("""{"id":"g","name":"G","type":"group","items":[]}"""))
        assertEquals(1, stepOf("""{"id":"g","name":"G","type":"group","items":[],"step":0}"""))
        assertEquals(3600, stepOf("""{"id":"g","name":"G","type":"group","items":[],"step":99999}"""))
    }

    @Test
    fun aGroupKeepsAtMostItsLimitAndDropsUnreadableSteps() {
        val items = JSONArray().apply {
            put(JSONObject("""{"id":"broken","type":"media"}"""))
            repeat(CoverItem.Group.MAX_ITEMS + 5) { put(CoverItemJson.encode(photo.copy(id = "p$it"))) }
        }
        val json = JSONObject().put("id", "g").put("name", "G").put("type", "group").put("items", items)
        val decoded = CoverItemJson.decode(json) as CoverItem.Group
        assertEquals(CoverItem.Group.MAX_ITEMS, decoded.items.size)
        assertEquals("p0", decoded.items.first().id)
    }

    @Test
    fun mediaFilesReachIntoGroupsAndTheirCollages() {
        assertEquals(setOf("a.webp", "b.mp4", "b.jpg"), group.mediaFiles().toSet())
        assertEquals(emptyList<String>(), CoverItem.Group(id = "g", name = "G", items = listOf(text, drawing)).mediaFiles())
    }

    @Test
    fun aQrCodeAnywhereInAGroupCounts() {
        assertTrue(group.showsQr())
        assertTrue(CoverItem.Group(id = "g", name = "G", items = listOf(photo, qr)).showsQr())
        assertFalse(CoverItem.Group(id = "g", name = "G", items = listOf(photo, text)).showsQr())
    }

    @Test
    fun mediaFramingSurvivesARoundTrip() {
        val framed = photo.copy(focusX = -0.75f, focusY = 0.3f)
        assertEquals(framed, roundTrip(framed))
    }

    @Test
    fun mediaFramingDefaultsToTheCentreAndIsClamped() {
        fun mediaOf(extra: String) = CoverItemJson.decode(JSONObject("""{"id":"m","name":"M","type":"media","fileName":"a.webp"$extra}""")) as CoverItem.Media
        assertEquals(0f, mediaOf("").focusX)
        assertEquals(0f, mediaOf("").focusY)
        assertEquals(1f, mediaOf(""","focusX":7.5""").focusX)
        assertEquals(-1f, mediaOf(""","focusY":-3""").focusY)
        assertEquals(0f, mediaOf(""","focusX":"left"""").focusX)
    }

    @Test
    fun aCentredPictureKeepsItsOldJson() {
        // The widget's cached frames are keyed by this JSON: an item nobody framed must not change it.
        val json = CoverItemJson.encode(photo)
        assertFalse(json.has("focusX"))
        assertFalse(json.has("focusY"))
        val framed = CoverItemJson.encode(photo.copy(focusY = 0.5f))
        assertFalse(framed.has("focusX"))
        assertEquals(0.5, framed.getDouble("focusY"), 0.0001)
        // A broken value is never written as NaN, which JSON cannot hold.
        assertFalse(CoverItemJson.encode(photo.copy(focusX = Float.NaN)).has("focusX"))
    }

    @Test
    fun leavesAreThePlainItemsInside() {
        assertEquals(listOf(photo, video, photo, video, qr, text), group.leaves())
        assertEquals(listOf(text), text.leaves())
    }
}
