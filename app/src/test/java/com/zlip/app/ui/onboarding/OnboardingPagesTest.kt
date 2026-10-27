package com.zlip.app.ui.onboarding

import com.zlip.app.R
import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingPagesTest {

    @Test
    fun everythingWhereTheMiniCoverAndMirroringExist() {
        val pages = onboardingPages(mirror = true, miniCover = true)
        assertEquals(
            listOf(R.string.intro_welcome_title, R.string.intro_mirror_title, R.string.intro_folded_title, R.string.intro_groups_title),
            pages.map { it.title },
        )
        assertEquals(R.string.intro_welcome_body, pages.first().body)
    }

    @Test
    fun android14NeverPromisesMirroringOrTheMiniCover() {
        val pages = onboardingPages(mirror = false, miniCover = false)
        assertEquals(
            listOf(R.string.intro_welcome_title, R.string.intro_folded_title, R.string.intro_groups_title),
            pages.map { it.title },
        )
        assertEquals(R.string.intro_welcome_body_no_mini, pages.first().body)
    }

    @Test
    fun mirroringWithoutPictureInPictureKeepsItsPageButNotTheMiniCover() {
        val pages = onboardingPages(mirror = true, miniCover = false)
        assertEquals(4, pages.size)
        assertEquals(R.string.intro_welcome_body_no_mini, pages.first().body)
    }
}
