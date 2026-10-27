package com.zlip.app.cover.mirror

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.zlip.app.data.MirrorFit
import kotlin.math.max
import kotlin.math.roundToInt

/** The side along which a filled mirror overflows the cover; swipes along it pan the hidden part into view. */
enum class PanAxis { NONE, HORIZONTAL, VERTICAL }

/**
 * Where the mirror goes on the cover, in pixels: the virtual display's size (the view showing it has the same
 * size, and the cover window crops whatever sticks out) and the view's offset from the cover's top left.
 */
data class MirrorLayout(val display: IntSize, val offset: IntOffset, val panAxis: PanAxis)

/**
 * FIT: the virtual display is exactly the cover, and Android scales the captured screen into it uniformly and
 * centered (black bars), so nothing is cut off.
 * FILL: the virtual display keeps the screen's shape at the smallest size that covers the cover. [pan] picks the
 * part of the overflowing side that shows: -1 its start (top or left), 0 the middle, 1 its end.
 */
fun mirrorLayout(content: IntSize, cover: IntSize, fit: MirrorFit, pan: Float): MirrorLayout {
    if (fit == MirrorFit.FIT || content.width <= 0 || content.height <= 0) {
        return MirrorLayout(cover, IntOffset.Zero, PanAxis.NONE)
    }
    val scale = max(cover.width.toFloat() / content.width, cover.height.toFloat() / content.height)
    // Never smaller than the cover: rounding must not leave an empty line along an edge.
    val display = IntSize(
        width = max(cover.width, (content.width * scale).roundToInt()),
        height = max(cover.height, (content.height * scale).roundToInt()),
    )
    val overflowX = display.width - cover.width
    val overflowY = display.height - cover.height
    val axis = when {
        overflowY > overflowX -> PanAxis.VERTICAL
        overflowX > 0 -> PanAxis.HORIZONTAL
        else -> PanAxis.NONE
    }
    // 0 shows the start edge, 1 the end edge; the side that does not pan stays centered.
    val position = (pan.coerceIn(-1f, 1f) + 1f) / 2f
    val x = if (axis == PanAxis.HORIZONTAL) position else 0.5f
    val y = if (axis == PanAxis.VERTICAL) position else 0.5f
    return MirrorLayout(display, IntOffset(-(overflowX * x).roundToInt(), -(overflowY * y).roundToInt()), axis)
}
