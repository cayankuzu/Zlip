package com.zlip.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import com.zlip.app.R

/**
 * Warns, without blocking anything, that the idle cutoff is about to turn the cover off: counts the last
 * [seconds] down (null hides it). Any touch keeps the cover on, a tap on the chip included, which is also how
 * TalkBack users answer the announcement.
 */
@Composable
fun AutoOffChip(seconds: Int?, onKeepOn: () -> Unit, modifier: Modifier = Modifier) {
    // The last count stays on the chip while it fades out.
    var last by remember { mutableIntStateOf(0) }
    if (seconds != null) SideEffect { last = seconds }
    val announcement = stringResource(R.string.auto_off_announce)
    AnimatedVisibility(
        visible = seconds != null,
        modifier = modifier,
        enter = fadeIn() + slideInVertically(),
        exit = fadeOut(),
    ) {
        Surface(
            onClick = onKeepOn,
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            shadowElevation = 6.dp,
            // TalkBack announces a live region on every change, so it must not hold the ticking count (that read
            // out fifteen times in a row): it speaks one fixed sentence when the chip appears.
            modifier = Modifier.clearAndSetSemantics {
                contentDescription = announcement
                liveRegion = LiveRegionMode.Polite
                role = Role.Button
                onClick {
                    onKeepOn()
                    true
                }
            },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Timer, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.auto_off_warning, seconds ?: last), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
