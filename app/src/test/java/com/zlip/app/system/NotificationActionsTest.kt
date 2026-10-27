package com.zlip.app.system

import com.zlip.app.system.NotifAction.NEXT
import com.zlip.app.system.NotifAction.NEXT_ITEM
import com.zlip.app.system.NotifAction.PLAY_PAUSE
import com.zlip.app.system.NotifAction.PREVIOUS
import com.zlip.app.system.NotifAction.SOUND
import com.zlip.app.system.NotifAction.STOP
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationActionsTest {

    @Test
    fun theTrioKeepsItsSlotsForEveryKindOfItem() {
        // A photo, a clip with sound, a group with other items, a group alone: the same first three, all collapsed.
        for ((group, otherItems, sound) in listOf(
            Triple(false, false, false),
            Triple(false, false, true),
            Triple(true, true, true),
            Triple(true, false, false),
        )) {
            val buttons = notificationActions(group, otherItems, sound, editing = false)
            assertEquals(listOf(PREVIOUS, PLAY_PAUSE, NEXT), buttons.actions.take(3))
            assertEquals(listOf(0, 1, 2), buttons.compact)
        }
    }

    @Test
    fun aGroupWithOtherItemsOffersTheNextItemBeforeClose() {
        assertEquals(
            listOf(PREVIOUS, PLAY_PAUSE, NEXT, NEXT_ITEM, STOP),
            notificationActions(group = true, otherItems = true, sound = true, editing = false).actions,
        )
    }

    @Test
    fun aClipWithSoundOffersTheSoundSwitchBeforeClose() {
        assertEquals(
            listOf(PREVIOUS, PLAY_PAUSE, NEXT, SOUND, STOP),
            notificationActions(group = false, otherItems = false, sound = true, editing = false).actions,
        )
        // A group alone has nowhere else to go: its sound takes the free slot.
        assertEquals(
            listOf(PREVIOUS, PLAY_PAUSE, NEXT, SOUND, STOP),
            notificationActions(group = true, otherItems = false, sound = true, editing = false).actions,
        )
    }

    @Test
    fun closeIsAlwaysLastAndOnlyExpanded() {
        val plain = notificationActions(group = false, otherItems = false, sound = false, editing = false).actions
        assertEquals(listOf(PREVIOUS, PLAY_PAUSE, NEXT, STOP), plain)
        for (group in listOf(false, true)) {
            for (sound in listOf(false, true)) {
                val actions = notificationActions(group, otherItems = true, sound, editing = false).actions
                assertEquals(STOP, actions.last())
            }
        }
    }

    @Test
    fun editingOffersNoMoves() {
        val buttons = notificationActions(group = true, otherItems = true, sound = true, editing = true)
        assertEquals(listOf(PLAY_PAUSE, STOP), buttons.actions)
        assertEquals(listOf(0), buttons.compact)
    }
}
