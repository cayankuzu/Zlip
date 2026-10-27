package com.zlip.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zlip.app.R
import com.zlip.app.data.CoverSettings
import java.util.Locale

/**
 * Auto-advance: on/off, the photo time, whether videos play to their end, shuffle and repeat. Used in
 * Settings; Home has a quick on/off of its own.
 */
@Composable
fun AutoAdvanceControls(
    settings: CoverSettings,
    onSettingsChange: (CoverSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        SwitchRow(
            title = stringResource(R.string.advance_title),
            description = stringResource(R.string.advance_auto_desc),
            checked = settings.autoAdvance,
            onCheckedChange = { onSettingsChange(settings.copy(autoAdvance = it)) },
            icon = Icons.Filled.Slideshow,
        )
        if (settings.autoAdvance) {
            Spacer(Modifier.height(12.dp))
            FieldLabel(stringResource(R.string.playback_photo_time))
            Spacer(Modifier.height(6.dp))
            ChoiceChips(
                options = CoverSettings.ADVANCE_SECONDS_OPTIONS,
                selected = settings.advanceSeconds,
                label = { durationLabel(it) },
                onSelect = { onSettingsChange(settings.copy(advanceSeconds = it)) },
            )
            HintText(stringResource(R.string.advance_photo_time_hint))
            Spacer(Modifier.height(12.dp))
            FieldLabel(stringResource(R.string.playback_videos))
            Spacer(Modifier.height(6.dp))
            VideosToEndChoice(
                toEnd = settings.videosToEnd,
                onChange = { onSettingsChange(settings.copy(videosToEnd = it)) },
            )
            Spacer(Modifier.height(8.dp))
            SwitchRow(
                title = stringResource(R.string.advance_shuffle),
                description = stringResource(R.string.advance_shuffle_desc),
                checked = settings.shuffle,
                onCheckedChange = { onSettingsChange(settings.copy(shuffle = it)) },
                icon = Icons.Filled.Shuffle,
            )
            SwitchRow(
                title = stringResource(R.string.advance_repeat),
                description = stringResource(R.string.advance_repeat_desc),
                checked = settings.repeat,
                onCheckedChange = { onSettingsChange(settings.copy(repeat = it)) },
                icon = Icons.Filled.Repeat,
            )
        }
    }
}

/**
 * Videos play once to their end, or are cut when the photo time is up; with a hint saying what the choice
 * does. Shared by auto-advance and the group editor, so both read the same.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideosToEndChoice(toEnd: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(true, false).forEachIndexed { index, choice ->
                // No checkmark: the fill marks the choice, and "Süre dolunca geç" needs the room.
                SegmentedButton(
                    selected = toEnd == choice,
                    onClick = { onChange(choice) },
                    shape = SegmentedButtonDefaults.itemShape(index, 2),
                    icon = {},
                ) { SegmentLabel(stringResource(if (choice) R.string.playback_videos_to_end else R.string.playback_videos_cut)) }
            }
        }
        HintText(stringResource(if (toEnd) R.string.playback_videos_to_end_hint else R.string.playback_videos_cut_hint))
    }
}

/** A small label above a setting's choices ("Photo time", "Videos"). */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** "Off", "15 s", "5 min"… */
@Composable
fun durationLabel(seconds: Int): String = when {
    seconds <= 0 -> stringResource(R.string.duration_off)
    seconds < 60 -> stringResource(R.string.duration_seconds, seconds)
    else -> stringResource(R.string.duration_minutes, seconds / 60)
}

/** "45 s", "2 min 10 s", "1 h 5 min": a running time, rounded to the second. */
@Composable
fun totalDurationLabel(ms: Long): String {
    val seconds = ((ms + 500) / 1_000).coerceAtLeast(0)
    val hours = (seconds / 3_600).toInt()
    val minutes = ((seconds % 3_600) / 60).toInt()
    val rest = (seconds % 60).toInt()
    return when {
        hours > 0 && minutes > 0 -> stringResource(R.string.duration_hours_minutes, hours, minutes)
        hours > 0 -> stringResource(R.string.duration_hours, hours)
        minutes > 0 && rest > 0 -> stringResource(R.string.duration_minutes_seconds, minutes, rest)
        minutes > 0 -> stringResource(R.string.duration_minutes, minutes)
        else -> stringResource(R.string.duration_seconds, rest)
    }
}

/** "0:42", "4:05": a clip's length. */
fun clipLengthLabel(ms: Long): String {
    val seconds = ((ms + 500) / 1_000).coerceAtLeast(0)
    return "%d:%02d".format(Locale.ROOT, seconds / 60, seconds % 60)
}
