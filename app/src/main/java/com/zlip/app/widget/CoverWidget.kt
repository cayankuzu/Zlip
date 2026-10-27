package com.zlip.app.widget

import android.app.ActivityOptions
import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.zlip.app.CoverActivity
import com.zlip.app.MainActivity
import com.zlip.app.ZlipApp
import com.zlip.app.R
import com.zlip.app.data.AdvanceMode
import com.zlip.app.data.CountdownMath
import com.zlip.app.data.CoverItem
import com.zlip.app.data.CoverSettings
import com.zlip.app.data.GroupTiming
import com.zlip.app.data.leaves
import com.zlip.app.data.showsQr
import com.zlip.app.render.CountdownView
import com.zlip.app.render.LedStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.random.Random

/**
 * The cover-screen widget (Samsung Flex Window): a slideshow of the playlist drawn by the system itself,
 * so it keeps running when the phone is folded and Zlip is not running. Frames are pre-rendered PNGs
 * served by [CoverImageProvider]; the host advances them with an AdapterViewFlipper, costing Zlip
 * nothing. Tapping it opens the live view (the screen saver) on the cover at the selected item: the host
 * takes only one click target for the whole widget, so it cannot tell which frame was showing.
 */
class CoverWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        // Also sent after a reboot/unlock, so the widget always comes back with fresh frames.
        refresh(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        refresh(context)
    }

    override fun onDisabled(context: Context) {
        // The last widget is gone: its frames are of no use to anyone.
        refresh(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        // A countdown on the widget reached its next day (or hour): draw the new number.
        if (intent.action == CoverWidgets.ACTION_REFRESH) refresh(context) else super.onReceive(context, intent)
    }

    /** Keeps the process alive until the frames are pushed (rendering can take a moment after a reboot). */
    private fun refresh(context: Context) {
        val pending = goAsync()
        CoverWidgets.requestUpdate(context) { pending.finish() }
    }
}

/** What a widget shows besides its frames; other settings (brightness, power mode...) do not affect it. */
internal data class WidgetConfig(
    val advanceMode: AdvanceMode,
    val advanceSeconds: Int,
    val shuffle: Boolean,
    val repeat: Boolean,
    val clock: Boolean,
    val led: LedStyle?,
) {
    companion object {
        fun of(settings: CoverSettings) = WidgetConfig(
            settings.advanceMode,
            settings.advanceSeconds,
            settings.shuffle,
            settings.repeat,
            settings.coverClock,
            LedStyle.of(settings),
        )
    }
}

/**
 * With auto-advance off a chosen group still plays its steps: at about its own [stepSeconds], stopping on the last one
 * when it does not [repeat]. [shuffle] is part of what was pushed, so switching it in the editor pushes again.
 */
internal data class GroupPace(val stepSeconds: Int, val repeat: Boolean, val shuffle: Boolean)

internal object CoverWidgets {

    /** One update at a time: frames are rendered and pruned in the same directory. */
    private val mutex = Mutex()

    /** What was pushed last (guarded by [mutex]), so unchanged content is not sent to the host again. */
    private var lastPush: List<Any?>? = null

    private fun component(context: Context) = ComponentName(context, CoverWidgetProvider::class.java)

    fun hasWidgets(context: Context): Boolean =
        AppWidgetManager.getInstance(context).getAppWidgetIds(component(context)).isNotEmpty()

    /**
     * Rebuilds every placed widget from the current library, even if nothing changed (the host may have
     * lost its views). [onDone] runs once: when the push is done, or after [RECEIVER_BUDGET_MS] so a
     * broadcast receiver is released in time (the update then finishes in the app scope).
     */
    fun requestUpdate(context: Context, onDone: () -> Unit = {}) {
        val app = context.applicationContext as ZlipApp
        val done = AtomicBoolean(false)
        val finishOnce = { if (done.compareAndSet(false, true)) onDone() }
        val budget = app.appScope.launch {
            delay(RECEIVER_BUDGET_MS)
            finishOnce()
        }
        app.appScope.launch {
            try {
                withTimeoutOrNull(LOAD_TIMEOUT_MS) { app.repository.loaded.first { it } } ?: return@launch
                val repository = app.repository
                update(app, repository.currentSequence, repository.currentSelected, WidgetConfig.of(repository.currentSettings), force = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Widget update failed", e)
            } finally {
                budget.cancel()
                finishOnce()
            }
        }
    }

    /**
     * Pushes [sequence] (or, with auto-advance off, only [selected]: the chosen item stays on the cover)
     * to every placed widget. Frames the host no longer needs are deleted only after the new views are
     * pushed, so a widget never points at a missing file.
     */
    suspend fun update(
        context: Context,
        sequence: List<CoverItem>,
        selected: CoverItem?,
        config: WidgetConfig,
        force: Boolean = false,
    ) = mutex.withLock {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(component(context))
        if (ids.isEmpty()) {
            // No widget placed (or the last one removed): keep no frames around.
            withContext(Dispatchers.IO) { CoverFrames.prune(context, emptyList()) }
            scheduleCountdownRefresh(context, emptyList())
            lastPush = null
            return@withLock
        }
        val order = when {
            config.advanceMode == AdvanceMode.OFF -> listOfNotNull(selected)
            // Shuffled the same way for the same items, so an unrelated change does not reorder the widget.
            config.shuffle -> sequence.shuffled(Random(sequence.map { it.id }.sorted().hashCode()))
            else -> sequence
        }
        // With auto-advance off a chosen group still plays its steps, at its own pace.
        val groupPace = (selected as? CoverItem.Group)?.takeIf { config.advanceMode == AdvanceMode.OFF }
            ?.let { GroupPace(it.stepSeconds, it.repeat, it.shuffle) }
        val frames = withContext(Dispatchers.IO) {
            // A group's steps become frames of their own, in its order: the host's flipper plays them one by one.
            order.flatMap { if (it is CoverItem.Group) it.widgetSteps() else listOf(it) }.take(MAX_FRAMES).mapNotNull { item ->
                ensureActive()
                CoverFrames.frameFor(context, item, config.led)?.let { item to it }
            }
        }
        val push = listOf(ids.toList(), config, groupPace, frames.map { (item, file) -> Triple(item.id, item.name, file.name) })
        if (force || push != lastPush) {
            for (id in ids) {
                manager.updateAppWidget(id, buildViews(context, id, frames, config, groupPace))
            }
            lastPush = push
        }
        withContext(Dispatchers.IO) { CoverFrames.prune(context, frames.map { it.second }) }
        scheduleCountdownRefresh(context, frames.map { it.first })
    }

    /**
     * Countdown stills show whole days (hours on the last day), so the widget needs a redraw only when that
     * number changes. A non-waking alarm: it fires the next time the phone is awake anyway, costing nothing.
     */
    private fun scheduleCountdownRefresh(context: Context, shown: List<CoverItem>) {
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val next = shown.flatMap { it.leaves() }
            .filterIsInstance<CoverItem.Countdown>()
            // A reached countdown still changes once more at midnight ("Today!" becomes "Yesterday").
            .map { CountdownMath.nextCoarseChange(it, now, zone) ?: (now + CountdownView.millisToMidnight(now, zone)) }
            .minOrNull()
        val alarms = context.getSystemService(AlarmManager::class.java)
        val refresh = PendingIntent.getBroadcast(
            context,
            REQUEST_REFRESH,
            Intent(context, CoverWidgetProvider::class.java).setAction(ACTION_REFRESH),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (next == null) alarms.cancel(refresh) else alarms.set(AlarmManager.RTC, next + REFRESH_SLACK_MS, refresh)
    }

    private fun buildViews(
        context: Context,
        widgetId: Int,
        frames: List<Pair<CoverItem, File>>,
        config: WidgetConfig,
        groupPace: GroupPace?,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, layoutFor(config, frames.size, groupPace))
        // A clock on each frame flips with it and dims as two frames crossfade; only needed to leave a QR code clear.
        val clockPerFrame = frames.any { (item, _) -> item.showsQr() }
        val items = RemoteViews.RemoteCollectionItems.Builder()
            .setHasStableIds(true)
            .setViewTypeCount(1)
        frames.forEachIndexed { index, (item, file) ->
            val frame = RemoteViews(context.packageName, R.layout.widget_frame).apply {
                setImageViewUri(R.id.widget_image, CoverImageProvider.uri(context, file))
                setContentDescription(R.id.widget_image, item.name)
                // Among QR codes each frame carries its own clock, left off a code (alone or in a collage cell): no
                // corner of the code is free, and the live cover hides its clock there too.
                val frameClock = config.clock && clockPerFrame && !item.showsQr()
                setViewVisibility(R.id.widget_frame_clock, if (frameClock) View.VISIBLE else View.GONE)
            }
            // Position-based ids: unique by construction.
            items.addItem(index.toLong(), frame)
        }
        views.setRemoteAdapter(R.id.widget_flipper, items.build())
        views.setEmptyView(R.id.widget_flipper, R.id.widget_empty)
        // The widget's own clock stays steady over the flipping frames (and the empty library's brand tile).
        views.setViewVisibility(R.id.widget_clock, if (config.clock && !clockPerFrame) View.VISIBLE else View.GONE)

        // Tap anywhere = the screen saver on the cover display itself. One click target on the whole widget,
        // as Samsung's Flex Window guide does: its cover host never delivers per-frame collection clicks.
        val onCover = ActivityOptions.makeBasic().setLaunchDisplayId(COVER_DISPLAY_ID).apply {
            // The widget host sends this on the user's tap; let it start the screen saver on our behalf.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                setPendingIntentCreatorBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                @Suppress("DEPRECATION")
                setPendingIntentCreatorBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            }
        }.toBundle()
        val saver = Intent(context, CoverActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(CoverActivity.EXTRA_FROM_WIDGET, true)
        views.setOnClickPendingIntent(
            R.id.widget_root,
            PendingIntent.getActivity(context, widgetId, saver, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE, onCover),
        )
        // Empty library: open Zlip to add something.
        views.setOnClickPendingIntent(
            R.id.widget_empty,
            PendingIntent.getActivity(
                context,
                REQUEST_OPEN_APP,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        return views
    }

    /**
     * AdapterViewFlipper's autoStart and flipInterval are not callable through RemoteViews, so the
     * interval is chosen by layout. Same view ids in all of them. [groupPace]: auto-advance is off and the
     * chosen item is a group, whose steps play at (about) its own pace, and go round only if it repeats.
     */
    private fun layoutFor(config: WidgetConfig, frameCount: Int, groupPace: GroupPace?): Int {
        if (frameCount < 2) return R.layout.widget_cover_paused
        if (config.advanceMode == AdvanceMode.OFF) {
            if (groupPace == null) return R.layout.widget_cover_paused
            return flippingLayout(FLIP_SECONDS.minBy { abs(it - groupPace.stepSeconds) }, repeat = groupPace.repeat)
        }
        return flippingLayout(config.advanceSeconds, config.repeat)
    }

    /** Loop off: the *_once layouts stop on the last frame, like the live view does. */
    private fun flippingLayout(seconds: Int, repeat: Boolean): Int = when (seconds) {
        5 -> if (repeat) R.layout.widget_cover_5s else R.layout.widget_cover_5s_once
        10 -> if (repeat) R.layout.widget_cover_10s else R.layout.widget_cover_10s_once
        15 -> if (repeat) R.layout.widget_cover_15s else R.layout.widget_cover_15s_once
        30 -> if (repeat) R.layout.widget_cover_30s else R.layout.widget_cover_30s_once
        60 -> if (repeat) R.layout.widget_cover_60s else R.layout.widget_cover_60s_once
        else -> if (repeat) R.layout.widget_cover_300s else R.layout.widget_cover_300s_once
    }

    /**
     * A group's steps in the order the widget shows them: as listed, or shuffled once with a seed of the group's own,
     * so an unrelated push does not reorder them (the host's flipper cannot reshuffle every pass).
     */
    private fun CoverItem.Group.widgetSteps(): List<CoverItem> =
        GroupTiming.playOrder(items.size, shuffle, id.hashCode().toLong(), pass = 0).map { items[it] }

    /** The flip intervals there are layouts for. */
    private val FLIP_SECONDS = listOf(5, 10, 15, 30, 60, 300)

    /**
     * Unique among Zlip's MainActivity PendingIntents: the controls notification uses 0, the tile 1, the small
     * window 4, the always-show notification 10 and the mirroring notification 20.
     */
    private const val REQUEST_OPEN_APP = 2
    private const val REQUEST_REFRESH = 3
    const val ACTION_REFRESH = "com.zlip.app.action.WIDGET_REFRESH"

    /** Draw a moment after the number changed, never just before. */
    private const val REFRESH_SLACK_MS = 2_000L

    /** Samsung documents the Flex Window as display 1 (MAIN_SCREEN_ID = 0, COVER_SCREEN_ID = 1). */
    private const val COVER_DISPLAY_ID = 1

    /** Enough for a long playlist without holding hundreds of files. */
    private const val MAX_FRAMES = 40

    /** How long a broadcast receiver is kept waiting; well under the system's deadline. */
    private const val RECEIVER_BUDGET_MS = 8_000L

    /** Give up on a library that will not load (corrupt storage); the next update tries again. */
    private const val LOAD_TIMEOUT_MS = 20_000L

    private const val TAG = "ZlipWidget"
}
