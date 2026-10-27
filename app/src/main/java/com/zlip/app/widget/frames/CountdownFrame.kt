package com.zlip.app.widget.frames

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.icu.text.DisplayContext
import android.icu.text.MeasureFormat
import android.icu.text.RelativeDateTimeFormatter
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import android.icu.util.ULocale
import android.text.TextPaint
import android.text.TextUtils
import com.zlip.app.data.CountdownMath
import com.zlip.app.data.CoverItem
import com.zlip.app.render.CountdownCheckDots
import com.zlip.app.render.CountdownFormat
import com.zlip.app.render.CountdownView
import com.zlip.app.render.DotFont
import com.zlip.app.render.dotPitchFor
import com.zlip.app.render.drawDotText
import com.zlip.app.widget.CoverFrames
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * A still countdown card for the cover widget: the coarse count (N days, N hours on the last day,
 * "Today!") big in the middle, the title above, the unit and the date below. Scales to any box, down to
 * collage cells, dropping lines that would get too small. Frames have no Context, so the words come from
 * ICU and the date formatter, in the app's language ([CountdownFormat.frameLocale]).
 */
internal fun drawCountdownFrame(item: CoverItem.Countdown, canvas: Canvas, box: RectF, nowMillis: Long, use24Hour: Boolean? = null) {
    canvas.save()
    canvas.clipRect(box)
    canvas.drawColor(item.background)
    if (box.width() >= MIN_BOX && box.height() >= MIN_BOX) drawCard(item, canvas, box, nowMillis, use24Hour)
    canvas.restore()
}

/**
 * Everything besides the item that decides how its frame looks at [nowMillis], for the frame cache key:
 * the moment counted to (a repeating countdown moves on), the coarse count, whether a reached countdown
 * is still "today" or already done, the year (dates of other years show it), and the language and time
 * zone the words and dates are written in.
 */
internal fun countdownFrameKey(item: CoverItem.Countdown, nowMillis: Long): String {
    val zone = ZoneId.systemDefault()
    val target = CountdownMath.target(item, nowMillis, zone)
    val coarse = CountdownMath.coarse(target, nowMillis, item.countUp)
    val done = coarse == CountdownMath.Coarse.Now && isDone(item, target, nowMillis, zone)
    val year = Instant.ofEpochMilli(nowMillis).atZone(zone).year
    return "$target|$coarse|$done|$year|${CountdownFormat.frameLocale().toLanguageTag()}|${zone.id}"
}

private fun isDone(item: CoverItem.Countdown, target: Long, now: Long, zone: ZoneId): Boolean =
    CountdownView.of(target, now, item.countUp, zone) is CountdownView.Done

/** The big line ([big] null draws the tick mark), its unit and the date line. */
private class FrameTexts(val big: String?, val unit: String?, val date: String)

private fun frameTexts(item: CoverItem.Countdown, now: Long, locale: Locale, zone: ZoneId, use24Hour: Boolean?): FrameTexts {
    val target = CountdownMath.target(item, now, zone)
    val withYear = !CountdownFormat.sameYear(target, now, zone)
    fun date(weekday: Boolean) = CountdownFormat.moment(target, locale, zone, use24Hour = use24Hour, weekday = weekday, withYear = withYear)
    return when (val coarse = CountdownMath.coarse(target, now, item.countUp)) {
        is CountdownMath.Coarse.Days -> FrameTexts(
            big = CountdownFormat.number(coarse.days, locale),
            unit = unitWord(coarse.days, MeasureUnit.DAY, locale),
            // The weekday only helps within a week; the coarse day count (part of the cache key) decides it.
            date = date(weekday = coarse.days < 7),
        )
        is CountdownMath.Coarse.Hours -> FrameTexts(
            big = CountdownFormat.number(coarse.hours.toLong(), locale),
            unit = unitWord(coarse.hours.toLong(), MeasureUnit.HOUR, locale),
            date = date(weekday = true),
        )
        CountdownMath.Coarse.Now -> if (isDone(item, target, now, zone)) {
            FrameTexts(big = null, unit = null, date = date(weekday = false))
        } else {
            FrameTexts(big = todayWord(locale), unit = null, date = date(weekday = true))
        }
    }
}

/** "days" / "gün": the unit ICU writes next to [count], without the number. */
private fun unitWord(count: Long, unit: MeasureUnit, locale: Locale): String {
    val full = MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.WIDE).format(Measure(count, unit))
    return full.replace(CountdownFormat.number(count, locale), "").trim().ifEmpty { full }
}

/** "Today!" / "Bugün!". */
private fun todayWord(locale: Locale): String {
    val formatter = RelativeDateTimeFormatter.getInstance(
        ULocale.forLocale(locale),
        null,
        RelativeDateTimeFormatter.Style.LONG,
        DisplayContext.CAPITALIZATION_FOR_STANDALONE,
    )
    return formatter.format(RelativeDateTimeFormatter.Direction.THIS, RelativeDateTimeFormatter.AbsoluteUnit.DAY) + "!"
}

private fun drawCard(item: CoverItem.Countdown, canvas: Canvas, box: RectF, now: Long, use24Hour: Boolean?) {
    val locale = CountdownFormat.frameLocale()
    val zone = ZoneId.systemDefault()
    val texts = frameTexts(item, now, locale, zone, use24Hour)
    // Sizes are given for the full 748 x 720 frame and scale with the box.
    val scale = min(box.width() / CoverFrames.WIDTH, box.height() / CoverFrames.HEIGHT)
    val pad = max(PAD * scale, 4f)
    val innerWidth = box.width() - 2 * pad
    val gap = GAP * scale
    val titleSize = max(TITLE * scale, MIN_TEXT)
    // A tiny box keeps just the number; a title would crowd it out.
    val title = item.title.trim().takeIf { it.isNotEmpty() && box.height() >= titleSize * LINE * 4 }
    val unitSize = (UNIT * scale).takeIf { it >= MIN_TEXT && texts.unit != null }
    val dateSize = (DATE * scale).takeIf { it >= MIN_TEXT }

    var used = 0f
    if (title != null) used += titleSize * LINE + gap * 1.5f
    if (unitSize != null) used += unitSize * LINE + gap
    if (dateSize != null) used += dateSize * LINE + gap
    val mainHeight = (box.height() - 2 * pad - used).coerceIn(0f, box.height() * MAIN_SHARE)
    val main = mainBlock(item, texts.big, innerWidth, mainHeight)

    // The whole stack is centred vertically, like the live card.
    var top = box.top + (box.height() - used - main.height) / 2f
    val centerX = box.centerX()
    if (title != null) {
        val paint = textPaint(item.textColor, titleSize, Typeface.create(Typeface.DEFAULT, 600, false))
        drawLine(canvas, TextUtils.ellipsize(title, paint, innerWidth, TextUtils.TruncateAt.END).toString(), paint, centerX, top, titleSize * LINE)
        top += titleSize * LINE + gap * 1.5f
    }
    main.draw(canvas, centerX, top)
    top += main.height
    if (unitSize != null && texts.unit != null) {
        top += gap
        val color = withAlpha(item.textColor, 0xD9)
        if (item.dotMatrix && CountdownFormat.ledReady(texts.unit)) {
            val led = ledBlock(texts.unit, innerWidth, unitSize * LINE, color, unlit = null)
            led.draw(canvas, centerX, top + (unitSize * LINE - led.height) / 2f)
        } else {
            drawLine(canvas, texts.unit, textPaint(color, unitSize, Typeface.create(Typeface.DEFAULT, 500, false)), centerX, top, unitSize * LINE)
        }
        top += unitSize * LINE
    }
    if (dateSize != null) {
        top += gap
        val paint = textPaint(withAlpha(item.textColor, 0x80), dateSize, Typeface.DEFAULT)
        drawLine(canvas, TextUtils.ellipsize(texts.date, paint, innerWidth, TextUtils.TruncateAt.END).toString(), paint, centerX, top, dateSize * LINE)
    }
}

/** Something drawn centred on a horizontal position, [height] px tall from its top. */
private class Block(val height: Float, val draw: (canvas: Canvas, centerX: Float, top: Float) -> Unit)

private fun mainBlock(item: CoverItem.Countdown, big: String?, width: Float, height: Float): Block {
    if (width <= 0f || height <= 0f) return Block(0f) { _, _, _ -> }
    val accent = item.accentColor
    return when {
        big == null -> checkBlock(item.dotMatrix, accent, min(width, height))
        item.dotMatrix && CountdownFormat.ledReady(big) -> ledBlock(big, width, height, accent, unlit = withAlpha(accent, UNLIT_ALPHA))
        else -> boldBlock(big, accent, width, height)
    }
}

/** LED dots on whole-pixel pitch, with the dim unlit grid of a real panel behind them. */
private fun ledBlock(text: String, width: Float, height: Float, color: Int, unlit: Int?): Block {
    val pitch = dotPitchFor(text, width, height)
    val columns = DotFont.width(text)
    return Block(DotFont.HEIGHT * pitch) { canvas, centerX, top ->
        val left = centerX - columns * pitch / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        if (unlit != null) {
            paint.color = unlit
            for (y in 0 until DotFont.HEIGHT) {
                for (x in 0 until columns) canvas.drawCircle(left + (x + 0.5f) * pitch, top + (y + 0.5f) * pitch, pitch * LED_DOT_RADIUS, paint)
            }
        }
        paint.color = color
        canvas.drawDotText(text, left, top, pitch, paint)
    }
}

/** Heavy tabular figures, scaled so the glyphs themselves (not the font's line box) fill the space. */
private fun boldBlock(text: String, color: Int, width: Float, height: Float): Block {
    val paint = textPaint(color, PROBE_SIZE, Typeface.create(Typeface.DEFAULT, 800, false)).apply {
        fontFeatureSettings = "'tnum'"
        letterSpacing = -0.02f
    }
    val bounds = Rect()
    paint.getTextBounds(text, 0, text.length, bounds)
    val fit = min(width / bounds.width().coerceAtLeast(1), height / bounds.height().coerceAtLeast(1)) * FIT_MARGIN
    paint.textSize = PROBE_SIZE * fit
    paint.getTextBounds(text, 0, text.length, bounds)
    return Block(bounds.height().toFloat()) { canvas, centerX, top ->
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText(text, centerX - bounds.exactCenterX(), top - bounds.top, paint)
    }
}

/** The "done" mark: LED dots on their grid, or a drawn tick inside a faint ring. */
private fun checkBlock(dotMatrix: Boolean, color: Int, side: Float): Block = Block(side) { canvas, centerX, top ->
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val left = centerX - side / 2f
    if (dotMatrix) {
        val rows = CountdownCheckDots
        val pitch = (side / rows.size).let { if (it >= 2f) floor(it) else it }
        val origin = (side - pitch * rows.size) / 2f
        for (y in rows.indices) {
            for (x in rows[y].indices) {
                paint.color = if (rows[y][x] == '#') color else withAlpha(color, UNLIT_ALPHA)
                canvas.drawCircle(left + origin + (x + 0.5f) * pitch, top + origin + (y + 0.5f) * pitch, pitch * LED_DOT_RADIUS, paint)
            }
        }
    } else {
        val stroke = side * 0.07f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = stroke
        paint.color = withAlpha(color, 0x48)
        canvas.drawCircle(centerX, top + side / 2f, side / 2f - stroke, paint)
        paint.color = color
        paint.strokeWidth = stroke * 1.4f
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        val tick = Path().apply {
            moveTo(left + side * 0.30f, top + side * 0.52f)
            lineTo(left + side * 0.44f, top + side * 0.66f)
            lineTo(left + side * 0.71f, top + side * 0.37f)
        }
        canvas.drawPath(tick, paint)
    }
}

/** One line of text centred in a [lineHeight] tall slot starting at [top]. */
private fun drawLine(canvas: Canvas, text: String, paint: TextPaint, centerX: Float, top: Float, lineHeight: Float) {
    val metrics = paint.fontMetrics
    val baseline = top + lineHeight / 2f - (metrics.ascent + metrics.descent) / 2f
    canvas.drawText(text, centerX, baseline, paint)
}

private fun textPaint(color: Int, size: Float, typeface: Typeface) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color
    textSize = size
    this.typeface = typeface
    textAlign = Paint.Align.CENTER
}

private fun withAlpha(argb: Int, alpha: Int): Int = (argb and 0x00FFFFFF) or (alpha shl 24)

private const val MIN_BOX = 24f
private const val MIN_TEXT = 11f
private const val PAD = 38f
private const val GAP = 13f
private const val TITLE = 44f
private const val UNIT = 36f
private const val DATE = 28f
private const val LINE = 1.2f
private const val MAIN_SHARE = 0.54f
private const val PROBE_SIZE = 100f
private const val FIT_MARGIN = 0.97f
private const val UNLIT_ALPHA = 0x22

/** Same dot size as the LED font's own drawing. */
private const val LED_DOT_RADIUS = 0.38f
