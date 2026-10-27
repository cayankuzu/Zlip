package com.zlip.app.ui.countdown

import com.zlip.app.data.CoverItem
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Date math behind the countdown editor's pickers and quick presets, and the next return of a repeating
 * countdown, which every card reads. Pure java.time, unit tested.
 */
internal object CountdownDates {

    /** A new card: a week from today at 09:00. */
    fun defaultTarget(now: Long, zone: ZoneId): Long = at(date(now, zone).plusDays(7), LocalTime.of(9, 0), zone)

    /** The next 1 January, 00:00. */
    fun nextNewYear(now: Long, zone: ZoneId): Long = at(date(now, zone).withDayOfYear(1).plusYears(1), LocalTime.MIDNIGHT, zone)

    /** The coming Saturday, 00:00 (a week ahead when it is already the weekend). */
    fun nextWeekend(now: Long, zone: ZoneId): Long =
        at(date(now, zone).with(TemporalAdjusters.next(DayOfWeek.SATURDAY)), LocalTime.MIDNIGHT, zone)

    /** Today, 00:00: where a "days since" count starts. */
    fun startOfToday(now: Long, zone: ZoneId): Long = at(date(now, zone), LocalTime.MIDNIGHT, zone)

    /** [target] with its time of day set to [hour]:[minute], same date. */
    fun withTime(target: Long, hour: Int, minute: Int, zone: ZoneId): Long = at(date(target, zone), LocalTime.of(hour, minute), zone)

    /** [target] moved to the date the date picker returned ([pickerMillis]), keeping its time of day. */
    fun withPickedDate(target: Long, pickerMillis: Long, zone: ZoneId): Long =
        at(Instant.ofEpochMilli(pickerMillis).atZone(ZoneOffset.UTC).toLocalDate(), time(target, zone), zone)

    /** The date picker works in UTC midnights: [target]'s local date in that form. */
    fun pickerMillis(target: Long, zone: ZoneId): Long = date(target, zone).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    /**
     * Birthdays and anniversaries: a date already past (say, the day someone was born) becomes its next
     * yearly return, today included. 29 February falls on the 28th in other years.
     */
    fun nextAnnual(target: Long, now: Long, zone: ZoneId): Long {
        val today = date(now, zone)
        val picked = date(target, zone)
        if (!picked.isBefore(today)) return target
        var next = picked.withYear(today.year)
        if (next.isBefore(today)) next = picked.withYear(today.year + 1)
        return at(next, time(target, zone), zone)
    }

    /** The weekend, a weekly class: a past date becomes its next return on that weekday, today included. */
    fun nextWeekly(target: Long, now: Long, zone: ZoneId): Long {
        val today = date(now, zone)
        val picked = date(target, zone)
        if (!picked.isBefore(today)) return target
        val weeks = (ChronoUnit.DAYS.between(picked, today) + 6) / 7
        return at(picked.plusWeeks(weeks), time(target, zone), zone)
    }

    /**
     * The moment a countdown that comes back ([repeat]) is counting to at [now]. A return stays put for
     * the whole day it falls on, so the card says "Today!" all day before it moves on.
     */
    fun nextReturn(target: Long, repeat: CoverItem.Countdown.Repeat, now: Long, zone: ZoneId): Long = when (repeat) {
        CoverItem.Countdown.Repeat.NONE -> target
        CoverItem.Countdown.Repeat.WEEKLY -> nextWeekly(target, now, zone)
        CoverItem.Countdown.Repeat.YEARLY -> nextAnnual(target, now, zone)
    }

    fun hour(target: Long, zone: ZoneId): Int = time(target, zone).hour

    fun minute(target: Long, zone: ZoneId): Int = time(target, zone).minute

    private fun date(millis: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    private fun time(millis: Long, zone: ZoneId): LocalTime = Instant.ofEpochMilli(millis).atZone(zone).toLocalTime().withSecond(0).withNano(0)

    /** Daylight-saving gaps move forward to the next valid time instead of failing. */
    private fun at(date: LocalDate, time: LocalTime, zone: ZoneId): Long = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
}
