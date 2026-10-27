package com.zlip.app.cover

import android.os.SystemClock
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.zlip.app.R
import com.zlip.app.cover.mirror.MirrorController
import com.zlip.app.cover.mirror.MirrorGestureHint
import com.zlip.app.cover.mirror.MirrorState
import com.zlip.app.cover.mirror.MirrorSurface
import com.zlip.app.cover.mirror.steerMirror
import com.zlip.app.data.ContentRepository
import com.zlip.app.data.CoverItem
import com.zlip.app.data.CoverSettings
import com.zlip.app.data.MediaFit
import com.zlip.app.data.MediaFocus
import com.zlip.app.data.MediaFraming
import com.zlip.app.data.MediaStorage
import com.zlip.app.data.showsQr
import com.zlip.app.render.CoverContent
import com.zlip.app.render.LocalAllowSound
import com.zlip.app.render.LocalAnimationFps
import com.zlip.app.render.LocalHoldGroupStep
import com.zlip.app.render.LocalMediaFraming
import com.zlip.app.render.LocalOnSoundLost
import com.zlip.app.render.LedStyle
import com.zlip.app.render.LocalVideoOverlay
import com.zlip.app.render.Playback
import com.zlip.app.render.ledMatrix
import com.zlip.app.ui.theme.BrandGradient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.pow
import kotlin.random.Random

/**
 * What the cover shows, with its controls.
 *
 * Phone open ([lockTouches]): the cover faces the palm, so touches are ignored until a double tap (a single tap says
 * so) and lock again after 3 s; while unlocked the controls show, a swipe moves on and a tap on empty space is done
 * with them (it locks again). Its [onClose], the controls' ×, turns the cover off.
 * Folded screen saver: touches work directly. A tap shows the controls, a sideways swipe moves on, a swipe up opens
 * the menu ([CoverMenu]) and a swipe down hands the cover back to its lock screen ([onDismiss]); [onClose] ends it.
 * The open phone has no menu: its cover faces other people.
 *
 * Moves follow one model everywhere: in a group, swipes and the main buttons step through it (on to the next item
 * after its last step), and two labelled pills leave it for another item; a single item moves to the next one. After a
 * move the user made while the controls are hidden, a short hint says where it went. TalkBack gets every move, pause
 * and close as actions on both covers.
 *
 * A photo or video (a group's step too) frames like a phone's gallery wherever touches work: two fingers zoom it, one
 * finger moves it while it is zoomed in (it then stays the item on screen), and the controls switch Fit / Fill, which
 * is also the way back from any zoom. The framing is saved for the item once the fingers let go ([CoverFraming]).
 *
 * The content (not the mirror, not the controls) is keyed on [CoverPlayback.session], so whenever the
 * cover starts over everything on it plays from its beginning.
 *
 * [ambient] is the screen saver's resting state: a dimmed still frame (no decoding) under the clock.
 *
 * [mirror] is the open phone's screen mirroring (the folded screen saver passes none): while it runs the
 * cover shows the inner screen instead, and once unlocked it is steered like a map: drag, fling, pinch, and a tap
 * between the whole screen and filled ([steerMirror]).
 * [inPip]: Zlip is only its small window, so no editor is in view.
 *
 * [onUserActivity]: the user did something here that is not a touch the host sees, a TalkBack action or a
 * click in the menu or controls (TalkBack's double tap sends no touch). The folded screen saver wakes and
 * keeps its stay-lit time running from then, as for a touch.
 */
@Composable
fun CoverScreen(
    repository: ContentRepository,
    playback: CoverPlayback,
    lockTouches: Boolean = true,
    onClose: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    ambient: Boolean = false,
    showClock: Boolean = false,
    mirror: MirrorController? = null,
    inPip: Boolean = false,
    onUserActivity: () -> Unit = {},
) {
    val selected by repository.selectedItem.collectAsState()
    val draft by repository.draft.collectAsState()
    val sequence by repository.sequence.collectAsState()
    val settings by repository.settings.collectAsState()
    val paused by playback.paused.collectAsState()
    val session by playback.session.collectAsState()
    val soundOn by playback.soundOn.collectAsState()
    val groupProgress by playback.groupProgress.collectAsState()
    val itemSkipAvailable by playback.itemSkipAvailable.collectAsState()
    val mirrorOn = mirror?.state?.collectAsState()?.value == MirrorState.ON
    val mirroring = mirror?.takeIf { mirrorOn }
    val currentMirroring by rememberUpdatedState(mirroring)
    // Drafts belong to the open phone while the editor is in view; otherwise the cover keeps the playlist.
    val editing = lockTouches && draft != null && !inPip
    val item = if (editing) draft else selected
    // A group reports the step on screen, so LED and brightness follow that step rather than the whole group.
    // Seeded with step one, where every showing starts, so the first frame is never judged by the whole group
    // (a QR step anywhere in it would flash full brightness without LED dots, and rebuild a video's surface).
    var groupStep by remember(item?.id, session) { mutableStateOf((item as? CoverItem.Group)?.items?.firstOrNull()) }
    val events = remember(item?.id, session) { item?.let { playback.eventsFor(it.id) { step -> groupStep = step } } }
    val onSoundLost = remember(playback) { { playback.soundLost() } }
    val lock = remember { TouchLock(autoLock = lockTouches) }
    var unlocked by remember { mutableStateOf(false) }
    var controlsShown by remember { mutableStateOf(false) }
    val menuAllowed = !lockTouches
    var menuShown by remember { mutableStateOf(false) }
    var lastTouch by remember { mutableLongStateOf(0L) }
    val controlsVisible = if (lockTouches) unlocked else controlsShown && !menuShown
    val currentOnDismiss by rememberUpdatedState(onDismiss ?: onClose)
    val currentOnUserActivity by rememberUpdatedState(onUserActivity)
    val context = LocalContext.current
    val accessibility = remember { context.getSystemService(AccessibilityManager::class.java) }
    // TalkBack's explore by touch: its double tap never reaches the touch lock, so the open phone answers actions.
    var talkBack by remember { mutableStateOf(accessibility.isTouchExplorationEnabled) }
    DisposableEffect(accessibility) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { enabled -> talkBack = enabled }
        accessibility.addTouchExplorationStateChangeListener(listener)
        talkBack = accessibility.isTouchExplorationEnabled
        onDispose { accessibility.removeTouchExplorationStateChangeListener(listener) }
    }
    val showControlsLabel = stringResource(R.string.cover_show_controls)
    val hideControlsLabel = stringResource(R.string.cover_controls_hide)
    val openMenuLabel = stringResource(R.string.cover_menu_open)
    val lockScreenLabel = stringResource(R.string.cover_to_lock_screen)
    // The same words as Home, the notification and the Mini kapak on the open phone; the screen saver closes itself.
    val closeLabel = stringResource(if (lockTouches) R.string.action_cover_off else R.string.cover_close)
    // What the moves do here, the same as the buttons: steps inside a group, items otherwise.
    val inGroup = !editing && item is CoverItem.Group
    val itemSkip = !editing && itemSkipAvailable
    // A group alone in the playlist goes round its steps; a single item alone has nowhere to go.
    val canSkip = !editing && (sequence.size > 1 || item is CoverItem.Group)
    val previousLabel = stringResource(if (inGroup) R.string.action_previous_step else R.string.action_previous)
    val nextLabel = stringResource(if (inGroup) R.string.action_next_step else R.string.action_next)
    val previousItemLabel = stringResource(R.string.action_previous_item)
    val nextItemLabel = stringResource(R.string.action_next_item)
    val pauseLabel = stringResource(if (paused) R.string.action_resume else R.string.action_pause)
    val openMenu = {
        controlsShown = false
        menuShown = true
    }
    // Closed by the user (not by starting over or resting): that was use of the cover, like a touch.
    val closeMenu = {
        menuShown = false
        currentOnUserActivity()
        playback.noteInteraction()
    }

    // Photos and videos frame like a phone's gallery: pinched closer, moved around once closer, switched Fit / Fill.
    val framing = remember(repository) { CoverFraming(repository) }
    val glideScope = rememberCoroutineScope()
    val framingOf = remember(framing) { { media: CoverItem.Media -> framing.framingOf(media) } }
    // Not while resting, mirroring or showing an editor's draft, nor under the menu.
    val media = mediaOnScreen(item, groupStep).takeIf { mirroring == null && !ambient && !menuShown && !editing }
    // The picture's shape, read from its file; fingers frame it only once it is known.
    val measured by produceState<Pair<String, Float>?>(null, media?.fileName) {
        val shown = media ?: return@produceState
        value = shown.fileName to withContext(Dispatchers.IO) { MediaStorage.aspect(context, shown) }
    }
    val aspect = measured?.takeIf { it.first == media?.fileName }?.second ?: 0f
    val framable = media?.takeIf { aspect > 0f }
    val currentFramable by rememberUpdatedState(framable)
    val currentAspect by rememberUpdatedState(aspect)
    val currentRotation by rememberUpdatedState(settings.rotation)
    var coverSize by remember { mutableStateOf(IntSize.Zero) }
    // Only what the controls, TalkBack and the hint need, so moving fingers recompose the picture alone.
    val look by remember(media, framing) {
        derivedStateOf(structuralEqualityPolicy()) { media?.let { FramingLook.of(framing.framingOf(it)) } }
    }
    // Moves the picture as fingers, a glide or TalkBack say; false once it cannot move any further that way.
    val moveMedia = { zoomChange: Float, pivot: Offset, pan: Offset ->
        val shown = currentFramable
        if (shown == null) {
            false
        } else {
            val before = framing.framingOf(shown)
            val after = CoverFramingMath.moved(
                before,
                currentAspect,
                coverSize.width.toFloat(),
                coverSize.height.toFloat(),
                currentRotation,
                zoomChange,
                pivot,
                pan,
            )
            if (after != before) framing.show(shown, after)
            after != before
        }
    }
    val zoomMediaBy = { change: Float -> moveMedia(change, Offset(coverSize.width / 2f, coverSize.height / 2f), Offset.Zero) }
    val switchFit = {
        media?.let(framing::switchFit)
        playback.noteInteraction()
    }
    val zoomInLabel = stringResource(R.string.media_zoom_in)
    val zoomOutLabel = stringResource(R.string.media_zoom_out)
    val fillLabel = stringResource(R.string.media_fit_fill)
    val fitLabel = stringResource(R.string.media_fit_fit)

    // Auto-advance and a group's step wait while fingers frame the picture.
    LaunchedEffect(framing.moving) { playback.setFraming(framing.moving) }
    // Saved once the fingers have let go for a moment, so a few quick gestures make one write; whatever is left when
    // the cover closes is saved then. A zoom that is no zoom (a slip of two fingers) snaps back to 1x first.
    LaunchedEffect(framing) {
        snapshotFlow { framing.edit != null && !framing.moving }.collectLatest { due ->
            if (due) {
                val shown = currentFramable?.takeIf { it.id == framing.edit?.id }
                if (shown != null) {
                    val before = framing.framingOf(shown)
                    val after = MediaFocus.snapped(before, currentAspect, coverSize.width.toFloat(), coverSize.height.toFloat())
                    if (after != before) framing.show(shown, after)
                }
                delay(FRAMING_SAVE_MS)
                framing.save()
            }
        }
    }
    DisposableEffect(framing) {
        onDispose {
            framing.save()
            playback.setFraming(false)
        }
    }

    // A pinch lets one finger move the picture only for this showing of this item.
    LaunchedEffect(media?.id, session) { framing.forgetPinch() }

    // The first zoom of each session says how a zoomed-in picture moves.
    var zoomHint by remember { mutableStateOf(false) }
    var zoomHintSession by remember { mutableIntStateOf(-1) }
    val movingZoomedIn = look?.zoomedIn == true && framing.moving
    LaunchedEffect(movingZoomedIn) {
        if (!movingZoomedIn || zoomHintSession == session) return@LaunchedEffect
        zoomHintSession = session
        zoomHint = true
    }
    LaunchedEffect(zoomHint) {
        if (!zoomHint) return@LaunchedEffect
        delay(ZOOM_HINT_MS)
        zoomHint = false
    }

    // Burn-in guard: nudge everything that stays put (content, LED grid, clock) a little every minute
    // (redraw only). Resting can hold one still all night, so then it wanders much further. The awake step
    // follows the clock, so a wake from rest carries on the orbit instead of starting again at (0,0).
    var shift by remember { mutableStateOf(0 to 0) }
    LaunchedEffect(ambient) {
        while (true) {
            val now = SystemClock.uptimeMillis()
            shift = if (ambient) restingShift() else PIXEL_SHIFT_DP[BurnInShift.awakeStep(now, PIXEL_SHIFT_DP.size)]
            delay(BurnInShift.msToNextStep(now))
        }
    }

    // Starting over is a fresh look: no controls, menu or unlocked cover left over from before.
    LaunchedEffect(session) {
        controlsShown = false
        menuShown = false
        if (unlocked) {
            unlocked = false
            lock.lock()
        }
    }
    // A resting screen saver is a dimmed still: a bright menu must not stay on it all night.
    LaunchedEffect(ambient) {
        if (ambient) menuShown = false
    }
    // Nothing plays or advances under the menu.
    DisposableEffect(menuShown) {
        playback.setMenuOpen(menuShown)
        onDispose { playback.setMenuOpen(false) }
    }
    if (menuAllowed) BackHandler(enabled = menuShown, onBack = closeMenu)

    // A toy takes taps itself, so its first showing in each session says where the menu is.
    var toyHint by remember { mutableStateOf(false) }
    var toyHintSession by remember { mutableIntStateOf(-1) }
    val toyShown = menuAllowed && item is CoverItem.Toy && !ambient
    LaunchedEffect(toyShown, session) {
        if (!toyShown || toyHintSession == session) return@LaunchedEffect
        toyHintSession = session
        toyHint = true
        try {
            delay(TOY_HINT_MS)
        } finally {
            toyHint = false
        }
    }

    // The first unlock of each mirroring session says how to steer the mirror; the first move puts it away.
    var mirrorHint by remember { mutableStateOf(false) }
    LaunchedEffect(unlocked, mirroring) {
        if (!unlocked || mirroring?.takeGestureHint() != true) return@LaunchedEffect
        mirrorHint = true
        try {
            delay(MIRROR_HINT_MS)
        } finally {
            mirrorHint = false
        }
    }

    // Open phone: re-lock after 3 s without touches. The screen itself stays on (FLAG_KEEP_SCREEN_ON). Not with
    // TalkBack, whose exploring never counts as a touch: the controls would vanish under the user's finger.
    LaunchedEffect(unlocked, talkBack) {
        if (talkBack) return@LaunchedEffect
        while (unlocked) {
            delay(lock.millisUntilLock(SystemClock.uptimeMillis()).coerceAtLeast(16))
            if (lock.lockIfIdle(SystemClock.uptimeMillis())) unlocked = false
        }
    }
    // Folded: the controls fade away by themselves once the user stops touching. Not with TalkBack, as above.
    LaunchedEffect(controlsShown, lastTouch, talkBack) {
        if (!controlsShown || talkBack) return@LaunchedEffect
        delay(CONTROLS_TIMEOUT_MS)
        controlsShown = false
    }
    // Done with the controls: a tap on empty space (or TalkBack's "hide") puts them away, and the open phone locks again.
    val hideControls = {
        if (lockTouches) {
            unlocked = false
            lock.lock()
        } else {
            controlsShown = false
        }
    }

    // A single tap on the locked open-phone cover: if no second tap follows, say how to use it (at most every 30 s).
    var lockedTapAt by remember { mutableLongStateOf(0L) }
    var lockedHintShownAt by remember { mutableStateOf<Long?>(null) }
    var lockedHint by remember { mutableStateOf(false) }
    LaunchedEffect(lockedTapAt) {
        if (lockedTapAt == 0L) return@LaunchedEffect
        delay(CoverHints.LOCKED_TAP_WAIT_MS)
        val now = SystemClock.uptimeMillis()
        if (lock.isActive || !CoverHints.lockedHintDue(now, lockedHintShownAt)) return@LaunchedEffect
        lockedHintShownAt = now
        lockedHint = true
        try {
            delay(CoverHints.LOCKED_HINT_MS)
        } finally {
            lockedHint = false
        }
    }

    // After a move the user made (here, the notification, Home or the Mini kapak) while the controls are hidden, a
    // moment of "Adım 3/5" or the item's name says where it went. Auto-advance never sends one.
    // The last hint stays set while it fades out, so its text does not vanish before the pill does.
    var moveHint by remember { mutableStateOf<MoveHint?>(null) }
    var moveHintShown by remember { mutableStateOf(false) }
    LaunchedEffect(playback) {
        playback.manualMoves.collectLatest { move ->
            // A step is reported by the group once it shows; wait for that (briefly), not for the old step.
            if (move == CoverMove.STEP) {
                val before = playback.groupProgress.value
                withTimeoutOrNull(MOVE_SETTLE_MS) { playback.groupProgress.first { it != before } }
            }
            val hint = CoverHints.afterMove(move, repository.currentSelected, repository.currentSequence, playback.groupProgress.value)
            if (hint == null) {
                moveHintShown = false
                return@collectLatest
            }
            moveHint = hint
            moveHintShown = true
            try {
                delay(MOVE_HINT_MS)
            } finally {
                moveHintShown = false
            }
        }
    }
    val moveHintText = when (val hint = moveHint) {
        is MoveHint.Step -> stringResource(R.string.cover_group_step, hint.step, hint.count)
        is MoveHint.Item -> listOfNotNull(hint.name, hint.position?.let { "$it/${hint.count}" }).joinToString(" · ")
        null -> null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { coverSize = it }
            .semantics {
                // TalkBack cannot swipe the cover, and on the open phone its double tap never reaches the touch lock:
                // offer the same things as actions. They reach no touch handler, so each one tells the host it was used.
                if ((!lockTouches || talkBack) && !menuShown) {
                    onClick(label = if (controlsVisible) hideControlsLabel else showControlsLabel) {
                        currentOnUserActivity()
                        when {
                            controlsVisible -> hideControls()
                            // Unlocked for as long as TalkBack is on: it never counts as a touch, so no idle re-lock.
                            lockTouches -> {
                                lock.unlock(SystemClock.uptimeMillis())
                                unlocked = true
                            }
                            // Like a touch, the first one only wakes a resting screen saver.
                            !ambient -> {
                                controlsShown = true
                                lastTouch = SystemClock.uptimeMillis()
                            }
                        }
                        true
                    }
                    customActions = buildList {
                        val used = { action: () -> Unit ->
                            currentOnUserActivity()
                            lastTouch = SystemClock.uptimeMillis()
                            action()
                            true
                        }
                        // Moves, exactly as the buttons: steps in a group, items otherwise; the item pills' way out. Not
                        // while mirroring: the playlist behind the mirror is out of sight.
                        if (mirroring == null) {
                            if (canSkip) {
                                add(CustomAccessibilityAction(nextLabel) { used(playback::next) })
                                add(CustomAccessibilityAction(previousLabel) { used(playback::previous) })
                            }
                            if (itemSkip) {
                                add(CustomAccessibilityAction(nextItemLabel) { used(playback::nextItem) })
                                add(CustomAccessibilityAction(previousItemLabel) { used(playback::previousItem) })
                            }
                            add(CustomAccessibilityAction(pauseLabel) { used(playback::togglePause) })
                        }
                        val close = onClose
                        if (close != null) add(CustomAccessibilityAction(closeLabel) { close(); true })
                        if (lockTouches) return@buildList
                        add(
                            CustomAccessibilityAction(openMenuLabel) {
                                // Woken first, so the menu never opens over a resting (dimmed, paused) screen.
                                currentOnUserActivity()
                                openMenu()
                                true
                            },
                        )
                        add(CustomAccessibilityAction(lockScreenLabel) { currentOnDismiss?.invoke(); true })
                        // A photo or video: what fingers do with it, as actions (zooming around the cover's centre).
                        val shownLook = look
                        if (shownLook != null) {
                            val framed = { action: () -> Unit ->
                                currentOnUserActivity()
                                playback.noteInteraction()
                                action()
                                true
                            }
                            if (framable != null && !shownLook.closest) {
                                add(CustomAccessibilityAction(zoomInLabel) { framed { zoomMediaBy(MediaFocus.ZOOM_STEP) } })
                            }
                            if (framable != null && shownLook.zoomedIn) {
                                add(CustomAccessibilityAction(zoomOutLabel) { framed { zoomMediaBy(1f / MediaFocus.ZOOM_STEP) } })
                            }
                            // Says what it does: a fitted picture fills the cover, a filled one fits.
                            val fitAction = if (shownLook.fit == MediaFit.FIT) fillLabel else fitLabel
                            add(CustomAccessibilityAction(fitAction) { framed { media?.let(framing::switchFit) } })
                        }
                    }
                }
            }
            .pointerInput(lock) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // The menu answers its own touches; nothing here may react under it.
                    if (menuShown) return@awaitEachGesture
                    lock.onDown(down.uptimeMillis)
                    lastTouch = down.uptimeMillis
                    // Someone is using the cover: auto-advance waits (a toy mid-roll must not be skipped).
                    if (!lockTouches || unlocked) playback.noteInteraction()
                    // A finger stops a gliding picture where it is.
                    framing.touched()
                    // An unlocked mirror follows the fingers live, like a map; locked, only the double tap below counts.
                    val steered = currentMirroring
                    if (steered != null && lock.isActive) {
                        lastTouch = steerMirror(steered, down, lock) { mirrorHint = false }
                        return@awaitEachGesture
                    }
                    // A photo or video the fingers can frame, now that the cover listens to touches.
                    val framed = currentFramable?.takeIf { lock.isActive }
                    if (framed != null && framing.pansWithOneFinger(framed)) {
                        // Zoomed in by a pinch in this showing: the fingers move the picture around, and it stays the
                        // item on screen. A zoom saved earlier only frames it; swipes stay swipes until a new pinch.
                        val gesture = frameMedia(down, lock, pinching = false, framing, glideScope, moveMedia)
                        // A tap on the picture: the open phone is done with the controls (pause is the button's job).
                        if (gesture.tapped) {
                            if (lockTouches) hideControls() else controlsShown = !controlsShown
                        }
                        lastTouch = gesture.upTime
                        return@awaitEachGesture
                    }
                    var last = down
                    var lifted = false
                    var othersDown = false
                    var pinched = false
                    // More than one finger at any point (a palm, a pinch): never a tap that asks for the lock's hint.
                    var multiTouch = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            // A cancelled pointer, or one a control button took, arrives consumed: no gesture.
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || change.isConsumed) break
                            last = change
                            if (event.changes.count { it.pressed } > 1) multiTouch = true
                            // A second finger on a photo or video: a pinch, which zooms it from here on.
                            if (framed != null && event.changes.count { it.pressed } > 1) {
                                pinched = true
                                break
                            }
                            // Only a real swipe is taken from the buttons; finger jitter during a tap must
                            // not cancel a control's click.
                            if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                event.changes.forEach { it.consume() }
                            }
                            if (!change.pressed) {
                                lifted = true
                                othersDown = event.changes.any { it.pressed }
                                break
                            }
                            lock.onMove(change.uptimeMillis)
                        }
                        if (pinched && framed != null) {
                            framing.pinched(framed)
                            lastTouch = frameMedia(down, lock, pinching = true, framing, glideScope, moveMedia).upTime
                            return@awaitEachGesture
                        }
                        if (!lifted) {
                            lock.onCancel(SystemClock.uptimeMillis())
                            return@awaitEachGesture
                        }
                        val result = lock.onGesture(
                            downMs = down.uptimeMillis,
                            downX = down.position.x,
                            downY = down.position.y,
                            upMs = last.uptimeMillis,
                            upX = last.position.x,
                            upY = last.position.y,
                            multiTouch = multiTouch || othersDown,
                        )
                        val shownMirror = currentMirroring
                        when (result) {
                            GestureResult.ACTIVATED -> {
                                lockedTapAt = 0L
                                unlocked = true
                            }
                            // Empty space means "done with the controls" on both covers; pause is the middle button.
                            GestureResult.TAP -> when {
                                shownMirror != null -> shownMirror.toggleView()
                                lockTouches -> hideControls()
                                else -> controlsShown = !controlsShown
                            }
                            GestureResult.LOCKED_TAP -> lockedTapAt = last.uptimeMillis
                            // CoverPlayback ignores these while an editor streams a draft. A mirror steers itself
                            // (steerMirror above), never the playlist hidden behind it.
                            GestureResult.SWIPE_NEXT -> if (shownMirror == null) playback.next()
                            GestureResult.SWIPE_PREVIOUS -> if (shownMirror == null) playback.previous()
                            GestureResult.SWIPE_DOWN -> if (shownMirror == null && !lockTouches) currentOnDismiss?.invoke()
                            GestureResult.SWIPE_UP -> if (shownMirror == null && menuAllowed) openMenu()
                            GestureResult.IGNORED -> Unit
                        }
                        // Keep counting as "touched" until every finger is up, so the idle lock never fires mid-touch.
                        var upTime = last.uptimeMillis
                        while (othersDown) {
                            val event = awaitPointerEvent()
                            event.changes.forEach { it.consume() }
                            upTime = event.changes.firstOrNull()?.uptimeMillis ?: upTime
                            lock.onMove(upTime)
                            othersDown = event.changes.any { it.pressed }
                        }
                        lock.onAllUp(upTime)
                        lastTouch = upTime
                    } catch (e: CancellationException) {
                        lock.onCancel(SystemClock.uptimeMillis())
                        throw e
                    }
                }
            },
    ) {
        // A QR code has to stay scannable: never turned into LED dots or dimmed. A group is judged by the step
        // on screen only, never as a whole.
        val judged = if (item is CoverItem.Group) groupStep else item
        val showsQr = mirroring == null && judged?.showsQr() == true
        val led = LedStyle.of(settings).takeUnless { showsQr }
        // Not over what fills the corner: a QR code's finder pattern and quiet zone (no corner is free, so
        // hidden rather than moved), or a toy's result band while it is played.
        val clockShown = showClock && !showsQr && !(judged is CoverItem.Toy && !ambient)
        // A mirror takes the content's place: the inner screen, composed by the display hardware (no decoding).
        if (mirroring != null) MirrorSurface(mirroring, Modifier.fillMaxSize()) else Box(
            modifier = Modifier
                .fillMaxSize()
                // The burn-in nudge moves the LED grid and the clock too, so no dot stays lit in the same place.
                .graphicsLayer {
                    val (dx, dy) = shift
                    translationX = dx * density
                    translationY = dy * density
                }
                // Under the menu there is nothing for TalkBack to find.
                .then(if (menuShown) Modifier.clearAndSetSemantics {} else Modifier),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .ledMatrix(led)
                    .graphicsLayer { rotationZ = settings.rotation.toFloat() },
            ) {
                CompositionLocalProvider(
                    LocalMediaFraming provides framingOf,
                    LocalHoldGroupStep provides framing.moving,
                    LocalAnimationFps provides settings.powerMode.fps,
                    // A hardware overlay cannot be rotated or turned into LED dots: use a texture then.
                    LocalVideoOverlay provides (settings.rotation == 0 && led == null),
                    // The cover is where sound plays: whenever its sound is on, never while the screen saver rests.
                    LocalAllowSound provides (soundOn && !ambient),
                    LocalOnSoundLost provides onSoundLost,
                ) {
                    // A new session builds the content afresh: a video at 0:00, a GIF at its first frame, a group
                    // at step one. Its events are new too, so nothing from before can end the new showing.
                    key(session) {
                        CompositionLocalProvider(LocalLifecycleOwner provides rememberSessionLifecycle(playback, session)) {
                            CoverContent(
                                item = item,
                                modifier = Modifier.fillMaxSize(),
                                playback = if (paused || ambient || menuShown) Playback.PAUSED else Playback.LIVE,
                                events = events,
                                interactive = !ambient && !menuShown && (!lockTouches || unlocked),
                            )
                        }
                    }
                }
            }
            // Under the controls' scrim it would only crowd their bottom row; they say what matters then.
            if (clockShown && !controlsVisible) {
                CoverClock(
                    Modifier
                        .align(Alignment.BottomStart)
                        .windowInsetsPadding(WindowInsets.displayCutout)
                        .padding(start = 20.dp, bottom = 16.dp),
                )
            }
        }

        // Brightness as content dimming: a black veil over the content and the clock (video included, since
        // the window is composited above its surface). Gamma 2.2 so the slider feels linear; darker OLED
        // pixels draw less power. Resting adds a further veil, like an always-on display.
        // QR codes are shown at full brightness so cameras read them even in daylight.
        val userBrightness = if (showsQr && !ambient) 1f else settings.brightness.coerceIn(CoverSettings.MIN_BRIGHTNESS, 1f)
        val brightness = userBrightness * if (ambient) AMBIENT_FACTOR else 1f
        val dim = 1f - brightness.pow(1f / 2.2f)
        if (dim > 0.005f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = dim)),
            )
        }

        // A frozen frame needs its own sign that it is paused, on both covers. Only while the controls are hidden, so it
        // never meets the controls' × in the same corner.
        AnimatedVisibility(
            visible = paused && !controlsVisible && !ambient && !menuShown && !editing && mirroring == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.displayCutout)
                .padding(16.dp),
        ) {
            PausedBadge()
        }

        AnimatedVisibility(
            visible = toyHint && !controlsVisible && !menuShown,
            enter = fadeIn(),
            exit = fadeOut(),
            // At the top: the toy's own result band and its "tap" pill take the bottom.
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.displayCutout)
                .padding(top = 12.dp),
        ) {
            MenuHint()
        }

        AnimatedVisibility(
            visible = mirrorHint && mirroring != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.displayCutout)
                .padding(bottom = 18.dp),
        ) {
            MirrorGestureHint()
        }

        AnimatedVisibility(
            visible = zoomHint && look != null,
            enter = fadeIn(),
            exit = fadeOut(),
            // Under the title while the controls show (their item pills and bottom row take the lower half); otherwise
            // at the bottom, above the clock in the bottom-left corner.
            modifier = Modifier
                .align(if (controlsVisible) Alignment.TopCenter else Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.displayCutout)
                .padding(
                    top = if (controlsVisible) 64.dp else 0.dp,
                    bottom = if (controlsVisible) 0.dp else if (clockShown) 96.dp else 18.dp,
                ),
        ) {
            ZoomHint()
        }

        // Where a move went, while the controls are hidden; never over a resting screen saver, the menu or a mirror.
        AnimatedVisibility(
            visible = moveHintShown && !controlsVisible && !ambient && !menuShown && mirroring == null && !editing,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.displayCutout)
                .padding(start = 24.dp, end = 24.dp, top = 14.dp),
        ) {
            HintPill(moveHintText.orEmpty(), announce = true)
        }

        // A single tap on the locked open-phone cover did nothing visible: say how to use it.
        AnimatedVisibility(
            visible = lockedHint && !controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.displayCutout)
                .padding(bottom = 18.dp),
        ) {
            HintPill(stringResource(R.string.cover_locked_hint), icon = Icons.Filled.TouchApp)
        }

        AnimatedVisibility(visible = controlsVisible, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize()) {
                if (lockTouches) {
                    // Unlocked frame: the cover is listening to touches now.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .border(3.dp, Brush.linearGradient(BrandGradient), RoundedCornerShape(20.dp)),
                    )
                }
                // Mirroring shows the frame and its × alone: taps and swipes steer the mirror, there is no playlist to
                // control.
                if (mirroring != null) {
                    if (onClose != null) CoverCloseButton(closeLabel, onClose, Modifier.align(Alignment.TopEnd))
                    return@Box
                }
                val shown = item
                val index = sequence.indexOfFirst { it.id == shown?.id }
                // Only the cover's sound for now, so an editor's draft may switch it too.
                val canSwitchSound = hasSoundToSwitch(shown)
                CoverControls(
                    title = shown?.name.orEmpty(),
                    position = if (!editing && index >= 0 && sequence.size > 1) "${index + 1}/${sequence.size}" else null,
                    paused = paused,
                    // A group steps through its items even when it is alone in the playlist (it goes round).
                    canSkip = canSkip,
                    soundOn = soundOn.takeIf { canSwitchSound },
                    onPrevious = {
                        lastTouch = SystemClock.uptimeMillis()
                        currentOnUserActivity()
                        playback.previous()
                    },
                    onTogglePause = {
                        lastTouch = SystemClock.uptimeMillis()
                        currentOnUserActivity()
                        playback.togglePause()
                    },
                    onNext = {
                        lastTouch = SystemClock.uptimeMillis()
                        currentOnUserActivity()
                        playback.next()
                    },
                    onToggleSound = {
                        lastTouch = SystemClock.uptimeMillis()
                        currentOnUserActivity()
                        playback.toggleSound()
                    },
                    onClose = onClose,
                    closeLabel = closeLabel,
                    mediaFit = look?.fit,
                    onSwitchFit = {
                        lastTouch = SystemClock.uptimeMillis()
                        currentOnUserActivity()
                        switchFit()
                    },
                    onOpenMenu = if (menuAllowed) {
                        {
                            currentOnUserActivity()
                            openMenu()
                        }
                    } else {
                        null
                    },
                    compact = shown is CoverItem.Toy,
                    // Until the group reports its step, it is at step one: every showing starts there.
                    group = (shown as? CoverItem.Group)?.takeIf { inGroup }?.let { group ->
                        groupProgress?.takeIf { it.groupId == group.id } ?: GroupProgress(group.id, 1, group.items.size)
                    },
                    itemSkip = itemSkip,
                    onPreviousItem = {
                        lastTouch = SystemClock.uptimeMillis()
                        currentOnUserActivity()
                        playback.previousItem()
                    },
                    onNextItem = {
                        lastTouch = SystemClock.uptimeMillis()
                        currentOnUserActivity()
                        playback.nextItem()
                    },
                )
            }
        }

        if (menuAllowed) {
            AnimatedVisibility(
                visible = menuShown,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                CoverMenu(
                    repository = repository,
                    playback = playback,
                    item = item,
                    onDismiss = closeMenu,
                    onClose = onClose,
                    onUserActivity = currentOnUserActivity,
                    // False while it slides away; true again if it is reopened during that exit.
                    open = menuShown,
                    brightnessLocked = showsQr,
                )
            }
        }
    }
}

/**
 * The content's lifecycle: the host's, except that content of a session that was replaced never starts
 * again. A waking screen saver starts over as it starts, a frame before the new session's content takes
 * the old one's place; without this the old video would start decoding only to be thrown away.
 */
@Composable
private fun rememberSessionLifecycle(playback: CoverPlayback, session: Int): LifecycleOwner {
    val host = LocalLifecycleOwner.current.lifecycle
    return remember(host, session) { SessionLifecycle(host) { playback.session.value == session } }
}

private class SessionLifecycle(
    private val host: Lifecycle,
    private val isCurrent: () -> Boolean,
) : LifecycleOwner, LifecycleEventObserver, RememberObserver {
    private val registry = LifecycleRegistry(this)

    override val lifecycle: Lifecycle get() = registry

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        // Stopping and destroying always follow the host; starting only while this session is the current one.
        if (event.targetState > registry.currentState && !isCurrent()) return
        registry.handleLifecycleEvent(event)
    }

    // Following the host starts here: added, it catches up with the host's state at once.
    override fun onRemembered() = host.addObserver(this)

    override fun onForgotten() = host.removeObserver(this)

    override fun onAbandoned() = Unit
}

/** A random spot [RESTING_SHIFT_MIN_DP]..[RESTING_SHIFT_MAX_DP] dp off on each axis, either way. */
private fun restingShift(): Pair<Int, Int> {
    fun axis() = Random.nextInt(RESTING_SHIFT_MIN_DP, RESTING_SHIFT_MAX_DP + 1) * if (Random.nextBoolean()) 1 else -1
    return axis() to axis()
}

/** Offsets (dp) the cover content cycles through, one step a minute, so static art never burns in. */
private val PIXEL_SHIFT_DP = listOf(0 to 0, 2 to 1, -1 to 2, -2 to -1, 1 to -2)

/**
 * The awake burn-in step, taken from the clock rather than counted from when the content appeared: an
 * always-on cover rests a minute after each touch, so a count that restarts on every wake would never get
 * past (0,0). Pure, so it is unit-tested.
 */
internal object BurnInShift {
    const val STEP_MS = 60_000L

    /** Which of [steps] offsets is shown at [uptimeMs]. */
    fun awakeStep(uptimeMs: Long, steps: Int): Int = ((uptimeMs / STEP_MS) % steps).toInt()

    /** How long until the next step, so every change lands on the minute boundary the step is taken from. */
    fun msToNextStep(uptimeMs: Long): Long = STEP_MS - uptimeMs % STEP_MS
}

/** While resting, each minute moves everything this far (dp, on each axis) from the centre. */
private const val RESTING_SHIFT_MIN_DP = 12
private const val RESTING_SHIFT_MAX_DP = 16

private const val CONTROLS_TIMEOUT_MS = 4_000L

/** How long a toy's first showing in a session points at the menu. */
private const val TOY_HINT_MS = 2_000L

/** How long the mirror's gesture hint stays, unless a move puts it away first. */
private const val MIRROR_HINT_MS = 4_000L

/** How long the hint after a manual move stays. */
private const val MOVE_HINT_MS = 1_200L

/** How long a step move waits for the group to report its new step before the hint shows what it has. */
private const val MOVE_SETTLE_MS = 300L

/** How long the zoom hint stays. */
private const val ZOOM_HINT_MS = 3_000L

/** How long after the fingers let go a framing is saved, unless they come back first. */
private const val FRAMING_SAVE_MS = 600L

/** What the controls, TalkBack and the hint show of a picture's framing; coarse, so moving fingers rarely change it. */
private data class FramingLook(val fit: MediaFit, val zoomedIn: Boolean, val closest: Boolean) {
    companion object {
        fun of(framing: MediaFraming) = FramingLook(framing.fit, framing.zoom >= MediaFocus.SNAP_ZOOM, framing.zoom >= MediaFocus.MAX_ZOOM)
    }
}

/** Resting brightness relative to the user's setting. */
private const val AMBIENT_FACTOR = 0.35f

/** What the short hint after a manual move says: the group's step, or the item it went to. */
internal sealed interface MoveHint {
    /** Still inside the group: "Adım [step]/[count]". */
    data class Step(val step: Int, val count: Int) : MoveHint

    /** On another item: its [name] (null if it has none) and, in a longer playlist, [position] of [count]. */
    data class Item(val name: String?, val position: Int?, val count: Int) : MoveHint
}

/** What the cover's short hints say and when. Pure, so it is unit-tested. */
internal object CoverHints {
    /** How long after a single tap on the locked cover a second one still makes the unlocking double tap. */
    const val LOCKED_TAP_WAIT_MS = 350L

    /** How long the "double-tap to use" hint stays. */
    const val LOCKED_HINT_MS = 1_500L

    /** The hint shows at most once in this long, so taps on a cover that faces the palm do not keep it up. */
    const val LOCKED_HINT_GAP_MS = 30_000L

    /**
     * After a manual [move]: a step inside the group [selected] says where in the group it is ([progress]); a move to
     * another item says which ([selected]'s name and its place in [sequence]). Null when there is nothing to say.
     */
    fun afterMove(move: CoverMove, selected: CoverItem?, sequence: List<CoverItem>, progress: GroupProgress?): MoveHint? {
        if (move == CoverMove.STEP && progress != null && progress.groupId == selected?.id) {
            return MoveHint.Step(progress.step, progress.count)
        }
        if (selected == null) return null
        val index = sequence.indexOfFirst { it.id == selected.id }
        val position = (index + 1).takeIf { index >= 0 && sequence.size > 1 }
        val name = selected.name.ifBlank { null }
        if (name == null && position == null) return null
        return MoveHint.Item(name, position, sequence.size)
    }

    /** Whether the locked cover's hint may show at [nowMs], having last shown at [lastShownMs] (null: not yet). */
    fun lockedHintDue(nowMs: Long, lastShownMs: Long?): Boolean = lastShownMs == null || nowMs - lastShownMs >= LOCKED_HINT_GAP_MS
}
