package com.zlip.app.ui.collage

import com.zlip.app.data.CollageLayout
import org.junit.Assert.assertEquals
import org.junit.Test

class CollageCellsTest {

    private val most = CollageLayout.entries.maxOf { it.cells.size }

    @Test
    fun cellsAlwaysHaveRoomForTheBiggestLayout() {
        val padded = withRoomForEveryLayout(listOf(listOf("a"), listOf("b")))
        assertEquals(most, padded.size)
        assertEquals(listOf(listOf("a"), listOf("b")), padded.take(2))
        assertEquals(List(most - 2) { emptyList<String>() }, padded.drop(2))
    }

    @Test
    fun picksAreNeverCut() {
        val full = List(most) { listOf("x$it") }
        assertEquals(full, withRoomForEveryLayout(full))
        assertEquals(most + 1, withRoomForEveryLayout(full + listOf(listOf("extra"))).size)
    }

    @Test
    fun anUnnamedCollageSaysHowManyCellsItFills() {
        val format = { type: String, count: Int -> "$type · $count" }
        assertEquals("Kolaj · 3", defaultCollageName("Kolaj", 3, format))
        assertEquals("Kolaj", defaultCollageName("Kolaj", 0, format))
    }
}
