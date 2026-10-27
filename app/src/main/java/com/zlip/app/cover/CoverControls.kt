package com.zlip.app.cover

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import com.zlip.app.R
import java.text.DateFormat
import java.util.Date

/**
 * On-cover controls, shown after a tap (folded) or the unlocking double tap (open phone). Big targets
 * for a small screen: previous / play-pause / next in the middle, sound and close at the edges, and on
 * the folded cover the way into its menu. Kept clear of the cover's camera cutout, if it has one.
 */
@Composable
internal fun CoverControls(
    title: String,
    position: String?,
    paused: Boolean,
    canSkip: Boolean,
    /** The cover's sound for now ([CoverPlayback.soundOn]); null when the item has no sound to switch. */
    soundOn: Boolean?,
    onPrevious: () -> Unit,
    onTogglePause: () -> Unit,
    onNext: () -> Unit,
    onToggleSound: () -> Unit,
    onClose: (() -> Unit)?,
    /** Opens the folded cover's menu; null on the open phone, which has none. */
    onOpenMenu: (() -> Unit)? = null,
    /** The item takes taps itself (a toy): keep the middle free, skip from the edges, no pause. */
    compact: Boolean = false,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .windowInsetsPadding(WindowInsets.displayCutout),
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(start = 18.dp, end = 6.dp, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (position != null) Text(position, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
            }
            if (onClose != null) {
                ControlButton(Icons.Filled.Close, stringResource(R.string.cover_close), onClose, size = 44)
            }
        }

        if (compact) {
            if (canSkip) {
                Box(Modifier.align(Alignment.CenterStart).padding(start = 10.dp)) {
                    ControlButton(Icons.Filled.SkipPrevious, stringResource(R.string.action_previous), onPrevious, size = 44)
                }
                Box(Modifier.align(Alignment.CenterEnd).padding(end = 10.dp)) {
                    ControlButton(Icons.Filled.SkipNext, stringResource(R.string.action_next), onNext, size = 44)
                }
            }
        } else {
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (canSkip) ControlButton(Icons.Filled.SkipPrevious, stringResource(R.string.action_previous), onPrevious, size = 56)
                ControlButton(
                    if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    stringResource(if (paused) R.string.action_resume else R.string.action_pause),
                    onTogglePause,
                    size = 72,
                    filled = true,
                )
                if (canSkip) ControlButton(Icons.Filled.SkipNext, stringResource(R.string.action_next), onNext, size = 56)
            }
        }

        if (soundOn != null) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 14.dp),
            ) {
                ControlButton(
                    if (soundOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                    stringResource(if (soundOn) R.string.action_sound_off else R.string.action_sound_on),
                    onToggleSound,
                    size = 52,
                )
            }
        }

        if (onOpenMenu != null) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 10.dp, bottom = 14.dp),
            ) {
                ControlButton(Icons.Filled.KeyboardArrowUp, stringResource(R.string.cover_menu_open), onOpenMenu, size = 44)
            }
        }
    }
}

@Composable
private fun ControlButton(icon: ImageVector, description: String, onClick: () -> Unit, size: Int, filled: Boolean = false) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(size.dp)
            .background(if (filled) Color.White else Color.Black.copy(alpha = 0.55f), CircleShape),
    ) {
        Icon(icon, contentDescription = description, tint = if (filled) Color.Black else Color.White, modifier = Modifier.size((size * 0.5f).dp))
    }
}

/**
 * Small screen-saver clock (time + date). The time is read again whenever the screen saver starts (the
 * cover woke) and then follows the system's minute ticks and clock or time zone changes, only while it is
 * on screen: a timer would sleep with the phone and show a time from before the cover slept.
 */
@Composable
internal fun CoverClock(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LifecycleStartEffect(context) {
        now = System.currentTimeMillis()
        val ticks = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                now = System.currentTimeMillis()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(context, ticks, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onStopOrDispose { context.unregisterReceiver(ticks) }
    }
    val shadow = Shadow(color = Color.Black.copy(alpha = 0.8f), blurRadius = 12f)
    Column(modifier) {
        Text(
            text = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(now)),
            style = TextStyle(color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Light, shadow = shadow),
        )
        Text(
            text = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(now)),
            style = TextStyle(color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp, shadow = shadow),
        )
    }
}

/** Shown while the cover is locked and paused, so a frozen frame does not look broken. */
@Composable
internal fun PausedBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Pause, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(stringResource(R.string.cover_paused), color = Color.White, fontSize = 11.sp)
    }
}

/**
 * Where the folded cover's menu is, for a moment on a toy (whose taps go to the game). Not for TalkBack,
 * which cannot swipe: it offers the menu as an action instead.
 */
@Composable
internal fun MenuHint(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clearAndSetSemantics {}
            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(50))
            .padding(start = 10.dp, end = 14.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(4.dp))
        Text(stringResource(R.string.cover_menu_hint), color = Color.White, fontSize = 13.sp)
    }
}
