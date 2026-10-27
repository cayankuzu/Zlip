package com.zlip.app.cover

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionLifecycleTest {

    private class Host : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    @Test
    fun followingCatchesUpAndStopsWithTheHost() {
        val host = Host().apply { registry.currentState = Lifecycle.State.RESUMED }
        val session = SessionLifecycle(host.lifecycle) { true }
        session.follow()
        assertEquals(Lifecycle.State.RESUMED, session.lifecycle.currentState)

        // The cover goes dark: the content must stop, so its clip stops playing.
        host.registry.currentState = Lifecycle.State.CREATED
        assertEquals(Lifecycle.State.CREATED, session.lifecycle.currentState)

        host.registry.currentState = Lifecycle.State.RESUMED
        assertEquals(Lifecycle.State.RESUMED, session.lifecycle.currentState)
    }

    @Test
    fun aReplacedSessionStopsButNeverStartsAgain() {
        var current = true
        val host = Host().apply { registry.currentState = Lifecycle.State.RESUMED }
        val session = SessionLifecycle(host.lifecycle) { current }
        session.follow()

        current = false
        host.registry.currentState = Lifecycle.State.CREATED
        assertEquals(Lifecycle.State.CREATED, session.lifecycle.currentState)
        host.registry.currentState = Lifecycle.State.RESUMED
        assertEquals(Lifecycle.State.CREATED, session.lifecycle.currentState)
    }

    @Test
    fun followingTwiceStillStopsWithTheHost() {
        val host = Host().apply { registry.currentState = Lifecycle.State.RESUMED }
        val session = SessionLifecycle(host.lifecycle) { true }
        session.follow()
        session.follow()

        host.registry.currentState = Lifecycle.State.CREATED
        assertEquals(Lifecycle.State.CREATED, session.lifecycle.currentState)
    }

    @Test
    fun unfollowingStopsTheContent() {
        val host = Host().apply { registry.currentState = Lifecycle.State.RESUMED }
        val session = SessionLifecycle(host.lifecycle) { true }
        session.follow()

        session.unfollow()
        assertEquals(Lifecycle.State.CREATED, session.lifecycle.currentState)
        // No longer following: the host starting again does not start a session that is gone.
        host.registry.currentState = Lifecycle.State.CREATED
        host.registry.currentState = Lifecycle.State.RESUMED
        assertEquals(Lifecycle.State.CREATED, session.lifecycle.currentState)
    }
}
