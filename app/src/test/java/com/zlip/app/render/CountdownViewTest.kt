package com.zlip.app.render

import com.zlip.app.data.CountdownMath
import com.zlip.app.data.CountdownMath.DAY
import com.zlip.app.data.CountdownMath.HOUR
import com.zlip.app.data.CountdownMath.MINUTE
import com.zlip.app.data.CountdownMath.SECOND
import com.zlip.app.data.CoverItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class CountdownViewTest {

    private val zone: ZoneId = ZoneOffset.UTC

    /** 24 Dec 2026, 09:00 UTC. */
    private val target = LocalDateTime.of(2026, 12, 24, 9, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun moreThanADayLeftShowsDays() {
        val view = CountdownView.of(target, target - (12 * DAY + 3 * HOUR), countUp = false, zone)
        assertTrue(view is CountdownView.Days)
        view as CountdownView.Days
        assertEquals(12, view.parts.days)
        assertEquals(3, view.parts.hours)
        assertFalse(view.since)
    }

    @Test
    fun lastDayShowsTheClock() {
        val view = CountdownView.of(target, target - (5 * HOUR + 30 * SECOND), countUp = false, zone)
        assertTrue(view is CountdownView.Clock)
        assertEquals(5, (view as CountdownView.Clock).parts.hours)
    }

    @Test
    fun reachedTheSameDayIsToday() {
        assertEquals(CountdownView.Today, CountdownView.of(target, target, countUp = false, zone))
        assertEquals(CountdownView.Today, CountdownView.of(target, target + 14 * HOUR + 59 * MINUTE, countUp = false, zone))
    }

    @Test
    fun reachedOnAnEarlierDayIsDoneWithCalendarDays() {
        // 25 Dec 00:00 is the next calendar day, even though only 15 hours have passed.
        assertEquals(CountdownView.Done(1), CountdownView.of(target, target + 15 * HOUR, countUp = false, zone))
        assertEquals(CountdownView.Done(10), CountdownView.of(target, target + 10 * DAY, countUp = false, zone))
    }

    @Test
    fun countUpShowsDaysSince() {
        val view = CountdownView.of(target, target + 40 * DAY + HOUR, countUp = true, zone)
        assertEquals(CountdownView.Days(com.zlip.app.data.CountdownMath.parts(target, target + 40 * DAY + HOUR, true), since = true), view)
    }

    @Test
    fun countUpBeforeItsStartCountsDownToIt() {
        val view = CountdownView.of(target, target - 3 * DAY, countUp = true, zone)
        assertTrue(view is CountdownView.Days && !view.since)
        assertTrue(CountdownView.of(target, target - HOUR, countUp = true, zone) is CountdownView.Clock)
    }

    @Test
    fun secondsTickWhenTheDistanceCrossesAWholeSecond() {
        // Counting down: 5.5 s left shows 5 s until 4.999 s are left.
        assertEquals(501, CountdownView.millisToNextSecond(target, target - 5_500))
        assertEquals(1, CountdownView.millisToNextSecond(target, target - 5_000))
        // Counting up: 5.5 s since shows 5 s until 6 s have passed.
        assertEquals(500, CountdownView.millisToNextSecond(target, target + 5_500))
        assertEquals(1_000, CountdownView.millisToNextSecond(target, target + 5_000))
    }

    @Test
    fun withoutSecondsOnScreenTheCardSleepsUntilTheDayCountChanges() {
        // 2 days and 10 minutes left: "2 days" turns into "1 day" in 10 minutes.
        val soon = target - (2 * DAY + 10 * MINUTE)
        assertEquals(10 * MINUTE + 1, CountdownView.millisUntilChange(target, soon, countUp = false, zone = zone, secondsShown = false))
        // ...but never longer than the safety cap, so clock changes are noticed.
        val later = target - (2 * DAY + 12 * HOUR)
        assertEquals(CountdownView.MAX_SLEEP_MS, CountdownView.millisUntilChange(target, later, countUp = false, zone = zone, secondsShown = false))
        assertEquals(501, CountdownView.millisUntilChange(target, target - (2 * DAY + 500), countUp = false, zone = zone, secondsShown = true))
    }

    @Test
    fun aReachedCountdownWakesAtMidnightAtTheLatest() {
        assertEquals(CountdownView.MAX_SLEEP_MS, CountdownView.millisUntilChange(target, target, countUp = false, zone = zone, secondsShown = true))
        // 23:50 the same day: ten minutes to midnight, when "Today!" becomes "Yesterday".
        val lateEvening = target + 14 * HOUR + 50 * MINUTE
        assertEquals(10 * MINUTE, CountdownView.millisUntilChange(target, lateEvening, countUp = false, zone = zone, secondsShown = true))
    }

    @Test
    fun yearlyCountdownSaysTodayAllDayAndIsNeverDone() {
        val born = LocalDateTime.of(1990, 12, 24, 9, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        val birthday = CoverItem.Countdown(id = "c", name = "", title = "", targetMillis = born, repeat = CoverItem.Countdown.Repeat.YEARLY)
        fun viewAt(now: Long) = CountdownView.of(CountdownMath.target(birthday, now, zone), now, countUp = false, zone)
        assertEquals(CountdownView.Today, viewAt(target + 14 * HOUR + 59 * MINUTE))
        // Midnight: instead of "Yesterday", next year's birthday, 364 days and 9 hours away.
        val nextDay = viewAt(target + 15 * HOUR)
        assertTrue(nextDay is CountdownView.Days)
        assertEquals(364, (nextDay as CountdownView.Days).parts.days)
    }

    @Test
    fun templateKeepsTheShapeOfTheText() {
        assertEquals("0:00:00", CountdownFormat.template("5:12:45"))
        assertEquals("000", CountdownFormat.template("365"))
    }
}
