package com.zlip.app.render

import com.zlip.app.data.GroupTiming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GroupStepsTest {

    private val at = GroupSteps::At

    @Test
    fun nextGoesThroughThePassAndLeavesAtTheEndUnlessItWraps() {
        assertEquals(at(0, 1), GroupSteps.next(at(0, 0), size = 3, wrap = false))
        assertEquals(at(0, 2), GroupSteps.next(at(0, 1), size = 3, wrap = false))
        // The last step: the playlist moves on, or a group alone starts a new pass.
        assertNull(GroupSteps.next(at(0, 2), size = 3, wrap = false))
        assertEquals(at(1, 0), GroupSteps.next(at(0, 2), size = 3, wrap = true))
    }

    @Test
    fun previousAtTheVeryFirstStepLeavesUnlessItWraps() {
        assertEquals(at(0, 0), GroupSteps.previous(at(0, 1), size = 3, wrap = false))
        assertNull(GroupSteps.previous(at(0, 0), size = 3, wrap = false))
        assertEquals(at(0, 2), GroupSteps.previous(at(0, 0), size = 3, wrap = true))
    }

    @Test
    fun previousAtStepOneOfALaterPassShowsTheStepJustSeen() {
        // Wrapping or not, it goes back to the last step of the pass before, never to another pass's last step.
        assertEquals(at(1, 4), GroupSteps.previous(at(2, 0), size = 5, wrap = false))
        assertEquals(at(1, 4), GroupSteps.previous(at(2, 0), size = 5, wrap = true))
    }

    @Test
    fun aRepeatingShuffledGroupStepsBackToTheSameItem() {
        val size = 5
        val seed = 42L
        val lastOfPassOne = GroupTiming.playOrder(size, shuffle = true, seed = seed, pass = 0).last()
        val wrapped = GroupSteps.next(at(0, size - 1), size, wrap = true)!!
        val back = GroupSteps.previous(wrapped, size, wrap = true)!!
        assertEquals(lastOfPassOne, GroupTiming.playOrder(size, shuffle = true, seed = seed, pass = back.pass)[back.index])
    }

    @Test
    fun aShrunkGroupStillMovesFromAValidStep() {
        // A live edit removed steps under the current position: it counts from the last one left.
        assertNull(GroupSteps.next(at(0, 7), size = 3, wrap = false))
        assertEquals(at(0, 1), GroupSteps.previous(at(0, 7), size = 3, wrap = false))
        assertNull(GroupSteps.next(at(0, 0), size = 0, wrap = true))
        assertNull(GroupSteps.previous(at(0, 0), size = 0, wrap = true))
    }

    @Test
    fun showItemLandsOnThatItemInThisPassesOrder() {
        val size = 6
        val seed = 7L
        val pass = 3
        val order = GroupTiming.playOrder(size, shuffle = true, seed = seed, pass = pass)
        for (itemIndex in 0 until size) {
            val to = GroupSteps.show(itemIndex, order, pass)!!
            assertEquals(pass, to.pass)
            assertEquals(itemIndex, order[to.index])
        }
        assertNull(GroupSteps.show(size, order, pass))
        assertNull(GroupSteps.show(-1, order, pass))
    }

    @Test
    fun aShuffledPassCountsOneTwoThreeInPlayOrder() {
        val size = 5
        val order = GroupTiming.playOrder(size, shuffle = true, seed = 3L, pass = 0)
        val positions = mutableListOf<Int>()
        var step: GroupSteps.At? = at(0, 0)
        while (step != null) {
            // What the group reports: its position in play order, 1-based, whatever item that is.
            positions += step.index + 1
            step = GroupSteps.next(step, size, wrap = false)
        }
        assertEquals(listOf(1, 2, 3, 4, 5), positions)
        assertEquals(order.sorted(), (0 until size).toList())
    }
}
