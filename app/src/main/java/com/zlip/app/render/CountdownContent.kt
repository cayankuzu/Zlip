package com.zlip.app.render

import android.icu.text.NumberFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.zlip.app.R
import com.zlip.app.data.CountdownMath
import com.zlip.app.data.CoverItem
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min

/**
 * What a countdown card shows at one moment. Pure (no Android), shared by the live cover, the widget
 * frames and the editor, and unit tested.
 */
internal sealed interface CountdownView {
    /** Whole days as the big number: counting up since a start that has passed ([since]), or down to the moment. */
    data class Days(val parts: CountdownMath.Parts, val since: Boolean) : CountdownView

    /** Less than a day to go: the clock itself is the big number. */
    data class Clock(val parts: CountdownMath.Parts) : CountdownView

    /** A countdown that reached its moment today. */
    data object Today : CountdownView

    /** A countdown that reached its moment on an earlier day, [daysAgo] calendar days ago. */
    data class Done(val daysAgo: Long) : CountdownView

    companion object {
        /** Longest a live card sleeps between checks, so clock or time zone changes are picked up. */
        const val MAX_SLEEP_MS = 30 * CountdownMath.MINUTE

        fun of(target: Long, now: Long, countUp: Boolean, zone: ZoneId): CountdownView {
            if (countUp && now >= target) return Days(CountdownMath.parts(target, now, countUp = true), since = true)
            // A count-up that has not started yet counts down to its start.
            val left = CountdownMath.parts(target, now, countUp = false)
            if (!left.reached) return if (left.days > 0) Days(left, since = false) else Clock(left)
            val daysAgo = ChronoUnit.DAYS.between(localDate(target, zone), localDate(now, zone))
            return if (daysAgo <= 0) Today else Done(daysAgo)
        }

        /**
         * How long a live card can sleep before what it shows changes: the next whole second while seconds
         * are on screen, the next day (or hour) change when they are not, midnight once the moment is reached.
         */
        fun millisUntilChange(target: Long, now: Long, countUp: Boolean, zone: ZoneId, secondsShown: Boolean): Long {
            val wait = when (val view = of(target, now, countUp, zone)) {
                is Clock -> millisToNextSecond(target, now)
                is Days -> if (secondsShown) {
                    millisToNextSecond(target, now)
                } else {
                    (CountdownMath.nextCoarseChange(target, now, countUp = view.since) ?: (now + MAX_SLEEP_MS)) - now
                }
                Today, is Done -> millisToMidnight(now, zone)
            }
            return wait.coerceIn(1, MAX_SLEEP_MS)
        }

        /** Until the shown seconds change: they step whenever the distance to [target] crosses a whole second. */
        fun millisToNextSecond(target: Long, now: Long): Long =
            if (now < target) Math.floorMod(target - now, CountdownMath.SECOND) + 1
            else CountdownMath.SECOND - Math.floorMod(now - target, CountdownMath.SECOND)

        fun millisToMidnight(now: Long, zone: ZoneId): Long =
            localDate(now, zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - now

        private fun localDate(millis: Long, zone: ZoneId) = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    }
}

/** Number, clock and date wording shared by the live card, the widget frames and the editor. */
internal object CountdownFormat {

    /**
     * Dates in the language the app's words resolved to ([translation], e.g. "tr"), keeping the user's
     * regional format when it is the same language, so "until" and the month never mix languages.
     */
    fun locale(preferred: Locale, translation: String): Locale =
        if (preferred.language == translation) preferred else Locale.forLanguageTag(translation)

    /** Frames have no Context: the default locale if the app is translated to it, English otherwise. */
    fun frameLocale(): Locale {
        val default = Locale.getDefault()
        return if (default.language in TRANSLATIONS) default else Locale.ENGLISH
    }

    fun number(value: Long, locale: Locale): String = NumberFormat.getIntegerInstance(locale).format(value)

    /** "5:12:45", or "12:45" in the last hour; [padHours] gives the secondary line's steady "05:12:45". */
    fun clock(parts: CountdownMath.Parts, padHours: Boolean): String = when {
        padHours -> String.format(Locale.ROOT, "%02d:%02d:%02d", parts.hours, parts.minutes, parts.seconds)
        parts.hours > 0 -> String.format(Locale.ROOT, "%d:%02d:%02d", parts.hours, parts.minutes, parts.seconds)
        else -> String.format(Locale.ROOT, "%d:%02d", parts.minutes, parts.seconds)
    }

    /** The widest text [text] can become as its digits change; sizing by it keeps the layout from jumping. */
    fun template(text: String): String = text.map { if (it.isDigit()) '0' else it }.joinToString("")

    /** Short date of the moment ("Thu, 24 Dec", "24 Dec 2027, 09:00") in [locale]'s own order. */
    fun moment(millis: Long, locale: Locale, zone: ZoneId, use24Hour: Boolean?, weekday: Boolean, withYear: Boolean): String {
        val at = Instant.ofEpochMilli(millis).atZone(zone)
        val skeleton = buildString {
            if (weekday) append("EEE")
            append("MMMd")
            if (withYear) append("y")
            if (at.hour != 0 || at.minute != 0) append(hourSkeleton(use24Hour))
        }
        return format(millis, skeleton, locale, zone)
    }

    /** Just the time of day, in the user's 12/24-hour preference ([use24Hour] null: the locale's). */
    fun time(millis: Long, locale: Locale, zone: ZoneId, use24Hour: Boolean?): String =
        format(millis, hourSkeleton(use24Hour), locale, zone)

    /** A long, unambiguous date for the editor ("Thursday, 24 December 2026"). */
    fun longDate(millis: Long, locale: Locale, zone: ZoneId): String = format(millis, "EEEEyMMMMd", locale, zone)

    /** Calendar days between the two moments' dates (positive when [millis] is later). */
    fun dayDistance(millis: Long, now: Long, zone: ZoneId): Long =
        ChronoUnit.DAYS.between(Instant.ofEpochMilli(now).atZone(zone).toLocalDate(), Instant.ofEpochMilli(millis).atZone(zone).toLocalDate())

    fun sameYear(millis: Long, now: Long, zone: ZoneId): Boolean =
        Instant.ofEpochMilli(millis).atZone(zone).year == Instant.ofEpochMilli(now).atZone(zone).year

    /** True when every character has its own LED glyph (titles and translations can hold anything). */
    fun ledReady(text: String): Boolean {
        val unknown = DotFont.glyph('?')
        return text.isNotEmpty() && text.all { it == '?' || DotFont.glyph(it) !== unknown }
    }

    private fun hourSkeleton(use24Hour: Boolean?): String = when (use24Hour) {
        true -> "Hm"
        false -> "hm"
        null -> "jm"
    }

    private fun format(millis: Long, skeleton: String, locale: Locale, zone: ZoneId): String {
        val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton)
        return SimpleDateFormat(pattern, locale).apply { timeZone = TimeZone.getTimeZone(zone) }.format(Date(millis))
    }

    private val TRANSLATIONS = setOf("en", "tr")
}

/** The tick mark of a reached countdown, as LED dots (7 x 7, like a [DotFont] glyph). */
internal val CountdownCheckDots: Array<String> = arrayOf(
    ".......",
    "......#",
    ".....#.",
    "#...#..",
    ".#.#...",
    "..#....",
    ".......",
)

/**
 * A countdown (or count-up) card: the title, one huge number, its unit and small lines with the rest of
 * the time and the date. Ticks once a second only while LIVE and while seconds are on screen (small
 * collage cells only wake when the day count changes); PAUSED and STILL compute a single frame.
 */
@Composable
internal fun CountdownCover(item: CoverItem.Countdown, modifier: Modifier, playback: Playback) {
    val context = LocalContext.current
    val locale = CountdownFormat.locale(LocalConfiguration.current.locales[0], stringResource(R.string.countdown_language))
    val use24Hour = android.text.format.DateFormat.is24HourFormat(context)
    BoxWithConstraints(
        modifier = modifier
            .background(Color(item.background))
            .clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        val metrics = CardMetrics.of(maxWidth, maxHeight)
        val live = playback == Playback.LIVE
        // Live: every second it shows. Paused or resting (the always-on screen saver): still kept current,
        // but only as often as the day or hour changes. Thumbnails never tick.
        val now = rememberCountdownNow(item, ticking = playback != Playback.STILL, secondsShown = live && metrics.secondary != null)
        val zone = ZoneId.systemDefault()
        val target = CountdownMath.target(item, now, zone)
        val view = CountdownView.of(target, now, item.countUp, zone)
        val texts = cardTexts(item, target, view, now, locale, zone, use24Hour)
        val pulse = rememberPulse(active = live && view == CountdownView.Today)
        CountdownCard(item, texts, metrics, maxWidth, maxHeight, pulse.takeIf { view == CountdownView.Today })
    }
}

/** Everything the card writes, already in the user's language. [main] null draws the tick mark. */
private class CardTexts(
    val title: String?,
    val main: String?,
    val unit: String?,
    val secondary: String?,
    val date: String?,
    val description: String,
)

/** Sizes relative to the full cover (about 360 x 346 dp); lines that would get too small are left out. */
private class CardMetrics(
    val pad: Dp,
    val title: Dp?,
    val unit: Dp?,
    val secondary: Dp?,
    val date: Dp?,
    val gap: Dp,
) {
    companion object {
        fun of(width: Dp, height: Dp): CardMetrics {
            val s = min(width / COVER_WIDTH, height / COVER_HEIGHT)
            // Tiny thumbnails keep just the number; the title and unit come back as soon as there is room.
            val roomy = minOf(width, height)
            return CardMetrics(
                pad = (18.dp * s).coerceAtLeast(4.dp),
                title = (21.dp * s).coerceAtLeast(9.dp).takeIf { roomy >= 80.dp },
                unit = (17.dp * s).coerceAtLeast(8.dp).takeIf { roomy >= 56.dp },
                secondary = (15.dp * s).takeIf { it >= MIN_LINE },
                date = (13.dp * s).takeIf { it >= MIN_LINE },
                gap = 6.dp * s,
            )
        }
    }
}

@Composable
private fun cardTexts(
    item: CoverItem.Countdown,
    target: Long,
    view: CountdownView,
    now: Long,
    locale: Locale,
    zone: ZoneId,
    use24Hour: Boolean,
): CardTexts {
    val title = item.title.trim().takeIf { it.isNotEmpty() }
    val nearDate = abs(CountdownFormat.dayDistance(target, now, zone)) < 7
    val otherYear = !CountdownFormat.sameYear(target, now, zone)
    // Date patterns are built only when they change, not on every second's tick.
    val moment = remember(target, locale, zone, use24Hour, nearDate, otherYear) {
        CountdownFormat.moment(target, locale, zone, use24Hour, weekday = nearDate, withYear = otherYear)
    }
    return when (view) {
        is CountdownView.Days -> {
            val count = view.parts.days.toCount()
            val date = stringResource(if (view.since) R.string.countdown_since else R.string.countdown_until, moment)
            CardTexts(
                title = title,
                main = remember(view.parts.days, locale) { CountdownFormat.number(view.parts.days, locale) },
                unit = pluralStringResource(R.plurals.countdown_unit_days, count),
                secondary = CountdownFormat.clock(view.parts, padHours = true),
                date = date,
                description = spoken(title, pluralStringResource(R.plurals.countdown_cd_days, count, count), date),
            )
        }
        is CountdownView.Clock -> {
            val time = remember(target, locale, zone, use24Hour) { CountdownFormat.time(target, locale, zone, use24Hour) }
            val tomorrow = CountdownFormat.dayDistance(target, now, zone) > 0
            val left = stringResource(R.string.countdown_unit_left)
            val date = stringResource(if (tomorrow) R.string.countdown_tomorrow_at else R.string.countdown_today_at, time)
            val hours = pluralStringResource(R.plurals.countdown_cd_hours, view.parts.hours, view.parts.hours)
            val minutes = pluralStringResource(R.plurals.countdown_cd_minutes, view.parts.minutes, view.parts.minutes)
            CardTexts(
                title = title,
                main = CountdownFormat.clock(view.parts, padHours = false),
                unit = left,
                secondary = null,
                date = date,
                description = spoken(title, if (view.parts.hours > 0) "$hours $minutes $left" else "$minutes $left", date),
            )
        }
        CountdownView.Today -> {
            val today = stringResource(R.string.countdown_today)
            val date = remember(target, locale, zone, use24Hour, otherYear) {
                CountdownFormat.moment(target, locale, zone, use24Hour, weekday = true, withYear = otherYear)
            }
            CardTexts(title, today, null, null, date, spoken(title, today, date))
        }
        is CountdownView.Done -> {
            val ago = if (view.daysAgo == 1L) {
                stringResource(R.string.countdown_yesterday)
            } else {
                pluralStringResource(R.plurals.countdown_days_ago, view.daysAgo.toCount(), view.daysAgo.toCount())
            }
            CardTexts(title, null, ago, null, moment, spoken(title, ago, moment))
        }
    }
}

private fun Long.toCount(): Int = coerceIn(0, Int.MAX_VALUE.toLong()).toInt()

private fun spoken(vararg parts: String?): String = parts.filterNotNull().filter { it.isNotBlank() }.joinToString(", ")

@Composable
private fun CountdownCard(
    item: CoverItem.Countdown,
    texts: CardTexts,
    m: CardMetrics,
    width: Dp,
    height: Dp,
    pulse: FloatState?,
) {
    val density = LocalDensity.current
    val textColor = Color(item.textColor)
    val accent = Color(item.accentColor)
    val title = texts.title?.takeIf { m.title != null }
    val titleSize = m.title ?: 0.dp
    val unit = texts.unit?.takeIf { m.unit != null }
    val unitSize = m.unit ?: 0.dp
    val secondary = texts.secondary?.takeIf { m.secondary != null }
    val secondarySize = m.secondary ?: 0.dp
    val date = texts.date?.takeIf { m.date != null }
    val dateSize = m.date ?: 0.dp

    // Whatever the lines leave is the big number's, up to a share of the card so it never crowds them.
    var used = 0.dp
    if (title != null) used += titleSize * LINE + m.gap * 1.5f
    if (unit != null) used += unitSize * LINE + m.gap
    if (secondary != null) used += secondarySize * LINE + m.gap * 0.5f
    if (date != null) used += dateSize * LINE + m.gap
    val mainWidth = (width - m.pad * 2).coerceAtLeast(0.dp)
    val mainHeight = (height - m.pad * 2 - used).coerceIn(0.dp, height * MAIN_SHARE)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(m.pad)
            .clearAndSetSemantics { contentDescription = texts.description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (title != null) {
            Text(
                text = title,
                style = lineStyle(density, titleSize, textColor, FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = mainWidth),
            )
            Spacer(Modifier.height(m.gap * 1.5f))
        }
        Box(
            modifier = if (pulse != null) {
                Modifier.graphicsLayer {
                    val grow = 1f + PULSE_GROW * pulse.floatValue
                    scaleX = grow
                    scaleY = grow
                }
            } else {
                Modifier
            },
            contentAlignment = Alignment.Center,
        ) {
            val main = texts.main
            when {
                main == null -> CheckMark(item.dotMatrix, accent, mainWidth, mainHeight)
                item.dotMatrix && CountdownFormat.ledReady(main) ->
                    LedText(main, accent, accent.copy(alpha = UNLIT_ALPHA), mainWidth, mainHeight, glow = pulse)
                else -> FittedText(main, CountdownFormat.template(main), bigStyle(accent), mainWidth, mainHeight)
            }
        }
        if (unit != null) {
            Spacer(Modifier.height(m.gap))
            if (item.dotMatrix && CountdownFormat.ledReady(unit)) {
                LedText(unit, textColor.copy(alpha = 0.85f), unlit = null, width = mainWidth, height = unitSize * LINE)
            } else {
                Text(unit, style = lineStyle(density, unitSize, textColor.copy(alpha = 0.8f), FontWeight.Medium), maxLines = 1)
            }
        }
        if (secondary != null) {
            Spacer(Modifier.height(m.gap * 0.5f))
            Text(secondary, style = lineStyle(density, secondarySize, textColor.copy(alpha = 0.62f), FontWeight.Medium), maxLines = 1)
        }
        if (date != null) {
            Spacer(Modifier.height(m.gap))
            Text(
                text = date,
                style = lineStyle(density, dateSize, textColor.copy(alpha = 0.5f), FontWeight.Normal),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = mainWidth),
            )
        }
    }
}

/**
 * Small lines are sized in dp, not sp: the cover is a fixed little panel and the layout must fit it.
 * Tabular digits keep ticking numbers from wobbling.
 */
private fun lineStyle(density: Density, size: Dp, color: Color, weight: FontWeight): TextStyle = with(density) {
    TextStyle(
        color = color,
        fontSize = size.toSp(),
        lineHeight = (size * LINE).toSp(),
        fontWeight = weight,
        textAlign = TextAlign.Center,
        fontFeatureSettings = "'tnum'",
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )
}

private fun bigStyle(color: Color) = TextStyle(
    color = color,
    fontWeight = FontWeight.Bold,
    fontFeatureSettings = "'tnum'",
    letterSpacing = (-0.02).em,
    // One em tall with no extra leading, so fitting the box fits the digits themselves.
    lineHeight = 1.em,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
    textAlign = TextAlign.Center,
)

/** [text] as LED dots, as large as fits [width] x [height]; [glow] adds a soft halo around lit dots. */
@Composable
private fun LedText(text: String, color: Color, unlit: Color?, width: Dp, height: Dp, glow: FloatState? = null) {
    val density = LocalDensity.current
    val pitch = with(density) { dotPitchFor(text, width.toPx(), height.toPx()) }
    if (pitch <= 0f) return
    val columns = DotFont.width(text)
    val size = with(density) { DpSize((columns * pitch).toDp(), (DotFont.HEIGHT * pitch).toDp()) }
    Canvas(Modifier.size(size)) {
        val shine = glow?.floatValue ?: 0f
        if (shine > 0f) {
            val halo = color.copy(alpha = GLOW_ALPHA * shine)
            DotFont.forEachDot(text) { x, y -> drawCircle(halo, pitch * GLOW_RADIUS, Offset((x + 0.5f) * pitch, (y + 0.5f) * pitch)) }
        }
        drawDotText(text, color, Offset.Zero, pitch, unlit)
    }
}

/** Plain bold type scaled so [template] (the widest the text gets) fills [width] x [height]. */
@Composable
private fun FittedText(text: String, template: String, style: TextStyle, width: Dp, height: Dp) {
    val measurer = rememberTextMeasurer(cacheSize = 2)
    val density = LocalDensity.current
    val fontSize = remember(template, style, width, height, density) {
        val probe = measurer.measure(template, style.copy(fontSize = PROBE_SIZE), softWrap = false, maxLines = 1, density = density)
        val scale = with(density) {
            min(width.toPx() / probe.size.width.coerceAtLeast(1), height.toPx() / probe.size.height.coerceAtLeast(1))
        }
        PROBE_SIZE * (scale * FIT_MARGIN).coerceAtLeast(0f)
    }
    Text(text = text, style = style.copy(fontSize = fontSize), maxLines = 1, softWrap = false)
}

/** The "done" mark: LED dots on their dim grid, or a drawn tick inside a faint ring. */
@Composable
private fun CheckMark(dotMatrix: Boolean, color: Color, width: Dp, height: Dp) {
    val side = minOf(width, height)
    if (side <= 0.dp) return
    Canvas(Modifier.size(side)) {
        if (dotMatrix) {
            val pitch = (size.minDimension / CHECK_SIZE).let { if (it >= 2f) floor(it) else it }
            val origin = (size.minDimension - pitch * CHECK_SIZE) / 2f
            drawDotRows(CountdownCheckDots, color, color.copy(alpha = UNLIT_ALPHA), Offset(origin, origin), pitch)
        } else {
            val s = size.minDimension
            val stroke = s * 0.07f
            drawCircle(color.copy(alpha = 0.28f), radius = s / 2f - stroke, style = Stroke(stroke))
            val tick = Path().apply {
                moveTo(s * 0.30f, s * 0.52f)
                lineTo(s * 0.44f, s * 0.66f)
                lineTo(s * 0.71f, s * 0.37f)
            }
            drawPath(tick, color, style = Stroke(width = stroke * 1.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

private fun DrawScope.drawDotRows(rows: Array<String>, color: Color, unlit: Color, topLeft: Offset, pitch: Float) {
    val radius = pitch * LED_DOT_RADIUS
    for (y in rows.indices) {
        for (x in rows[y].indices) {
            val center = Offset(topLeft.x + (x + 0.5f) * pitch, topLeft.y + (y + 0.5f) * pitch)
            drawCircle(if (rows[y][x] == '#') color else unlit, radius, center)
        }
    }
}

/**
 * The time the card shows. LIVE wakes exactly when the display would change (aligned to the target's
 * seconds, not the wall clock's); PAUSED and STILL read the clock once.
 */
@Composable
private fun rememberCountdownNow(item: CoverItem.Countdown, ticking: Boolean, secondsShown: Boolean): Long {
    val now by produceState(System.currentTimeMillis(), item.targetMillis, item.countUp, item.repeat, ticking, secondsShown) {
        value = System.currentTimeMillis()
        if (!ticking) return@produceState
        while (true) {
            val clock = System.currentTimeMillis()
            val zone = ZoneId.systemDefault()
            // Looked up on every wake: a repeating countdown moves on to its next return at midnight.
            val change = CountdownView.millisUntilChange(CountdownMath.target(item, clock, zone), clock, item.countUp, zone, secondsShown)
            val wait = if (secondsShown) change else change.coerceAtLeast(CountdownMath.MINUTE)
            delay(wait + TICK_SLACK_MS)
            // Waits for a real frame: while the card is not drawn (frame clock paused) the loop sleeps here.
            withFrameNanos { }
            value = System.currentTimeMillis()
        }
    }
    return now
}

/**
 * A gentle celebratory breath (0..1) for "Today!": a few slow pulses, then a long rest, so the panel is
 * not redrawn all day. Steps at [LocalAnimationFps]; nothing runs unless [active].
 */
@Composable
private fun rememberPulse(active: Boolean): FloatState {
    val pulse = remember { mutableFloatStateOf(0f) }
    val fps = LocalAnimationFps.current
    LaunchedEffect(active, fps) {
        pulse.floatValue = 0f
        if (!active) return@LaunchedEffect
        val frameMs = 1_000L / fps.coerceIn(1, 120)
        while (true) {
            val start = withFrameNanos { it }
            var elapsed = 0L
            while (elapsed < PULSE_BURST_MS) {
                delay((frameMs - FRAME_MS).coerceAtLeast(0))
                elapsed = withFrameNanos { (it - start) / 1_000_000 }
                pulse.floatValue = ((1 - cos(2 * PI * elapsed / PULSE_PERIOD_MS)) / 2).toFloat()
            }
            pulse.floatValue = 0f
            delay(PULSE_REST_MS)
        }
    }
    return pulse
}

private val COVER_WIDTH = 360.dp
private val COVER_HEIGHT = 346.dp
private val MIN_LINE = 9.dp
private val PROBE_SIZE = 100.sp

/** Line height as a multiple of the font size. */
private const val LINE = 1.2f

/** The big number takes at most this share of the card's height. */
private const val MAIN_SHARE = 0.54f
private const val FIT_MARGIN = 0.97f
private const val UNLIT_ALPHA = 0.13f
private const val GLOW_ALPHA = 0.32f
private const val GLOW_RADIUS = 0.62f

/** Same dot size as [drawDotText], so the tick mark matches the digits. */
private const val LED_DOT_RADIUS = 0.38f
private const val CHECK_SIZE = 7
private const val TICK_SLACK_MS = 4L
private const val FRAME_MS = 16L
private const val PULSE_PERIOD_MS = 2_400L
private const val PULSE_BURST_MS = 3 * PULSE_PERIOD_MS
private const val PULSE_REST_MS = 12_000L
private const val PULSE_GROW = 0.05f
