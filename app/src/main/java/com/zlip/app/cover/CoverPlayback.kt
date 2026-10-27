package com.zlip.app.cover

import android.os.SystemClock
import android.util.Log
import com.zlip.app.data.AdvanceMode
import com.zlip.app.data.ContentRepository
import com.zlip.app.data.CoverItem
import com.zlip.app.data.GroupTiming
import com.zlip.app.data.MediaKind
import com.zlip.app.render.GroupStepper
import com.zlip.app.render.PlaybackEvents
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Playback state shared by the cover screen, the folded live view, the notification and the
 * inner-screen controls: pause/resume, next/previous and auto-advance through the playlist.
 */
class CoverPlayback(
    private val repository: ContentRepository,
    private val scope: CoroutineScope,
) {
    private val _paused = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    private val _session = MutableStateFlow(0)

    /**
     * Bumped by [startOver]. The cover content is keyed on it, so a new session plays everything from its
     * beginning: a video at 0:00, a GIF at its first frame, a marquee at its start, a group at step one.
     */
    val session: StateFlow<Int> = _session.asStateFlow()

    private val _soundOn = MutableStateFlow<Set<String>>(emptySet())

    /** Clips whose sound volume up turned on for this session only; their saved setting stays as it is. */
    val soundOn: StateFlow<Set<String>> = _soundOn.asStateFlow()

    /** Folded live views on screen (a count, so a finishing instance cannot hide a new one). */
    private val liveViews = MutableStateFlow(0)
    val liveViewVisible: StateFlow<Boolean> = liveViews.map { it > 0 }
        .stateIn(scope, SharingStarted.Eagerly, false)

    /** The folded screen saver is resting (dimmed still): nothing should advance or change under it. */
    private val liveResting = MutableStateFlow(false)

    /** The folded cover's menu covers the screen: playback and auto-advance wait until it closes. */
    private val menuOpen = MutableStateFlow(false)

    /** Bumped on every touch of the cover; restarts the auto-advance timer so it never skips mid-use. */
    private val interactions = MutableStateFlow(0L)

    /** Counts deliberate cover touches; the inner screen's idle cutoff treats each one as activity. */
    val coverTouches: StateFlow<Long> = interactions.asStateFlow()

    /** Asks open live views to close (the notification's "turn off" while folded). */
    private val _closeLiveView = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val closeLiveView: SharedFlow<Unit> = _closeLiveView.asSharedFlow()

    /**
     * An editor's draft is on the open-phone cover. The folded live view never shows drafts (the editor is
     * out of sight), so it does not count there.
     */
    private val editing: StateFlow<Boolean> = combine(repository.draft, liveViewVisible) { draft, live -> draft != null && !live }
        .stateIn(scope, SharingStarted.Eagerly, false)

    /** Ids of items that just finished one full pass (a video reached its end, a marquee wrapped). */
    private val cycles = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** A play-once video that is holding its last frame (a state, so a restarted plan still sees it). */
    private val endedItem = MutableStateFlow<String?>(null)

    /** Items whose media could not be played; smart advance must not wait for them. */
    private val failedItems = MutableStateFlow<Set<String>>(emptySet())

    /** The group on screen (by item id) and how to step through it; main thread only. */
    private var groupStepper: Pair<String, GroupStepper>? = null

    /** The step a group on the cover shows (group id to step). */
    private val stepShown = MutableStateFlow<Pair<String, CoverItem>?>(null)

    /** The step a group on the cover shows, as (group id, step item id), for "Grup · 2/4" in the notification. */
    val groupStep: StateFlow<Pair<String, String>?> = stepShown.map { shown -> shown?.let { (group, step) -> group to step.id } }
        .stateIn(scope, SharingStarted.Eagerly, null)

    /** What the cover shows: the selected item, or the step its group is on. */
    val shownItem: StateFlow<CoverItem?> = combine(repository.selectedItem, stepShown) { selected, step ->
        step?.takeIf { it.first == selected?.id }?.second ?: selected
    }.stateIn(scope, SharingStarted.Eagerly, null)

    /**
     * Players heard on a cover right now, by the item whose events they report to (a group's steps report
     * to the group). A count, so a clip going quiet as the next one starts cannot hide it.
     */
    private val heard = MutableStateFlow<Map<String, Int>>(emptyMap())

    /**
     * The selected item is heard on the cover right now (not paused, no menu over it): someone is watching.
     * Reported by the player itself, so a clip that ended, failed, lost the audio to a call or music, or is
     * paused never counts, whatever its saved sound setting says.
     */
    val playingWithSound: StateFlow<Boolean> = combine(repository.selectedItem, heard, paused, menuOpen) { selected, heardNow, isPaused, menu ->
        !isPaused && !menu && HeardCount.isHeard(heardNow, selected?.id)
    }.stateIn(scope, SharingStarted.Eagerly, false)

    /** How long the selected item was already seen in earlier sessions; main thread only. */
    private val credit = VisibleCredit()

    init {
        // New content from the phone (another item, or an editor opening/closing) should play rather
        // than inherit an old pause. Tracked here, not in a composition, so a session restart keeps it.
        // The draft's presence, not [editing]: that also flips with every cover sleep and wake while an
        // editor is left open, and must not wipe the time seen each time.
        scope.launch {
            combine(repository.selectedItem.map { it?.id }, repository.draft.map { it != null }) { id, hasDraft -> id to hasDraft }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    _paused.value = false
                    credit.clear()
                }
        }
    }

    fun togglePause() {
        _paused.value = !_paused.value
    }

    fun resume() {
        _paused.value = false
    }

    /**
     * Plays everything from its beginning (the phone was folded or opened, the folded cover woke): a new
     * [session], no pause, and another try for media that failed or held its last frame. The selected item,
     * toy results and settings stay; so does the time the item was already seen, so auto-advance still
     * moves on although every glance restarts it.
     */
    fun startOver() {
        _paused.value = false
        endedItem.value = null
        failedItems.value = emptySet()
        _soundOn.value = emptySet()
        stepShown.value = null
        _session.update { it + 1 }
        Log.i(TAG, "Starting over, session ${_session.value}")
    }

    /** Volume up on a muted clip that has sound: it plays with sound until the next [startOver]. */
    fun soundOnForNow(clip: CoverItem.Media) {
        if (clip.kind == MediaKind.VIDEO && clip.hasAudio && !clip.sound) _soundOn.update { it + clip.id }
    }

    /** The sound button: switches [clip]'s saved sound setting; sound turned on only for now goes off as well. */
    fun toggleSound(clip: CoverItem.Media) {
        val on = clip.sound || clip.id in _soundOn.value
        _soundOn.update { it - clip.id }
        if (!on) repository.upsert(clip.copy(sound = true)) else if (clip.sound) repository.upsert(clip.copy(sound = false))
    }

    /** Next step of the group on the cover (stories-style), then the next item after its last step. */
    fun next() {
        // While an editor streams a draft the selection is hidden; changing it would be invisible.
        if (editing.value) return
        _paused.value = false
        if (stepGroup(forward = true)) return
        repository.selectNext(shuffle = repository.settings.value.shuffle)
    }

    fun previous() {
        if (editing.value) return
        _paused.value = false
        if (stepGroup(forward = false)) return
        repository.selectPrevious()
    }

    /** Moves inside the group on the cover; false when there is no group or no step that way. */
    private fun stepGroup(forward: Boolean): Boolean {
        val (groupId, stepper) = groupStepper ?: return false
        if (groupId != repository.currentSelected?.id) return false
        // Alone in the sequence there is nothing to move on to: the group goes round instead.
        val alone = repository.currentSequence.none { it.id != groupId }
        return if (forward) stepper.next(wrap = alone) else stepper.previous(wrap = alone)
    }

    fun setLiveViewResting(resting: Boolean) {
        liveResting.value = resting
    }

    fun setMenuOpen(open: Boolean) {
        menuOpen.value = open
    }

    fun noteInteraction() {
        // Someone is using the cover: the item gets its full time again from now.
        credit.clear()
        interactions.update { it + 1 }
    }

    fun liveViewStarted() = liveViews.update { it + 1 }

    fun liveViewStopped() = liveViews.update { (it - 1).coerceAtLeast(0) }

    fun requestCloseLiveView() {
        _closeLiveView.tryEmit(Unit)
    }

    /**
     * What the renderer of [itemId] reports: finished passes, a held last frame, playback failures. A group
     * also offers its steps to [next]/[previous] and reports each step it shows to [onGroupStep].
     */
    fun eventsFor(itemId: String, onGroupStep: (CoverItem) -> Unit = {}): PlaybackEvents = object : PlaybackEvents {
        override fun onCycle() {
            cycles.tryEmit(itemId)
        }

        override fun onEnded(ended: Boolean) {
            if (ended) endedItem.value = itemId else endedItem.compareAndSet(itemId, null)
        }

        override fun onFailed(failed: Boolean) {
            failedItems.update { if (failed) it + itemId else it - itemId }
        }

        override fun onAudible(audible: Boolean) {
            heard.update { HeardCount.update(it, itemId, audible) }
        }

        override fun onStep(child: CoverItem) {
            stepShown.value = itemId to child
            onGroupStep(child)
        }

        override fun attachStepper(stepper: GroupStepper) {
            groupStepper = itemId to stepper
        }

        override fun detachStepper(stepper: GroupStepper) {
            if (groupStepper?.second === stepper) groupStepper = null
        }
    }

    /**
     * Auto-advance while the cover (or the folded live view) is on, not paused and no editor draft or menu
     * is shown. Restarts whenever the item, the settings or the [session] change, so manual swipes and
     * "next" reset it, and content that starts over is waited for from its beginning.
     *
     * While the open-phone cover [mirroring] the inner screen its content is out of sight: nothing moves on
     * unseen, and the cover resumes at the item it left.
     */
    fun runAutoAdvance(status: StateFlow<CoverStatus>, mirroring: Flow<Boolean>) {
        val coverShowsContent = combine(status, mirroring) { s, mirrored -> s == CoverStatus.ACTIVE && !mirrored }
        scope.launch {
            combine(
                combine(coverShowsContent, liveViewVisible, liveResting, menuOpen, interactions) { showing, live, rest, menu, touch ->
                    ((showing || (live && !rest)) && !menu) to touch
                }.distinctUntilChanged(),
                repository.settings.map { Triple(it.advanceMode, it.advanceSeconds, it.shuffle to it.repeat) }.distinctUntilChanged(),
                combine(paused, editing, session) { isPaused, isEditing, current -> (isPaused || isEditing) to current },
                // The whole item (StateFlow already skips equal values): an edit that switches scrolling or
                // looping must reach the plan, not a stale copy.
                repository.selectedItem,
            ) { (active, _), (mode, seconds, order), (held, _), item ->
                if (active && !held && mode != AdvanceMode.OFF && item != null) {
                    Plan(item, mode, seconds * 1_000L, shuffle = order.first, repeat = order.second)
                } else {
                    null
                }
            }.collectLatest { plan -> if (plan != null) runPlan(plan) }
        }
    }

    private data class Plan(val item: CoverItem, val mode: AdvanceMode, val minMs: Long, val shuffle: Boolean, val repeat: Boolean)

    private suspend fun runPlan(plan: Plan) {
        val id = plan.item.id
        val seenMs = credit.of(id)
        val started = SystemClock.uptimeMillis()
        // A touch cancels this plan only after noteInteraction() cleared the credit; this tells the two apart.
        val touchesAtStart = interactions.value
        try {
            awaitTurnEnd(plan, seenMs)
        } catch (e: CancellationException) {
            credit.addCutShort(id, SystemClock.uptimeMillis() - started, touchesAtStart, interactions.value)
            throw e
        }
        credit.clear()
        repository.advance(plan.shuffle, plan.repeat)
    }

    /** Waits until [plan]'s item has had its time, less the [seenMs] it was already on screen before. */
    private suspend fun awaitTurnEnd(plan: Plan, seenMs: Long): Unit = coroutineScope {
        val id = plan.item.id
        if (plan.item is CoverItem.Group && id !in failedItems.value) {
            // A group is a sequence the user built: it plays to its end in SMART and TIMER alike (the timer
            // would cut it mid-way), and moves on early only when none of its steps can play.
            withTimeoutOrNull(AdvanceWait.passCapMs(GroupTiming.maxPassMs(plan.item), seenMs)) { passOrFailure(id).first() }
        } else {
            // Start listening before waiting, so a short video's first pass is not missed.
            val firstCycle = async { cycles.first { it == id } }
            val minimumMs = AdvanceWait.minimumMs(plan.minMs, seenMs)
            delay(minimumMs)
            if (plan.mode == AdvanceMode.SMART && GroupTiming.isCycleBased(plan.item) && id !in failedItems.value) {
                val holdsLastFrame = plan.item is CoverItem.Media && !plan.item.loop &&
                    (firstCycle.isCompleted || endedItem.value == id)
                if (!holdsLastFrame) {
                    // Let the video, GIF or marquee finish its current pass (at most one more), or give up if it fails.
                    val capMs = AdvanceWait.passCapMs(plan.minMs + GroupTiming.extraWaitMs(plan.item), seenMs)
                    withTimeoutOrNull(capMs - minimumMs) { passOrFailure(id).first() }
                }
            }
            firstCycle.cancel()
        }
    }

    private fun passOrFailure(id: String): Flow<Unit> = merge(
        cycles.filter { it == id }.map { },
        failedItems.filter { id in it }.map { },
    )

    private companion object {
        /** Grep the device log for this to see when the cover started over. */
        const val TAG = "ZlipSession"
    }
}

/**
 * How long auto-advance waits for an item that was already on screen for a while in earlier sessions.
 * Every fold, unfold and cover wake restarts the item, so with glances shorter than the item's time it
 * would never move on; the time seen adds up instead. Pure, so it is unit-tested.
 */
internal object AdvanceWait {
    /** Never flips the moment the cover wakes: whatever shows gets at least this long. */
    const val MIN_AFTER_START_MS = 3_000L

    /** The time an item stays ([minMs]: the timer, or a still's time in smart mode) less what was [seenMs]. */
    fun minimumMs(minMs: Long, seenMs: Long): Long = maxOf(minMs - seenMs, MIN_AFTER_START_MS)

    /**
     * The longest wait for an item that ends its own pass (a video, GIF, marquee, animation, collage or
     * group), whose pass takes at most [maxPassMs]: once that much was seen it moves on soon after a wake.
     */
    fun passCapMs(maxPassMs: Long, seenMs: Long): Long = maxOf(maxPassMs - seenMs, MIN_AFTER_START_MS)
}

/**
 * How many players of each item are heard. A step going quiet just after the next one started (a group
 * cutting from clip to clip, an old session's player released late) must not hide the one still playing,
 * and a stray "quiet" never goes below nothing. Pure, so it is unit-tested.
 */
internal object HeardCount {
    fun update(counts: Map<String, Int>, id: String, audible: Boolean): Map<String, Int> {
        val count = (counts[id] ?: 0) + if (audible) 1 else -1
        return if (count > 0) counts + (id to count) else counts - id
    }

    fun isHeard(counts: Map<String, Int>, id: String?): Boolean = id != null && (counts[id] ?: 0) > 0
}

/** The visible time of one item, carried across sessions; another item starts again from nothing. */
internal class VisibleCredit {
    private var itemId: String? = null
    private var seenMs = 0L

    fun of(id: String): Long = if (id == itemId) seenMs else 0L

    fun add(id: String, ms: Long) {
        if (id != itemId) {
            itemId = id
            seenMs = 0L
        }
        seenMs += ms.coerceAtLeast(0L)
    }

    /**
     * The [ms] a plan that was cut short had shown [id]. Cut short by sleep, a fold or a setting, it counts
     * next time. Cut short by a touch (the touch count moved from [touchesAtStart] to [touchesNow]) it does
     * not: the touch gives the item its full time again, so a toy mid-roll is never skipped.
     */
    fun addCutShort(id: String, ms: Long, touchesAtStart: Long, touchesNow: Long) {
        if (touchesNow == touchesAtStart) add(id, ms)
    }

    fun clear() {
        itemId = null
        seenMs = 0L
    }
}
