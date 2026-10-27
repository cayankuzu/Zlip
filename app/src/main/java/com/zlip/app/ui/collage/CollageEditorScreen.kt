package com.zlip.app.ui.collage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zlip.app.R
import com.zlip.app.data.CollageLayout
import com.zlip.app.data.ContentRepository
import com.zlip.app.data.CoverItem
import com.zlip.app.render.CoverContent
import com.zlip.app.render.Playback
import com.zlip.app.ui.components.ChoiceChips
import com.zlip.app.ui.components.ColorSwatchRow
import com.zlip.app.ui.components.CoverPreviewFrame
import com.zlip.app.ui.components.DefaultPalette
import com.zlip.app.ui.components.HintText
import com.zlip.app.ui.components.ItemPickerSheet
import com.zlip.app.ui.components.SectionCard
import com.zlip.app.ui.components.SwitchRow
import com.zlip.app.ui.components.durationLabel

private val INTERVALS = listOf(3, 5, 10, 15, 30, 60)
private val GAPS = listOf(0, 4, 10)

/**
 * Cells survive rotation and process death as item ids only ("a,b|c|"): the items themselves come back
 * from the library and the collage being edited, so the saved state stays tiny whatever is in a cell.
 */
private val CellIdsSaver = Saver<List<List<String>>, String>(
    save = { cells -> cells.joinToString("|") { it.joinToString(",") } },
    restore = { saved -> saved.split("|").map { cell -> if (cell.isEmpty()) emptyList() else cell.split(",") } },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollageEditorScreen(
    initial: CoverItem.Collage?,
    library: List<CoverItem>,
    previewPlayback: Playback,
    onDraftChange: (CoverItem) -> Unit,
    onDirtyChange: (dirty: Boolean, canSave: Boolean) -> Unit,
    onSave: (CoverItem.Collage) -> Unit,
    onBack: () -> Unit,
) {
    val id = rememberSaveable { initial?.id ?: ContentRepository.newId() }
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var layout by rememberSaveable { mutableStateOf(initial?.layout ?: CollageLayout.GRID) }
    var cellIds by rememberSaveable(stateSaver = CellIdsSaver) {
        mutableStateOf(initial?.cells?.map { cell -> cell.map { it.id } } ?: List(CollageLayout.GRID.cells.size) { emptyList() })
    }
    // A collage keeps its own copies. Saved edits reach them anyway (ContentRepository.upsert), so they match the
    // library; they win so the baseline stays exact and items deleted from the library since stay in their cell.
    val byId = remember(initial, library) { (library + initial?.cells?.flatten().orEmpty()).associateBy { it.id } }
    val cells = cellIds.map { cell -> cell.mapNotNull(byId::get) }
    var interval by rememberSaveable { mutableIntStateOf(initial?.intervalSeconds ?: 5) }
    var gap by rememberSaveable { mutableIntStateOf(initial?.gapDp ?: 4) }
    var rounded by rememberSaveable { mutableStateOf(initial?.rounded ?: true) }
    var background by rememberSaveable { mutableIntStateOf(initial?.background ?: CoverItem.BLACK) }
    var pickingCell by rememberSaveable { mutableStateOf<Int?>(null) }

    // Anything but another collage or a group can go into a cell (a cell already steps through its items).
    val choices = remember(library) { library.filter { it !is CoverItem.Collage && it !is CoverItem.Group } }
    // Dealt out automatically: toys are dead tiles in a cell and QR codes too small to scan there.
    val autoFillChoices = remember(choices) { choices.filter { it !is CoverItem.Toy && it !is CoverItem.Qr } }
    val fallbackName = stringResource(R.string.type_collage)

    fun collage(name: String, layout: CollageLayout, cells: List<List<CoverItem>>, interval: Int, gap: Int, rounded: Boolean, background: Int) =
        CoverItem.Collage(
            id = id,
            name = name.ifBlank { fallbackName },
            layout = layout,
            cells = List(layout.cells.size) { cells.getOrElse(it) { emptyList() } },
            intervalSeconds = interval,
            gapDp = gap,
            rounded = rounded,
            background = background,
        )

    val item = collage(name, layout, cells, interval, gap, rounded, background)
    val baseline = remember {
        collage(
            name = initial?.name.orEmpty(),
            layout = initial?.layout ?: CollageLayout.GRID,
            cells = initial?.cells ?: emptyList(),
            interval = initial?.intervalSeconds ?: 5,
            gap = initial?.gapDp ?: 4,
            rounded = initial?.rounded ?: true,
            background = initial?.background ?: CoverItem.BLACK,
        )
    }
    LaunchedEffect(item) {
        onDraftChange(item)
        onDirtyChange(item != baseline, item.cells.any { it.isNotEmpty() })
    }

    fun setLayout(next: CollageLayout) {
        layout = next
        // Keep what the user already chose; a smaller layout keeps the first cells, a bigger one adds empty ones.
        cellIds = List(next.cells.size) { cellIds.getOrElse(it) { emptyList() } }
    }

    /**
     * Deals the library out over the cells, so every cell gets its own rotation. With fewer items than
     * cells it picks a layout that fits them, so no cell is left empty.
     */
    fun autoFill() {
        val fitting = when {
            autoFillChoices.size >= layout.cells.size || autoFillChoices.size < 2 -> layout
            autoFillChoices.size == 2 -> if (layout == CollageLayout.STACKED) CollageLayout.STACKED else CollageLayout.SIDE_BY_SIDE
            else -> if (layout == CollageLayout.BIG_TOP) CollageLayout.BIG_TOP else CollageLayout.BIG_LEFT
        }
        layout = fitting
        val count = fitting.cells.size
        cellIds = List(count) { cell -> autoFillChoices.filterIndexed { index, _ -> index % count == cell }.map { it.id } }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (initial == null) R.string.collage_title_new else R.string.collage_title_edit)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { onSave(item) }, enabled = item.cells.any { it.isNotEmpty() }) {
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
                    .fillMaxWidth(0.78f)
                    .align(Alignment.CenterHorizontally),
            ) {
                CoverContent(item = item, modifier = Modifier.fillMaxSize(), playback = previewPlayback)
                CellTargets(layout = layout, cells = item.cells, onPick = { pickingCell = it })
            }
            HintText(stringResource(R.string.collage_tap_hint), Modifier.align(Alignment.CenterHorizontally))

            SectionCard {
                Label(stringResource(R.string.collage_layout))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CollageLayout.entries.forEach { option ->
                        LayoutChip(option, selected = option == layout, onClick = { setLayout(option) })
                    }
                }
            }

            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.collage_cells), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (autoFillChoices.isNotEmpty()) {
                        TextButton(onClick = ::autoFill) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.collage_autofill))
                        }
                    }
                }
                if (choices.isEmpty()) HintText(stringResource(R.string.collage_empty_library))
                item.cells.forEachIndexed { index, cell ->
                    CellRow(index = index, cell = cell, onClick = { pickingCell = index })
                }
            }

            SectionCard {
                Label(stringResource(R.string.collage_interval))
                ChoiceChips(options = INTERVALS, selected = interval, label = { durationLabel(it) }, onSelect = { interval = it })
                Spacer(Modifier.height(14.dp))
                Label(stringResource(R.string.collage_gap))
                ChoiceChips(
                    options = GAPS,
                    selected = gap,
                    label = {
                        stringResource(
                            when (it) {
                                0 -> R.string.collage_gap_none
                                4 -> R.string.collage_gap_thin
                                else -> R.string.collage_gap_wide
                            },
                        )
                    },
                    onSelect = { gap = it },
                )
                Spacer(Modifier.height(8.dp))
                SwitchRow(stringResource(R.string.collage_rounded), null, rounded, { rounded = it })
                Spacer(Modifier.height(8.dp))
                Label(stringResource(R.string.pixel_background))
                ColorSwatchRow(colors = DefaultPalette, selected = background, onSelect = { background = it })
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

    pickingCell?.let { index ->
        key(index) {
            ItemPickerSheet(
                title = stringResource(R.string.collage_pick_title, index + 1),
                hint = stringResource(R.string.collage_pick_hint),
                current = item.cells.getOrElse(index) { emptyList() },
                choices = choices,
                onCommit = { picked ->
                    cellIds = List(layout.cells.size) { if (it == index) picked.map { item -> item.id } else cellIds.getOrElse(it) { emptyList() } }
                    pickingCell = null
                },
            )
        }
    }
}

/** Invisible tap areas over the preview, one per cell, with a "+" on empty cells. */
@Composable
private fun CellTargets(layout: CollageLayout, cells: List<List<CoverItem>>, onPick: (Int) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        layout.cells.forEachIndexed { index, rect ->
            val empty = cells.getOrNull(index).isNullOrEmpty()
            val label = stringResource(R.string.collage_cell, index + 1)
            Box(
                modifier = Modifier
                    .offset(x = maxWidth * rect[0], y = maxHeight * rect[1])
                    .size(maxWidth * (rect[2] - rect[0]), maxHeight * (rect[3] - rect[1]))
                    .semantics { contentDescription = label }
                    .clickable(role = Role.Button) { onPick(index) },
                contentAlignment = Alignment.Center,
            ) {
                if (empty) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.16f), CircleShape)
                            .padding(8.dp),
                    )
                }
            }
        }
    }
}

/** A tiny drawing of the layout; selected gets the accent outline. */
@Composable
private fun LayoutChip(layout: CollageLayout, selected: Boolean, onClick: () -> Unit) {
    val label = stringResource(layout.label())
    val accent = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val fill = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(width = 64.dp, height = 62.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(if (selected) 2.dp else 1.dp, accent, RoundedCornerShape(12.dp))
                .semantics {
                    this.selected = selected
                    contentDescription = label
                }
                .clickable(role = Role.RadioButton, onClick = onClick)
                .padding(8.dp),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val inset = 2.dp.toPx()
                layout.cells.forEach { rect ->
                    val left = size.width * rect[0] + inset
                    val top = size.height * rect[1] + inset
                    val cell = Size(size.width * (rect[2] - rect[0]) - 2 * inset, size.height * (rect[3] - rect[1]) - 2 * inset)
                    drawRoundRect(fill, Offset(left, top), cell, CornerRadius(4.dp.toPx()))
                    drawRoundRect(accent, Offset(left, top), cell, CornerRadius(4.dp.toPx()), style = Stroke(1.dp.toPx()))
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CellRow(index: Int, cell: List<CoverItem>, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.collage_cell, index + 1), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = if (cell.isEmpty()) {
                    stringResource(R.string.collage_cell_empty)
                } else {
                    pluralStringResource(R.plurals.collage_cell_items, cell.size, cell.size) + " · " + cell.joinToString(", ") { it.name }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            cell.take(3).forEach { thumb ->
                Box(
                    Modifier
                        .size(width = 34.dp, height = 33.dp)
                        .clip(RoundedCornerShape(6.dp)),
                ) {
                    CoverContent(item = thumb, modifier = Modifier.fillMaxSize(), playback = Playback.STILL)
                }
            }
        }
        Icon(Icons.Filled.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 10.dp))
    }
}

private fun CollageLayout.label(): Int = when (this) {
    CollageLayout.SIDE_BY_SIDE -> R.string.collage_layout_side
    CollageLayout.STACKED -> R.string.collage_layout_stacked
    CollageLayout.BIG_LEFT -> R.string.collage_layout_big_left
    CollageLayout.BIG_TOP -> R.string.collage_layout_big_top
    CollageLayout.GRID -> R.string.collage_layout_grid
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
