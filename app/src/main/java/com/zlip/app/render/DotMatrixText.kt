package com.zlip.app.render

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.floor
import kotlin.math.min

/**
 * A 7-dot-high LED sign font (like a dot-matrix display or Nothing's Glyph Matrix). Upper case Latin
 * and Turkish letters, digits and common punctuation; lower case is drawn as upper case and unknown
 * characters as '?'. Glyphs have their own widths (narrow punctuation), one dot apart.
 */
object DotFont {
    const val HEIGHT = 7

    private val glyphs: Map<Char, Array<String>> = mapOf(
        '0' to arrayOf(".###.", "#...#", "#..##", "#.#.#", "##..#", "#...#", ".###."),
        '1' to arrayOf("..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###."),
        '2' to arrayOf(".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####"),
        '3' to arrayOf("#####", "...#.", "..#..", "...#.", "....#", "#...#", ".###."),
        '4' to arrayOf("...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#."),
        '5' to arrayOf("#####", "#....", "####.", "....#", "....#", "#...#", ".###."),
        '6' to arrayOf("..##.", ".#...", "#....", "####.", "#...#", "#...#", ".###."),
        '7' to arrayOf("#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#..."),
        '8' to arrayOf(".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###."),
        '9' to arrayOf(".###.", "#...#", "#...#", ".####", "....#", "...#.", ".##.."),
        'A' to arrayOf(".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"),
        'B' to arrayOf("####.", "#...#", "#...#", "####.", "#...#", "#...#", "####."),
        'C' to arrayOf(".###.", "#...#", "#....", "#....", "#....", "#...#", ".###."),
        'D' to arrayOf("###..", "#..#.", "#...#", "#...#", "#...#", "#..#.", "###.."),
        'E' to arrayOf("#####", "#....", "#....", "####.", "#....", "#....", "#####"),
        'F' to arrayOf("#####", "#....", "#....", "####.", "#....", "#....", "#...."),
        'G' to arrayOf(".###.", "#...#", "#....", "#.###", "#...#", "#...#", ".####"),
        'H' to arrayOf("#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"),
        'I' to arrayOf(".###.", "..#..", "..#..", "..#..", "..#..", "..#..", ".###."),
        'J' to arrayOf("..###", "...#.", "...#.", "...#.", "...#.", "#..#.", ".##.."),
        'K' to arrayOf("#...#", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#"),
        'L' to arrayOf("#....", "#....", "#....", "#....", "#....", "#....", "#####"),
        'M' to arrayOf("#...#", "##.##", "#.#.#", "#.#.#", "#...#", "#...#", "#...#"),
        'N' to arrayOf("#...#", "#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#"),
        'O' to arrayOf(".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."),
        'P' to arrayOf("####.", "#...#", "#...#", "####.", "#....", "#....", "#...."),
        'Q' to arrayOf(".###.", "#...#", "#...#", "#...#", "#.#.#", "#..#.", ".##.#"),
        'R' to arrayOf("####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#"),
        'S' to arrayOf(".####", "#....", "#....", ".###.", "....#", "....#", "####."),
        'T' to arrayOf("#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#.."),
        'U' to arrayOf("#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."),
        'V' to arrayOf("#...#", "#...#", "#...#", "#...#", "#...#", ".#.#.", "..#.."),
        'W' to arrayOf("#...#", "#...#", "#...#", "#.#.#", "#.#.#", "#.#.#", ".#.#."),
        'X' to arrayOf("#...#", "#...#", ".#.#.", "..#..", ".#.#.", "#...#", "#...#"),
        'Y' to arrayOf("#...#", "#...#", ".#.#.", "..#..", "..#..", "..#..", "..#.."),
        'Z' to arrayOf("#####", "....#", "...#.", "..#..", ".#...", "#....", "#####"),
        'Ç' to arrayOf(".###.", "#...#", "#....", "#....", "#...#", ".###.", "..#.."),
        'Ğ' to arrayOf(".###.", ".....", ".####", "#....", "#..##", "#...#", ".###."),
        'İ' to arrayOf("..#..", ".....", ".###.", "..#..", "..#..", "..#..", ".###."),
        'Ö' to arrayOf(".#.#.", ".....", ".###.", "#...#", "#...#", "#...#", ".###."),
        'Ş' to arrayOf(".####", "#....", ".###.", "....#", "####.", ".....", "..#.."),
        'Ü' to arrayOf(".#.#.", ".....", "#...#", "#...#", "#...#", "#...#", ".###."),
        ' ' to arrayOf("...", "...", "...", "...", "...", "...", "..."),
        ':' to arrayOf(".", "#", "#", ".", "#", "#", "."),
        '.' to arrayOf(".", ".", ".", ".", ".", ".", "#"),
        ',' to arrayOf("..", "..", "..", "..", "..", ".#", "#."),
        '!' to arrayOf("#", "#", "#", "#", "#", ".", "#"),
        '?' to arrayOf(".###.", "#...#", "....#", "...#.", "..#..", ".....", "..#.."),
        '\'' to arrayOf("#", "#", ".", ".", ".", ".", "."),
        '-' to arrayOf("....", "....", "....", "####", "....", "....", "...."),
        '+' to arrayOf(".....", "..#..", "..#..", "#####", "..#..", "..#..", "....."),
        '/' to arrayOf("....#", "...#.", "...#.", "..#..", ".#...", ".#...", "#...."),
        '%' to arrayOf("##..#", "##.#.", "...#.", "..#..", ".#...", ".#.##", "#..##"),
        '#' to arrayOf(".#.#.", "#####", ".#.#.", ".#.#.", ".#.#.", "#####", ".#.#."),
        '(' to arrayOf(".#", "#.", "#.", "#.", "#.", "#.", ".#"),
        ')' to arrayOf("#.", ".#", ".#", ".#", ".#", ".#", "#."),
        '♥' to arrayOf(".....", ".#.#.", "#####", "#####", ".###.", "..#..", "....."),
        '*' to arrayOf(".....", "#.#.#", ".###.", "#####", ".###.", "#.#.#", "....."),
    )

    fun glyph(c: Char): Array<String> {
        glyphs[c]?.let { return it }
        // Turkish dotless ı and lower case letters use the capital glyphs; anything else becomes '?'.
        val upper = if (c == 'ı') 'I' else if (c == 'i') 'İ' else c.uppercaseChar()
        return glyphs[upper] ?: glyphs.getValue('?')
    }

    /** Width of [text] in dots, with [spacing] empty dots between glyphs. */
    fun width(text: String, spacing: Int = 1): Int =
        if (text.isEmpty()) 0 else text.sumOf { glyph(it)[0].length } + spacing * (text.length - 1)

    /** Calls [action] with the dot coordinates (x right, y down) of every lit dot of [text]. */
    inline fun forEachDot(text: String, spacing: Int = 1, action: (x: Int, y: Int) -> Unit) {
        var originX = 0
        for (c in text) {
            val rows = glyph(c)
            for (y in rows.indices) {
                val row = rows[y]
                for (x in row.indices) if (row[x] == '#') action(originX + x, y)
            }
            originX += rows[0].length + spacing
        }
    }
}

/**
 * Draws [text] as round LED dots, [pitch] px apart, from [topLeft]. With [unlit] the dark dots of the
 * text's grid are drawn too, which reads like a real LED panel.
 */
fun DrawScope.drawDotText(text: String, color: Color, topLeft: Offset, pitch: Float, unlit: Color? = null) {
    val radius = pitch * DOT_RADIUS
    if (unlit != null) {
        val columns = DotFont.width(text)
        for (y in 0 until DotFont.HEIGHT) {
            for (x in 0 until columns) {
                drawCircle(unlit, radius, Offset(topLeft.x + (x + 0.5f) * pitch, topLeft.y + (y + 0.5f) * pitch))
            }
        }
    }
    DotFont.forEachDot(text) { x, y ->
        drawCircle(color, radius, Offset(topLeft.x + (x + 0.5f) * pitch, topLeft.y + (y + 0.5f) * pitch))
    }
}

/** The same LED dots on a plain Android canvas (widget frames). [paint] is used for its color. */
fun android.graphics.Canvas.drawDotText(text: String, left: Float, top: Float, pitch: Float, paint: android.graphics.Paint) {
    val radius = pitch * DOT_RADIUS
    DotFont.forEachDot(text) { x, y -> drawCircle(left + (x + 0.5f) * pitch, top + (y + 0.5f) * pitch, radius, paint) }
}

/** Largest dot pitch (whole pixels, so dots stay crisp) that fits [text] into [width] x [height] px. */
fun dotPitchFor(text: String, width: Float, height: Float, maxPitch: Float = Float.MAX_VALUE): Float {
    val columns = DotFont.width(text).coerceAtLeast(1)
    val pitch = min(min(width / columns, height / DotFont.HEIGHT), maxPitch)
    return if (pitch >= 2f) floor(pitch) else pitch
}

/** [text] as an LED sign, as large as fits the available width (up to [maxPitch] per dot), centred. */
@Composable
fun DotMatrixText(text: String, color: Color, modifier: Modifier = Modifier, maxPitch: Dp = 14.dp, unlit: Color? = null) {
    val density = LocalDensity.current
    BoxWithConstraints(modifier) {
        val widthPx = with(density) { maxWidth.toPx() }
        val pitch = dotPitchFor(text, widthPx, Float.MAX_VALUE, with(density) { maxPitch.toPx() })
        val columns = DotFont.width(text)
        Canvas(
            Modifier
                .width(with(density) { (columns * pitch).toDp() })
                .height(with(density) { (DotFont.HEIGHT * pitch).toDp() }),
        ) {
            drawDotText(text, color, Offset.Zero, pitch, unlit)
        }
    }
}

/** Dot radius as a share of the pitch: round dots with a visible gap, like LEDs. */
private const val DOT_RADIUS = 0.38f
