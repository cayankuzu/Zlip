package com.zlip.app.ui.onboarding

import com.zlip.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class OnboardingPagesTest {

    @Test
    fun everythingWhereTheMiniCoverAndMirroringExist() {
        val pages = onboardingPages(mirror = true, miniCover = true)
        assertEquals(
            listOf(
                R.string.intro_welcome_title,
                R.string.intro_cover_title,
                R.string.intro_mirror_title,
                R.string.intro_folded_title,
                R.string.intro_groups_title,
            ),
            pages.map { it.title },
        )
        assertEquals(R.string.intro_welcome_body, pages.first().body)
        assertNotNull("the welcome page has its Mini kapak row", pages.first().rows)
    }

    @Test
    fun android14NeverPromisesMirroringOrTheMiniCover() {
        val pages = onboardingPages(mirror = false, miniCover = false)
        assertEquals(
            listOf(R.string.intro_welcome_title, R.string.intro_cover_title, R.string.intro_folded_title, R.string.intro_groups_title),
            pages.map { it.title },
        )
        assertEquals(R.string.intro_welcome_body_no_mini, pages.first().body)
        assertNull(pages.first().rows)
    }

    @Test
    fun mirroringWithoutPictureInPictureKeepsItsPageButNotTheMiniCover() {
        val pages = onboardingPages(mirror = true, miniCover = false)
        assertEquals(5, pages.size)
        assertEquals(R.string.intro_welcome_body_no_mini, pages.first().body)
    }

    @Test
    fun onlyTheCoverPageAsksForNotifications() {
        for (mirror in listOf(true, false)) {
            val pages = onboardingPages(mirror = mirror, miniCover = mirror)
            assertEquals(listOf(R.string.intro_cover_title), pages.filter { it.asksNotifications }.map { it.title })
        }
    }

    @Test
    fun everyPageAfterTheWelcomeTeachesWithRows() {
        val pages = onboardingPages(mirror = true, miniCover = false)
        assertEquals(emptyList<Int>(), pages.drop(1).filter { it.rows == null }.map { it.title })
    }
}
