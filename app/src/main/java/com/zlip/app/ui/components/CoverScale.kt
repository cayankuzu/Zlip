package com.zlip.app.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit

/** Width of the cover screen in dp (748 px at density 2.0): what sizes in a cover item are measured against. */
const val COVER_WIDTH_DP: Float = 374f

/**
 * Lays [content] out in cover units: whatever width it is given counts as the 374 dp of the real cover, so
 * text (sp), marquee speeds, paddings and toys keep the cover's proportions in a preview, a thumbnail or the
 * Mini kapak. Without it a 120 sp sign would look a third larger in a 73% preview and spill out of a tile.
 * Only the cover content goes inside; labels and badges drawn over a thumbnail stay at their normal size.
 */
@Composable
fun CoverScale(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val outer = LocalDensity.current
        val factor = maxWidth.value / COVER_WIDTH_DP
        // A box with no width yet (or an unbounded one) has no scale: a density of 0 or infinity would turn sizes
        // into NaN and never let auto-sized text settle on a size.
        val scaled = remember(outer, factor) { if (factor > 0f && factor.isFinite()) ScaledDensity(outer, factor) else outer }
        CompositionLocalProvider(LocalDensity provides scaled, content = content)
    }
}

/**
 * [outer] with every dp [factor] times as many pixels. sp still turns into dp along the system's own
 * (non-linear) font-size curve, so a sign grows with the phone's font size exactly as it does on the cover.
 */
private class ScaledDensity(private val outer: Density, factor: Float) : Density {
    override val density: Float = outer.density * factor
    override val fontScale: Float = outer.fontScale

    override fun TextUnit.toDp(): Dp = with(outer) { this@toDp.toDp() }

    override fun Dp.toSp(): TextUnit = with(outer) { this@toSp.toSp() }
}
