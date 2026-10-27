package com.zlip.app.ui.text

import android.icu.text.BreakIterator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.zlip.app.R
import com.zlip.app.data.ContentRepository
import com.zlip.app.data.CoverItem
import com.zlip.app.data.takeSafe
import com.zlip.app.render.CoverContent
import com.zlip.app.render.Playback
import com.zlip.app.ui.components.ColorSwatchRow
import com.zlip.app.ui.components.CoverPreviewFrame
import com.zlip.app.ui.components.CoverScale
import com.zlip.app.ui.components.DefaultPalette
import com.zlip.app.ui.components.EditorTopBar
import com.zlip.app.ui.components.SectionCard
import com.zlip.app.ui.components.SwitchRow
import kotlin.math.roundToInt

private const val MAX_TEXT = 300

/** [prefill] starts a new item with this text (shared from another app); it counts as an unsaved change. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextEditorScreen(
    initial: CoverItem.Text?,
    previewPlayback: Playback,
    onDraftChange: (CoverItem) -> Unit,
    onDirtyChange: (dirty: Boolean, canSave: Boolean) -> Unit,
    onSave: (CoverItem.Text) -> Unit,
    onBack: () -> Unit,
    prefill: String = "",
) {
    val id = rememberSaveable { initial?.id ?: ContentRepository.newId() }
    var textValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        val start = initial?.text ?: prefill.takeSafe(MAX_TEXT)
        mutableStateOf(TextFieldValue(start, TextRange(start.length)))
    }
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var textColor by rememberSaveable { mutableIntStateOf(initial?.textColor ?: CoverItem.WHITE) }
    var background by rememberSaveable { mutableIntStateOf(initial?.background ?: CoverItem.BLACK) }
    var sizeSp by rememberSaveable { mutableIntStateOf(initial?.sizeSp ?: 48) }
    var scroll by rememberSaveable { mutableStateOf(initial?.scroll ?: true) }
    var speedDps by rememberSaveable { mutableIntStateOf(initial?.speedDps ?: 60) }
    var bold by rememberSaveable { mutableStateOf(initial?.bold ?: true) }
    val text = textValue.text

    val placeholder = stringResource(R.string.text_placeholder)
    val fallbackName = stringResource(R.string.type_text)

    fun textItem(
        text: String,
        name: String,
        textColor: Int,
        background: Int,
        sizeSp: Int,
        scroll: Boolean,
        speedDps: Int,
        bold: Boolean,
    ) = CoverItem.Text(
        id = id,
        name = name.ifBlank { text.trim().takeSafe(24) }.ifBlank { fallbackName },
        text = text.ifBlank { placeholder },
        textColor = textColor,
        background = background,
        sizeSp = sizeSp,
        scroll = scroll,
        speedDps = speedDps,
        bold = bold,
    )

    val item = textItem(text, name, textColor, background, sizeSp, scroll, speedDps, bold)
    // Built from what the editor was opened with (not from restored state), so edits that survived
    // process death still count as unsaved.
    val baseline = remember {
        textItem(
            text = initial?.text.orEmpty(),
            name = initial?.name.orEmpty(),
            textColor = initial?.textColor ?: CoverItem.WHITE,
            background = initial?.background ?: CoverItem.BLACK,
            sizeSp = initial?.sizeSp ?: 48,
            scroll = initial?.scroll ?: true,
            speedDps = initial?.speedDps ?: 60,
            bold = initial?.bold ?: true,
        )
    }
    LaunchedEffect(item) {
        onDraftChange(item)
        onDirtyChange(item != baseline, text.isNotBlank())
    }

    Scaffold(
        topBar = {
            EditorTopBar(
                title = stringResource(if (initial == null) R.string.text_title_new else R.string.text_title_edit),
                onBack = onBack,
                onSave = { onSave(item) },
                saveEnabled = text.isNotBlank(),
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
                // In cover units, so the size slider and the marquee speed look exactly as they will on the cover.
                CoverScale { CoverContent(item = item, modifier = Modifier.fillMaxSize(), playback = previewPlayback) }
            }

            OutlinedTextField(
                value = textValue,
                onValueChange = { textValue = limitInsertion(textValue, it, MAX_TEXT) },
                label = { Text(stringResource(R.string.text_field)) },
                // Save stays off until there is something to show; say why rather than leave it greyed out.
                supportingText = { Text(if (text.isBlank()) stringResource(R.string.text_required) else "${text.length} / $MAX_TEXT") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            // One tap for the signs people show most: name the moment, hold the phone up. Only while the field is empty,
            // so a stray tap can never replace what the user typed.
            AnimatedVisibility(visible = text.isEmpty(), enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Column {
                    Label(R.string.text_presets_title)
                    // Wraps rather than scrolls, so no preset hides off-screen.
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        stringArrayResource(R.array.text_presets).forEach { preset ->
                            SuggestionChip(
                                onClick = { textValue = TextFieldValue(preset, TextRange(preset.length)) },
                                label = { Text(preset) },
                            )
                        }
                    }
                }
            }

            SectionCard {
                Label(R.string.text_color)
                ColorSwatchRow(colors = DefaultPalette, selected = textColor, onSelect = { textColor = it })
                Spacer(Modifier.height(14.dp))
                Label(R.string.pixel_background)
                ColorSwatchRow(colors = DefaultPalette, selected = background, onSelect = { background = it })
                Spacer(Modifier.height(14.dp))
                Label(stringResource(R.string.text_size, sizeSp))
                val sizeLabel = stringResource(R.string.text_size_label)
                Slider(
                    value = sizeSp.toFloat(),
                    onValueChange = { sizeSp = it.roundToInt() },
                    valueRange = 16f..160f,
                    modifier = Modifier.semantics {
                        contentDescription = sizeLabel
                        stateDescription = sizeSp.toString()
                    },
                )
                SwitchRow(stringResource(R.string.text_bold), null, bold, { bold = it })
            }

            SectionCard {
                SwitchRow(stringResource(R.string.text_scroll), stringResource(R.string.text_scroll_desc), scroll, { scroll = it })
                if (scroll) {
                    Spacer(Modifier.height(8.dp))
                    Label(stringResource(R.string.text_speed, speedDps))
                    val speedLabel = stringResource(R.string.text_speed_label)
                    Slider(
                        value = speedDps.toFloat(),
                        onValueChange = { speedDps = it.roundToInt() },
                        valueRange = 10f..400f,
                        modifier = Modifier.semantics {
                            contentDescription = speedLabel
                            stateDescription = speedDps.toString()
                        },
                    )
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= ContentRepository.MAX_NAME) name = it },
                label = { Text(stringResource(R.string.field_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * Keeps [new] within [max] characters by trimming the end of what was just typed or pasted (the part
 * right before the cursor), never text that was already there. Never splits an emoji.
 */
private fun limitInsertion(old: TextFieldValue, new: TextFieldValue, max: Int): TextFieldValue {
    if (new.text.length <= max) return new
    val cursor = new.selection.end.coerceIn(0, new.text.length)
    val insertStart = (cursor - (new.text.length - old.text.length)).coerceIn(0, cursor)
    var cutStart = (cursor - (new.text.length - max)).coerceAtLeast(insertStart)
    // Snap back to a grapheme boundary so flags, skin tones and joined emoji are kept whole or dropped whole.
    if (cutStart > insertStart) {
        val breaks = BreakIterator.getCharacterInstance().apply { setText(new.text) }
        if (!breaks.isBoundary(cutStart)) cutStart = maxOf(breaks.preceding(cutStart), insertStart)
    }
    val trimmed = new.text.removeRange(cutStart, cursor)
    return if (trimmed.length <= max) TextFieldValue(trimmed, TextRange(cutStart)) else old
}

@Composable
private fun Label(text: Int) = Label(stringResource(text))

@Composable
private fun Label(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}
