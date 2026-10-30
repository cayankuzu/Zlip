package com.zlip.app.system

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.zlip.app.MainActivity
import com.zlip.app.ZlipApp
import com.zlip.app.R
import com.zlip.app.cover.CoverDisplayController
import com.zlip.app.cover.CoverPlayback
import com.zlip.app.cover.CoverStatus
import com.zlip.app.cover.GroupProgress
import com.zlip.app.cover.hasSoundToSwitch
import com.zlip.app.cover.mirror.MirrorController
import com.zlip.app.cover.mirror.MirrorState
import com.zlip.app.data.ContentRepository
import com.zlip.app.data.CoverItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Cover controls in the notification shade while the cover plays (open phone or folded live view), as a media
 * notification with fixed slots ([notificationActions]): ⏮ ⏯ ⏭ always first and shown even when collapsed, then the
 * item's extra (Sonraki içerik for a group, the sound switch for a clip) and × last, away from ⏭. The title is what
 * plays; the text says where ("Adım 2/5 · Listede 3/7"). Swiped away, they stay away until the next session. While the
 * inner screen is mirrored, the mirroring notification is the control instead.
 * Turning the cover on is up to the quick-settings tile and Zlip's small window.
 * Plain notifications only (no foreground service, no media session): nothing has to stay alive in the background.
 */
@OptIn(FlowPreview::class)
class CoverNotifier(
    private val context: Context,
    private val repository: ContentRepository,
    private val cover: CoverDisplayController,
    private val playback: CoverPlayback,
    private val mirror: MirrorController,
    private val scope: CoroutineScope,
) {
    private sealed interface Model {
        data object Hidden : Model
        data class Playing(
            val title: String,
            /** Where the item is in a playlist of more than one: (1-based position, size). */
            val position: Pair<Int, Int>?,
            /** No playlist of the user's: the whole library rotates, so the position is "of all", not "in the list". */
            val wholeLibrary: Boolean,
            val paused: Boolean,
            val editing: Boolean,
            /** The folded screen saver plays it, rather than the open phone's cover. */
            val live: Boolean,
            /** The cover's sound for now (null: the item has no sound to switch). */
            val sound: Boolean?,
            /** A group is on the cover: ⏮ and ⏭ move through its steps. */
            val inGroup: Boolean,
            /** Where that group is, in its play order (so a shuffled group still counts 1, 2, 3). */
            val step: GroupProgress?,
            /** The group can be left for another item: "Sonraki içerik". */
            val itemSkip: Boolean,
        ) : Model
    }

    /** The item on the cover and where it is, plus the cover's sound for now. */
    private data class Shown(
        val selected: CoverItem?,
        val sequence: List<CoverItem>,
        val wholeLibrary: Boolean,
        val progress: GroupProgress?,
        val soundOn: Boolean,
        val itemSkip: Boolean,
    )

    private val manager = NotificationManagerCompat.from(context)

    /** The user swiped the controls away; reposting them on every change would bring them straight back. */
    private val playingDismissed = MutableStateFlow(false)

    init {
        createChannel()
        scope.launch {
            // A new session (open phone or screen saver) brings swiped-away controls back.
            combine(cover.status, playback.liveViewVisible) { status, live -> status == CoverStatus.ACTIVE || live }
                .distinctUntilChanged()
                .collect { playing -> if (playing) playingDismissed.value = false }
        }
        scope.launch {
            combine(
                combine(cover.status, playback.liveViewVisible, mirror.state) { status, live, mirroring -> Triple(status, live, mirroring) },
                combine(
                    repository.selectedItem,
                    combine(repository.sequence, repository.playlist, ::Pair),
                    playback.groupProgress,
                    playback.soundOn,
                    playback.itemSkipAvailable,
                ) { selected, (sequence, playlist), progress, soundOn, itemSkip ->
                    Shown(selected, sequence, wholeLibrary = playlist.isEmpty(), progress, soundOn, itemSkip)
                },
                repository.draft,
                playback.paused,
                combine(repository.settings, playingDismissed) { settings, dismissed -> settings.notificationControls && !dismissed },
            ) { (status, live, mirroring), shown, draft, paused, wanted ->
                val (selected, sequence, wholeLibrary, progress, soundOn, itemSkip) = shown
                val active = status == CoverStatus.ACTIVE
                when {
                    !wanted -> Model.Hidden
                    // Nothing to pause or skip while mirroring; its own notification stops it.
                    mirroring == MirrorState.ON -> Model.Hidden
                    // The folded live view or background service plays the playlist too, so it gets the same controls.
                    active || live || CoverGuardService.running -> {
                        val index = sequence.indexOfFirst { it.id == selected?.id }
                        // Drafts only show on the open-phone cover, never in the live view.
                        val editing = draft != null && !live
                        val inGroup = !editing && selected is CoverItem.Group
                        Model.Playing(
                            // Not the draft's name: that would re-post the notification on every keystroke.
                            title = selected?.name.orEmpty(),
                            position = if (index >= 0 && sequence.size > 1) index + 1 to sequence.size else null,
                            wholeLibrary = wholeLibrary,
                            paused = paused,
                            editing = editing,
                            live = live,
                            sound = notifiedSound(selected, soundOn),
                            inGroup = inGroup,
                            step = progress?.takeIf { inGroup },
                            itemSkip = inGroup && itemSkip,
                        )
                    }
                    else -> Model.Hidden
                }
            }
                .distinctUntilChanged()
                // Status passes through a brief in-between state while sessions start and stop; never
                // cancel and re-post the notification for that.
                .debounce { model -> if (model == Model.Hidden) HIDE_DELAY_MS else 0L }
                .distinctUntilChanged()
                .collect { model ->
                when (model) {
                    Model.Hidden -> {
                        if (!CoverGuardService.running) {
                            manager.cancel(NOTIFICATION_ID)
                        }
                    }
                    is Model.Playing -> post(model)
                }
            }
        }
    }

    /** Whether posting is possible right now (Android 13+ asks the user first). */
    fun canPost(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED &&
            manager.areNotificationsEnabled() &&
            manager.getNotificationChannel(CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE

    /** Where to send the user when the system will no longer show the permission dialog. */
    fun settingsIntent(): Intent {
        val appBlocked = !manager.areNotificationsEnabled() ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        return if (appBlocked) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else {
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL_ID)
        }
    }

    /** The user swiped the controls away: keep them hidden until the cover starts again. */
    fun dismissPlaying() {
        playingDismissed.value = true
    }

    private fun base(): NotificationCompat.Builder = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_stat_zlip)
        .setColor(BRAND_COLOR)
        .setContentIntent(openApp())
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setShowWhen(false)
        .setCategory(NotificationCompat.CATEGORY_STATUS)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

    private fun playingNotification(model: Model.Playing) = base().apply {
        // What plays is the headline; the fixed "cover on" goes to the small header.
        setContentTitle(model.title.ifBlank { context.getString(R.string.notif_title) })
        setContentText(
            if (model.editing) {
                context.getString(R.string.notif_text_editing)
            } else {
                // Labelled parts, so the group's step and the playlist position never read as two bare fractions.
                listOfNotNull(
                    context.getString(R.string.notif_paused).takeIf { model.paused },
                    model.step?.let { context.getString(R.string.cover_group_step, it.step, it.count) },
                    model.position?.let { (index, size) ->
                        // Worded like Home's "Tümü · 3/7" / "Liste · 3/7": there may be no list at all.
                        context.getString(if (model.wholeLibrary) R.string.notif_all_position else R.string.notif_list_position, index, size)
                    },
                ).joinToString(" · ").ifEmpty { null }
            },
        )
        when {
            model.live -> setSubText(context.getString(R.string.tile_screen_saver))
            // Not twice: a nameless item already has "cover on" as its title.
            model.title.isNotBlank() -> setSubText(context.getString(R.string.notif_title))
        }
        val ongoing = !model.paused
        setOngoing(ongoing)
        // Android 14+ lets the user swipe even ongoing notifications away.
        setDeleteIntent(action(ACTION_PLAYING_DISMISSED))
        val buttons = notificationActions(group = model.inGroup, otherItems = model.itemSkip, sound = model.sound != null, editing = model.editing)
        buttons.actions.forEach { button ->
            when (button) {
                NotifAction.PREVIOUS -> addAction(
                    R.drawable.ic_notif_previous,
                    context.getString(if (model.inGroup) R.string.action_previous_step else R.string.action_previous),
                    action(ACTION_PREVIOUS),
                )
                NotifAction.PLAY_PAUSE -> addAction(
                    if (model.paused) R.drawable.ic_notif_play else R.drawable.ic_notif_pause,
                    context.getString(if (model.paused) R.string.action_resume else R.string.action_pause),
                    action(ACTION_TOGGLE),
                )
                NotifAction.NEXT -> addAction(
                    R.drawable.ic_notif_next,
                    context.getString(if (model.inGroup) R.string.action_next_step else R.string.action_next),
                    action(ACTION_NEXT),
                )
                NotifAction.NEXT_ITEM -> addAction(R.drawable.ic_notif_next_item, context.getString(R.string.action_next_item), action(ACTION_NEXT_ITEM))
                NotifAction.SOUND -> addAction(
                    if (model.sound == true) R.drawable.ic_notif_sound_off else R.drawable.ic_notif_sound_on,
                    context.getString(if (model.sound == true) R.string.action_sound_off else R.string.action_sound_on),
                    action(ACTION_SOUND),
                )
                // The same words as the × on the cover it turns off.
                NotifAction.STOP -> addAction(
                    R.drawable.ic_notif_close,
                    context.getString(if (model.live) R.string.cover_close else R.string.action_cover_off),
                    action(ACTION_STOP),
                )
            }
        }
        setStyle(MediaStyle().setShowActionsInCompactView(*buttons.compact.toIntArray()))
    }.build()

    private fun post(model: Model.Playing) {
        if (!canPost()) return
        try {
            val notification = playingNotification(model)
            val service = CoverGuardService.instance
            if (service != null && CoverGuardService.running) {
                service.updateForeground(model.paused, notification)
            }
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the call; nothing to show.
        }
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun action(name: String): PendingIntent = CoverActionReceiver.pendingIntent(context, name)

    fun currentNotification(): android.app.Notification {
        val selected = repository.selectedItem.value
        val sequence = repository.sequence.value
        val playlist = repository.playlist.value
        val progress = playback.groupProgress.value
        val soundOn = playback.soundOn.value
        val itemSkip = playback.itemSkipAvailable.value
        val paused = playback.paused.value
        val live = playback.liveViewVisible.value
        val index = sequence.indexOfFirst { it.id == selected?.id }
        val inGroup = selected is CoverItem.Group
        val model = Model.Playing(
            title = selected?.name.orEmpty(),
            position = if (index >= 0 && sequence.size > 1) index + 1 to sequence.size else null,
            wholeLibrary = playlist.isEmpty(),
            paused = paused,
            editing = false,
            live = live,
            sound = notifiedSound(selected, soundOn),
            inGroup = inGroup,
            step = progress?.takeIf { inGroup },
            itemSkip = inGroup && itemSkip,
        )
        return playingNotification(model)
    }

    fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW).apply {
            description = context.getString(R.string.notif_channel_desc)
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "cover_controls"
        const val NOTIFICATION_ID = 1
        private const val BRAND_COLOR = 0xFF8A9BFF.toInt()
        const val ACTION_TOGGLE = "com.zlip.app.action.TOGGLE"
        const val ACTION_PREVIOUS = "com.zlip.app.action.PREVIOUS"
        const val ACTION_NEXT = "com.zlip.app.action.NEXT"

        /** The next item, leaving a group whatever step it is on. */
        const val ACTION_NEXT_ITEM = "com.zlip.app.action.NEXT_ITEM"

        /** Turns the cover off from outside Zlip (the notification): the small window, if one floats, closes too. */
        const val ACTION_STOP = "com.zlip.app.action.STOP"

        /** The small window's own "turn cover off": the window stays, dimmed, as the way back on. */
        const val ACTION_STOP_KEEP_WINDOW = "com.zlip.app.action.STOP_KEEP_WINDOW"
        const val ACTION_SOUND = "com.zlip.app.action.SOUND"
        const val ACTION_PLAYING_DISMISSED = "com.zlip.app.action.PLAYING_DISMISSED"
        const val ACTION_MIRROR_STOP = "com.zlip.app.action.MIRROR_STOP"

        /** The small window's Whole screen / Fill switch while mirroring, as a tap on the cover. */
        const val ACTION_MIRROR_VIEW = "com.zlip.app.action.MIRROR_VIEW"

        /** The small window's Fit / Fill for the photo or video on the cover, as the cover's own button. */
        const val ACTION_MEDIA_FIT = "com.zlip.app.action.MEDIA_FIT"

        private const val HIDE_DELAY_MS = 300L
    }
}

/**
 * The sound the notification shows for [selected]: the cover's sound for now ([soundOn]), exactly as the
 * cover's own controls show it; null when [selected] has no sound to switch (see [hasSoundToSwitch]).
 */
internal fun notifiedSound(selected: CoverItem?, soundOn: Boolean): Boolean? = soundOn.takeIf { hasSoundToSwitch(selected) }

/** A button of the cover-controls notification. */
internal enum class NotifAction { PREVIOUS, PLAY_PAUSE, NEXT, NEXT_ITEM, SOUND, STOP }

/** The notification's buttons in order, and the positions of those its collapsed view shows. */
internal data class NotifActions(val actions: List<NotifAction>, val compact: List<Int>)

/**
 * The notification's buttons: ⏮ ⏯ ⏭ in fixed slots, also when collapsed, so they never move or change meaning between
 * items. The expanded view adds one extra, "Sonraki içerik" for a [group] with [otherItems] or else the sound switch
 * for an item with [sound], and × always last, outside the collapsed view. While [editing] (a draft on the cover) a
 * move would change a hidden selection: only ⏯ and ×. Pure, so it is unit-tested.
 */
internal fun notificationActions(group: Boolean, otherItems: Boolean, sound: Boolean, editing: Boolean): NotifActions {
    if (editing) return NotifActions(listOf(NotifAction.PLAY_PAUSE, NotifAction.STOP), compact = listOf(0))
    val extra = when {
        group && otherItems -> NotifAction.NEXT_ITEM
        sound -> NotifAction.SOUND
        else -> null
    }
    val actions = listOfNotNull(NotifAction.PREVIOUS, NotifAction.PLAY_PAUSE, NotifAction.NEXT, extra, NotifAction.STOP)
    return NotifActions(actions, compact = listOf(0, 1, 2))
}

/**
 * Handles the buttons of the cover-controls and mirroring notifications and of Zlip's small window. Not
 * exported: only our own PendingIntents can reach it.
 */
class CoverActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as ZlipApp
        when (intent.action) {
            CoverNotifier.ACTION_TOGGLE -> app.playback.togglePause()
            // Moves are ignored while an editor draft is shown.
            CoverNotifier.ACTION_PREVIOUS -> app.playback.previous()
            CoverNotifier.ACTION_NEXT -> app.playback.next()
            CoverNotifier.ACTION_NEXT_ITEM -> app.playback.nextItem()
            CoverNotifier.ACTION_PLAYING_DISMISSED -> {
                // Swiped away while paused (like YouTube Music / Spotify): turn off cover and stop service!
                app.stopCover(hideMiniWindow = true)
                app.playback.requestCloseLiveView()
                val service = Intent(context, CoverGuardService::class.java).setAction(CoverGuardService.ACTION_STOP)
                context.startService(service)
            }
            CoverNotifier.ACTION_MIRROR_STOP -> app.mirror.stop()
            CoverNotifier.ACTION_MIRROR_VIEW -> app.mirror.toggleView()
            CoverNotifier.ACTION_MEDIA_FIT -> app.playback.switchShownMediaFit()
            // The same switch as the cover's own button: the cover's sound, until it next starts over.
            CoverNotifier.ACTION_SOUND -> if (hasSoundToSwitch(app.repository.selectedItem.value)) app.playback.toggleSound()
            CoverNotifier.ACTION_STOP -> {
                if (app.cover.status.value == CoverStatus.ACTIVE) app.stopCover(hideMiniWindow = true)
                app.playback.requestCloseLiveView()
                val service = Intent(context, CoverGuardService::class.java).setAction(CoverGuardService.ACTION_STOP)
                context.startService(service)
            }
            CoverNotifier.ACTION_STOP_KEEP_WINDOW -> if (app.cover.status.value == CoverStatus.ACTIVE) app.stopCover()
        }
    }

    companion object {
        /** A broadcast to this receiver for [action], for notification and picture-in-picture buttons. */
        fun pendingIntent(context: Context, action: String): PendingIntent = PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            Intent(context, CoverActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
