package com.zlip.app.data

/**
 * Which part of a photo or video that is cropped to fill its box ([MediaFit.FILL]) stays in view:
 * [CoverItem.Media.focusX] and focusY, -1 = left / top edge, 0 = centre, 1 = right / bottom edge. Shared by
 * the live cover, the widget's stills and the editor's drag, so all three crop the same part. Pure, so it is
 * unit-tested.
 */
internal object MediaFocus {
    const val MIN = -1f
    const val MAX = 1f

    /** One TalkBack step ("show more of the top"): four steps go from the centre to an edge. */
    const val STEP = 0.25f

    /** A stored or dragged value kept in range; a damaged file or a fast drag never pushes the picture off its box. */
    fun clamp(value: Float): Float = if (value.isNaN()) 0f else value.coerceIn(MIN, MAX)

    /**
     * How far a picture of [aspect] (width / height) reaches past a [boxWidth] x [boxHeight] box it covers, on
     * each axis, in the box's units. At most one of the two is above 0: that is the axis the focus moves along.
     */
    fun overflow(aspect: Float, boxWidth: Float, boxHeight: Float): Pair<Float, Float> {
        if (aspect <= 0f || boxWidth <= 0f || boxHeight <= 0f) return 0f to 0f
        return if (aspect > boxWidth / boxHeight) {
            (boxHeight * aspect - boxWidth) to 0f
        } else {
            0f to (boxWidth / aspect - boxHeight)
        }
    }

    /** Where the picture's leading edge sits relative to the box's: 0 at -1 (edge to edge), -[overflow] at 1. */
    fun offset(overflow: Float, focus: Float): Float = -overflow.coerceAtLeast(0f) * (clamp(focus) + 1f) / 2f

    /**
     * [focus] after the finger moved [drag] along an axis where the picture reaches [overflow] past its box:
     * the picture follows the finger exactly. An axis without overflow has nothing to move, so it stays.
     */
    fun dragged(focus: Float, drag: Float, overflow: Float): Float =
        if (overflow <= 0f) focus else clamp(focus - 2f * drag / overflow)
}
