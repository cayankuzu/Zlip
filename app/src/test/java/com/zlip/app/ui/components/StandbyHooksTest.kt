package com.zlip.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class StandbyHooksTest {

    private val changes = mutableListOf<Boolean>()
    private val hooks = StandbyHooks(wake = {}, onOpenChange = { changes += it })

    @Test
    fun reportsOpenOnceWhileAnyIsOpen() {
        hooks.opened()
        hooks.opened()
        hooks.closed()
        hooks.closed()
        assertEquals(listOf(true, false), changes)
    }

    @Test
    fun aStrayCloseNeverLeavesTheNextOneUncounted() {
        hooks.closed()
        hooks.opened()
        hooks.closed()
        assertEquals(listOf(true, false), changes)
    }
}
