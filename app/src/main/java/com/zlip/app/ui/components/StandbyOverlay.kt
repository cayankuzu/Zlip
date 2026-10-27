package com.zlip.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zlip.app.R
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * Inner screen while the cover is on and the user is idle: pure black (OLED pixels off) with a faint
 * mark that hops to a new spot every minute so nothing burns in. Any tap wakes the app.
 */
@Composable
fun StandbyOverlay(onWake: () -> Unit) {
    var spot by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            spot = (spot + 1) % SPOTS.size
        }
    }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onWake),
        contentAlignment = Alignment.Center,
    ) {
        val (fx, fy) = SPOTS[spot]
        val hintMaxWidth = hintWidth(maxWidth, fx)
        Column(
            modifier = Modifier
                .offset(x = maxWidth * fx, y = maxHeight * fy)
                .alpha(0.32f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(painterResource(R.drawable.brand_mark), contentDescription = null, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(8.dp))
            // Wraps within the room this spot leaves, so the only line saying how to wake up never runs off-screen.
            Text(
                text = stringResource(R.string.standby_hint),
                color = Color.White,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = hintMaxWidth),
            )
        }
    }
}

/** Offsets from the center as fractions of the screen. */
private val SPOTS = listOf(0f to 0f, -0.2f to -0.25f, 0.18f to 0.2f, -0.15f to 0.28f, 0.2f to -0.22f)

/** Kept clear of the screen's edges on both sides. */
private val HINT_MARGIN = 12.dp

/**
 * The widest the hint may be at a spot [offsetFraction] of the [screenWidth] off the centre: a centred
 * line that stays [HINT_MARGIN] inside both edges. Pure, so it is unit-tested.
 */
internal fun hintWidth(screenWidth: Dp, offsetFraction: Float): Dp =
    (screenWidth * (1f - 2f * abs(offsetFraction)) - HINT_MARGIN * 2).coerceAtLeast(0.dp)
