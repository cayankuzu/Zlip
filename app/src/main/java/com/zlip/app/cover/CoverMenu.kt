package com.zlip.app.cover

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zlip.app.R
import com.zlip.app.data.ContentRepository
import com.zlip.app.data.CoverItem
import com.zlip.app.data.CoverSettings
import com.zlip.app.data.MediaKind
import com.zlip.app.render.CoverContent
import com.zlip.app.render.Playback
import com.zlip.app.system.CoverGuard
import com.zlip.app.ui.components.ChoiceChips
import com.zlip.app.ui.components.CoverPreviewFrame
import com.zlip.app.ui.components.badgeLabel
import com.zlip.app.ui.theme.BrandMint
import kotlinx.coroutines.flow.drop

/**
 * The folded cover's menu, opened with a swipe up, so everything can be done without opening the phone:
 * the playlist as a paged grid of still thumbnails (tap one to show it), brightness, how long the cover
 * stays lit and quick switches. The title, position, sound and close button of what is showing stay at
 * hand too, so toys (whose taps go to the game) keep them. It covers the whole cover; a swipe down, Back or
 * picking an item closes it. Laid out for the small square cover: big targets, nothing tiny.
 *
 * Every click, switch, slider change and page turn calls [onUserActivity], since TalkBack's double tap
 * sends no touch the host could count. [open] is false while the menu slides away.
 */
@Composable
internal fun CoverMenu(
    repository: ContentRepository,
    playback: CoverPlayback,
    item: CoverItem?,
    onDismiss: () -> Unit,
    onClose: (() -> Unit)?,
    onUserActivity: () -> Unit,
    open: Boolean,
) {
    val sequence by repository.sequence.collectAsState()
    val settings by repository.settings.collectAsState()
    val soundOn by playback.soundOn.collectAsState()
    val context = LocalContext.current
    // Read each time the menu opens: the permission can only be granted on the phone.
    val canAppearOnTop = remember { CoverGuard.canAppearOnTop(context) }
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val density = LocalDensity.current
    val closeDistancePx = with(density) { CLOSE_DISTANCE.toPx() }
    val closeVelocityPx = with(density) { CLOSE_VELOCITY.toPx() }

    // How far the menu is pulled down: it follows the finger, and let go far (or fast) enough it closes.
    var pull by remember { mutableFloatStateOf(0f) }
    val release: suspend (Float) -> Unit = { velocity ->
        if (pull > closeDistancePx || velocity > closeVelocityPx) currentOnDismiss() else animate(pull, 0f) { value, _ -> pull = value }
    }
    val currentRelease by rememberUpdatedState(release)
    // Reopened while sliding away after a pull-to-close: the same composition comes back, still pulled down.
    LaunchedEffect(open) {
        if (open && pull != 0f) animate(pull, 0f) { value, _ -> pull = value }
    }
    val pullAtTop = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Pushing back up first takes back the pull.
                if (pull <= 0f || available.y >= 0f) return Offset.Zero
                val taken = maxOf(available.y, -pull)
                pull += taken
                return Offset(0f, taken)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // Pulled further down at the top of the list: the menu follows the finger.
                if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
                pull += available.y
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pull <= 0f) return Velocity.Zero
                currentRelease(available.y)
                return available
            }
        }
    }

    // While brightness is dragged the menu steps back, so the cover's dimming shows behind it.
    val brightnessTouch = remember { MutableInteractionSource() }
    val dragging by brightnessTouch.collectIsDraggedAsState()
    val backdrop by animateFloatAsState(if (dragging) 0f else 1f, label = "menu-backdrop")
    val faded = Modifier.graphicsLayer { alpha = backdrop }
    val menuTitle = stringResource(R.string.cover_menu_title)

    Column(
        Modifier
            .fillMaxSize()
            .graphicsLayer { translationY = pull }
            .background(MENU_BACKGROUND.copy(alpha = MENU_BACKGROUND.alpha * backdrop))
            // Takes every touch, so nothing under the menu answers one.
            .pointerInput(Unit) {}
            .semantics { paneTitle = menuTitle }
            .windowInsetsPadding(WindowInsets.displayCutout),
    ) {
        val index = sequence.indexOfFirst { it.id == item?.id }
        val clip = (item as? CoverItem.Media)?.takeIf { it.kind == MediaKind.VIDEO && it.hasAudio }
        MenuHeader(
            title = item?.name.orEmpty(),
            position = if (index >= 0 && sequence.size > 1) "${index + 1}/${sequence.size}" else null,
            soundOn = clip?.let { it.sound || it.id in soundOn },
            onToggleSound = {
                onUserActivity()
                clip?.let(playback::toggleSound)
            },
            onClose = onClose,
            onDismiss = onDismiss,
            modifier = faded.draggable(
                state = rememberDraggableState { delta -> pull = (pull + delta).coerceAtLeast(0f) },
                orientation = Orientation.Vertical,
                onDragStopped = { velocity -> currentRelease(velocity) },
            ),
        )
        Column(
            Modifier
                .weight(1f)
                .nestedScroll(pullAtTop)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        ) {
            if (sequence.isNotEmpty()) {
                ItemPages(
                    sequence = sequence,
                    currentId = item?.id,
                    onPick = { picked ->
                        if (picked.id != item?.id) repository.select(picked.id)
                        // Closing counts as use of the cover too (onUserActivity).
                        onDismiss()
                    },
                    onPageChange = onUserActivity,
                    modifier = faded,
                )
            }

            SectionLabel(stringResource(R.string.settings_brightness), faded)
            BrightnessSlider(
                brightness = settings.brightness,
                interactionSource = brightnessTouch,
                onChange = { value -> repository.updateSettings { it.copy(brightness = value) } },
                // Once per drag or TalkBack step, not on every move.
                onChangeFinished = onUserActivity,
            )

            Column(faded) {
                SectionLabel(stringResource(R.string.settings_stay_on))
                ChoiceChips(
                    options = stayOnChoices(settings.stayOnSeconds),
                    selected = settings.stayOnSeconds,
                    label = { stayOnLabel(it) },
                    onSelect = { seconds ->
                        onUserActivity()
                        repository.updateSettings { it.copy(stayOnSeconds = seconds) }
                    },
                )
                Spacer(Modifier.height(8.dp))
                MenuSwitch(
                    title = stringResource(R.string.cover_menu_auto_show),
                    note = if (canAppearOnTop) null else stringResource(R.string.cover_menu_allow_on_phone),
                    checked = settings.autoShowOnCover && canAppearOnTop,
                    enabled = canAppearOnTop,
                    onCheckedChange = { on ->
                        onUserActivity()
                        repository.updateSettings { it.copy(autoShowOnCover = on) }
                    },
                )
                MenuSwitch(
                    title = stringResource(R.string.cover_menu_clock),
                    checked = settings.coverClock,
                    onCheckedChange = { on ->
                        onUserActivity()
                        repository.updateSettings { it.copy(coverClock = on) }
                    },
                )
                MenuSwitch(
                    title = stringResource(R.string.cover_menu_led),
                    checked = settings.ledMatrix,
                    onCheckedChange = { on ->
                        onUserActivity()
                        repository.updateSettings { it.copy(ledMatrix = on) }
                    },
                )
            }
        }
    }
}

/** The handle (a tap or a swipe down closes the menu), then what is showing: title, position, sound, close. */
@Composable
private fun MenuHeader(
    title: String,
    position: String?,
    soundOn: Boolean?,
    onToggleSound: () -> Unit,
    onClose: (() -> Unit)?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val closeMenu = stringResource(R.string.cover_menu_close)
    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(22.dp)
                .clickable(role = Role.Button, onClick = onDismiss)
                .semantics { contentDescription = closeMenu },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(width = 40.dp, height = 4.dp)
                    .background(Color.White.copy(alpha = 0.4f), RoundedCornerShape(50)),
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 10.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (position != null) Text(position, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
            }
            if (soundOn != null) {
                MenuIconButton(
                    if (soundOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                    stringResource(if (soundOn) R.string.action_sound_off else R.string.action_sound_on),
                    onToggleSound,
                )
            }
            if (onClose != null) MenuIconButton(Icons.Filled.Close, stringResource(R.string.cover_close), onClose)
        }
    }
}

@Composable
private fun MenuIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .padding(start = 6.dp)
            .size(48.dp)
            .background(Color.White.copy(alpha = 0.1f), CircleShape),
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(24.dp))
    }
}

/**
 * The playlist in pages of [COLUMNS] x 2 still thumbnails (no decoders), opening on the page of what is
 * showing, which is marked. A group is one tile, like in the playlist.
 */
@Composable
private fun ItemPages(
    sequence: List<CoverItem>,
    currentId: String?,
    onPick: (CoverItem) -> Unit,
    onPageChange: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pages = sequence.chunked(PAGE_SIZE)
    val currentPage = (sequence.indexOfFirst { it.id == currentId } / PAGE_SIZE).coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = currentPage) { pages.size }
    // Paging (a swipe, or TalkBack's scroll action) is use of the cover too.
    val currentOnPageChange by rememberUpdatedState(onPageChange)
    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.drop(1).collect { currentOnPageChange() }
    }
    // Every page keeps the same height, so the settings below never jump while paging.
    val rows = if (sequence.size > COLUMNS) 2 else 1
    Column(modifier) {
        HorizontalPager(state = pager, pageSpacing = 16.dp, verticalAlignment = Alignment.Top) { page ->
            val tiles = pages[page]
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(rows) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(COLUMNS) { column ->
                            val tile = tiles.getOrNull(row * COLUMNS + column)
                            if (tile != null) {
                                ItemTile(tile, current = tile.id == currentId, onClick = { onPick(tile) }, modifier = Modifier.weight(1f))
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
        if (pages.size > 1) PageDots(page = pager.currentPage, count = pages.size)
    }
}

/** One thumbnail, its name on a soft shade along the bottom (room is short on the cover). */
@Composable
private fun ItemTile(item: CoverItem, current: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val showing = stringResource(R.string.cover_menu_showing)
    val badge = stringResource(item.badgeLabel())
    val name = item.name.ifBlank { badge }
    Box(
        modifier
            .clip(RoundedCornerShape(TILE_CORNER))
            .clickable(onClickLabel = stringResource(R.string.cover_menu_show), role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = name
                selected = current
                if (current) stateDescription = showing
            },
    ) {
        CoverPreviewFrame(Modifier.fillMaxWidth().clearAndSetSemantics {}, glowing = current) {
            CoverContent(item = item, modifier = Modifier.fillMaxSize(), playback = Playback.STILL)
            Text(
                text = name,
                color = if (current) BrandMint else Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
                    .padding(start = 8.dp, end = 8.dp, top = 14.dp, bottom = 6.dp),
            )
        }
        if (item is CoverItem.Group) {
            Text(
                text = stringResource(R.string.badge_group_count, badge, item.items.size),
                color = Color.White,
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(50))
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
        if (current) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color(0xFF070912),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(20.dp)
                    .background(BrandMint, CircleShape)
                    .padding(2.dp),
            )
        }
    }
}

@Composable
private fun PageDots(page: Int, count: Int) {
    val description = stringResource(R.string.intro_page_position, page + 1, count)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        repeat(count) { index ->
            Box(
                Modifier
                    .size(6.dp)
                    .background(Color.White.copy(alpha = if (index == page) 0.9f else 0.3f), CircleShape),
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 18.dp, bottom = 2.dp),
    )
}

/** Cover brightness (the software dimming): dragging dims the cover live; the file is saved once after the drag. */
@Composable
private fun BrightnessSlider(
    brightness: Float,
    interactionSource: MutableInteractionSource,
    onChange: (Float) -> Unit,
    onChangeFinished: () -> Unit,
) {
    var value by remember(brightness) { mutableFloatStateOf(brightness) }
    val label = stringResource(R.string.settings_brightness)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.BrightnessLow, contentDescription = null, tint = Color.White.copy(alpha = 0.7f))
        Slider(
            value = value,
            onValueChange = {
                value = it
                onChange(it)
            },
            onValueChangeFinished = onChangeFinished,
            valueRange = CoverSettings.MIN_BRIGHTNESS..1f,
            interactionSource = interactionSource,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
                .semantics { contentDescription = label },
        )
        Icon(Icons.Filled.BrightnessHigh, contentDescription = null, tint = Color.White.copy(alpha = 0.7f))
    }
}

/** A setting with its switch; the whole row toggles, and TalkBack hears its state. */
@Composable
private fun MenuSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    /** Why it cannot be switched here, under the title. */
    note: String? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(MaterialTheme.shapes.small)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.5f),
            )
            if (note != null) Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** The stay-lit choices offered here, plus the current one if it was set to another time on the phone. */
private fun stayOnChoices(current: Int): List<Int> =
    (STAY_ON_CHOICES + current).distinct().sortedBy { if (it == CoverSettings.STAY_ON_ALWAYS) Int.MAX_VALUE else it }

@Composable
private fun stayOnLabel(seconds: Int): String = when (seconds) {
    0 -> stringResource(R.string.stay_on_system)
    CoverSettings.STAY_ON_ALWAYS -> stringResource(R.string.cover_menu_always)
    else -> stringResource(R.string.cover_menu_minutes, seconds / 60)
}

private const val COLUMNS = 3
private const val PAGE_SIZE = COLUMNS * 2

/** Matches [CoverPreviewFrame]'s corners, so a tap's ripple has the tile's shape. */
private val TILE_CORNER = 22.dp

/** Cover default, 1 min, 5 min, always. */
private val STAY_ON_CHOICES = listOf(0, 60, 300, CoverSettings.STAY_ON_ALWAYS)

/** Nearly opaque, so the paused cover just shows through. */
private val MENU_BACKGROUND = Color(0xF20A0B10)

/** Pulled down this far, or flung down this fast (per second), the menu closes. */
private val CLOSE_DISTANCE = 72.dp
private val CLOSE_VELOCITY = 900.dp
