package com.zlip.app.data

import org.json.JSONArray
import org.json.JSONObject

/** One thing the cover screen can show. Colors are ARGB ints. */
sealed interface CoverItem {
    val id: String
    val name: String

    /**
     * Hand-drawn pixel grid. [pixels] is row-major, size == width * height; 0 (transparent) shows
     * [background]. With [extraFrames] it is an animation: [pixels] is the first frame, each extra frame
     * has the same size, and frames advance at [fps] (looping).
     */
    data class PixelArt(
        override val id: String,
        override val name: String,
        val width: Int,
        val height: Int,
        val pixels: List<Int>,
        val background: Int = BLACK,
        val extraFrames: List<List<Int>> = emptyList(),
        val fps: Int = DEFAULT_FPS,
    ) : CoverItem {
        init {
            require(width > 0 && height > 0) { "Pixel grid must not be empty" }
            require(pixels.size == width * height) { "Expected ${width * height} pixels, got ${pixels.size}" }
            require(extraFrames.all { it.size == width * height }) { "Every frame must have ${width * height} pixels" }
        }

        operator fun get(x: Int, y: Int): Int = pixels[y * width + x]

        val frameCount: Int get() = 1 + extraFrames.size
        val animated: Boolean get() = extraFrames.isNotEmpty()

        /** Pixels of frame [index] (0 = [pixels]). */
        fun frame(index: Int): List<Int> = if (index <= 0) pixels else extraFrames.getOrElse(index - 1) { pixels }

        /** A single-frame copy showing frame [index], for drawing code that only knows [pixels]. */
        fun atFrame(index: Int): PixelArt = if (index == 0 && !animated) this else copy(pixels = frame(index), extraFrames = emptyList())

        companion object {
            const val DEFAULT_FPS = 6
            const val MAX_FRAMES = 24
        }
    }

    /** Photo, GIF / animated WebP or video copied into app storage (see [MediaStorage]). */
    data class Media(
        override val id: String,
        override val name: String,
        val fileName: String,
        val kind: MediaKind = MediaKind.IMAGE,
        val fit: MediaFit = MediaFit.FIT,
        /** Nearest-neighbour scaling: keeps pixel-art GIFs sharp instead of blurry. */
        val crisp: Boolean = true,
        val background: Int = BLACK,
        /** Animations and videos: repeat forever, or play once and hold the last frame. */
        val loop: Boolean = true,
        /** Videos: a still frame used for thumbnails, so the library never decodes video. */
        val posterFileName: String? = null,
        /** Videos: the clip's length in milliseconds; 0 = not known yet (older imports get it in the background). */
        val durationMs: Long = 0L,
        /** Videos: the file kept its audio track (older imports and silent clips have none). */
        val hasAudio: Boolean = false,
        /**
         * Videos with audio: always silent, whatever the cover's sound says. Off by default: a clip with audio
         * is heard on the cover whenever its sound is on there (see [CoverSettings.startWithSound]).
         */
        val muted: Boolean = false,
        /**
         * Which part of the picture stays in view on an axis where it is larger than its box (FILL's cropped axis,
         * or any axis once [zoom]ed in far enough), -1 (left / top edge) .. 1 (right / bottom edge), 0 = centre.
         * An axis that fits is centred whatever this says. See [MediaFocus].
         */
        val focusX: Float = 0f,
        val focusY: Float = 0f,
        /** How much closer than its [fit] the picture is shown, 1 .. [MediaFocus.MAX_ZOOM]. See [MediaFocus]. */
        val zoom: Float = MediaFocus.MIN_ZOOM,
    ) : CoverItem {
        /** A clip the cover may play with sound: a video that kept its audio and is not [muted]. */
        val playsSound: Boolean get() = kind == MediaKind.VIDEO && hasAudio && !muted
    }

    data class Text(
        override val id: String,
        override val name: String,
        val text: String,
        val textColor: Int = WHITE,
        val background: Int = BLACK,
        val sizeSp: Int = 48,
        val scroll: Boolean = true,
        /** Marquee speed in dp per second. */
        val speedDps: Int = 60,
        val bold: Boolean = true,
    ) : CoverItem

    /**
     * Several items on one screen. Each cell loops through its own items every [intervalSeconds]
     * (cells change one after another, never all at once). Cells hold copies of library items, so a
     * collage keeps working when an original is edited or deleted.
     */
    data class Collage(
        override val id: String,
        override val name: String,
        val layout: CollageLayout = CollageLayout.GRID,
        /** One list per cell (size == layout.cells.size); never another collage or a group. */
        val cells: List<List<CoverItem>> = List(layout.cells.size) { emptyList() },
        val intervalSeconds: Int = 5,
        val gapDp: Int = 4,
        val rounded: Boolean = true,
        val background: Int = BLACK,
    ) : CoverItem {
        /** Items in the longest cell: after that many steps every cell has shown everything once. */
        val longestCell: Int get() = cells.maxOfOrNull { it.size } ?: 0
    }

    /**
     * Items shown one after another, each full screen (unlike a [Collage], which splits the screen).
     * Stills stay [stepSeconds]; videos play to their end ([videosToEnd]); scrolling text, animations and
     * collages finish their pass after [stepSeconds] (see [GroupTiming]). One trip through [items] is one pass
     * for auto-advance. Holds copies of library items, like a collage, but never another group.
     */
    data class Group(
        override val id: String,
        override val name: String,
        val items: List<CoverItem> = emptyList(),
        /** Photo time: how long still steps stay, and the minimum for GIFs, animations and scrolling text. */
        val stepSeconds: Int = DEFAULT_STEP_SECONDS,
        /** Video steps play once from start to end; off cuts them at [stepSeconds]. */
        val videosToEnd: Boolean = true,
        /** The steps play in a new random order every pass, none twice in one pass. */
        val shuffle: Boolean = false,
        /** After the last step the group starts over; off, it stops on its last step. */
        val repeat: Boolean = true,
        /** Still steps fade into each other; off they cut. Video steps always cut. */
        val crossfade: Boolean = true,
    ) : CoverItem {
        companion object {
            const val DEFAULT_STEP_SECONDS = 5

            /** Keeps items.json and the widget's frames bounded. */
            const val MAX_ITEMS = 30
        }
    }

    /** A live countdown to a moment ("Holiday in 12 days"), or a count-up since one ("Day 40"). */
    data class Countdown(
        override val id: String,
        override val name: String,
        /** What is being counted down to, shown with the number. */
        val title: String,
        /** Epoch millis of the moment. */
        val targetMillis: Long,
        /** Count the time since [targetMillis] instead of the time until it. */
        val countUp: Boolean = false,
        val textColor: Int = WHITE,
        val accentColor: Int = MINT,
        val background: Int = BLACK,
        /** Digits as glowing dots (LED sign) instead of plain type. */
        val dotMatrix: Boolean = true,
        /** For moments that come back (the weekend, a birthday): the card always counts to the next return. */
        val repeat: Repeat = Repeat.NONE,
    ) : CoverItem {
        enum class Repeat { NONE, WEEKLY, YEARLY }
    }

    /** A scannable code on the cover: a link, Wi-Fi login, contact card or any text. */
    data class Qr(
        override val id: String,
        override val name: String,
        val kind: QrKind,
        /** Exactly what the code encodes (built by the editor from the user's fields). */
        val payload: String,
        /** The editor's fields, so the item can be edited again (keys depend on [kind]). */
        val fields: Map<String, String> = emptyMap(),
        /** Short line under the code ("Scan for Wi-Fi"). */
        val caption: String = "",
        /** Module color; must stay dark on a light [background] for scanners. */
        val codeColor: Int = BLACK,
        val background: Int = WHITE,
    ) : CoverItem

    /** A little game for the cover, played with a tap: roll, flip, spin or ask. */
    data class Toy(
        override val id: String,
        override val name: String,
        val kind: ToyKind,
        val color: Int = MINT,
        val background: Int = BLACK,
        /** Magic ball and decision wheel answers; empty uses the built-in ones. */
        val options: List<String> = emptyList(),
    ) : CoverItem

    companion object {
        const val BLACK: Int = 0xFF000000.toInt()
        const val WHITE: Int = 0xFFFFFFFF.toInt()
        const val MINT: Int = 0xFF7DF0C3.toInt()
    }
}

enum class QrKind { LINK, WIFI, CONTACT, TEXT }

enum class ToyKind { DICE, COIN, MAGIC_BALL, BOTTLE, WHEEL, COUNTER }

/** [this] item, or the plain items it is made of: a collage's cells and a group's steps, all the way down. */
fun CoverItem.leaves(): List<CoverItem> = when (this) {
    is CoverItem.Collage -> cells.flatten().flatMap { it.leaves() }
    is CoverItem.Group -> items.flatMap { it.leaves() }
    else -> listOf(this)
}

/** A QR code is on screen (alone, in a collage cell or as a group step): it must stay sharp and bright to scan. */
fun CoverItem.showsQr(): Boolean = leaves().any { it is CoverItem.Qr }

/** Every media file [this] item needs, including those inside collages and groups. */
fun CoverItem.mediaFiles(): List<String> = leaves().flatMap { if (it is CoverItem.Media) listOfNotNull(it.fileName, it.posterFileName) else emptyList() }

/**
 * [this] with every copy of [edited] inside it (collage cells, group steps, cells of a collage in a group)
 * replaced by [edited], so a saved edit shows up wherever the item was placed.
 */
fun CoverItem.replacingCopies(edited: CoverItem): CoverItem = when (this) {
    // Cells never hold collages or groups, so only plain items can match there.
    is CoverItem.Collage -> if (edited is CoverItem.Collage || edited is CoverItem.Group) {
        this
    } else {
        copy(cells = cells.map { cell -> cell.map { if (it.id == edited.id) edited else it } })
    }
    is CoverItem.Group -> if (edited is CoverItem.Group) this else copy(items = items.map { if (it.id == edited.id) edited else it.replacingCopies(edited) })
    else -> this
}

/**
 * [this] with media item [id] framed as [framing] wherever it is: the item itself, collage cells, group steps and
 * cells of a collage in a group. Returns [this] itself when nothing changed.
 */
fun CoverItem.withMediaFraming(id: String, framing: MediaFraming): CoverItem = when (this) {
    is CoverItem.Media -> if (this.id != id) this else framed(framing).let { if (it == this) this else it }
    is CoverItem.Collage -> {
        val updated = cells.map { cell -> cell.map { it.withMediaFraming(id, framing) } }
        if (updated == cells) this else copy(cells = updated)
    }
    is CoverItem.Group -> {
        val updated = items.map { it.withMediaFraming(id, framing) }
        if (updated == items) this else copy(items = updated)
    }
    else -> this
}

/** File names of the videos in [this] item (collage cells and group steps too) whose length is not known yet. */
fun CoverItem.videosWithoutDuration(): Set<String> =
    leaves().filter { it is CoverItem.Media && it.kind == MediaKind.VIDEO && it.durationMs <= 0 }
        .mapTo(LinkedHashSet()) { (it as CoverItem.Media).fileName }

/**
 * [this] with the lengths in [durations] (by file name) filled into every video that has none, copies inside
 * collages and groups included. Returns [this] itself when nothing changed.
 */
fun CoverItem.withVideoDurations(durations: Map<String, Long>): CoverItem = when (this) {
    is CoverItem.Media -> {
        val known = durations[fileName] ?: 0L
        if (kind == MediaKind.VIDEO && durationMs <= 0 && known > 0) copy(durationMs = known) else this
    }
    is CoverItem.Collage -> {
        val updated = cells.map { cell -> cell.map { it.withVideoDurations(durations) } }
        if (updated == cells) this else copy(cells = updated)
    }
    is CoverItem.Group -> {
        val updated = items.map { it.withVideoDurations(durations) }
        if (updated == items) this else copy(items = updated)
    }
    else -> this
}

/**
 * Collage cell rectangles as fractions of the screen (left, top, right, bottom). Shared by the live
 * renderer and the widget's still frames so both draw the same layout.
 */
enum class CollageLayout(val cells: List<FloatArray>) {
    SIDE_BY_SIDE(listOf(floatArrayOf(0f, 0f, .5f, 1f), floatArrayOf(.5f, 0f, 1f, 1f))),
    STACKED(listOf(floatArrayOf(0f, 0f, 1f, .5f), floatArrayOf(0f, .5f, 1f, 1f))),
    BIG_LEFT(listOf(floatArrayOf(0f, 0f, .5f, 1f), floatArrayOf(.5f, 0f, 1f, .5f), floatArrayOf(.5f, .5f, 1f, 1f))),
    BIG_TOP(listOf(floatArrayOf(0f, 0f, 1f, .5f), floatArrayOf(0f, .5f, .5f, 1f), floatArrayOf(.5f, .5f, 1f, 1f))),
    GRID(
        listOf(
            floatArrayOf(0f, 0f, .5f, .5f),
            floatArrayOf(.5f, 0f, 1f, .5f),
            floatArrayOf(0f, .5f, .5f, 1f),
            floatArrayOf(.5f, .5f, 1f, 1f),
        ),
    ),
}

enum class MediaFit { FIT, FILL }

enum class MediaKind { IMAGE, ANIMATION, VIDEO }

/** Like [String.take] but never splits a surrogate pair (half an emoji would be saved as '?'). */
fun String.takeSafe(maxChars: Int): String {
    val cut = take(maxChars)
    return if (cut.isNotEmpty() && cut.last().isHighSurrogate()) cut.dropLast(1) else cut
}

internal object CoverItemJson {

    fun encode(item: CoverItem): JSONObject = JSONObject().apply {
        put("id", item.id)
        put("name", item.name)
        when (item) {
            is CoverItem.PixelArt -> {
                put("type", "pixel")
                put("width", item.width)
                put("height", item.height)
                put("background", item.background)
                put("pixels", JSONArray(item.pixels))
                if (item.animated) {
                    put("frames", JSONArray().apply { item.extraFrames.forEach { put(JSONArray(it)) } })
                    put("fps", item.fps)
                }
            }
            is CoverItem.Media -> {
                put("type", "media")
                put("fileName", item.fileName)
                put("kind", item.kind.name)
                put("fit", item.fit.name)
                put("crisp", item.crisp)
                put("background", item.background)
                put("loop", item.loop)
                if (item.posterFileName != null) put("poster", item.posterFileName)
                if (item.durationMs > 0) put("durationMs", item.durationMs)
                put("hasAudio", item.hasAudio)
                // Only when set: an untouched clip keeps its JSON (the widget keys its frames by it).
                if (item.muted) put("muted", true)
                // Only when framed off the centre: an untouched item keeps its old JSON, so the widget's frames
                // (keyed by it) are not all redrawn after an update. Clamped: JSON cannot hold NaN.
                MediaFocus.clamp(item.focusX).let { if (it != 0f) put("focusX", it.toDouble()) }
                MediaFocus.clamp(item.focusY).let { if (it != 0f) put("focusY", it.toDouble()) }
                MediaFocus.clampZoom(item.zoom).let { if (it != MediaFocus.MIN_ZOOM) put("zoom", it.toDouble()) }
            }
            is CoverItem.Text -> {
                put("type", "text")
                put("text", item.text)
                put("textColor", item.textColor)
                put("background", item.background)
                put("sizeSp", item.sizeSp)
                put("scroll", item.scroll)
                put("speedDps", item.speedDps)
                put("bold", item.bold)
            }
            is CoverItem.Collage -> {
                put("type", "collage")
                put("layout", item.layout.name)
                put("cells", JSONArray().apply { item.cells.forEach { cell -> put(JSONArray().apply { cell.forEach { put(encode(it)) } }) } })
                put("interval", item.intervalSeconds)
                put("gap", item.gapDp)
                put("rounded", item.rounded)
                put("background", item.background)
            }
            is CoverItem.Countdown -> {
                put("type", "countdown")
                put("title", item.title)
                put("target", item.targetMillis)
                put("countUp", item.countUp)
                put("textColor", item.textColor)
                put("accentColor", item.accentColor)
                put("background", item.background)
                put("dotMatrix", item.dotMatrix)
                put("repeat", item.repeat.name)
            }
            is CoverItem.Qr -> {
                put("type", "qr")
                put("kind", item.kind.name)
                put("payload", item.payload)
                put("fields", JSONObject().apply { item.fields.forEach { (key, value) -> put(key, value) } })
                put("caption", item.caption)
                put("codeColor", item.codeColor)
                put("background", item.background)
            }
            is CoverItem.Toy -> {
                put("type", "toy")
                put("kind", item.kind.name)
                put("color", item.color)
                put("background", item.background)
                put("options", JSONArray(item.options))
            }
            is CoverItem.Group -> {
                put("type", "group")
                put("items", JSONArray().apply { item.items.forEach { put(encode(it)) } })
                put("step", item.stepSeconds)
                put("videosToEnd", item.videosToEnd)
                put("shuffle", item.shuffle)
                put("repeat", item.repeat)
                put("crossfade", item.crossfade)
            }
        }
    }

    /** Returns null for unknown or corrupt entries so one bad item never wipes the library. */
    fun decode(json: JSONObject): CoverItem? = runCatching {
        val id = json.getString("id")
        val name = json.optString("name")
        when (json.getString("type")) {
            "pixel" -> {
                val array = json.getJSONArray("pixels")
                val width = json.getInt("width")
                val height = json.getInt("height")
                val frames = json.optJSONArray("frames") ?: JSONArray()
                CoverItem.PixelArt(
                    id = id,
                    name = name,
                    width = width,
                    height = height,
                    pixels = List(array.length()) { array.getInt(it) },
                    background = json.optInt("background", CoverItem.BLACK),
                    // A damaged frame is dropped rather than costing the whole drawing.
                    extraFrames = (0 until frames.length())
                        .mapNotNull { index -> frames.optJSONArray(index)?.let { frame -> List(frame.length()) { frame.getInt(it) } } }
                        .filter { it.size == width * height }
                        .take(CoverItem.PixelArt.MAX_FRAMES - 1),
                    fps = json.optInt("fps", CoverItem.PixelArt.DEFAULT_FPS).coerceIn(1, 30),
                )
            }
            "media" -> CoverItem.Media(
                id = id,
                name = name,
                fileName = json.getString("fileName"),
                kind = enumOr(json.optString("kind"), MediaKind.IMAGE),
                fit = enumOr(json.optString("fit"), MediaFit.FIT),
                crisp = json.optBoolean("crisp", true),
                background = json.optInt("background", CoverItem.BLACK),
                loop = json.optBoolean("loop", true),
                posterFileName = json.optString("poster").ifEmpty { null },
                durationMs = json.optLong("durationMs", 0L).coerceAtLeast(0L),
                hasAudio = json.optBoolean("hasAudio", false),
                // The old "sound" key is ignored: it was mostly the import default, not a choice.
                muted = json.optBoolean("muted", false),
                focusX = MediaFocus.clamp(json.optDouble("focusX", 0.0).toFloat()),
                focusY = MediaFocus.clamp(json.optDouble("focusY", 0.0).toFloat()),
                zoom = MediaFocus.clampZoom(json.optDouble("zoom", 1.0).toFloat()),
            )
            "text" -> CoverItem.Text(
                id = id,
                name = name,
                text = json.getString("text"),
                textColor = json.optInt("textColor", CoverItem.WHITE),
                background = json.optInt("background", CoverItem.BLACK),
                sizeSp = json.optInt("sizeSp", 48),
                scroll = json.optBoolean("scroll", true),
                speedDps = json.optInt("speedDps", 60),
                bold = json.optBoolean("bold", true),
            )
            "collage" -> {
                val layout = enumOr(json.optString("layout"), CollageLayout.GRID)
                val array = json.optJSONArray("cells") ?: JSONArray()
                // Always exactly one list per cell; nested collages, groups and unreadable entries are dropped.
                val cells = List(layout.cells.size) { index ->
                    val cell = array.optJSONArray(index) ?: JSONArray()
                    (0 until cell.length()).mapNotNull { at -> cell.optJSONObject(at)?.let(::decode) }
                        .filter { it !is CoverItem.Collage && it !is CoverItem.Group }
                }
                CoverItem.Collage(
                    id = id,
                    name = name,
                    layout = layout,
                    cells = cells,
                    intervalSeconds = json.optInt("interval", 5).coerceIn(1, 3600),
                    gapDp = json.optInt("gap", 4).coerceIn(0, 32),
                    rounded = json.optBoolean("rounded", true),
                    background = json.optInt("background", CoverItem.BLACK),
                )
            }
            "countdown" -> CoverItem.Countdown(
                id = id,
                name = name,
                title = json.optString("title"),
                targetMillis = json.getLong("target"),
                countUp = json.optBoolean("countUp", false),
                textColor = json.optInt("textColor", CoverItem.WHITE),
                accentColor = json.optInt("accentColor", CoverItem.MINT),
                background = json.optInt("background", CoverItem.BLACK),
                dotMatrix = json.optBoolean("dotMatrix", true),
                repeat = enumOr(json.optString("repeat"), CoverItem.Countdown.Repeat.NONE),
            )
            "qr" -> {
                val fields = json.optJSONObject("fields")
                CoverItem.Qr(
                    id = id,
                    name = name,
                    kind = enumOr(json.optString("kind"), QrKind.TEXT),
                    payload = json.getString("payload"),
                    fields = fields?.keys()?.asSequence()?.associateWith { fields.optString(it) }.orEmpty(),
                    caption = json.optString("caption"),
                    codeColor = json.optInt("codeColor", CoverItem.BLACK),
                    background = json.optInt("background", CoverItem.WHITE),
                )
            }
            "toy" -> {
                val options = json.optJSONArray("options") ?: JSONArray()
                CoverItem.Toy(
                    id = id,
                    name = name,
                    kind = enumOr(json.optString("kind"), ToyKind.DICE),
                    color = json.optInt("color", CoverItem.MINT),
                    background = json.optInt("background", CoverItem.BLACK),
                    options = List(options.length()) { options.optString(it) }.filter { it.isNotBlank() },
                )
            }
            "group" -> {
                val array = json.optJSONArray("items") ?: JSONArray()
                CoverItem.Group(
                    id = id,
                    name = name,
                    // A group never holds another group; unreadable entries are dropped (a collage step is fine).
                    items = (0 until array.length()).mapNotNull { at -> array.optJSONObject(at)?.let(::decode) }
                        .filter { it !is CoverItem.Group }
                        .take(CoverItem.Group.MAX_ITEMS),
                    stepSeconds = json.optInt("step", CoverItem.Group.DEFAULT_STEP_SECONDS).coerceIn(1, 3600),
                    videosToEnd = json.optBoolean("videosToEnd", true),
                    shuffle = json.optBoolean("shuffle", false),
                    repeat = json.optBoolean("repeat", true),
                    crossfade = json.optBoolean("crossfade", true),
                )
            }
            else -> null
        }
    }.getOrNull()

    private inline fun <reified E : Enum<E>> enumOr(name: String, fallback: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: fallback
}
