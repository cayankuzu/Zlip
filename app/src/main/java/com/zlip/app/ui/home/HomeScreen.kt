package com.zlip.app.ui.home

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.ScreenShare
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureInPictureAlt
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
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zlip.app.R
import com.zlip.app.cover.CoverError
import com.zlip.app.cover.CoverStatus
import com.zlip.app.cover.GroupProgress
import com.zlip.app.cover.coverHoverSupported
import com.zlip.app.cover.mirror.MirrorState
import com.zlip.app.data.CoverItem
import com.zlip.app.data.CoverSettings
import com.zlip.app.data.MediaKind
import com.zlip.app.data.showsQr
import com.zlip.app.render.CoverContent
import com.zlip.app.render.LedStyle
import com.zlip.app.render.LocalVideoProgressSink
import com.zlip.app.render.LocalVideoSyncPosition
import com.zlip.app.render.VideoProgressSink
import com.zlip.app.render.ledMatrix
import com.zlip.app.render.Playback
import com.zlip.app.system.AppUpdateManager
import com.zlip.app.system.UpdateState
import com.zlip.app.ui.gallery.InAppGalleryViewer
import kotlinx.coroutines.flow.MutableSharedFlow
import com.zlip.app.ui.components.AutoShowControl
import com.zlip.app.ui.components.CoverPreviewFrame
import com.zlip.app.ui.components.CoverScale
import com.zlip.app.ui.components.DragHandle
import com.zlip.app.ui.components.GradientButton
import com.zlip.app.ui.components.HintText
import com.zlip.app.ui.components.IconBadge
import com.zlip.app.ui.components.LocalHovering
import com.zlip.app.ui.components.MediaPlayerBar
import com.zlip.app.ui.components.MirrorViewControls
import com.zlip.app.ui.components.MoveButtons
import com.zlip.app.ui.components.ReorderableList
import com.zlip.app.ui.components.ReorderableRowScope
import com.zlip.app.ui.components.SectionCard
import com.zlip.app.ui.components.SectionTitle
import com.zlip.app.ui.components.SegmentLabel
import com.zlip.app.ui.components.StatusPill
import com.zlip.app.ui.components.VideoSeekBar
import com.zlip.app.ui.components.badgeLabel
import com.zlip.app.ui.components.durationLabel
import com.zlip.app.ui.components.miniCoverSupported
import com.zlip.app.ui.components.wakesStandby
import com.zlip.app.ui.theme.BrandBlue
import com.zlip.app.ui.theme.BrandGradient
import com.zlip.app.ui.theme.BrandMint
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Brush as GradientBrush

/**
 * Room under the last row for the "Add" button: its 56 dp height, the 16 dp the Scaffold keeps under it and a
 * 32 dp gap, so scrolled to the end no control (the brightness slider's end included) is left under the button.
 */
private val FAB_CLEARANCE = 56.dp + 16.dp + 32.dp

/** How close the brightness slider may come to the "Add" button before the button shrinks to its "+". */
private val FAB_SLIDER_MARGIN = 8.dp

/** Room the brightness slider leaves at its end while the round "+" floats over its row: the button and a margin. */
private val SLIDER_FAB_ROOM = 56.dp + 16.dp

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
    val onClearPlaylist: () -> Unit,
    /** Makes the playlist exactly these ids, in order: how a change to it is undone. */
    val onSetPlaylist: (List<String>) -> Unit,
)

/**
 * A message for Home's snackbar from outside it ("Added to your library"), shown once: Home reports it taken with
 * `onSnackTaken`. [onAction] runs when its [actionLabel] (if any) is tapped.
 */
class HomeSnack(val message: String, val actionLabel: String?, val onAction: () -> Unit = {})

/**
 * Home: the cover's preview and switch, its controls while it is on, and the library. [rotation] is what the cover
 * moves through; [shownOnCover] what it shows right now (a group's current step); [groupProgress] where a group on the
 * cover is, and [itemSkip] whether the rotation has another item to leave it for.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    items: List<CoverItem>,
    playlist: List<String>,
    rotation: List<CoverItem>,
    selectedId: String?,
    shownOnCover: CoverItem?,
    groupProgress: GroupProgress?,
    itemSkip: Boolean,
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
    snack: HomeSnack?,
    onSnackTaken: () -> Unit,
    onStartCover: () -> Unit,
    onStopCover: () -> Unit,
    onShrinkToMini: () -> Unit,
    onMirrorChange: (Boolean) -> Unit,
    onTogglePause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onNextItem: () -> Unit,
    onPreviousItem: () -> Unit,
    onSettingsChange: (CoverSettings) -> Unit,
    onAdd: (AddKind) -> Unit,
    onOpenSettings: () -> Unit,
    videoPositionMs: Long = 0L,
    videoDurationMs: Long = 0L,
    onSeek: (Long) -> Unit = {},
) {
    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    var showSortSheet by rememberSaveable { mutableStateOf(false) }
    var showFoldedSteps by rememberSaveable { mutableStateOf(false) }
    var galleryViewerItemId by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    // Not the composition's own effects: a snackbar must outlive the state change that started it.
    val snackScope = rememberCoroutineScope()

    /** Shows [message] now (the newest one is the one worth acting on), with [action] behind [actionLabel]. */
    fun showSnack(message: String, actionLabel: String? = null, action: () -> Unit = {}) {
        snackScope.launch {
            snackbar.currentSnackbarData?.dismiss()
            // With an action there is something to read and decide, so it stays longer.
            val result = snackbar.showSnackbar(
                message = message,
                actionLabel = actionLabel,
                withDismissAction = actionLabel == null,
                duration = if (actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) action()
        }
    }
    LaunchedEffect(snack) {
        val incoming = snack ?: return@LaunchedEffect
        onSnackTaken()
        showSnack(incoming.message, incoming.actionLabel, incoming.onAction)
    }
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
    val resources = LocalResources.current
    val miniCover = remember(context) { miniCoverSupported(context) }
    val undoLabel = stringResource(R.string.action_undo)

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

    // The "Add" button floats over the list. Wide at the top when the cover is off, where it introduces itself;
    // a round "+" once the list is scrolled or the active controls card is on screen, so it never overlaps buttons.
    val fabExpanded by remember(status) { derivedStateOf { gridState.firstVisibleItemIndex == 0 && status != CoverStatus.ACTIVE } }
    val updateState by AppUpdateManager.state.collectAsState()
    var updateDismissed by rememberSaveable { mutableStateOf(false) }
    var sliderBounds by remember { mutableStateOf<Rect?>(null) }
    // Kept while the button is hidden (it has no bounds then), so hiding it cannot by itself bring it back.
    var fabBounds by remember { mutableStateOf<Rect?>(null) }
    val fabMargin = with(LocalDensity.current) { FAB_SLIDER_MARGIN.toPx() }
    val fabOverSlider = fabCoversSlider(fabBounds, sliderBounds, fabMargin)
    val reportSlider: (Rect) -> Unit = remember { { bounds -> sliderBounds = bounds } }
    val addLabel = stringResource(R.string.home_add)

    // Changing the playlist changes what the cover rotates, so each change says so, and can be taken back.
    fun addToPlaylist(ids: Set<String>) {
        val added = ids.count { it !in playlist }
        library.onAddToPlaylist(ids)
        if (added > 0) {
            showSnack(
                message = resources.getQuantityString(R.plurals.home_list_added, added, added),
                actionLabel = resources.getString(R.string.home_list_show),
                action = { onFilterChange(LibraryFilter.PLAYLIST) },
            )
        }
    }

    fun removeFromPlaylist(ids: Set<String>) {
        val before = playlist
        val removed = ids.count { it in before }
        library.onRemoveFromPlaylist(ids)
        if (removed > 0) {
            showSnack(resources.getQuantityString(R.plurals.home_list_removed, removed, removed), undoLabel) { library.onSetPlaylist(before) }
        }
    }

    fun clearPlaylist() {
        val before = playlist
        library.onClearPlaylist()
        showSnack(resources.getString(R.string.home_list_cleared), undoLabel) { library.onSetPlaylist(before) }
    }

    // The sort sheet is a window of its own over Home: while it is open its snackbars (removing a row, with Undo)
    // show inside it, and one still running when it closes carries on here.
    val sortSheetShown = showSortSheet && !hovering
    Scaffold(
        snackbarHost = { if (!sortSheetShown) SnackbarHost(snackbar) },
        topBar = {
            if (selecting) {
                SelectionBar(
                    count = checked.size,
                    allInPlaylist = checked.all { it in playlist },
                    onClose = { checked = emptyList() },
                    onSelectAll = { checked = visibleItems.map { it.id } },
                    onAddToPlaylist = {
                        addToPlaylist(checked.toSet())
                        checked = emptyList()
                    },
                    onRemoveFromPlaylist = {
                        removeFromPlaylist(checked.toSet())
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
            // Measured outside the animation: scaling it away must not change where the button is said to be.
            Box(
                Modifier.onGloballyPositioned { coordinates ->
                    coordinates.boundsInRoot().takeUnless { it.isEmpty }?.let { fabBounds = it }
                },
            ) {
                AnimatedVisibility(
                    visible = !selecting,
                    enter = scaleIn() + fadeIn(),
                    exit = scaleOut() + fadeOut(),
                ) {
                    ExtendedFloatingActionButton(
                        onClick = { showAddSheet = true },
                        expanded = fabExpanded && !fabOverSlider,
                        // Collapsed there is no text left, so the icon carries the label for TalkBack.
                        icon = { Icon(Icons.Filled.Add, contentDescription = if (fabExpanded && !fabOverSlider) null else addLabel) },
                        text = { Text(addLabel) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        },
    ) { padding ->
        val layoutDirection = LocalLayoutDirection.current
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = 150.dp),
            // Clear of a side navigation bar (landscape), and at the end of the list of the "Add" button, which sits
            // above the navigation bar that the bottom padding already holds.
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(layoutDirection) + 16.dp,
                end = padding.calculateEndPadding(layoutDirection) + 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + FAB_CLEARANCE,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            val showUpdateBanner = !updateDismissed && (
                updateState is UpdateState.Available ||
                    updateState is UpdateState.Downloading ||
                    updateState is UpdateState.ReadyToInstall
                )
            if (showUpdateBanner) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "update-banner") {
                    UpdateBannerCard(
                        state = updateState,
                        onUpdateNow = { info -> snackScope.launch { AppUpdateManager.downloadAndInstall(context, info) } },
                        onInstall = { file -> AppUpdateManager.installApk(context, file) },
                        onDismiss = { updateDismissed = true },
                    )
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }, key = "hero") {
                // With the cover on, exactly what it shows: a group's current step, not always its first.
                val previewItem = if (status == CoverStatus.ACTIVE) shownOnCover ?: selectedItem else selectedItem
                val position = rotationPosition(rotation.map { it.id }, selectedItem?.id)
                val positionLabel = position?.let { (at, of) ->
                    stringResource(if (playlist.isEmpty()) R.string.home_position_all else R.string.home_position_list, at, of)
                }
                val activeStepLabel = groupProgress?.takeIf { status == CoverStatus.ACTIVE && it.groupId == selectedItem?.id }?.let { progress ->
                    stringResource(R.string.cover_group_step, progress.step, progress.count)
                }
                val heroSubtitle = listOfNotNull(activeStepLabel, positionLabel).joinToString(" · ").ifEmpty { null }
                HeroSection(
                    item = previewItem,
                    title = selectedItem?.name,
                    subtitle = heroSubtitle,
                    loaded = loaded,
                    playback = heroPlayback,
                    led = LedStyle.of(settings).takeUnless { previewItem?.showsQr() == true },
                    status = status,
                    lastError = lastError,
                    mirror = mirror,
                    staysOnElsewhere = miniCover && settings.keepCoverInOtherApps,
                    videoPositionMs = videoPositionMs,
                    videoDurationMs = videoDurationMs,
                    onSeek = onSeek,
                    onTogglePause = onTogglePause,
                    paused = paused,
                    onOpenGallery = { galleryViewerItemId = selectedItem?.id },
                    onStart = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onStartCover()
                    },
                    onStop = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onStopCover()
                    },
                    onShrinkToMini = onShrinkToMini,
                    onMirrorChange = onMirrorChange,
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }, key = "controls") {
                AnimatedVisibility(
                    visible = status == CoverStatus.ACTIVE,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    // Scrolled out of the list or closed with the cover: no slider for the "Add" button to avoid.
                    DisposableEffect(Unit) { onDispose { sliderBounds = null } }
                    // While mirroring there is no playlist to steer, only how the screen fills the cover.
                    if (mirror == MirrorState.ON) {
                        MirrorControlsCard(settings, onSettingsChange, onBrightnessBounds = reportSlider, sliderEndRoom = fabOverSlider)
                    } else {
                        val isGroupActive = groupProgress?.takeIf { it.groupId == selectedItem?.id } != null
                        ControlsCard(
                            settings = settings,
                            paused = paused,
                            stepping = isGroupActive,
                            itemSkip = itemSkip,
                            onTogglePause = onTogglePause,
                            onNext = onNext,
                            onPrevious = onPrevious,
                            onNextItem = onNextItem,
                            onPreviousItem = onPreviousItem,
                            onSettingsChange = onSettingsChange,
                            onBrightnessBounds = reportSlider,
                            sliderEndRoom = fabOverSlider,
                            videoPositionMs = videoPositionMs,
                            videoDurationMs = videoDurationMs,
                            onSeek = onSeek,
                        )
                    }
                }
            }
            if (loaded && !widgetPlaced && !settings.widgetTipDismissed && items.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "widget-tip") {
                    WidgetTipCard(
                        // The steps right here: Settings opened at its top, far from them.
                        onHow = { showFoldedSteps = true },
                        onDismiss = { onSettingsChange(settings.copy(widgetTipDismissed = true)) },
                    )
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }, key = "library-title") {
                Column {
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.home_library),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = if (playlist.isEmpty()) {
                                items.size.toString()
                            } else {
                                pluralStringResource(R.plurals.playlist_count, playlist.size, playlist.size)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .padding(horizontal = 10.dp, vertical = 3.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    // Wraps rather than scrolls (as ChoiceChips does): a sideways row hid its last chips with nothing to
                    // show they were there.
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
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
                    // The list's own actions, on a row of their own under the chips: next to the title and count they
                    // squeezed "Kitaplık" onto two lines at large font sizes.
                    if (activeFilter == LibraryFilter.PLAYLIST && playlist.isNotEmpty() && !selecting) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Drag-and-drop ordering lives in its own sheet: long-press on a card selects.
                            if (playlist.size >= 2) {
                                TextButton(onClick = { showSortSheet = true }) {
                                    Icon(Icons.Filled.SwapVert, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.home_sort))
                                }
                            }
                            // One tap back to "the whole library rotates", with Undo.
                            TextButton(onClick = ::clearPlaylist) {
                                Icon(Icons.Filled.ClearAll, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.home_list_clear))
                            }
                        }
                    }
                    if (playlist.isEmpty() && items.size >= 2 && !selecting && !settings.playlistTipDismissed) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            HintText(stringResource(R.string.home_playlist_tip), Modifier.weight(1f))
                            IconButton(onClick = { onSettingsChange(settings.copy(playlistTipDismissed = true)) }) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.home_widget_tip_dismiss),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
            // Not before the library has loaded: an empty grid then only means "not read yet".
            if (loaded && visibleItems.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(if (items.isEmpty()) R.string.home_library_empty else R.string.home_filter_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        // Nothing at all yet: the two ways in, right here rather than a pointer to a button elsewhere.
                        if (items.isEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { onAdd(AddKind.GALLERY) }, modifier = Modifier.fillMaxWidth(0.8f)) {
                                Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.home_empty_gallery))
                            }
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = { showAddSheet = true }, modifier = Modifier.fillMaxWidth(0.8f)) {
                                Icon(Icons.Filled.Brush, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.home_empty_create))
                            }
                        }
                    }
                }
            }
            items(visibleItems, key = { it.id }) { item ->
                val playlistIndex = playlist.indexOf(item.id)
                val isSelectedOnCover = item.id == selectedItem?.id
                val isCoverActiveNow = status == CoverStatus.ACTIVE
                LibraryCard(
                    item = item,
                    previewItem = if (isSelectedOnCover && isCoverActiveNow && shownOnCover != null) shownOnCover else item,
                    activeGroupProgress = groupProgress?.takeIf { isSelectedOnCover && isCoverActiveNow && it.groupId == item.id },
                    onCover = isSelectedOnCover,
                    coverOn = isCoverActiveNow,
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
                            // Picked, but nothing shows it: the cover is off (and the hero scrolled away). Say so, and
                            // offer to turn it on with this item.
                            if (status == CoverStatus.AVAILABLE) {
                                showSnack(
                                    message = resources.getString(R.string.home_cover_off_snack),
                                    actionLabel = resources.getString(R.string.home_cover_on_action),
                                    action = onStartCover,
                                )
                            }
                        }
                    },
                    // combinedClickable already gives the long-press haptic.
                    onLongClick = { toggleChecked(item.id) },
                    onEdit = { library.onEdit(item) },
                    onTogglePlaylist = {
                        if (playlistIndex >= 0) removeFromPlaylist(setOf(item.id)) else addToPlaylist(setOf(item.id))
                    },
                    onMove = { delta -> library.onMoveInPlaylist(item.id, delta) },
                    onDelete = { pendingDelete = setOf(item.id) },
                    onOpenGallery = { galleryViewerItemId = item.id },
                )
            }
        }
    }

    galleryViewerItemId?.let { initialId ->
        val galleryItems = if (visibleItems.any { it.id == initialId }) visibleItems else items
        InAppGalleryViewer(
            items = galleryItems,
            initialItemId = initialId,
            isCoverActive = status == CoverStatus.ACTIVE,
            coverPlayingId = selectedItem?.id,
            shownOnCover = shownOnCover,
            groupProgress = groupProgress,
            onPreviousStep = onPrevious,
            onNextStep = onNext,
            coverPositionMs = videoPositionMs,
            coverDurationMs = videoDurationMs,
            onCoverSeek = onSeek,
            onToggleCoverPause = onTogglePause,
            isCoverPaused = paused,
            onSelectCover = { id ->
                library.onSelect(id)
                galleryViewerItemId = null
            },
            onEdit = { item ->
                galleryViewerItemId = null
                library.onEdit(item)
            },
            onDelete = { item ->
                pendingDelete = setOf(item.id)
                galleryViewerItemId = null
            },
            onDismiss = { galleryViewerItemId = null },
        )
    }

    if (showAddSheet && !hovering) {
        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false },
            modifier = Modifier.wakesStandby(),
            // Opens fully: half a sheet hid the last row of choices.
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            SheetTitle(R.string.home_add_title, onDone = { showAddSheet = false })
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

    if (sortSheetShown) {
        PlaylistSortSheet(
            items = playlist.mapNotNull { id -> items.firstOrNull { it.id == id } },
            activeItemId = selectedItem?.id.takeIf { status == CoverStatus.ACTIVE },
            shownOnCover = shownOnCover,
            onMove = library.onMoveInPlaylist,
            onRemove = { id -> removeFromPlaylist(setOf(id)) },
            onDismiss = { showSortSheet = false },
            snackbar = snackbar,
        )
    }

    // "Nasıl?" on the widget tip: the three steps right here, with a way out.
    if (showFoldedSteps && !hovering) {
        ModalBottomSheet(
            onDismissRequest = { showFoldedSteps = false },
            modifier = Modifier.wakesStandby(),
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            SheetTitle(R.string.home_widget_tip_title, onDone = { showFoldedSteps = false })
            Text(
                text = stringResource(R.string.folded_steps),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 32.dp),
            )
        }
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

/**
 * Whether the "Add" button, at [fab], comes within [margin] px of the brightness slider at [slider] (both in root
 * coordinates). A slider scrolled out of view is clipped to an empty rectangle, and never counts.
 */
internal fun fabCoversSlider(fab: Rect?, slider: Rect?, margin: Float): Boolean {
    if (fab == null || slider == null || slider.isEmpty) return false
    return fab.inflate(margin).overlaps(slider)
}

/** Tells [onBounds] (if any) where this element is, in root coordinates, whenever that may have changed. */
private fun Modifier.reportBounds(onBounds: ((Rect) -> Unit)?): Modifier =
    if (onBounds == null) this else onGloballyPositioned { onBounds(it.boundsInRoot()) }

/**
 * Where [current] is in the cover's [rotation] (ids, in order), as (1-based place, count), or null when that says
 * nothing: it is not in the rotation, or the rotation has only one item.
 */
internal fun rotationPosition(rotation: List<String>, current: String?): Pair<Int, Int>? {
    val index = rotation.indexOf(current)
    return if (index >= 0 && rotation.size > 1) index + 1 to rotation.size else null
}

/** A sheet's title with a visible way out on the right: not everyone knows a sheet closes with a swipe. */
@Composable
private fun SheetTitle(@StringRes title: Int, onDone: () -> Unit) {
    Row(
        modifier = Modifier.padding(start = 24.dp, end = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onDone) { Text(stringResource(R.string.action_done)) }
    }
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
        title = { Text(pluralStringResource(R.plurals.selection_count, count, count), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        actions = {
            IconButton(onClick = onSelectAll) {
                Icon(Icons.Filled.SelectAll, contentDescription = stringResource(R.string.action_select_all))
            }
            // What selecting is mostly for, so it is a labelled button rather than one more icon.
            FilledTonalButton(
                onClick = if (allInPlaylist) onRemoveFromPlaylist else onAddToPlaylist,
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            ) {
                Icon(
                    imageVector = if (allInPlaylist) Icons.Filled.PlaylistRemove else Icons.AutoMirrored.Filled.PlaylistAdd,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(
                    text = stringResource(if (allInPlaylist) R.string.action_playlist_remove else R.string.action_playlist_add),
                    maxLines = 1,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
            }
        },
    )
}

/**
 * The cover's preview, state and switch. [item] is what the preview shows (with the cover on, what the cover shows);
 * [title] names the item on the cover (a group's name while it shows one of its steps). While mirroring, the cover
 * shows the inner screen, so the preview shows a mirroring sign instead.
 */
@Composable
private fun HeroSection(
    item: CoverItem?,
    title: String?,
    subtitle: String? = null,
    loaded: Boolean,
    playback: Playback,
    led: LedStyle?,
    status: CoverStatus,
    lastError: CoverError?,
    mirror: MirrorState,
    staysOnElsewhere: Boolean,
    videoPositionMs: Long = 0L,
    videoDurationMs: Long = 0L,
    onSeek: (Long) -> Unit = {},
    onTogglePause: () -> Unit = {},
    paused: Boolean = false,
    onOpenGallery: () -> Unit = {},
    onStart: () -> Unit,
    onStop: () -> Unit,
    onShrinkToMini: () -> Unit,
    onMirrorChange: (Boolean) -> Unit,
) {
    val active = status == CoverStatus.ACTIVE
    val mirroring = active && mirror == MirrorState.ON
    val isVideo = (item as? CoverItem.Media)?.kind == MediaKind.VIDEO || item is CoverItem.Group
    val localSeekFlow = remember(item?.id) { MutableSharedFlow<Long>(extraBufferCapacity = 2) }
    var localPosMs by remember(item?.id) { mutableLongStateOf(0L) }
    var localDurMs by remember(item?.id) { mutableLongStateOf(0L) }
    var localPaused by remember(item?.id) { mutableStateOf(false) }

    val localProgressSink = remember(item?.id) {
        VideoProgressSink(
            onProgress = { pos, dur ->
                localPosMs = pos
                localDurMs = dur
            },
            seekFlow = localSeekFlow,
        )
    }

    val displayPosMs = if (active) videoPositionMs else localPosMs
    val displayDurMs = (if (active) videoDurationMs else localDurMs)
        .takeIf { it > 0L }
        ?: (item as? CoverItem.Media)?.takeIf { it.kind == MediaKind.VIDEO }?.durationMs
        ?: 0L
    val isPaused = if (active) paused else localPaused
    val effectivePlayback = when {
        playback == Playback.STILL -> Playback.STILL
        isPaused -> Playback.PAUSED
        else -> Playback.LIVE
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CoverPreviewFrame(
            modifier = Modifier
                .fillMaxWidth(0.66f)
                .clickable { onOpenGallery() },
            glowing = active,
        ) {
            if (mirroring) {
                Icon(
                    Icons.AutoMirrored.Filled.ScreenShare,
                    contentDescription = null,
                    tint = BrandMint,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxSize(0.34f),
                )
            } else {
                val syncPos = if (active) videoPositionMs else null
                val progressSink = if (active) null else localProgressSink
                CompositionLocalProvider(
                    LocalVideoSyncPosition provides syncPos,
                    LocalVideoProgressSink provides progressSink,
                ) {
                    CoverScale { CoverContent(item = item, modifier = Modifier.fillMaxSize().ledMatrix(led), playback = effectivePlayback) }
                }
                if (isPaused && (active || isVideo)) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(48.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = stringResource(R.string.action_resume),
                            tint = Color.White,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }

            // Visual affordance badge for opening full-screen gallery (frame itself handles the click)
            Surface(
                color = Color.Black.copy(alpha = 0.55f),
                shape = CircleShape,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(32.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Fullscreen,
                        contentDescription = stringResource(R.string.gallery_open),
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        // Dedicated media player bar below the preview when cover is off and a video is shown
        if (isVideo && !active && displayDurMs > 0L) {
            Spacer(Modifier.height(10.dp))
            MediaPlayerBar(
                paused = localPaused,
                onTogglePause = { localPaused = !localPaused },
                positionMs = displayPosMs,
                durationMs = displayDurMs,
                onSeek = { targetMs ->
                    localSeekFlow.tryEmit(targetMs)
                    localPosMs = targetMs
                },
                modifier = Modifier.fillMaxWidth(0.84f),
            )
        }

        Spacer(Modifier.height(12.dp))
        val (label, color) = when (status) {
            CoverStatus.ACTIVE -> R.string.status_active_title to BrandMint
            CoverStatus.AVAILABLE -> R.string.status_available_title to BrandBlue
            CoverStatus.UNAVAILABLE -> R.string.status_unavailable_title to Color(0xFFFFC861)
            CoverStatus.UNSUPPORTED -> R.string.status_unsupported_title to MaterialTheme.colorScheme.error
            CoverStatus.CHECKING -> R.string.status_checking_title to MaterialTheme.colorScheme.onSurfaceVariant
        }
        StatusPill(stringResource(label), color)
        Spacer(Modifier.height(6.dp))
        Text(
            // Blank (but keeping its line) until the library is read, rather than a wrong "nothing selected".
            text = when {
                !loaded -> ""
                mirroring -> stringResource(R.string.cover_mirroring)
                else -> title ?: stringResource(R.string.home_nothing_selected)
            },
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (loaded && !mirroring && subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelLarge,
                color = if (active) BrandMint else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
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
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
        if (lastError != null && !active) {
            Text(
                text = stringResource(if (lastError == CoverError.START_REJECTED) R.string.error_start_rejected else R.string.error_session_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(8.dp))
        when (status) {
            CoverStatus.AVAILABLE -> GradientButton(
                text = stringResource(R.string.home_cover_start),
                onClick = onStart,
                icon = Icons.Filled.PlayArrow,
                modifier = Modifier.fillMaxWidth(0.84f),
            )
            CoverStatus.ACTIVE -> if (staysOnElsewhere) {
                Row(
                    modifier = Modifier.fillMaxWidth(0.92f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = onStop,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Filled.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.home_cover_stop),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    OutlinedButton(
                        onClick = onShrinkToMini,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Filled.PictureInPictureAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.home_shrink_mini),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            } else {
                OutlinedButton(
                    onClick = onStop,
                    modifier = Modifier
                        .fillMaxWidth(0.84f)
                        .height(48.dp),
                ) {
                    Icon(Icons.Filled.PowerSettingsNew, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.home_cover_stop), style = MaterialTheme.typography.titleMedium)
                }
            }
            else -> Unit
        }
        // What the cover shows: the playlist, or the inner screen mirrored. Only where the cover can stay on
        // in other apps; otherwise a mirror could only ever show Zlip itself.
        if (coverHoverSupported && (status == CoverStatus.AVAILABLE || active)) {
            Spacer(Modifier.height(10.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth(0.84f)) {
                // No checkmark, one shrinking line: "Ekranı yansıt" broke onto two lines at larger font sizes.
                SegmentedButton(
                    selected = mirror == MirrorState.OFF,
                    onClick = { onMirrorChange(false) },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    icon = {},
                ) { SegmentLabel(stringResource(R.string.mirror_mode_content)) }
                SegmentedButton(
                    selected = mirror != MirrorState.OFF,
                    onClick = { onMirrorChange(true) },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    icon = {},
                ) { SegmentLabel(stringResource(R.string.mirror_mode_screen)) }
            }
        }
    }
}

/** Non-intrusive top card offering 1-tap in-place OTA update when a newer GitHub release is detected. */
@Composable
private fun UpdateBannerCard(
    state: UpdateState,
    onUpdateNow: (com.zlip.app.system.UpdateInfo) -> Unit,
    onInstall: (java.io.File) -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, BrandMint.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            when (state) {
                is UpdateState.Available -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Filled.SystemUpdate, size = 36.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_update_available, state.info.versionName),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (state.info.releaseNotes.isNotBlank()) {
                                Text(
                                    text = state.info.releaseNotes.lines().firstOrNull { it.isNotBlank() }.orEmpty().removePrefix("- ").removePrefix("* "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        FilledTonalButton(
                            onClick = { onUpdateNow(state.info) },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        ) {
                            Text(stringResource(R.string.settings_update_now), style = MaterialTheme.typography.labelLarge)
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_cancel), modifier = Modifier.size(18.dp))
                        }
                    }
                }
                is UpdateState.Downloading -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Filled.SystemUpdate, size = 34.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_update_downloading, state.progressPercent),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { state.progressPercent / 100f },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                is UpdateState.ReadyToInstall -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Filled.SystemUpdate, size = 36.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.settings_update_install),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        Button(onClick = { onInstall(state.apkFile) }) {
                            Text(stringResource(R.string.settings_update_install))
                        }
                    }
                }
                else -> Unit
            }
        }
    }
}

/** The controls card while mirroring: which part of the screen the cover shows, and the cover's brightness. */
@Composable
private fun MirrorControlsCard(
    settings: CoverSettings,
    onSettingsChange: (CoverSettings) -> Unit,
    onBrightnessBounds: ((Rect) -> Unit)? = null,
    sliderEndRoom: Boolean = false,
) {
    SectionCard {
        Text(
            text = stringResource(R.string.mirror_fit_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        MirrorViewControls()
        HintText(stringResource(R.string.mirror_cover_gestures))
        Spacer(Modifier.height(8.dp))
        BrightnessRow(settings, onSettingsChange, Modifier.reportBounds(onBrightnessBounds), endRoom = sliderEndRoom)
    }
}

/**
 * The cover's transport while it plays the playlist. For a single item: ⏮ ⏯ ⏭ move between items. For a group
 * ([groupStep] is its "Tatil · Adım 3/8"): ‹ › step inside it, and with [itemSkip] a second row leaves it for the
 * previous or next item in one tap, the same two kinds of move as on the cover and in the notification.
 */
@Composable
private fun ControlsCard(
    settings: CoverSettings,
    paused: Boolean,
    stepping: Boolean,
    itemSkip: Boolean,
    onTogglePause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onNextItem: () -> Unit,
    onPreviousItem: () -> Unit,
    onSettingsChange: (CoverSettings) -> Unit,
    onBrightnessBounds: ((Rect) -> Unit)? = null,
    sliderEndRoom: Boolean = false,
    videoPositionMs: Long = 0L,
    videoDurationMs: Long = 0L,
    onSeek: (Long) -> Unit = {},
) {
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrevious) {
                if (stepping) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.action_previous_step))
                } else {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = stringResource(R.string.action_previous))
                }
            }
            FilledTonalIconButton(onClick = onTogglePause, modifier = Modifier.size(56.dp)) {
                Icon(
                    imageVector = if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    contentDescription = stringResource(if (paused) R.string.action_resume else R.string.action_pause),
                )
            }
            IconButton(onClick = onNext) {
                if (stepping) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.action_next_step))
                } else {
                    Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.action_next))
                }
            }
        }

        if (videoDurationMs > 0) {
            Spacer(Modifier.height(6.dp))
            VideoSeekBar(
                positionMs = videoPositionMs,
                durationMs = videoDurationMs,
                onSeek = onSeek,
                activeColor = MaterialTheme.colorScheme.primary,
                inactiveColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f),
                totalTimeColor = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
            )
        }
        // Out of the group in one tap; a group alone in the rotation goes round instead, so there is nowhere to go.
        if (stepping && itemSkip) {
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ItemSkipButton(Icons.Filled.SkipPrevious, R.string.action_previous_item, iconFirst = true, onClick = onPreviousItem)
                ItemSkipButton(Icons.Filled.SkipNext, R.string.action_next_item, iconFirst = false, onClick = onNextItem)
            }
        }
        Spacer(Modifier.height(8.dp))
        BrightnessRow(settings, onSettingsChange, Modifier.reportBounds(onBrightnessBounds), endRoom = sliderEndRoom)
        Spacer(Modifier.height(6.dp))
        // Single toggleable chip for auto-advance + its sub-options on the same wrapping row
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = settings.autoAdvance,
                onClick = { onSettingsChange(settings.copy(autoAdvance = !settings.autoAdvance)) },
                label = { Text(stringResource(R.string.advance_title)) },
                leadingIcon = if (settings.autoAdvance) {
                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                } else {
                    null
                },
            )
            if (settings.autoAdvance) {
                FilterChip(
                    selected = settings.shuffle,
                    onClick = { onSettingsChange(settings.copy(shuffle = !settings.shuffle)) },
                    label = { Text(stringResource(R.string.advance_shuffle)) },
                    leadingIcon = { Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) },
                )
                FilterChip(
                    selected = settings.repeat,
                    onClick = { onSettingsChange(settings.copy(repeat = !settings.repeat)) },
                    label = { Text(stringResource(R.string.advance_repeat)) },
                    leadingIcon = { Icon(Icons.Filled.Repeat, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) },
                )
                AdvanceSecondsChip(settings, onSettingsChange)
            }
        }
    }
}

/** One of the two "leave the group" buttons: the icon on the outer side, the words always whole (two lines if need be). */
@Composable
private fun RowScope.ItemSkipButton(icon: ImageVector, @StringRes label: Int, iconFirst: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight(),
    ) {
        if (iconFirst) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        }
        Text(stringResource(label), textAlign = TextAlign.Center)
        if (!iconFirst) {
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        }
    }
}

/** "Fotoğraf: 10 sn": photo time (videos play to their end), right where auto-advance is turned on; a tap lists the choices. */
@Composable
private fun AdvanceSecondsChip(settings: CoverSettings, onSettingsChange: (CoverSettings) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        AssistChip(
            onClick = { open = true },
            label = { Text(stringResource(R.string.home_advance_seconds, durationLabel(settings.advanceSeconds))) },
            leadingIcon = { Icon(Icons.Filled.Timer, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
        )
        DropdownMenu(
            // Kept open, but not drawn as the small window (a menu is a window of its own).
            expanded = open && !LocalHovering.current,
            onDismissRequest = { open = false },
            modifier = Modifier.wakesStandby(),
        ) {
            CoverSettings.ADVANCE_SECONDS_OPTIONS.forEach { seconds ->
                DropdownMenuItem(
                    text = { Text(durationLabel(seconds)) },
                    leadingIcon = if (seconds == settings.advanceSeconds) {
                        { Icon(Icons.Filled.Check, contentDescription = null) }
                    } else {
                        null
                    },
                    onClick = {
                        open = false
                        onSettingsChange(settings.copy(advanceSeconds = seconds))
                    },
                    modifier = Modifier.semantics { selected = seconds == settings.advanceSeconds },
                )
            }
        }
    }
}

/**
 * Cover brightness: dragging updates the cover live; the file is saved once after the drag (debounced). [endRoom]: the
 * "Add" button floats over the row's end, so the slider and its icon end before it (inside the row, whose bounds the
 * button's placement is judged by, so this cannot feed back into it).
 */
@Composable
fun BrightnessRow(
    settings: CoverSettings,
    onSettingsChange: (CoverSettings) -> Unit,
    modifier: Modifier = Modifier,
    endRoom: Boolean = false,
) {
    var value by remember(settings.brightness) { mutableFloatStateOf(settings.brightness) }
    val label = stringResource(R.string.settings_brightness)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
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
        if (endRoom) Spacer(Modifier.width(SLIDER_FAB_ROOM))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryCard(
    item: CoverItem,
    previewItem: CoverItem = item,
    activeGroupProgress: GroupProgress? = null,
    onCover: Boolean,
    coverOn: Boolean,
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
    onOpenGallery: () -> Unit = {},
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
                    CoverScale { CoverContent(item = previewItem, modifier = Modifier.fillMaxSize(), playback = Playback.STILL) }
                }
                TypeBadge(
                    item = item,
                    activeGroupProgress = activeGroupProgress,
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
                        // "On the cover screen" only while it really is.
                        contentDescription = stringResource(if (coverOn) R.string.home_on_cover else R.string.home_selected),
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
                            MenuItem(Icons.Filled.Collections, R.string.gallery_open) {
                                onMenuOpenChange(false)
                                onOpenGallery()
                            }
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
 * how many places it moves; [onRemove] takes an item out of the playlist (its menu). Home's [snackbar] shows
 * inside the sheet, so the Undo of a removal is seen and reached while sorting.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistSortSheet(
    items: List<CoverItem>,
    activeItemId: String? = null,
    shownOnCover: CoverItem? = null,
    onMove: (id: String, delta: Int) -> Unit,
    onRemove: (id: String) -> Unit,
    onDismiss: () -> Unit,
    snackbar: SnackbarHostState,
) {
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
        Box {
            Column {
                SheetTitle(R.string.sort_title, onDone = onDismiss)
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
                        val preview = if (item.id == activeItemId && shownOnCover != null) shownOnCover else item
                        SortRow(item = item, previewItem = preview, onToTop = { toTop(item) }, onToBottom = { toBottom(item) }, onRemove = { onRemove(item.id) })
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
        }
    }
}

/** One playlist entry in [PlaylistSortSheet]: handle, thumbnail, name and type, arrows and a top/bottom/remove menu. */
@Composable
private fun ReorderableRowScope.SortRow(
    item: CoverItem,
    previewItem: CoverItem = item,
    onToTop: () -> Unit,
    onToBottom: () -> Unit,
    onRemove: () -> Unit,
) {
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
            CoverScale { CoverContent(item = previewItem, modifier = Modifier.fillMaxSize(), playback = Playback.STILL) }
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
                MenuItem(Icons.Filled.PlaylistRemove, R.string.action_playlist_remove) {
                    menuOpen = false
                    onRemove()
                }
            }
        }
    }
}

@Composable
private fun TypeBadge(item: CoverItem, activeGroupProgress: GroupProgress? = null, modifier: Modifier = Modifier) {
    val label = stringResource(item.badgeLabel())
    val badgeText = when {
        item is CoverItem.Group && activeGroupProgress != null -> "$label · ${activeGroupProgress.step}/${activeGroupProgress.count}"
        item is CoverItem.Group -> stringResource(R.string.badge_group_count, label, item.items.size)
        else -> label
    }
    Text(
        text = badgeText,
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
