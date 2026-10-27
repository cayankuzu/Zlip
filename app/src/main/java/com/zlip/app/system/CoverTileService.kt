package com.zlip.app.system

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.zlip.app.MainActivity
import com.zlip.app.ZlipApp
import com.zlip.app.R
import com.zlip.app.cover.CoverStatus
import com.zlip.app.cover.mirror.MirrorState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Quick-settings tile: turns the cover off when it is on (the folded screen saver included), otherwise opens
 * Zlip, starts the cover and goes back to the app the user was in, with Zlip as its small window.
 * Declared as an "active" tile, so the system only binds it when we ask for an update.
 */
class CoverTileService : TileService() {

    private val app: ZlipApp get() = application as ZlipApp
    private var listenJob: Job? = null

    /** What the tile shows; the screen saver counts as on, since it is what the cover plays. */
    private enum class Shown { OFF, UNSUPPORTED, ON, MIRRORING, SCREEN_SAVER }

    override fun onStartListening() {
        // Follow the cover for as long as the panel is open; StateFlows also emit their current values first.
        listenJob?.cancel()
        listenJob = app.appScope.launch {
            combine(app.cover.status, app.playback.liveViewVisible, app.mirror.state) { status, live, mirror ->
                when {
                    live -> Shown.SCREEN_SAVER
                    status == CoverStatus.ACTIVE && mirror == MirrorState.ON -> Shown.MIRRORING
                    status == CoverStatus.ACTIVE -> Shown.ON
                    status == CoverStatus.UNSUPPORTED -> Shown.UNSUPPORTED
                    else -> Shown.OFF
                }
            }.collect { show(it) }
        }
    }

    override fun onStopListening() {
        listenJob?.cancel()
        listenJob = null
    }

    override fun onDestroy() {
        listenJob?.cancel()
        listenJob = null
        super.onDestroy()
    }

    override fun onClick() {
        // Checked before the lock: the screen saver plays over the cover's lock screen, closing it needs no unlock.
        if (app.playback.liveViewVisible.value) {
            app.playback.requestCloseLiveView()
            show(Shown.OFF)
            return
        }
        if (app.cover.status.value == CoverStatus.ACTIVE) {
            app.stopCover()
            // The session ends asynchronously; show the result right away so a second tap does not restart it.
            show(Shown.OFF)
            return
        }
        val launch = {
            val intent = Intent(this, MainActivity::class.java)
                // CLEAR_TOP: reuse the existing Zlip screen even if Settings or the picker sits on top of it.
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_START_COVER, true)
                .putExtra(MainActivity.EXTRA_RETURN_AFTER_START, true)
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
        }
        if (isLocked) unlockAndRun(launch) else launch()
    }

    private fun show(shown: Shown) {
        val tile = qsTile ?: return
        tile.state = when (shown) {
            Shown.ON, Shown.MIRRORING, Shown.SCREEN_SAVER -> Tile.STATE_ACTIVE
            Shown.UNSUPPORTED -> Tile.STATE_UNAVAILABLE
            Shown.OFF -> Tile.STATE_INACTIVE
        }
        tile.subtitle = getString(
            when (shown) {
                Shown.ON -> R.string.tile_on
                Shown.MIRRORING -> R.string.cover_mirroring
                Shown.SCREEN_SAVER -> R.string.tile_screen_saver
                Shown.OFF, Shown.UNSUPPORTED -> R.string.tile_off
            },
        )
        tile.updateTile()
    }

    companion object {
        fun requestUpdate(context: Context) {
            requestListeningState(context, ComponentName(context, CoverTileService::class.java))
        }
    }
}
