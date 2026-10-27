package com.zlip.app.ui.settings

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TouchApp
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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.zlip.app.R
import com.zlip.app.cover.coverHoverSupported
import com.zlip.app.data.AdvanceMode
import com.zlip.app.data.CoverSettings
import com.zlip.app.data.PowerMode
import com.zlip.app.ui.components.AutoAdvanceControls
import com.zlip.app.ui.components.AutoShowControl
import com.zlip.app.ui.components.ChoiceChips
import com.zlip.app.ui.components.CoverGesturesSheet
import com.zlip.app.ui.components.HintText
import com.zlip.app.ui.components.IconBadge
import com.zlip.app.ui.components.MirrorFitChips
import com.zlip.app.ui.components.SectionCard
import com.zlip.app.ui.components.SectionTitle
import com.zlip.app.ui.components.SegmentLabel
import com.zlip.app.ui.components.SwitchRow
import com.zlip.app.ui.components.durationLabel
import com.zlip.app.ui.home.BrightnessRow

/**
 * Settings: first the cover gestures to look up, then the cover's look, the cover while the phone is open, the
 * folded cover, the ways to control it, a collapsed "Gelişmiş" for what most people never need, and About.
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
    var gesturesOpen by rememberSaveable { mutableStateOf(false) }
    if (gesturesOpen) CoverGesturesSheet(onDismiss = { gesturesOpen = false })

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
            GesturesRow(onClick = { gesturesOpen = true })

            SectionTitle(R.string.settings_section_cover, modifier = Modifier.padding(top = 8.dp))
            SectionCard {
                Text(stringResource(R.string.settings_brightness), style = MaterialTheme.typography.bodyLarge)
                BrightnessRow(settings, onSettingsChange)
                HintText(stringResource(R.string.settings_brightness_desc))
                Spacer(Modifier.height(16.dp))

                CoverSoundChoice(settings, onSettingsChange)
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
                // What rotates only matters while something does.
                if (settings.advanceMode != AdvanceMode.OFF) HintText(stringResource(R.string.advance_playlist_hint))

                if (coverHoverSupported) {
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.mirror_fit_title), style = MaterialTheme.typography.bodyLarge)
                    HintText(stringResource(R.string.mirror_fit_desc))
                    Spacer(Modifier.height(8.dp))
                    MirrorFitChips(selected = settings.mirrorFit, onSelect = { onSettingsChange(settings.copy(mirrorFit = it)) })
                    HintText(stringResource(R.string.mirror_settings_hint))
                }
            }

            SectionTitle(R.string.settings_section_open_phone, modifier = Modifier.padding(top = 8.dp))
            OpenPhoneCard(settings, onSettingsChange)

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
                // Still usable, so they can be set up ahead; only nothing reads them yet.
                if (foldedSettingsIdle(widgetPlaced, settings.autoShowOnCover)) {
                    HintText(stringResource(R.string.settings_folded_inactive), modifier = Modifier.padding(bottom = 8.dp))
                }
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

/** "Kapak hareketleri": the first row, opening the gesture sheet, so a skipped intro is never the only guide. */
@Composable
private fun GesturesRow(onClick: () -> Unit) {
    SectionCard(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(Icons.Filled.TouchApp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_gestures), style = MaterialTheme.typography.bodyLarge)
                HintText(stringResource(R.string.settings_gestures_desc))
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

/**
 * What keeps the open-phone cover on or turns it off, in one place: starting with Zlip, the Mini kapak, the
 * idle cutoff and the dimmed inner screen. The Mini kapak notes when the system has picture-in-picture off for
 * Zlip (looked up again on every return, since it is changed on a system page).
 */
@Composable
private fun OpenPhoneCard(settings: CoverSettings, onSettingsChange: (CoverSettings) -> Unit) {
    val context = LocalContext.current
    var pipMode by remember { mutableIntStateOf(AppOpsManager.MODE_ALLOWED) }
    LifecycleResumeEffect(Unit) {
        pipMode = pictureInPictureMode(context)
        onPauseOrDispose { }
    }
    SectionCard {
        SwitchRow(
            title = stringResource(R.string.settings_auto_start),
            description = stringResource(R.string.settings_auto_start_desc),
            checked = settings.autoStart,
            onCheckedChange = { onSettingsChange(settings.copy(autoStart = it)) },
            icon = Icons.Filled.AutoMode,
        )
        if (coverHoverSupported) {
            Spacer(Modifier.height(12.dp))
            SwitchRow(
                title = stringResource(R.string.settings_keep_outside),
                description = stringResource(R.string.settings_keep_outside_desc),
                checked = settings.keepCoverInOtherApps,
                onCheckedChange = { onSettingsChange(settings.copy(keepCoverInOtherApps = it)) },
                icon = Icons.Filled.PictureInPictureAlt,
            )
            if (pipBlocked(pipMode, settings.keepCoverInOtherApps)) {
                HintText(stringResource(R.string.settings_pip_off))
                TextButton(onClick = { openAppDetails(context) }) { Text(stringResource(R.string.settings_pip_open)) }
            }
        }
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
    }
}

/**
 * Whether the cover's videos start with sound each time it turns on. A choice of two rather than a switch:
 * the cover's sound button changes the sound for the moment, so this only says how it starts.
 */
@Composable
private fun CoverSoundChoice(settings: CoverSettings, onSettingsChange: (CoverSettings) -> Unit) {
    val choices = listOf(true to R.string.settings_cover_sound_on, false to R.string.settings_cover_sound_off)
    Text(stringResource(R.string.settings_cover_sound), style = MaterialTheme.typography.bodyLarge)
    HintText(stringResource(R.string.settings_cover_sound_desc))
    Spacer(Modifier.height(8.dp))
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        choices.forEachIndexed { index, (on, label) ->
            SegmentedButton(
                selected = settings.startWithSound == on,
                onClick = { onSettingsChange(settings.copy(startWithSound = on)) },
                shape = SegmentedButtonDefaults.itemShape(index, choices.size),
                // No checkmark: the fill marks the choice, and the Turkish labels need the room.
                icon = {},
            ) { SegmentLabel(stringResource(label)) }
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
                // No checkmark: the fill marks the choice, and the labels need the room at large font sizes.
                SegmentedButton(
                    selected = settings.powerMode == mode,
                    onClick = { onSettingsChange(settings.copy(powerMode = mode)) },
                    shape = SegmentedButtonDefaults.itemShape(index, PowerMode.entries.size),
                    icon = {},
                ) { SegmentLabel(stringResource(mode.label())) }
            }
        }
        HintText(stringResource(settings.powerMode.description()))
        Spacer(Modifier.height(16.dp))

        Text(stringResource(R.string.settings_rotation), style = MaterialTheme.typography.bodyLarge)
        HintText(stringResource(R.string.settings_rotation_desc))
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            CoverSettings.ROTATIONS.forEachIndexed { index, degrees ->
                SegmentedButton(
                    selected = settings.rotation == degrees,
                    onClick = { onSettingsChange(settings.copy(rotation = degrees)) },
                    shape = SegmentedButtonDefaults.itemShape(index, CoverSettings.ROTATIONS.size),
                    icon = {},
                ) { SegmentLabel("$degrees°") }
            }
        }
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

/** Whether the folded-cover settings are waiting on a placed widget or "Katlayınca kendiliğinden aç". */
internal fun foldedSettingsIdle(widgetPlaced: Boolean, autoShowOnCover: Boolean): Boolean = !widgetPlaced && !autoShowOnCover

/**
 * Whether the Mini kapak is on but cannot open: the system's picture-in-picture switch for Zlip is off. Turning
 * it off in system settings sets the op to errored; ignored is treated the same. Allowed and default are fine.
 */
internal fun pipBlocked(pipMode: Int, miniCoverOn: Boolean): Boolean =
    miniCoverOn && (pipMode == AppOpsManager.MODE_ERRORED || pipMode == AppOpsManager.MODE_IGNORED)

// Deprecated in the Android 16 QPR SDK for a flagged overload; this one works on every Android Zlip runs on.
@Suppress("DEPRECATION")
private fun pictureInPictureMode(context: Context): Int {
    val appOps = context.getSystemService(AppOpsManager::class.java) ?: return AppOpsManager.MODE_ALLOWED
    return appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_PICTURE_IN_PICTURE, Process.myUid(), context.packageName)
}

/** Zlip's App info page, where picture-in-picture is turned back on. */
private fun openAppDetails(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    runCatching { context.startActivity(intent) }
}
