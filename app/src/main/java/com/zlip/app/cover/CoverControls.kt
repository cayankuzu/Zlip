package com.zlip.app.cover

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Pinch
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.ZoomOutMap
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import com.zlip.app.R
import com.zlip.app.data.MediaFit
import java.text.DateFormat
import java.util.Date

/**
 * On-cover controls, shown after a tap (folded) or the unlocking double tap (open phone). Big targets
 * for a small screen: previous / play-pause / next in the middle, sound and close at the edges, Fit / Fill
 * for a photo or video, and on the folded cover the labelled way into its menu. Kept clear of the cover's camera
 * cutout, if it has one, and of the screen-saver clock's corner (bottom left).
 *
 * A [group] on the cover makes the middle buttons steps: chevrons in place of the skip icons, "Adım 2/5" under the
 * title, and a stories-style bar along the top for up to [MAX_SEGMENTS] steps. With [itemSkip] two labelled pills
 * under them leave the group for the previous or next item, so getting past a long group takes one press.
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
    /** What the × does, for TalkBack: turn the cover off (open phone) or close the screen saver (folded). */
    closeLabel: String,
    /** How the photo or video on the cover is fitted; null when it shows something else. */
    mediaFit: MediaFit? = null,
    /** Switches it to the other fit, not zoomed and centred (also the way back from any zoom). */
    onSwitchFit: () -> Unit = {},
    /** Opens the folded cover's menu; null on the open phone, which has none. */
    onOpenMenu: (() -> Unit)? = null,
    /** The item takes taps itself (a toy): keep the middle free, skip from the edges, no pause. */
    compact: Boolean = false,
    /** The group on the cover and the step it shows; null for anything else. */
    group: GroupProgress? = null,
    /** The group can be left for another item ([CoverPlayback.itemSkipAvailable]): show the item pills. */
    itemSkip: Boolean = false,
    onPreviousItem: () -> Unit = {},
    onNextItem: () -> Unit = {},
) {
    val previousIcon = if (group != null) Icons.AutoMirrored.Filled.KeyboardArrowLeft else Icons.Filled.SkipPrevious
    val nextIcon = if (group != null) Icons.AutoMirrored.Filled.KeyboardArrowRight else Icons.Filled.SkipNext
    val previousLabel = stringResource(if (group != null) R.string.action_previous_step else R.string.action_previous)
    val nextLabel = stringResource(if (group != null) R.string.action_next_step else R.string.action_next)
    // In a group the step comes first, then the playlist position: "Adım 2/5 · 3/7".
    val subtitle = if (group != null) {
        listOfNotNull(stringResource(R.string.cover_group_step, group.step, group.count), position).joinToString(" · ")
    } else {
        position
    }
    val segments = group?.takeIf { it.count in 2..MAX_SEGMENTS }
    // Anything along the bottom edge (sound, Fit / Fill, the menu): a toy's item pills sit above it.
    val bottomRow = soundOn != null || mediaFit != null || onOpenMenu != null
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .windowInsetsPadding(WindowInsets.displayCutout),
    ) {
        if (segments != null) {
            StepSegments(
                step = segments.step,
                count = segments.count,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp, top = 6.dp),
            )
        }
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(start = 18.dp, end = 6.dp, top = if (segments != null) 14.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(subtitle, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (onClose != null) ControlButton(Icons.Filled.Close, closeLabel, onClose, size = 44)
        }

        if (compact) {
            if (canSkip) {
                Box(Modifier.align(Alignment.CenterStart).padding(start = 10.dp)) {
                    ControlButton(previousIcon, previousLabel, onPrevious, size = 44)
                }
                Box(Modifier.align(Alignment.CenterEnd).padding(end = 10.dp)) {
                    ControlButton(nextIcon, nextLabel, onNext, size = 44)
                }
            }
            // The toy keeps the middle; the pills go along the bottom, above the sound and menu buttons.
            if (itemSkip) {
                ItemPills(
                    onPreviousItem = onPreviousItem,
                    onNextItem = onNextItem,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = if (bottomRow) 14.dp + 52.dp + 10.dp else 14.dp),
                )
            }
        } else {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (canSkip) ControlButton(previousIcon, previousLabel, onPrevious, size = 56)
                    ControlButton(
                        if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        stringResource(if (paused) R.string.action_resume else R.string.action_pause),
                        onTogglePause,
                        size = 72,
                        filled = true,
                    )
                    if (canSkip) ControlButton(nextIcon, nextLabel, onNext, size = 56)
                }
                if (itemSkip) ItemPills(onPreviousItem = onPreviousItem, onNextItem = onNextItem)
            }
        }

        // Fit and the labelled menu pill take the bottom right, and grow with the font size: the sound button then
        // moves to the bottom left (the clock there hides while the controls show), so a thumb never hits both.
        val bottomEndTaken = mediaFit != null || onOpenMenu != null
        if (soundOn != null) {
            Box(
                Modifier
                    .align(if (bottomEndTaken) Alignment.BottomStart else Alignment.BottomCenter)
                    .padding(start = if (bottomEndTaken) 10.dp else 0.dp, bottom = 14.dp),
            ) {
                ControlButton(
                    if (soundOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                    stringResource(if (soundOn) R.string.action_sound_off else R.string.action_sound_on),
                    onToggleSound,
                    size = 52,
                )
            }
        }

        if (bottomEndTaken) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 10.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (mediaFit != null) {
                    // Says what a press does: a fitted picture fills the cover, a filled one fits.
                    val fitted = mediaFit == MediaFit.FIT
                    ControlButton(
                        if (fitted) Icons.Filled.ZoomOutMap else Icons.Filled.FitScreen,
                        stringResource(if (fitted) R.string.media_fit_fill else R.string.media_fit_fit),
                        onSwitchFit,
                        size = 44,
                    )
                }
                if (onOpenMenu != null) {
                    // Labelled, so the way into the menu is found without knowing the swipe up.
                    ControlPill(
                        icon = Icons.Filled.KeyboardArrowUp,
                        text = stringResource(R.string.cover_menu_label),
                        onClick = onOpenMenu,
                        onClickLabel = stringResource(R.string.cover_menu_open),
                        height = 44.dp,
                        modifier = Modifier.padding(end = 2.dp),
                    )
                }
            }
        }
    }
}

/** "Önceki içerik" and "Sonraki içerik": leave the group on the cover for another item, whatever step it is on. */
@Composable
private fun ItemPills(onPreviousItem: () -> Unit, onNextItem: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Each may shrink (its text ends in "…") so both still fit at a large font size.
        ControlPill(
            icon = Icons.Filled.SkipPrevious,
            text = stringResource(R.string.action_previous_item),
            onClick = onPreviousItem,
            modifier = Modifier.weight(1f, fill = false),
        )
        ControlPill(
            icon = Icons.Filled.SkipNext,
            text = stringResource(R.string.action_next_item),
            onClick = onNextItem,
            iconFirst = false,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/** Where a group is, stories-style: one segment per step, the steps up to and including this one lit. */
@Composable
private fun StepSegments(step: Int, count: Int, modifier: Modifier = Modifier) {
    // The step line under the title says the same in words, for TalkBack too.
    Row(modifier.height(3.dp).clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(count) { index ->
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color.White.copy(alpha = if (index < step) 0.9f else 0.3f), RoundedCornerShape(50)),
            )
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

/** A labelled button, for what an icon alone would not say. Grows with the font size rather than clipping its text. */
@Composable
private fun ControlPill(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconFirst: Boolean = true,
    onClickLabel: String? = null,
    height: Dp = 40.dp,
) {
    Row(
        modifier = modifier
            .heightIn(min = height)
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (iconFirst) Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        Text(
            text = text,
            color = Color.White,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (!iconFirst) Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

/** The controls' ×, on its own: the mirroring frame shows it in the same corner when the cover is unlocked. */
@Composable
internal fun CoverCloseButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .windowInsetsPadding(WindowInsets.displayCutout)
            .padding(end = 6.dp, top = 10.dp),
    ) {
        ControlButton(Icons.Filled.Close, label, onClick, size = 44)
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

/** Shown while the cover is paused and its controls are hidden, so a frozen frame does not look broken. */
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
 * How a zoomed-in photo or video is moved, for a moment the first time in a session: one finger moves it, and only
 * once it is zoomed out do swipes change the item again. Not for TalkBack, which zooms with actions.
 */
@Composable
internal fun ZoomHint(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clearAndSetSemantics {}
            .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(50))
            .padding(start = 10.dp, end = 14.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Pinch, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.cover_zoom_hint), color = Color.White, fontSize = 13.sp)
    }
}

/**
 * Where the folded cover's menu is, for a moment on a toy (whose taps go to the game). Not for TalkBack,
 * which cannot swipe: it offers the menu as an action instead.
 */
@Composable
internal fun MenuHint(modifier: Modifier = Modifier) {
    HintPill(stringResource(R.string.cover_menu_hint), modifier, icon = Icons.Filled.KeyboardArrowUp)
}

/**
 * A short note on the cover, rounded and dark so it reads over anything. [announce]: TalkBack reads it out when it
 * appears (where the move went); otherwise it is for the eyes only (a gesture TalkBack does not use).
 */
@Composable
internal fun HintPill(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, announce: Boolean = false) {
    Row(
        modifier = modifier
            .clearAndSetSemantics {
                if (announce) {
                    contentDescription = text
                    liveRegion = LiveRegionMode.Polite
                }
            }
            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(50))
            .padding(start = if (icon != null) 10.dp else 14.dp, end = 14.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Steps a group may have for the controls to draw its segment bar; above that the step line alone says where it is. */
private const val MAX_SEGMENTS = 12
