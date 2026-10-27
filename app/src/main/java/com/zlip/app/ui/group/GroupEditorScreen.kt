package com.zlip.app.ui.group

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Gradient
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
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
import com.zlip.app.render.PlaybackEvents
import com.zlip.app.ui.components.ChoiceChips
import com.zlip.app.ui.components.CoverPreviewFrame
import com.zlip.app.ui.components.CoverScale
import com.zlip.app.ui.components.DragHandle
import com.zlip.app.ui.components.FieldLabel
import com.zlip.app.ui.components.HintText
import com.zlip.app.ui.components.ItemPickerSheet
import com.zlip.app.ui.components.MoveButtons
import com.zlip.app.ui.components.ReorderableList
import com.zlip.app.ui.components.ReorderableRowScope
import com.zlip.app.ui.components.SectionCard
import com.zlip.app.ui.components.SwitchRow
import com.zlip.app.ui.components.VideosToEndChoice
import com.zlip.app.ui.components.badgeLabel
import com.zlip.app.ui.components.clipLengthLabel
import com.zlip.app.ui.components.durationLabel
import com.zlip.app.ui.components.totalDurationLabel

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
 * callback, so files picked while the activity was recreated still reach the group.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupEditorScreen(
    initial: CoverItem.Group?,
    prefill: List<String>,
    library: List<CoverItem>,
    previewPlayback: Playback,
    onAddFromGallery: (room: Int) -> Unit,
    pendingAdds: List<String>?,
    onPendingAddsConsumed: () -> Unit,
    onDraftChange: (CoverItem) -> Unit,
    onDirtyChange: (dirty: Boolean, canSave: Boolean) -> Unit,
    onSave: (CoverItem.Group) -> Unit,
    onBack: () -> Unit,
) {
    val id = rememberSaveable { initial?.id ?: ContentRepository.newId() }
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
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
    val fallbackName = stringResource(R.string.type_group)

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
    val baseline = remember { start.copy(id = id, name = start.name.ifBlank { fallbackName }) }
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

    // The preview reports the step it shows, for the "2/4" under it.
    var shownStep by remember { mutableIntStateOf(0) }
    val previewEvents = remember {
        object : PlaybackEvents {
            override fun onCycle() = Unit

            override fun onEnded(ended: Boolean) = Unit

            override fun onFailed(failed: Boolean) = Unit

            override fun onStep(child: CoverItem) {
                shownStep = itemIds.indexOf(child.id)
            }
        }
    }

    val full = items.size >= CoverItem.Group.MAX_ITEMS
    val scrollState = rememberScrollState()
    val removeLabel = stringResource(R.string.group_remove)
    val untilEnd = stringResource(R.string.group_until_end)
    val stepLabel = durationLabel(stepSeconds)
    val atLeastStep = stringResource(R.string.group_at_least, stepLabel)
    val estimate = GroupTiming.estimate(item)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (initial == null) R.string.group_title_new else R.string.group_title_edit)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { onSave(item) }, enabled = canSave) {
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
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CoverPreviewFrame(
                Modifier
                    .fillMaxWidth(0.72f)
                    .align(Alignment.CenterHorizontally),
            ) {
                CoverScale { CoverContent(item = item, modifier = Modifier.fillMaxSize(), playback = previewPlayback, events = previewEvents) }
            }
            val count = if (previewPlayback == Playback.LIVE && items.size > 1 && shownStep >= 0) {
                "${shownStep + 1}/${items.size}"
            } else {
                pluralStringResource(R.plurals.group_items, items.size, items.size)
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
                modifier = Modifier.fillMaxWidth(),
            )

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
                        actions = { step -> listOf(CustomAccessibilityAction(removeLabel) { itemIds = itemIds - step.id; true }) },
                    ) { step ->
                        val video = step is CoverItem.Media && step.kind == MediaKind.VIDEO
                        StepRow(
                            step = step,
                            timing = when {
                                // A video playing to its end stays its own length; cut, it stays the photo time.
                                video && videosToEnd -> (step as CoverItem.Media).durationMs.takeIf { it > 0 }
                                    ?.let { stringResource(R.string.group_clip_to_end, clipLengthLabel(it)) }
                                    ?: untilEnd
                                video -> stepLabel
                                // GIFs, marquees, animations and collages finish their pass after the photo time.
                                GroupTiming.isCycleBased(step) -> atLeastStep
                                else -> stepLabel
                            },
                            onRemove = { itemIds = itemIds - step.id },
                        )
                    }
                }
                if (items.size < MIN_ITEMS) HintText(stringResource(R.string.group_need_two))
                if (full) HintText(pluralStringResource(R.plurals.group_full, CoverItem.Group.MAX_ITEMS, CoverItem.Group.MAX_ITEMS))
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(onClick = { picking = true }, enabled = choices.isNotEmpty() || items.isNotEmpty(), modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Checklist, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.group_choose), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = { onAddFromGallery(roomLeft(items.size)) },
                        enabled = !full,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.group_from_gallery), maxLines = 1, overflow = TextOverflow.Ellipsis)
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

/** One step: thumbnail, name, type and how long it stays; drag handle, move buttons and remove. */
@Composable
private fun ReorderableRowScope.StepRow(step: CoverItem, timing: String, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DragHandle(Modifier.padding(end = 8.dp))
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
                .padding(start = 12.dp),
        ) {
            Text(step.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = stringResource(R.string.group_item_timing, stringResource(step.badgeLabel()), timing),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        MoveButtons()
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.group_remove))
        }
    }
}
