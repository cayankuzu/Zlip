package com.zlip.app.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntSize

/**
 * How sheets, dialogs and menus take part in the inner-screen standby. They are windows of their own, so
 * their touches never reach the activity: [wake] counts each one as interaction. They also take the input
 * focus, which on its own holds the standby off; [onOpenChange] says when one of ours has that focus, so the
 * standby may still run under a sheet left open (but not while the shade or another window is in front of it).
 */
class StandbyHooks(val wake: () -> Unit, private val onOpenChange: (Boolean) -> Unit) {
    private var open = 0

    internal fun opened() {
        if (open++ == 0) onOpenChange(true)
    }

    internal fun closed() {
        // Never below zero: one stray call must not leave the next real sheet uncounted.
        if (open > 0 && --open == 0) onOpenChange(false)
    }
}

/** Provided by ZlipRoot; null in previews and anywhere without a standby. */
val LocalStandbyHooks = staticCompositionLocalOf<StandbyHooks?> { null }

/** For the content of a sheet, dialog or menu: see [StandbyHooks]. */
fun Modifier.wakesStandby(): Modifier = this then WakesStandbyElement

private data object WakesStandbyElement : ModifierNodeElement<WakesStandbyNode>() {
    override fun create() = WakesStandbyNode()

    override fun update(node: WakesStandbyNode) = Unit
}

private class WakesStandbyNode :
    Modifier.Node(),
    CompositionLocalConsumerModifierNode,
    ObserverModifierNode,
    PointerInputModifierNode {
    private var hooks: StandbyHooks? = null

    // Whether this window is counted as open right now; keeps opened()/closed() in pairs.
    private var counted = false

    override fun onAttach() = followFocus()

    override fun onObservedReadsChanged() = followFocus()

    /**
     * Counts only while this window (inside a sheet or menu, LocalWindowInfo is its own) has the input focus:
     * with the shade or another window in front, touches go there and never reach us, so the standby must not
     * run. Focus coming back means the user is back, which starts the standby wait again.
     */
    private fun followFocus() {
        var focused = false
        observeReads {
            hooks = currentValueOf(LocalStandbyHooks)
            focused = currentValueOf(LocalWindowInfo).isWindowFocused
        }
        val hooks = hooks ?: return
        if (focused == counted) return
        counted = focused
        if (focused) {
            hooks.opened()
            hooks.wake()
        } else {
            hooks.closed()
        }
    }

    override fun onDetach() {
        if (counted) hooks?.closed()
        counted = false
        hooks = null
    }

    override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
        // Seen on the way down, before a button inside consumes it.
        if (pass == PointerEventPass.Initial && pointerEvent.changes.any { it.changedToDownIgnoreConsumed() }) hooks?.wake?.invoke()
    }

    override fun onCancelPointerInput() = Unit
}
