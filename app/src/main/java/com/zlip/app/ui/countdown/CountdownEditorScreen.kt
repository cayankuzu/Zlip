package com.zlip.app.ui.countdown

import android.text.format.DateFormat
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Weekend
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import com.zlip.app.R
import com.zlip.app.data.ContentRepository
import com.zlip.app.data.CountdownMath
import com.zlip.app.data.CoverItem
import com.zlip.app.data.CoverItem.Countdown.Repeat
import com.zlip.app.data.takeSafe
import com.zlip.app.render.CountdownCover
import com.zlip.app.render.CountdownFormat
import com.zlip.app.render.Playback
import com.zlip.app.ui.components.ColorSwatchRow
import com.zlip.app.ui.components.CoverPreviewFrame
import com.zlip.app.ui.components.CoverScale
import com.zlip.app.ui.components.DefaultPalette
import com.zlip.app.ui.components.HintText
import com.zlip.app.ui.components.IconBadge
import com.zlip.app.ui.components.LocalHovering
import com.zlip.app.ui.components.SectionCard
import com.zlip.app.ui.components.SegmentLabel
import com.zlip.app.ui.components.SwitchRow
import java.time.ZoneId

private const val MAX_TITLE = 32

/** A window shorter than this (the phone in landscape) cannot show the date picker's whole calendar. */
private val COMPACT_HEIGHT = 480.dp

/** Below this contrast ratio against the background, colors are flagged as hard to read. */
private const val MIN_CONTRAST = 1.8

/**
 * One tap fills the title and the date (or opens the date picker where only the user knows it). Moments
 * that come back repeat, so the card moves on to the next one instead of staying "done".
 */
private enum class Preset(@StringRes val label: Int, val icon: ImageVector, val repeat: Repeat) {
    NEW_YEAR(R.string.countdown_preset_new_year, Icons.Filled.Celebration, Repeat.YEARLY),
    WEEKEND(R.string.countdown_preset_weekend, Icons.Filled.Weekend, Repeat.WEEKLY),
    BIRTHDAY(R.string.countdown_preset_birthday, Icons.Filled.Cake, Repeat.YEARLY),
    HOLIDAY(R.string.countdown_preset_holiday, Icons.Filled.BeachAccess, Repeat.NONE),
    DAYS_SINCE(R.string.countdown_preset_days_since, Icons.Filled.History, Repeat.NONE),
}

private enum class EditorDialog { NONE, DATE, TIME }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountdownEditorScreen(
    initial: CoverItem.Countdown?,
    previewPlayback: Playback,
    onDraftChange: (CoverItem) -> Unit,
    onDirtyChange: (dirty: Boolean, canSave: Boolean) -> Unit,
    onSave: (CoverItem.Countdown) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val zone = remember { ZoneId.systemDefault() }
    val locale = CountdownFormat.locale(LocalConfiguration.current.locales[0], stringResource(R.string.countdown_language))
    val use24Hour = DateFormat.is24HourFormat(context)

    val id = rememberSaveable { initial?.id ?: ContentRepository.newId() }
    val defaultTarget = rememberSaveable { CountdownDates.defaultTarget(System.currentTimeMillis(), ZoneId.systemDefault()) }
    // A name that was only ever the title stays empty here, so it keeps following the title.
    val initialName = initial?.let { start -> start.name.takeIf { it != start.title.trim().takeSafe(ContentRepository.MAX_NAME) } }.orEmpty()
    var title by rememberSaveable { mutableStateOf(initial?.title.orEmpty()) }
    var target by rememberSaveable { mutableLongStateOf(initial?.targetMillis ?: defaultTarget) }
    var countUp by rememberSaveable { mutableStateOf(initial?.countUp ?: false) }
    var accent by rememberSaveable { mutableIntStateOf(initial?.accentColor ?: CoverItem.MINT) }
    var textColor by rememberSaveable { mutableIntStateOf(initial?.textColor ?: CoverItem.WHITE) }
    var background by rememberSaveable { mutableIntStateOf(initial?.background ?: CoverItem.BLACK) }
    var dotMatrix by rememberSaveable { mutableStateOf(initial?.dotMatrix ?: true) }
    var name by rememberSaveable { mutableStateOf(initialName) }
    var dialog by rememberSaveable { mutableStateOf(EditorDialog.NONE) }
    var repeat by rememberSaveable { mutableStateOf(initial?.repeat ?: Repeat.NONE) }
    val titleFocus = remember { FocusRequester() }

    val fallbackName = stringResource(R.string.type_countdown)
    val presetTitles = Preset.entries.map { stringResource(it.label) }

    fun countdown(
        title: String,
        target: Long,
        countUp: Boolean,
        accent: Int,
        textColor: Int,
        background: Int,
        dotMatrix: Boolean,
        name: String,
        repeat: Repeat,
    ) = CoverItem.Countdown(
        id = id,
        name = name.trim().ifBlank { title.trim().takeSafe(ContentRepository.MAX_NAME) }.ifBlank { fallbackName },
        title = title.trim(),
        targetMillis = target,
        countUp = countUp,
        textColor = textColor,
        accentColor = accent,
        background = background,
        dotMatrix = dotMatrix,
        repeat = repeat,
    )

    val item = countdown(title, target, countUp, accent, textColor, background, dotMatrix, name, repeat)
    // A repeating countdown shows (and edits) the return it counts to, not the date it was first set to.
    val shown = CountdownMath.target(item, System.currentTimeMillis(), zone)
    // Built from what the editor was opened with (not from restored state), so edits that survived
    // process death still count as unsaved.
    val baseline = remember {
        countdown(
            title = initial?.title.orEmpty(),
            target = initial?.targetMillis ?: defaultTarget,
            countUp = initial?.countUp ?: false,
            accent = initial?.accentColor ?: CoverItem.MINT,
            textColor = initial?.textColor ?: CoverItem.WHITE,
            background = initial?.background ?: CoverItem.BLACK,
            dotMatrix = initial?.dotMatrix ?: true,
            name = initialName,
            repeat = initial?.repeat ?: Repeat.NONE,
        )
    }
    LaunchedEffect(item) {
        onDraftChange(item)
        onDirtyChange(item != baseline, title.isNotBlank())
    }

    fun applyPreset(preset: Preset, presetTitle: String) {
        val now = System.currentTimeMillis()
        repeat = preset.repeat
        when (preset) {
            Preset.NEW_YEAR, Preset.WEEKEND -> {
                countUp = false
                title = presetTitle
                target = if (preset == Preset.NEW_YEAR) CountdownDates.nextNewYear(now, zone) else CountdownDates.nextWeekend(now, zone)
            }
            Preset.BIRTHDAY, Preset.HOLIDAY -> {
                countUp = false
                title = presetTitle
                // Birthdays start at midnight; the user knows the date, so ask for it right away.
                if (preset == Preset.BIRTHDAY) target = CountdownDates.withTime(target, 0, 0, zone)
                dialog = EditorDialog.DATE
            }
            Preset.DAYS_SINCE -> {
                countUp = true
                target = CountdownDates.startOfToday(now, zone)
                // "Days since" needs the user's own words ("Smoke-free"); a preset title would only mislead.
                if (title.trim() in presetTitles) title = ""
                titleFocus.requestFocus()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (initial == null) R.string.countdown_title_new else R.string.countdown_title_edit)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { onSave(item) }, enabled = title.isNotBlank()) {
                        Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.action_save))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CoverPreviewFrame(
                Modifier
                    .fillMaxWidth(0.72f)
                    .align(Alignment.CenterHorizontally),
            ) {
                CoverScale { CountdownCover(item = item, modifier = Modifier.fillMaxSize(), playback = previewPlayback) }
            }

            Column {
                Label(stringResource(R.string.countdown_presets))
                // Wraps rather than scrolls, so no preset hides off-screen.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Preset.entries.forEachIndexed { index, preset ->
                        AssistChip(
                            onClick = { applyPreset(preset, presetTitles[index]) },
                            label = { Text(presetTitles[index]) },
                            leadingIcon = { Icon(preset.icon, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it.replace('\n', ' ').takeSafe(MAX_TITLE) },
                label = { Text(stringResource(R.string.countdown_field_title)) },
                placeholder = { Text(stringResource(if (countUp) R.string.countdown_field_title_hint_up else R.string.countdown_field_title_hint)) },
                supportingText = { Text("${title.length} / $MAX_TITLE") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(titleFocus),
            )

            SectionCard {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(false, true).forEachIndexed { index, up ->
                        SegmentedButton(
                            selected = countUp == up,
                            onClick = {
                                // Only a countdown can repeat; time since a moment just keeps growing. It keeps the
                                // date on screen rather than jumping back to the one the card was first set to.
                                if (up) {
                                    target = shown
                                    repeat = Repeat.NONE
                                }
                                countUp = up
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                            icon = {},
                        ) {
                            SegmentLabel(stringResource(if (up) R.string.countdown_mode_up else R.string.countdown_mode_down))
                        }
                    }
                }
                HintText(stringResource(if (countUp) R.string.countdown_mode_up_desc else R.string.countdown_mode_down_desc))
                Spacer(Modifier.height(10.dp))
                PickerRow(
                    icon = Icons.Filled.Event,
                    label = stringResource(if (countUp) R.string.countdown_date_start else R.string.countdown_date),
                    value = CountdownFormat.longDate(shown, locale, zone),
                    actionLabel = stringResource(R.string.countdown_change_date),
                    onClick = { dialog = EditorDialog.DATE },
                )
                PickerRow(
                    icon = Icons.Filled.Schedule,
                    label = stringResource(if (countUp) R.string.countdown_time_start else R.string.countdown_time),
                    value = CountdownFormat.time(shown, locale, zone, use24Hour),
                    actionLabel = stringResource(R.string.countdown_change_time),
                    onClick = { dialog = EditorDialog.TIME },
                )
                if (!countUp) {
                    Spacer(Modifier.height(10.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        Repeat.entries.forEachIndexed { index, option ->
                            SegmentedButton(
                                selected = repeat == option,
                                onClick = {
                                    if (option != repeat) {
                                        // Starts from the return on screen, so "once" keeps the date the user sees;
                                        // a date already past (last year's birthday) moves on to its next return.
                                        target = CountdownDates.nextReturn(shown, option, System.currentTimeMillis(), zone)
                                        repeat = option
                                    }
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = Repeat.entries.size),
                                // No checkmark: the fill marks the choice, and "Tekrar yok" needs the room.
                                icon = {},
                            ) {
                                SegmentLabel(stringResource(option.label()))
                            }
                        }
                    }
                }
                val now = System.currentTimeMillis()
                when {
                    // A repeating countdown never stays passed: it says "Today!" for the day, then moves on.
                    !countUp && repeat == Repeat.NONE && target <= now -> HintText(stringResource(R.string.countdown_passed))
                    countUp && target > now -> HintText(stringResource(R.string.countdown_not_started))
                }
            }

            SectionCard {
                SwitchRow(stringResource(R.string.countdown_led), stringResource(R.string.countdown_led_desc), dotMatrix, { dotMatrix = it })
                Spacer(Modifier.height(12.dp))
                Label(stringResource(R.string.countdown_accent))
                ColorSwatchRow(colors = DefaultPalette, selected = accent, onSelect = { accent = it })
                Spacer(Modifier.height(14.dp))
                Label(stringResource(R.string.text_color))
                ColorSwatchRow(colors = DefaultPalette, selected = textColor, onSelect = { textColor = it })
                Spacer(Modifier.height(14.dp))
                Label(stringResource(R.string.pixel_background))
                ColorSwatchRow(colors = DefaultPalette, selected = background, onSelect = { background = it })
                if (hardToRead(accent, background) || hardToRead(textColor, background)) {
                    HintText(stringResource(R.string.countdown_low_contrast), Modifier.padding(top = 6.dp))
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= ContentRepository.MAX_NAME) name = it },
                label = { Text(stringResource(R.string.field_name)) },
                placeholder = { Text(item.name) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    when (dialog) {
        EditorDialog.NONE -> Unit
        EditorDialog.DATE -> {
            // In landscape the calendar's lower weeks do not fit the dialog: start with typing the date there.
            val compact = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() } < COMPACT_HEIGHT
            val state = rememberDatePickerState(
                initialSelectedDateMillis = CountdownDates.pickerMillis(shown, zone),
                initialDisplayMode = if (compact) DisplayMode.Input else DisplayMode.Picker,
            )
            // As the small window the dialog stays away (the date picked so far kept) until Zlip is full size.
            if (!LocalHovering.current) DatePickerDialog(
                onDismissRequest = { dialog = EditorDialog.NONE },
                confirmButton = {
                    TextButton(
                        enabled = state.selectedDateMillis != null,
                        onClick = {
                            state.selectedDateMillis?.let { picked ->
                                val moved = CountdownDates.withPickedDate(shown, picked, zone)
                                // A repeating date picked in the past (the day someone was born) becomes its next return.
                                target = if (countUp) moved else CountdownDates.nextReturn(moved, repeat, System.currentTimeMillis(), zone)
                            }
                            dialog = EditorDialog.NONE
                        },
                    ) { Text(stringResource(R.string.action_done)) }
                },
                dismissButton = {
                    TextButton(onClick = { dialog = EditorDialog.NONE }) { Text(stringResource(R.string.action_cancel)) }
                },
            ) {
                // Scrolls if the calendar is switched back on in a short window, so every day stays reachable.
                DatePicker(state = state, modifier = if (compact) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            }
        }
        EditorDialog.TIME -> TimeDialog(
            hour = CountdownDates.hour(shown, zone),
            minute = CountdownDates.minute(shown, zone),
            is24Hour = use24Hour,
            onConfirm = { hour, minute ->
                target = CountdownDates.withTime(shown, hour, minute, zone)
                dialog = EditorDialog.NONE
            },
            onDismiss = { dialog = EditorDialog.NONE },
        )
    }
}

/** Material's time picker in a dialog, with the usual switch between the dial and typing. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(hour: Int, minute: Int, is24Hour: Boolean, onConfirm: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = is24Hour)
    var typing by rememberSaveable { mutableStateOf(false) }
    // As the small window the dialog stays away (the time set so far kept) until Zlip is full size.
    if (LocalHovering.current) return
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            ) {
                Text(
                    text = stringResource(R.string.countdown_pick_time),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))
                Box(Modifier.align(Alignment.CenterHorizontally)) {
                    if (typing) TimeInput(state = state) else TimePicker(state = state)
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = { typing = !typing }) {
                        Icon(
                            imageVector = if (typing) Icons.Filled.Schedule else Icons.Filled.Keyboard,
                            contentDescription = stringResource(if (typing) R.string.countdown_time_dial else R.string.countdown_time_keyboard),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                    TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text(stringResource(R.string.action_done)) }
                }
            }
        }
    }
}

/** A tappable setting that shows its value: icon, small label, the value, and a chevron. */
@Composable
private fun PickerRow(icon: ImageVector, label: String, value: String, actionLabel: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClickLabel = actionLabel, role = Role.Button, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun hardToRead(foreground: Int, background: Int): Boolean =
    ColorUtils.calculateContrast(foreground, background or 0xFF000000.toInt()) < MIN_CONTRAST

@StringRes
private fun Repeat.label(): Int = when (this) {
    Repeat.NONE -> R.string.countdown_repeat_none
    Repeat.WEEKLY -> R.string.countdown_repeat_weekly
    Repeat.YEARLY -> R.string.countdown_repeat_yearly
}

@Composable
private fun Label(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}
