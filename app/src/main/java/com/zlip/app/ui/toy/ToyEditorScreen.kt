package com.zlip.app.ui.toy

import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zlip.app.R
import com.zlip.app.data.ContentRepository
import com.zlip.app.data.CoverItem
import com.zlip.app.data.ToyKind
import com.zlip.app.data.ToyLogic
import com.zlip.app.data.takeSafe
import com.zlip.app.render.Playback
import com.zlip.app.render.ToyCover
import com.zlip.app.render.ToyGlyph
import com.zlip.app.ui.components.ColorSwatchRow
import com.zlip.app.ui.components.CoverPreviewFrame
import com.zlip.app.ui.components.DefaultPalette
import com.zlip.app.ui.components.HintText
import com.zlip.app.ui.components.SectionCard
import com.zlip.app.ui.components.SectionTitle
import kotlinx.coroutines.launch

/** Answers survive rotation and process death as a plain string list (blank ones included while editing). */
private val OptionsSaver = Saver<List<String>, ArrayList<String>>(save = { ArrayList(it) }, restore = { it })

/** Quick fills for the wheel: chip name and its choices. */
private val WHEEL_PRESETS = listOf(
    R.string.toy_preset_yes_no to R.array.toy_preset_yes_no_items,
    R.string.toy_preset_dinner to R.array.toy_preset_dinner_items,
    R.string.toy_preset_rps to R.array.toy_preset_rps_items,
    R.string.toy_preset_truth to R.array.toy_preset_truth_items,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToyEditorScreen(
    initial: CoverItem.Toy?,
    previewPlayback: Playback,
    onDraftChange: (CoverItem) -> Unit,
    onDirtyChange: (dirty: Boolean, canSave: Boolean) -> Unit,
    onSave: (CoverItem.Toy) -> Unit,
    onBack: () -> Unit,
) {
    val toyNames = ToyKind.entries.associateWith { stringResource(it.title()) }
    val id = rememberSaveable { initial?.id ?: ContentRepository.newId() }
    // A name equal to its toy's own name counts as "no name": it follows the toy when another is picked.
    val initialName = initial?.name?.takeIf { it != toyNames[initial.kind] }.orEmpty()
    var kind by rememberSaveable { mutableStateOf(initial?.kind ?: ToyKind.DICE) }
    var name by rememberSaveable { mutableStateOf(initialName) }
    var color by rememberSaveable { mutableIntStateOf(initial?.color ?: CoverItem.MINT) }
    var background by rememberSaveable { mutableIntStateOf(initial?.background ?: CoverItem.BLACK) }
    var options by rememberSaveable(stateSaver = OptionsSaver) { mutableStateOf(initial?.options.orEmpty()) }
    var focusNewOption by remember { mutableStateOf(false) }
    val newOption = remember { FocusRequester() }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val undoLabel = stringResource(R.string.action_undo)

    /**
     * Swaps the whole list for [next] (a preset, or empty for the built-in ones). Options the user typed
     * can be brought back from the snackbar.
     */
    fun replaceOptions(next: List<String>, message: String) {
        val previous = options
        options = next
        if (ToyLogic.cleanOptions(previous).isEmpty()) return
        scope.launch {
            // The newest replacement is the one worth undoing; an older snackbar would only hold it in the queue.
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(message, actionLabel = undoLabel, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) options = previous
        }
    }

    fun toy(kind: ToyKind, name: String, color: Int, background: Int, options: List<String>) = CoverItem.Toy(
        id = id,
        name = name.trim().ifEmpty { toyNames.getValue(kind) },
        kind = kind,
        color = color,
        background = background,
        // Answers only belong to the toys that use them; the editor keeps them while another toy is tried.
        options = if (kind.usesOptions) ToyLogic.cleanOptions(options) else emptyList(),
    )

    val item = toy(kind, name, color, background, options)
    // Built from what the editor was opened with (not from restored state), so edits that survived
    // process death still count as unsaved.
    val baseline = remember {
        toy(
            kind = initial?.kind ?: ToyKind.DICE,
            name = initialName,
            color = initial?.color ?: CoverItem.MINT,
            background = initial?.background ?: CoverItem.BLACK,
            options = initial?.options.orEmpty(),
        )
    }
    LaunchedEffect(item) {
        onDraftChange(item)
        onDirtyChange(item != baseline, true)
    }
    LaunchedEffect(focusNewOption, options.size) {
        if (focusNewOption) {
            // The new field exists once this runs; if it somehow does not, typing still works after a tap.
            runCatching { newOption.requestFocus() }
            focusNewOption = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (initial == null) R.string.toy_editor_title_new else R.string.toy_editor_title_edit)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { onSave(item) }) {
                        Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.action_save))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
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
                ToyCover(item, Modifier.fillMaxSize(), previewPlayback, interactive = true)
            }
            if (previewPlayback != Playback.STILL) {
                HintText(stringResource(R.string.toy_editor_try), Modifier.align(Alignment.CenterHorizontally))
            }

            SectionCard {
                SectionTitle(R.string.toy_editor_pick)
                Spacer(Modifier.height(12.dp))
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ToyKind.entries.chunked(2).forEach { row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            row.forEach { option ->
                                ToyCard(
                                    kind = option,
                                    selected = option == kind,
                                    color = color,
                                    background = background,
                                    onClick = { kind = option },
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                )
                            }
                        }
                    }
                }
            }

            if (kind.usesOptions) {
                val wheel = kind == ToyKind.WHEEL
                val filled = ToyLogic.cleanOptions(options).size
                val replaced = stringResource(if (wheel) R.string.toy_editor_choices_replaced else R.string.toy_editor_answers_replaced)
                SectionCard {
                    SectionTitle(if (wheel) R.string.toy_editor_choices else R.string.toy_editor_answers) {
                        if (options.isNotEmpty()) {
                            TextButton(onClick = { replaceOptions(emptyList(), replaced) }) { Text(stringResource(R.string.toy_editor_builtin)) }
                        }
                    }
                    HintText(
                        when {
                            filled == 0 -> stringResource(if (wheel) R.string.toy_editor_choices_hint else R.string.toy_editor_answers_hint)
                            wheel && filled < ToyLogic.MIN_WHEEL_SLICES -> stringResource(R.string.toy_editor_choices_one)
                            else -> pluralStringResource(
                                if (wheel) R.plurals.toy_editor_choice_count else R.plurals.toy_editor_answer_count,
                                filled,
                                filled,
                                ToyLogic.MAX_OPTIONS,
                            )
                        },
                    )
                    // Quick fills are for starting out; once the user has typed choices, a stray tap must not swap them.
                    AnimatedVisibility(visible = wheel && filled == 0) {
                        Column {
                            Spacer(Modifier.height(12.dp))
                            Label(R.string.toy_editor_presets)
                            WheelPresets(onPick = { replaceOptions(it, replaced) })
                        }
                    }
                    options.forEachIndexed { index, value ->
                        OutlinedTextField(
                            value = value,
                            onValueChange = { typed ->
                                options = options.toMutableList().also { it[index] = typed.replace('\n', ' ').takeSafe(ToyLogic.MAX_OPTION_CHARS) }
                            },
                            label = { Text(stringResource(if (wheel) R.string.toy_editor_choice_n else R.string.toy_editor_answer_n, index + 1)) },
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = { options = options.filterIndexed { at, _ -> at != index } }) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = stringResource(
                                            if (wheel) R.string.toy_editor_remove_choice else R.string.toy_editor_remove_answer,
                                            index + 1,
                                        ),
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .then(if (index == options.lastIndex) Modifier.focusRequester(newOption) else Modifier),
                        )
                    }
                    if (options.size < ToyLogic.MAX_OPTIONS) {
                        TextButton(
                            onClick = {
                                options = options + ""
                                focusNewOption = true
                            },
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(if (wheel) R.string.toy_editor_add_choice else R.string.toy_editor_add_answer))
                        }
                    }
                }
            }

            SectionCard {
                SectionTitle(R.string.toy_editor_colors)
                Spacer(Modifier.height(12.dp))
                Label(if (kind == ToyKind.MAGIC_BALL) R.string.toy_editor_ball_color else R.string.toy_editor_color)
                ColorSwatchRow(colors = DefaultPalette, selected = color, onSelect = { color = it })
                Spacer(Modifier.height(14.dp))
                Label(R.string.pixel_background)
                ColorSwatchRow(colors = DefaultPalette, selected = background, onSelect = { background = it })
                // The magic ball is always black, so only its answer color matters.
                if (color == background && kind != ToyKind.MAGIC_BALL) {
                    HintText(stringResource(R.string.toy_editor_same_colors))
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= ContentRepository.MAX_NAME) name = it },
                label = { Text(stringResource(R.string.field_name)) },
                placeholder = { Text(toyNames.getValue(kind)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** One toy in the picker: its resting face in the chosen colors, its name and what it does. */
@Composable
private fun ToyCard(kind: ToyKind, selected: Boolean, color: Int, background: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = MaterialTheme.shapes.medium
    val tile = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = shape,
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(12.dp),
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(tile)
                .background(Color(background))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, tile),
        ) {
            ToyGlyph(
                kind = kind,
                color = color,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(kind.title()),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(kind.description()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun WheelPresets(onPick: (List<String>) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        WHEEL_PRESETS.forEach { (label, items) ->
            val choices = presetChoices(items)
            SuggestionChip(onClick = { onPick(choices) }, label = { Text(stringResource(label)) })
        }
    }
}

@Composable
private fun presetChoices(@ArrayRes items: Int): List<String> = stringArrayResource(items).toList()

private val ToyKind.usesOptions: Boolean get() = this == ToyKind.MAGIC_BALL || this == ToyKind.WHEEL

@StringRes
private fun ToyKind.title(): Int = when (this) {
    ToyKind.DICE -> R.string.toy_dice
    ToyKind.COIN -> R.string.toy_coin
    ToyKind.MAGIC_BALL -> R.string.toy_ball
    ToyKind.BOTTLE -> R.string.toy_bottle
    ToyKind.WHEEL -> R.string.toy_wheel
    ToyKind.COUNTER -> R.string.toy_counter
}

@StringRes
private fun ToyKind.description(): Int = when (this) {
    ToyKind.DICE -> R.string.toy_dice_desc
    ToyKind.COIN -> R.string.toy_coin_desc
    ToyKind.MAGIC_BALL -> R.string.toy_ball_desc
    ToyKind.BOTTLE -> R.string.toy_bottle_desc
    ToyKind.WHEEL -> R.string.toy_wheel_desc
    ToyKind.COUNTER -> R.string.toy_counter_desc
}

@Composable
private fun Label(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}
