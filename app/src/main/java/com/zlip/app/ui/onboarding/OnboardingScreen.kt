package com.zlip.app.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ScreenShare
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.zlip.app.R
import com.zlip.app.cover.coverHoverSupported
import com.zlip.app.ui.components.GradientButton
import com.zlip.app.ui.components.miniCoverSupported
import com.zlip.app.ui.theme.BrandGradient
import kotlinx.coroutines.launch

internal data class Page(@StringRes val title: Int, @StringRes val body: Int, val icon: ImageVector?)

/**
 * The open cover (and the Mini kapak), mirroring, the folded screen saver, then groups and the gallery. Only
 * what this phone has: mirroring needs Android 15 ([mirror]), the Mini kapak picture-in-picture too ([miniCover]).
 */
internal fun onboardingPages(mirror: Boolean, miniCover: Boolean): List<Page> = buildList {
    add(Page(R.string.intro_welcome_title, if (miniCover) R.string.intro_welcome_body else R.string.intro_welcome_body_no_mini, null))
    if (mirror) add(Page(R.string.intro_mirror_title, R.string.intro_mirror_body, Icons.AutoMirrored.Filled.ScreenShare))
    add(Page(R.string.intro_folded_title, R.string.intro_folded_body, Icons.Filled.Bedtime))
    add(Page(R.string.intro_groups_title, R.string.intro_groups_body, Icons.Filled.ViewCarousel))
}

/** First-run introduction. The last page offers the notification permission for shade controls. */
@Composable
fun OnboardingScreen(
    notificationsAllowed: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val pages = remember(context) { onboardingPages(mirror = coverHoverSupported, miniCover = miniCoverSupported(context)) }
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == pages.lastIndex
    BackHandler(enabled = pager.currentPage > 0) {
        scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (!last) {
                TextButton(onClick = onDone) { Text(stringResource(R.string.intro_skip)) }
            } else {
                Spacer(Modifier.height(48.dp))
            }
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { index ->
            PageContent(pages[index])
        }
        val position = stringResource(R.string.intro_page_position, pager.currentPage + 1, pages.size)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp)
                // One "Page 2 of 4" for TalkBack instead of four unlabeled shapes, announced when Next turns the page.
                .clearAndSetSemantics {
                    contentDescription = position
                    liveRegion = LiveRegionMode.Polite
                },
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(pages.size) { index ->
                val selected = index == pager.currentPage
                val width by animateDpAsState(if (selected) 24.dp else 8.dp, label = "dot")
                val color by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    label = "dotColor",
                )
                Box(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .height(8.dp)
                        .width(width)
                        .clip(RoundedCornerShape(50))
                        .background(color),
                )
            }
        }
        if (last && !notificationsAllowed) {
            OutlinedButton(
                onClick = onRequestNotificationPermission,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) { Text(stringResource(R.string.intro_allow_notifications)) }
            Spacer(Modifier.height(12.dp))
        }
        GradientButton(
            text = stringResource(if (last) R.string.intro_start else R.string.intro_next),
            onClick = {
                if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Centred while it fits; with large fonts or in landscape the art shrinks (at most 30% of the page) and
 * the page scrolls, so no text is ever cut off.
 */
@Composable
private fun PageContent(page: Page) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val art = min(200.dp, maxHeight * 0.3f)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (page.icon == null) {
                Image(
                    painter = painterResource(R.drawable.brand_tile),
                    contentDescription = null,
                    modifier = Modifier.size(art),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(art * 0.8f)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(BrandGradient.map { it.copy(alpha = 0.22f) })),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(page.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(art * 0.38f))
                }
            }
            Spacer(Modifier.height(art * 0.18f))
            Text(
                text = stringResource(page.title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(page.body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
