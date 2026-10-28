package com.zlip.app.render

import android.graphics.Canvas
import android.graphics.CornerPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.graphics.ColorUtils
import com.zlip.app.data.ToyKind
import com.zlip.app.data.ToyLogic
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Share of a toy's height used by the toy itself; the band below it holds the hint or the result. */
internal const val TOY_AREA_FRACTION = 0.8f

private const val INK = 0xFF101114.toInt()
private const val WHITE = 0xFFFFFFFF.toInt()
private const val BLACK = 0xFF000000.toInt()

/** Below this radius (px) small details (coin reeding, wheel labels, bulbs) are skipped: icons and thumbnails. */
private const val DETAIL_PX = 56f

/** [argb] darkened towards black by [amount] (0..1). */
private fun shade(argb: Int, amount: Float): Int = ColorUtils.blendARGB(argb, BLACK, amount)

/** [argb] lightened towards white by [amount] (0..1). */
private fun tint(argb: Int, amount: Float): Int = ColorUtils.blendARGB(argb, WHITE, amount)

/** [argb] with its alpha multiplied by [alpha] (0..1). */
private fun faded(argb: Int, alpha: Float): Int =
    ColorUtils.setAlphaComponent(argb, ((argb ushr 24) * alpha.coerceIn(0f, 1f)).toInt())

/** LED symbols of the built-in wheel, in slice order: Yes, No, Maybe, Ask again. */
private val WHEEL_SYMBOLS: List<Array<String>> = listOf(
    arrayOf(".......", "......#", ".....#.", "#...#..", ".#.#...", "..#....", "......."),
    arrayOf(".......", ".#...#.", "..#.#..", "...#...", "..#.#..", ".#...#.", "......."),
    DotFont.glyph('?'),
    // A clockwise arrow: a ring whose head points down on the right, open just ahead of it.
    arrayOf("..###..", ".#...#.", "#...###", "#....#.", "#......", ".#...#.", "..###.."),
)

/**
 * Draws the cover toys on a plain Android canvas, so the live cover (through drawIntoCanvas), the
 * editor's icons and the widget's still frames all look exactly alike. Sizes are px; each toy is
 * centred where it is told. Holds its paints and a small text cache: keep one per view, main thread only.
 */
internal class ToyPainter {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val rounded = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val path = Path()
    private val rect = RectF()

    // ---- Colors and layout shared by the live toys and the still frames ------------------------------

    companion object {
        /** Near-black or white, whichever reads better on [argb]. */
        fun inkOn(argb: Int): Int {
            val opaque = argb or BLACK
            return if (ColorUtils.calculateContrast(INK, opaque) >= ColorUtils.calculateContrast(WHITE, opaque)) INK else WHITE
        }

        /** [color] when it stands out on [background], otherwise the readable ink for that background. */
        fun accentOn(color: Int, background: Int): Int =
            if (ColorUtils.calculateContrast(color or BLACK, background or BLACK) >= 2.2) color or BLACK else inkOn(background)

        /** True when every character of [text] has its own LED glyph (anything else would show as '?'). */
        fun ledSupports(text: String): Boolean {
            val unknown = DotFont.glyph('\u0000')
            return text.isNotBlank() && text.all { it == '?' || DotFont.glyph(it) !== unknown }
        }

        fun dieSide(width: Float, height: Float): Float = min(width, height) * 0.5f
        fun coinRadius(width: Float, height: Float): Float = min(width, height) * 0.36f
        fun dialRadius(width: Float, height: Float): Float = min(width, height) * 0.47f
        fun counterDiameter(width: Float, height: Float): Float = min(width, height) * 0.92f

        /** The wheel leaves room above it for the pointer. */
        fun wheelRadius(width: Float, height: Float): Float = min(width / 2f, height / 2.2f) * 0.94f
        fun wheelCenterY(height: Float, radius: Float): Float = height / 2f + radius * 0.07f
    }

    /**
     * [kind] at rest, centred in the area ([left], [top], [width], [height]) that excludes the band:
     * thumbnails, editor icons and widget frames.
     */
    fun drawResting(canvas: Canvas, kind: ToyKind, color: Int, options: List<String>, left: Float, top: Float, width: Float, height: Float) {
        val cx = left + width / 2f
        val cy = top + height / 2f
        when (kind) {
            ToyKind.DICE -> {
                val side = dieSide(width, height)
                drawShadow(canvas, cx, cy + side * 0.62f, side * 0.95f, 1f)
                drawDie(canvas, cx, cy, side, ToyLogic.RESTING_FACE, color)
            }
            ToyKind.COIN -> {
                val radius = coinRadius(width, height)
                drawShadow(canvas, cx, cy + radius * 1.14f, radius * 1.7f, 1f)
                drawCoin(canvas, cx, cy, radius, color, heads = true)
            }
            ToyKind.WHEEL -> {
                val labels = ToyLogic.customWheelSlices(options)
                val radius = wheelRadius(width, height)
                val wheelY = top + wheelCenterY(height, radius)
                canvas.save()
                canvas.rotate(ToyLogic.restingWheelRotation(labels?.size ?: ToyLogic.BUILT_IN_WHEEL_SLICES), cx, wheelY)
                drawWheel(canvas, cx, wheelY, radius, labels, color)
                canvas.restore()
                drawWheelPointer(canvas, cx, wheelY, radius, 0f)
            }
            ToyKind.COUNTER -> drawCounter(canvas, cx, cy, counterDiameter(width, height), 0, color)
            ToyKind.POMODORO -> drawPomodoro(
                canvas,
                cx,
                cy,
                counterDiameter(width, height),
                ToyLogic.POMODORO_DURATION_MS,
                ToyLogic.POMODORO_DURATION_MS,
                running = false,
                color = color,
            )
            ToyKind.STOPWATCH -> drawStopwatch(
                canvas,
                cx,
                cy,
                counterDiameter(width, height),
                elapsedMs = 0L,
                running = false,
                color = color,
            )
        }
    }

    // ---- Shadow -------------------------------------------------------------------------------------

    /** A soft contact shadow, [width] px wide, centred on ([cx], [cy]); [strength] 0..1 fades it as the toy lifts. */
    fun drawShadow(canvas: Canvas, cx: Float, cy: Float, width: Float, strength: Float) {
        val radius = width / 2f
        if (strength <= 0.01f || radius < 1f) return
        canvas.save()
        canvas.scale(1f, 0.2f, cx, cy)
        fill.shader = RadialGradient(cx, cy, radius, faded(BLACK, 0.5f * strength), 0, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, radius, fill)
        fill.shader = null
        canvas.restore()
    }

    // ---- Dice ---------------------------------------------------------------------------------------

    /** A rounded die of [side] px centred on ([cx], [cy]) showing [face], body in [color]. */
    fun drawDie(canvas: Canvas, cx: Float, cy: Float, side: Float, face: Int, color: Int) {
        if (side < 1f) return
        val half = side / 2f
        val corner = side * 0.21f
        // A darker rim around a softly lit top face reads as a bevelled cube.
        rect.set(cx - half, cy - half, cx + half, cy + half)
        fill.color = edgeOf(color)
        canvas.drawRoundRect(rect, corner, corner, fill)
        val inset = side * 0.05f
        rect.inset(inset, inset)
        fill.shader = LinearGradient(rect.left, rect.top, rect.right, rect.bottom, tint(color, 0.22f), shade(color, 0.04f), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(rect, corner - inset, corner - inset, fill)
        fill.shader = null
        // Pips sit slightly sunk: a dark crescent above each one.
        val pip = inkOn(color)
        val sunk = if (pip == INK) shade(color, 0.42f) else shade(color, 0.55f)
        val spacing = side * 0.255f
        val radius = side * 0.082f
        for ((column, row) in ToyLogic.pips(face)) {
            val x = cx + column * spacing
            val y = cy + row * spacing
            fill.color = sunk
            canvas.drawCircle(x, y - radius * 0.16f, radius, fill)
            fill.color = pip
            canvas.drawCircle(x, y + radius * 0.04f, radius * 0.94f, fill)
        }
    }

    /** The rim color of a die or coin: darker, but never lost against black on dark colors. */
    private fun edgeOf(color: Int): Int =
        if (ColorUtils.calculateLuminance(color or BLACK) < 0.03) tint(color, 0.22f) else shade(color, 0.34f)

    // ---- Coin ---------------------------------------------------------------------------------------

    /**
     * A coin of [radius] px centred on ([cx], [cy]). Flipping squeezes it horizontally ([squeeze] =
     * |cos| of the turn) and shows its edge on the side given by [edge] (sin of the turn).
     * Heads shows a star, tails the value "1", so the sides differ at a glance.
     */
    fun drawCoin(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int, heads: Boolean, squeeze: Float = 1f, edge: Float = 0f) {
        if (radius < 1f) return
        val scaleX = squeeze.coerceIn(0.02f, 1f)
        val depth = radius * 0.14f * edge
        val faceX = cx - depth / 2f
        if (abs(depth) > 0.5f) {
            // The rim between the two faces, seen while the coin is turned.
            fill.color = shade(color, 0.45f)
            val backX = cx + depth / 2f
            rect.set(backX - radius * scaleX, cy - radius, backX + radius * scaleX, cy + radius)
            canvas.drawOval(rect, fill)
            rect.set(min(faceX, backX), cy - radius, max(faceX, backX), cy + radius)
            canvas.drawRect(rect, fill)
        }
        canvas.save()
        canvas.scale(scaleX, 1f, faceX, cy)
        fill.shader = RadialGradient(
            faceX - radius * 0.35f,
            cy - radius * 0.4f,
            radius * 1.55f,
            intArrayOf(tint(color, 0.38f), color, shade(color, 0.28f)),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(faceX, cy, radius, fill)
        fill.shader = null
        // Raised rim and reeding.
        stroke.color = shade(color, 0.24f)
        stroke.strokeWidth = radius * 0.05f
        canvas.drawCircle(faceX, cy, radius * 0.84f, stroke)
        if (radius >= DETAIL_PX) {
            fill.color = shade(color, 0.2f)
            val dots = 40
            for (i in 0 until dots) {
                val angle = i * 2f * PI.toFloat() / dots
                canvas.drawCircle(faceX + cos(angle) * radius * 0.925f, cy + sin(angle) * radius * 0.925f, radius * 0.022f, fill)
            }
        }
        // Embossed emblem: a light copy up-left, the dark one on top.
        val offset = radius * 0.018f
        if (heads) {
            starPath(faceX - offset, cy - offset, radius * 0.42f)
            fill.color = tint(color, 0.5f)
            canvas.drawPath(path, fill)
            starPath(faceX, cy, radius * 0.42f)
            fill.color = shade(color, 0.32f)
            canvas.drawPath(path, fill)
        } else {
            val one = DotFont.glyph('1')
            val pitch = radius * 0.13f
            val left = faceX - one[0].length * pitch / 2f
            val top = cy - one.size * pitch / 2f
            fill.color = tint(color, 0.5f)
            drawDotGlyph(canvas, one, left - offset, top - offset, pitch)
            fill.color = shade(color, 0.32f)
            drawDotGlyph(canvas, one, left, top, pitch)
        }
        canvas.restore()
    }

    private fun starPath(cx: Float, cy: Float, outer: Float) {
        path.reset()
        val inner = outer * 0.43f
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) outer else inner
            val angle = (-90f + i * 36f) * PI.toFloat() / 180f
            val x = cx + cos(angle) * r
            val y = cy + sin(angle) * r
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
    }

    // ---- Dial ---------------------------------------------------------------------------------------

    /**
     * The ring of dots around the stopwatch. [lit] (0 until [ToyLogic.DIAL_TICKS], or -1) is the dot the
     * second hand points at, lit with a halo as [glow] (0..1) rises.
     */
    fun drawDial(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int, lit: Int, glow: Float) {
        if (radius < 1f) return
        val dot = radius * 0.028f
        val ticks = ToyLogic.DIAL_TICKS
        for (i in 0 until ticks) {
            val angle = (i * 360f / ticks - 90f) * PI.toFloat() / 180f
            val major = i % (ticks / 4) == 0
            fill.color = faded(color or BLACK, if (major) 0.45f else 0.22f)
            canvas.drawCircle(cx + cos(angle) * radius, cy + sin(angle) * radius, if (major) dot * 1.3f else dot, fill)
        }
        if (lit !in 0 until ticks || glow <= 0.01f) return
        val angle = (lit * 360f / ticks - 90f) * PI.toFloat() / 180f
        val x = cx + cos(angle) * radius
        val y = cy + sin(angle) * radius
        val halo = dot * 5f
        fill.shader = RadialGradient(x, y, halo, faded(color or BLACK, 0.6f * glow), faded(color, 0f), Shader.TileMode.CLAMP)
        canvas.drawCircle(x, y, halo, fill)
        fill.shader = null
        fill.color = faded(tint(color, 0.35f), glow)
        canvas.drawCircle(x, y, dot * 1.6f, fill)
    }

    // ---- Decision wheel -----------------------------------------------------------------------------

    private var wheelKey: List<String>? = null
    private var wheelTextSize = 0f
    private var wheelFitted: List<String> = emptyList()

    /**
     * The wheel's turning part, upright: slice 0 starts at 12 o'clock and the slices run clockwise
     * (rotate the canvas or layer to turn it). [labels] null draws the built-in Yes / No / Maybe /
     * Ask again as LED symbols. [highlight] (or -1) is the winning slice, brought out as [emphasis] rises.
     */
    fun drawWheel(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        labels: List<String>?,
        color: Int,
        highlight: Int = -1,
        emphasis: Float = 0f,
    ) {
        if (radius < 1f) return
        val count = labels?.size ?: ToyLogic.BUILT_IN_WHEEL_SLICES
        val sweep = 360f / count
        val tints = sliceTints(color or BLACK)
        rect.set(cx - radius, cy - radius, cx + radius, cy + radius)
        for (i in 0 until count) {
            fill.color = tints[ToyLogic.sliceTint(i, count)]
            // A hair of overlap so no seam shows between slices.
            canvas.drawArc(rect, -90f + i * sweep, sweep + 0.6f, true, fill)
        }

        val detailed = radius >= DETAIL_PX
        if (detailed) {
            if (labels != null) fitWheelLabels(labels, radius, sweep)
            for (i in 0 until count) {
                val mid = -90f + (i + 0.5f) * sweep
                val ink = inkOn(tints[ToyLogic.sliceTint(i, count)])
                canvas.save()
                if (labels == null) {
                    // Symbols stand upright, their top towards the rim.
                    val rad = mid * PI.toFloat() / 180f
                    val x = cx + cos(rad) * radius * 0.58f
                    val y = cy + sin(rad) * radius * 0.58f
                    canvas.rotate(mid + 90f, x, y)
                    val symbol = WHEEL_SYMBOLS[i]
                    val pitch = radius * 0.05f
                    fill.color = ink
                    drawDotGlyph(canvas, symbol, x - symbol[0].length * pitch / 2f, y - symbol.size * pitch / 2f, pitch)
                } else {
                    canvas.rotate(mid, cx, cy)
                    text.color = ink
                    text.textSize = wheelTextSize
                    val label = wheelFitted[i]
                    canvas.drawText(label, cx + radius * 0.57f, cy - (text.ascent() + text.descent()) / 2f, text)
                }
                canvas.restore()
            }
        }

        val shown = emphasis.coerceIn(0f, 1f)
        if (highlight in 0 until count && shown > 0.01f) {
            fill.color = faded(BLACK, 0.55f * shown)
            for (i in 0 until count) {
                if (i != highlight) canvas.drawArc(rect, -90f + i * sweep, sweep + 0.6f, true, fill)
            }
        }

        // Cut lines, rim and bulbs.
        val rim = if (ColorUtils.calculateLuminance(color or BLACK) < 0.06) tint(color, 0.3f) else shade(color, 0.5f)
        stroke.color = rim
        stroke.strokeWidth = radius * 0.014f
        for (i in 0 until count) {
            val rad = (-90f + i * sweep) * PI.toFloat() / 180f
            canvas.drawLine(cx, cy, cx + cos(rad) * radius, cy + sin(rad) * radius, stroke)
        }
        stroke.strokeWidth = radius * 0.06f
        canvas.drawCircle(cx, cy, radius - stroke.strokeWidth / 2f, stroke)
        if (detailed) {
            fill.color = 0xE6FFFFFF.toInt()
            for (i in 0 until count) {
                val rad = (-90f + i * sweep) * PI.toFloat() / 180f
                canvas.drawCircle(cx + cos(rad) * radius * 0.97f, cy + sin(rad) * radius * 0.97f, radius * 0.022f, fill)
            }
        }

        if (highlight in 0 until count && shown > 0.01f) {
            path.reset()
            path.moveTo(cx, cy)
            path.arcTo(rect, -90f + highlight * sweep, sweep)
            path.close()
            stroke.color = faded(WHITE, shown)
            stroke.strokeWidth = radius * 0.028f
            canvas.drawPath(path, stroke)
        }

        // Hub.
        fill.color = rim
        canvas.drawCircle(cx, cy, radius * 0.14f, fill)
        fill.color = tint(color, 0.15f)
        canvas.drawCircle(cx, cy, radius * 0.075f, fill)
        fill.color = 0x66FFFFFF
        canvas.drawCircle(cx - radius * 0.025f, cy - radius * 0.025f, radius * 0.022f, fill)
    }

    /** One text size for all labels (so they look like a set), each shortened with "…" to fit its slice. */
    private fun fitWheelLabels(labels: List<String>, radius: Float, sweep: Float) {
        val chord = 2f * radius * 0.57f * sin(sweep / 2f * PI.toFloat() / 180f)
        val size = min(radius * (if (labels.size > 8) 0.1f else 0.12f), chord * 0.62f)
        if (labels == wheelKey && size == wheelTextSize) return
        wheelKey = labels
        wheelTextSize = size
        text.textSize = size
        val room = radius * 0.62f
        wheelFitted = labels.map { TextUtils.ellipsize(it, text, room, TextUtils.TruncateAt.END).toString() }
    }

    /** Tints for the slices: the color, a darker and a lighter one (adjusted at the ends of the scale). */
    private fun sliceTints(color: Int): IntArray {
        val luminance = ColorUtils.calculateLuminance(color)
        return when {
            luminance < 0.05 -> intArrayOf(color, tint(color, 0.18f), tint(color, 0.36f))
            luminance > 0.85 -> intArrayOf(color, shade(color, 0.16f), shade(color, 0.32f))
            else -> intArrayOf(color, shade(color, 0.3f), tint(color, 0.3f))
        }
    }

    private fun drawDotGlyph(canvas: Canvas, rows: Array<String>, left: Float, top: Float, pitch: Float) {
        val radius = pitch * 0.42f
        for (y in rows.indices) {
            val row = rows[y]
            for (x in row.indices) {
                if (row[x] == '#') canvas.drawCircle(left + (x + 0.5f) * pitch, top + (y + 0.5f) * pitch, radius, fill)
            }
        }
    }

    /**
     * The wheel's flapper at 12 o'clock over a wheel of [radius] centred on ([cx], [cy]), bent
     * [deflect] degrees about its pin by the passing pegs.
     */
    fun drawWheelPointer(canvas: Canvas, cx: Float, cy: Float, radius: Float, deflect: Float) {
        if (radius < 1f) return
        val pinY = cy - radius
        val pin = radius * 0.085f
        val tipY = cy - radius + radius * 0.17f
        canvas.save()
        canvas.rotate(deflect, cx, pinY)
        path.reset()
        path.moveTo(cx - pin, pinY)
        path.lineTo(cx, tipY)
        path.lineTo(cx + pin, pinY)
        rect.set(cx - pin, pinY - pin, cx + pin, pinY + pin)
        path.arcTo(rect, 0f, -180f)
        path.close()
        canvas.save()
        canvas.translate(0f, radius * 0.02f)
        fill.color = 0x59000000
        canvas.drawPath(path, fill)
        canvas.restore()
        fill.color = 0xFFF7F7F5.toInt()
        canvas.drawPath(path, fill)
        fill.color = 0xFF2A2C33.toInt()
        canvas.drawCircle(cx, pinY, pin * 0.36f, fill)
        canvas.restore()
    }

    // ---- Counter ------------------------------------------------------------------------------------

    /**
     * The tally as lit dots on a round LED matrix (like a Glyph Matrix), [diameter] px across. The
     * matrix grows (dots get smaller) only when the number would no longer fit.
     */
    fun drawCounter(canvas: Canvas, cx: Float, cy: Float, diameter: Float, count: Int, color: Int) {
        if (diameter < 1f) return
        val label = count.toString()
        val columns = DotFont.width(label)
        var grid = max(COUNTER_GRID, columns + 6)
        if (grid % 2 == 0) grid++
        val pitch = diameter / grid
        val left = cx - diameter / 2f
        val top = cy - diameter / 2f
        val half = grid / 2f
        val dot = pitch * 0.38f
        fill.color = faded(color or BLACK, 0.13f)
        for (y in 0 until grid) {
            for (x in 0 until grid) {
                val dx = x + 0.5f - half
                val dy = y + 0.5f - half
                if (dx * dx + dy * dy <= half * half) {
                    canvas.drawCircle(left + (x + 0.5f) * pitch, top + (y + 0.5f) * pitch, dot, fill)
                }
            }
        }
        fill.color = color or BLACK
        canvas.drawDotText(label, left + (grid - columns) / 2 * pitch, top + (grid - DotFont.HEIGHT) / 2 * pitch, pitch, fill)
    }

    // ---- Pomodoro focus timer -----------------------------------------------------------------------

    /**
     * Circular LED focus timer with a glowing progress ring around the perimeter and MM:SS in dot-matrix
     * digits at the centre.
     */
    fun drawPomodoro(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        diameter: Float,
        remainingMs: Long,
        totalMs: Long,
        running: Boolean,
        color: Int,
    ) {
        if (diameter < 1f) return
        val opaque = color or BLACK
        val ringRadius = diameter * 0.46f
        val ringStroke = max(2f, diameter * 0.042f)

        // Outer track ring
        stroke.color = faded(opaque, 0.18f)
        stroke.strokeWidth = ringStroke
        canvas.drawCircle(cx, cy, ringRadius, stroke)

        // Active progress arc (clockwise from 12 o'clock)
        val fraction = (remainingMs.toFloat() / totalMs.coerceAtLeast(1L)).coerceIn(0f, 1f)
        val sweep = 360f * fraction
        if (sweep > 0.5f) {
            rect.set(cx - ringRadius, cy - ringRadius, cx + ringRadius, cy + ringRadius)
            stroke.color = if (running) tint(opaque, 0.18f) else opaque
            stroke.strokeWidth = ringStroke
            canvas.drawArc(rect, -90f, sweep, false, stroke)

            // Glowing leading tip dot when detailed
            if (diameter >= DETAIL_PX) {
                val tipRad = (-90f + sweep) * PI.toFloat() / 180f
                val tipX = cx + cos(tipRad) * ringRadius
                val tipY = cy + sin(tipRad) * ringRadius
                val halo = ringStroke * 2.2f
                fill.shader = RadialGradient(tipX, tipY, halo, faded(opaque, 0.65f), faded(opaque, 0f), Shader.TileMode.CLAMP)
                canvas.drawCircle(tipX, tipY, halo, fill)
                fill.shader = null
                fill.color = tint(opaque, 0.45f)
                canvas.drawCircle(tipX, tipY, ringStroke * 0.68f, fill)
            }
        }

        // Inner round LED matrix with MM:SS
        val innerDiameter = diameter * 0.80f
        val label = ToyLogic.formatTimerMmSs(remainingMs)
        val columns = DotFont.width(label)
        var grid = max(COUNTER_GRID, columns + 4)
        if (grid % 2 == 0) grid++
        val pitch = innerDiameter / grid
        val left = cx - innerDiameter / 2f
        val top = cy - innerDiameter / 2f
        val half = grid / 2f
        val dot = pitch * 0.38f
        fill.color = faded(opaque, 0.11f)
        for (y in 0 until grid) {
            for (x in 0 until grid) {
                val dx = x + 0.5f - half
                val dy = y + 0.5f - half
                if (dx * dx + dy * dy <= half * half) {
                    canvas.drawCircle(left + (x + 0.5f) * pitch, top + (y + 0.5f) * pitch, dot, fill)
                }
            }
        }
        fill.color = if (running) tint(opaque, 0.15f) else opaque
        canvas.drawDotText(label, left + (grid - columns) / 2f * pitch, top + (grid - DotFont.HEIGHT) / 2f * pitch, pitch, fill)
    }

    // ---- Stopwatch ----------------------------------------------------------------------------------

    /**
     * Precision LED stopwatch with a 24-tick outer dial, sweeping second indicator and MM:SS LED matrix.
     */
    fun drawStopwatch(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        diameter: Float,
        elapsedMs: Long,
        running: Boolean,
        color: Int,
    ) {
        if (diameter < 1f) return
        val opaque = color or BLACK
        val dialR = diameter * 0.46f
        val secAngle = ((elapsedMs % 60_000L) / 60_000f) * 360f
        val litTick = if (running || elapsedMs > 0L) ToyLogic.nearestTick(secAngle) else 0
        drawDial(canvas, cx, cy, dialR, opaque, litTick, if (running) 1f else 0.65f)

        // Stopwatch crown at 12 o'clock
        if (diameter >= DETAIL_PX) {
            val crownW = diameter * 0.11f
            val crownH = diameter * 0.045f
            fill.color = faded(opaque, 0.65f)
            rect.set(cx - crownW / 2f, cy - dialR - crownH * 1.4f, cx + crownW / 2f, cy - dialR - crownH * 0.4f)
            canvas.drawRoundRect(rect, crownH * 0.4f, crownH * 0.4f, fill)
        }

        // Inner LED matrix showing MM:SS
        val innerDiameter = diameter * 0.78f
        val label = ToyLogic.formatStopwatchMmSs(elapsedMs)
        val columns = DotFont.width(label)
        var grid = max(COUNTER_GRID, columns + 4)
        if (grid % 2 == 0) grid++
        val pitch = innerDiameter / grid
        val left = cx - innerDiameter / 2f
        val top = cy - innerDiameter / 2f
        val half = grid / 2f
        val dot = pitch * 0.38f
        fill.color = faded(opaque, 0.11f)
        for (y in 0 until grid) {
            for (x in 0 until grid) {
                val dx = x + 0.5f - half
                val dy = y + 0.5f - half
                if (dx * dx + dy * dy <= half * half) {
                    canvas.drawCircle(left + (x + 0.5f) * pitch, top + (y + 0.5f) * pitch, dot, fill)
                }
            }
        }
        fill.color = if (running) tint(opaque, 0.18f) else opaque
        canvas.drawDotText(label, left + (grid - columns) / 2f * pitch, top + (grid - DotFont.HEIGHT) / 2f * pitch, pitch, fill)
    }
}

/** Dots across the counter's round matrix (Nothing's Glyph Matrix is 25 x 25). */
private const val COUNTER_GRID = 25
