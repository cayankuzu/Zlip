package com.zlip.app.cover

import com.zlip.app.cover.AdvanceWait.MIN_AFTER_START_MS
import org.junit.Assert.assertEquals
import org.junit.Test

class AdvanceWaitTest {

    @Test
    fun unseenItemGetsItsFullTime() {
        assertEquals(30_000L, AdvanceWait.minimumMs(minMs = 30_000, seenMs = 0))
        assertEquals(100_000L, AdvanceWait.passCapMs(maxPassMs = 100_000, seenMs = 0))
    }

    @Test
    fun timeSeenBeforeIsTakenOff() {
        assertEquals(18_000L, AdvanceWait.minimumMs(minMs = 30_000, seenMs = 12_000))
        assertEquals(55_000L, AdvanceWait.passCapMs(maxPassMs = 100_000, seenMs = 45_000))
    }

    @Test
    fun neverFlipsTheMomentTheCoverWakes() {
        assertEquals(MIN_AFTER_START_MS, AdvanceWait.minimumMs(minMs = 30_000, seenMs = 29_000))
        assertEquals(MIN_AFTER_START_MS, AdvanceWait.minimumMs(minMs = 10_000, seenMs = 60_000))
        assertEquals(MIN_AFTER_START_MS, AdvanceWait.minimumMs(minMs = 1_000, seenMs = 0))
    }

    @Test
    fun aPassSeenInFullMovesOnSoonAfterAWake() {
        assertEquals(MIN_AFTER_START_MS, AdvanceWait.passCapMs(maxPassMs = 100_000, seenMs = 100_000))
        assertEquals(MIN_AFTER_START_MS, AdvanceWait.passCapMs(maxPassMs = 100_000, seenMs = 250_000))
    }

    @Test
    fun shortGlancesStillAddUpToAnAdvance() {
        // TIMER 30 s, the cover sleeping after 10 s glances: the item moves on during the third one.
        val credit = VisibleCredit()
        credit.add("photo", 10_000)
        credit.add("photo", 10_000)
        assertEquals(10_000L, AdvanceWait.minimumMs(minMs = 30_000, seenMs = credit.of("photo")))
    }

    @Test
    fun creditBelongsToOneItem() {
        val credit = VisibleCredit()
        credit.add("a", 4_000)
        credit.add("a", 5_000)
        assertEquals(9_000L, credit.of("a"))
        assertEquals(0L, credit.of("b"))
        // Another item starts from nothing, and the first one's time is gone.
        credit.add("b", 2_000)
        assertEquals(2_000L, credit.of("b"))
        assertEquals(0L, credit.of("a"))
    }

    @Test
    fun clearedCreditStartsOver() {
        val credit = VisibleCredit()
        credit.add("a", 4_000)
        credit.clear()
        assertEquals(0L, credit.of("a"))
    }

    @Test
    fun aTouchGivesTheItemItsFullTimeAgain() {
        // TIMER 10 s, a toy watched for 9 s, then tapped: noteInteraction() clears the credit and bumps the
        // touch count, and only then does the cancelled plan report its 9 s.
        val credit = VisibleCredit()
        credit.clear()
        credit.addCutShort("toy", 9_000, touchesAtStart = 3, touchesNow = 4)
        assertEquals(0L, credit.of("toy"))
        assertEquals(10_000L, AdvanceWait.minimumMs(minMs = 10_000, seenMs = credit.of("toy")))
    }

    @Test
    fun aPlanCutShortBySleepStillCounts() {
        // The cover slept, folded or a setting changed: no touch, so the time seen carries over.
        val credit = VisibleCredit()
        credit.addCutShort("photo", 8_000, touchesAtStart = 5, touchesNow = 5)
        credit.addCutShort("photo", 4_000, touchesAtStart = 5, touchesNow = 5)
        assertEquals(12_000L, credit.of("photo"))
        assertEquals(18_000L, AdvanceWait.minimumMs(minMs = 30_000, seenMs = credit.of("photo")))
    }

    @Test
    fun aTouchKeepsEarlierCreditCleared() {
        // Credit from earlier glances is gone after a touch, and the touched plan adds nothing back.
        val credit = VisibleCredit()
        credit.add("clip", 20_000)
        credit.clear()
        credit.addCutShort("clip", 6_000, touchesAtStart = 0, touchesNow = 1)
        assertEquals(0L, credit.of("clip"))
    }

    @Test
    fun negativeTimeIsIgnored() {
        val credit = VisibleCredit()
        credit.add("a", 3_000)
        credit.add("a", -500)
        assertEquals(3_000L, credit.of("a"))
    }
}
