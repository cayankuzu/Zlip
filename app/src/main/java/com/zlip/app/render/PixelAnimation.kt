package com.zlip.app.render

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import com.zlip.app.data.CoverItem
import kotlinx.coroutines.delay
import kotlin.math.min

/** One 60 Hz frame: the loop sleeps until just before a step is due, then waits for vsync. */
private const val VSYNC_MS = 16L

/** A frame that arrives this close before the step is due still takes it (vsync jitter). */
private const val STEP_SLACK_NANOS = 4_000_000L

/**
 * Pixel art on the cover. An animated drawing (a Glyph-Matrix-like flip book) steps through its frames
 * at the item's fps, never faster than the power mode's [LocalAnimationFps], and loops forever; every
 * wrap back to the first frame is one pass for smart auto-advance. PAUSED holds the frame on screen,
 * STILL (thumbnails) and single-frame drawings show the first frame and run nothing at all.
 */
@Composable
internal fun PixelArtCover(item: CoverItem.PixelArt, modifier: Modifier, playback: Playback, events: PlaybackEvents?) {
    if (playback == Playback.STILL || !item.animated) {
        PixelArtView(item.atFrame(0), modifier)
        return
    }
    AnimatedPixelArt(item, modifier, running = playback == Playback.LIVE, events = events)
}

@Composable
private fun AnimatedPixelArt(item: CoverItem.PixelArt, modifier: Modifier, running: Boolean, events: PlaybackEvents?) {
    // Keyed by id: another drawing starts at its first frame, while live edits of this one (frames drawn,
    // added or deleted in the editor) keep their place, clamped to the frames that still exist.
    var index by remember(item.id) { mutableIntStateOf(0) }
    val frameCount = item.frameCount
    val shown = index.coerceIn(0, frameCount - 1)
    val currentEvents by rememberUpdatedState(events)
    val fps = min(item.fps.coerceIn(1, 30), LocalAnimationFps.current.coerceAtLeast(1))

    LaunchedEffect(item.id, running, fps, frameCount) {
        if (!running) return@LaunchedEffect
        val frameNanos = 1_000_000_000L / fps
        var due = withFrameNanos { it } + frameNanos
        while (true) {
            // Sleep most of the wait, then step on a real frame. withFrameNanos suspends while Compose's
            // frame clock is paused (app in the background, cover off), so a hidden animation costs nothing.
            val sleepMs = (due - System.nanoTime()) / 1_000_000 - VSYNC_MS
            if (sleepMs > 0) delay(sleepMs)
            withFrameNanos { now ->
                if (now >= due - STEP_SLACK_NANOS) {
                    val next = (index.coerceIn(0, frameCount - 1) + 1) % frameCount
                    index = next
                    if (next == 0) currentEvents?.onCycle()
                    // Steady cadence without drift; after a stall (paused clock, slow frame) start afresh
                    // instead of rushing through the missed frames.
                    due = if (now - due > frameNanos) now + frameNanos else due + frameNanos
                }
            }
        }
    }

    PixelArtView(item.atFrame(shown), modifier)
}
