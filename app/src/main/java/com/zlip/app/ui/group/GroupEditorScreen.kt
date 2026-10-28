package com.zlip.app.ui.group

import android.view.ViewConfiguration
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Gradient
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zlip.app.R
import com.zlip.app.data.ContentRepository
import com.zlip.app.data.CoverItem
import com.zlip.app.data.GroupTiming
import com.zlip.app.data.MediaKind
import com.zlip.app.render.CoverContent
import com.zlip.app.render.Playback
import com.zlip.app.ui.components.ChoiceChips
import com.zlip.app.ui.components.CoverPreviewFrame
import com.zlip.app.ui.components.CoverScale
import com.zlip.app.ui.components.DragHandle
import com.zlip.app.ui.components.EditorTopBar
import com.zlip.app.ui.components.FieldLabel
import com.zlip.app.ui.components.HintText
import com.zlip.app.ui.components.ItemPickerSheet
import com.zlip.app.ui.components.LocalHovering
import com.zlip.app.ui.components.ReorderableList
import com.zlip.app.ui.components.ReorderableRowScope
import com.zlip.app.ui.components.SectionCard
import com.zlip.app.ui.components.SwitchRow
import com.zlip.app.ui.components.VideosToEndChoice
import com.zlip.app.ui.components.badgeLabel
import com.zlip.app.ui.components.clipLengthLabel
import com.zlip.app.ui.components.durationLabel
import com.zlip.app.ui.components.totalDurationLabel
import com.zlip.app.ui.components.wakesStandby
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Photo time choices: the same rhythm as a collage's interval. */
private val STEP_OPTIONS = listOf(3, 5, 10, 15, 30, 60)

/** A group needs at least this many steps to be worth saving (one item is simply that item). */
private const val MIN_ITEMS = 2

/** [current] steps with [added] after them (none twice), cut to what a group holds. */
internal fun withAddedSteps(current: List<String>, added: List<String>): List<String> =
    (current + added).distinct().take(CoverItem.Group.MAX_ITEMS)

/** How many more steps fit in a group that has [count]. */
internal fun roomLeft(count: Int): Int = (CoverItem.Group.MAX_ITEMS - count).coerceAtLeast(0)

/** A group is saved with 2 up to [CoverItem.Group.MAX_ITEMS] steps; more would be cut when the library is read. */
internal fun canSaveSteps(count: Int): Boolean = count in MIN_ITEMS..CoverItem.Group.MAX_ITEMS

/**
 * A group with no name of its own: its first step's name ("Tatil"), so several groups stay apart in the library and the
 * notification; [typeName] ("Grup") while no step has a name, or when the first is a camera's file name
 * ("20261027_143022", "IMG_1234"), which says nothing. No step count: it would go stale with the next edit, and the
 * cover and Home already say "Adım 3/8".
 */
internal fun defaultGroupName(stepNames: List<String>, typeName: String): String {
    val first = stepNames.firstOrNull { it.isNotBlank() }?.trim() ?: return typeName
    return if (looksLikeFileName(first)) typeName else first
}

/** What a gallery file is called before anyone names it: a long run of digits (a date, a time) or "IMG_1234". */
internal fun looksLikeFileName(name: String): Boolean = FILE_NAME.containsMatchIn(name)

private val FILE_NAME = Regex("""\d{6,}|^[A-Za-z]{2,5}[_-]?\d{3,}$""")

/** Where the step [id] is among [ids]: the preview shows it. The first step when it is gone (or none was picked yet). */
internal fun shownStepIndex(ids: List<String>, id: String?): Int = ids.indexOf(id).takeIf { it >= 0 } ?: 0

/** The step after (or before) [index] in a list of [size], going round; -1 in an empty list. */
internal fun stepBeside(index: Int, size: Int, forward: Boolean): Int =
    if (size <= 0) -1 else (index + if (forward) 1 else -1).mod(size)

/** [ids] with [id] put back at [index] (or at the end, if the list got shorter), unless it is there already or full. */
internal fun withStepRestored(ids: List<String>, id: String, index: Int): List<String> {
    if (id in ids || ids.size >= CoverItem.Group.MAX_ITEMS) return ids
    return ids.toMutableList().apply { add(index.coerceIn(0, size), id) }
}

/** The steps survive rotation and process death as item ids only; the items come back from the library. */
private val ItemIdsSaver = Saver<List<String>, String>(
    save = { ids -> ids.joinToString(",") },
    restore = { saved -> if (saved.isEmpty()) emptyList() else saved.split(",") },
)

/**
 * Builds a group: items played one after another, full screen. The order is set by dragging, the
 * up/down buttons or TalkBack actions; items come from the library ("Choose items") or straight from
 * the gallery. [prefill] starts a new group with these library items (a multi-pick from the gallery);
 * [onAddFromGallery] picks and imports at most the room it is given; the ids come back, in picked order, as
 * [pendingAdds], which this editor takes and then reports with [onPendingAddsConsumed]. They are state, not a
 * callback, so files picked while the activity was recreated still reach the group. [hasPlaylist]: the user has a
 * custom playlist; without one a group's steps play only inside the group (see [com.zlip.app.data.Rotation]).
 *
 * The preview is a still of one step, never a second player: while the cover plays this draft, [coverStep] (group id,
 * step id) is what it shows, and a tap on a step row or ‹ › opens that step there through [onShowOnCover] (false
 * when the cover does not play this group). Without the cover they pick the step the preview shows.
 */
@Composable
fun GroupEditorScreen(
    initial: CoverItem.Group?,
    prefill: List<String>,
    library: List<CoverItem>,
    hasPlaylist: Boolean,
    coverStep: Pair<String, String>?,
    onShowOnCover: (groupId: String, stepId: String) -> Boolean,
    onAddFromGallery: (room: Int) -> Unit,
    pendingAdds: List<String>?,
    onPendingAddsConsumed: () -> Unit,
    onDraftChange: (CoverItem) -> Unit,
    onDirtyChange: (dirty: Boolean, canSave: Boolean) -> Unit,
    onSave: (CoverItem.Group) -> Unit,
    onBack: () -> Unit,
) {
    val resources = LocalResources.current
    val id = rememberSaveable { initial?.id ?: ContentRepository.newId() }
    // A name the editor made up starts blank, so it keeps following the steps instead of freezing at the first one.
    var name by rememberSaveable {
        val typeName = resources.getString(R.string.type_group)
        mutableStateOf(initial?.name?.takeUnless { it == defaultGroupName(initial.items.map { step -> step.name }, typeName) }.orEmpty())
    }
    // Capped like the cover caps it on reading, so what is saved is what plays.
    var itemIds by rememberSaveable(stateSaver = ItemIdsSaver) {
        mutableStateOf((initial?.items?.map { it.id } ?: prefill).take(CoverItem.Group.MAX_ITEMS))
    }
    // A new group starts from the defaults (photos 5 s, videos to the end, in order, repeating, soft fades).
    val start = initial ?: CoverItem.Group(id = id, name = "")
    var stepSeconds by rememberSaveable { mutableIntStateOf(start.stepSeconds) }
    var videosToEnd by rememberSaveable { mutableStateOf(start.videosToEnd) }
    var shuffle by rememberSaveable { mutableStateOf(start.shuffle) }
    var repeat by rememberSaveable { mutableStateOf(start.repeat) }
    var crossfade by rememberSaveable { mutableStateOf(start.crossfade) }
    var picking by rememberSaveable { mutableStateOf(false) }
    // The group's own copies win, like a collage's: the baseline stays exact, and items deleted from the
    // library since stay in the group. Saved edits of library items reach these copies anyway.
    val byId = remember(initial, library) { (library + initial?.items.orEmpty()).associateBy { it.id } }
    val items = itemIds.mapNotNull(byId::get)
    // Toys need taps a group step never gets; groups do not nest.
    val choices = remember(library) { library.filter { it !is CoverItem.Group && it !is CoverItem.Toy } }
    val typeName = stringResource(R.string.type_group)
    val fallbackName = defaultGroupName(items.map { it.name }, typeName)

    val item = CoverItem.Group(
        id = id,
        name = name.ifBlank { fallbackName },
        items = items,
        stepSeconds = stepSeconds,
        videosToEnd = videosToEnd,
        shuffle = shuffle,
        repeat = repeat,
        crossfade = crossfade,
    )
    // Every option counts: changing only how the group plays still asks before it is thrown away.
    val baseline = remember { start.copy(id = id, name = start.name.ifBlank { typeName }) }
    val canSave = canSaveSteps(items.size)
    LaunchedEffect(item) {
        onDraftChange(item)
        onDirtyChange(item != baseline, canSave)
    }
    // Files the gallery just brought in for this group: after the steps shown (which drops ids gone from the library).
    LaunchedEffect(pendingAdds) {
        if (pendingAdds != null) {
            itemIds = withAddedSteps(itemIds.filter(byId::containsKey), pendingAdds)
            onPendingAddsConsumed()
        }
    }

    // The step the preview shows, by id so reordering keeps it: the cover's while it plays this draft (followed as it
    // moves on), or the one tapped last. A tap shows here at once, without waiting for the cover to report it.
    var shownId by rememberSaveable { mutableStateOf<String?>(null) }
    val coverStepId = coverStep?.takeIf { it.first == id }?.second
    LaunchedEffect(coverStepId) { if (coverStepId != null) shownId = coverStepId }
    val shownIndex = shownStepIndex(items.map { it.id }, shownId)
    val shownStep = items.getOrNull(shownIndex)

    /** Shows [step] in the preview and, while the cover plays this group, on the cover. */
    fun showStep(step: CoverItem) {
        shownId = step.id
        onShowOnCover(id, step.id)
    }

    fun showBeside(forward: Boolean) {
        items.getOrNull(stepBeside(shownIndex, items.size, forward))?.let(::showStep)
    }

    val full = items.size >= CoverItem.Group.MAX_ITEMS
    val scrollState = rememberScrollState()
    val removeLabel = stringResource(R.string.group_remove)
    val showLabel = stringResource(R.string.group_step_show)
    val untilEnd = stringResource(R.string.group_until_end)
    val stepLabel = durationLabel(stepSeconds)
    val atLeastStep = stringResource(R.string.group_at_least, stepLabel)
    val estimate = GroupTiming.estimate(item)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val stepRemovedLabel = stringResource(R.string.group_step_removed)
    val undoLabel = stringResource(R.string.action_undo)

    /** Takes [step] out of the group; the snackbar's Undo puts it back where it was. */
    fun removeStep(step: CoverItem) {
        val index = itemIds.indexOf(step.id)
        if (index < 0) return
        itemIds = itemIds - step.id
        scope.launch {
            // The newest removal is the one worth undoing; an older snackbar would only hold it in the queue.
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(stepRemovedLabel, actionLabel = undoLabel, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) itemIds = withStepRestored(itemIds, step.id, index)
        }
    }

    Scaffold(
        topBar = {
            EditorTopBar(
                title = stringResource(if (initial == null) R.string.group_title_new else R.string.group_title_edit),
                onBack = onBack,
                onSave = { onSave(item) },
                saveEnabled = canSave,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            var previewPaused by remember { mutableStateOf(false) }
            CoverPreviewFrame(
                Modifier
                    .fillMaxWidth(0.72f)
                    .align(Alignment.CenterHorizontally)
                    .clickable { previewPaused = !previewPaused },
            ) {
                // Live preview: videos and animations play right inside the group editor preview
                key(shownStep?.id, previewPaused) {
                    CoverScale {
                        CoverContent(
                            item = shownStep ?: item,
                            modifier = Modifier.fillMaxSize(),
                            playback = if (previewPaused) Playback.PAUSED else Playback.LIVE,
                        )
                    }
                }
            }
            // "Adım 2/4": the shown step's row, the one marked in the list. "1 içerik" while there is nothing to step.
            val count = if (items.size > 1) {
                stringResource(R.string.cover_group_step, shownIndex + 1, items.size)
            } else {
                pluralStringResource(R.plurals.group_items, items.size, items.size)
            }
            val canStep = items.size > 1
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The step above or below in the list, on the cover too, without waiting for it to come round.
                IconButton(onClick = { showBeside(forward = false) }, enabled = canStep) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.action_previous_step))
                }
                Text(
                    // How long one round takes: "4 items · about 2 min 10 s", or "at least" while a length is unknown.
                    text = if (items.isEmpty()) {
                        count
                    } else {
                        stringResource(
                            if (estimate.atLeast) R.string.group_total_at_least else R.string.group_total_about,
                            count,
                            totalDurationLabel(estimate.ms),
                        )
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { showBeside(forward = true) }, enabled = canStep) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.action_next_step))
                }
            }

            SectionCard {
                Text(stringResource(R.string.group_order), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                if (items.isEmpty()) {
                    HintText(stringResource(R.string.group_empty))
                } else {
                    ReorderableList(
                        items = items,
                        itemKey = { it.id },
                        // Positions are those of the items shown, so move within exactly those.
                        onMove = { from, to -> itemIds = items.map { it.id }.toMutableList().apply { add(to, removeAt(from)) } },
                        scrollState = scrollState,
                        actions = { step -> listOf(CustomAccessibilityAction(removeLabel) { removeStep(step); true }) },
                        // The row's tap and state, on the same TalkBack element as its name and its actions.
                        rowSemantics = { step ->
                            selected = step.id == shownStep?.id
                            onClick(label = showLabel) {
                                showStep(step)
                                true
                            }
                        },
                    ) { step ->
                        val video = step is CoverItem.Media && step.kind == MediaKind.VIDEO
                        StepRow(
                            step = step,
                            shown = step.id == shownStep?.id,
                            // Opens this step on the cover (and in the preview), so any step can be checked at once.
                            onShow = { showStep(step) },
                            timing = when {
                                // A video playing to its end stays its own length (the Playback card says "to the end");
                                // cut, it stays the photo time.
                                video && videosToEnd -> (step as CoverItem.Media).durationMs.takeIf { it > 0 }
                                    ?.let(::clipLengthLabel)
                                    ?: untilEnd
                                video -> stepLabel
                                // GIFs, marquees, animations and collages finish their pass after the photo time.
                                GroupTiming.isCycleBased(step) -> atLeastStep
                                else -> stepLabel
                            },
                            onRemove = { removeStep(step) },
                        )
                    }
                }
                if (items.size < MIN_ITEMS) HintText(stringResource(R.string.group_need_two))
                if (full) HintText(pluralStringResource(R.plurals.group_full, CoverItem.Group.MAX_ITEMS, CoverItem.Group.MAX_ITEMS))
                // Without a playlist of their own, the steps leave the cover's rotation and play only here.
                if (!hasPlaylist && items.isNotEmpty()) HintText(stringResource(R.string.group_steps_only_in_group))
                Spacer(Modifier.height(12.dp))
                // One above the other, full width: side by side, "Galeriden ekle" was cut at larger font sizes.
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = { picking = true },
                        enabled = choices.isNotEmpty() || items.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Checklist, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.group_choose))
                    }
                    OutlinedButton(
                        onClick = { onAddFromGallery(roomLeft(items.size)) },
                        enabled = !full,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.group_from_gallery))
                    }
                }
            }

            SectionCard {
                Text(stringResource(R.string.group_playback), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                FieldLabel(stringResource(R.string.playback_photo_time))
                Spacer(Modifier.height(6.dp))
                ChoiceChips(options = STEP_OPTIONS, selected = stepSeconds, label = { durationLabel(it) }, onSelect = { stepSeconds = it })
                HintText(stringResource(R.string.group_photo_time_hint))
                Spacer(Modifier.height(12.dp))
                FieldLabel(stringResource(R.string.playback_videos))
                Spacer(Modifier.height(6.dp))
                VideosToEndChoice(toEnd = videosToEnd, onChange = { videosToEnd = it })
                Spacer(Modifier.height(8.dp))
                SwitchRow(
                    title = stringResource(R.string.group_shuffle),
                    description = stringResource(R.string.group_shuffle_desc),
                    checked = shuffle,
                    onCheckedChange = { shuffle = it },
                    icon = Icons.Filled.Shuffle,
                )
                SwitchRow(
                    title = stringResource(R.string.group_repeat),
                    description = stringResource(R.string.group_repeat_desc),
                    checked = repeat,
                    onCheckedChange = { repeat = it },
                    icon = Icons.Filled.Repeat,
                )
                SwitchRow(
                    title = stringResource(R.string.group_crossfade),
                    description = stringResource(R.string.group_crossfade_desc),
                    checked = crossfade,
                    onCheckedChange = { crossfade = it },
                    icon = Icons.Filled.Gradient,
                )
            }

            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= ContentRepository.MAX_NAME) name = it },
                label = { Text(stringResource(R.string.field_name)) },
                placeholder = { Text(fallbackName) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (picking) {
        ItemPickerSheet(
            title = stringResource(R.string.group_pick_title),
            hint = stringResource(R.string.group_pick_hint),
            current = items,
            choices = choices,
            maxCount = CoverItem.Group.MAX_ITEMS,
            onCommit = { picked ->
                itemIds = picked.map { it.id }
                picking = false
            },
        )
    }
}

/**
 * One step: drag handle, thumbnail, name, type and how long it stays, and a ⋮ menu (move up, move down, remove). The
 * moves and the removal sit in the menu so the name keeps its room and a slightly wrong tap cannot remove a step.
 * A tap on the row calls [onShow] (TalkBack finds that tap on the whole row, see ReorderableList's rowSemantics); the
 * row the preview (and the cover) shows is [shown]: filled and outlined, so it stands out by more than its colour.
 */
@Composable
private fun ReorderableRowScope.StepRow(step: CoverItem, shown: Boolean, timing: String, onShow: () -> Unit, onRemove: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    val currentOnShow by rememberUpdatedState(onShow)
    // Lifted by a long-press to drag: letting go without moving puts it back, it does not open the step.
    val lifted by rememberUpdatedState(isDragging)
    val presses = remember { MutableInteractionSource() }
    val shape = MaterialTheme.shapes.small
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DragHandle(Modifier.padding(end = 4.dp))
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(shape)
                .then(
                    if (shown) {
                        Modifier
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .border(2.dp, MaterialTheme.colorScheme.primary, shape)
                    } else {
                        Modifier
                    },
                )
                .indication(presses, ripple())
                // A plain tap with a ripple; its semantics are the row's own.
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = { offset ->
                            coroutineScope {
                                // Like a button in a scrolling screen, the ripple waits a moment: a scroll that starts
                                // on a row does not flash it, a quick tap still shows it.
                                val press = PressInteraction.Press(offset)
                                val shown = launch {
                                    delay(ViewConfiguration.getTapTimeout().toLong())
                                    presses.emit(press)
                                }
                                val released = tryAwaitRelease()
                                if (shown.isActive) {
                                    shown.cancel()
                                    if (released) {
                                        presses.emit(press)
                                        presses.emit(PressInteraction.Release(press))
                                    }
                                } else {
                                    presses.emit(if (released) PressInteraction.Release(press) else PressInteraction.Cancel(press))
                                }
                            }
                        },
                        onTap = { if (!lifted) currentOnShow() },
                    )
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(width = 52.dp, height = 50.dp)
                    .clip(RoundedCornerShape(8.dp)),
            ) {
                CoverScale { CoverContent(item = step, modifier = Modifier.fillMaxSize(), playback = Playback.STILL) }
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 12.dp, end = 4.dp),
            ) {
                Text(step.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = stringResource(R.string.group_item_timing, stringResource(step.badgeLabel()), timing),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    // Two lines: the timing is what this row is for, and large fonts must not cut it.
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.group_step_menu))
            }
            DropdownMenu(
                // Kept open, but not drawn as the small window (a menu is a window of its own).
                expanded = menuOpen && !LocalHovering.current,
                onDismissRequest = { menuOpen = false },
                modifier = Modifier.wakesStandby(),
            ) {
                if (canMoveUp) {
                    StepMenuItem(Icons.Filled.KeyboardArrowUp, R.string.reorder_move_up) {
                        menuOpen = false
                        moveUp()
                    }
                }
                if (canMoveDown) {
                    StepMenuItem(Icons.Filled.KeyboardArrowDown, R.string.reorder_move_down) {
                        menuOpen = false
                        moveDown()
                    }
                }
                StepMenuItem(Icons.Filled.Close, R.string.group_remove) {
                    menuOpen = false
                    onRemove()
                }
            }
        }
    }
}

@Composable
private fun StepMenuItem(icon: ImageVector, @StringRes text: Int, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(text)) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
    )
}
