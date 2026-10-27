package com.zlip.app.cover.mirror

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.Surface
import android.widget.Toast
import androidx.compose.ui.unit.IntSize
import com.zlip.app.R
import com.zlip.app.cover.CoverStatus
import com.zlip.app.data.ContentRepository
import com.zlip.app.data.MirrorFit
import com.zlip.app.system.CoverGuard
import com.zlip.app.system.MirrorService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class MirrorState {
    OFF,

    /** The system's screen-sharing dialog is up. */
    ASKING,

    /** The inner screen is shown on the open-phone cover. */
    ON,
}

/**
 * Screen mirroring: the inner screen (display 0) shown on the open-phone cover, through a MediaProjection
 * that the user allows for every session (Android asks each time) and a virtual display drawing straight into a
 * SurfaceView in the cover window, so the display hardware composes it and Zlip decodes nothing.
 *
 * A projection may create only one virtual display, so it is created once and outlives cover windows: when the
 * cover session restarts (our own picker, the consent dialog) the display is detached and attached to the new
 * window's surface. Mirroring ends with the cover session (after a short grace), when the inner screen goes dark
 * (folded, timed out), and when Android stops the projection (lock, the status-bar chip, another app sharing).
 * Never persisted: consent does not carry over.
 */
class MirrorController(
    private val context: Context,
    private val repository: ContentRepository,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(MirrorState.OFF)
    val state: StateFlow<MirrorState> = _state.asStateFlow()

    /** The captured screen's size; it swaps on rotation. */
    private val contentSize = MutableStateFlow<IntSize?>(null)

    /** The cover's mirror view, once a cover window shows it. */
    private val coverSize = MutableStateFlow<IntSize?>(null)

    /** Which part of an overflowing filled mirror shows, -1..1 (see [mirrorLayout]). */
    private val pan = MutableStateFlow(0f)

    /** Where the mirror sits on the cover; null until the cover's mirror view has been measured. */
    val layout: StateFlow<MirrorLayout?> = combine(
        contentSize,
        coverSize,
        repository.settings.map { it.mirrorFit }.distinctUntilChanged(),
        pan,
    ) { content, cover, fit, offset ->
        cover?.let { mirrorLayout(content ?: it, it, fit, offset) }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    private val handler = Handler(Looper.getMainLooper())
    private val displays by lazy { context.getSystemService(DisplayManager::class.java) }
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var surface: Surface? = null
    private var transitions: CoverGuard.DarkTransitions? = null

    private val callback = object : MediaProjection.Callback() {
        override fun onStop() {
            // Android ended it (lock, the status-bar chip, another app sharing), or we did.
            stop()
        }

        override fun onCapturedContentResize(width: Int, height: Int) {
            if (width > 0 && height > 0) contentSize.value = IntSize(width, height)
        }
    }

    /** Folding (or the inner screen timing out) leaves nothing to mirror. */
    private val innerScreenListener = object : DisplayManager.DisplayListener {
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == Display.DEFAULT_DISPLAY && transitions?.justWentDark(displayId) == true) stop()
        }

        override fun onDisplayAdded(displayId: Int) = Unit

        override fun onDisplayRemoved(displayId: Int) = Unit
    }

    init {
        scope.launch { layout.collect { resizeDisplay() } }
    }

    /**
     * Ends mirroring once the cover session has been gone for [COVER_GRACE_MS]: long enough for a session to come
     * back after our own picker or a system dialog, short enough not to leave a projection running for nothing.
     */
    fun followCover(status: StateFlow<CoverStatus>) {
        scope.launch {
            combine(state, status) { mirror, cover -> mirror == MirrorState.ON && cover != CoverStatus.ACTIVE }
                .distinctUntilChanged()
                .collectLatest { orphaned ->
                    if (orphaned) {
                        delay(COVER_GRACE_MS)
                        stop()
                    }
                }
        }
    }

    fun onConsentRequested() {
        if (_state.value == MirrorState.OFF) _state.value = MirrorState.ASKING
    }

    fun onConsentDenied() {
        if (_state.value == MirrorState.ASKING) _state.value = MirrorState.OFF
    }

    /** The foreground service could not obtain the projection the user allowed. */
    fun onStartFailed() {
        onConsentDenied()
        Toast.makeText(context, R.string.mirror_failed, Toast.LENGTH_LONG).show()
    }

    /** Called by [MirrorService] with the projection the user just allowed. */
    internal fun attach(projection: MediaProjection) {
        if (_state.value != MirrorState.ASKING) {
            // Turned off while the dialog was up: the permission is not used.
            projection.stop()
            MirrorService.stop(context)
            return
        }
        this.projection = projection
        // Must be registered before the virtual display exists, or Android refuses to create it.
        projection.registerCallback(callback, handler)
        contentSize.value = innerScreenSize()
        val size = layout.value?.display ?: DEFAULT_COVER_SIZE
        virtualDisplay = try {
            projection.createVirtualDisplay(
                "ZlipMirror",
                size.width,
                size.height,
                densityFor(size),
                // Never PUBLIC: other apps must not be able to present on our mirror.
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface,
                null,
                handler,
            )
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not create the mirror display", e)
            null
        }
        if (virtualDisplay == null) {
            stop()
            Toast.makeText(context, R.string.mirror_failed, Toast.LENGTH_LONG).show()
            return
        }
        transitions = CoverGuard.DarkTransitions(displays)
        displays.registerDisplayListener(innerScreenListener, handler)
        _state.value = MirrorState.ON
    }

    /** The cover window's SurfaceView got a surface; mirroring draws into it from now on. */
    fun attachSurface(surface: Surface) {
        this.surface = surface
        virtualDisplay?.surface = surface
    }

    /** [surface] is going away with its window; the projection keeps running without drawing. */
    fun detachSurface(surface: Surface) {
        if (this.surface != surface) return
        this.surface = null
        virtualDisplay?.surface = null
    }

    fun setCoverSize(size: IntSize) {
        if (size.width > 0 && size.height > 0) coverSize.value = size
    }

    /** A tap on the cover: switches between the whole screen and a filled, centered crop. */
    fun toggleFit() {
        pan.value = 0f
        repository.updateSettings {
            it.copy(mirrorFit = if (it.mirrorFit == MirrorFit.FIT) MirrorFit.FILL else MirrorFit.FIT)
        }
    }

    /** A swipe along [axis] shows more of the hidden part of a filled mirror; other swipes do nothing. */
    fun pan(axis: PanAxis, towardsEnd: Boolean) {
        if (layout.value?.panAxis != axis || axis == PanAxis.NONE) return
        pan.value = (pan.value + if (towardsEnd) PAN_STEP else -PAN_STEP).coerceIn(-1f, 1f)
    }

    /** Ends mirroring (the user, the cover going away, or Android); the next start asks for permission again. */
    fun stop() {
        val wasRunning = projection != null
        if (_state.value == MirrorState.OFF && !wasRunning) return
        _state.value = MirrorState.OFF
        if (transitions != null) {
            displays.unregisterDisplayListener(innerScreenListener)
            transitions = null
        }
        virtualDisplay?.release()
        virtualDisplay = null
        val ended = projection
        projection = null
        pan.value = 0f
        ended?.unregisterCallback(callback)
        ended?.stop()
        if (wasRunning) MirrorService.stop(context)
    }

    private fun resizeDisplay() {
        val display = virtualDisplay ?: return
        val size = layout.value?.display ?: return
        display.resize(size.width, size.height, densityFor(size))
    }

    /** The inner screen as it is turned now; [MediaProjection.Callback.onCapturedContentResize] follows it later. */
    private fun innerScreenSize(): IntSize? {
        val inner = displays.getDisplay(Display.DEFAULT_DISPLAY) ?: return null
        val mode = inner.mode
        val sideways = inner.rotation == Surface.ROTATION_90 || inner.rotation == Surface.ROTATION_270
        return if (sideways) IntSize(mode.physicalHeight, mode.physicalWidth) else IntSize(mode.physicalWidth, mode.physicalHeight)
    }

    /** The inner screen's density scaled to the mirror, so its content keeps its proportions. */
    private fun densityFor(size: IntSize): Int {
        val inner = contentSize.value?.width?.takeIf { it > 0 } ?: return context.resources.displayMetrics.densityDpi
        return (context.resources.displayMetrics.densityDpi * size.width / inner).coerceAtLeast(MIN_DPI)
    }

    private companion object {
        const val TAG = "ZlipMirror"
        const val COVER_GRACE_MS = 4_000L

        /** Start, middle and end of the overflow are one swipe apart. */
        const val PAN_STEP = 1f

        const val MIN_DPI = 72
        val DEFAULT_COVER_SIZE = IntSize(748, 720)
    }
}
