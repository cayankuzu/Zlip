package com.zlip.app.data

import com.zlip.app.data.CountdownMath.DAY
import com.zlip.app.data.CountdownMath.HOUR
import com.zlip.app.data.CountdownMath.MINUTE
import com.zlip.app.data.CoverItem.Countdown.Repeat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class CountdownMathTest {

    private val target = 1_800_000_000_000L

    @Test
    fun splitsTheTimeLeft() {
        val parts = CountdownMath.parts(target, target - (2 * DAY + 3 * HOUR + 4 * MINUTE + 5_000), countUp = false)
        assertEquals(CountdownMath.Parts(2, 3, 4, 5, reached = false), parts)
    }

    @Test
    fun countdownStopsAtZeroOnceReached() {
        val parts = CountdownMath.parts(target, target + HOUR, countUp = false)
        assertEquals(0, parts.days)
        assertEquals(0, parts.hours)
        assertTrue(parts.reached)
    }

    @Test
    fun countUpShowsElapsedTimeAndZeroBeforeStart() {
        assertEquals(40, CountdownMath.parts(target, target + 40 * DAY + HOUR, countUp = true).days)
        val early = CountdownMath.parts(target, target - DAY, countUp = true)
        assertEquals(0, early.days)
        assertFalse(early.reached)
    }

    @Test
    fun coarseLabelUsesDaysThenHoursThenNow() {
        assertEquals(CountdownMath.Coarse.Days(2), CountdownMath.coarse(target, target - (2 * DAY + HOUR), countUp = false))
        assertEquals(CountdownMath.Coarse.Hours(5), CountdownMath.coarse(target, target - (5 * HOUR + MINUTE), countUp = false))
        assertEquals(CountdownMath.Coarse.Now, CountdownMath.coarse(target, target - MINUTE, countUp = false))
        assertEquals(CountdownMath.Coarse.Now, CountdownMath.coarse(target, target + MINUTE, countUp = false))
    }

    @Test
    fun nextChangeIsWhenTheCoarseLabelChanges() {
        val now = target - (2 * DAY + 12 * HOUR)
        val next = CountdownMath.nextCoarseChange(target, now, countUp = false)!!
        assertEquals(CountdownMath.Coarse.Days(2), CountdownMath.coarse(target, next - 1, countUp = false))
        assertEquals(CountdownMath.Coarse.Days(1), CountdownMath.coarse(target, next, countUp = false))
    }

    @Test
    fun nextChangeOnTheLastDayIsHourly() {
        val now = target - (5 * HOUR + 30 * MINUTE)
        val next = CountdownMath.nextCoarseChange(target, now, countUp = false)!!
        assertEquals(CountdownMath.Coarse.Hours(5), CountdownMath.coarse(target, next - 1, countUp = false))
        assertEquals(CountdownMath.Coarse.Hours(4), CountdownMath.coarse(target, next, countUp = false))
    }

    @Test
    fun reachedCountdownNeverChangesAgain() {
        assertNull(CountdownMath.nextCoarseChange(target, target + 1, countUp = false))
    }

    @Test
    fun countUpChangesDaily() {
        val now = target + 3 * DAY + HOUR
        assertEquals(target + 4 * DAY, CountdownMath.nextCoarseChange(target, now, countUp = true))
    }

    @Test
    fun countUpBeforeItsStartCountsDownToIt() {
        val early = target - (2 * DAY + HOUR)
        assertEquals(CountdownMath.Coarse.Days(2), CountdownMath.coarse(target, early, countUp = true))
        assertEquals(target - 2 * DAY + 1, CountdownMath.nextCoarseChange(target, early, countUp = true))
        assertEquals(CountdownMath.Coarse.Now, CountdownMath.coarse(target, target - MINUTE, countUp = true))
        assertEquals(CountdownMath.Coarse.Days(0), CountdownMath.coarse(target, target + MINUTE, countUp = true))
    }

    private val zone: ZoneId = ZoneId.of("Europe/Istanbul")

    private fun at(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun countdown(target: Long, repeat: Repeat, countUp: Boolean = false) =
        CoverItem.Countdown(id = "c", name = "", title = "", targetMillis = target, countUp = countUp, repeat = repeat)

    @Test
    fun yearlyCountdownCountsToTheNextReturn() {
        // Born 5 Mar 1990; on 26 Oct 2026 the card counts to 5 Mar 2027.
        val birthday = countdown(at(1990, 3, 5), Repeat.YEARLY)
        val now = at(2026, 10, 26, 14, 35)
        val target = CountdownMath.target(birthday, now, zone)
        assertEquals(at(2027, 3, 5), target)
        assertEquals(CountdownMath.Coarse.Days(129), CountdownMath.coarse(target, now, countUp = false))
        assertEquals(CountdownMath.nextCoarseChange(target, now, countUp = false), CountdownMath.nextCoarseChange(birthday, now, zone))
    }

    @Test
    fun repeatingCountdownStaysTodayAllDayThenMovesOnAtMidnight() {
        val birthday = countdown(at(1990, 10, 26), Repeat.YEARLY)
        val evening = at(2026, 10, 26, 23, 30)
        assertEquals(at(2026, 10, 26), CountdownMath.target(birthday, evening, zone))
        assertEquals(CountdownMath.Coarse.Now, CountdownMath.coarse(CountdownMath.target(birthday, evening, zone), evening, countUp = false))
        // The widget must redraw at midnight, when the card moves on to next year's.
        val midnight = at(2026, 10, 27)
        assertEquals(midnight, CountdownMath.nextCoarseChange(birthday, evening, zone))
        assertEquals(CountdownMath.Coarse.Days(364), CountdownMath.coarse(CountdownMath.target(birthday, midnight, zone), midnight, countUp = false))
    }

    @Test
    fun weeklyCountdownCountsToTheComingWeekday() {
        // Set up on last Saturday; on Monday noon it counts to the coming Saturday, 4 days 12 hours away.
        val weekend = countdown(at(2026, 10, 24), Repeat.WEEKLY)
        val monday = at(2026, 10, 26, 12)
        assertEquals(at(2026, 10, 31), CountdownMath.target(weekend, monday, zone))
        assertEquals(at(2026, 10, 27) + 1, CountdownMath.nextCoarseChange(weekend, monday, zone))
    }

    @Test
    fun onceCountdownStaysReachedAndCountUpNeverRepeats() {
        val past = at(2026, 10, 1)
        val now = at(2026, 10, 26)
        assertEquals(past, CountdownMath.target(countdown(past, Repeat.NONE), now, zone))
        assertNull(CountdownMath.nextCoarseChange(countdown(past, Repeat.NONE), now, zone))
        assertEquals(past, CountdownMath.target(countdown(past, Repeat.YEARLY, countUp = true), now, zone))
    }
}
