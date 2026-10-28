package com.zlip.app.system

import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Display
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.zlip.app.CoverActivity
import com.zlip.app.MainActivity
import com.zlip.app.ZlipApp
import com.zlip.app.R
import com.zlip.app.widget.CoverWidgets
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Opens the screen saver on the cover display, and keeps the optional "always show" service in step. */
object CoverGuard {
    private const val TAG = "ZlipGuard"

    fun hasCoverDisplay(context: Context): Boolean {
        val displays = context.getSystemService(DisplayManager::class.java)
        return displays.getDisplay(COVER_DISPLAY_ID) != null ||
            displays.displays.any { it.displayId != Display.DEFAULT_DISPLAY && it.flags and Display.FLAG_PRIVATE == 0 }
    }

    /** Samsung documents the Flex Window as display 1; fall back to any other real display (Xiaomi Mix Flip, Motorola Razr, etc.). */
    fun coverDisplayId(context: Context): Int {
        val displays = context.getSystemService(DisplayManager::class.java)
        if (displays.getDisplay(COVER_DISPLAY_ID) != null) return COVER_DISPLAY_ID
        return displays.displays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY && it.flags and Display.FLAG_PRIVATE == 0 }?.displayId
            ?: COVER_DISPLAY_ID
    }

    /**
     * Remembers each display's power state and says when one has just gone dark. Display listeners fire
     * for many changes (brightness, refresh rate...), so acting on "is dark" rather than "just went dark"
     * would re-open a screen saver the user had just closed.
     */
    class DarkTransitions(private val displays: DisplayManager) {
        private val lastState = HashMap<Int, Int>()

        init {
            displays.displays.forEach { lastState[it.displayId] = it.state }
        }

        /** True once per transition of [displayId] from lit to dark. */
        fun justWentDark(displayId: Int): Boolean {
            val state = displays.getDisplay(displayId)?.state ?: return false
            val before = lastState.put(displayId, state)
            return isDark(state) && before != null && !isDark(before)
        }

        private fun isDark(state: Int) = state == Display.STATE_OFF || state == Display.STATE_DOZE || state == Display.STATE_DOZE_SUSPEND
    }

    /**
     * Whether the screen saver may be put on the cover: only while no call rings or goes on ([audioMode] is
     * [AudioManager.MODE_NORMAL]; the cover's call screen must stay on top), only when it is not there
     * already ([needsArming]), and only on a [folded] phone. Pure, so it is unit-tested.
     */
    fun shouldArm(audioMode: Int, needsArming: Boolean, folded: Boolean): Boolean =
        audioMode == AudioManager.MODE_NORMAL && needsArming && folded

    /** The phone's audio mode: ringing, in a call or in a voice chat, or normal. Needs no permission. */
    fun audioMode(context: Context): Int = context.getSystemService(AudioManager::class.java).mode

    /** Quick pre-check: the inner screen is dark (folded, or an open phone that locked). */
    fun innerScreenDark(context: Context): Boolean =
        context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)?.state != Display.STATE_ON

    private fun coverScreenDark(context: Context): Boolean =
        context.getSystemService(DisplayManager::class.java).getDisplay(coverDisplayId(context))?.state != Display.STATE_ON

    /**
     * Runs [action] only if the phone is really folded shut. The inner screen also goes dark when an open
     * phone locks, and the screen saver must not start behind it then. Reads the hinge once (an on-change
     * sensor reports its current angle when registered) and lets go of it right away.
     */
    fun ifFolded(context: Context, action: () -> Unit) {
        val sensors = context.getSystemService(SensorManager::class.java)
        val hinge = sensors.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE) ?: run {
            if (hasCoverDisplay(context) && innerScreenDark(context)) action()
            return
        }
        val handler = Handler(Looper.getMainLooper())
        val listener = object : SensorEventListener {
            var done = false

            override fun onSensorChanged(event: SensorEvent) {
                if (done) return
                done = true
                sensors.unregisterListener(this)
                if (event.values.firstOrNull()?.let { it < FOLDED_MAX_DEGREES } == true) action()
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        sensors.registerListener(listener, hinge, SensorManager.SENSOR_DELAY_NORMAL, handler)
        handler.postDelayed({
            if (!listener.done) {
                listener.done = true
                sensors.unregisterListener(listener)
                if (hasCoverDisplay(context) && innerScreenDark(context)) action()
            }
        }, 300L)
    }

    /**
     * Starts the screen saver on the cover. From the background this only works with "Appear on top"
     * (or right after the app was on screen); a refusal is logged, never thrown.
     */
    fun showOnCover(context: Context, itemId: String? = null): Boolean {
        val intent = Intent(context, CoverActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .apply { if (itemId != null) putExtra(CoverActivity.EXTRA_ITEM_ID, itemId) }
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(coverDisplayId(context))
        return try {
            context.startActivity(intent, options.toBundle())
            true
        } catch (e: Throwable) {
            Log.w(TAG, "Could not open the screen saver on the cover", e)
            false
        }
    }

    fun canAppearOnTop(context: Context): Boolean = Settings.canDrawOverlays(context)

    /** The system page where the user allows "Appear on top" for Zlip. */
    fun appearOnTopSettings(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.fromParts("package", context.packageName, null))

    /**
     * Runs the always-show service exactly when the user wants it and it can work. Call while Zlip is
     * on screen (a foreground service may only be started from there) or from the boot receiver.
     */
    fun sync(context: Context) {
        val app = context.applicationContext as ZlipApp
        // Until the library is loaded the settings are only defaults ("off"): deciding now would stop the
        // service the user turned on. That happens right after an update, when Android restarts Zlip at once.
        if (!app.repository.loaded.value) {
            app.appScope.launch {
                app.repository.loaded.first { it }
                sync(app)
            }
            return
        }
        val wanted = app.repository.currentSettings.autoShowOnCover && canAppearOnTop(context)
        val service = Intent(context, CoverGuardService::class.java)
        if (wanted) {
            if (CoverGuardService.running) return
            try {
                ContextCompat.startForegroundService(context, service)
            } catch (e: IllegalStateException) {
                // Started from the background after all (ForegroundServiceStartNotAllowedException); retry when visible.
                Log.w(TAG, "Could not start the cover guard", e)
            }
        } else {
            context.stopService(service)
        }
    }

    /**
     * The update broadcast that restarts the always-show service is not always delivered: on One UI 9 a
     * second force-stop during the install cancels it about every other time, and nothing else may start a
     * foreground service from the background. So whenever Zlip starts in the background (the cover widget
     * redraws after an update, for one) with the service wanted but not running, and the phone lies folded
     * with both screens dark, put the screen saver on the cover directly; "Appear on top" allows that. The
     * cover shows it on the next wake, and from there it starts the service again.
     */
    fun rearmIfStopped(context: Context) {
        val app = context.applicationContext as ZlipApp
        app.appScope.launch {
            app.repository.loaded.first { it }
            if (CoverGuardService.running || !app.repository.currentSettings.autoShowOnCover || !canAppearOnTop(app)) return@launch
            if (!innerScreenDark(app) || !coverScreenDark(app) || !CoverActivity.needsArming) return@launch
            ifFolded(app) {
                if (!CoverGuardService.running && shouldArm(audioMode(app), CoverActivity.needsArming, folded = true) && coverScreenDark(app)) {
                    showOnCover(app)
                }
            }
        }
    }

    const val COVER_DISPLAY_ID = 1

    /** Hinge angles below this count as closed (0 = shut, 180 = flat open). */
    internal const val FOLDED_MAX_DEGREES = 30f
    private const val HINGE_TIMEOUT_MS = 1_000L
}

/**
 * "Always show on the cover": keeps the screen saver armed while the phone is folded. When the inner screen
 * goes dark (the phone was folded) or the cover goes dark (it slept, maybe after the user closed or swiped
 * the screen saver away), it re-opens the screen saver on the dark cover, so it is in front of the lock
 * screen the moment the cover wakes. Closing it therefore lasts until the cover next sleeps. Never during
 * a call, whose screen must stay on top.
 *
 * It listens for display changes, with no polling or wake locks. Only an open phone whose screen went dark
 * (locked, or timed out on a desk) is followed further: it can be folded later without any display change,
 * so until its screen lights again the hinge sensor is watched (on-change, quiet while the phone lies still).
 */
class CoverGuardService : Service() {

    private val displays by lazy { getSystemService(DisplayManager::class.java) }
    private val sensors by lazy { getSystemService(SensorManager::class.java) }
    private val transitions by lazy { CoverGuard.DarkTransitions(displays) }

    /** Follows the hinge of an open phone whose screen went dark; null while there is nothing to wait for. */
    private var hingeWatch: SensorEventListener? = null

    private val listener = object : DisplayManager.DisplayListener {
        override fun onDisplayChanged(displayId: Int) {
            val wentDark = transitions.justWentDark(displayId)
            // The phone is in use again: no fold to wait for.
            if (displayId == Display.DEFAULT_DISPLAY && !CoverGuard.innerScreenDark(this@CoverGuardService)) stopHingeWatch()
            // Only the moment a screen goes dark counts: the fold, or the cover falling asleep.
            if (!wentDark) return
            // Never on top of an open phone.
            if (!CoverGuard.innerScreenDark(this@CoverGuardService) || !CoverActivity.needsArming) return
            if (displayId == Display.DEFAULT_DISPLAY) {
                // Folded just now, or (an open phone that locked) maybe later: the hinge tells either way.
                watchHinge()
            } else {
                CoverGuard.ifFolded(this@CoverGuardService) { arm(folded = true) }
            }
        }

        override fun onDisplayAdded(displayId: Int) = Unit

        override fun onDisplayRemoved(displayId: Int) = Unit
    }

    private fun arm(folded: Boolean) {
        if (CoverGuard.shouldArm(CoverGuard.audioMode(this), CoverActivity.needsArming, folded)) CoverGuard.showOnCover(this)
    }

    /** Arms as soon as the hinge closes; the sensor reports its angle when registered, then each change. */
    private fun watchHinge() {
        if (hingeWatch != null) return
        val hinge = sensors.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE) ?: run {
            if (CoverGuard.hasCoverDisplay(this) && CoverGuard.innerScreenDark(this)) arm(folded = true)
            return
        }
        val handler = Handler(Looper.getMainLooper())
        var done = false
        val watch = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (done) return
                val folded = event.values.firstOrNull()?.let { it < CoverGuard.FOLDED_MAX_DEGREES } ?: return
                if (!folded) return
                done = true
                stopHingeWatch()
                arm(folded)
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        hingeWatch = watch
        sensors.registerListener(watch, hinge, SensorManager.SENSOR_DELAY_NORMAL, handler)
        handler.postDelayed({
            if (!done && hingeWatch != null) {
                done = true
                stopHingeWatch()
                if (CoverGuard.hasCoverDisplay(this) && CoverGuard.innerScreenDark(this)) arm(folded = true)
            }
        }, 300L)
    }

    private fun stopHingeWatch() {
        hingeWatch?.let(sensors::unregisterListener)
        hingeWatch = null
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        try {
            startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } catch (e: Throwable) {
            // ForegroundServiceStartNotAllowedException / SecurityException: the next visit to Zlip (or a reboot) starts it again.
            Log.w("ZlipGuard", "Cannot run in the foreground now", e)
            stopSelf()
            return
        }
        running = true
        transitions // capture the current states before the first change arrives
        displays.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            // "Turn off" in the notification: the user no longer wants it, so remember that.
            (application as ZlipApp).repository.updateSettings { it.copy(autoShowOnCover = false) }
            stopSelf()
            return START_NOT_STICKY
        }
        if (!running || !CoverGuard.canAppearOnTop(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        if (running) {
            running = false
            displays.unregisterDisplayListener(listener)
            stopHingeWatch()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_stat_zlip)
        .setContentTitle(getString(R.string.guard_notif_title))
        .setContentText(getString(R.string.guard_notif_text))
        .setOngoing(true)
        .setSilent(true)
        .setShowWhen(false)
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                REQUEST_OPEN,
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(MainActivity.EXTRA_OPEN_SETTINGS, true),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        .addAction(
            R.drawable.ic_notif_close,
            getString(R.string.action_turn_off),
            PendingIntent.getService(
                this,
                REQUEST_STOP,
                Intent(this, CoverGuardService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        .build()

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.guard_channel), NotificationManager.IMPORTANCE_MIN).apply {
            description = getString(R.string.guard_channel_desc)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        /** False until the service runs in the foreground, and when Android refused that (it then quits). */
        @Volatile
        var running = false
            private set

        private const val CHANNEL_ID = "cover_guard"
        private const val NOTIFICATION_ID = 2
        private const val REQUEST_OPEN = 10
        private const val REQUEST_STOP = 11
        private const val ACTION_STOP = "com.zlip.app.action.GUARD_STOP"
    }
}

/**
 * After a reboot or an app update: brings the always-show service back if the user had it on, and redraws
 * the cover widget at once (an update can change what its views do, and a stale widget would not open
 * the screen saver). A clock or time zone change redraws it too, since countdowns count calendar days;
 * these broadcasts may also start the service, which helps when an update broadcast was lost.
 */
class CoverBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED) return
        val app = context.applicationContext as ZlipApp
        val pending = goAsync()
        app.appScope.launch {
            try {
                // Started inside the update/boot broadcast, which is what lets a foreground service start
                // from the background; give the library time to load first.
                withTimeoutOrNull(BOOT_LOAD_TIMEOUT_MS) { app.repository.loaded.first { it } }
                CoverGuard.sync(app)
                CoverWidgets.requestUpdate(app)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        /** Well within a background broadcast's deadline; loading normally takes a few milliseconds. */
        const val BOOT_LOAD_TIMEOUT_MS = 20_000L

        val HANDLED = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
