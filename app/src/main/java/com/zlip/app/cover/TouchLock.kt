package com.zlip.app.cover

import kotlin.math.abs
import kotlin.math.hypot

enum class GestureResult { IGNORED, ACTIVATED, TAP, SWIPE_NEXT, SWIPE_PREVIOUS, SWIPE_DOWN, SWIPE_UP }

/**
 * Guards the cover screen against accidental touches while the phone is open (the cover faces the
 * palm). Locked by default: every touch is ignored until a double tap. Once active, taps and swipes
 * work; after [idleTimeoutMs] without any touch it locks again. Locking never turns the screen off.
 *
 * Pure logic with caller-supplied timestamps (uptime millis) so it can be unit tested. Per gesture the
 * caller reports [onDown], any [onMove]s, then [onGesture] when the first finger lifts and [onAllUp]
 * once no finger is left (or [onCancel] if the gesture was cancelled).
 */
class TouchLock(
    private val tapMaxDurationMs: Long = 300,
    private val doubleTapWindowMs: Long = 350,
    private val tapSlopPx: Float = 40f,
    private val doubleTapSlopPx: Float = 150f,
    private val swipeMinPx: Float = 80f,
    val idleTimeoutMs: Long = 3_000,
    /**
     * False for surfaces the user opened on purpose (the folded cover's live view): touches work right
     * away and it never locks.
     */
    private val autoLock: Boolean = true,
) {
    var isActive: Boolean = !autoLock
        private set

    private var lastInteractionMs = 0L
    private var fingerDown = false
    private var pendingTapUpMs = NO_TAP
    private var pendingTapX = 0f
    private var pendingTapY = 0f

    fun onDown(timeMs: Long) {
        fingerDown = true
        if (isActive) lastInteractionMs = timeMs
    }

    fun onMove(timeMs: Long) {
        if (isActive) lastInteractionMs = timeMs
    }

    /** Scores a finished gesture (first finger lifted). */
    fun onGesture(downMs: Long, downX: Float, downY: Float, upMs: Long, upX: Float, upY: Float): GestureResult {
        val dx = upX - downX
        val dy = upY - downY
        val isTap = upMs - downMs <= tapMaxDurationMs && hypot(dx, dy) <= tapSlopPx

        if (!isActive) {
            if (!isTap) {
                pendingTapUpMs = NO_TAP
                return GestureResult.IGNORED
            }
            val isSecondTap = pendingTapUpMs != NO_TAP &&
                downMs - pendingTapUpMs in 0..doubleTapWindowMs &&
                hypot(downX - pendingTapX, downY - pendingTapY) <= doubleTapSlopPx
            if (isSecondTap) {
                pendingTapUpMs = NO_TAP
                isActive = true
                lastInteractionMs = upMs
                return GestureResult.ACTIVATED
            }
            pendingTapUpMs = upMs
            pendingTapX = upX
            pendingTapY = upY
            return GestureResult.IGNORED
        }

        lastInteractionMs = upMs
        return when {
            isTap -> GestureResult.TAP
            abs(dx) >= swipeMinPx && abs(dx) > abs(dy) ->
                if (dx < 0) GestureResult.SWIPE_NEXT else GestureResult.SWIPE_PREVIOUS
            abs(dy) >= swipeMinPx && abs(dy) > abs(dx) ->
                if (dy > 0) GestureResult.SWIPE_DOWN else GestureResult.SWIPE_UP
            else -> GestureResult.IGNORED
        }
    }

    /** Every finger is off the screen; the idle countdown starts from here. */
    fun onAllUp(timeMs: Long) {
        fingerDown = false
        if (isActive) lastInteractionMs = timeMs
    }

    /** The gesture was cancelled; nothing fires but no finger counts as down any more. */
    fun onCancel(timeMs: Long) = onAllUp(timeMs)

    /** How long until [lockIfIdle] would lock; re-check after this delay. */
    fun millisUntilLock(nowMs: Long): Long = when {
        !isActive || !autoLock -> Long.MAX_VALUE
        fingerDown -> idleTimeoutMs
        else -> (lastInteractionMs + idleTimeoutMs - nowMs).coerceAtLeast(0)
    }

    /** Locks if active and untouched for [idleTimeoutMs]. Returns true if it just locked. */
    fun lockIfIdle(nowMs: Long): Boolean {
        if (!autoLock || !isActive || fingerDown || nowMs - lastInteractionMs < idleTimeoutMs) return false
        lock()
        return true
    }

    fun lock() {
        isActive = false
        pendingTapUpMs = NO_TAP
    }

    private companion object {
        const val NO_TAP = Long.MIN_VALUE
    }
}
