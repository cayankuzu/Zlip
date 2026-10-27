package com.zlip.app.cover

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display

/**
 * Starts the cover over ([CoverPlayback.startOver]) whenever the inner screen switches between lit and
 * dark: the phone was folded or opened, or an open phone locked or unlocked. Needs no hinge reading. The
 * folded cover sleeping and waking touches only the cover display; the screen saver starts over on its own
 * then. Half-folding and the inner screen's black standby keep it lit, so they do not count.
 *
 * Display events for a cached (frozen) process can arrive late, so every cover host also calls [check] as
 * it starts: it reads the real state, so a fold made while frozen starts the content over before its
 * first frame, and the late event then finds nothing changed.
 */
class CoverSessions(context: Context, private val playback: CoverPlayback) {
    private val displays = context.getSystemService(DisplayManager::class.java)
    private val edge = InnerScreenEdge()

    private val listener = object : DisplayManager.DisplayListener {
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == Display.DEFAULT_DISPLAY) check()
        }

        override fun onDisplayAdded(displayId: Int) = Unit

        override fun onDisplayRemoved(displayId: Int) = Unit
    }

    init {
        // The first reading only sets where the inner screen stands.
        check()
        displays.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
    }

    /**
     * Reads the inner screen's state now and starts the cover over if it switched between lit and dark since
     * the last look. Returns true when it did. Main thread only.
     */
    fun check(): Boolean {
        val state = displays.getDisplay(Display.DEFAULT_DISPLAY)?.state ?: return false
        if (!edge.onState(state)) return false
        playback.startOver()
        return true
    }
}

/** Remembers whether the inner screen is lit and says when a new state is on the other side. Pure, so it is unit-tested. */
internal class InnerScreenEdge(private var lit: Boolean? = null) {

    /** True when [state] (a `Display.STATE_*`) crosses between lit and dark; the first reading never does. */
    fun onState(state: Int): Boolean {
        val now = when (state) {
            Display.STATE_ON, Display.STATE_VR, Display.STATE_ON_SUSPEND -> true
            Display.STATE_OFF, Display.STATE_DOZE, Display.STATE_DOZE_SUSPEND -> false
            // Unknown says nothing either way.
            else -> return false
        }
        val crossed = lit != null && lit != now
        lit = now
        return crossed
    }
}
