package com.zlip.app

import android.app.Application
import android.util.Log
import com.zlip.app.cover.CoverDisplayController
import com.zlip.app.cover.CoverPlayback
import com.zlip.app.cover.CoverSessions
import com.zlip.app.cover.mirror.MirrorController
import com.zlip.app.cover.mirror.MirrorState
import com.zlip.app.data.AdvanceMode
import com.zlip.app.data.ContentRepository
import com.zlip.app.system.CoverGuard
import com.zlip.app.system.CoverNotifier
import com.zlip.app.system.CoverTileService
import com.zlip.app.widget.CoverWidgets
import com.zlip.app.widget.WidgetConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class ZlipApp : Application() {

    /** A failed background save must never take the cover session down with it: log and carry on. */
    val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate +
            CoroutineExceptionHandler { _, e -> Log.e("ZlipApp", "Background task failed", e) },
    )

    lateinit var repository: ContentRepository
        private set

    lateinit var playback: CoverPlayback
        private set

    /** Starts the cover over on every fold and unfold; cover hosts call [CoverSessions.check] as they start. */
    lateinit var sessions: CoverSessions
        private set

    lateinit var cover: CoverDisplayController
        private set

    lateinit var mirror: MirrorController
        private set

    lateinit var notifier: CoverNotifier
        private set

    override fun onCreate() {
        super.onCreate()
        repository = ContentRepository(this, appScope)
        playback = CoverPlayback(repository, appScope)
        sessions = CoverSessions(this, playback)
        mirror = MirrorController(this, repository, appScope)
        // The cover's own ×: the same "off" as Home, the notification and the tile.
        cover = CoverDisplayController(repository, playback, sessions, mirror, appScope) { stopCover(hideMiniWindow = true) }
        // Nothing moves on out of sight while the cover mirrors the inner screen.
        playback.runAutoAdvance(cover.status, mirror.state.map { it == MirrorState.ON })
        mirror.followCover(cover.status)
        notifier = CoverNotifier(this, repository, cover, playback, mirror, appScope)
        CoverGuard.sync(this)
        CoverGuard.rearmIfStopped(this)
        // Keep cover-screen widgets in step with the playlist (debounced; frames are cached by content).
        appScope.launch {
            combine(
                repository.sequence,
                repository.settings.map(WidgetConfig::of).distinctUntilChanged(),
                repository.selectedItem,
            ) { sequence, config, selected ->
                // The selection matters only with auto-advance off; otherwise every advance would re-push.
                Triple(sequence, config, selected.takeIf { config.advanceMode == AdvanceMode.OFF })
            }
                .distinctUntilChanged()
                .drop(1)
                .debounce(WIDGET_DEBOUNCE_MS)
                .collect { (sequence, config, selected) ->
                    // One failed update (storage full, a broken file) must not end widget syncing for good.
                    try {
                        CoverWidgets.update(this@ZlipApp, sequence, selected, config)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w("ZlipApp", "Widget update failed", e)
                    }
                }
        }
        // Keep the quick-settings tile in sync; it is only bound when we ask.
        appScope.launch {
            combine(cover.status, playback.liveViewVisible, mirror.state) { status, live, mirroring -> Triple(status, live, mirroring) }
                .drop(1)
                .collect { CoverTileService.requestUpdate(this@ZlipApp) }
        }
    }

    private val _hideMiniWindow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * The cover was turned off from outside the full-size app (its own ×, the notification, the tile): Zlip's small
     * window, if it floats over another app, goes too, so "off" is complete. A cover that ended by itself (a fold,
     * the screen lock, a timeout) keeps the window as the way back on.
     */
    val hideMiniWindow: SharedFlow<Unit> = _hideMiniWindow.asSharedFlow()

    /**
     * The user turned the cover off (in Zlip, from the cover's ×, a notification, the tile or the small window).
     * [hideMiniWindow]: the request came from outside the full-size app, so the small window closes with it.
     */
    fun stopCover(hideMiniWindow: Boolean = false) {
        mirror.stop()
        cover.stop(byUser = true)
        if (hideMiniWindow) _hideMiniWindow.tryEmit(Unit)
    }
}

private const val WIDGET_DEBOUNCE_MS = 1_500L
