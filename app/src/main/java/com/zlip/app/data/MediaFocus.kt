package com.zlip.app.data

import kotlin.math.max

/**
 * How a photo or video sits in its box: scaled to [fit] it, then shown [zoom] times closer, with the part at
 * [focusX] / [focusY] in view on every axis where it is larger than the box (see [MediaFocus]).
 */
data class MediaFraming(
    val fit: MediaFit = MediaFit.FIT,
    val zoom: Float = MediaFocus.MIN_ZOOM,
    val focusX: Float = 0f,
    val focusY: Float = 0f,
) {
    /** Zoomed in or moved off the centre: something "Reset" would undo. */
    val adjusted: Boolean get() = zoom != MediaFocus.MIN_ZOOM || focusX != 0f || focusY != 0f

    /** The same framing with every value in range; a damaged file or a wild gesture never breaks a renderer. */
    fun clamped(): MediaFraming = copy(zoom = MediaFocus.clampZoom(zoom), focusX = MediaFocus.clamp(focusX), focusY = MediaFocus.clamp(focusY))
}

/** Where a picture lands in its box, in the box's units: its leading (left, top) edge relative to the box's, and its size. */
data class MediaPlacement(val left: Float, val top: Float, val width: Float, val height: Float)

/** How [this] item is framed. */
val CoverItem.Media.framing: MediaFraming get() = MediaFraming(fit, zoom, focusX, focusY)

/** [this] item framed as [framing], kept in range. */
fun CoverItem.Media.framed(framing: MediaFraming): CoverItem.Media =
    framing.clamped().let { copy(fit = it.fit, zoom = it.zoom, focusX = it.focusX, focusY = it.focusY) }

/**
 * Where a photo or video lands in its box: scaled to [MediaFit.FIT] (whole picture) or [MediaFit.FILL] (box
 * covered), times [CoverItem.Media.zoom]. On every axis where the picture is then larger than its box (FILL's
 * cropped axis; any axis once zoomed in far enough), [CoverItem.Media.focusX] and focusY pick the part that
 * stays in view: -1 = left / top edge, 0 = centre, 1 = right / bottom edge. An axis that fits is centred.
 * Shared by the live cover, the thumbnails, the widget's stills and the editor's gestures, so all of them
 * show the same part. Pure, so it is unit-tested.
 */
internal object MediaFocus {
    const val MIN = -1f
    const val MAX = 1f

    /** One TalkBack step ("show more of the top"): four steps go from the centre to an edge. */
    const val STEP = 0.25f

    /** Not zoomed: the picture as its fit shows it. */
    const val MIN_ZOOM = 1f

    /** Closest a picture can be shown, relative to its fit; further than this a cover-sized picture only turns to mush. */
    const val MAX_ZOOM = 4f

    /** One TalkBack zoom step ("zoom in"): four steps go from 1x to [MAX_ZOOM]. */
    const val ZOOM_STEP = 1.415f

    /** A stored or dragged value kept in range; a damaged file or a fast drag never pushes the picture off its box. */
    fun clamp(value: Float): Float = if (value.isNaN()) 0f else value.coerceIn(MIN, MAX)

    /** A stored or pinched zoom kept in range; NaN (a damaged file) means not zoomed. */
    fun clampZoom(value: Float): Float = if (value.isNaN()) MIN_ZOOM else value.coerceIn(MIN_ZOOM, MAX_ZOOM)

    /**
     * The size (width to height) of a picture of [aspect] (width / height) in a [boxWidth] x [boxHeight] box: as
     * large as [fit] allows (FIT: inside the box, FILL: covering it), times [zoom]. 0 to 0 when unknown.
     */
    fun size(aspect: Float, boxWidth: Float, boxHeight: Float, fit: MediaFit, zoom: Float): Pair<Float, Float> {
        if (!(aspect > 0f) || aspect.isInfinite() || !(boxWidth > 0f) || !(boxHeight > 0f)) return 0f to 0f
        // FIT is limited by the side the picture reaches first, FILL by the other one.
        val widthBound = (aspect > boxWidth / boxHeight) != (fit == MediaFit.FILL)
        val z = clampZoom(zoom)
        return if (widthBound) boxWidth * z to boxWidth / aspect * z else boxHeight * aspect * z to boxHeight * z
    }

    /**
     * How far a picture of [aspect] reaches past its [boxWidth] x [boxHeight] box on each axis, in the box's units,
     * when framed with [fit] and [zoom]. An axis above 0 is one the focus moves along; both may be.
     */
    fun overflow(aspect: Float, boxWidth: Float, boxHeight: Float, fit: MediaFit, zoom: Float): Pair<Float, Float> {
        val (width, height) = size(aspect, boxWidth, boxHeight, fit, zoom)
        if (width <= 0f || height <= 0f) return 0f to 0f
        return max(0f, width - boxWidth) to max(0f, height - boxHeight)
    }

    /** Where the picture's leading edge sits relative to the box's: 0 at -1 (edge to edge), -[overflow] at 1. */
    fun offset(overflow: Float, focus: Float): Float = -overflow.coerceAtLeast(0f) * (clamp(focus) + 1f) / 2f

    /**
     * The leading edge of a picture [size] long in a [box] on one axis: placed by [focus] when it is larger, centred
     * when it fits.
     */
    fun leading(size: Float, box: Float, focus: Float): Float = if (size > box) offset(size - box, focus) else (box - size) / 2f

    /** Where a picture of [aspect] lands in a [boxWidth] x [boxHeight] box framed as [framing]; null when unknown. */
    fun place(aspect: Float, boxWidth: Float, boxHeight: Float, framing: MediaFraming): MediaPlacement? {
        val (width, height) = size(aspect, boxWidth, boxHeight, framing.fit, framing.zoom)
        if (width <= 0f || height <= 0f) return null
        return MediaPlacement(leading(width, boxWidth, framing.focusX), leading(height, boxHeight, framing.focusY), width, height)
    }

    /**
     * [focus] after the finger moved [drag] along an axis where the picture reaches [overflow] past its box:
     * the picture follows the finger exactly. An axis without overflow has nothing to move, so it stays.
     */
    fun dragged(focus: Float, drag: Float, overflow: Float): Float =
        if (overflow <= 0f) focus else clamp(focus - 2f * drag / overflow)

    /**
     * [framing] after a pinch: zoomed [zoomChange] times (kept in range) around [pivotX], [pivotY] (the fingers'
     * midpoint, in the box's units from its top left), which also moved by [panX], [panY]. The part of the picture
     * that was under the fingers stays under them, as far as the picture's edges allow. An axis that then fits is
     * centred (focus 0), so zooming all the way out leaves the item as it was before it was zoomed.
     */
    fun zoomedAround(
        framing: MediaFraming,
        aspect: Float,
        boxWidth: Float,
        boxHeight: Float,
        zoomChange: Float,
        pivotX: Float,
        pivotY: Float,
        panX: Float = 0f,
        panY: Float = 0f,
    ): MediaFraming {
        val change = if (zoomChange > 0f && zoomChange.isFinite()) zoomChange else 1f
        val zoom = clampZoom(clampZoom(framing.zoom) * change)
        val before = place(aspect, boxWidth, boxHeight, framing) ?: return framing.copy(zoom = zoom)
        val (width, height) = size(aspect, boxWidth, boxHeight, framing.fit, zoom)
        // The point under the fingers, as a fraction of the picture, before and after.
        val u = (pivotX - before.left) / before.width
        val v = (pivotY - before.top) / before.height
        return framing.copy(
            zoom = zoom,
            focusX = focusFor(pivotX + finite(panX) - u * width, width, boxWidth),
            focusY = focusFor(pivotY + finite(panY) - v * height, height, boxHeight),
        )
    }

    /**
     * The focus that puts the leading edge of a picture [size] long at [leading] in a [box], as near as the picture's
     * edges allow; 0 when the picture fits (it is centred whatever the focus says).
     */
    fun focusFor(leading: Float, size: Float, box: Float): Float {
        val overflow = size - box
        return if (overflow <= 0f || leading.isNaN()) 0f else clamp(-2f * leading / overflow - 1f)
    }

    private fun finite(value: Float): Float = if (value.isFinite()) value else 0f
}
