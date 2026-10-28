package com.zlip.app

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Icon
import android.hardware.display.DisplayManager
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.quicksettings.TileService
import android.util.Rational
import android.view.Display
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.zlip.app.cover.CoverStatus
import com.zlip.app.cover.coverHoverSupported
import com.zlip.app.cover.mirror.MirrorState
import com.zlip.app.data.CoverItem
import com.zlip.app.data.MediaFit
import com.zlip.app.system.CoverActionReceiver
import com.zlip.app.system.CoverGuard
import com.zlip.app.system.CoverNotifier
import com.zlip.app.system.MirrorService
import com.zlip.app.ui.IncomingContent
import com.zlip.app.ui.ZlipRoot
import com.zlip.app.ui.theme.ZlipTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class MainActivity : ComponentActivity() {

    private val app: ZlipApp get() = application as ZlipApp

    /**
     * Android only lets the top (focused) activity open the cover screen. RESUMED is not enough: in
     * split screen or under a pop-up window an unfocused activity is resumed too.
     */
    private val topResumed = MutableStateFlow(false)

    /** Set by the quick-settings tile or the small window: start the cover even if auto-start is off. */
    private val startRequested = MutableStateFlow(false)

    /** Set when the tile's long-press opens us: show settings. */
    private val settingsRequested = MutableStateFlow(false)

    /** Shared to Zlip, or the "Yeni yazı" shortcut: waiting for [ZlipRoot] to open it. */
    private val incoming = MutableStateFlow<IncomingContent?>(null)

    /** Set by the "Ekranı yansıt" shortcut: ask for mirroring once we are on top. */
    private val mirrorRequested = MutableStateFlow(false)

    private val lastInteraction = MutableStateFlow(SystemClock.uptimeMillis())

    /** Uptime of the last deliberate touch on the cover (0 = none yet); the idle cutoff counts it too. */
    private val lastCoverTouch = MutableStateFlow(0L)
    private val standby = MutableStateFlow(false)

    /** Standby may run on every screen; only an import the user waits for holds it off. */
    private val standbyAllowed = MutableStateFlow(true)

    /** A photo or video import is running; it ends with the activity, so nothing may finish or idle it out. */
    private val importing = MutableStateFlow(false)

    /**
     * One of Zlip's own sheets, dialogs or menus has the input focus. Their touches wake the standby
     * themselves, so losing focus to them does not hold it off like another window in front does.
     */
    private val ownModalOpen = MutableStateFlow(false)

    /** Zlip is shown as its small picture-in-picture window, the cover's "hover" toggle in other apps. */
    private val inPip = MutableStateFlow(false)

    /** Split screen or a pop-up window (or picture-in-picture): other apps share the inner screen. */
    private val multiWindow = MutableStateFlow(false)

    /** Our window has input focus: no shade, dialog or other app in front of it. */
    private val windowFocused = MutableStateFlow(false)

    /** Seconds left before the idle cutoff during its warning period; null otherwise. */
    private val autoOffCountdown = MutableStateFlow<Int?>(null)

    /** The idle cutoff has passed: nothing holds the screen on any more. */
    private val idleCutOff = MutableStateFlow(false)

    /**
     * The idle cutoff turned the cover off. Auto-start waits until someone uses Zlip again (or it leaves the
     * screen), or it would turn the cover straight back on while Zlip stays on top.
     */
    private val idleStopped = MutableStateFlow(false)

    /** An editor holds unsaved changes: leaving must not shrink Zlip, whose dismissal could lose them. */
    private val editorDirty = MutableStateFlow(false)

    /**
     * Zlip is not on screen full size: stopped, or only its small window. Cleared in onResume, not onStart,
     * which runs before onNewIntent for an activity coming back from the background.
     */
    private var hiddenFromUser = true

    /** The small window's current look and buttons; null where it cannot hold the cover. */
    private var hoverParams: PictureInPictureParams? = null

    /** Waits for a cover that the tile or the small window asked for, then shrinks Zlip again. */
    private var returnJob: Job? = null

    /** True while our own photo picker is open, so that trip does not count as leaving the app. */
    private var openingPicker = false

    /** Uptime when the cover last showed Zlip while the phone was open (0 = not in this visit). */
    private var coverActiveAt = 0L

    private val mirrorConsent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == RESULT_OK && data != null) {
            // The mirror shows on the cover: turn it on too, once Zlip is back on top.
            if (app.cover.status.value == CoverStatus.AVAILABLE) {
                app.cover.resetAutoStart()
                startRequested.value = true
            }
            try {
                MirrorService.start(this, result.resultCode, data)
            } catch (e: IllegalStateException) {
                // Not allowed to start a foreground service right now (we are no longer in front).
                app.mirror.onStartFailed()
            }
        } else {
            app.mirror.onConsentDenied()
        }
    }

    private val displays by lazy { getSystemService(DisplayManager::class.java) }
    private val transitions by lazy { CoverGuard.DarkTransitions(displays) }
    private val foldListener = object : DisplayManager.DisplayListener {
        override fun onDisplayChanged(displayId: Int) {
            // Folded while using Zlip: carry on seamlessly on the closed phone's cover screen.
            val innerDark = displayId == Display.DEFAULT_DISPLAY && transitions.justWentDark(displayId)
            if (innerDark && !app.cover.endedByUser.value && CoverActivity.needsArming) {
                // Locking an open phone darkens the inner screen too; only a closed hinge means folded. Never
                // over a call that is ringing or going on.
                CoverGuard.ifFolded(this@MainActivity) {
                    if (CoverGuard.shouldArm(CoverGuard.audioMode(this@MainActivity), needsArming = CoverActivity.needsArming, folded = true)) {
                        CoverGuard.showOnCover(this@MainActivity)
                    }
                }
            }
        }

        override fun onDisplayAdded(displayId: Int) = Unit

        override fun onDisplayRemoved(displayId: Int) = Unit
    }

    private val accessibility by lazy { getSystemService(AccessibilityManager::class.java) }
    private val touchExploration = MutableStateFlow(false)
    private val touchExplorationListener = AccessibilityManager.TouchExplorationStateChangeListener { enabled ->
        touchExploration.value = enabled
    }

    override fun onDestroy() {
        accessibility.removeTouchExplorationStateChangeListener(touchExplorationListener)
        displays.unregisterDisplayListener(foldListener)
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // A share opens us inside the sharing app's task. A second, fully live Zlip there would fight the real one
        // over the cover, so it never builds: nothing usable says so and closes, anything else goes to Zlip's task.
        when (shareLaunch(isShare(intent), IncomingContent.fromShare(intent) != null, isTaskRoot)) {
            ShareLaunch.UNSUPPORTED -> {
                Toast.makeText(applicationContext, R.string.share_unsupported, Toast.LENGTH_SHORT).show()
                finish()
                return
            }
            ShareLaunch.FORWARD -> {
                startActivity(
                    Intent(intent)
                        .setClass(this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                )
                finish()
                return
            }
            ShareLaunch.NOT_A_SHARE, ShareLaunch.OPEN -> Unit
        }
        // A recreated or restored activity gets its original launch intent again; it was handled already.
        if (savedInstanceState == null) handleIntent(intent, coldStart = true)
        accessibility.addTouchExplorationStateChangeListener(touchExplorationListener)
        touchExploration.value = accessibility.isTouchExplorationEnabled
        multiWindow.value = isInMultiWindowMode
        inPip.value = isInPictureInPictureMode
        app.cover.setHovering(isInPictureInPictureMode)
        transitions // capture the current states before the first change arrives
        displays.registerDisplayListener(foldListener, Handler(Looper.getMainLooper()))

        // Stamped while the cover is on and when it ends, so a fold just after a long session still counts.
        lifecycleScope.launch {
            var wasActive = false
            app.cover.status.collect { status ->
                if (status == CoverStatus.ACTIVE || wasActive) coverActiveAt = SystemClock.uptimeMillis()
                wasActive = status == CoverStatus.ACTIVE
                // On again by any route (the tile, the small window, mirroring): the idle stop is over.
                if (status == CoverStatus.ACTIVE) idleStopped.value = false
            }
        }
        lifecycleScope.launch {
            app.playback.coverTouches.drop(1).collect {
                lastCoverTouch.value = SystemClock.uptimeMillis()
                idleStopped.value = false
            }
        }
        // Closing our own session was refused from the small window: hiding the window ends it instead.
        lifecycleScope.launch {
            app.cover.closeRefused.collect { if (isInPictureInPictureMode) moveTaskToBack(true) }
        }
        // Turned off from the cover's ×, the notification or the tile: the small window goes with it, so "off" is
        // complete. Only hidden, never finished: an import still running finishes in the library.
        lifecycleScope.launch {
            app.hideMiniWindow.collect { if (isInPictureInPictureMode) moveTaskToBack(true) }
        }
        // The optional always-show service may only be started while Zlip is on screen.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                app.repository.settings.map { it.autoShowOnCover }.distinctUntilChanged().collect { CoverGuard.sync(this@MainActivity) }
            }
        }

        // Start the cover whenever we are on top and it is free: automatically (if enabled, the user has not
        // turned it off and the idle cutoff did not just end it) or because the tile asked. Re-arms after the
        // system ends a session.
        lifecycleScope.launch {
            combine(
                app.cover.status,
                app.repository.settings,
                app.repository.loaded,
                topResumed,
                combine(app.cover.autoStartAllowed, idleStopped, startRequested, ::Triple),
            ) { status, settings, loaded, top, (allowed, idle, requested) ->
                shouldStartCover(top, loaded, status, settings.autoStart, allowed, idle, requested)
            }.collect { shouldStart ->
                if (shouldStart) {
                    startRequested.value = false
                    app.cover.start(this@MainActivity)
                }
            }
        }

        // The small window's look and buttons follow the cover, kept current so Android can shrink Zlip the
        // moment the user leaves while the cover is on.
        if (coverHoverSupported && packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            lifecycleScope.launch {
                combine(
                    app.cover.status,
                    combine(app.repository.settings.map { it.keepCoverInOtherApps }.distinctUntilChanged(), editorDirty, ::Pair),
                    // The photo or video the cover shows (a group's step too) offers Fit / Fill.
                    combine(app.playback.paused, app.playback.shownItem.map { (it as? CoverItem.Media)?.fit }.distinctUntilChanged(), ::Pair),
                    app.mirror.state,
                    // Decided by the selected item, not its step, so the buttons never jump while a group plays.
                    combine(
                        app.repository.selectedItem.map { it?.name to hoverOffersFit(it) }.distinctUntilChanged(),
                        app.playback.itemSkipAvailable,
                        app.playback.movesAvailable,
                    ) { (name, media), group, moves -> HoverItem(name, media, group, moves) },
                ) { status, (keep, dirty), (paused, mediaFit), mirror, (name, media, group, moves) ->
                    HoverState(
                        coverOn = status == CoverStatus.ACTIVE,
                        keep = keep,
                        editorDirty = dirty,
                        paused = paused,
                        mirroring = mirror == MirrorState.ON,
                        itemName = name,
                        mediaFit = mediaFit,
                        media = media,
                        group = group,
                        canMove = moves,
                    )
                }.distinctUntilChanged().collect { state ->
                    hoverParams = buildHoverParams(state).also(::setPictureInPictureParams)
                }
            }
        }

        // Inner-screen standby: after N idle seconds with the cover on, go black at minimum brightness.
        // The screen must stay on (Android ends the cover session when the phone locks), so this is
        // the cheapest state that keeps the cover alive. Only while the inner screen shows nothing but Zlip,
        // full screen and focused (or focus is with one of its own sheets): in split screen or a pop-up window the
        // brightness override would dim the other app too, in the small window the user is busy elsewhere, and a
        // mirror must not go black.
        val focusedOnZlip = combine(windowFocused, ownModalOpen) { focused, ownModal -> focused || ownModal }
        val innerIsZlip = combine(focusedOnZlip, multiWindow, inPip, app.mirror.state) { focused, multi, pip, mirror ->
            focused && !multi && !pip && mirror == MirrorState.OFF
        }
        lifecycleScope.launch {
            combine(
                app.cover.status,
                app.repository.settings,
                lastInteraction,
                combine(standbyAllowed, innerIsZlip, idleStopped) { allowed, zlipOnly, idle -> (allowed && zlipOnly) to idle },
                touchExploration,
            ) { status, settings, last, (allowed, idle), talkBack ->
                // TalkBack input (hover, accessibility clicks) never reaches onUserInteraction, so it could
                // not keep the screen awake: no standby while touch exploration is on. After the idle cutoff the
                // inner screen stays dark until the system timeout locks the phone.
                Triple((status == CoverStatus.ACTIVE || idle) && allowed && !talkBack, settings.standbySeconds, last)
            }.collectLatest { (active, seconds, last) ->
                if (!active || seconds <= 0) {
                    standby.value = false
                    return@collectLatest
                }
                val wait = last + seconds * 1_000L - SystemClock.uptimeMillis()
                if (wait > 0) {
                    standby.value = false
                    delay(wait)
                }
                standby.value = true
            }
        }

        // Idle cutoff: with the cover on and Zlip full size, after the chosen time without a touch on either
        // screen, the cover turns off and nothing holds the inner screen on any more. A chip counts down the last
        // seconds; any touch starts the wait again. The small window never holds the screen, so there the system
        // timeout (fed by touches in other apps) applies from the start.
        lifecycleScope.launch {
            combine(
                app.cover.status,
                app.repository.settings.map { it.autoOffMinutes }.distinctUntilChanged(),
                // A long video's conversion takes minutes without a touch: the cover stays on until it is done.
                combine(inPip, importing) { pip, busy -> pip || busy },
                lastInteraction,
                lastCoverTouch,
            ) { status, minutes, held, inner, cover ->
                if (status == CoverStatus.ACTIVE && !held && minutes > 0) maxOf(inner, cover) + minutes * 60_000L else null
            }.collectLatest { deadline ->
                idleCutOff.value = false
                autoOffCountdown.value = null
                if (deadline == null) return@collectLatest
                delay(deadline - AUTO_OFF_WARNING_MS - SystemClock.uptimeMillis())
                while (true) {
                    val left = deadline - SystemClock.uptimeMillis()
                    if (left <= 0) break
                    autoOffCountdown.value = ((left + 999) / 1_000).toInt()
                    // Wake up exactly when the shown second changes.
                    delay((left - 1) % 1_000 + 1)
                }
                autoOffCountdown.value = null
                idleCutOff.value = true
                // Really off when the chip says so: only letting go of the screen would leave the cover on for a
                // whole system timeout more (releasing the hold counts as activity), then lock the phone. Not when
                // another app shares the inner screen or TalkBack is on: their input never reaches us but does feed
                // the system timeout, which then decides.
                if (!multiWindow.value && !touchExploration.value) {
                    idleStopped.value = true
                    app.mirror.stop()
                    app.cover.stop(byUser = false)
                }
            }
        }

        lifecycleScope.launch {
            combine(app.cover.status, standby, inPip, idleCutOff) { status, isStandby, pip, cutOff ->
                val hold = !pip && !cutOff
                Triple(status == CoverStatus.ACTIVE && hold, isStandby, hold)
            }.collect { (keepOn, isStandby, hold) ->
                app.cover.setScreenHold(hold)
                applyWindowState(keepOn, isStandby)
            }
        }

        // "Ekranı yansıt" shortcut: Android's consent dialog only opens over a Zlip that is in front with a cover to use.
        lifecycleScope.launch {
            combine(mirrorRequested, topResumed, app.cover.status) { requested, top, status ->
                requested && top && (status == CoverStatus.AVAILABLE || status == CoverStatus.ACTIVE)
            }.collect { go ->
                if (go) {
                    mirrorRequested.value = false
                    if (coverHoverSupported) {
                        startMirror()
                    } else {
                        // The shortcut is hidden below Android 15, but one pinned earlier can still arrive.
                        Toast.makeText(applicationContext, R.string.mirror_needs_android15, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        setContent {
            ZlipTheme {
                ZlipRoot(
                    app = app,
                    standby = standby.asStateFlow(),
                    settingsRequested = settingsRequested,
                    incoming = incoming,
                    onStartCover = {
                        app.cover.resetAutoStart()
                        app.cover.start(this)
                    },
                    onStopCover = { app.stopCover() },
                    onOpeningPicker = { openingPicker = true },
                    // Typing and touches in our own sheets never reach onUserInteraction.
                    onWake = ::noteUserActivity,
                    onStandbyAllowedChange = { allowed ->
                        standbyAllowed.value = allowed
                        lastInteraction.value = SystemClock.uptimeMillis()
                    },
                    onImportingChange = { running ->
                        importing.value = running
                        // The idle wait starts again from the end of an import.
                        lastInteraction.value = SystemClock.uptimeMillis()
                    },
                    onModalOpenChange = { ownModalOpen.value = it },
                    inPip = inPip.asStateFlow(),
                    autoOffCountdown = autoOffCountdown.asStateFlow(),
                    // Where the small window is not possible, Back leaves as it always did.
                    onEnterHover = { if (!enterHover()) moveTaskToBack(false) },
                    onMirrorChange = { on -> if (on) startMirror() else app.mirror.stop() },
                    onEditorDirtyChange = { editorDirty.value = it },
                )
            }
        }
        countAccessibilityActivity()
        holdSplashUntilLoaded()
    }

    /**
     * TalkBack swipes and double-taps send no input events, so onUserInteraction never sees them, and the idle
     * cutoff would warn (and turn the cover off) in the middle of use. The accessibility focus moves and clicks
     * that Compose reports pass through the content view on their way to the system: count those instead.
     */
    private fun countAccessibilityActivity() {
        findViewById<View>(android.R.id.content).accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onRequestSendAccessibilityEvent(host: ViewGroup, child: View, event: AccessibilityEvent): Boolean {
                if (event.eventType == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED || event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                    noteUserActivity()
                }
                return super.onRequestSendAccessibilityEvent(host, child, event)
            }
        }
    }

    /** Someone is using Zlip: standby and the idle cutoff start counting afresh, and auto-start may run again. */
    private fun noteUserActivity() {
        lastInteraction.value = SystemClock.uptimeMillis()
        idleStopped.value = false
    }

    /**
     * Keeps the system splash up until the library is read (at most [SPLASH_MAX_MS]), so the first frame is the
     * real Home instead of an empty one that fills in a moment later.
     */
    private fun holdSplashUntilLoaded() {
        val giveUpAt = SystemClock.uptimeMillis() + SPLASH_MAX_MS
        val content = findViewById<View>(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    val ready = app.repository.loaded.value || SystemClock.uptimeMillis() >= giveUpAt
                    if (ready) content.viewTreeObserver.removeOnPreDrawListener(this)
                    return ready
                }
            },
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent, coldStart = false)
    }

    /** [coldStart]: the intent that created this activity, rather than one delivered to a running Zlip. */
    private fun handleIntent(intent: Intent?, coldStart: Boolean) {
        if (intent?.getBooleanExtra(EXTRA_START_COVER, false) == true) {
            val returnAfterStart = intent.getBooleanExtra(EXTRA_RETURN_AFTER_START, false)
            val fromPip = intent.getBooleanExtra(EXTRA_FROM_PIP, false)
            intent.removeExtra(EXTRA_START_COVER)
            intent.removeExtra(EXTRA_RETURN_AFTER_START)
            intent.removeExtra(EXTRA_FROM_PIP)
            app.cover.resetAutoStart()
            // Already on: there is nothing to start, and a request left standing would turn the cover back on
            // after a later "off".
            if (app.cover.status.value != CoverStatus.ACTIVE) startRequested.value = true
            // Shrink back only to where the user came from; a Zlip they were using full size stays as it is.
            if (returnsAfterStart(returnAfterStart, coldStart, hiddenFromUser, fromPip)) returnWhenStarted()
        }
        if (intent?.action == TileService.ACTION_QS_TILE_PREFERENCES || intent?.getBooleanExtra(EXTRA_OPEN_SETTINGS, false) == true) {
            intent.removeExtra(EXTRA_OPEN_SETTINGS)
            settingsRequested.value = true
        }
        if (intent?.getBooleanExtra(EXTRA_START_MIRROR, false) == true) {
            intent.removeExtra(EXTRA_START_MIRROR)
            mirrorRequested.value = true
        }
        if (intent?.getBooleanExtra(EXTRA_NEW_TEXT, false) == true) {
            intent.removeExtra(EXTRA_NEW_TEXT)
            incoming.value = IncomingContent.Text("")
        }
        if (intent == null) return
        val shared = IncomingContent.fromShare(intent)
        if (shared != null) {
            incoming.value = shared
        } else if (!coldStart && isShare(intent)) {
            // Shared to a running Zlip (onCreate turns a fresh one away before this).
            Toast.makeText(applicationContext, R.string.share_unsupported, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Opened only to turn the cover on (the tile, or the small window's button): once it shows, shrink back so
     * the user lands in the app they came from, with the cover on.
     */
    private fun returnWhenStarted() {
        if (!coverHoverSupported) return
        returnJob?.cancel()
        returnJob = lifecycleScope.launch {
            // A start that does not come (refused, folded meanwhile) leaves Zlip open to say why.
            withTimeoutOrNull(RETURN_TIMEOUT_MS) { app.cover.showing.first { it } } ?: return@launch
            // Done: the request must not linger and fire again after a later "off".
            startRequested.value = false
            // Unsaved edits stay full size, where leaving asks about them (see buildHoverParams).
            if (app.repository.currentSettings.keepCoverInOtherApps && !editorDirty.value) enterHover()
        }
    }

    /** Shrinks Zlip into its small window; false where Android (or the user, in system settings) refuses. */
    private fun enterHover(): Boolean {
        val params = hoverParams ?: return false
        return try {
            enterPictureInPictureMode(params)
        } catch (e: IllegalStateException) {
            false
        }
    }

    /**
     * The small window's buttons, as many as Android shows ([hoverButtons]). Cover on: previous, pause/play and next,
     * always in those slots like the cover's own ⏮ ⏯ ⏭; with room for more, next item (a group), Fit / Fill (a photo or
     * video) and "turn the cover off" (the window stays, as the way back on). A single item alone in the rotation has
     * nowhere to move, so Fit / Fill and turning off take the slots ⏮ and ⏭ would waste. Mirroring: whole screen / fill (as a tap
     * on the cover), stop mirroring and turn off. Cover off (turned off, or ended by the lock or screen timeout): turn
     * on. Closing the window is the system's own close button, which ends the cover with it.
     */
    private fun buildHoverParams(state: HoverState): PictureInPictureParams {
        val buttons = hoverButtons(state.coverOn, state.mirroring, state.media, state.group, state.canMove, max = maxNumPictureInPictureActions)
        val actions = buttons.map { button ->
            when (button) {
                HoverButton.PREVIOUS -> broadcastAction(R.drawable.ic_notif_previous, R.string.action_previous, CoverNotifier.ACTION_PREVIOUS)
                HoverButton.NEXT_ITEM -> broadcastAction(R.drawable.ic_notif_next_item, R.string.action_next_item, CoverNotifier.ACTION_NEXT_ITEM)
                HoverButton.MIRROR_VIEW -> broadcastAction(R.drawable.ic_pip_mirror_view, R.string.mirror_toggle_view, CoverNotifier.ACTION_MIRROR_VIEW)
                HoverButton.MIRROR_STOP -> broadcastAction(R.drawable.ic_pip_mirror_off, R.string.mirror_stop, CoverNotifier.ACTION_MIRROR_STOP)
                HoverButton.PLAY_PAUSE -> if (state.paused) {
                    broadcastAction(R.drawable.ic_notif_play, R.string.action_resume, CoverNotifier.ACTION_TOGGLE)
                } else {
                    broadcastAction(R.drawable.ic_notif_pause, R.string.action_pause, CoverNotifier.ACTION_TOGGLE)
                }
                // Says what a press does: a fitted picture fills the cover, a filled one fits.
                HoverButton.MEDIA_FIT -> if (state.mediaFit == MediaFit.FILL) {
                    broadcastAction(R.drawable.ic_pip_media_fit, R.string.media_fit_fit, CoverNotifier.ACTION_MEDIA_FIT)
                } else {
                    broadcastAction(R.drawable.ic_pip_mirror_view, R.string.media_fit_fill, CoverNotifier.ACTION_MEDIA_FIT)
                }
                HoverButton.NEXT -> broadcastAction(R.drawable.ic_notif_next, R.string.action_next, CoverNotifier.ACTION_NEXT)
                HoverButton.COVER_OFF -> broadcastAction(R.drawable.ic_pip_power, R.string.pip_cover_off, CoverNotifier.ACTION_STOP_KEEP_WINDOW)
                // Only a full-size Zlip may start the cover: expand, start it, and shrink back (returnWhenStarted).
                HoverButton.COVER_ON -> remoteAction(R.drawable.ic_pip_power, R.string.pip_cover_on, turnOnIntent())
            }
        }
        val title = when {
            state.coverOn && state.mirroring -> R.string.cover_mirroring
            state.coverOn -> R.string.pip_status_on
            else -> R.string.pip_status_off
        }
        return PictureInPictureParams.Builder()
            .setAspectRatio(HOVER_ASPECT)
            // Leaving Zlip shrinks it only while there is a cover to keep alive. Never with unsaved edits: on some
            // builds swiping the small window away removes the task, and the edits would go with it unasked.
            .setAutoEnterEnabled(state.coverOn && state.keep && !state.editorDirty)
            // A still card: nothing to cross-fade while it resizes.
            .setSeamlessResizeEnabled(false)
            .setTitle(getString(title))
            .setSubtitle(state.itemName.takeUnless { state.coverOn && state.mirroring })
            .setActions(actions)
            .build()
    }

    private fun broadcastAction(@DrawableRes icon: Int, @StringRes title: Int, action: String): RemoteAction =
        remoteAction(icon, title, CoverActionReceiver.pendingIntent(this, action))

    private fun remoteAction(@DrawableRes icon: Int, @StringRes title: Int, intent: PendingIntent): RemoteAction {
        val label = getString(title)
        return RemoteAction(Icon.createWithResource(this, icon), label, label, intent)
    }

    // An action of its own as well as its own request code: PendingIntents that only differ in extras are one
    // record, and another MainActivity intent (the widget's) would replace these extras.
    private fun turnOnIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        REQUEST_TURN_ON,
        Intent(this, MainActivity::class.java)
            .setAction(ACTION_TURN_ON_COVER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_START_COVER, true)
            .putExtra(EXTRA_RETURN_AFTER_START, true)
            // The window may already be full size when this arrives; it still means "shrink back".
            .putExtra(EXTRA_FROM_PIP, true),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** "Mirror screen": Android asks for permission every time; once allowed, the cover starts too if it is off. */
    private fun startMirror() {
        if (app.mirror.state.value != MirrorState.OFF) return
        app.mirror.onConsentRequested()
        val projections = getSystemService(MediaProjectionManager::class.java)
        try {
            // The whole screen, never a single app: the cover should show whatever the user opens next.
            mirrorConsent.launch(projections.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay()))
        } catch (e: ActivityNotFoundException) {
            app.mirror.onStartFailed()
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        noteUserActivity()
    }

    override fun onTopResumedActivityChanged(isTopResumedActivity: Boolean) {
        super.onTopResumedActivityChanged(isTopResumedActivity)
        app.cover.setTopResumed(isTopResumedActivity)
        topResumed.value = isTopResumedActivity
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        windowFocused.value = hasFocus
        // Back from the shade or another window: standby starts counting afresh.
        if (hasFocus) lastInteraction.value = SystemClock.uptimeMillis()
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        multiWindow.value = isInMultiWindowMode
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip.value = isInPictureInPictureMode
        app.cover.setHovering(isInPictureInPictureMode)
        if (isInPictureInPictureMode) {
            // The user is elsewhere now: a tile tap from there should shrink Zlip back after starting the cover.
            hiddenFromUser = true
            // Whatever start was asked for has run or can no longer apply; it must not fire on a later expand.
            startRequested.value = false
        }
        // Leaving the small window while stopped means it was dismissed (its close button, or swiped away),
        // not expanded: the cover goes off with it, and Zlip has left the screen. Not counted as the user's
        // "turn off": like any other way of leaving, the next visit starts the cover again.
        if (!isInPictureInPictureMode && !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            app.mirror.stop()
            app.cover.stop(byUser = false)
        }
    }

    override fun onStart() {
        super.onStart()
        // A fold or unfold whose display event has not arrived yet (a frozen process) starts the cover over now.
        app.sessions.check()
    }

    override fun onResume() {
        super.onResume()
        openingPicker = false
        if (!isInPictureInPictureMode) hiddenFromUser = false
        lastInteraction.value = SystemClock.uptimeMillis()
        // Back from the "Appear on top" page (or a reboot): start or stop the always-show service to match.
        CoverGuard.sync(this)
    }

    override fun onStop() {
        super.onStop()
        hiddenFromUser = true
        app.repository.flush()
        // A tile or shortcut request that could not run while we were visible must not fire on some later visit.
        startRequested.value = false
        mirrorRequested.value = false
        // Coming back is a new visit: auto-start may run again.
        idleStopped.value = false
        returnJob?.cancel()
        if (!openingPicker && !isChangingConfigurations) {
            // The user really left: the next visit starts the cover again.
            app.cover.resetAutoStart()
        }
    }

    private fun applyWindowState(keepScreenOn: Boolean, isStandby: Boolean) {
        if (keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        window.attributes = window.attributes.apply {
            screenBrightness = if (isStandby) {
                WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF
            } else {
                WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
        val bars = WindowCompat.getInsetsController(window, window.decorView)
        if (isStandby) {
            bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            bars.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            bars.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    companion object {
        const val EXTRA_START_COVER = "com.zlip.app.extra.START_COVER"

        /** With [EXTRA_START_COVER]: once the cover is on, shrink Zlip so the user is back in their app. */
        const val EXTRA_RETURN_AFTER_START = "com.zlip.app.extra.RETURN_AFTER_START"
        const val EXTRA_OPEN_SETTINGS = "com.zlip.app.extra.OPEN_SETTINGS"

        /** The "Ekranı yansıt" launcher shortcut: mirror the inner screen to the cover. */
        const val EXTRA_START_MIRROR = "com.zlip.app.extra.START_MIRROR"

        /** The "Yeni yazı" launcher shortcut: open a new text item. */
        const val EXTRA_NEW_TEXT = "com.zlip.app.extra.NEW_TEXT"

        /** The longest the system splash waits for the library before Home draws anyway. */
        private const val SPLASH_MAX_MS = 500L

        /** Folding within this long after the cover session ended still counts as "folded while using it". */
        private const val FOLD_GRACE_MS = 3_000L

        /** The idle cutoff's warning chip shows for this long before it. */
        private const val AUTO_OFF_WARNING_MS = 15_000L

        /** How long a cover start asked for from outside may take before Zlip stays open instead. */
        private const val RETURN_TIMEOUT_MS = 5_000L

        /** The small window's "turn cover on" button; set apart from every other MainActivity PendingIntent. */
        private const val ACTION_TURN_ON_COVER = "com.zlip.app.action.TURN_ON_COVER"

        /** With [EXTRA_START_COVER]: sent by the small window itself, so Zlip shrinks back however it shows now. */
        private const val EXTRA_FROM_PIP = "com.zlip.app.extra.FROM_PIP"

        /** Unique among MainActivity PendingIntents (see the list in CoverWidget). */
        private const val REQUEST_TURN_ON = 4

        /** The cover screen's shape (748 x 720 px), so the small window frames the cover's picture exactly. */
        private val HOVER_ASPECT = Rational(748, 720)
    }
}

/** What the small window shows and offers. */
private data class HoverState(
    val coverOn: Boolean,
    val keep: Boolean,
    val editorDirty: Boolean,
    val paused: Boolean,
    val mirroring: Boolean,
    val itemName: String?,
    /**
     * How the photo or video the cover shows (a group's step too) is fitted, for Fit / Fill's label; null when it
     * shows something else.
     */
    val mediaFit: MediaFit?,
    /** The selected item offers Fit / Fill ([hoverOffersFit]), whatever step of it shows. */
    val media: Boolean,
    /** A group is on the cover and can be left for another item. */
    val group: Boolean,
    /** ⏮ and ⏭ go somewhere ([com.zlip.app.cover.CoverNav.canMove]). */
    val canMove: Boolean,
)

/** The selected item, as far as the small window cares: its name, Fit / Fill, leaving a group, moving at all. */
private data class HoverItem(val name: String?, val media: Boolean, val group: Boolean, val canMove: Boolean)

/** A button of the small window. */
internal enum class HoverButton { MIRROR_VIEW, MIRROR_STOP, PREVIOUS, PLAY_PAUSE, NEXT, NEXT_ITEM, MEDIA_FIT, COVER_OFF, COVER_ON }

/**
 * The small window's buttons, in order, cut to the [max] Android shows (usually three). With the cover on, ⏯ and ⏭ come
 * first and never move; the third is Fit / Fill for a photo or video ([media], see [hoverOffersFit]), which the user
 * asked to have in the window, and ⏮ otherwise. Room for more adds ⏮ (after Fit / Fill), next item ([group]: a group
 * that can be left) and turning the cover off. When moves would go nowhere (![canMove]: a single item alone in the
 * rotation), ⏯, Fit / Fill and turning off take the slots instead. The window's own close button comes from the
 * system, so none of these is spent on it. Pure, so it is unit-tested.
 */
internal fun hoverButtons(coverOn: Boolean, mirroring: Boolean, media: Boolean, group: Boolean, canMove: Boolean, max: Int): List<HoverButton> {
    val all = when {
        coverOn && mirroring -> listOf(HoverButton.MIRROR_VIEW, HoverButton.MIRROR_STOP, HoverButton.COVER_OFF)
        coverOn && !canMove -> listOfNotNull(HoverButton.PLAY_PAUSE, HoverButton.MEDIA_FIT.takeIf { media }, HoverButton.COVER_OFF)
        coverOn -> listOfNotNull(
            HoverButton.PLAY_PAUSE,
            HoverButton.NEXT,
            if (media) HoverButton.MEDIA_FIT else HoverButton.PREVIOUS,
            HoverButton.PREVIOUS.takeIf { media },
            HoverButton.NEXT_ITEM.takeIf { group },
            HoverButton.COVER_OFF,
        )
        else -> listOf(HoverButton.COVER_ON)
    }
    return all.take(max.coerceAtLeast(0))
}

/**
 * Whether the small window offers Fit / Fill for [selected]: a photo or video, or a group with one among its steps.
 * Decided by the item rather than the step on screen, so the buttons stay put while a group plays.
 */
internal fun hoverOffersFit(selected: CoverItem?): Boolean = when (selected) {
    is CoverItem.Media -> true
    is CoverItem.Group -> selected.items.any { it is CoverItem.Media }
    else -> false
}

private fun isShare(intent: Intent?): Boolean =
    intent?.action == Intent.ACTION_SEND || intent?.action == Intent.ACTION_SEND_MULTIPLE

/** What [MainActivity.onCreate] does with the intent that created it, as far as shares go. */
internal enum class ShareLaunch {
    NOT_A_SHARE,

    /** Nothing Zlip can use: say so and close. */
    UNSUPPORTED,

    /** Created inside the sharing app's task: hand the share to Zlip's own task and close. */
    FORWARD,

    /** Zlip's own task: open the shared content. */
    OPEN,
}

internal fun shareLaunch(isShare: Boolean, usable: Boolean, isTaskRoot: Boolean): ShareLaunch = when {
    !isShare -> ShareLaunch.NOT_A_SHARE
    !usable -> ShareLaunch.UNSUPPORTED
    !isTaskRoot -> ShareLaunch.FORWARD
    else -> ShareLaunch.OPEN
}

/**
 * Whether a cover start asked for from outside shrinks Zlip back once the cover shows: only when the request
 * [asked] for it and the user came from elsewhere (a fresh launch, Zlip out of sight or only its small window,
 * or the small window's own button), never when they were already using Zlip full size.
 */
internal fun returnsAfterStart(asked: Boolean, coldStart: Boolean, hiddenFromUser: Boolean, fromPip: Boolean): Boolean =
    asked && (coldStart || hiddenFromUser || fromPip)

/**
 * Whether Zlip, on top, should start the cover now: it must be free, and either auto-start is on and allowed
 * (the user did not turn it off, the idle cutoff did not just end it) or someone explicitly [requested] it.
 */
internal fun shouldStartCover(
    top: Boolean,
    loaded: Boolean,
    status: CoverStatus,
    autoStart: Boolean,
    allowed: Boolean,
    idleStopped: Boolean,
    requested: Boolean,
): Boolean = top && loaded && status == CoverStatus.AVAILABLE && ((autoStart && allowed && !idleStopped) || requested)
