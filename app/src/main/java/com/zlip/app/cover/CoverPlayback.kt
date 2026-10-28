package com.zlip.app.cover

import android.os.SystemClock
import android.util.Log
import com.zlip.app.data.AdvanceMode
import com.zlip.app.data.ContentRepository
import com.zlip.app.data.CoverItem
import com.zlip.app.data.GroupTiming
import com.zlip.app.data.MediaKind
import com.zlip.app.data.framing
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

    private val _soundOn = MutableStateFlow(repository.settings.value.startWithSound)

    /**
     * The cover's sound for this session: clips with audio (not muted) are heard while it is on. Every
     * [startOver] sets it back to [com.zlip.app.data.CoverSettings.startWithSound]; the sound button, the
     * notification and volume up change it until then ([toggleSound], [soundOnForNow]).
     */
    val soundOn: StateFlow<Boolean> = _soundOn.asStateFlow()

    /** Folded live views on screen (a count, so a finishing instance cannot hide a new one). */
    private val liveViews = MutableStateFlow(0)
    val liveViewVisible: StateFlow<Boolean> = liveViews.map { it > 0 }
        .stateIn(scope, SharingStarted.Eagerly, false)

    /** The folded screen saver is resting (dimmed still): nothing should advance or change under it. */
    private val liveResting = MutableStateFlow(false)

    /** The folded cover's menu covers the screen: playback and auto-advance wait until it closes. */
    private val menuOpen = MutableStateFlow(false)

    /** Fingers are framing a photo or video on the cover (or it glides on after them): auto-advance waits. */
    private val framingNow = MutableStateFlow(false)

    /** Bumped on every touch of the cover; restarts the auto-advance timer so it never skips mid-use. */
    private val interactions = MutableStateFlow(0L)

    /** Counts deliberate cover touches; the inner screen's idle cutoff treats each one as activity. */
    val coverTouches: StateFlow<Long> = interactions.asStateFlow()

    /**
     * Bumped on every manual step inside the group on the cover, from any surface (Home, notification, Mini kapak,
     * TalkBack): like a touch it gives the group a full turn again, so going back to see steps is never cut short.
     * Kept apart from [interactions], which also counts as activity for the inner screen's idle cutoff.
     */
    private val manualSteps = MutableStateFlow(0L)

    /** What restarts the item's turn: touches of the cover and manual steps. */
    private val turnRestarts: Long get() = interactions.value + manualSteps.value

    /** Asks open live views to close (the notification's "turn off" while folded). */
    private val _closeLiveView = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val closeLiveView: SharedFlow<Unit> = _closeLiveView.asSharedFlow()

    /** Real-time video position in ms reported by the active video player. */
    private val _videoPositionMs = MutableStateFlow(0L)
    val videoPositionMs: StateFlow<Long> = _videoPositionMs.asStateFlow()

    /** Duration of the current video in ms. */
    private val _videoDurationMs = MutableStateFlow(0L)
    val videoDurationMs: StateFlow<Long> = _videoDurationMs.asStateFlow()

    /** Seek request sent from controls to the active player. */
    private val _seekRequest = MutableSharedFlow<Long>(extraBufferCapacity = 2)
    val seekRequest: SharedFlow<Long> = _seekRequest.asSharedFlow()

    fun updateVideoProgress(positionMs: Long, durationMs: Long) {
        _videoPositionMs.value = positionMs
        _videoDurationMs.value = durationMs
    }

    fun seekTo(positionMs: Long) {
        _seekRequest.tryEmit(positionMs)
        _videoPositionMs.value = positionMs
        noteInteraction()
    }

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

    /** The id of [groupStepper]'s group, as state: the group playing on a cover right now. */
    private val steppedGroup = MutableStateFlow<String?>(null)

    /** The step a group on the cover shows, and where it is in the group's play order. */
    private val stepShown = MutableStateFlow<StepShown?>(null)

    /**
     * The group playing on a cover right now and the step it shows, as (group id, step id); null while no group plays
     * on one. The group editor's preview is a still of this step, so it never shows something else than the cover.
     */
    val liveGroupStep: StateFlow<Pair<String, String>?> = combine(steppedGroup, stepShown) { groupId, shown ->
        shown?.takeIf { it.groupId == groupId }?.let { it.groupId to it.child.id }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    /** The step a group on the cover shows, as (group id, step item id), for "Grup · 2/4" in the notification. */
    val groupStep: StateFlow<Pair<String, String>?> = stepShown.map { shown -> shown?.let { it.groupId to it.child.id } }
        .stateIn(scope, SharingStarted.Eagerly, null)

    /**
     * Where the group on the cover is ("Adım 2/5"): its step in this pass's play order, so a shuffled group still
     * counts 1, 2, 3. Null unless the selected item is a group that reported a step.
     */
    val groupProgress: StateFlow<GroupProgress?> = combine(repository.selectedItem, stepShown, CoverNav::progress)
        .stateIn(scope, SharingStarted.Eagerly, null)

    /**
     * A group is on the cover and the rotation has something else to go to: [nextItem] and [previousItem] leave the
     * group. A group alone goes round instead, so there is nowhere to skip to.
     */
    val itemSkipAvailable: StateFlow<Boolean> = combine(repository.selectedItem, repository.sequence, CoverNav::itemSkipAvailable)
        .stateIn(scope, SharingStarted.Eagerly, false)

    /** [next] and [previous] go somewhere ([CoverNav.canMove]); a single item alone in the rotation has nowhere to go. */
    val movesAvailable: StateFlow<Boolean> = combine(repository.selectedItem, repository.sequence, CoverNav::canMove)
        .stateIn(scope, SharingStarted.Eagerly, false)

    private val _manualMoves = MutableSharedFlow<CoverMove>(extraBufferCapacity = 4)

    /**
     * One event per move the user made ([next], [previous], [nextItem], [previousItem]) from any surface, sent once
     * the move happened, saying whether it stayed inside the group; never for auto-advance. The cover answers it with
     * a short "Adım 3/5" (or the item's name) hint.
     */
    val manualMoves: SharedFlow<CoverMove> = _manualMoves.asSharedFlow()

    /**
     * What the cover shows: the selected item, or the step its group is on. The step as the group holds it now, so an
     * edit of that step (a new framing) shows here at once rather than when the group next moves on.
     */
    val shownItem: StateFlow<CoverItem?> = combine(repository.selectedItem, stepShown) { selected, step ->
        val shown = step?.takeIf { it.groupId == selected?.id }?.child
        if (shown == null) selected else (selected as? CoverItem.Group)?.items?.firstOrNull { it.id == shown.id } ?: shown
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
                    _videoPositionMs.value = 0L
                    _videoDurationMs.value = 0L
                }
        }
        // The setting changed on the phone: the cover follows it now, not only from its next start.
        scope.launch {
            repository.settings.map { it.startWithSound }.distinctUntilChanged().drop(1).collect { _soundOn.value = it }
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
     * [session], no pause, the sound as the user set it to start, and another try for media that failed or
     * held its last frame (or lost its sound to a call or music). The selected item, toy results and settings
     * stay; so does the time the item was already seen, so auto-advance still moves on although every glance
     * restarts it.
     */
    fun startOver() {
        _paused.value = false
        endedItem.value = null
        failedItems.value = emptySet()
        _soundOn.value = repository.settings.value.startWithSound
        stepShown.value = null
        _videoPositionMs.value = 0L
        _videoDurationMs.value = 0L
        _session.update { it + 1 }
        Log.i(TAG, "Starting over, session ${_session.value}")
    }

    /**
     * Volume up on the folded cover: when what it shows has sound to play ([hasSoundToSwitch]) the sound comes
     * on, like most video apps, until the next [startOver]. A press meant for something else changes nothing.
     */
    fun soundOnForNow() {
        if (hasSoundToSwitch(repository.currentSelected)) _soundOn.value = true
    }

    /** The cover was turned off and on again: its sound starts as set, without starting anything else over. */
    fun resetSound() {
        _soundOn.value = repository.settings.value.startWithSound
    }

    /** The sound button (cover controls, cover menu, notification): on or off until the next [startOver]. */
    fun toggleSound() {
        _soundOn.update { !it }
    }

    /**
     * A clip on the cover lost its sound for good: another app took the audio (music, a video) or headphones
     * were unplugged. The cover stays silent, and its sound button says so, until the user turns sound on
     * again or the cover starts over.
     */
    fun soundLost() {
        _soundOn.value = false
    }

    /**
     * Next step of the group on the cover (stories-style), then the next item after its last step. A single item
     * goes on to the next item in list order: manual moves never shuffle, so [previous] always undoes it.
     */
    fun next() = move(forward = true, byStep = true)

    /** Previous step of the group on the cover, then the previous item before its first step. */
    fun previous() = move(forward = false, byStep = true)

    /** The next item in list order, leaving a group on the cover whatever step it is on. */
    fun nextItem() = move(forward = true, byStep = false)

    /** The previous item in list order, leaving a group on the cover whatever step it is on. */
    fun previousItem() = move(forward = false, byStep = false)

    private fun move(forward: Boolean, byStep: Boolean) {
        // While an editor streams a draft the selection is hidden; changing it would be invisible.
        if (editing.value) return
        val before = repository.currentSelected?.id
        val stayed = CoverNav.staysInGroup(byStep) { stepGroup(forward) }
        if (stayed) {
            credit.clear()
            manualSteps.update { it + 1 }
        } else {
            if (forward) repository.selectNext(shuffle = CoverNav.MANUAL_SHUFFLE) else repository.selectPrevious()
            // Nowhere to go (one item in the rotation): nothing moved, so nothing resumes or says where it went.
            if (repository.currentSelected?.id == before) return
        }
        _paused.value = false
        _manualMoves.tryEmit(if (stayed) CoverMove.STEP else CoverMove.ITEM)
    }

    /**
     * Shows the step [stepId] of the group [groupId] on the cover, and plays it: a tap on a step in the group editor,
     * whose draft the cover shows. False when that group is not playing on a cover or has no such step.
     */
    fun showGroupStep(groupId: String, stepId: String): Boolean {
        val (playingId, stepper) = groupStepper ?: return false
        if (playingId != groupId || !stepper.showStep(stepId)) return false
        _paused.value = false
        return true
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

    /** Fingers frame a photo or video on the cover ([framingNow]); the touch that started it already counted as use. */
    fun setFraming(active: Boolean) {
        framingNow.value = active
    }

    /**
     * The small window's Fit / Fill: the photo or video the cover shows (a group's step too) switches to the other fit,
     * not zoomed and centred, as the cover's own button does. Anything else is left alone.
     */
    fun switchShownMediaFit() {
        val media = shownItem.value as? CoverItem.Media ?: return
        repository.setMediaFraming(media.id, media.framing.fitSwitched())
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

        override fun onStepAt(child: CoverItem, position: Int, count: Int) {
            val changed = stepShown.value?.child?.id != child.id
            stepShown.value = StepShown(itemId, child, position, count)
            if (changed) {
                _videoPositionMs.value = 0L
                _videoDurationMs.value = 0L
            }
            onGroupStep(child)
        }

        override fun attachStepper(stepper: GroupStepper) {
            groupStepper = itemId to stepper
            steppedGroup.value = itemId
        }

        override fun detachStepper(stepper: GroupStepper) {
            if (groupStepper?.second === stepper) {
                groupStepper = null
                steppedGroup.value = null
            }
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
                combine(
                    coverShowsContent,
                    liveViewVisible,
                    liveResting,
                    // The menu over the cover, or fingers framing a picture on it.
                    combine(menuOpen, framingNow) { menu, frame -> menu || frame },
                    combine(interactions, manualSteps) { touches, steps -> touches + steps },
                ) { showing, live, rest, held, touch ->
                    ((showing || (live && !rest)) && !held) to touch
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

    /** One item's turn: [mode] SMART plays videos to their end; [minMs] is the photo time. */
    private data class Plan(val item: CoverItem, val mode: AdvanceMode, val minMs: Long, val shuffle: Boolean, val repeat: Boolean)

    private suspend fun runPlan(plan: Plan) {
        val id = plan.item.id
        val seenMs = credit.of(id)
        val started = SystemClock.uptimeMillis()
        // A touch or a manual step cancels this plan only after clearing the credit; this tells them from the rest.
        val touchesAtStart = turnRestarts
        try {
            awaitTurnEnd(plan, seenMs)
        } catch (e: CancellationException) {
            credit.addCutShort(id, SystemClock.uptimeMillis() - started, touchesAtStart, turnRestarts)
            throw e
        }
        credit.clear()
        repository.advance(plan.shuffle, plan.repeat)
    }

    /** Waits until [plan]'s item has had its time, less the [seenMs] it was already on screen before. */
    private suspend fun awaitTurnEnd(plan: Plan, seenMs: Long): Unit = coroutineScope {
        val id = plan.item.id
        val video = (plan.item as? CoverItem.Media)?.takeIf { it.kind == MediaKind.VIDEO }
        if (plan.item is CoverItem.Group && id !in failedItems.value) {
            // A group is a sequence the user built: it plays to its end in SMART and TIMER alike (the timer
            // would cut it mid-way), and moves on early only when none of its steps can play.
            if (endedItem.value == id) {
                // With repeat off it already played its pass and holds its last step, like a play-once clip.
                delay(AdvanceWait.MIN_AFTER_START_MS)
            } else {
                withTimeoutOrNull(AdvanceWait.passCapMs(GroupTiming.maxPassMs(plan.item), seenMs)) {
                    merge(passOrFailure(id), endedItem.filter { it == id }.map { }).first()
                }
            }
        } else if (plan.mode == AdvanceMode.SMART && video != null && id !in failedItems.value) {
            // Videos to the end: the photo time does not apply, the clip plays one full pass from its start.
            // Listening starts first, so a short clip's first pass is not missed.
            val firstPass = async { passOrFailure(id).first() }
            withTimeoutOrNull(AdvanceWait.videoCapMs(video, seenMs)) {
                delay(AdvanceWait.MIN_AFTER_START_MS)
                val wait = AdvanceWait.videoWait(
                    failed = id in failedItems.value,
                    passSeen = firstPass.isCompleted,
                    holdsLastFrame = !video.loop && (firstPass.isCompleted || endedItem.value == id),
                    // Unknown length (0) counts as short: it may be.
                    shortClip = video.durationMs <= AdvanceWait.MIN_AFTER_START_MS,
                )
                when (wait) {
                    AdvanceWait.VideoWait.DONE -> Unit
                    AdvanceWait.VideoWait.FIRST_PASS -> firstPass.await()
                    AdvanceWait.VideoWait.CURRENT_PASS -> passOrFailure(id).first()
                }
            }
            firstPass.cancel()
        } else {
            // Stills get the photo time; with videos to the end, GIFs, animations and marquees then finish the
            // pass they are in. Listening starts first, so a short GIF's first pass is not missed.
            val firstCycle = async { cycles.first { it == id } }
            val minimumMs = AdvanceWait.minimumMs(plan.minMs, seenMs)
            delay(minimumMs)
            if (plan.mode == AdvanceMode.SMART && GroupTiming.isCycleBased(plan.item) && id !in failedItems.value) {
                val holdsLastFrame = plan.item is CoverItem.Media && !plan.item.loop &&
                    (firstCycle.isCompleted || endedItem.value == id)
                if (!holdsLastFrame) {
                    // Let the GIF, animation or marquee finish its current pass (at most one more), or give up if it fails.
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

/** Where the group [groupId] on the cover is: [step] (1-based, in this pass's play order) of [count]. */
data class GroupProgress(val groupId: String, val step: Int, val count: Int)

/** What a manual move did: [STEP] stayed inside the group on the cover, [ITEM] went to another item. */
enum class CoverMove { STEP, ITEM }

/** The step [child] that the group [groupId] shows, at [position] (1-based) of the [count] steps in its play order. */
internal data class StepShown(val groupId: String, val child: CoverItem, val position: Int, val count: Int)

/** How manual moves go through groups and the playlist. Pure, so it is unit-tested. */
internal object CoverNav {
    /**
     * Manual moves follow the list order even with shuffle on, so Önceki always undoes Sonraki; shuffle is for
     * auto-advance alone.
     */
    const val MANUAL_SHUFFLE = false

    /**
     * Whether a manual move stays inside the group on the cover: a step move ([byStep]) tries [step] first and stays
     * when it moved; an item move never steps, so it skips the rest of the group.
     */
    inline fun staysInGroup(byStep: Boolean, step: () -> Boolean): Boolean = byStep && step()

    /** [shown]'s place in its group while that group is the [selected] item; otherwise none. */
    fun progress(selected: CoverItem?, shown: StepShown?): GroupProgress? {
        if (shown == null || selected !is CoverItem.Group || shown.groupId != selected.id) return null
        return GroupProgress(shown.groupId, shown.position, shown.count)
    }

    /** A group is [selected] and the [sequence] has another item to move to. */
    fun itemSkipAvailable(selected: CoverItem?, sequence: List<CoverItem>): Boolean =
        selected is CoverItem.Group && sequence.any { it.id != selected.id }

    /**
     * Whether a manual move goes anywhere: a group steps (alone, it goes round), and anything else moves to another
     * item of the [sequence], which an item picked by hand outside it always has. A single item alone goes nowhere.
     */
    fun canMove(selected: CoverItem?, sequence: List<CoverItem>): Boolean =
        selected is CoverItem.Group || sequence.any { it.id != selected?.id }
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

    /**
     * The longest wait for [video] playing to its end: its length ([GroupTiming.UNKNOWN_VIDEO_MS] when unknown) plus room.
     * The room also covers a clip shorter than [MIN_AFTER_START_MS], which finishes its second pass.
     */
    fun videoCapMs(video: CoverItem.Media, seenMs: Long): Long = passCapMs(GroupTiming.videoPassMs(video), seenMs)

    /** What a video playing to its end still waits for once [MIN_AFTER_START_MS] are over. */
    enum class VideoWait {
        /** Moves on now: it failed, or a play-once clip already ended and holds its last frame. */
        DONE,

        /** The end of its first pass, still playing. */
        FIRST_PASS,

        /** A looping clip shorter than the minimum: the end of the pass it is in now. */
        CURRENT_PASS,
    }

    /**
     * [shortClip]: the clip is (or may be) shorter than [MIN_AFTER_START_MS]. For a longer one an end seen during the
     * minimum ends the pass that was running when the plan started again (after a pause near its end, say): done.
     */
    fun videoWait(failed: Boolean, passSeen: Boolean, holdsLastFrame: Boolean, shortClip: Boolean): VideoWait = when {
        failed || holdsLastFrame -> VideoWait.DONE
        !passSeen -> VideoWait.FIRST_PASS
        shortClip -> VideoWait.CURRENT_PASS
        else -> VideoWait.DONE
    }
}

/**
 * Whether the cover's sound switch means anything for [item]: a clip that may play with sound
 * ([CoverItem.Media.playsSound]), or a group with such a clip among its steps. A group offers it on every step,
 * so the controls do not jump around as photos and clips take turns, and its sound can be set before the clip
 * comes. Collage cells always play silent, so a collage has none. Pure, so it is unit-tested.
 */
internal fun hasSoundToSwitch(item: CoverItem?): Boolean = when (item) {
    is CoverItem.Media -> item.playsSound
    is CoverItem.Group -> item.items.any { it is CoverItem.Media && it.playsSound }
    else -> false
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
