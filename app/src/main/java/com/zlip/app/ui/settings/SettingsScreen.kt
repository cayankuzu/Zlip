package com.zlip.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AddToHomeScreen
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.zlip.app.R
import com.zlip.app.cover.coverHoverSupported
import com.zlip.app.data.CoverSettings
import com.zlip.app.data.PowerMode
import com.zlip.app.ui.components.AutoAdvanceControls
import com.zlip.app.ui.components.AutoShowControl
import com.zlip.app.ui.components.ChoiceChips
import com.zlip.app.ui.components.HintText
import com.zlip.app.ui.components.IconBadge
import com.zlip.app.ui.components.MirrorFitChips
import com.zlip.app.ui.components.SectionCard
import com.zlip.app.ui.components.SectionTitle
import com.zlip.app.ui.components.SwitchRow
import com.zlip.app.ui.components.durationLabel
import com.zlip.app.ui.home.BrightnessRow

/**
 * Settings in five sections: the cover's look, the folded cover, the ways to control it, a collapsed
 * "Gelişmiş" for what most people never need, and About.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: CoverSettings,
    notificationsAllowed: Boolean,
    widgetPlaced: Boolean,
    onSettingsChange: (CoverSettings) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onAddTile: () -> Unit,
    onShowOnboarding: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var advancedOpen by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle(R.string.settings_section_cover)
            SectionCard {
                Text(stringResource(R.string.settings_brightness), style = MaterialTheme.typography.bodyLarge)
                BrightnessRow(settings, onSettingsChange)
                HintText(stringResource(R.string.settings_brightness_desc))
                Spacer(Modifier.height(16.dp))

                SwitchRow(
                    title = stringResource(R.string.settings_led),
                    description = stringResource(R.string.settings_led_desc),
                    checked = settings.ledMatrix,
                    onCheckedChange = { onSettingsChange(settings.copy(ledMatrix = it)) },
                    icon = Icons.Filled.Grain,
                )
                if (settings.ledMatrix) LedStyleChoices(settings, onSettingsChange)
                Spacer(Modifier.height(16.dp))

                AutoAdvanceControls(settings, onSettingsChange)
                HintText(stringResource(R.string.advance_playlist_hint))

                if (coverHoverSupported) {
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.mirror_fit_title), style = MaterialTheme.typography.bodyLarge)
                    HintText(stringResource(R.string.mirror_fit_desc))
                    Spacer(Modifier.height(8.dp))
                    MirrorFitChips(selected = settings.mirrorFit, onSelect = { onSettingsChange(settings.copy(mirrorFit = it)) })
                    HintText(stringResource(R.string.mirror_settings_hint))
                }
            }

            SectionTitle(R.string.settings_section_folded, modifier = Modifier.padding(top = 8.dp))
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(if (widgetPlaced) Icons.Filled.CheckCircle else Icons.Filled.Widgets)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(if (widgetPlaced) R.string.folded_placed else R.string.folded_not_placed),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        HintText(stringResource(R.string.folded_desc))
                    }
                }
                if (!widgetPlaced) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.folded_steps),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(16.dp))
                AutoShowControl(settings, onSettingsChange)
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.settings_stay_on), style = MaterialTheme.typography.bodyLarge)
                HintText(stringResource(R.string.settings_stay_on_desc))
                Spacer(Modifier.height(8.dp))
                ChoiceChips(
                    options = CoverSettings.STAY_ON_OPTIONS,
                    selected = settings.stayOnSeconds,
                    label = { seconds ->
                        when (seconds) {
                            0 -> stringResource(R.string.stay_on_system)
                            CoverSettings.STAY_ON_ALWAYS -> stringResource(R.string.stay_on_always)
                            else -> durationLabel(seconds)
                        }
                    },
                    onSelect = { onSettingsChange(settings.copy(stayOnSeconds = it)) },
                )
                Spacer(Modifier.height(12.dp))
                SwitchRow(
                    title = stringResource(R.string.settings_nightstand),
                    description = stringResource(R.string.settings_nightstand_desc),
                    checked = settings.alwaysOnWhileCharging,
                    onCheckedChange = { onSettingsChange(settings.copy(alwaysOnWhileCharging = it)) },
                    icon = Icons.Filled.BatteryChargingFull,
                )
                SwitchRow(
                    title = stringResource(R.string.settings_cover_clock),
                    description = stringResource(R.string.settings_cover_clock_desc),
                    checked = settings.coverClock,
                    onCheckedChange = { onSettingsChange(settings.copy(coverClock = it)) },
                    icon = Icons.Filled.Schedule,
                )
            }

            SectionTitle(R.string.settings_section_controls, modifier = Modifier.padding(top = 8.dp))
            SectionCard {
                SwitchRow(
                    title = stringResource(R.string.settings_notification),
                    description = stringResource(R.string.settings_notification_desc),
                    checked = settings.notificationControls,
                    onCheckedChange = { enabled ->
                        onSettingsChange(settings.copy(notificationControls = enabled))
                        if (enabled && !notificationsAllowed) onRequestNotificationPermission()
                    },
                    icon = Icons.Filled.NotificationsActive,
                )
                if (settings.notificationControls && !notificationsAllowed) {
                    HintText(stringResource(R.string.settings_notification_denied))
                    Button(onClick = onRequestNotificationPermission, modifier = Modifier.padding(top = 8.dp)) {
                        Text(stringResource(R.string.settings_notification_allow))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.settings_tile), style = MaterialTheme.typography.bodyLarge)
                HintText(stringResource(R.string.settings_tile_desc))
                OutlinedButton(onClick = onAddTile, modifier = Modifier.padding(top = 8.dp)) {
                    Icon(Icons.AutoMirrored.Filled.AddToHomeScreen, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_tile_add))
                }
                if (coverHoverSupported) {
                    Spacer(Modifier.height(12.dp))
                    SwitchRow(
                        title = stringResource(R.string.settings_keep_outside),
                        description = stringResource(R.string.settings_keep_outside_desc),
                        checked = settings.keepCoverInOtherApps,
                        onCheckedChange = { onSettingsChange(settings.copy(keepCoverInOtherApps = it)) },
                        icon = Icons.Filled.PictureInPictureAlt,
                    )
                }
            }

            AdvancedHeader(open = advancedOpen, onToggle = { advancedOpen = !advancedOpen })
            AnimatedVisibility(
                visible = advancedOpen,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                AdvancedCard(settings, onSettingsChange)
            }

            SectionTitle(R.string.settings_section_about, modifier = Modifier.padding(top = 8.dp))
            SectionCard {
                val version = remember(context) {
                    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
                }
                Text(stringResource(R.string.settings_version, version), style = MaterialTheme.typography.bodyLarge)
                HintText(stringResource(R.string.settings_privacy))
                TextButton(onClick = onShowOnboarding, modifier = Modifier.padding(top = 4.dp)) {
                    Text(stringResource(R.string.settings_show_intro))
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** LED dot size and color, under the LED switch while it is on. */
@Composable
private fun LedStyleChoices(settings: CoverSettings, onSettingsChange: (CoverSettings) -> Unit) {
    Spacer(Modifier.height(8.dp))
    Text(stringResource(R.string.settings_led_density), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(6.dp))
    ChoiceChips(
        options = CoverSettings.LED_DENSITY_OPTIONS,
        selected = settings.ledDensity,
        label = { density ->
            stringResource(
                when (density) {
                    CoverSettings.LED_DENSITY_OPTIONS.first() -> R.string.led_density_low
                    CoverSettings.LED_DENSITY_OPTIONS.last() -> R.string.led_density_high
                    else -> R.string.led_density_mid
                },
            )
        },
        onSelect = { onSettingsChange(settings.copy(ledDensity = it)) },
    )
    Spacer(Modifier.height(10.dp))
    Text(stringResource(R.string.settings_led_color), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(6.dp))
    ChoiceChips(
        options = LED_TINTS.map { it.first },
        selected = settings.ledTint,
        label = { tint -> stringResource(LED_TINTS.first { it.first == tint }.second) },
        onSelect = { onSettingsChange(settings.copy(ledTint = it)) },
    )
}

/** "Gelişmiş" as a section title that opens and closes the card under it; closed, it lists what is inside. */
@Composable
private fun AdvancedHeader(open: Boolean, onToggle: () -> Unit) {
    val state = stringResource(if (open) R.string.settings_advanced_expanded else R.string.settings_advanced_collapsed)
    val arrow by animateFloatAsState(if (open) 180f else 0f, label = "advancedArrow")
    Row(
        modifier = Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics {
                heading()
                stateDescription = state
            }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_section_advanced), style = MaterialTheme.typography.titleMedium)
            if (!open) HintText(stringResource(R.string.settings_advanced_summary))
        }
        Icon(Icons.Filled.ExpandMore, contentDescription = null, modifier = Modifier.rotate(arrow))
    }
}

@Composable
private fun AdvancedCard(settings: CoverSettings, onSettingsChange: (CoverSettings) -> Unit) {
    SectionCard {
        Text(stringResource(R.string.settings_power_mode), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            PowerMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = settings.powerMode == mode,
                    onClick = { onSettingsChange(settings.copy(powerMode = mode)) },
                    shape = SegmentedButtonDefaults.itemShape(index, PowerMode.entries.size),
                ) { Text(stringResource(mode.label())) }
            }
        }
        HintText(stringResource(settings.powerMode.description()))
        Spacer(Modifier.height(16.dp))

        Text(stringResource(R.string.settings_standby), style = MaterialTheme.typography.bodyLarge)
        HintText(stringResource(R.string.settings_standby_desc))
        Spacer(Modifier.height(8.dp))
        ChoiceChips(
            options = CoverSettings.STANDBY_OPTIONS,
            selected = settings.standbySeconds,
            label = { durationLabel(it) },
            onSelect = { onSettingsChange(settings.copy(standbySeconds = it)) },
        )
        Spacer(Modifier.height(16.dp))

        Text(stringResource(R.string.settings_auto_off), style = MaterialTheme.typography.bodyLarge)
        HintText(stringResource(R.string.settings_auto_off_desc))
        Spacer(Modifier.height(8.dp))
        ChoiceChips(
            options = CoverSettings.AUTO_OFF_OPTIONS,
            selected = settings.autoOffMinutes,
            label = { minutes -> if (minutes == 0) stringResource(R.string.auto_off_never) else durationLabel(minutes * 60) },
            onSelect = { onSettingsChange(settings.copy(autoOffMinutes = it)) },
        )
        Spacer(Modifier.height(12.dp))

        SwitchRow(
            title = stringResource(R.string.settings_auto_start),
            description = stringResource(R.string.settings_auto_start_desc),
            checked = settings.autoStart,
            onCheckedChange = { onSettingsChange(settings.copy(autoStart = it)) },
            icon = Icons.Filled.AutoMode,
        )
        Spacer(Modifier.height(12.dp))

        Text(stringResource(R.string.settings_rotation), style = MaterialTheme.typography.bodyLarge)
        HintText(stringResource(R.string.settings_rotation_desc))
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            CoverSettings.ROTATIONS.forEachIndexed { index, degrees ->
                SegmentedButton(
                    selected = settings.rotation == degrees,
                    onClick = { onSettingsChange(settings.copy(rotation = degrees)) },
                    shape = SegmentedButtonDefaults.itemShape(index, CoverSettings.ROTATIONS.size),
                ) { Text("$degrees°") }
            }
        }
        Spacer(Modifier.height(12.dp))

        SwitchRow(
            title = stringResource(R.string.settings_video_sound),
            description = stringResource(R.string.settings_video_sound_desc),
            checked = settings.videoSoundDefault,
            onCheckedChange = { onSettingsChange(settings.copy(videoSoundDefault = it)) },
            icon = Icons.AutoMirrored.Filled.VolumeUp,
        )
        Spacer(Modifier.height(12.dp))
        HintText(stringResource(R.string.settings_battery_tip))
    }
}

/** LED colors: 0 keeps the content's own colors. */
private val LED_TINTS = listOf(
    0 to R.string.led_color_original,
    0xFFFF3B30.toInt() to R.string.led_color_red,
    0xFFFFB020.toInt() to R.string.led_color_amber,
    0xFF34C759.toInt() to R.string.led_color_green,
    0xFF6FD6FF.toInt() to R.string.led_color_blue,
    0xFFFFFFFF.toInt() to R.string.led_color_white,
)

private fun PowerMode.label(): Int = when (this) {
    PowerMode.SAVER -> R.string.power_saver
    PowerMode.BALANCED -> R.string.power_balanced
    PowerMode.SMOOTH -> R.string.power_smooth
}

private fun PowerMode.description(): Int = when (this) {
    PowerMode.SAVER -> R.string.power_saver_desc
    PowerMode.BALANCED -> R.string.power_balanced_desc
    PowerMode.SMOOTH -> R.string.power_smooth_desc
}
