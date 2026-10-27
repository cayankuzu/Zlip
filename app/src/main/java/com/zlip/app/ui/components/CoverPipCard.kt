package com.zlip.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ScreenShare
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zlip.app.R
import com.zlip.app.cover.CoverStatus
import com.zlip.app.data.CoverItem
import com.zlip.app.data.CoverSettings
import com.zlip.app.data.showsQr
import com.zlip.app.render.CoverContent
import com.zlip.app.render.LedStyle
import com.zlip.app.render.Playback
import com.zlip.app.render.ledMatrix
import com.zlip.app.ui.theme.BrandMint

/**
 * Zlip as its small picture-in-picture window, the cover's toggle while other apps are in front: a still of what
 * the cover shows (a mirroring sign while mirroring) and one status line. Static on purpose, nothing decodes in
 * it; its buttons are the window's own actions.
 */
@Composable
fun CoverPipCard(item: CoverItem?, settings: CoverSettings, status: CoverStatus, paused: Boolean, mirroring: Boolean) {
    val coverOn = status == CoverStatus.ACTIVE
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (coverOn && mirroring) {
            Icon(
                Icons.AutoMirrored.Filled.ScreenShare,
                contentDescription = null,
                tint = BrandMint,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxSize(0.34f),
            )
        } else {
            val led = LedStyle.of(settings).takeUnless { item?.showsQr() == true }
            // In cover units: a big sign or the empty-cover note shrinks with the window instead of spilling out.
            CoverScale { CoverContent(item = item, modifier = Modifier.fillMaxSize().ledMatrix(led), playback = Playback.STILL) }
            // Off: the picture steps back, so the state reads at a glance.
            if (!coverOn) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f)),
                )
            }
        }
        val (label, color) = when {
            coverOn && mirroring -> R.string.cover_mirroring to BrandMint
            coverOn && paused -> R.string.cover_paused to Color(0xFFFFC861)
            coverOn -> R.string.pip_status_on to BrandMint
            else -> R.string.pip_status_off to Color.White.copy(alpha = 0.6f)
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
                .padding(start = 10.dp, end = 10.dp, top = 14.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(color),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(label),
                color = Color.White,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
