package com.zlip.app.cover.mirror

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.zlip.app.data.MirrorFit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The views a tap on the cover, the inner screen's chips and the small window's button jump to. */
enum class MirrorPreset {
    /** The whole screen, with black bars beside it (or above and below). */
    FIT,

    /** The cover filled edge to edge; the rest of the screen is a swipe away. */
    FILL,

    /** Twice [FILL], for small print. */
    CLOSE,
}

/** The preset a mirror starts with for this setting. */
fun MirrorFit.preset(): MirrorPreset = when (this) {
    MirrorFit.FIT -> MirrorPreset.FIT
    MirrorFit.FILL -> MirrorPreset.FILL
}

/**
 * Which part of the captured screen the cover shows, like a map's viewport: [zoom] relative to [MirrorPreset.FIT]
 * (1 = the whole screen) and [center], the screen point at the middle of the cover as fractions of the screen's
 * width and height, so a rotation or a resize keeps the same spot in view. An axis that does not overflow the cover
 * stays centered and keeps its [center] for when it overflows again (Whole screen and back returns to the same spot).
 */
data class MirrorView(val zoom: Float = 1f, val center: Offset = Offset(0.5f, 0.5f))

/**
 * Where the mirror goes on the cover, in pixels: the SurfaceView's [frame] (the whole captured screen at the view's
 * zoom; the cover window crops whatever sticks out) and its [offset] from the cover's top left, which axes have more
 * to show ([canPanX], [canPanY]), whether it can zoom further, and the virtual display's [buffer], which the
 * compositor scales to the frame.
 */
data class MirrorLayout(
    val frame: IntSize,
    val offset: IntOffset,
    val canPanX: Boolean,
    val canPanY: Boolean,
    val buffer: IntSize,
    val canZoomIn: Boolean = false,
    val canZoomOut: Boolean = false,
) {
    /** Already at the top left: where a page starts, so "back to the top" has nothing to do. */
    val atStart: Boolean get() = (!canPanX || offset.x == 0) && (!canPanY || offset.y == 0)
}

/** The zoom at which the screen just covers the cover; 1 for a screen shaped like the cover. */
fun fillZoom(content: IntSize, cover: IntSize): Float {
    if (!content.isKnown() || !cover.isKnown()) return 1f
    val fill = max(cover.width.toFloat() / content.width, cover.height.toFloat() / content.height)
    return max(1f, fill / fitScale(content, cover))
}

/** The closest a pinch may go: [MAX_ZOOM_OVER_FILL] times the filled view. */
fun maxZoom(content: IntSize, cover: IntSize): Float = fillZoom(content, cover) * MAX_ZOOM_OVER_FILL

fun presetZoom(preset: MirrorPreset, content: IntSize, cover: IntSize): Float = when (preset) {
    MirrorPreset.FIT -> 1f
    MirrorPreset.FILL -> fillZoom(content, cover)
    MirrorPreset.CLOSE -> min(fillZoom(content, cover) * CLOSE_OVER_FILL, maxZoom(content, cover))
}

/** The preset [zoom] shows, if any; null somewhere in between (after a pinch). */
fun presetAt(zoom: Float, content: IntSize, cover: IntSize): MirrorPreset? =
    MirrorPreset.entries.firstOrNull { abs(zoom / presetZoom(it, content, cover) - 1f) < PRESET_TOLERANCE }

/** How a mirror starts: [preset], at the top of a portrait screen (where its content starts), centered otherwise. */
fun startView(preset: MirrorPreset, content: IntSize, cover: IntSize): MirrorView =
    MirrorView(presetZoom(preset, content, cover), Offset(0.5f, if (content.height > content.width) 0f else 0.5f))

/**
 * The mirror on the cover for [view]. The frame keeps the screen's shape; an axis it overflows shows the part around
 * the view's center, never beyond the screen's edge, and an axis it does not overflow is centered. A [content] size
 * that is not known yet counts as the cover's.
 */
fun mirrorLayout(content: IntSize, cover: IntSize, view: MirrorView): MirrorLayout {
    if (!cover.isKnown()) return MirrorLayout(cover, IntOffset.Zero, canPanX = false, canPanY = false, buffer = cover)
    val screen = screenOf(content, cover)
    val limit = maxZoom(screen, cover)
    val zoom = view.zoom.coerceIn(1f, limit)
    val frame = frameSize(screen, cover, zoom)
    return MirrorLayout(
        frame = frame,
        offset = IntOffset(
            axisOffset(view.center.x, frame.width, cover.width),
            axisOffset(view.center.y, frame.height, cover.height),
        ),
        canPanX = frame.width > cover.width,
        canPanY = frame.height > cover.height,
        buffer = bufferSize(screen, cover, zoom),
        canZoomIn = zoom < limit * (1f - PRESET_TOLERANCE),
        canZoomOut = zoom > 1f + PRESET_TOLERANCE,
    )
}

/**
 * [this] moved by a finger's ([dx], [dy]) cover pixels: the screen follows the finger, up to its edges. An axis with
 * nothing hidden does not move.
 */
fun MirrorView.panned(content: IntSize, cover: IntSize, dx: Float, dy: Float): MirrorView {
    if (!cover.isKnown()) return this
    val screen = screenOf(content, cover)
    val frame = frameSize(screen, cover, zoom.coerceIn(1f, maxZoom(screen, cover)))
    fun axis(stored: Float, length: Int, room: Int, delta: Float): Float =
        if (length <= room) stored else shownCenter(shownCenter(stored, length.toFloat(), room) - delta / length, length.toFloat(), room)
    return copy(
        center = Offset(axis(center.x, frame.width, cover.width, dx), axis(center.y, frame.height, cover.height, dy)),
    )
}

/**
 * [this] zoomed by [factor] around [focus] (cover pixels, the fingers' midpoint or a tap): the screen point under it
 * stays under it, as far as the screen's edges allow.
 */
fun MirrorView.zoomed(content: IntSize, cover: IntSize, factor: Float, focus: Offset): MirrorView {
    if (!cover.isKnown() || factor <= 0f) return this
    val screen = screenOf(content, cover)
    val limit = maxZoom(screen, cover)
    val from = zoom.coerceIn(1f, limit)
    val to = (from * factor).coerceIn(1f, limit)
    if (to == from) return if (zoom == from) this else copy(zoom = from)
    val scale = fitScale(screen, cover)
    fun axis(stored: Float, length: Int, room: Int, at: Float): Float {
        val frameFrom = length * scale * from
        val frameTo = length * scale * to
        val overflowsFrom = side(frameFrom, room) > room
        val offsetFrom = if (overflowsFrom) room / 2f - shownCenter(stored, frameFrom, room) * frameFrom else (room - frameFrom) / 2f
        // The screen point under the focus, and the center that puts it back under the focus at the new size.
        val point = (at - offsetFrom) / frameFrom
        val centerTo = (room / 2f - (at - point * frameTo)) / frameTo
        return if (side(frameTo, room) > room) shownCenter(centerTo, frameTo, room) else centerTo.coerceIn(0f, 1f)
    }
    return MirrorView(
        zoom = to,
        center = Offset(axis(center.x, screen.width, cover.width, focus.x), axis(center.y, screen.height, cover.height, focus.y)),
    )
}

/**
 * [this] after the screen or the cover changed size (a rotation): a preset stays that preset (Fill fills again),
 * any other zoom stays within range. The center is already relative, so the same spot stays in view.
 */
fun MirrorView.rescaled(oldContent: IntSize, oldCover: IntSize, content: IntSize, cover: IntSize): MirrorView {
    val screen = screenOf(content, cover)
    val preset = presetAt(zoom, screenOf(oldContent, oldCover), oldCover)
    return copy(zoom = preset?.let { presetZoom(it, screen, cover) } ?: zoom.coerceIn(1f, maxZoom(screen, cover)))
}

/**
 * The virtual display's size at [zoom]: the frame's, but never below the filled frame's (a tap or a pinch up to Fill
 * is sharp before the display has caught up) and, above that, never beyond [MAX_BUFFER_PX] on its longer side or the
 * screen's own size: more pixels only cost memory and GPU time, and the compositor scales the buffer to the frame.
 */
internal fun bufferSize(content: IntSize, cover: IntSize, zoom: Float): IntSize {
    val fit = fitScale(content, cover)
    val fill = fit * fillZoom(content, cover)
    val cap = min(MAX_BUFFER_PX.toFloat() / max(content.width, content.height), 1f)
    val scale = max(min(max(fit * zoom, fill), cap), fill)
    return IntSize(max(1, (content.width * scale).roundToInt()), max(1, (content.height * scale).roundToInt()))
}

/** Screen pixels to cover pixels with the whole screen in view. */
private fun fitScale(content: IntSize, cover: IntSize): Float =
    min(cover.width.toFloat() / content.width, cover.height.toFloat() / content.height)

private fun frameSize(content: IntSize, cover: IntSize, zoom: Float): IntSize {
    val scale = fitScale(content, cover) * zoom
    return IntSize(side(content.width * scale, cover.width), side(content.height * scale, cover.height))
}

/** Within a pixel of the cover counts as the cover: rounding must not leave a hairline along an edge, or a 1 px pan. */
private fun side(exact: Float, cover: Int): Int {
    val rounded = exact.roundToInt()
    return if (abs(rounded - cover) <= 1) cover else max(1, rounded)
}

/** The center an overflowing axis can show: the frame never leaves an empty edge. Centered when nothing overflows. */
private fun shownCenter(center: Float, frame: Float, cover: Int): Float {
    if (frame <= cover) return 0.5f
    val half = cover / 2f / frame
    return center.coerceIn(half, 1f - half)
}

private fun axisOffset(center: Float, frame: Int, cover: Int): Int =
    if (frame <= cover) {
        (cover - frame) / 2
    } else {
        (cover / 2f - shownCenter(center, frame.toFloat(), cover) * frame).roundToInt().coerceIn(cover - frame, 0)
    }

private fun IntSize.isKnown() = width > 0 && height > 0

private fun screenOf(content: IntSize, cover: IntSize) = if (content.isKnown()) content else cover

/** Fill is the start of zooming in, not its end: up to this much closer for small print. */
const val MAX_ZOOM_OVER_FILL = 2.5f

private const val CLOSE_OVER_FILL = 2f

/** Zooms this close (relative) to a preset count as that preset, so float drift never loses the chip's selection. */
private const val PRESET_TOLERANCE = 0.02f

/** The virtual display's longest side: beyond this the extra sharpness is not worth the memory and GPU time. */
internal const val MAX_BUFFER_PX = 2048
