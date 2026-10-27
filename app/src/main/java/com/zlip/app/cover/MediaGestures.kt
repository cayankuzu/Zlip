package com.zlip.app.cover

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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlin.math.abs
import kotlin.math.hypot

/** How a gesture on a photo or video ended: when its last finger lifted (uptime millis), and whether it was only a tap. */
internal class FramedGesture(val upTime: Long, val tapped: Boolean)

/**
 * One gesture framing a photo or video on the cover, like a phone's gallery, from its first finger [down] until the
 * last one lifts: two fingers zoom the picture around their midpoint and move it along, one finger moves a zoomed-in
 * picture around and lets it glide on (in [glideScope]). [pinching]: a second finger is already down (the gesture began
 * as an ordinary touch). [move] moves the picture (zoom change, the fingers' midpoint, how far they moved, in cover
 * pixels) and says whether it still moved. [framing] shows it live and knows while it moves; every move also counts as
 * use ([TouchLock.onMove]), so the open phone's cover never locks mid-gesture.
 *
 * Never changes the item: a swipe here moves the picture, never to the next one.
 */
internal suspend fun AwaitPointerEventScope.frameMedia(
    down: PointerInputChange,
    lock: TouchLock,
    pinching: Boolean,
    framing: CoverFraming,
    glideScope: CoroutineScope,
    move: (zoomChange: Float, pivot: Offset, pan: Offset) -> Boolean,
): FramedGesture {
    val tracker = VelocityTracker()
    // The fingers' midpoint as it has moved so far: a fling follows it, whatever fingers came and went.
    var travelled = Offset.Zero
    tracker.addPosition(down.uptimeMillis, travelled)
    var trackedFingers = 1
    var twoFingers = pinching
    var steering = false
    var slopPan = Offset.Zero
    var slopZoom = 1f
    var upTime = down.uptimeMillis
    try {
        while (true) {
            val event = awaitPointerEvent()
            // A cancelled gesture, or a touch a control button took, arrives consumed: nothing to finish.
            if (event.changes.any { it.isConsumed }) {
                framing.stopMoving()
                lock.onCancel(SystemClock.uptimeMillis())
                return FramedGesture(upTime, tapped = false)
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
                    framing.startMoving()
                }
            }
            if (steering) {
                if (zoom != 1f || pan != Offset.Zero) move(zoom, event.calculateCentroid(useCurrent = false), pan)
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
        framing.stopMoving()
        lock.onCancel(SystemClock.uptimeMillis())
        throw e
    }
    lock.onAllUp(upTime)
    // A pinch ends with fingers lifting one after the other, which is no fling.
    val fling = if (steering && !twoFingers) {
        val velocity = tracker.calculateVelocity()
        val max = viewConfiguration.maximumFlingVelocity
        Velocity(velocity.x.coerceIn(-max, max), velocity.y.coerceIn(-max, max))
    } else {
        Velocity.Zero
    }
    if (hypot(fling.x, fling.y) >= MIN_FLING_DP_PER_S.dp.toPx()) {
        // Android's own scrolling physics at the cover's density, so it glides like any list there; around the centre,
        // which only matters for zooming, not for moving.
        val centre = Offset(size.width / 2f, size.height / 2f)
        framing.glide(glideScope, fling, splineBasedDecay(this)) { pan -> move(1f, centre, pan) }
    } else {
        framing.stopMoving()
    }
    return FramedGesture(upTime, tapped = !steering && !twoFingers && upTime - down.uptimeMillis <= TAP_MAX_MS)
}

/** As the cover's own taps ([TouchLock]): longer is a press, not a tap. */
private const val TAP_MAX_MS = 300L

/** Slower than this a lifted finger just lets go. */
private const val MIN_FLING_DP_PER_S = 50
