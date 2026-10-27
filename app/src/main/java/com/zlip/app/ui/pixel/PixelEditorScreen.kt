package com.zlip.app.ui.pixel

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.GridOff
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.zlip.app.R
import com.zlip.app.data.ContentRepository
import com.zlip.app.data.CoverItem
import com.zlip.app.render.CoverContent
import com.zlip.app.render.Playback
import com.zlip.app.ui.components.ChoiceChips
import com.zlip.app.ui.components.ColorSwatchRow
import com.zlip.app.ui.components.DefaultPalette
import com.zlip.app.ui.components.EditorTopBar
import com.zlip.app.ui.components.HintText
import kotlin.math.abs

private enum class PixelTool(val icon: ImageVector, val label: Int) {
    PENCIL(Icons.Filled.Brush, R.string.pixel_tool_pencil),
    ERASER(Icons.Filled.Clear, R.string.pixel_tool_eraser),
    FILL(Icons.Filled.FormatColorFill, R.string.pixel_tool_fill),
    PICKER(Icons.Filled.Colorize, R.string.pixel_tool_picker),
}

/** One undo step: the whole drawing (frames are shared, not copied) and the frame the change was on. */
private data class Snapshot(val sheet: PixelFrames, val selected: Int)

/**
 * A change one touch just made, which a pinch or a double tap starting right after it takes back: the
 * undo step it recorded, the drawing it left, and the redo steps that recording cleared.
 */
private class TouchChange(val before: Snapshot, val after: PixelFrames, val clearedRedo: List<Snapshot>)

/** A finished tap on the canvas, so a quick second one on the same cell can count as a double tap. */
private class Tap(val cell: Pair<Int, Int>, val upMillis: Long, val change: TouchChange?)

/** Canvas size and every frame survive rotation and process death as one compact IntArray. */
private val SheetSaver = Saver<MutableState<PixelFrames>, IntArray>(
    save = { it.value.pack() },
    // A damaged entry returns null, and the editor starts again from what it was opened with.
    restore = { packed -> PixelFrames.unpack(packed)?.let { mutableStateOf(it) } },
)

private val GRID_SIZES = listOf(16, 24, 32, 48)
private val FPS_CHOICES = listOf(2, 4, 6, 8, 12, 16)
private const val MAX_UNDO = 50

/** From this grid size up, cells get too small for a finger, so the editor points out pinch to zoom. */
private const val ZOOM_HINT_SIZE = 32

/** A window this much wider than tall (the phone in landscape) puts the tools beside the canvas. */
private const val SIDE_BY_SIDE_RATIO = 1.3f

/** Beside the tools, the canvas takes at most this share of the width, so the tools keep a usable column. */
private const val SIDE_CANVAS_MAX_FRACTION = 0.6f

/** Room left under the canvas so the frame strip peeks out, and the smallest the canvas gets in a short window. */
private val CANVAS_PEEK = 56.dp
private val CANVAS_MIN_HEIGHT = 160.dp

/**
 * A stroke this old that has moved past the touch slop is drawing: a second finger landing now is a
 * palm or a slip and is ignored. Before that, a second finger turns the touch into a pinch.
 */
private const val SETTLED_STROKE_MS = 150L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PixelEditorScreen(
    initial: CoverItem.PixelArt?,
    onDraftChange: (CoverItem) -> Unit,
    onDirtyChange: (dirty: Boolean, canSave: Boolean) -> Unit,
    onSave: (CoverItem.PixelArt) -> Unit,
    onBack: () -> Unit,
) {
    // Saveable so unsaved work survives process death; the undo history is deliberately not kept.
    val id = rememberSaveable { initial?.id ?: ContentRepository.newId() }
    val defaultName = stringResource(R.string.type_pixel)
    val initialFps = (initial?.fps ?: CoverItem.PixelArt.DEFAULT_FPS).coerceIn(1, 30)
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var sheet by rememberSaveable(saver = SheetSaver) {
        mutableStateOf(initial?.let { PixelFrames.of(it) } ?: PixelFrames.blank(16, 16))
    }
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var fps by rememberSaveable { mutableIntStateOf(initialFps) }
    var background by rememberSaveable { mutableIntStateOf(initial?.background ?: CoverItem.BLACK) }
    var color by rememberSaveable { mutableIntStateOf(DefaultPalette[4]) }
    var tool by rememberSaveable { mutableStateOf(PixelTool.PENCIL) }
    var showGrid by rememberSaveable { mutableStateOf(true) }
    var mirror by rememberSaveable { mutableStateOf(false) }
    var onionSkin by rememberSaveable { mutableStateOf(true) }
    var playing by rememberSaveable { mutableStateOf(false) }
    val undoStack = remember { mutableStateListOf<Snapshot>() }
    val redoStack = remember { mutableStateListOf<Snapshot>() }
    // Read while drawing and by the gesture handler; only the zoomed / not zoomed switch recomposes.
    val zoom = remember { mutableStateOf(PixelZoom.FIT) }
    val zoomed by remember { derivedStateOf { zoom.value.zoomed } }

    // The speed only means something with frames; a still drawing always carries the default, so
    // toggling frames on and off again never leaves a change behind that nobody can see.
    fun pixelArt(name: String, sheet: PixelFrames, background: Int, fps: Int) = CoverItem.PixelArt(
        id = id,
        name = name.ifBlank { defaultName },
        width = sheet.width,
        height = sheet.height,
        pixels = sheet.frames[0],
        background = background,
        extraFrames = sheet.frames.drop(1),
        fps = if (sheet.count > 1) fps else CoverItem.PixelArt.DEFAULT_FPS,
    )

    // Built from what the editor was opened with (not from restored state), so edits that survived
    // process death still count as unsaved.
    val baseline = remember {
        pixelArt(initial?.name.orEmpty(), initial?.let { PixelFrames.of(it) } ?: PixelFrames.blank(16, 16), initial?.background ?: CoverItem.BLACK, initialFps)
    }
    // All frames and the speed go to the cover, so it plays the animation live while it is drawn.
    val art = remember(name, sheet, background, fps) { pixelArt(name, sheet, background, fps) }
    LaunchedEffect(art) {
        onDraftChange(art)
        onDirtyChange(art != baseline, true)
    }

    val animated = sheet.count > 1
    val frameIndex = selected.coerceIn(0, sheet.count - 1)
    val previewing = playing && animated

    /** The frame the tools draw on; read fresh by the gesture handler, which outlives recompositions. */
    fun current(): Int = selected.coerceIn(0, sheet.count - 1)

    fun restore(snapshot: Snapshot) {
        sheet = snapshot.sheet
        selected = snapshot.selected.coerceIn(0, snapshot.sheet.count - 1)
    }

    fun recordUndo(snapshot: Snapshot) {
        undoStack.add(snapshot)
        if (undoStack.size > MAX_UNDO) undoStack.removeAt(0)
        redoStack.clear()
    }

    /** Call right before a change that is certain to modify the drawing. Stops the preview, so the edit is seen. */
    fun pushUndo() {
        playing = false
        recordUndo(Snapshot(sheet, current()))
    }

    // Each step jumps to the frame it changed, so undoing on another frame never happens out of sight.
    fun undo() {
        val snapshot = undoStack.removeLastOrNull() ?: return
        playing = false
        redoStack.add(Snapshot(sheet, snapshot.selected))
        restore(snapshot)
    }

    fun redo() {
        val snapshot = redoStack.removeLastOrNull() ?: return
        playing = false
        undoStack.add(Snapshot(sheet, snapshot.selected))
        restore(snapshot)
    }

    /** Records [before] as the undo step of a change a touch is making, keeping what [takeBack] needs. */
    fun recordTouch(before: Snapshot): List<Snapshot> {
        val clearedRedo = redoStack.toList()
        recordUndo(before)
        return clearedRedo
    }

    /** Takes back a change a touch just made as if it never happened: no undo step, and redo as it was. */
    fun takeBack(change: TouchChange) {
        // Something else changed the drawing since (the undo button): that wins.
        if (sheet !== change.after || undoStack.lastOrNull() !== change.before) return
        undoStack.removeAt(undoStack.lastIndex)
        redoStack.clear()
        redoStack.addAll(change.clearedRedo)
        restore(change.before)
    }

    fun paint(cells: List<Pair<Int, Int>>, value: Int) {
        val index = current()
        val width = sheet.width
        val height = sheet.height
        val next = sheet.frames[index].toMutableList()
        var changed = false
        for ((x, y) in cells) {
            val targets = if (mirror) listOf(x, width - 1 - x) else listOf(x)
            for (tx in targets) {
                if (tx !in 0 until width || y !in 0 until height) continue
                val i = y * width + tx
                if (next[i] != value) {
                    next[i] = value
                    changed = true
                }
            }
        }
        if (changed) sheet = sheet.replace(index, next)
    }

    fun resize(size: Int) {
        if (size == sheet.width && size == sheet.height) return
        pushUndo()
        sheet = sheet.resized(size, size)
        zoom.value = PixelZoom.FIT
    }

    fun addFrame() {
        if (!sheet.canAdd) return
        val index = current()
        pushUndo()
        sheet = sheet.insertCopy(index)
        selected = index + 1
    }

    fun deleteFrame() {
        if (sheet.count <= 1) return
        val index = current()
        pushUndo()
        sheet = sheet.remove(index)
        selected = index.coerceAtMost(sheet.count - 1)
    }

    fun moveFrame(by: Int) {
        val from = current()
        val to = from + by
        if (to !in 0 until sheet.count) return
        pushUndo()
        sheet = sheet.move(from, to)
        selected = to
    }

    Scaffold(
        topBar = {
            EditorTopBar(
                title = stringResource(if (initial == null) R.string.pixel_title_new else R.string.pixel_title_edit),
                onBack = onBack,
                onSave = { onSave(art) },
                extraActions = {
                    IconButton(onClick = ::undo, enabled = undoStack.isNotEmpty()) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = stringResource(R.string.action_undo))
                    }
                    IconButton(onClick = ::redo, enabled = redoStack.isNotEmpty()) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = stringResource(R.string.action_redo))
                    }
                },
            )
        },
    ) { padding ->
        // The drawing surface; where it goes and how big it may be is decided by the layout below.
        val canvas: @Composable (Modifier) -> Unit = { canvasModifier ->
            Box(modifier = canvasModifier) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { zoom.value = zoom.value.fitted(it) }
                        // One finger draws, two fingers zoom and pan, and a double tap while zoomed in shows it all.
                        .pointerInput(Unit) {
                            var lastTap: Tap? = null
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                down.consume()
                                val previousTap = lastTap
                                lastTap = null
                                if (playing) {
                                    playing = false
                                    if (sheet.count > 1) {
                                        // A touch on the playing preview only stops it; drawing starts with the next one.
                                        waitForUpOrCancellation()?.consume()
                                        return@awaitEachGesture
                                    }
                                }
                                val width = sheet.width
                                val height = sheet.height
                                val grid = PixelGrid(size.toSize(), width, height)
                                val start = grid.cellAt(down.position, zoom.value)
                                // A second tap on the same cell would change nothing, so zoomed in it means "show it all";
                                // whatever the first tap did is taken back.
                                if (zoom.value.zoomed && start != null && previousTap != null && previousTap.cell == start &&
                                    down.uptimeMillis - previousTap.upMillis < viewConfiguration.doubleTapTimeoutMillis
                                ) {
                                    previousTap.change?.let(::takeBack)
                                    zoom.value = PixelZoom.FIT
                                    waitForUpOrCancellation()?.consume()
                                    return@awaitEachGesture
                                }
                                val drawing = tool == PixelTool.PENCIL || tool == PixelTool.ERASER
                                val value = if (tool == PixelTool.ERASER) 0 else color
                                // One undo step per stroke, recorded only once the stroke really changes something.
                                val before = Snapshot(sheet, current())
                                var clearedRedo: List<Snapshot>? = null
                                fun strokeTo(cells: List<Pair<Int, Int>>) {
                                    paint(cells, value)
                                    if (clearedRedo == null && sheet !== before.sheet) clearedRedo = recordTouch(before)
                                }
                                fun strokeChange(): TouchChange? = clearedRedo?.let { TouchChange(before, sheet, it) }
                                // Pencil and eraser paint at once, so a dot shows under the finger without delay.
                                if (drawing && start != null) strokeTo(listOf(start))
                                var last = start
                                var travelled = false
                                var pinching = false
                                var upMillis: Long? = null
                                while (true) {
                                    val event = awaitPointerEvent()
                                    event.changes.forEach { it.consume() }
                                    if (pinching) {
                                        if (event.changes.none { it.pressed }) break
                                        val centroid = event.calculateCentroid()
                                        if (centroid.isSpecified) {
                                            zoom.value = zoom.value.pinched(centroid, event.calculateZoom(), event.calculatePan(), size)
                                        }
                                        continue
                                    }
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) {
                                        upMillis = change.uptimeMillis
                                        break
                                    }
                                    if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) travelled = true
                                    val settled = travelled && change.uptimeMillis - down.uptimeMillis >= SETTLED_STROKE_MS
                                    if (event.changes.count { it.pressed } > 1 && !settled) {
                                        // The start of a pinch: the dot the first finger made must not stay behind.
                                        strokeChange()?.let(::takeBack)
                                        pinching = true
                                        continue
                                    }
                                    if (!drawing) continue
                                    val cell = grid.cellAt(change.position, zoom.value)
                                    if (cell == null) {
                                        // Left the canvas: don't join the exit and re-entry points with a line.
                                        last = null
                                        continue
                                    }
                                    if (cell != last) {
                                        strokeTo(line(last ?: cell, cell))
                                        last = cell
                                    }
                                }
                                if (pinching || upMillis == null || start == null) return@awaitEachGesture
                                // Fill and picker act on release, once it is clear the touch was not the start of a pinch.
                                val tapChange = when (tool) {
                                    PixelTool.FILL -> {
                                        val index = current()
                                        val frame = sheet.frames[index]
                                        val filled = floodFill(frame, width, height, start, color)
                                        if (filled !== frame) {
                                            playing = false
                                            val fillBefore = Snapshot(sheet, index)
                                            val cleared = recordTouch(fillBefore)
                                            sheet = sheet.replace(index, filled)
                                            TouchChange(fillBefore, sheet, cleared)
                                        } else {
                                            null
                                        }
                                    }
                                    PixelTool.PICKER -> {
                                        val picked = sheet.frames[current()][start.second * width + start.first]
                                        color = if (picked != 0) picked else background
                                        tool = PixelTool.PENCIL
                                        null
                                    }
                                    PixelTool.PENCIL, PixelTool.ERASER -> strokeChange()
                                }
                                if (!travelled) lastTap = Tap(start, upMillis, tapChange)
                            }
                        },
                ) {
                    if (previewing) {
                        CoverContent(item = art, modifier = Modifier.fillMaxSize(), playback = Playback.LIVE)
                    } else {
                        // The frame being drawn, with the one before it faintly underneath while onion skin is on.
                        val pixels = remember(sheet, frameIndex, background, onionSkin) {
                            val frame = sheet.frames[frameIndex]
                            if (onionSkin && frameIndex > 0) onionSkin(frame, sheet.frames[frameIndex - 1], background) else frame
                        }
                        PixelCanvas(
                            pixels = pixels,
                            columns = sheet.width,
                            rows = sheet.height,
                            background = background,
                            gridColor = if (showGrid) Color.White.copy(alpha = 0.18f) else null,
                            zoom = { zoom.value },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                if (zoomed && !previewing) {
                    FilledTonalIconButton(
                        onClick = { zoom.value = PixelZoom.FIT },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp),
                    ) {
                        Icon(Icons.Filled.FitScreen, contentDescription = stringResource(R.string.pixel_zoom_fit))
                    }
                }
            }
        }
        val tools: @Composable ColumnScope.() -> Unit = {
            FrameStrip(
                sheet = sheet,
                background = background,
                selected = frameIndex,
                playing = previewing,
                onSelect = {
                    playing = false
                    selected = it
                },
                onAdd = ::addFrame,
                onTogglePlay = { playing = !previewing },
            )
            if (animated) {
                FrameActions(
                    onionSkin = onionSkin,
                    onOnionSkinChange = {
                        onionSkin = it
                        playing = false
                    },
                    canMoveEarlier = frameIndex > 0,
                    canMoveLater = frameIndex < sheet.count - 1,
                    canDelete = sheet.count > 1,
                    onMoveEarlier = { moveFrame(-1) },
                    onMoveLater = { moveFrame(1) },
                    onDelete = ::deleteFrame,
                )
            }
            if (!sheet.canAdd) {
                HintText(pluralStringResource(R.plurals.pixel_anim_limit, CoverItem.PixelArt.MAX_FRAMES, CoverItem.PixelArt.MAX_FRAMES))
            } else if (!animated) {
                HintText(stringResource(R.string.pixel_anim_hint))
            }

            // Wraps rather than scrolls: Mirror, Grid and Clear sat off-screen with nothing to show they were there.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PixelTool.entries.forEach { entry ->
                    FilterChip(
                        selected = tool == entry,
                        onClick = { tool = entry },
                        label = { Text(stringResource(entry.label)) },
                        leadingIcon = { Icon(entry.icon, contentDescription = null) },
                    )
                }
                FilterChip(
                    selected = mirror,
                    onClick = { mirror = !mirror },
                    label = { Text(stringResource(R.string.pixel_mirror)) },
                    leadingIcon = { Icon(Icons.Filled.Flip, contentDescription = null) },
                )
                FilterChip(
                    selected = showGrid,
                    onClick = { showGrid = !showGrid },
                    label = { Text(stringResource(R.string.pixel_grid)) },
                    leadingIcon = { Icon(if (showGrid) Icons.Filled.GridOn else Icons.Filled.GridOff, contentDescription = null) },
                )
                // An action, not a toggle: an assist chip, so TalkBack does not announce a checkbox.
                AssistChip(
                    onClick = {
                        // Clears the frame being drawn; the other frames stay.
                        val index = current()
                        if (sheet.frames[index].any { it != 0 }) {
                            pushUndo()
                            sheet = sheet.replace(index, List(sheet.width * sheet.height) { 0 })
                        }
                    },
                    label = { Text(stringResource(R.string.pixel_clear)) },
                    leadingIcon = { Icon(Icons.Filled.DeleteSweep, contentDescription = null) },
                )
            }

            Label(R.string.pixel_color)
            ColorSwatchRow(
                colors = DefaultPalette,
                selected = color,
                onSelect = {
                    color = it
                    if (tool == PixelTool.ERASER || tool == PixelTool.PICKER) tool = PixelTool.PENCIL
                },
            )

            Label(R.string.pixel_background)
            ColorSwatchRow(colors = DefaultPalette, selected = background, onSelect = { background = it })

            if (animated) {
                Label(R.string.pixel_anim_speed)
                // An imported speed that is not one of the presets stays selectable.
                ChoiceChips(
                    options = (FPS_CHOICES + fps).distinct().sorted(),
                    selected = fps,
                    label = { stringResource(R.string.pixel_anim_fps, it) },
                    onSelect = { fps = it },
                )
            }

            Label(R.string.pixel_size)
            ChoiceChips(
                options = GRID_SIZES,
                // An imported drawing that is not one of these squares has none selected.
                selected = sheet.width.takeIf { sheet.width == sheet.height } ?: -1,
                label = { "$it×$it" },
                onSelect = { resize(it) },
            )
            if (maxOf(sheet.width, sheet.height) >= ZOOM_HINT_SIZE) {
                HintText(stringResource(R.string.pixel_zoom_hint))
            }

            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= ContentRepository.MAX_NAME) name = it },
                label = { Text(stringResource(R.string.field_name)) },
                placeholder = { Text(defaultName) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
        }

        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val aspect = sheet.width.toFloat() / sheet.height
            if (maxWidth > maxHeight * SIDE_BY_SIDE_RATIO) {
                // Landscape: the drawing at full height on one side, the tools scrolling on their own beside it.
                // A canvas as wide as the window would be three screens tall, take every drag and hide the tools.
                val canvasMaxWidth = maxWidth * SIDE_CANVAS_MAX_FRACTION
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    canvas(
                        Modifier
                            .align(Alignment.CenterVertically)
                            // Keep strokes out of the back-gesture zone on the canvas's side.
                            .windowInsetsPadding(WindowInsets.systemGestures.only(WindowInsetsSides.Start))
                            .systemGestureExclusion()
                            .padding(vertical = 12.dp)
                            .widthIn(max = canvasMaxWidth)
                            // Full height if that fits the width cap; a wide imported drawing is narrowed instead,
                            // never stretched out of shape.
                            .aspectRatio(aspect, matchHeightConstraintsFirst = true),
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .imePadding()
                            .verticalScroll(rememberScrollState())
                            .padding(top = 12.dp, end = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        content = tools,
                    )
                }
            } else {
                // Measured outside the keyboard's padding, so the canvas keeps its size while the name is typed.
                val canvasMaxHeight = (maxHeight - CANVAS_PEEK).coerceAtLeast(CANVAS_MIN_HEIGHT)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    canvas(
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            // Keep strokes out of the system back-gesture zones so drawing never triggers "back".
                            .windowInsetsPadding(WindowInsets.systemGestures.only(WindowInsetsSides.Horizontal))
                            .systemGestureExclusion()
                            // Never taller than the window, with the frame strip peeking below as a sign the page goes on.
                            .widthIn(max = canvasMaxHeight * aspect)
                            .fillMaxWidth()
                            .aspectRatio(aspect),
                    )
                    tools()
                }
            }
        }
    }
}

@Composable
private fun Label(text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Bresenham line so fast strokes do not leave gaps. */
private fun line(from: Pair<Int, Int>, to: Pair<Int, Int>): List<Pair<Int, Int>> {
    var (x0, y0) = from
    val (x1, y1) = to
    val dx = abs(x1 - x0)
    val dy = -abs(y1 - y0)
    val sx = if (x0 < x1) 1 else -1
    val sy = if (y0 < y1) 1 else -1
    var err = dx + dy
    val cells = mutableListOf<Pair<Int, Int>>()
    while (true) {
        cells += x0 to y0
        if (x0 == x1 && y0 == y1) break
        val e2 = 2 * err
        if (e2 >= dy) {
            err += dy
            x0 += sx
        }
        if (e2 <= dx) {
            err += dx
            y0 += sy
        }
    }
    return cells
}

private fun floodFill(pixels: List<Int>, width: Int, height: Int, start: Pair<Int, Int>, replacement: Int): List<Int> {
    val target = pixels[start.second * width + start.first]
    if (target == replacement) return pixels
    val result = pixels.toMutableList()
    val queue = ArrayDeque<Int>()
    queue.add(start.second * width + start.first)
    while (queue.isNotEmpty()) {
        val index = queue.removeFirst()
        if (result[index] != target) continue
        result[index] = replacement
        val x = index % width
        val y = index / width
        if (x > 0) queue.add(index - 1)
        if (x < width - 1) queue.add(index + 1)
        if (y > 0) queue.add(index - width)
        if (y < height - 1) queue.add(index + width)
    }
    return result
}
