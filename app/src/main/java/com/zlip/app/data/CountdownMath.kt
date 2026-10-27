package com.zlip.app.data

import com.zlip.app.ui.countdown.CountdownDates
import java.time.Instant
import java.time.ZoneId

/** Pure countdown arithmetic shared by the live cover, the widget frames and the editor (unit tested). */
object CountdownMath {
    const val SECOND = 1_000L
    const val MINUTE = 60 * SECOND
    const val HOUR = 60 * MINUTE
    const val DAY = 24 * HOUR

    /** Time left until the target (or elapsed since it, when counting up), split for display. */
    data class Parts(val days: Long, val hours: Int, val minutes: Int, val seconds: Int, val reached: Boolean)

    /**
     * Never negative: a countdown whose moment has passed stays at zero with [Parts.reached] true; a
     * count-up before its start also shows zero.
     */
    fun parts(target: Long, now: Long, countUp: Boolean): Parts {
        val delta = if (countUp) now - target else target - now
        val d = delta.coerceAtLeast(0)
        return Parts(
            days = d / DAY,
            hours = ((d % DAY) / HOUR).toInt(),
            minutes = ((d % HOUR) / MINUTE).toInt(),
            seconds = ((d % MINUTE) / SECOND).toInt(),
            reached = !countUp && delta <= 0,
        )
    }

    /** The coarse label shown where nothing ticks (widget frames): whole days, or hours on the last day. */
    sealed interface Coarse {
        data class Days(val days: Long) : Coarse
        data class Hours(val hours: Int) : Coarse
        data object Now : Coarse
    }

    fun coarse(target: Long, now: Long, countUp: Boolean): Coarse {
        // A count-up that has not started yet counts down to its start, like the live card does.
        if (countUp && now < target) return coarse(target, now, countUp = false)
        val p = parts(target, now, countUp)
        return when {
            p.reached -> Coarse.Now
            p.days > 0 || countUp -> Coarse.Days(p.days)
            p.hours > 0 -> Coarse.Hours(p.hours)
            else -> Coarse.Now
        }
    }

    /** When [coarse] next changes, or null if it never will (a countdown that already reached zero). */
    fun nextCoarseChange(target: Long, now: Long, countUp: Boolean): Long? {
        if (countUp) {
            if (now < target) return nextCoarseChange(target, now, countUp = false) ?: target
            return target + ((now - target) / DAY + 1) * DAY
        }
        val left = target - now
        if (left <= 0) return null
        // On the last day the label counts hours; before that, whole days.
        val unit = if (left < DAY) HOUR else DAY
        // The whole-unit count drops one millisecond after the remaining time reaches a multiple of the unit.
        return target - (left / unit) * unit + 1
    }

    /**
     * The moment [item] counts to at [now]: its own target, or the next return of one that repeats. Cards
     * read this rather than [CoverItem.Countdown.targetMillis].
     */
    fun target(item: CoverItem.Countdown, now: Long, zone: ZoneId): Long =
        if (item.countUp) item.targetMillis else CountdownDates.nextReturn(item.targetMillis, item.repeat, now, zone)

    /**
     * When [item]'s coarse label next changes. A repeating countdown also changes at the midnight that ends
     * its "Today!", when it moves on to the next return; one that does not repeat stays reached for good (null).
     */
    fun nextCoarseChange(item: CoverItem.Countdown, now: Long, zone: ZoneId): Long? {
        val next = nextCoarseChange(target(item, now, zone), now, item.countUp)
        if (next != null || item.countUp || item.repeat == CoverItem.Countdown.Repeat.NONE) return next
        return Instant.ofEpochMilli(now).atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }
}
