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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
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
import com.zlip.app.data.showsQr
import com.zlip.app.render.CoverContent
import com.zlip.app.render.LocalAllowSound
import com.zlip.app.render.LocalAnimationFps
import com.zlip.app.render.LocalOnSoundLost
import com.zlip.app.render.LedStyle
import com.zlip.app.render.LocalVideoOverlay
import com.zlip.app.render.Playback
import com.zlip.app.render.ledMatrix
import com.zlip.app.ui.theme.BrandGradient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.math.pow
import kotlin.random.Random

/**
 * What the cover shows, with its controls.
 *
 * Phone open ([lockTouches]): the cover faces the palm, so touches are ignored until a double tap and
 * lock again after 3 s; while unlocked the controls show, a tap pauses and a swipe changes the item.
 * Folded screen saver: touches work directly. A tap shows the controls, a sideways swipe changes the
 * item, a swipe up opens the menu ([CoverMenu]) and a swipe down hands the cover back to its lock screen
 * ([onDismiss]); [onClose] ends it. The open phone has no menu: its cover faces other people.
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
    val showControlsLabel = stringResource(R.string.cover_show_controls)
    val openMenuLabel = stringResource(R.string.cover_menu_open)
    val lockScreenLabel = stringResource(R.string.cover_to_lock_screen)
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

    // Open phone: re-lock after 3 s without touches. The screen itself stays on (FLAG_KEEP_SCREEN_ON).
    LaunchedEffect(unlocked) {
        while (unlocked) {
            delay(lock.millisUntilLock(SystemClock.uptimeMillis()).coerceAtLeast(16))
            if (lock.lockIfIdle(SystemClock.uptimeMillis())) unlocked = false
        }
    }
    // Folded: the controls fade away by themselves once the user stops touching. Not with TalkBack, whose
    // exploring never counts as a touch here: the controls would vanish under the user's finger.
    LaunchedEffect(controlsShown, lastTouch) {
        if (!controlsShown || accessibility.isTouchExplorationEnabled) return@LaunchedEffect
        delay(CONTROLS_TIMEOUT_MS)
        controlsShown = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .semantics {
                // TalkBack cannot swipe the cover: offer the same things as actions. They reach no touch
                // handler, so each one tells the host it was used.
                if (!lockTouches && !menuShown) {
                    onClick(label = showControlsLabel) {
                        currentOnUserActivity()
                        // Like a touch, the first one only wakes a resting screen saver.
                        if (!ambient) {
                            controlsShown = true
                            lastTouch = SystemClock.uptimeMillis()
                        }
                        true
                    }
                    customActions = listOf(
                        CustomAccessibilityAction(openMenuLabel) {
                            // Woken first, so the menu never opens over a resting (dimmed, paused) screen.
                            currentOnUserActivity()
                            openMenu()
                            true
                        },
                        CustomAccessibilityAction(lockScreenLabel) { currentOnDismiss?.invoke(); true },
                    )
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
                    // An unlocked mirror follows the fingers live, like a map; locked, only the double tap below counts.
                    val steered = currentMirroring
                    if (steered != null && lock.isActive) {
                        lastTouch = steerMirror(steered, down, lock) { mirrorHint = false }
                        return@awaitEachGesture
                    }
                    var last = down
                    var lifted = false
                    var othersDown = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            // A cancelled pointer, or one a control button took, arrives consumed: no gesture.
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || change.isConsumed) break
                            last = change
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
                        )
                        val shownMirror = currentMirroring
                        when (result) {
                            GestureResult.ACTIVATED -> unlocked = true
                            GestureResult.TAP -> when {
                                shownMirror != null -> shownMirror.toggleView()
                                lockTouches -> playback.togglePause()
                                else -> controlsShown = !controlsShown
                            }
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
            // Not over what fills the corner: a QR code's finder pattern and quiet zone (no corner is free, so
            // hidden rather than moved), or a toy's result band while it is played.
            val clockFits = !showsQr && !(judged is CoverItem.Toy && !ambient)
            if (showClock && clockFits) {
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

        // The live view has no unlock hint, so a frozen frame needs its own sign that it is paused.
        AnimatedVisibility(
            visible = paused && !lockTouches && !controlsVisible && !ambient && !menuShown,
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
                // Mirroring shows the frame alone: taps and swipes steer the mirror, there is no playlist to control.
                if (mirroring != null) return@Box
                val shown = item
                val index = sequence.indexOfFirst { it.id == shown?.id }
                // Only the cover's sound for now, so an editor's draft may switch it too.
                val canSwitchSound = hasSoundToSwitch(shown)
                CoverControls(
                    title = shown?.name.orEmpty(),
                    position = if (!editing && index >= 0 && sequence.size > 1) "${index + 1}/${sequence.size}" else null,
                    paused = paused,
                    // A group steps through its items even when it is alone in the playlist.
                    canSkip = !editing && (sequence.size > 1 || shown is CoverItem.Group),
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
                    onOpenMenu = if (menuAllowed) {
                        {
                            currentOnUserActivity()
                            openMenu()
                        }
                    } else {
                        null
                    },
                    compact = shown is CoverItem.Toy,
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

/** Resting brightness relative to the user's setting. */
private const val AMBIENT_FACTOR = 0.35f
