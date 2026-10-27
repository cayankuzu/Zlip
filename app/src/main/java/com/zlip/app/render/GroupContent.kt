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
import androidx.compose.runtime.SideEffect
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
 * Exactly one step is composed at a time, as a new composition per pass and item: a video always starts
 * at 0 and only one decoder is alive. A step's own events end it and never reach [events], so a video's
 * loop is not mistaken for the group's pass; the group reports one [PlaybackEvents.onCycle] per trip
 * through all items (repeat on or off), or [PlaybackEvents.onFailed] when none of them can play. Unlike a
 * collage, steps are full screen, so videos keep the cover's hardware overlay and are heard with the cover's
 * session sound (unless muted), as on their own. A group that stopped on its last step (repeat off) reports
 * [PlaybackEvents.onEnded] while it holds it.
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
 * One showing of a step: the pass and the item ([childId]) it shows. Not its place in the list, so a live edit that
 * only moves the step (or others around it) neither restarts it nor cuts to another one.
 */
private data class Step(val pass: Int, val childId: String, val video: Boolean)

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
    // The pass the group was on before this one: previous may step back into it once (the step just seen), never further.
    var backFloor by remember { mutableIntStateOf(0) }
    // The position in this pass's order (the list order, or the shuffled one) that the group last moved to.
    var index by remember { mutableIntStateOf(0) }
    // The item on screen there. Null right after a move, until the step it lands on is shown.
    var shownId by remember { mutableStateOf<String?>(null) }
    // A new random order each time the group comes on screen; stable while it stays (recomposition, edits).
    val seed = remember { Random.nextLong() }
    val order = remember(items.size, item.shuffle, pass) { GroupTiming.playOrder(items.size, item.shuffle, seed, pass) }
    // A live edit (a step moved, removed or added, shuffle switched) keeps the step on screen wherever it went.
    val position = GroupSteps.keptPosition(shownId, index, order.map { items[it].id })
    val child = items[order[position]]
    // Next and previous count from the step on screen.
    SideEffect {
        index = position
        shownId = child.id
    }
    val step = Step(pass, child.id, video = child.isVideo())
    val signals = remember(step) { StepSignals() }
    // Positions that failed during this pass. One set and one flag for the group's whole showing, so a step that keeps
    // playing through a live edit counts in the same set the next step reads.
    val failedSteps = remember { mutableSetOf<Int>() }
    var stuck by remember { mutableStateOf(false) }
    // Another set of items starts with a clean slate.
    LaunchedEffect(items) {
        failedSteps.clear()
        stuck = false
    }
    // Repeat off: the step the group stopped on at the end of its pass. Any move (next, previous, a step opened from
    // the editor) or repeat turned back on plays on; an edit that only moves the stopped step keeps it held.
    var stoppedAt by remember { mutableStateOf<Step?>(null) }
    val stopped = !item.repeat && stoppedAt == step
    // Whether the playlist was told that nothing here can play (so it hears when something can again).
    var failureReported by remember { mutableStateOf(false) }
    val currentEvents by rememberUpdatedState(events)
    val currentPosition by rememberUpdatedState(position)
    val currentItems by rememberUpdatedState(items)
    val currentSize by rememberUpdatedState(items.size)
    val currentRepeat by rememberUpdatedState(item.repeat)
    val currentShuffle by rememberUpdatedState(item.shuffle)
    // Nothing advances while paused, while the cover is dark or while the step is being framed; coming back restarts
    // the step's minimum time.
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val running = playback == Playback.LIVE && lifecycleState.isAtLeast(Lifecycle.State.STARTED) && !LocalHoldGroupStep.current

    // Next/previous (cover swipes and buttons, notification, inner-screen controls) move through the steps
    // first, in the order they play (shuffled too).
    val stepper = remember {
        object : GroupStepper {
            override fun next(wrap: Boolean): Boolean = go(GroupSteps.next(GroupSteps.At(pass, index), currentSize, wrap))

            override fun previous(wrap: Boolean): Boolean =
                go(GroupSteps.previous(GroupSteps.At(pass, index), currentSize, wrap, minPass = backFloor))

            override fun showStep(childId: String): Boolean {
                val steps = currentItems
                val order = GroupTiming.playOrder(steps.size, currentShuffle, seed, pass)
                return go(GroupSteps.show(steps.indexOfFirst { it.id == childId }, order, pass))
            }

            private fun go(to: GroupSteps.At?): Boolean {
                if (to == null) return false
                if (to.pass > pass) backFloor = pass
                pass = to.pass
                index = to.index
                shownId = null
                stoppedAt = null
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
            events?.onEnded(false)
        }
    }

    // The position in play order, so a shuffled group still counts 1, 2, 3; a live edit that moves it tells again.
    LaunchedEffect(step, position, order.size) { currentEvents?.onStepAt(child, position + 1, order.size) }

    // Repeat off: holding the last step is the group's end, like a play-once clip's last frame, so a plan that starts
    // after it stopped moves on instead of waiting for a pass that never comes.
    LaunchedEffect(stopped) { currentEvents?.onEnded(stopped) }

    LaunchedEffect(step, running, item.stepSeconds, item.videosToEnd, stuck, stopped) {
        if (!running || stuck || stopped) return@LaunchedEffect
        val failed = playStep(
            minMs = GroupTiming.stepMinMs(item, child),
            waitsForPass = GroupTiming.stepWaitsForPass(item, child),
            extraMs = GroupTiming.extraWaitMs(child),
            signals = signals,
        )
        // Where the step is now, and the current size: a live edit may have changed the group while this step played.
        val at = currentPosition
        if (failed) failedSteps += at else failedSteps -= at
        val next = GroupTiming.next(at, currentSize)
        if (!next.wrapped) {
            index = next.index
            shownId = null
            stoppedAt = null
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
        backFloor = pass
        pass++
        index = 0
        shownId = null
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
        val shownChild = items.firstOrNull { it.id == shown.childId } ?: return@AnimatedContent
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

/**
 * Where next, previous and a jump take the group on screen, as a pass and a position in that pass's play order
 * ([GroupTiming.playOrder]). Null when the step cannot move that way. Pure, so it is unit-tested.
 */
internal object GroupSteps {
    data class At(val pass: Int, val index: Int)

    /** The next step; after the last one only with [wrap] (step one of a new pass). */
    fun next(at: At, size: Int, wrap: Boolean): At? {
        if (size <= 0) return null
        val index = at.index.coerceIn(0, size - 1)
        return when {
            index + 1 < size -> at.copy(index = index + 1)
            wrap -> At(at.pass + 1, 0)
            else -> null
        }
    }

    /**
     * The previous step. At step one of a pass after [minPass] that is the last step of the pass before, the step
     * just seen (play orders are stable per seed and pass, so a shuffled pass comes back exactly). [minPass] is the
     * pass the group was on before the current one, so back crosses one pass boundary at most and a group that
     * looped for minutes still leaves at its first step. There, only with [wrap] (on to the last), else null.
     */
    fun previous(at: At, size: Int, wrap: Boolean, minPass: Int = 0): At? {
        if (size <= 0) return null
        val index = at.index.coerceIn(0, size - 1)
        return when {
            index > 0 -> at.copy(index = index - 1)
            at.pass > minPass -> At(at.pass - 1, size - 1)
            wrap -> at.copy(index = size - 1)
            else -> null
        }
    }

    /** The group's item at [itemIndex] (in its list) where it plays in [pass], whose play order is [order]. */
    fun show(itemIndex: Int, order: List<Int>, pass: Int): At? {
        val position = order.indexOf(itemIndex)
        return if (position < 0) null else At(pass, position)
    }

    /**
     * Where the step on screen is after a live edit, in a pass that plays the items [orderIds] in this order: the item
     * [shownId] wherever it went; else (it was removed, or nothing is shown yet) position [index], so the step that
     * took its place plays; position 0 once the group got shorter than that.
     */
    fun keptPosition(shownId: String?, index: Int, orderIds: List<String>): Int {
        val kept = if (shownId == null) -1 else orderIds.indexOf(shownId)
        return when {
            kept >= 0 -> kept
            index in orderIds.indices -> index
            else -> 0
        }
    }
}
