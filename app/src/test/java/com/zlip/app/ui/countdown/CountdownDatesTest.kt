package com.zlip.app.ui.countdown

import com.zlip.app.data.CoverItem.Countdown.Repeat
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class CountdownDatesTest {

    private val zone: ZoneId = ZoneId.of("Europe/Istanbul")

    private fun millis(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun local(millis: Long): LocalDateTime = Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime()

    /** Monday 26 Oct 2026, 14:35. */
    private val now = millis(2026, 10, 26, 14, 35)

    @Test
    fun newCardIsAWeekAheadAtNine() {
        assertEquals(LocalDateTime.of(2026, 11, 2, 9, 0), local(CountdownDates.defaultTarget(now, zone)))
    }

    @Test
    fun newYearIsTheComingFirstOfJanuary() {
        assertEquals(LocalDateTime.of(2027, 1, 1, 0, 0), local(CountdownDates.nextNewYear(now, zone)))
        // On New Year's Day itself the next one is a year away.
        assertEquals(LocalDateTime.of(2028, 1, 1, 0, 0), local(CountdownDates.nextNewYear(millis(2027, 1, 1, 10), zone)))
    }

    @Test
    fun weekendIsTheComingSaturday() {
        assertEquals(LocalDateTime.of(2026, 10, 31, 0, 0), local(CountdownDates.nextWeekend(now, zone)))
        // Already Saturday: the next one.
        assertEquals(LocalDateTime.of(2026, 11, 7, 0, 0), local(CountdownDates.nextWeekend(millis(2026, 10, 31, 12), zone)))
    }

    @Test
    fun daysSinceStartsAtTodaysMidnight() {
        assertEquals(LocalDateTime.of(2026, 10, 26, 0, 0), local(CountdownDates.startOfToday(now, zone)))
    }

    @Test
    fun pickedDateKeepsTheTimeOfDay() {
        val target = millis(2026, 11, 2, 9, 30)
        val picked = LocalDateTime.of(2026, 12, 24, 0, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        assertEquals(LocalDateTime.of(2026, 12, 24, 9, 30), local(CountdownDates.withPickedDate(target, picked, zone)))
        assertEquals(LocalDateTime.of(2026, 11, 2, 18, 5), local(CountdownDates.withTime(target, 18, 5, zone)))
    }

    @Test
    fun pickerWorksInUtcMidnights() {
        // 1 Nov 00:30 in Istanbul is still 31 Oct in UTC; the picker must still see 1 Nov.
        val target = millis(2026, 11, 1, 0, 30)
        assertEquals(LocalDateTime.of(2026, 11, 1, 0, 0).toInstant(ZoneOffset.UTC).toEpochMilli(), CountdownDates.pickerMillis(target, zone))
    }

    @Test
    fun birthdayInThePastBecomesItsNextReturn() {
        // Born 5 Mar 1990: next birthday 5 Mar 2027. Born 2 Dec: later this year.
        assertEquals(LocalDateTime.of(2027, 3, 5, 0, 0), local(CountdownDates.nextAnnual(millis(1990, 3, 5), now, zone)))
        assertEquals(LocalDateTime.of(2026, 12, 2, 0, 0), local(CountdownDates.nextAnnual(millis(1990, 12, 2), now, zone)))
        // Today counts as the next return; future dates are left alone.
        assertEquals(LocalDateTime.of(2026, 10, 26, 0, 0), local(CountdownDates.nextAnnual(millis(1990, 10, 26), now, zone)))
        assertEquals(millis(2027, 6, 1), CountdownDates.nextAnnual(millis(2027, 6, 1), now, zone))
    }

    @Test
    fun leapDayBirthdayFallsOnThe28thInOtherYears() {
        assertEquals(LocalDateTime.of(2027, 2, 28, 0, 0), local(CountdownDates.nextAnnual(millis(2000, 2, 29), now, zone)))
    }

    @Test
    fun weeklyMomentInThePastBecomesItsNextReturn() {
        // Last Saturday 09:00: the coming one. Sunday: a week minus a day ahead.
        assertEquals(LocalDateTime.of(2026, 10, 31, 9, 0), local(CountdownDates.nextWeekly(millis(2026, 10, 24, 9), now, zone)))
        assertEquals(LocalDateTime.of(2026, 11, 1, 0, 0), local(CountdownDates.nextWeekly(millis(2026, 10, 25), now, zone)))
        // A Monday seven weeks back returns today, even at a time already past; future dates are left alone.
        assertEquals(LocalDateTime.of(2026, 10, 26, 8, 0), local(CountdownDates.nextWeekly(millis(2026, 9, 7, 8), now, zone)))
        assertEquals(millis(2026, 11, 5), CountdownDates.nextWeekly(millis(2026, 11, 5), now, zone))
    }

    @Test
    fun weeklyReturnKeepsTheWallClockTimeAcrossDaylightSaving() {
        // Berlin leaves summer time on 25 Oct 2026: the next Saturday is still at 09:00 local time.
        val berlin = ZoneId.of("Europe/Berlin")
        val saturday = LocalDateTime.of(2026, 10, 24, 9, 0).atZone(berlin).toInstant().toEpochMilli()
        val monday = LocalDateTime.of(2026, 10, 26, 12, 0).atZone(berlin).toInstant().toEpochMilli()
        val next = CountdownDates.nextWeekly(saturday, monday, berlin)
        assertEquals(LocalDateTime.of(2026, 10, 31, 9, 0), Instant.ofEpochMilli(next).atZone(berlin).toLocalDateTime())
    }

    @Test
    fun repeatingReturnLastsTheWholeDayThenMovesOn() {
        val saturday = millis(2026, 10, 31)
        assertEquals(saturday, CountdownDates.nextReturn(saturday, Repeat.WEEKLY, millis(2026, 10, 31, 23, 59), zone))
        assertEquals(LocalDateTime.of(2026, 11, 7, 0, 0), local(CountdownDates.nextReturn(saturday, Repeat.WEEKLY, millis(2026, 11, 1, 0, 1), zone)))
        assertEquals(LocalDateTime.of(2027, 10, 31, 0, 0), local(CountdownDates.nextReturn(saturday, Repeat.YEARLY, millis(2026, 11, 1), zone)))
        assertEquals(saturday, CountdownDates.nextReturn(saturday, Repeat.NONE, millis(2026, 11, 1), zone))
    }
}
