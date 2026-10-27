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
import com.zlip.app.MainActivity
import com.zlip.app.ZlipApp
import com.zlip.app.R
import com.zlip.app.cover.CoverDisplayController
import com.zlip.app.cover.CoverPlayback
import com.zlip.app.cover.CoverStatus
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
 * Cover controls in the notification shade while the cover plays (open phone or folded live view):
 * pause/resume, next, turn off, plus where in the playlist it is ("2/5") and in a group ("Grup · 2/4"). Swiped away, they stay away until the
 * next session. While the inner screen is mirrored, the mirroring notification is the control instead.
 * Turning the cover on is up to the quick-settings tile and Zlip's small window.
 * Plain notifications only (no foreground service): nothing has to stay alive in the background.
 */
@OptIn(FlowPreview::class)
class CoverNotifier(
    private val context: Context,
    repository: ContentRepository,
    cover: CoverDisplayController,
    playback: CoverPlayback,
    mirror: MirrorController,
    scope: CoroutineScope,
) {
    private sealed interface Model {
        data object Hidden : Model
        data class Playing(
            val title: String,
            val position: String?,
            val paused: Boolean,
            val editing: Boolean,
            /** The cover's sound for now (null: the item has no sound to switch). */
            val sound: Boolean?,
            /** A group on the cover: the step it shows (from 1) and how many it has. */
            val groupStep: Pair<Int, Int>?,
        ) : Model
    }

    /** The item on the cover and where it is, plus the cover's sound for now. */
    private data class Shown(
        val selected: CoverItem?,
        val sequence: List<CoverItem>,
        val step: Pair<String, String>?,
        val soundOn: Boolean,
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
                combine(repository.selectedItem, repository.sequence, playback.groupStep, playback.soundOn, ::Shown),
                repository.draft,
                playback.paused,
                combine(repository.settings, playingDismissed) { settings, dismissed -> settings.notificationControls && !dismissed },
            ) { (status, live, mirroring), (selected, sequence, step, soundOn), draft, paused, wanted ->
                val active = status == CoverStatus.ACTIVE
                when {
                    !wanted -> Model.Hidden
                    // Nothing to pause or skip while mirroring; its own notification stops it.
                    mirroring == MirrorState.ON -> Model.Hidden
                    // The folded live view plays the playlist too, so it gets the same controls.
                    active || live -> {
                        val index = sequence.indexOfFirst { it.id == selected?.id }
                        // Drafts only show on the open-phone cover, never in the live view.
                        val editing = draft != null && !live
                        val group = (selected as? CoverItem.Group)?.takeIf { !editing && step?.first == it.id }
                        val stepIndex = group?.items?.indexOfFirst { it.id == step?.second } ?: -1
                        Model.Playing(
                            // Not the draft's name: that would re-post the notification on every keystroke.
                            title = selected?.name.orEmpty(),
                            position = if (index >= 0 && sequence.size > 1) "${index + 1}/${sequence.size}" else null,
                            paused = paused,
                            editing = editing,
                            sound = notifiedSound(selected, soundOn),
                            groupStep = if (group != null && stepIndex >= 0) stepIndex + 1 to group.items.size else null,
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
                    Model.Hidden -> manager.cancel(NOTIFICATION_ID)
                    is Model.Playing -> post(playingNotification(model))
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
        val title = listOfNotNull(model.title.ifBlank { null }, model.position).joinToString(" · ")
        setContentTitle(context.getString(R.string.notif_title))
        setContentText(
            when {
                model.editing -> context.getString(R.string.notif_text_editing)
                model.paused -> context.getString(R.string.notif_text_paused, title)
                else -> title
            },
        )
        // "Grup · 2/4" in the header while a group plays.
        model.groupStep?.let { (step, count) -> setSubText(context.getString(R.string.notif_group_step, step, count)) }
        setOngoing(true)
        // Android 14+ lets the user swipe even ongoing notifications away.
        setDeleteIntent(action(ACTION_PLAYING_DISMISSED))
        addAction(
            if (model.paused) R.drawable.ic_notif_play else R.drawable.ic_notif_pause,
            context.getString(if (model.paused) R.string.action_resume else R.string.action_pause),
            action(ACTION_TOGGLE),
        )
        // Three buttons fit: a clip with sound (or a group with one) gets the sound switch in place of "next" (a swipe on the cover
        // still skips). "Next" would change a hidden selection while an editor shows its draft on the cover.
        when {
            model.sound != null && !model.editing -> addAction(
                if (model.sound) R.drawable.ic_notif_sound_off else R.drawable.ic_notif_sound_on,
                context.getString(if (model.sound) R.string.action_sound_off else R.string.action_sound_on),
                action(ACTION_SOUND),
            )
            !model.editing -> addAction(R.drawable.ic_notif_next, context.getString(R.string.action_next), action(ACTION_NEXT))
        }
        addAction(R.drawable.ic_notif_close, context.getString(R.string.action_cover_off), action(ACTION_STOP))
    }.build()

    private fun post(notification: android.app.Notification) {
        if (!canPost()) return
        try {
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

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW).apply {
            description = context.getString(R.string.notif_channel_desc)
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "cover_controls"
        private const val NOTIFICATION_ID = 1
        private const val BRAND_COLOR = 0xFF8A9BFF.toInt()
        const val ACTION_TOGGLE = "com.zlip.app.action.TOGGLE"
        const val ACTION_NEXT = "com.zlip.app.action.NEXT"
        const val ACTION_STOP = "com.zlip.app.action.STOP"
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

/**
 * Handles the buttons of the cover-controls and mirroring notifications and of Zlip's small window. Not
 * exported: only our own PendingIntents can reach it.
 */
class CoverActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as ZlipApp
        when (intent.action) {
            CoverNotifier.ACTION_TOGGLE -> app.playback.togglePause()
            CoverNotifier.ACTION_NEXT -> app.playback.next() // ignored while an editor draft is shown
            CoverNotifier.ACTION_PLAYING_DISMISSED -> app.notifier.dismissPlaying()
            CoverNotifier.ACTION_MIRROR_STOP -> app.mirror.stop()
            CoverNotifier.ACTION_MIRROR_VIEW -> app.mirror.toggleView()
            CoverNotifier.ACTION_MEDIA_FIT -> app.playback.switchShownMediaFit()
            // The same switch as the cover's own button: the cover's sound, until it next starts over.
            CoverNotifier.ACTION_SOUND -> if (hasSoundToSwitch(app.repository.selectedItem.value)) app.playback.toggleSound()
            CoverNotifier.ACTION_STOP -> {
                if (app.cover.status.value == CoverStatus.ACTIVE) app.stopCover()
                app.playback.requestCloseLiveView()
            }
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
