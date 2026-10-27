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
        fun ballRadius(width: Float, height: Float): Float = min(width, height) * 0.47f
        fun bottleLength(width: Float, height: Float): Float = min(width, height) * 0.74f
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
            ToyKind.MAGIC_BALL -> drawMagicBall(canvas, cx, cy, ballRadius(width, height), color, reveal = 0f, answer = null, answerAlpha = 0f, minDotPitch = 0f)
            ToyKind.BOTTLE -> {
                drawDial(canvas, cx, cy, dialRadius(width, height), color, lit = -1, glow = 0f)
                canvas.save()
                canvas.rotate(ToyLogic.RESTING_BOTTLE_ANGLE, cx, cy)
                drawBottle(canvas, cx, cy, bottleLength(width, height), color)
                canvas.restore()
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

    // ---- Magic ball ---------------------------------------------------------------------------------

    private var answerKey: String? = null
    private var answerSize = 0f
    private var answerMinPitch = 0f
    private var answerLines: List<String> = emptyList()
    private var answerPitch = 0f
    private var answerTextSize = 0f
    private var answerTop = 0f
    private var answerStep = 0f

    /**
     * The magic ball, [radius] px. [reveal] 0 shows the classic "8" disc, 1 the dark answer window.
     * [answer] (already in capitals) floats up in a triangle of [color] as [answerAlpha] goes to 1: in
     * LED dots when they can be at least [minDotPitch] px apart, otherwise in bold capitals.
     */
    fun drawMagicBall(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        color: Int,
        reveal: Float,
        answer: String?,
        answerAlpha: Float,
        minDotPitch: Float,
    ) {
        if (radius < 1f) return
        fill.shader = RadialGradient(
            cx - radius * 0.38f,
            cy - radius * 0.42f,
            radius * 1.5f,
            intArrayOf(0xFF6A6F7C.toInt(), 0xFF23262E.toInt(), 0xFF07080A.toInt()),
            floatArrayOf(0f, 0.4f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx, cy, radius, fill)
        fill.shader = null
        // A faint rim light keeps the black ball visible on a black screen.
        stroke.color = 0x2EFFFFFF
        stroke.strokeWidth = max(1f, radius * 0.012f)
        canvas.drawCircle(cx, cy, radius - stroke.strokeWidth / 2f, stroke)

        val window = radius * 0.6f
        fill.shader = RadialGradient(
            cx,
            cy,
            window,
            intArrayOf(0xFF1A2556.toInt(), 0xFF0A1028.toInt(), 0xFF020308.toInt()),
            floatArrayOf(0f, 0.65f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx, cy, window, fill)
        fill.shader = null
        stroke.color = 0xFF33363F.toInt()
        stroke.strokeWidth = radius * 0.03f
        canvas.drawCircle(cx, cy, window + stroke.strokeWidth / 2f, stroke)

        val shown = answerAlpha.coerceIn(0f, 1f) * reveal.coerceIn(0f, 1f)
        if (answer != null && shown > 0.01f) drawAnswer(canvas, cx, cy, window, color, answer, shown, minDotPitch)

        if (reveal < 1f) {
            val disc = 1f - reveal.coerceIn(0f, 1f)
            fill.color = faded(0xFFF4F4F2.toInt(), disc)
            canvas.drawCircle(cx, cy, window * 0.98f, fill)
            val pitch = window * 0.13f
            fill.color = faded(INK, disc)
            canvas.drawDotText("8", cx - DotFont.width("8") * pitch / 2f, cy - DotFont.HEIGHT * pitch / 2f, pitch, fill)
        }

        // Gloss over everything, like light on the glass.
        val glossX = cx - radius * 0.42f
        val glossY = cy - radius * 0.56f
        val gloss = radius * 0.3f
        canvas.save()
        canvas.rotate(-32f, glossX, glossY)
        canvas.scale(1f, 0.55f, glossX, glossY)
        fill.shader = RadialGradient(glossX, glossY, gloss, 0x5CFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        canvas.drawCircle(glossX, glossY, gloss, fill)
        fill.shader = null
        canvas.restore()
    }

    /** The upside-down triangle with its answer, floating up from the dark as [alpha] rises. */
    private fun drawAnswer(canvas: Canvas, cx: Float, cy: Float, window: Float, color: Int, answer: String, alpha: Float, minDotPitch: Float) {
        val size = window * 0.93f
        layoutAnswer(answer, size, minDotPitch)
        val scale = 0.84f + 0.16f * alpha
        val centerY = cy + (1f - alpha) * window * 0.2f
        canvas.save()
        canvas.scale(scale, scale, cx, centerY)
        val halfWidth = size * 0.866f
        val top = centerY - size * 0.5f
        path.reset()
        path.moveTo(cx - halfWidth, top)
        path.lineTo(cx + halfWidth, top)
        path.lineTo(cx, top + size * 1.5f)
        path.close()
        rounded.pathEffect = CornerPathEffect(size * 0.14f)
        rounded.style = Paint.Style.FILL
        rounded.color = faded(color or BLACK, alpha)
        canvas.drawPath(path, rounded)
        rounded.style = Paint.Style.STROKE
        rounded.strokeWidth = size * 0.035f
        rounded.color = faded(shade(color, 0.35f), alpha)
        canvas.drawPath(path, rounded)

        val ink = faded(inkOn(color), alpha)
        if (answerPitch > 0f) {
            fill.color = ink
            answerLines.forEachIndexed { index, line ->
                val lineTop = top + answerTop + index * answerStep
                canvas.drawDotText(line, cx - DotFont.width(line) * answerPitch / 2f, lineTop, answerPitch, fill)
            }
        } else {
            text.color = ink
            text.textSize = answerTextSize
            text.letterSpacing = 0.04f
            val cap = answerTextSize * 0.72f
            answerLines.forEachIndexed { index, line ->
                canvas.drawText(line, cx, top + answerTop + index * answerStep + cap, text)
            }
            text.letterSpacing = 0f
        }
        canvas.restore()
    }

    /**
     * Fits [answer] into the triangle of circumradius [size] (top edge 1.73 [size] wide, 1.5 [size]
     * tall): the largest LED pitch (or text size) whose greedy line breaks fit the narrowing rows, then
     * the block is lowered towards the triangle's centre as far as it still fits. Cached per answer.
     */
    private fun layoutAnswer(answer: String, size: Float, minDotPitch: Float) {
        if (answer == answerKey && size == answerSize && minDotPitch == answerMinPitch) return
        answerKey = answer
        answerSize = size
        answerMinPitch = minDotPitch
        val words = answer.split(' ').filter { it.isNotEmpty() }
        val halfWidth = size * 0.866f
        val height = size * 1.5f
        val margin = height * 0.07f

        if (minDotPitch > 0f && ledSupports(answer)) {
            var pitch = height * 0.11f
            while (pitch >= minDotPitch) {
                val glyph = DotFont.HEIGHT * pitch
                val step = (DotFont.HEIGHT + 2) * pitch
                val lines = wrapInTriangle(words, halfWidth, height, margin, glyph, step) { DotFont.width(it) * pitch }
                if (lines != null) {
                    answerLines = lines
                    answerPitch = pitch
                    answerStep = step
                    answerTop = lowerBlock(lines, halfWidth, height, margin, glyph, step) { DotFont.width(it) * pitch }
                    return
                }
                pitch *= 0.92f
            }
        }

        answerPitch = 0f
        text.letterSpacing = 0.04f
        var textSize = height * 0.16f
        val smallest = max(height * 0.06f, 6f)
        while (true) {
            text.textSize = textSize
            val glyph = textSize * 0.72f
            val step = textSize * 1.12f
            val lines = wrapInTriangle(words, halfWidth, height, margin, glyph, step) { text.measureText(it) }
            if (lines != null || textSize <= smallest) {
                // At the smallest size every word gets its own line; the triangle clips nothing visible.
                answerLines = lines ?: words
                answerTextSize = textSize
                answerStep = step
                answerTop = if (lines != null) lowerBlock(lines, halfWidth, height, margin, glyph, step) { text.measureText(it) } else margin
                break
            }
            textSize = max(textSize * 0.92f, smallest)
        }
        text.letterSpacing = 0f
    }

    /** Usable width of the triangle's row whose lowest point is [depth] below the top edge. */
    private fun rowWidth(halfWidth: Float, height: Float, depth: Float): Float = 2f * halfWidth * (1f - depth / height) * 0.84f

    private inline fun wrapInTriangle(
        words: List<String>,
        halfWidth: Float,
        height: Float,
        margin: Float,
        glyph: Float,
        step: Float,
        measure: (String) -> Float,
    ): List<String>? {
        val lines = ArrayList<String>()
        var current = ""
        var index = 0
        while (index < words.size) {
            val depth = margin + lines.size * step + glyph
            if (depth > height * 0.8f) return null
            val candidate = if (current.isEmpty()) words[index] else "$current ${words[index]}"
            when {
                measure(candidate) <= rowWidth(halfWidth, height, depth) -> {
                    current = candidate
                    index++
                }
                current.isEmpty() -> return null
                else -> {
                    lines += current
                    current = ""
                }
            }
        }
        if (current.isNotEmpty()) lines += current
        return lines
    }

    /** The lowest top offset (centred on the triangle's middle at best) at which every line still fits. */
    private inline fun lowerBlock(lines: List<String>, halfWidth: Float, height: Float, margin: Float, glyph: Float, step: Float, measure: (String) -> Float): Float {
        val block = (lines.size - 1) * step + glyph
        var top = max(margin, height * 0.36f - block / 2f)
        while (top > margin) {
            val fits = lines.indices.all { measure(lines[it]) <= rowWidth(halfWidth, height, top + it * step + glyph) }
            if (fits) return top
            top -= height * 0.02f
        }
        return margin
    }

    // ---- Spin the bottle ----------------------------------------------------------------------------

    /** A glass bottle [length] px long, neck up, centred on ([cx], [cy]); rotate the canvas to aim it. */
    fun drawBottle(canvas: Canvas, cx: Float, cy: Float, length: Float, color: Int) {
        if (length < 1f) return
        val l = length
        val body = l * 0.155f
        val neck = l * 0.055f
        val bottom = cy + l * 0.5f
        val corner = l * 0.07f
        val shoulder = cy - l * 0.02f
        val neckStart = cy - l * 0.25f
        val lip = cy - l * 0.45f
        path.reset()
        path.moveTo(cx - body, bottom - corner)
        path.lineTo(cx - body, shoulder)
        path.cubicTo(cx - body, cy - l * 0.15f, cx - neck, cy - l * 0.14f, cx - neck, neckStart)
        path.lineTo(cx - neck, lip)
        path.lineTo(cx + neck, lip)
        path.lineTo(cx + neck, neckStart)
        path.cubicTo(cx + neck, cy - l * 0.14f, cx + body, cy - l * 0.15f, cx + body, shoulder)
        path.lineTo(cx + body, bottom - corner)
        path.quadTo(cx + body, bottom, cx + body - corner, bottom)
        path.lineTo(cx - body + corner, bottom)
        path.quadTo(cx - body, bottom, cx - body, bottom - corner)
        path.close()
        // Glass: dark edges, a lit band left of the middle.
        fill.shader = LinearGradient(
            cx - body,
            0f,
            cx + body,
            0f,
            intArrayOf(shade(color, 0.38f), tint(color, 0.12f), color, shade(color, 0.48f)),
            floatArrayOf(0f, 0.32f, 0.62f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawPath(path, fill)
        fill.shader = null
        // Label and lip.
        fill.color = faded(tint(color, 0.62f), 0.92f)
        rect.set(cx - body, cy + l * 0.1f, cx + body, cy + l * 0.29f)
        canvas.drawRect(rect, fill)
        fill.color = shade(color, 0.2f)
        rect.set(cx - neck * 1.25f, lip - l * 0.035f, cx + neck * 1.25f, lip + l * 0.01f)
        canvas.drawRoundRect(rect, l * 0.012f, l * 0.012f, fill)
        // Highlights.
        stroke.color = 0x5EFFFFFF
        stroke.strokeWidth = l * 0.04f
        canvas.drawLine(cx - body * 0.55f, cy + l * 0.03f, cx - body * 0.55f, bottom - l * 0.09f, stroke)
        stroke.strokeWidth = l * 0.018f
        canvas.drawLine(cx - neck * 0.42f, lip + l * 0.04f, cx - neck * 0.42f, neckStart - l * 0.01f, stroke)
    }

    /**
     * The ring of dots around the bottle. [lit] (0 until [ToyLogic.DIAL_TICKS], or -1) is the dot the
     * neck points at, lit with a halo as [glow] (0..1) rises.
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
}

/** Dots across the counter's round matrix (Nothing's Glyph Matrix is 25 x 25). */
private const val COUNTER_GRID = 25
