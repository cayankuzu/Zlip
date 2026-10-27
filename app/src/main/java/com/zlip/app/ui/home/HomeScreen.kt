package com.zlip.app.ui.home

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistRemove
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zlip.app.R
import com.zlip.app.cover.CoverError
import com.zlip.app.cover.CoverStatus
import com.zlip.app.cover.coverHoverSupported
import com.zlip.app.cover.mirror.MirrorState
import com.zlip.app.data.AdvanceMode
import com.zlip.app.data.CoverItem
import com.zlip.app.data.CoverSettings
import com.zlip.app.data.MediaKind
import com.zlip.app.data.showsQr
import com.zlip.app.render.CoverContent
import com.zlip.app.render.LedStyle
import com.zlip.app.render.ledMatrix
import com.zlip.app.render.Playback
import com.zlip.app.ui.components.AutoShowControl
import com.zlip.app.ui.components.CoverPreviewFrame
import com.zlip.app.ui.components.DragHandle
import com.zlip.app.ui.components.GradientButton
import com.zlip.app.ui.components.HintText
import com.zlip.app.ui.components.IconBadge
import com.zlip.app.ui.components.LocalHovering
import com.zlip.app.ui.components.MirrorFitChips
import com.zlip.app.ui.components.MoveButtons
import com.zlip.app.ui.components.ReorderableList
import com.zlip.app.ui.components.ReorderableRowScope
import com.zlip.app.ui.components.SectionCard
import com.zlip.app.ui.components.SectionTitle
import com.zlip.app.ui.components.StatusPill
import com.zlip.app.ui.components.badgeLabel
import com.zlip.app.ui.components.label
import com.zlip.app.ui.components.miniCoverSupported
import com.zlip.app.ui.components.wakesStandby
import com.zlip.app.ui.theme.BrandBlue
import com.zlip.app.ui.theme.BrandGradient
import com.zlip.app.ui.theme.BrandMint
import androidx.compose.ui.graphics.Brush as GradientBrush

/** What the add sheet offers; [GALLERY] picks one or several photos and videos. */
enum class AddKind { GALLERY, PIXEL, TEXT, COLLAGE, GROUP, COUNTDOWN, QR, TOY }

enum class LibraryFilter(@StringRes val label: Int) {
    ALL(R.string.filter_all),
    PLAYLIST(R.string.filter_playlist),
    PIXEL(R.string.badge_pixel),
    PHOTO(R.string.filter_photo),
    VIDEO(R.string.badge_video),
    TEXT(R.string.badge_text),
    COLLAGE(R.string.badge_collage),
    GROUP(R.string.badge_group),
    TOOLS(R.string.filter_tools),
    ;

    fun accepts(item: CoverItem, playlist: List<String>): Boolean = when (this) {
        ALL -> true
        PLAYLIST -> item.id in playlist
        PIXEL -> item is CoverItem.PixelArt
        PHOTO -> item is CoverItem.Media && item.kind != MediaKind.VIDEO
        VIDEO -> item is CoverItem.Media && item.kind == MediaKind.VIDEO
        TEXT -> item is CoverItem.Text
        COLLAGE -> item is CoverItem.Collage
        GROUP -> item is CoverItem.Group
        TOOLS -> item is CoverItem.Countdown || item is CoverItem.Qr || item is CoverItem.Toy
    }
}

/** Everything the library can do with items, bundled to keep [HomeScreen]'s signature readable. */
class LibraryActions(
    val onSelect: (String) -> Unit,
    val onEdit: (CoverItem) -> Unit,
    val onDelete: (Set<String>) -> Unit,
    val onAddToPlaylist: (Set<String>) -> Unit,
    val onRemoveFromPlaylist: (Set<String>) -> Unit,
    val onMoveInPlaylist: (id: String, delta: Int) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    items: List<CoverItem>,
    playlist: List<String>,
    selectedId: String?,
    status: CoverStatus,
    settings: CoverSettings,
    paused: Boolean,
    mirror: MirrorState,
    lastError: CoverError?,
    heroPlayback: Playback,
    widgetPlaced: Boolean,
    gridState: LazyGridState,
    library: LibraryActions,
    loaded: Boolean,
    filter: LibraryFilter,
    onFilterChange: (LibraryFilter) -> Unit,
    onStartCover: () -> Unit,
    onStopCover: () -> Unit,
    onMirrorChange: (Boolean) -> Unit,
    onTogglePause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSettingsChange: (CoverSettings) -> Unit,
    onAdd: (AddKind) -> Unit,
    onOpenSettings: () -> Unit,
) {
    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    var showSortSheet by rememberSaveable { mutableStateOf(false) }
    // The playlist chip disappears with the last playlist item; never strand the user on an empty filter.
    val activeFilter = if (filter == LibraryFilter.PLAYLIST && playlist.isEmpty()) LibraryFilter.ALL else filter
    // Make the fallback stick, so refilling the playlist does not jump the grid away from "All".
    LaunchedEffect(loaded, playlist.isEmpty()) {
        if (loaded && filter == LibraryFilter.PLAYLIST && playlist.isEmpty()) onFilterChange(LibraryFilter.ALL)
    }
    var pendingDelete by remember { mutableStateOf<Set<String>?>(null) }
    var openMenuId by remember { mutableStateOf<String?>(null) }
    // Multi-select: long-press a card to start, tap cards to add/remove, then act on all of them.
    var checked by rememberSaveable { mutableStateOf(listOf<String>()) }
    val selecting = checked.isNotEmpty()
    val selectedItem = items.firstOrNull { it.id == selectedId } ?: items.firstOrNull()
    val visibleItems = if (activeFilter == LibraryFilter.PLAYLIST) {
        playlist.mapNotNull { id -> items.firstOrNull { it.id == id } }
    } else {
        items.filter { activeFilter.accepts(it, playlist) }
    }
    val haptics = LocalHapticFeedback.current
    // Sheets and dialogs are windows of their own: as the small window they stay away, still open for later.
    val hovering = LocalHovering.current
    val context = LocalContext.current
    val miniCover = remember(context) { miniCoverSupported(context) }

    BackHandler(enabled = selecting) { checked = emptyList() }
    // Drop ids that no longer exist (deleted elsewhere) so the selection never points at nothing. Not
    // before the library has loaded, or a selection restored after process death would be wiped.
    LaunchedEffect(items, loaded) {
        if (loaded) checked = checked.filter { id -> items.any { it.id == id } }
    }
    // A card's menu belongs to its card; drop it if the card disappears, so it cannot pop up again later.
    LaunchedEffect(visibleItems, openMenuId) {
        if (openMenuId != null && visibleItems.none { it.id == openMenuId }) openMenuId = null
    }
    // Nothing left to put in order: the sheet goes.
    LaunchedEffect(loaded, playlist.size) {
        if (loaded && playlist.size < 2) showSortSheet = false
    }

    fun toggleChecked(id: String) {
        checked = if (id in checked) checked - id else checked + id
    }

    Scaffold(
        topBar = {
            if (selecting) {
                SelectionBar(
                    count = checked.size,
                    allInPlaylist = checked.all { it in playlist },
                    onClose = { checked = emptyList() },
                    onSelectAll = { checked = visibleItems.map { it.id } },
                    onAddToPlaylist = {
                        library.onAddToPlaylist(checked.toSet())
                        checked = emptyList()
                    },
                    onRemoveFromPlaylist = {
                        library.onRemoveFromPlaylist(checked.toSet())
                        checked = emptyList()
                    },
                    onDelete = { pendingDelete = checked.toSet() },
                )
            } else {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(R.drawable.brand_tile),
                                contentDescription = null,
                                modifier = Modifier.size(34.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
                        }
                    },
                    actions = {
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title))
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (!selecting) {
                ExtendedFloatingActionButton(
                    onClick = { showAddSheet = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.home_add)) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { padding ->
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = 150.dp),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 104.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "hero") {
                HeroSection(
                    item = selectedItem,
                    loaded = loaded,
                    playback = heroPlayback,
                    led = LedStyle.of(settings).takeUnless { selectedItem?.showsQr() == true },
                    status = status,
                    lastError = lastError,
                    mirror = mirror,
                    staysOnElsewhere = miniCover && settings.keepCoverInOtherApps,
                    onStart = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onStartCover()
                    },
                    onStop = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onStopCover()
                    },
                    onMirrorChange = onMirrorChange,
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }, key = "controls") {
                AnimatedVisibility(
                    visible = status == CoverStatus.ACTIVE,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    // While mirroring there is no playlist to steer, only how the screen fills the cover.
                    if (mirror == MirrorState.ON) {
                        MirrorControlsCard(settings, onSettingsChange)
                    } else {
                        ControlsCard(
                            settings = settings,
                            paused = paused,
                            position = positionLabel(items, playlist, selectedItem),
                            onTogglePause = onTogglePause,
                            onNext = onNext,
                            onPrevious = onPrevious,
                            onSettingsChange = onSettingsChange,
                        )
                    }
                }
            }
            // The folded cover's one switch: whether the screen saver comes up on every wake.
            if (loaded) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "folded") {
                    SectionCard {
                        AutoShowControl(settings, onSettingsChange)
                    }
                }
            }
            if (loaded && !widgetPlaced && !settings.widgetTipDismissed && items.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "widget-tip") {
                    WidgetTipCard(
                        onHow = onOpenSettings,
                        onDismiss = { onSettingsChange(settings.copy(widgetTipDismissed = true)) },
                    )
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }, key = "library-title") {
                Column {
                    SectionTitle(R.string.home_library, modifier = Modifier.padding(top = 8.dp)) {
                        Text(
                            text = if (playlist.isEmpty()) {
                                items.size.toString()
                            } else {
                                pluralStringResource(R.plurals.playlist_count, playlist.size, playlist.size)
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        // Drag-and-drop ordering lives in its own sheet: long-press on a card selects.
                        if (activeFilter == LibraryFilter.PLAYLIST && playlist.size >= 2 && !selecting) {
                            TextButton(onClick = { showSortSheet = true }, modifier = Modifier.padding(start = 4.dp)) {
                                Icon(Icons.Filled.SwapVert, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.home_sort))
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        LibraryFilter.entries.forEach { entry ->
                            if (entry == LibraryFilter.PLAYLIST && playlist.isEmpty()) return@forEach
                            // A type the library does not have yet is only clutter (unless it is the one in use).
                            val typeFilter = entry != LibraryFilter.ALL && entry != LibraryFilter.PLAYLIST
                            if (typeFilter && entry != activeFilter && items.none { entry.accepts(it, playlist) }) return@forEach
                            FilterChip(
                                selected = activeFilter == entry,
                                onClick = { onFilterChange(entry) },
                                label = { Text(stringResource(entry.label)) },
                                leadingIcon = if (entry == LibraryFilter.PLAYLIST) {
                                    { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                    if (playlist.isEmpty() && items.size >= 2 && !selecting) {
                        HintText(stringResource(R.string.home_playlist_tip))
                    }
                }
            }
            // Not before the library has loaded: an empty grid then only means "not read yet".
            if (loaded && visibleItems.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                    Text(
                        text = stringResource(if (items.isEmpty()) R.string.home_library_empty else R.string.home_filter_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                    )
                }
            }
            items(visibleItems, key = { it.id }) { item ->
                val playlistIndex = playlist.indexOf(item.id)
                LibraryCard(
                    item = item,
                    onCover = item.id == selectedItem?.id,
                    selectionMode = selecting,
                    checked = item.id in checked,
                    playlistNumber = if (playlistIndex >= 0) playlistIndex + 1 else null,
                    reorderable = activeFilter == LibraryFilter.PLAYLIST,
                    canMoveEarlier = playlistIndex > 0,
                    canMoveLater = playlistIndex in 0 until playlist.lastIndex,
                    menuOpen = openMenuId == item.id,
                    onMenuOpenChange = { open -> openMenuId = if (open) item.id else null },
                    onClick = {
                        if (selecting) {
                            toggleChecked(item.id)
                        } else {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            library.onSelect(item.id)
                        }
                    },
                    // combinedClickable already gives the long-press haptic.
                    onLongClick = { toggleChecked(item.id) },
                    onEdit = { library.onEdit(item) },
                    onTogglePlaylist = {
                        if (playlistIndex >= 0) library.onRemoveFromPlaylist(setOf(item.id)) else library.onAddToPlaylist(setOf(item.id))
                    },
                    onMove = { delta -> library.onMoveInPlaylist(item.id, delta) },
                    onDelete = { pendingDelete = setOf(item.id) },
                )
            }
        }
    }

    if (showAddSheet && !hovering) {
        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false },
            modifier = Modifier.wakesStandby(),
            // Opens fully: half a sheet hid the last row of choices.
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Text(
                text = stringResource(R.string.home_add_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            val pick: (AddKind) -> Unit = { kind ->
                showAddSheet = false
                onAdd(kind)
            }
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp),
            ) {
                AddSection(R.string.add_section_media) {
                    AddTile(Icons.Filled.PhotoLibrary, R.string.home_add_gallery, R.string.home_add_gallery_desc) { pick(AddKind.GALLERY) }
                }
                AddSection(R.string.add_section_create) {
                    AddTile(Icons.Filled.Brush, R.string.type_pixel, R.string.home_add_pixel_desc) { pick(AddKind.PIXEL) }
                    AddTile(Icons.Filled.TextFields, R.string.type_text, R.string.home_add_text_desc) { pick(AddKind.TEXT) }
                    AddTile(Icons.Filled.Dashboard, R.string.type_collage, R.string.home_add_collage_desc) { pick(AddKind.COLLAGE) }
                    AddTile(Icons.Filled.ViewCarousel, R.string.type_group, R.string.home_add_group_desc) { pick(AddKind.GROUP) }
                }
                AddSection(R.string.add_section_smart) {
                    AddTile(Icons.Filled.HourglassTop, R.string.type_countdown, R.string.home_add_countdown_desc) { pick(AddKind.COUNTDOWN) }
                    AddTile(Icons.Filled.QrCode2, R.string.type_qr, R.string.home_add_qr_desc) { pick(AddKind.QR) }
                    AddTile(Icons.Filled.Casino, R.string.type_toy, R.string.home_add_toy_desc) { pick(AddKind.TOY) }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }

    if (showSortSheet && !hovering) {
        PlaylistSortSheet(
            items = playlist.mapNotNull { id -> items.firstOrNull { it.id == id } },
            onMove = library.onMoveInPlaylist,
            onDismiss = { showSortSheet = false },
        )
    }

    pendingDelete?.takeUnless { hovering }?.let { ids ->
        val single = ids.singleOrNull()?.let { id -> items.firstOrNull { it.id == id } }
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            modifier = Modifier.wakesStandby(),
            title = { Text(stringResource(R.string.home_delete_title)) },
            text = {
                Text(
                    if (single != null) {
                        stringResource(R.string.home_delete_body, single.name)
                    } else {
                        pluralStringResource(R.plurals.home_delete_many, ids.size, ids.size)
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    checked = emptyList()
                    library.onDelete(ids)
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/** "2 / 5" for the item on the cover within what auto-advance moves through, or null if not useful. */
private fun positionLabel(items: List<CoverItem>, playlist: List<String>, current: CoverItem?): String? {
    val sequence = if (playlist.isEmpty()) items.map { it.id } else playlist
    val index = sequence.indexOf(current?.id)
    return if (index >= 0 && sequence.size > 1) "${index + 1} / ${sequence.size}" else null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionBar(
    count: Int,
    allInPlaylist: Boolean,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onRemoveFromPlaylist: () -> Unit,
    onDelete: () -> Unit,
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_cancel))
            }
        },
        title = { Text(pluralStringResource(R.plurals.selection_count, count, count)) },
        actions = {
            IconButton(onClick = onSelectAll) {
                Icon(Icons.Filled.SelectAll, contentDescription = stringResource(R.string.action_select_all))
            }
            if (allInPlaylist) {
                IconButton(onClick = onRemoveFromPlaylist) {
                    Icon(Icons.Filled.PlaylistRemove, contentDescription = stringResource(R.string.action_playlist_remove))
                }
            } else {
                IconButton(onClick = onAddToPlaylist) {
                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = stringResource(R.string.action_playlist_add))
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
            }
        },
    )
}

@Composable
private fun HeroSection(
    item: CoverItem?,
    loaded: Boolean,
    playback: Playback,
    led: LedStyle?,
    status: CoverStatus,
    lastError: CoverError?,
    mirror: MirrorState,
    staysOnElsewhere: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onMirrorChange: (Boolean) -> Unit,
) {
    val active = status == CoverStatus.ACTIVE
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CoverPreviewFrame(Modifier.fillMaxWidth(0.74f), glowing = active) {
            // Still while the cover is on (the real thing is on the back), in standby and in the background.
            CoverContent(item = item, modifier = Modifier.fillMaxSize().ledMatrix(led), playback = playback)
        }
        Spacer(Modifier.height(16.dp))
        val (label, color) = when (status) {
            CoverStatus.ACTIVE -> R.string.status_active_title to BrandMint
            CoverStatus.AVAILABLE -> R.string.status_available_title to BrandBlue
            CoverStatus.UNAVAILABLE -> R.string.status_unavailable_title to Color(0xFFFFC861)
            CoverStatus.UNSUPPORTED -> R.string.status_unsupported_title to MaterialTheme.colorScheme.error
            CoverStatus.CHECKING -> R.string.status_checking_title to MaterialTheme.colorScheme.onSurfaceVariant
        }
        StatusPill(stringResource(label), color)
        Spacer(Modifier.height(10.dp))
        Text(
            // Blank (but keeping its line) until the library is read, rather than a wrong "nothing selected".
            text = if (loaded) item?.name ?: stringResource(R.string.home_nothing_selected) else "",
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val body = when (status) {
            CoverStatus.ACTIVE -> when {
                mirror == MirrorState.ON -> if (staysOnElsewhere) R.string.mirror_active_body else R.string.mirror_active_body_leave
                // Only where the Mini kapak exists and is on does the cover outlive leaving Zlip.
                staysOnElsewhere -> R.string.status_active_body
                else -> R.string.status_active_body_leave
            }
            CoverStatus.AVAILABLE -> R.string.status_available_body
            CoverStatus.UNAVAILABLE -> R.string.status_unavailable_body
            CoverStatus.UNSUPPORTED -> R.string.status_unsupported_body
            CoverStatus.CHECKING -> R.string.status_checking_body
        }
        Text(
            text = stringResource(body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
        if (lastError != null && !active) {
            Text(
                text = stringResource(if (lastError == CoverError.START_REJECTED) R.string.error_start_rejected else R.string.error_session_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(10.dp))
        when (status) {
            CoverStatus.AVAILABLE -> GradientButton(
                text = stringResource(R.string.home_cover_start),
                onClick = onStart,
                icon = Icons.Filled.PlayArrow,
                modifier = Modifier.fillMaxWidth(0.8f),
            )
            CoverStatus.ACTIVE -> OutlinedButton(
                onClick = onStop,
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(52.dp),
            ) {
                Icon(Icons.Filled.PowerSettingsNew, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.home_cover_stop), style = MaterialTheme.typography.titleMedium)
            }
            else -> Unit
        }
        // What the cover shows: the playlist, or the inner screen mirrored. Only where the cover can stay on
        // in other apps; otherwise a mirror could only ever show Zlip itself.
        if (coverHoverSupported && (status == CoverStatus.AVAILABLE || active)) {
            Spacer(Modifier.height(14.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth(0.8f)) {
                SegmentedButton(
                    selected = mirror == MirrorState.OFF,
                    onClick = { onMirrorChange(false) },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) { Text(stringResource(R.string.mirror_mode_content)) }
                SegmentedButton(
                    selected = mirror != MirrorState.OFF,
                    onClick = { onMirrorChange(true) },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) { Text(stringResource(R.string.mirror_mode_screen)) }
            }
            if (mirror != MirrorState.ON) {
                Text(
                    text = stringResource(R.string.mirror_consent_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/** The controls card while mirroring: how the screen fills the cover, and the cover's brightness. */
@Composable
private fun MirrorControlsCard(settings: CoverSettings, onSettingsChange: (CoverSettings) -> Unit) {
    SectionCard {
        Text(
            text = stringResource(R.string.mirror_fit_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        MirrorFitChips(selected = settings.mirrorFit, onSelect = { onSettingsChange(settings.copy(mirrorFit = it)) })
        HintText(stringResource(R.string.mirror_cover_gestures))
        Spacer(Modifier.height(8.dp))
        BrightnessRow(settings, onSettingsChange)
    }
}

@Composable
private fun ControlsCard(
    settings: CoverSettings,
    paused: Boolean,
    position: String?,
    onTogglePause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSettingsChange: (CoverSettings) -> Unit,
) {
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrevious) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = stringResource(R.string.action_previous))
            }
            FilledTonalIconButton(onClick = onTogglePause, modifier = Modifier.size(56.dp)) {
                Icon(
                    imageVector = if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    contentDescription = stringResource(if (paused) R.string.action_resume else R.string.action_pause),
                )
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.action_next))
            }
        }
        if (position != null) {
            Text(
                text = position,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(8.dp))
        BrightnessRow(settings, onSettingsChange)
        Spacer(Modifier.height(8.dp))
        // Quick auto-advance: mode chips plus shuffle/repeat toggles; details live in Settings.
        Text(
            text = stringResource(R.string.advance_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AdvanceMode.entries.forEach { mode ->
                FilterChip(
                    selected = settings.advanceMode == mode,
                    onClick = { onSettingsChange(settings.copy(advanceMode = mode)) },
                    label = { Text(stringResource(mode.label())) },
                )
            }
            if (settings.advanceMode != AdvanceMode.OFF) {
                IconToggleButton(
                    checked = settings.shuffle,
                    onCheckedChange = { onSettingsChange(settings.copy(shuffle = it)) },
                ) {
                    Icon(
                        Icons.Filled.Shuffle,
                        contentDescription = stringResource(R.string.advance_shuffle),
                        tint = if (settings.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconToggleButton(
                    checked = settings.repeat,
                    onCheckedChange = { onSettingsChange(settings.copy(repeat = it)) },
                ) {
                    Icon(
                        Icons.Filled.Repeat,
                        contentDescription = stringResource(R.string.advance_repeat),
                        tint = if (settings.repeat) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Cover brightness: dragging updates the cover live; the file is saved once after the drag (debounced). */
@Composable
fun BrightnessRow(settings: CoverSettings, onSettingsChange: (CoverSettings) -> Unit) {
    var value by remember(settings.brightness) { mutableFloatStateOf(settings.brightness) }
    val label = stringResource(R.string.settings_brightness)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.BrightnessLow, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(
            value = value,
            onValueChange = {
                value = it
                onSettingsChange(settings.copy(brightness = it))
            },
            valueRange = CoverSettings.MIN_BRIGHTNESS..1f,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
                .semantics { contentDescription = label },
        )
        Icon(Icons.Filled.BrightnessHigh, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryCard(
    item: CoverItem,
    onCover: Boolean,
    selectionMode: Boolean,
    checked: Boolean,
    playlistNumber: Int?,
    reorderable: Boolean,
    canMoveEarlier: Boolean,
    canMoveLater: Boolean,
    menuOpen: Boolean,
    onMenuOpenChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onEdit: () -> Unit,
    onTogglePlaylist: () -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    val highlighted = if (selectionMode) checked else onCover
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (selectionMode && checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        border = if (highlighted) {
            BorderStroke(2.dp, GradientBrush.linearGradient(BrandGradient))
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
        modifier = Modifier
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(
                role = if (selectionMode) Role.Checkbox else Role.Button,
                onClickLabel = if (selectionMode) null else stringResource(R.string.action_show_on_cover),
                onLongClickLabel = if (selectionMode) null else stringResource(R.string.action_select),
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .then(if (selectionMode) Modifier.semantics { toggleableState = ToggleableState(checked) } else Modifier),
    ) {
        Column {
            Box {
                CoverPreviewFrame(
                    Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                ) {
                    CoverContent(item = item, modifier = Modifier.fillMaxSize(), playback = Playback.STILL)
                }
                TypeBadge(
                    item = item,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(14.dp),
                )
                when {
                    selectionMode -> Icon(
                        imageVector = if (checked) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (checked) BrandMint else Color.White,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50)),
                    )
                    onCover -> Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = stringResource(R.string.home_on_cover),
                        tint = BrandMint,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50)),
                    )
                }
                if (playlistNumber != null) {
                    val badgeDescription = stringResource(R.string.playlist_position, playlistNumber)
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .clearAndSetSemantics { contentDescription = badgeDescription }
                            .padding(14.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null, tint = BrandMint, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("$playlistNumber", style = MaterialTheme.typography.labelSmall, color = Color.White)
                    }
                }
            }
            Row(
                modifier = Modifier.padding(start = 14.dp, end = 2.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (!selectionMode) {
                    Box {
                        IconButton(onClick = { onMenuOpenChange(true) }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.home_item_menu, item.name))
                        }
                        DropdownMenu(
                            // Kept open, but not drawn as the small window (a menu is a window of its own).
                            expanded = menuOpen && !LocalHovering.current,
                            onDismissRequest = { onMenuOpenChange(false) },
                            modifier = Modifier.wakesStandby(),
                        ) {
                            MenuItem(Icons.Filled.Edit, R.string.action_edit) {
                                onMenuOpenChange(false)
                                onEdit()
                            }
                            if (playlistNumber == null) {
                                MenuItem(Icons.AutoMirrored.Filled.PlaylistAdd, R.string.action_playlist_add) {
                                    onMenuOpenChange(false)
                                    onTogglePlaylist()
                                }
                            } else {
                                MenuItem(Icons.Filled.PlaylistRemove, R.string.action_playlist_remove) {
                                    onMenuOpenChange(false)
                                    onTogglePlaylist()
                                }
                            }
                            if (reorderable && canMoveEarlier) {
                                MenuItem(Icons.AutoMirrored.Filled.ArrowBack, R.string.action_move_earlier) {
                                    onMenuOpenChange(false)
                                    onMove(-1)
                                }
                            }
                            if (reorderable && canMoveLater) {
                                MenuItem(Icons.AutoMirrored.Filled.ArrowForward, R.string.action_move_later) {
                                    onMenuOpenChange(false)
                                    onMove(+1)
                                }
                            }
                            MenuItem(Icons.Filled.Delete, R.string.action_delete) {
                                onMenuOpenChange(false)
                                onDelete()
                            }
                        }
                    }
                } else {
                    Spacer(Modifier.height(48.dp))
                }
            }
        }
    }
}

@Composable
private fun MenuItem(icon: ImageVector, @StringRes text: Int, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(text)) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
    )
}

/** "Show it when folded too": how to place the cover widget, until it is placed or the tip is closed for good. */
@Composable
private fun WidgetTipCard(onHow: () -> Unit, onDismiss: () -> Unit) {
    SectionCard {
        Row(verticalAlignment = Alignment.Top) {
            IconBadge(Icons.Filled.Widgets)
            Spacer(Modifier.width(14.dp))
            Column(
                Modifier
                    .weight(1f)
                    .padding(top = 2.dp),
            ) {
                Text(stringResource(R.string.home_widget_tip_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(R.string.home_widget_tip_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.home_widget_tip_dismiss))
            }
        }
        TextButton(onClick = onHow, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.home_widget_tip_action))
        }
    }
}

/**
 * Puts the playlist in order: drag a row by its handle (or long-press it), tap its arrows, or send it to
 * the top or bottom from its menu; TalkBack offers all of these as actions. [onMove] takes an item's id and
 * how many places it moves.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistSortSheet(items: List<CoverItem>, onMove: (id: String, delta: Int) -> Unit, onDismiss: () -> Unit) {
    val scrollState = rememberScrollState()
    val toTopLabel = stringResource(R.string.sort_to_top)
    val toBottomLabel = stringResource(R.string.sort_to_bottom)
    // Further than the list is long: the repository stops at the first or last place.
    val toTop = { item: CoverItem -> onMove(item.id, -items.size) }
    val toBottom = { item: CoverItem -> onMove(item.id, items.size) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.wakesStandby(),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Text(
            text = stringResource(R.string.sort_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        HintText(stringResource(R.string.sort_hint), modifier = Modifier.padding(horizontal = 24.dp))
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(scrollState)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 8.dp, bottom = 24.dp),
        ) {
            ReorderableList(
                items = items,
                itemKey = { it.id },
                onMove = { from, to -> onMove(items[from].id, to - from) },
                scrollState = scrollState,
                actions = { item ->
                    val index = items.indexOf(item)
                    buildList {
                        if (index > 0) add(CustomAccessibilityAction(toTopLabel) { toTop(item); true })
                        if (index < items.lastIndex) add(CustomAccessibilityAction(toBottomLabel) { toBottom(item); true })
                    }
                },
            ) { item ->
                SortRow(item = item, onToTop = { toTop(item) }, onToBottom = { toBottom(item) })
            }
        }
    }
}

/** One playlist entry in [PlaylistSortSheet]: handle, thumbnail, name and type, arrows and a top/bottom menu. */
@Composable
private fun ReorderableRowScope.SortRow(item: CoverItem, onToTop: () -> Unit, onToBottom: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
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
            CoverContent(item = item, modifier = Modifier.fillMaxSize(), playback = Playback.STILL)
        }
        Column(
            Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(item.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = stringResource(item.badgeLabel()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        MoveButtons()
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.home_item_menu, item.name))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, modifier = Modifier.wakesStandby()) {
                if (canMoveUp) {
                    MenuItem(Icons.Filled.VerticalAlignTop, R.string.sort_to_top) {
                        menuOpen = false
                        onToTop()
                    }
                }
                if (canMoveDown) {
                    MenuItem(Icons.Filled.VerticalAlignBottom, R.string.sort_to_bottom) {
                        menuOpen = false
                        onToBottom()
                    }
                }
            }
        }
    }
}

@Composable
private fun TypeBadge(item: CoverItem, modifier: Modifier = Modifier) {
    val label = stringResource(item.badgeLabel())
    Text(
        // "Grup · 4": how many steps a group plays.
        text = if (item is CoverItem.Group) stringResource(R.string.badge_group_count, label, item.items.size) else label,
        style = MaterialTheme.typography.labelSmall,
        color = Color.White,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** A titled section of add tiles, two per row; a section's only tile spans the row. */
@Composable
private fun AddSection(@StringRes title: Int, tiles: @Composable AddSectionScope.() -> Unit) {
    val scope = AddSectionScope().apply { tiles() }
    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 8.dp),
    )
    scope.tiles.chunked(2).forEach { row ->
        // Both tiles of a row share the taller one's height.
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .height(IntrinsicSize.Min)
                .padding(bottom = 10.dp),
        ) {
            row.forEach { tile -> Box(Modifier.weight(1f).fillMaxHeight()) { tile() } }
            if (row.size == 1 && scope.tiles.size > 1) Spacer(Modifier.weight(1f))
        }
    }
}

/** Collects tiles so [AddSection] can lay them out in rows. */
private class AddSectionScope {
    val tiles = mutableListOf<@Composable () -> Unit>()

    @Suppress("FunctionName")
    fun AddTile(icon: ImageVector, @StringRes title: Int, @StringRes description: Int, onClick: () -> Unit) {
        tiles += { AddTileContent(icon, title, description, onClick) }
    }
}

@Composable
private fun AddTileContent(icon: ImageVector, @StringRes title: Int, @StringRes description: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .heightIn(min = 124.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            IconBadge(icon, size = 40.dp)
            Spacer(Modifier.height(10.dp))
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
            // Never cut: Turkish descriptions run longer than the English ones.
            Text(
                text = stringResource(description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
