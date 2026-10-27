package com.zlip.app.cover.mirror

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Choreographer
import android.view.Display
import android.view.Surface
import android.widget.Toast
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import com.zlip.app.R
import com.zlip.app.cover.CoverStatus
import com.zlip.app.data.ContentRepository
import com.zlip.app.data.MirrorFit
import com.zlip.app.system.CoverGuard
import com.zlip.app.system.MirrorService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.math.hypot

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
 *
 * The cover is a viewport onto the screen ([view]): fingers drag it along and pinch it, a fling glides on, a tap
 * or a chip snaps to a [MirrorPreset]. Moving only moves and scales the SurfaceView's frame, which the compositor
 * does for free; the virtual display itself is resized once the view has come to rest.
 */
@OptIn(FlowPreview::class)
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

    private val _view = MutableStateFlow(MirrorView())

    /** The part of the screen the cover shows; every mirroring starts from the setting's view (see [startView]). */
    val view: StateFlow<MirrorView> = _view.asStateFlow()

    /** Where the mirror sits on the cover; null until the cover's mirror view has been measured. */
    val layout: StateFlow<MirrorLayout?> = combine(contentSize, coverSize, _view) { content, cover, view ->
        cover?.let { mirrorLayout(content ?: it, it, view) }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    /** The preset a running snap heads for, so the chips show the choice at once rather than at its end. */
    private val snapTarget = MutableStateFlow<MirrorPreset?>(null)

    /** The preset on the cover, for the inner screen's chips; null after a pinch to somewhere in between. */
    val preset: StateFlow<MirrorPreset?> = combine(contentSize, coverSize, _view, snapTarget) { content, cover, view, target ->
        target ?: cover?.let { presetAt(view.zoom, content ?: it, it) }
    }.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, null)

    private val _buffer = MutableStateFlow<IntSize?>(null)

    /** The virtual display's size (and the SurfaceView's buffer): [MirrorLayout.buffer] once the view has settled. */
    val buffer: StateFlow<IntSize?> = _buffer.asStateFlow()

    /** A finger is on the mirror: the display is not resized under it. */
    private val touching = MutableStateFlow(false)

    /** The glide in progress (a fling, a snap, a scroll); a new touch or a new glide stops it. */
    private var motion: Job? = null
    private var flinging = false
    private var snapToken = 0

    /** The setting's view, applied as soon as both sizes are known (the cover's arrives with its first frame). */
    private var pendingStart: MirrorPreset? = null
    private var hintShown = false

    private val handler = Handler(Looper.getMainLooper())
    private val displays by lazy { context.getSystemService(DisplayManager::class.java) }
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var displaySize: IntSize? = null
    private var surface: Surface? = null
    private var transitions: CoverGuard.DarkTransitions? = null

    private val callback = object : MediaProjection.Callback() {
        override fun onStop() {
            // Android ended it (lock, the status-bar chip, another app sharing), or we did.
            stop()
        }

        override fun onCapturedContentResize(width: Int, height: Int) {
            if (width > 0 && height > 0) updateSizes(content = IntSize(width, height))
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
        // Resizing the virtual display re-lays out the whole mirrored screen: far too heavy for every frame of a drag,
        // a pinch or a fling, so it waits until the fingers are up and the view has been still for a moment.
        scope.launch {
            combine(layout, touching) { placed, touched -> placed?.buffer?.takeUnless { touched } }
                .filterNotNull()
                .distinctUntilChanged()
                .debounce { if (_buffer.value == null) 0L else BUFFER_SETTLE_MS }
                .collect { size ->
                    _buffer.value = size
                    resizeDisplay(size)
                }
        }
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
        // A fresh start: the setting's view, and the gesture hint once more.
        cancelMotion()
        hintShown = false
        pendingStart = repository.currentSettings.mirrorFit.preset()
        updateSizes(content = innerScreenSize() ?: contentSize.value)
        // Before the first cover frame the layout is not known yet: the filled size for the usual cover meanwhile.
        val size = layout.value?.buffer ?: mirrorLayout(contentSize.value ?: DEFAULT_COVER_SIZE, DEFAULT_COVER_SIZE, MirrorView()).buffer
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
        displaySize = size
        _buffer.value = size
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
        if (size.width > 0 && size.height > 0) updateSizes(cover = size)
    }

    /** True once per mirroring session: the first unlock says how to steer the mirror. */
    fun takeGestureHint(): Boolean {
        if (hintShown) return false
        hintShown = true
        return true
    }

    /**
     * A finger went down on the mirror: whatever was gliding stops under it, and the display waits for it to lift.
     * True when that stopped a fling: the touch was to catch it, as on any list, so a tap then changes nothing more.
     */
    fun onTouchStart(): Boolean {
        val caughtFling = flinging && motion?.isActive == true
        cancelMotion()
        touching.value = true
        return caughtFling
    }

    /** The fingers moved by ([dx], [dy]) cover pixels: the screen follows them, up to its edges. */
    fun pan(dx: Float, dy: Float) = steer { view, content, cover -> view.panned(content, cover, dx, dy) }

    /** A pinch: zoom by [factor] around [focus], the fingers' midpoint in cover pixels. */
    fun zoomBy(factor: Float, focus: Offset) = steer { view, content, cover -> view.zoomed(content, cover, factor, focus) }

    /**
     * Every finger is up; with a [velocity] (cover pixels per second) the screen glides on and slows down as [decay]
     * says (the cover's own scrolling physics, from the gesture).
     */
    fun onTouchEnd(velocity: Velocity = Velocity.Zero, decay: DecayAnimationSpec<Float> = exponentialDecay()) {
        touching.value = false
        if (velocity != Velocity.Zero) fling(velocity, decay)
    }

    /** Glides on at [velocity] (cover pixels per second), slowing down, until it stops or meets the screen's edges. */
    fun fling(velocity: Velocity, decay: DecayAnimationSpec<Float> = exponentialDecay()) {
        val speed = hypot(velocity.x, velocity.y)
        if (speed < 1f) return
        val towardsX = velocity.x / speed
        val towardsY = velocity.y / speed
        glide {
            var travelled = 0f
            AnimationState(0f, speed).animateDecay(decay) {
                val step = value - travelled
                travelled = value
                val before = _view.value
                pan(towardsX * step, towardsY * step)
                // Stopped by the edges along every axis it was moving on: nothing left to glide.
                if (step != 0f && _view.value == before) cancelAnimation()
            }
        }
        flinging = true
    }

    /**
     * Animates to [preset]; around [focus] (cover pixels, where a tap landed) when given, so that spot stays under the
     * finger, otherwise keeping the view's center (Whole screen and back returns to the same spot).
     */
    fun snapTo(preset: MirrorPreset, focus: Offset? = null) {
        val cover = coverSize.value ?: return
        val target = presetZoom(preset, contentSize.value ?: cover, cover)
        val token = ++snapToken
        snapTarget.value = preset
        glide {
            try {
                animate(_view.value.zoom, target, animationSpec = tween(SNAP_MS, easing = FastOutSlowInEasing)) { zoom, _ ->
                    steer { view, content, size ->
                        if (focus != null) view.zoomed(content, size, zoom / view.zoom, focus) else view.copy(zoom = zoom)
                    }
                }
            } finally {
                if (token == snapToken) snapTarget.value = null
            }
        }
    }

    /**
     * A tap on the cover (or the small window's button): the whole screen when zoomed in at all, otherwise filled,
     * around where the tap landed. Remembered as the view the next mirroring starts with.
     */
    fun toggleView(focus: Offset? = null) {
        val cover = coverSize.value ?: return
        val content = contentSize.value ?: cover
        val showing = snapTarget.value ?: presetAt(_view.value.zoom, content, cover)
        val zoomedIn = if (showing != null) showing != MirrorPreset.FIT else _view.value.zoom > 1f
        // A screen shaped like the cover is already filled as a whole: going closer is the only other view.
        val target = when {
            zoomedIn -> MirrorPreset.FIT
            fillZoom(content, cover) > MIN_FILL_GAIN -> MirrorPreset.FILL
            else -> MirrorPreset.CLOSE
        }
        choose(target, focus.takeIf { target != MirrorPreset.FIT })
    }

    /** A chip on the inner screen (or a tap): snaps to [preset]; Whole screen and Fill are kept as the starting view. */
    fun choose(preset: MirrorPreset, focus: Offset? = null) {
        snapTo(preset, focus)
        val fit = when (preset) {
            MirrorPreset.FIT -> MirrorFit.FIT
            MirrorPreset.FILL -> MirrorFit.FILL
            MirrorPreset.CLOSE -> return
        }
        if (repository.currentSettings.mirrorFit != fit) repository.updateSettings { it.copy(mirrorFit = fit) }
    }

    /** Glides by ([dx], [dy]) cover pixels, as a drag that far would: TalkBack's scroll actions and "back to the top". */
    fun scrollBy(dx: Float, dy: Float) {
        glide {
            var moved = Offset.Zero
            animate(0f, 1f, animationSpec = tween(SNAP_MS, easing = FastOutSlowInEasing)) { progress, _ ->
                val step = Offset(dx * progress, dy * progress) - moved
                moved += step
                pan(step.x, step.y)
            }
        }
    }

    /** Back to the top left, where a page starts, at the same zoom. */
    fun toStart() {
        val placed = layout.value ?: return
        scrollBy(if (placed.canPanX) -placed.offset.x.toFloat() else 0f, if (placed.canPanY) -placed.offset.y.toFloat() else 0f)
    }

    /** TalkBack's zoom actions: a step closer or further around the cover's middle. */
    fun zoomStep(closer: Boolean) {
        val cover = coverSize.value ?: return
        val middle = Offset(cover.width / 2f, cover.height / 2f)
        val factor = if (closer) ZOOM_STEP else 1f / ZOOM_STEP
        glide {
            var applied = 1f
            animate(1f, factor, animationSpec = tween(SNAP_MS, easing = FastOutSlowInEasing)) { value, _ ->
                zoomBy(value / applied, middle)
                applied = value
            }
        }
    }

    /** Ends mirroring (the user, the cover going away, or Android); the next start asks for permission again. */
    fun stop() {
        val wasRunning = projection != null
        if (_state.value == MirrorState.OFF && !wasRunning) return
        _state.value = MirrorState.OFF
        cancelMotion()
        touching.value = false
        if (transitions != null) {
            displays.unregisterDisplayListener(innerScreenListener)
            transitions = null
        }
        virtualDisplay?.release()
        virtualDisplay = null
        displaySize = null
        val ended = projection
        projection = null
        ended?.unregisterCallback(callback)
        ended?.stop()
        if (wasRunning) MirrorService.stop(context)
    }

    /**
     * New sizes for the screen (a rotation) or the cover. A pending start view is applied once both are known; a view
     * already on the cover keeps its preset and its spot.
     */
    private fun updateSizes(content: IntSize? = contentSize.value, cover: IntSize? = coverSize.value) {
        val oldCover = coverSize.value
        val oldContent = contentSize.value
        if (content == oldContent && cover == oldCover && pendingStart == null) return
        if (cover != null) {
            val start = pendingStart
            val screen = content ?: cover
            when {
                start != null -> {
                    pendingStart = null
                    _view.value = startView(start, screen, cover)
                }
                oldCover != null -> {
                    // A glide aimed at the old size would land somewhere else now.
                    cancelMotion()
                    _view.value = _view.value.rescaled(oldContent ?: oldCover, oldCover, screen, cover)
                }
            }
        }
        contentSize.value = content
        coverSize.value = cover
    }

    private inline fun steer(change: (view: MirrorView, content: IntSize, cover: IntSize) -> MirrorView) {
        val cover = coverSize.value ?: return
        val content = contentSize.value ?: cover
        _view.update { change(it, content, cover) }
    }

    /** Runs [block] as the one glide, on frames of its own: it runs outside any composition. */
    private fun glide(block: suspend () -> Unit) {
        motion?.cancel()
        flinging = false
        motion = scope.launch(ChoreographerFrameClock) { block() }
    }

    private fun cancelMotion() {
        motion?.cancel()
        motion = null
        flinging = false
        snapToken++
        snapTarget.value = null
    }

    private fun resizeDisplay(size: IntSize) {
        val display = virtualDisplay ?: return
        if (size == displaySize) return
        displaySize = size
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

        /** How long the view must rest before the virtual display follows it. */
        const val BUFFER_SETTLE_MS = 150L

        const val SNAP_MS = 280

        /** One TalkBack zoom step. */
        const val ZOOM_STEP = 1.5f

        /** Below this a filled view is hardly closer than the whole screen. */
        const val MIN_FILL_GAIN = 1.05f

        const val MIN_DPI = 72
        val DEFAULT_COVER_SIZE = IntSize(748, 720)
    }
}

/** Frames for the controller's glides, which run outside any composition: the main thread's vsync. */
private object ChoreographerFrameClock : MonotonicFrameClock {
    override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R =
        suspendCancellableCoroutine { continuation ->
            val choreographer = Choreographer.getInstance()
            val callback = Choreographer.FrameCallback { nanos -> continuation.resumeWith(runCatching { onFrame(nanos) }) }
            choreographer.postFrameCallback(callback)
            continuation.invokeOnCancellation { choreographer.removeFrameCallback(callback) }
        }
}
