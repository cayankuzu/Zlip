package com.zlip.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.zlip.app.cover.CoverScreen
import com.zlip.app.data.CoverItem
import com.zlip.app.data.CoverSettings
import com.zlip.app.system.CoverGuard
import com.zlip.app.ui.theme.ZlipTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.launch

/**
 * The folded cover's screen saver: plays the playlist in front of the cover lock screen, with swipes to
 * browse, a tap for controls, a swipe up for the menu (pick an item, brightness, stay-lit time...), sound,
 * and a swipe down back to the lock screen; no unlocking needed.
 *
 * It is never finished when the cover sleeps: as the top showWhenLocked activity of the cover display it
 * comes back in front of the lock screen every time the cover wakes, and every wake starts it from the
 * beginning. The cover stays lit for the user's chosen time (resting as a dimmed still with a clock in
 * always-on mode), then sleeps normally. Opening the phone closes it at once, so the main screen is never
 * affected, and during a call it steps aside for the call screen.
 */
class CoverActivity : ComponentActivity() {

    private val app: ZlipApp get() = application as ZlipApp
    private val displays by lazy { getSystemService(DisplayManager::class.java) }

    /** Uptime of the last touch (or wake); the stay-on timer counts from here. */
    private val lastTouch = MutableStateFlow(SystemClock.uptimeMillis())

    /**
     * Uptime of the last deliberate use (a touch, a TalkBack action, a widget tap). Unlike [lastTouch], a
     * wake on its own or a key pressed in a bag never sets it, so only this lets a clip with sound hold the
     * cover ([StayLit]).
     */
    private val touchedAt = MutableStateFlow(0L)

    /** Uptime the cover last lit up with the screen saver on it (each start); not lit yet: never. */
    private var visibleSince = Long.MAX_VALUE
    private val resting = MutableStateFlow(false)
    private val charging = MutableStateFlow(false)
    private var registered = false
    private val accessibility by lazy { getSystemService(AccessibilityManager::class.java) }

    /**
     * A clip heard on the cover. Resting pauses it, which is neither the end of watching nor a reason to wake
     * again, so resting keeps the value it had.
     */
    private val heard by lazy {
        combine(app.playback.playingWithSound, resting) { sound, rest -> sound to rest }
            .scan(false) { last, (sound, rest) -> if (rest) last else sound }
            .distinctUntilChanged()
    }

    /** The first start lights the cover for the full period; later wakes only get what is left of it. */
    private var startedOnce = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            charging.value = intent.isCharging()
        }
    }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayChanged(displayId: Int) {
            // The inner screen lit up: the phone was opened. Step aside so it behaves exactly as usual.
            if (displayId == Display.DEFAULT_DISPLAY && displays.getDisplay(displayId)?.state == Display.STATE_ON) leave()
        }

        override fun onDisplayAdded(displayId: Int) = Unit

        override fun onDisplayRemoved(displayId: Int) = Unit
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // Only ever on the cover; started or moved anywhere else (the phone is open) it just goes away.
        if (!onCover()) {
            leave()
            return
        }
        alive = true
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        // Volume keys change media volume here (videos with sound), not the ringer.
        volumeControlStream = AudioManager.STREAM_MUSIC
        // A fold whose display event has not arrived yet (a frozen process) starts the cover over before the
        // first frame. Either way a new screen saver (the fold, the widget, the always-show service) plays
        // from the beginning and never inherits a pause.
        val startedOver = app.sessions.check()
        if (savedInstanceState == null) {
            showRequestedItem(intent)
            if (!startedOver) app.playback.startOver()
        }
        displays.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // The notification's "turn off" closes the screen saver too.
                launch { app.playback.closeLiveView.collect { close() } }
                launch { keepAwake() }
                // Nothing advances under a resting screen saver.
                launch { resting.collect { app.playback.setLiveViewResting(it) } }
                // The end of a watched clip counts as a touch: the stay-lit time starts from there. Only within
                // the watch limit, so a playlist of clips cannot keep extending it.
                launch {
                    heard.drop(1).filter { !it }.collect {
                        val now = SystemClock.uptimeMillis()
                        if (StayLit.endCountsAsTouch(now, touchedAt.value, visibleSince)) lastTouch.value = now
                    }
                }
                // Switched in the cover menu: start or stop the always-show service now, while it may be started.
                launch {
                    app.repository.settings.map { it.autoShowOnCover }.distinctUntilChanged().drop(1)
                        .collect { CoverGuard.sync(this@CoverActivity) }
                }
            }
        }

        setContent {
            val settings by app.repository.settings.collectAsState()
            val isResting by resting.collectAsState()
            ZlipTheme {
                CoverScreen(
                    repository = app.repository,
                    playback = app.playback,
                    lockTouches = false,
                    onClose = ::close,
                    onDismiss = ::toLockScreen,
                    ambient = isResting,
                    showClock = settings.coverClock,
                    onUserActivity = ::used,
                )
            }
        }
        countAccessibilityActivity()
    }

    /**
     * TalkBack swipes and double-taps send no touch events, only accessibility events: count clicks (toys
     * included) and, while awake, focus moves as use, so the cover does not rest under a TalkBack user. A focus
     * move while resting is ignored: resting itself closes the menu, and the refocus it causes must not wake it.
     */
    private fun countAccessibilityActivity() {
        findViewById<View>(android.R.id.content).accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onRequestSendAccessibilityEvent(host: ViewGroup, child: View, event: AccessibilityEvent): Boolean {
                when (event.eventType) {
                    AccessibilityEvent.TYPE_VIEW_CLICKED -> used()
                    AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED -> if (!resting.value) used()
                }
                return super.onRequestSendAccessibilityEvent(host, child, event)
            }
        }
    }

    /**
     * Holds the cover on for the chosen time after each touch. Always-on (or charging with the nightstand
     * option) never lets go but rests after that time (at least a minute): dimmed, still, with the clock;
     * charging never makes the time shorter. Otherwise the flag is dropped and the cover sleeps on its own
     * timeout; the screen saver is back on the next wake. A clip heard after the user touched the cover in
     * this look counts as being watched, up to a limit ([StayLit]).
     */
    private suspend fun keepAwake() {
        combine(lastTouch, app.repository.settings, charging, heard, touchedAt) { touch, settings, isCharging, isHeard, touched ->
            val always = settings.stayOnSeconds == CoverSettings.STAY_ON_ALWAYS || (isCharging && settings.alwaysOnWhileCharging)
            // "Cover default" and "always" count as a minute here.
            val awakeMs = if (always) maxOf(settings.stayOnSeconds * 1_000L, REST_AFTER_MS) else settings.stayOnSeconds * 1_000L
            StayAwake(
                since = touch,
                awakeMs = awakeMs,
                rests = always,
                watching = StayLit.watching(isHeard, touched, visibleSince),
                touchedAt = touched,
            )
        }.distinctUntilChanged().collectLatest { awake ->
            resting.value = false
            if (awake.awakeMs <= 0) {
                keepScreenOn(false)
                return@collectLatest
            }
            keepScreenOn(true)
            // A watched clip holds the cover longer, but never without limit: a loop never ends by itself.
            val until = StayLit.deadline(awake.since, awake.awakeMs, awake.watching, awake.touchedAt)
            delay((until - SystemClock.uptimeMillis()).coerceAtLeast(0))
            if (awake.rests) resting.value = true else keepScreenOn(false)
        }
    }

    /** Only a real change: every window flag update relayouts the window (a call to the window manager). */
    private fun keepScreenOn(on: Boolean) {
        val isOn = window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
        if (on == isOn) return
        if (on) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // A widget tap is the user asking for it; the always-show service re-arming a dark cover is not.
        if (intent.openedByUser()) {
            used()
            app.playback.resume()
        }
        showRequestedItem(intent)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        // The first touch only wakes a resting screen saver; it must not also skip or pause.
        if (resting.value) {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) used()
            return true
        }
        // A touch's start and end are enough for the stay-lit time; each move would run it again.
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> used()
        }
        return super.dispatchTouchEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        // TalkBack's explore by touch arrives as hover events, never as touches: a finger landing counts.
        if (event.actionMasked == MotionEvent.ACTION_HOVER_ENTER && accessibility.isTouchExplorationEnabled) used()
        return super.dispatchGenericMotionEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // Volume up on a muted clip that has sound plays it with sound, like most video apps; only for this
        // viewing, so a press meant for something else does not make the clip loud from then on.
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP && event.repeatCount == 0) {
            (app.playback.shownItem.value as? CoverItem.Media)?.let(app.playback::soundOnForNow)
        }
        wake()
        return super.onKeyDown(keyCode, event)
    }

    /**
     * A request for a particular item ([EXTRA_ITEM_ID]) starts the screen saver there. The widget's tap
     * carries none (its host only takes one click target for the whole widget): it opens at the selection.
     */
    private fun showRequestedItem(intent: Intent?) {
        intent?.getStringExtra(EXTRA_ITEM_ID)?.let { app.repository.select(it) }
    }

    private fun wake() {
        resting.value = false
        lastTouch.value = SystemClock.uptimeMillis()
    }

    /**
     * Deliberate use of the cover: a touch, a TalkBack action or a click in the menu or controls, a widget
     * tap. Wakes it, restarts the stay-lit time, and lets a clip with sound hold the cover from now on.
     */
    private fun used() {
        touchedAt.value = SystemClock.uptimeMillis()
        wake()
    }

    override fun onStart() {
        super.onStart()
        if (!onCover() || innerScreenOn()) {
            leave()
            return
        }
        // Never over a call: its screen must stay on top. With "show on every wake" the next cover sleep
        // after the call brings the screen saver back.
        if (CoverGuard.audioMode(this) != AudioManager.MODE_NORMAL) {
            toLockScreen()
            return
        }
        // A fold or unfold whose display event is late starts over here at the latest.
        val startedOver = app.sessions.check()
        inBack = false
        // A new look: a clip with sound holds the cover only once the user touches it again.
        visibleSince = SystemClock.uptimeMillis()
        // Seen on the cover: the one moment a stopped always-show service (a lost update broadcast) can
        // be started again without the user opening Zlip.
        if (display?.state == Display.STATE_ON) CoverGuard.sync(this)
        app.playback.liveViewStarted()
        registered = true
        // The sticky broadcast answers at once with the current charging state.
        ContextCompat.registerReceiver(this, batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
            ?.let { charging.value = it.isCharging() }
        // Opening it starts a stay-on period. A later wake on its own (a notification, the fold, a key in a
        // bag) does not: the timer still counts from the last touch, so the cover follows its own timeout
        // unless the user touches it. Always-on and the nightstand rest at once instead.
        if (!startedOnce) {
            startedOnce = true
            // Opened from the widget: a tap on the cover, so the user is looking.
            if (intent.openedByUser()) used() else wake()
        } else {
            // Every wake is a fresh look: the content plays from its beginning, not from where the cover slept.
            // (A touch that only wakes a resting screen saver is the same look, so it does not start over.)
            if (!startedOver) app.playback.startOver()
            resting.value = false
        }
    }

    override fun onStop() {
        if (registered) {
            registered = false
            app.playback.setLiveViewResting(false)
            app.playback.liveViewStopped()
            unregisterReceiver(batteryReceiver)
        }
        super.onStop()
        // The cover going dark only pauses the screen saver; opening the phone ends it.
        if (innerScreenOn()) leave()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!onCover()) leave()
    }

    override fun onDestroy() {
        displays.unregisterDisplayListener(displayListener)
        if (isFinishing) alive = false
        super.onDestroy()
    }

    /** Swipe down: back to the cover's lock screen. The screen saver stays ready for the widget. */
    private fun toLockScreen() {
        inBack = true
        moveTaskToBack(true)
    }

    /** The close button: the screen saver is done until it is opened again. */
    private fun close() {
        alive = false
        finishAndRemoveTask()
    }

    /** Leaves without a trace, never over the main screen's lock screen. */
    private fun leave() {
        setShowWhenLocked(false)
        alive = false
        if (!isFinishing) finishAndRemoveTask()
        overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
    }

    private fun onCover(): Boolean = (display?.displayId ?: Display.DEFAULT_DISPLAY) != Display.DEFAULT_DISPLAY

    private fun innerScreenOn(): Boolean = displays.getDisplay(Display.DEFAULT_DISPLAY)?.state == Display.STATE_ON

    companion object {
        const val EXTRA_ITEM_ID = "com.zlip.app.extra.ITEM_ID"

        /** Set by the cover widget's tap: the user asked for the screen saver. */
        const val EXTRA_FROM_WIDGET = "com.zlip.app.extra.FROM_WIDGET"

        /** After this long without a touch an always-on screen saver rests (dimmed still, clock). */
        private const val REST_AFTER_MS = 60_000L

        /** True while a screen saver instance exists (it may be behind the lock screen, or asleep). */
        @Volatile
        var alive: Boolean = false
            private set

        /** The user swiped it away to the lock screen; it still exists, behind. */
        @Volatile
        private var inBack: Boolean = false

        /** Whether the always-show service should (re)open it on the dark cover. */
        val needsArming: Boolean get() = !alive || inBack
    }
}

/**
 * What keeps the screen saver lit: since when, for how long, whether it rests then, a clip being watched,
 * and the last deliberate touch (which bounds the watching).
 */
private data class StayAwake(val since: Long, val awakeMs: Long, val rests: Boolean, val watching: Boolean, val touchedAt: Long)

/**
 * How a clip with sound keeps the folded cover lit. Pure, so it is unit-tested; all times are uptime.
 *
 * It counts as watched only if the user touched the cover since it last lit up: a wake on its own (a
 * notification in a pocket) or a volume key pressed in a bag never holds it. Even then the hold ends
 * [WATCH_CAP_MS] after that touch (plus the stay-lit time), so a looping clip or a playlist of clips cannot
 * keep the cover lit forever.
 */
internal object StayLit {
    const val WATCH_CAP_MS = 10 * 60_000L

    /** A clip that is [heard] counts as watched when the last deliberate touch came after the cover lit up. */
    fun watching(heard: Boolean, touchedAt: Long, visibleSince: Long): Boolean = heard && touchedAt >= visibleSince

    /** When the stay-lit time is up: [awakeMs] after [since]; while [watching], no sooner than the watch limit. */
    fun deadline(since: Long, awakeMs: Long, watching: Boolean, touchedAt: Long): Long =
        if (watching) maxOf(since, touchedAt + WATCH_CAP_MS) + awakeMs else since + awakeMs

    /** A watched clip going quiet at [now] restarts the stay-lit time like a touch, but only within the limit. */
    fun endCountsAsTouch(now: Long, touchedAt: Long, visibleSince: Long): Boolean =
        touchedAt >= visibleSince && now < touchedAt + WATCH_CAP_MS
}

/** A widget tap (or a request for a particular item), as opposed to the always-show service re-arming it. */
private fun Intent.openedByUser(): Boolean = getBooleanExtra(CoverActivity.EXTRA_FROM_WIDGET, false) || hasExtra(CoverActivity.EXTRA_ITEM_ID)

private fun Intent.isCharging(): Boolean {
    val status = getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
}
