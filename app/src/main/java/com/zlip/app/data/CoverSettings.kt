package com.zlip.app.data

import org.json.JSONObject

/** Trades smoothness for battery. [fps] caps the cover's refresh rate and animation frame rate. */
enum class PowerMode(val fps: Int) {
    SAVER(20),
    BALANCED(30),
    SMOOTH(60),
}

/**
 * How the cover moves through the playlist on its own, as one value for code that only needs the gist.
 * Derived from [CoverSettings.autoAdvance] and [CoverSettings.videosToEnd]; never stored.
 */
enum class AdvanceMode {
    /** Stays on the chosen item. */
    OFF,

    /**
     * Stills show for [CoverSettings.advanceSeconds]; every video plays once from start to end, and GIFs,
     * animations and scrolling text finish the pass they are in once that time is up.
     */
    SMART,

    /** Every [CoverSettings.advanceSeconds], whatever is showing (groups still play their whole pass). */
    TIMER,
}

/** How the mirrored inner screen sits on the nearly square cover. */
enum class MirrorFit {
    /** All of it, with black bars (a portrait screen becomes a narrow strip). */
    FIT,

    /** Fills the cover and crops the rest; the hidden part can be panned into view. */
    FILL,
}

data class CoverSettings(
    /** Extra rotation applied to cover content: 0, 90, 180 or 270 degrees. */
    val rotation: Int = 0,
    /** Open the cover screen automatically whenever the app comes to the front. */
    val autoStart: Boolean = true,
    /**
     * Cover brightness, MIN_BRIGHTNESS..1, on top of the system's own (automatic) cover brightness: the
     * cover content is dimmed in software, which works on every firmware and saves power on OLED.
     */
    val brightness: Float = 0.7f,
    val powerMode: PowerMode = PowerMode.BALANCED,
    /** The cover moves through the playlist on its own. */
    val autoAdvance: Boolean = false,
    /**
     * Photo time: how long still content stays (photos, still drawings and text, countdowns, QR codes), and
     * the minimum for GIFs, animations and scrolling text. Videos too when [videosToEnd] is off.
     */
    val advanceSeconds: Int = 10,
    /** Every video plays once from start to end before the cover moves on; off cuts videos at [advanceSeconds]. */
    val videosToEnd: Boolean = true,
    /** Pick the next item at random instead of in order. */
    val shuffle: Boolean = false,
    /** Start over after the last item; when off, auto-advance stops on the last one. */
    val repeat: Boolean = true,
    /**
     * Inner screen goes black and dims to minimum after N idle seconds while the cover is on (it must
     * stay on, or Android ends the cover session); 0 = off.
     */
    val standbySeconds: Int = 30,
    /**
     * With the cover on and Zlip full screen, after this many minutes without a touch on either screen the
     * screen-on hold is released, so the system's own timeout ends the session; 0 = never.
     */
    val autoOffMinutes: Int = 10,
    /** Leaving Zlip shrinks it into a small picture-in-picture window, which keeps the open-phone cover on. */
    val keepCoverInOtherApps: Boolean = true,
    /** How a mirrored inner screen fills the cover when mirroring starts. Fill: a portrait screen is unreadable whole. */
    val mirrorFit: MirrorFit = MirrorFit.FILL,
    /** Cover controls in the notification shade while the cover plays: play/pause/next/off. */
    val notificationControls: Boolean = true,
    /** Show a clock on the folded cover (widget and screen saver). */
    val coverClock: Boolean = true,
    /**
     * Folded screen saver: how long the cover stays lit after the last touch. 0 follows the cover's own
     * timeout; [STAY_ON_ALWAYS] keeps it on, resting as a dimmed still with a clock after a minute.
     * Either way the screen saver is back in front of the lock screen whenever the cover wakes.
     */
    val stayOnSeconds: Int = 300,
    /** Nightstand: while charging, the folded screen saver stays on (resting) whatever [stayOnSeconds] says. */
    val alwaysOnWhileCharging: Boolean = true,
    /**
     * Show the screen saver every time the phone is folded or the cover wakes, even after it was closed.
     * Needs "Appear on top" and a quiet background service; on by default.
     */
    val autoShowOnCover: Boolean = true,
    /**
     * Videos with audio play with sound each time the cover turns on (a fold, an unfold, a cover wake).
     * The cover's sound button, the notification and volume up change it only until the cover starts over.
     */
    val startWithSound: Boolean = true,
    /** LED matrix look: the cover is drawn as glowing dots, like an LED sign. */
    val ledMatrix: Boolean = false,
    /** Dots across the cover in LED mode. */
    val ledDensity: Int = 48,
    /** LED mode color: 0 keeps the content's colors, otherwise every dot glows in this color. */
    val ledTint: Int = 0,
    val onboardingDone: Boolean = false,
    /** The user closed Home's "add the cover widget" tip; it stays away even while no widget is placed. */
    val widgetTipDismissed: Boolean = false,
    /** The user closed Home's "make a playlist" tip; it stays away for good. */
    val playlistTipDismissed: Boolean = false,
) {
    /** The auto-advance settings as one value (see [AdvanceMode]). */
    val advanceMode: AdvanceMode
        get() = when {
            !autoAdvance -> AdvanceMode.OFF
            videosToEnd -> AdvanceMode.SMART
            else -> AdvanceMode.TIMER
        }

    internal fun toJson(): JSONObject = JSONObject().apply {
        put("rotation", rotation)
        put("autoStart", autoStart)
        put("brightness", brightness.toDouble())
        put("powerMode", powerMode.name)
        put("autoAdvance", autoAdvance)
        put("advanceSeconds", advanceSeconds)
        put("videosToEnd", videosToEnd)
        put("shuffle", shuffle)
        put("repeat", repeat)
        put("standbySeconds", standbySeconds)
        put("autoOffMinutes", autoOffMinutes)
        put("keepCoverInOtherApps", keepCoverInOtherApps)
        put("mirrorFit", mirrorFit.name)
        put("notificationControls", notificationControls)
        put("coverClock", coverClock)
        put("stayOnSeconds", stayOnSeconds)
        put("alwaysOnWhileCharging", alwaysOnWhileCharging)
        put("autoShowOnCover", autoShowOnCover)
        put("startWithSound", startWithSound)
        put("ledMatrix", ledMatrix)
        put("ledDensity", ledDensity)
        put("ledTint", ledTint)
        put("onboardingDone", onboardingDone)
        put("widgetTipDismissed", widgetTipDismissed)
        put("playlistTipDismissed", playlistTipDismissed)
    }

    companion object {
        val ROTATIONS = listOf(0, 90, 180, 270)
        val ADVANCE_SECONDS_OPTIONS = listOf(5, 10, 15, 30, 60, 300)
        val STANDBY_OPTIONS = listOf(0, 15, 30, 60, 120)
        val AUTO_OFF_OPTIONS = listOf(5, 10, 30, 0)
        const val STAY_ON_ALWAYS = -1
        val STAY_ON_OPTIONS = listOf(0, 60, 300, 600, 1800, STAY_ON_ALWAYS)
        val LED_DENSITY_OPTIONS = listOf(32, 48, 64)
        const val MIN_BRIGHTNESS = 0.05f

        internal fun fromJson(json: JSONObject?): CoverSettings {
            if (json == null) return CoverSettings()
            val defaults = CoverSettings()
            val rotation = json.optInt("rotation", 0)
            val standby = json.optInt("standbySeconds", defaults.standbySeconds)
            // Version 1 stored a plain slideshow interval; carry it over as a timer.
            val legacySlideshow = json.optInt("slideshowSeconds", 0)
            val advanceSeconds = json.optInt("advanceSeconds", if (legacySlideshow > 0) legacySlideshow else defaults.advanceSeconds)
            // Before the switch and the videos choice, one mode said both: Smart let videos finish, Timer cut them.
            val legacyMode = AdvanceMode.entries.firstOrNull { it.name == json.optString("advanceMode") }
                ?: if (legacySlideshow > 0) AdvanceMode.TIMER else AdvanceMode.OFF
            val autoAdvance = if (json.has("autoAdvance")) json.optBoolean("autoAdvance") else legacyMode != AdvanceMode.OFF
            val videosToEnd = if (json.has("videosToEnd")) json.optBoolean("videosToEnd", true) else legacyMode != AdvanceMode.TIMER
            // The earlier "close the live view after N minutes" setting becomes how long the cover stays lit.
            val legacyTimeout = json.optInt("liveTimeoutMinutes", -2)
            val stayOn = json.optInt(
                "stayOnSeconds",
                when (legacyTimeout) {
                    -2 -> defaults.stayOnSeconds
                    0 -> STAY_ON_ALWAYS
                    else -> (legacyTimeout * 60).coerceAtMost(1800)
                },
            )
            val ledDensity = json.optInt("ledDensity", defaults.ledDensity)
            val autoOff = json.optInt("autoOffMinutes", defaults.autoOffMinutes)
            return CoverSettings(
                rotation = if (rotation in ROTATIONS) rotation else 0,
                autoStart = json.optBoolean("autoStart", true),
                brightness = json.optDouble("brightness", defaults.brightness.toDouble()).toFloat().coerceIn(MIN_BRIGHTNESS, 1f),
                powerMode = PowerMode.entries.firstOrNull { it.name == json.optString("powerMode") } ?: PowerMode.BALANCED,
                autoAdvance = autoAdvance,
                advanceSeconds = if (advanceSeconds in ADVANCE_SECONDS_OPTIONS) advanceSeconds else defaults.advanceSeconds,
                videosToEnd = videosToEnd,
                shuffle = json.optBoolean("shuffle", false),
                repeat = json.optBoolean("repeat", true),
                standbySeconds = if (standby in STANDBY_OPTIONS) standby else defaults.standbySeconds,
                autoOffMinutes = if (autoOff in AUTO_OFF_OPTIONS) autoOff else defaults.autoOffMinutes,
                keepCoverInOtherApps = json.optBoolean("keepCoverInOtherApps", defaults.keepCoverInOtherApps),
                mirrorFit = MirrorFit.entries.firstOrNull { it.name == json.optString("mirrorFit") } ?: defaults.mirrorFit,
                notificationControls = json.optBoolean("notificationControls", true),
                coverClock = json.optBoolean("coverClock", true),
                stayOnSeconds = if (stayOn in STAY_ON_OPTIONS) stayOn else defaults.stayOnSeconds,
                alwaysOnWhileCharging = json.optBoolean("alwaysOnWhileCharging", true),
                autoShowOnCover = json.optBoolean("autoShowOnCover", true),
                // The old "videoSoundDefault" only seeded new imports; the cover now starts with sound unless set.
                startWithSound = json.optBoolean("startWithSound", true),
                ledMatrix = json.optBoolean("ledMatrix", false),
                ledDensity = if (ledDensity in LED_DENSITY_OPTIONS) ledDensity else defaults.ledDensity,
                ledTint = json.optInt("ledTint", 0),
                onboardingDone = json.optBoolean("onboardingDone", false),
                widgetTipDismissed = json.optBoolean("widgetTipDismissed", false),
                playlistTipDismissed = json.optBoolean("playlistTipDismissed", false),
            )
        }
    }
}
