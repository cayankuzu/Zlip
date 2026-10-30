package com.zlip.app.cover

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchLockTest {

    private fun TouchLock.tap(atMs: Long, x: Float = 100f, y: Float = 100f): GestureResult {
        onDown(atMs)
        return onGesture(atMs, x, y, atMs + 50, x, y).also { onAllUp(atMs + 50) }
    }

    private fun TouchLock.swipe(atMs: Long, dx: Float): GestureResult {
        onDown(atMs)
        onMove(atMs + 100)
        return onGesture(atMs, 300f, 300f, atMs + 200, 300f + dx, 300f).also { onAllUp(atMs + 200) }
    }

    private fun TouchLock.verticalSwipe(atMs: Long, dy: Float): GestureResult {
        onDown(atMs)
        onMove(atMs + 100)
        return onGesture(atMs, 300f, 300f, atMs + 200, 310f, 300f + dy).also { onAllUp(atMs + 200) }
    }

    @Test
    fun verticalSwipesWhileActiveAreReported() {
        val lock = TouchLock(autoLock = false)
        assertEquals(GestureResult.SWIPE_DOWN, lock.verticalSwipe(1_000, 200f))
        assertEquals(GestureResult.SWIPE_UP, lock.verticalSwipe(2_000, -200f))
    }

    @Test
    fun verticalSwipeWhileLockedIsIgnored() {
        val lock = TouchLock()
        assertEquals(GestureResult.IGNORED, lock.verticalSwipe(1_000, 200f))
        assertFalse(lock.isActive)
    }

    @Test
    fun singleTapWhileLockedIsALockedTap() {
        val lock = TouchLock()
        assertEquals(GestureResult.LOCKED_TAP, lock.tap(1_000))
        assertFalse(lock.isActive)
    }

    @Test
    fun aPalmOrPinchIsNeverALockedTap() {
        val lock = TouchLock()
        lock.onDown(1_000)
        assertEquals(GestureResult.IGNORED, lock.onGesture(1_000, 100f, 100f, 1_050, 100f, 100f, multiTouch = true))
        lock.onAllUp(1_050)
        assertFalse(lock.isActive)
    }

    @Test
    fun aSecondTapActivatesRatherThanAskingAgain() {
        val lock = TouchLock()
        assertEquals(GestureResult.LOCKED_TAP, lock.tap(1_000))
        assertEquals(GestureResult.ACTIVATED, lock.tap(1_200))
    }

    @Test
    fun unlockWorksWithoutTheDoubleTapAndLocksWhenIdle() {
        val lock = TouchLock()
        lock.unlock(5_000)
        assertTrue(lock.isActive)
        assertEquals(GestureResult.TAP, lock.tap(5_500))
        assertFalse(lock.lockIfIdle(8_000))
        assertTrue(lock.lockIfIdle(8_550))
    }

    @Test
    fun doubleTapActivates() {
        val lock = TouchLock()
        lock.tap(1_000)
        assertEquals(GestureResult.ACTIVATED, lock.tap(1_200))
        assertTrue(lock.isActive)
    }

    @Test
    fun slowSecondTapDoesNotActivate() {
        val lock = TouchLock()
        lock.tap(1_000)
        assertEquals(GestureResult.LOCKED_TAP, lock.tap(2_000))
        assertFalse(lock.isActive)
    }

    @Test
    fun distantSecondTapDoesNotActivate() {
        val lock = TouchLock()
        lock.tap(1_000, x = 50f, y = 50f)
        assertEquals(GestureResult.LOCKED_TAP, lock.tap(1_150, x = 600f, y = 600f))
        assertFalse(lock.isActive)
    }

    @Test
    fun swipeWhileLockedIsIgnoredAndBreaksDoubleTap() {
        val lock = TouchLock()
        lock.tap(1_000)
        assertEquals(GestureResult.IGNORED, lock.swipe(1_100, dx = -200f))
        assertEquals(GestureResult.LOCKED_TAP, lock.tap(1_250))
        assertFalse(lock.isActive)
    }

    @Test
    fun longPressIsNotATap() {
        val lock = TouchLock()
        lock.onDown(1_000)
        lock.onGesture(1_000, 100f, 100f, 1_800, 100f, 100f)
        lock.onAllUp(1_800)
        assertEquals(GestureResult.LOCKED_TAP, lock.tap(1_900))
        assertFalse(lock.isActive)
    }

    @Test
    fun activeTapAndSwipesAreReported() {
        val lock = TouchLock()
        lock.tap(1_000)
        lock.tap(1_200)
        assertEquals(GestureResult.TAP, lock.tap(2_000))
        assertEquals(GestureResult.DOUBLE_TAP, lock.tap(2_200))
        assertEquals(GestureResult.SWIPE_NEXT, lock.swipe(2_500, dx = -200f))
        assertEquals(GestureResult.SWIPE_PREVIOUS, lock.swipe(3_000, dx = 200f))
    }

    @Test
    fun doubleTapWhileActiveReportsDoubleTap() {
        val lock = TouchLock(autoLock = false)
        assertEquals(GestureResult.TAP, lock.tap(1_000))
        assertEquals(GestureResult.DOUBLE_TAP, lock.tap(1_200))
    }

    @Test
    fun distantSecondTapWhileActiveReportsSingleTap() {
        val lock = TouchLock(autoLock = false)
        assertEquals(GestureResult.TAP, lock.tap(1_000, x = 50f, y = 50f))
        assertEquals(GestureResult.TAP, lock.tap(1_150, x = 600f, y = 600f))
    }

    @Test
    fun locksAfterThreeIdleSeconds() {
        val lock = TouchLock()
        lock.tap(1_000)
        lock.tap(1_200) // activated, last interaction at 1_250
        assertFalse(lock.lockIfIdle(4_200))
        assertEquals(50L, lock.millisUntilLock(4_200))
        assertTrue(lock.lockIfIdle(4_250))
        assertFalse(lock.isActive)
    }

    @Test
    fun touchResetsIdleTimer() {
        val lock = TouchLock()
        lock.tap(1_000)
        lock.tap(1_200)
        lock.tap(3_000) // TAP at 3_050
        assertFalse(lock.lockIfIdle(5_000))
        assertTrue(lock.lockIfIdle(6_050))
    }

    @Test
    fun heldFingerKeepsItActive() {
        val lock = TouchLock()
        lock.tap(1_000)
        lock.tap(1_200)
        lock.onDown(2_000)
        assertFalse(lock.lockIfIdle(9_000))
        assertTrue(lock.isActive)
    }

    @Test
    fun secondFingerStillDownKeepsItActive() {
        val lock = TouchLock()
        lock.tap(1_000)
        lock.tap(1_200)
        lock.onDown(2_000)
        lock.onGesture(2_000, 100f, 100f, 2_100, 100f, 100f) // first finger up, another still down
        assertFalse(lock.lockIfIdle(8_000))
        lock.onAllUp(8_000)
        assertFalse(lock.lockIfIdle(10_999))
        assertTrue(lock.lockIfIdle(11_000))
    }

    @Test
    fun cancelledGestureReleasesFinger() {
        val lock = TouchLock()
        lock.tap(1_000)
        lock.tap(1_200)
        lock.onDown(2_000)
        lock.onCancel(2_100)
        assertTrue(lock.lockIfIdle(5_100))
    }

    @Test
    fun withoutAutoLockTouchesWorkImmediatelyAndNeverLock() {
        val lock = TouchLock(autoLock = false)
        assertTrue(lock.isActive)
        assertEquals(GestureResult.TAP, lock.tap(1_000))
        assertEquals(GestureResult.SWIPE_NEXT, lock.swipe(2_000, dx = -200f))
        assertFalse(lock.lockIfIdle(1_000_000))
        assertTrue(lock.isActive)
    }

    @Test
    fun afterLockingSingleTapIsOnlyALockedTapAgain() {
        val lock = TouchLock()
        lock.tap(1_000)
        lock.tap(1_200)
        lock.lockIfIdle(10_000)
        assertEquals(GestureResult.LOCKED_TAP, lock.tap(11_000))
        assertFalse(lock.isActive)
    }
}
