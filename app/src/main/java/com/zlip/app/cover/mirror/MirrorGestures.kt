package com.zlip.app.cover.mirror

import android.os.SystemClock
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.zlip.app.cover.TouchLock
import kotlinx.coroutines.CancellationException
import kotlin.math.abs
import kotlin.math.hypot

/**
 * One gesture on the unlocked mirror, from its first finger [down] until the last one lifts: one finger drags the
 * screen along with it and lets it glide on, two pinch it around their midpoint, and a quick tap switches between the
 * whole screen and filled around the tap. Every move counts as use ([TouchLock.onMove]), so the cover never locks
 * mid-gesture. [onSteer] says the screen started to move. Returns when the last finger lifted (uptime millis).
 */
internal suspend fun AwaitPointerEventScope.steerMirror(
    mirror: MirrorController,
    down: PointerInputChange,
    lock: TouchLock,
    onSteer: () -> Unit,
): Long {
    val caughtFling = mirror.onTouchStart()
    val tracker = VelocityTracker()
    // The fingers' midpoint as it has moved so far: a fling follows it, whatever fingers came and went.
    var travelled = Offset.Zero
    tracker.addPosition(down.uptimeMillis, travelled)
    var trackedFingers = 1
    var twoFingers = false
    var steering = false
    var slopPan = Offset.Zero
    var slopZoom = 1f
    var upTime = down.uptimeMillis
    try {
        while (true) {
            val event = awaitPointerEvent()
            // A cancelled gesture arrives consumed: nothing to finish.
            if (event.changes.any { it.isConsumed }) {
                mirror.onTouchEnd()
                lock.onCancel(SystemClock.uptimeMillis())
                return upTime
            }
            upTime = event.changes.maxOf { it.uptimeMillis }
            lock.onMove(upTime)
            val fingers = event.changes.count { it.pressed }
            if (fingers == 0) break
            if (fingers > 1) twoFingers = true
            val pan = event.calculatePan()
            val zoom = event.calculateZoom()
            if (!steering) {
                // Past the touch slop, as a scroll or a pinch would be; until then it may still be a tap.
                slopPan += pan
                slopZoom *= zoom
                val pinched = abs(1f - slopZoom) * event.calculateCentroidSize(useCurrent = false)
                if (slopPan.getDistance() > viewConfiguration.touchSlop || pinched > viewConfiguration.touchSlop) {
                    steering = true
                    onSteer()
                }
            }
            if (steering) {
                if (zoom != 1f) mirror.zoomBy(zoom, event.calculateCentroid(useCurrent = true))
                if (pan != Offset.Zero) mirror.pan(pan.x, pan.y)
                event.changes.forEach { if (it.positionChanged()) it.consume() }
            }
            // A finger joining or leaving moves the midpoint without anyone flinging: start the speed over.
            if (fingers != trackedFingers) {
                tracker.resetTracking()
                trackedFingers = fingers
            }
            travelled += pan
            tracker.addPosition(upTime, travelled)
        }
    } catch (e: CancellationException) {
        mirror.onTouchEnd()
        lock.onCancel(SystemClock.uptimeMillis())
        throw e
    }
    when {
        // A pinch ends with fingers lifting one after the other, which is no fling.
        steering && !twoFingers -> {
            val velocity = tracker.calculateVelocity()
            val max = viewConfiguration.maximumFlingVelocity
            val fling = Velocity(velocity.x.coerceIn(-max, max), velocity.y.coerceIn(-max, max))
            // Android's own scrolling physics at the cover's density, so it glides like any list there.
            mirror.onTouchEnd(
                velocity = if (hypot(fling.x, fling.y) >= MIN_FLING_DP_PER_S.dp.toPx()) fling else Velocity.Zero,
                decay = splineBasedDecay(this),
            )
        }
        // A tap that caught a glide only stopped it.
        !steering && !twoFingers && !caughtFling && upTime - down.uptimeMillis <= TAP_MAX_MS -> {
            mirror.onTouchEnd()
            mirror.toggleView(focus = down.position)
        }
        else -> mirror.onTouchEnd()
    }
    lock.onAllUp(upTime)
    return upTime
}

/** As the cover's own taps ([TouchLock]): longer is a press, not a tap. */
private const val TAP_MAX_MS = 300L

/** Slower than this a lifted finger just lets go. */
private const val MIN_FLING_DP_PER_S = 50
