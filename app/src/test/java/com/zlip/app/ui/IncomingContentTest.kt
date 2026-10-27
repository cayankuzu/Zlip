package com.zlip.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IncomingContentTest {

    @Test
    fun aLoneWebLinkBecomesAQrCard() {
        assertEquals(IncomingContent.Link("https://instagram.com/ada"), IncomingContent.fromText("  https://instagram.com/ada\n"))
        assertEquals(IncomingContent.Link("HTTP://example.com/a?b=c"), IncomingContent.fromText("HTTP://example.com/a?b=c"))
    }

    @Test
    fun textAroundALinkStaysText() {
        val shared = "Look at this https://example.com"
        assertEquals(IncomingContent.Text(shared), IncomingContent.fromText(shared))
    }

    @Test
    fun linksWithoutAWebSchemeOrARealHostStayText() {
        assertEquals(IncomingContent.Text("example.com"), IncomingContent.fromText("example.com"))
        assertEquals(IncomingContent.Text("https://nohost"), IncomingContent.fromText("https://nohost"))
    }

    @Test
    fun aLinkTooLongForACodeStaysText() {
        val long = "https://example.com/" + "a".repeat(1_000)
        assertEquals(IncomingContent.Text(long), IncomingContent.fromText(long))
    }

    @Test
    fun blankTextIsNothing() {
        assertNull(IncomingContent.fromText(" \n "))
    }

    @Test
    fun aBigShareKeepsTheFirstFilesAndCountsTheRest() {
        val shared = (1..50).toList()
        assertEquals((1..20).toList() to 30, shared.takeAtMost(20))
    }

    @Test
    fun aShareWithinTheLimitIsKeptWhole() {
        assertEquals(listOf("a", "b") to 0, listOf("a", "b").takeAtMost(20))
        assertEquals(listOf("a", "b") to 0, listOf("a", "b").takeAtMost(2))
    }

    @Test
    fun noRoomKeepsNothing() {
        assertEquals(emptyList<String>() to 3, listOf("a", "b", "c").takeAtMost(0))
        assertEquals(emptyList<String>() to 3, listOf("a", "b", "c").takeAtMost(-1))
    }
}
