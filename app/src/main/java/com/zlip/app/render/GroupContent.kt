package com.zlip.app.render

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.zlip.app.data.CoverItem
import com.zlip.app.data.GroupTiming
import com.zlip.app.data.MediaKind
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withTimeoutOrNull

/** Crossfade between two still steps; video steps cut instead. */
private const val CROSSFADE_MS = 500

/**
 * A group: its items one after another, each full screen. Stills stay for the group's step time; videos,
 * GIFs, scrolling text, animations and collages first finish the pass they are in (see [GroupTiming]).
 *
 * Exactly one step is composed at a time, as a new composition per pass and index: a video always starts
 * at 0 and only one decoder is alive. A step's own events end it and never reach [events], so a video's
 * loop is not mistaken for the group's pass; the group reports one [PlaybackEvents.onCycle] per trip
 * through all items, or [PlaybackEvents.onFailed] when none of them can play. Unlike a collage, steps
 * are full screen, so videos keep the cover's hardware overlay and their own sound setting.
 */
@Composable
internal fun GroupCover(item: CoverItem.Group, modifier: Modifier, playback: Playback, events: PlaybackEvents?) {
    val first = item.items.firstOrNull()
    when {
        first == null -> Box(modifier.background(Color.Black))
        // Thumbnails: the first step's still, nothing running.
        playback == Playback.STILL -> CoverContent(item = first, modifier = modifier, playback = Playback.STILL)
        else -> LiveGroup(item, modifier, playback, events)
    }
}

/** One step on screen: which pass and position, and what it shows. */
private data class Step(val pass: Int, val index: Int, val childId: String, val video: Boolean)

/**
 * What the step on screen reported. A fresh one per step, so a late event of the previous step (a video
 * reporting its end as it is released) can never end the next one.
 */
private class StepSignals : PlaybackEvents {
    val cycles = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val ended = MutableStateFlow(false)
    val failed = MutableStateFlow(false)

    override fun onCycle() {
        cycles.tryEmit(Unit)
    }

    override fun onEnded(ended: Boolean) {
        this.ended.value = ended
    }

    override fun onFailed(failed: Boolean) {
        this.failed.value = failed
    }
}

@Composable
private fun LiveGroup(item: CoverItem.Group, modifier: Modifier, playback: Playback, events: PlaybackEvents?) {
    val items = item.items
    var pass by remember { mutableIntStateOf(0) }
    var index by remember { mutableIntStateOf(0) }
    // Live edits can shorten the group under the current position; start over then.
    val shownIndex = if (index in items.indices) index else 0
    val child = items[shownIndex]
    val step = Step(pass, shownIndex, child.id, video = child.isVideo())
    val signals = remember(step) { StepSignals() }
    // Positions that failed during this pass; another set of items starts with a clean slate.
    val failedSteps = remember(items) { mutableSetOf<Int>() }
    var stuck by remember(items) { mutableStateOf(false) }
    // Whether the playlist was told that nothing here can play (so it hears when something can again).
    var failureReported by remember { mutableStateOf(false) }
    val currentEvents by rememberUpdatedState(events)
    val currentSize by rememberUpdatedState(items.size)
    // Nothing advances while paused or while the cover is dark; coming back restarts the step's minimum time.
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val running = playback == Playback.LIVE && lifecycleState.isAtLeast(Lifecycle.State.STARTED)

    // Next/previous (cover swipes and buttons, notification, inner-screen controls) move through the steps first.
    val stepper = remember {
        object : GroupStepper {
            override fun next(wrap: Boolean): Boolean {
                val at = index.coerceIn(0, currentSize - 1)
                when {
                    at + 1 < currentSize -> index = at + 1
                    wrap -> {
                        pass++
                        index = 0
                    }
                    else -> return false
                }
                return true
            }

            override fun previous(wrap: Boolean): Boolean {
                val at = index.coerceIn(0, currentSize - 1)
                when {
                    at > 0 -> index = at - 1
                    wrap -> index = currentSize - 1
                    else -> return false
                }
                return true
            }
        }
    }
    DisposableEffect(events) {
        events?.attachStepper(stepper)
        onDispose {
            events?.detachStepper(stepper)
            // A failure belongs to this showing: next time the group comes round it gets a fresh chance.
            if (failureReported) events?.onFailed(false)
        }
    }

    LaunchedEffect(step) { currentEvents?.onStep(child) }

    LaunchedEffect(step, running, item.stepSeconds, stuck) {
        if (!running || stuck) return@LaunchedEffect
        val failed = playStep(child, GroupTiming.stepMs(item), signals)
        if (failed) failedSteps += shownIndex else failedSteps -= shownIndex
        // The current size: a live edit may have changed the group while this step played.
        val next = GroupTiming.next(shownIndex, currentSize)
        if (!next.wrapped) {
            index = next.index
            return@LaunchedEffect
        }
        if (GroupTiming.allFailed(failedSteps, currentSize)) {
            // Nothing here can play: hold still, and let the playlist move on instead of waiting for a pass.
            stuck = true
            failureReported = true
            currentEvents?.onFailed(true)
            return@LaunchedEffect
        }
        failedSteps.clear()
        if (failureReported) {
            failureReported = false
            currentEvents?.onFailed(false)
        }
        currentEvents?.onCycle()
        // Hold the last step a moment: the playlist usually moves on now, and step one would only flash.
        delay(GroupTiming.WRAP_GRACE_MS)
        pass++
        index = 0
    }

    AnimatedContent(
        targetState = step,
        transitionSpec = {
            // A video's surface cannot fade reliably, and a crossfade would keep two decoders alive: cut instead.
            val transition = if (initialState.video || targetState.video) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                fadeIn(tween(CROSSFADE_MS)) togetherWith fadeOut(tween(CROSSFADE_MS))
            }
            transition using null
        },
        modifier = modifier.background(Color.Black),
        label = "group-step",
    ) { shown ->
        // A step on its way out may already be gone from an edited group: it simply disappears.
        val shownChild = items.getOrNull(shown.index)?.takeIf { it.id == shown.childId } ?: return@AnimatedContent
        CoverContent(
            item = shownChild,
            modifier = Modifier.fillMaxSize(),
            playback = playback,
            // Only the current step may end the step; one fading out says nothing any more.
            events = if (shown == step) signals else null,
        )
    }
}

/**
 * Plays one step: its minimum time, then, for a video, GIF, marquee, animation or collage, the end of the
 * pass it is in (bounded). Returns true when the step failed; it then stays a moment before it is skipped.
 */
private suspend fun playStep(child: CoverItem, minMs: Long, signals: StepSignals): Boolean {
    val failedEarly = withTimeoutOrNull(minMs) { signals.failed.first { it } } != null
    // A play-once clip that already ended moves on at the minimum.
    val failed = when {
        failedEarly -> true
        !GroupTiming.isCycleBased(child) || signals.ended.value -> false
        else -> withTimeoutOrNull(GroupTiming.extraWaitMs(child)) {
            merge(
                signals.cycles.map { false },
                signals.ended.filter { it }.map { false },
                signals.failed.filter { it }.map { true },
            ).first()
        } ?: false
    }
    if (failed) delay(GroupTiming.FAILED_STEP_MS)
    return failed
}

private fun CoverItem.isVideo(): Boolean = this is CoverItem.Media && kind == MediaKind.VIDEO
