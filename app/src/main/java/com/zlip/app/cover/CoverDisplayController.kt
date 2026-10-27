package com.zlip.app.cover

import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import androidx.window.area.WindowAreaCapability
import androidx.window.area.WindowAreaController
import androidx.window.area.WindowAreaInfo
import androidx.window.area.WindowAreaPresentationSessionCallback
import androidx.window.area.WindowAreaSessionPresenter
import androidx.window.core.ExperimentalWindowApi
import com.zlip.app.cover.mirror.MirrorController
import com.zlip.app.data.ContentRepository
import com.zlip.app.data.CoverSettings
import com.zlip.app.ui.ComposeHostOwner
import com.zlip.app.ui.theme.ZlipTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class CoverError {
    /** Android refused to open the cover (we were not the top app even after retries). */
    START_REJECTED,

    /** The system ended a running session with an error. */
    SESSION_FAILED,
}

enum class CoverStatus {
    /** Still asking the system. */
    CHECKING,

    /** This phone/OS has no rear-display presentation support. */
    UNSUPPORTED,

    /** Supported but not possible right now (e.g. phone folded, power saving, or another app holds the cover). */
    UNAVAILABLE,

    /** Ready to start. */
    AVAILABLE,

    /** Our content is on the cover screen. */
    ACTIVE,
}

/**
 * Android 15+ keeps the open-phone cover on for as long as any Zlip window is visible (picture-in-picture, split
 * screen or a pop-up window included). Android 14 ends it as soon as another app comes to the front, so there a
 * small Zlip window cannot hold it.
 */
val coverHoverSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM

/**
 * Shows [CoverScreen] on the outer display while the phone is open, using Jetpack WindowManager's
 * rear-display presentation (dual-screen) mode. Only the top (focused) app may start a session. Once
 * running, Android 15+ keeps it while any Zlip activity is visible, even unfocused or in
 * picture-in-picture, and ends it when Zlip has no visible window left, the phone folds, power saving
 * kicks in or the screen locks or sleeps; the activity starts it again when it is the top app.
 *
 * Battery: the cover window gets a refresh-rate cap from [CoverSettings.powerMode], and holds the screen
 * on only while Zlip asks for it ([setScreenHold]).
 */
@OptIn(ExperimentalWindowApi::class)
class CoverDisplayController(
    private val repository: ContentRepository,
    private val playback: CoverPlayback,
    private val sessions: CoverSessions,
    private val mirror: MirrorController,
    private val scope: CoroutineScope,
) {
    private val windowAreaController: WindowAreaController by lazy { WindowAreaController.getOrCreate() }

    private val area = MutableStateFlow(Area.CHECKING)
    private val sessionActive = MutableStateFlow(false)
    private val startingState = MutableStateFlow(false)

    /** Our own session just ended; the area may still report itself as held until its next update. */
    private val releasedByUs = MutableStateFlow(false)
    private val _lastError = MutableStateFlow<CoverError?>(null)
    private var retryJob: Job? = null
    private val userStopped = MutableStateFlow(false)
    private val autoStartBlocked = MutableStateFlow(false)

    private val _endedByUser = MutableStateFlow(false)
    private val _closeRefused = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val screenHold = MutableStateFlow(true)
    private val hovering = MutableStateFlow(false)

    private var rearArea: WindowAreaInfo? = null
    private var session: WindowAreaSessionPresenter? = null
    private var coverView: View? = null
    private var viewOwner: ComposeHostOwner? = null
    private var windowJob: Job? = null
    private var starting: Boolean
        get() = startingState.value
        set(value) {
            startingState.value = value
        }
    private var closeRequested = false
    private var sessionStartedAt = 0L
    private var topResumed = false
    private var lostTopSinceStart = false

    /**
     * The area reports itself as held while our own session starts and ends, a moment before our session
     * callbacks run; that is us, not another app, so it must not read as "unavailable" in between.
     */
    val status: StateFlow<CoverStatus> = combine(area, sessionActive, startingState, releasedByUs) { area, active, starting, released ->
        when {
            active -> CoverStatus.ACTIVE
            area == Area.HELD && starting -> CoverStatus.ACTIVE
            area == Area.HELD && released -> CoverStatus.AVAILABLE
            else -> area.status
        }
    }.stateIn(scope, SharingStarted.Eagerly, CoverStatus.CHECKING)

    /** Why the last start or session failed, for a friendly message; cleared on the next start. */
    val lastError: StateFlow<CoverError?> = _lastError.asStateFlow()

    /**
     * False after the user turned the cover off, or after a start failed / the system closed the session
     * right away while we were on top (so restarts cannot loop). [resetAutoStart] re-arms it.
     */
    val autoStartAllowed: StateFlow<Boolean> = combine(userStopped, autoStartBlocked) { stopped, blocked ->
        !stopped && !blocked
    }.stateIn(scope, SharingStarted.Eagerly, true)

    /** Our content is on the cover right now ([status] already reads ACTIVE while a session is starting). */
    val showing: StateFlow<Boolean> = sessionActive.asStateFlow()

    /**
     * The user turned the cover off on purpose; cleared by the next start. Unlike [autoStartAllowed] this
     * survives leaving the app, so folding right after "turn off" does not bring up the screen saver.
     */
    val endedByUser: StateFlow<Boolean> = _endedByUser.asStateFlow()

    /**
     * Android refused to let us close our own session (some builds allow that only to the top app, and Zlip
     * may be just its small window). The activity then hides that window, which ends the session anyway.
     */
    val closeRefused: SharedFlow<Unit> = _closeRefused.asSharedFlow()

    init {
        // Tracked for the whole process, not just while an activity is visible: the notification and the
        // quick-settings tile must know when folding makes the cover unavailable. The system only calls
        // back on device-state changes, so this costs nothing while idle.
        scope.launch {
            windowAreaController.windowAreaInfos.collect { infos ->
                val rear = infos.firstOrNull { it.type == WindowAreaInfo.Type.TYPE_REAR_FACING }
                rearArea = rear
                releasedByUs.value = false
                area.value = rear.toArea()
            }
        }
    }

    /** The activity reports focus here; Android only lets the focused (top resumed) app start the cover. */
    fun setTopResumed(isTop: Boolean) {
        topResumed = isTop
        if (!isTop) {
            lostTopSinceStart = true
            retryJob?.cancel()
        }
    }

    /** Must be called while [activity] is the top resumed activity; Android rejects other callers. */
    fun start(activity: ComponentActivity) {
        if (session != null || starting || retryJob?.isActive == true) return
        closeRequested = false
        lostTopSinceStart = false
        userStopped.value = false
        _endedByUser.value = false
        _lastError.value = null
        attemptStart(activity, attempt = 0)
    }

    /**
     * Whether the cover window keeps the screen awake. Zlip lets go in its small window and after the idle
     * cutoff, so the system's screen timeout (fed by touches anywhere) can end the session.
     */
    fun setScreenHold(hold: Boolean) {
        screenHold.value = hold
    }

    /** Zlip is only on screen as its picture-in-picture window: the cover plays the playlist, never a draft. */
    fun setHovering(inPip: Boolean) {
        hovering.value = inPip
    }

    private fun attemptStart(activity: ComponentActivity, attempt: Int) {
        val area = rearArea ?: return
        if (session != null || starting) return
        starting = true
        try {
            windowAreaController.presentContentOnWindowArea(area.token, activity, activity.mainExecutor, sessionCallback)
        } catch (e: SecurityException) {
            starting = false
            // The focus callback can arrive a moment before the system counts us as the top app (seen on
            // One UI right after returning from Home or the shade): wait briefly and try again.
            if (attempt < START_RETRY_DELAYS_MS.size) {
                retryJob = activity.lifecycleScope.launch {
                    delay(START_RETRY_DELAYS_MS[attempt])
                    if (topResumed && !closeRequested) attemptStart(activity, attempt + 1)
                }
            } else {
                failStart(e)
            }
        } catch (e: RuntimeException) {
            // IllegalStateException if the system still has a session open.
            starting = false
            failStart(e)
        }
    }

    private fun failStart(e: RuntimeException) {
        Log.w(TAG, "Could not start the cover screen", e)
        autoStartBlocked.value = true
        _lastError.value = CoverError.START_REJECTED
    }

    /**
     * Turns the cover off, also while it is still starting. [byUser] keeps auto-start from turning it straight
     * back on.
     */
    fun stop(byUser: Boolean = true) {
        if (byUser) {
            userStopped.value = true
            _endedByUser.value = true
        }
        closeRequested = true
        session?.let(::close)
    }

    private fun close(session: WindowAreaSessionPresenter) {
        try {
            session.close()
        } catch (e: SecurityException) {
            Log.w(TAG, "Not allowed to close the cover session", e)
            _closeRefused.tryEmit(Unit)
        }
    }

    /** Called when the user leaves the app or explicitly asks for the cover again. */
    fun resetAutoStart() {
        userStopped.value = false
        autoStartBlocked.value = false
    }

    private val sessionCallback = object : WindowAreaPresentationSessionCallback {
        override fun onSessionStarted(session: WindowAreaSessionPresenter) {
            starting = false
            sessionStartedAt = SystemClock.uptimeMillis()
            this@CoverDisplayController.session = session
            releasedByUs.value = false
            // "Turn off" arrived while the session was still starting: end it before anything shows.
            if (closeRequested) {
                close(session)
                return
            }
            // An unfold whose display event is still on its way starts the content over before it shows. A
            // session that merely restarts (the shade can do that) keeps its pause.
            sessions.check()
            val owner = ComposeHostOwner().also { viewOwner = it }
            val view = owner.composeView(session.context) {
                ZlipTheme {
                    val inPip by hovering.collectAsState()
                    CoverScreen(repository, playback, mirror = mirror, inPip = inPip)
                }
            }.also { coverView = it }
            session.setContentView(view)
            windowJob = scope.launch {
                // Only a real change: re-applying window attributes relayouts the cover window (a brightness
                // drag changes the settings many times a second).
                launch {
                    repository.settings.map { it.powerMode.fps }.distinctUntilChanged()
                        .collect { fps -> applyRefreshRate(session.window, fps) }
                }
                launch { screenHold.collect { hold -> applyScreenHold(session.window, view, hold) } }
            }
            sessionActive.value = true
        }

        override fun onContainerVisibilityChanged(isVisible: Boolean) = Unit

        override fun onSessionEnded(t: Throwable?) {
            val wasStarted = session != null
            val lived = SystemClock.uptimeMillis() - sessionStartedAt
            starting = false
            session = null
            coverView = null
            windowJob?.cancel()
            windowJob = null
            viewOwner?.destroy()
            viewOwner = null
            if (wasStarted) releasedByUs.value = true
            sessionActive.value = false
            if (t != null) {
                Log.w(TAG, "Cover session ended with an error", t)
                _lastError.value = CoverError.SESSION_FAILED
            }
            // Leaving Zlip legitimately ends the session (cancel_when_requester_not_on_top); only a
            // failure while we stayed on top counts against auto-start.
            val lostFocus = lostTopSinceStart || !topResumed
            if (!closeRequested && !lostFocus && (t != null || !wasStarted || lived < MIN_HEALTHY_SESSION_MS)) {
                autoStartBlocked.value = true
            }
        }
    }

    private fun applyRefreshRate(window: Window?, fps: Int) {
        if (window != null) {
            window.attributes = window.attributes.apply {
                // No screenBrightness: One UI ignores it on the cover's presentation window (verified on a
                // Z Flip7 FE), so brightness is applied as content dimming in CoverScreen instead.
                // A hint for the system's per-app frame-rate override; the real cap is enforced in-app
                // (marquee stepping, 30 fps video, still thumbnails).
                preferredRefreshRate = fps.toFloat()
            }
        }
    }

    /** The cover window likely keeps the whole display group awake, so it follows Zlip's own window. */
    private fun applyScreenHold(window: Window?, view: View, hold: Boolean) {
        view.keepScreenOn = hold
        if (hold) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun WindowAreaInfo?.toArea(): Area {
        if (this == null) return Area.UNSUPPORTED
        return when (getCapability(WindowAreaCapability.Operation.OPERATION_PRESENT_ON_AREA)?.status) {
            WindowAreaCapability.Status.WINDOW_AREA_STATUS_AVAILABLE -> Area.AVAILABLE
            WindowAreaCapability.Status.WINDOW_AREA_STATUS_ACTIVE -> Area.HELD
            WindowAreaCapability.Status.WINDOW_AREA_STATUS_UNAVAILABLE -> Area.UNAVAILABLE
            else -> Area.UNSUPPORTED
        }
    }

    /** What the system says about the rear display; [HELD] means some session (maybe ours) is using it. */
    private enum class Area(val status: CoverStatus) {
        CHECKING(CoverStatus.CHECKING),
        UNSUPPORTED(CoverStatus.UNSUPPORTED),
        UNAVAILABLE(CoverStatus.UNAVAILABLE),
        AVAILABLE(CoverStatus.AVAILABLE),
        HELD(CoverStatus.UNAVAILABLE),
    }

    private companion object {
        const val TAG = "ZlipCover"

        /** A session the system closes faster than this (without us asking) counts as a failed start. */
        const val MIN_HEALTHY_SESSION_MS = 1_500L

        /** Back-off for "not the top app yet" rejections before giving up and showing an error. */
        val START_RETRY_DELAYS_MS = longArrayOf(250, 600, 1_200)
    }
}

