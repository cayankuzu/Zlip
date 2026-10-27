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
import androidx.compose.runtime.compositionLocalOf
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
import kotlin.random.Random

/** Crossfade between two still steps (when the group has soft transitions); video steps cut instead. */
private const val CROSSFADE_MS = 500

/**
 * True while fingers frame the step on the cover: the group stays on that step, and the step's time starts again
 * once they let go, as for any other use of the cover.
 */
val LocalHoldGroupStep = compositionLocalOf { false }

/**
 * A group: its items one after another, each full screen. Stills stay for the group's photo time; videos
 * play once to their end (or are cut at the photo time); GIFs, scrolling text, animations and collages first
 * finish the pass they are in (see [GroupTiming]). With shuffle the steps play in a new order every pass;
 * with repeat off the group stops on its last step.
 *
 * Exactly one step is composed at a time, as a new composition per pass and position: a video always starts
 * at 0 and only one decoder is alive. A step's own events end it and never reach [events], so a video's
 * loop is not mistaken for the group's pass; the group reports one [PlaybackEvents.onCycle] per trip
 * through all items (repeat on or off), or [PlaybackEvents.onFailed] when none of them can play. Unlike a
 * collage, steps are full screen, so videos keep the cover's hardware overlay and their own sound setting.
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

/**
 * One step on screen: which pass and position in that pass's order, which item ([itemIndex] in the group's
 * list) and what it shows.
 */
private data class Step(val pass: Int, val position: Int, val itemIndex: Int, val childId: String, val video: Boolean)

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
    // The position in this pass's order (the list order, or the shuffled one).
    var index by remember { mutableIntStateOf(0) }
    // A new random order each time the group comes on screen; stable while it stays (recomposition, edits).
    val seed = remember { Random.nextLong() }
    val order = remember(items.size, item.shuffle, pass) { GroupTiming.playOrder(items.size, item.shuffle, seed, pass) }
    // Live edits can shorten the group under the current position; start over then.
    val position = if (index in order.indices) index else 0
    val itemIndex = order[position]
    val child = items[itemIndex]
    val step = Step(pass, position, itemIndex, child.id, video = child.isVideo())
    val signals = remember(step) { StepSignals() }
    // Positions that failed during this pass; another set of items starts with a clean slate.
    val failedSteps = remember(items) { mutableSetOf<Int>() }
    var stuck by remember(items) { mutableStateOf(false) }
    // Repeat off: the step the group stopped on at the end of its pass. Any other step (next, previous,
    // repeat turned back on in the editor) plays on.
    var stoppedAt by remember { mutableStateOf<Step?>(null) }
    val stopped = !item.repeat && stoppedAt == step
    // Whether the playlist was told that nothing here can play (so it hears when something can again).
    var failureReported by remember { mutableStateOf(false) }
    val currentEvents by rememberUpdatedState(events)
    val currentSize by rememberUpdatedState(items.size)
    val currentRepeat by rememberUpdatedState(item.repeat)
    // Nothing advances while paused, while the cover is dark or while the step is being framed; coming back restarts
    // the step's minimum time.
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val running = playback == Playback.LIVE && lifecycleState.isAtLeast(Lifecycle.State.STARTED) && !LocalHoldGroupStep.current

    // Next/previous (cover swipes and buttons, notification, inner-screen controls) move through the steps
    // first, in the order they play (shuffled too).
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

    LaunchedEffect(step, running, item.stepSeconds, item.videosToEnd, stuck, stopped) {
        if (!running || stuck || stopped) return@LaunchedEffect
        val failed = playStep(
            minMs = GroupTiming.stepMinMs(item, child),
            waitsForPass = GroupTiming.stepWaitsForPass(item, child),
            extraMs = GroupTiming.extraWaitMs(child),
            signals = signals,
        )
        if (failed) failedSteps += position else failedSteps -= position
        // The current size: a live edit may have changed the group while this step played.
        val next = GroupTiming.next(position, currentSize)
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
        // One pass either way: with auto-advance on the playlist moves on now.
        currentEvents?.onCycle()
        if (!currentRepeat) {
            // Stays on its last step: a video or GIF holds its last frame, a photo stays.
            stoppedAt = step
            return@LaunchedEffect
        }
        // Hold the last step a moment: the playlist usually moves on now, and step one would only flash.
        delay(GroupTiming.WRAP_GRACE_MS)
        pass++
        index = 0
    }

    AnimatedContent(
        targetState = step,
        transitionSpec = {
            // A video's surface cannot fade reliably, and a crossfade would keep two decoders alive: cut instead.
            val transition = if (!item.crossfade || initialState.video || targetState.video) {
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
        val shownChild = items.getOrNull(shown.itemIndex)?.takeIf { it.id == shown.childId } ?: return@AnimatedContent
        CoverContent(
            item = shownChild.asStep(item, stopped = shown == step && stopped),
            modifier = Modifier.fillMaxSize(),
            playback = playback,
            // Only the current step may end the step; one fading out says nothing any more.
            events = if (shown == step) signals else null,
        )
    }
}

/**
 * Plays one step: its minimum time ([minMs]), then, when it [waitsForPass] (a video playing to its end, a
 * GIF, marquee, animation or collage), the end of the pass it is in, bounded by [extraMs]. Returns true when
 * the step failed; it then stays a moment before it is skipped.
 */
private suspend fun playStep(minMs: Long, waitsForPass: Boolean, extraMs: Long, signals: StepSignals): Boolean {
    val failedEarly = withTimeoutOrNull(minMs) { signals.failed.first { it } } != null
    // A play-once clip that already ended moves on at the minimum.
    val failed = when {
        failedEarly -> true
        !waitsForPass || signals.ended.value -> false
        else -> withTimeoutOrNull(extraMs) {
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

/**
 * [this] step as the group plays it. A video that plays to its end plays exactly once (it ends, and the step
 * moves on); a group that [stopped] on its last step lets a video or GIF finish and hold its last frame.
 */
private fun CoverItem.asStep(group: CoverItem.Group, stopped: Boolean): CoverItem = when {
    this !is CoverItem.Media || kind == MediaKind.IMAGE || !loop -> this
    stopped || GroupTiming.playsToEnd(group, this) -> copy(loop = false)
    else -> this
}

private fun CoverItem.isVideo(): Boolean = this is CoverItem.Media && kind == MediaKind.VIDEO
