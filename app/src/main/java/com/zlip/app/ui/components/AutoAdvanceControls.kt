package com.zlip.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
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
import com.zlip.app.data.AdvanceMode
import com.zlip.app.data.CoverSettings

/** Auto-advance mode, time per item, shuffle and repeat. Used on the home screen and in Settings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoAdvanceControls(
    settings: CoverSettings,
    onSettingsChange: (CoverSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(stringResource(R.string.advance_title), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            AdvanceMode.entries.forEachIndexed { index, mode ->
                // No checkmark: the fill marks the choice, and "Zamanlayıcı" needs the room.
                SegmentedButton(
                    selected = settings.advanceMode == mode,
                    onClick = { onSettingsChange(settings.copy(advanceMode = mode)) },
                    shape = SegmentedButtonDefaults.itemShape(index, AdvanceMode.entries.size),
                    icon = {},
                ) { SegmentLabel(stringResource(mode.label())) }
            }
        }
        HintText(stringResource(settings.advanceMode.description()))
        if (settings.advanceMode != AdvanceMode.OFF) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(if (settings.advanceMode == AdvanceMode.SMART) R.string.advance_min_time else R.string.advance_time),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            ChoiceChips(
                options = CoverSettings.ADVANCE_SECONDS_OPTIONS,
                selected = settings.advanceSeconds,
                label = { durationLabel(it) },
                onSelect = { onSettingsChange(settings.copy(advanceSeconds = it)) },
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

/** "Off", "15 s", "5 min"… */
@Composable
fun durationLabel(seconds: Int): String = when {
    seconds <= 0 -> stringResource(R.string.duration_off)
    seconds < 60 -> stringResource(R.string.duration_seconds, seconds)
    else -> stringResource(R.string.duration_minutes, seconds / 60)
}

fun AdvanceMode.label(): Int = when (this) {
    AdvanceMode.OFF -> R.string.advance_off
    AdvanceMode.SMART -> R.string.advance_smart
    AdvanceMode.TIMER -> R.string.advance_timer
}

private fun AdvanceMode.description(): Int = when (this) {
    AdvanceMode.OFF -> R.string.advance_off_desc
    AdvanceMode.SMART -> R.string.advance_smart_desc
    AdvanceMode.TIMER -> R.string.advance_timer_desc
}
