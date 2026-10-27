package com.zlip.app.ui.media

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.zlip.app.R
import com.zlip.app.data.ContentRepository
import com.zlip.app.data.CoverItem
import com.zlip.app.data.MediaFit
import com.zlip.app.data.MediaFocus
import com.zlip.app.data.MediaFraming
import com.zlip.app.data.MediaKind
import com.zlip.app.data.MediaStorage
import com.zlip.app.data.framed
import com.zlip.app.data.framing
import com.zlip.app.render.CoverContent
import com.zlip.app.render.Playback
import com.zlip.app.ui.components.ColorSwatchRow
import com.zlip.app.ui.components.CoverPreviewFrame
import com.zlip.app.ui.components.CoverScale
import com.zlip.app.ui.components.DefaultPalette
import com.zlip.app.ui.components.EditorTopBar
import com.zlip.app.ui.components.HintText
import com.zlip.app.ui.components.SectionCard
import com.zlip.app.ui.components.SegmentLabel
import com.zlip.app.ui.components.SwitchRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/** Below this (1 % of the preview), the picture reaches too little past it for moving it to be worth offering. */
private const val MIN_FRAMING_CROP = 0.01f

/** Display options for an imported photo, GIF or video. The file itself is already in the library. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaEditorScreen(
    initial: CoverItem.Media,
    previewPlayback: Playback,
    onDraftChange: (CoverItem) -> Unit,
    onDirtyChange: (dirty: Boolean, canSave: Boolean) -> Unit,
    onSave: (CoverItem.Media) -> Unit,
    onBack: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var fit by rememberSaveable { mutableStateOf(initial.fit) }
    var crisp by rememberSaveable { mutableStateOf(initial.crisp) }
    var loop by rememberSaveable { mutableStateOf(initial.loop) }
    var muted by rememberSaveable { mutableStateOf(initial.muted) }
    var background by rememberSaveable { mutableIntStateOf(initial.background) }
    var focusX by rememberSaveable { mutableFloatStateOf(initial.focusX) }
    var focusY by rememberSaveable { mutableFloatStateOf(initial.focusY) }
    var zoom by rememberSaveable { mutableFloatStateOf(initial.zoom) }

    val fallbackName = stringResource(R.string.default_media_name)
    val item = initial.copy(
        name = name.ifBlank { fallbackName },
        crisp = crisp,
        loop = loop,
        background = background,
        muted = muted,
    ).framed(MediaFraming(fit, zoom, focusX, focusY))
    val onFramingChange = { framing: MediaFraming ->
        fit = framing.fit
        zoom = framing.zoom
        focusX = framing.focusX
        focusY = framing.focusY
    }
    val baseline = remember { initial.copy(name = initial.name.ifBlank { fallbackName }) }
    LaunchedEffect(item) {
        onDraftChange(item)
        onDirtyChange(item != baseline, true)
    }

    val title = when (initial.kind) {
        MediaKind.IMAGE -> R.string.media_title_photo
        MediaKind.ANIMATION -> R.string.media_title_gif
        MediaKind.VIDEO -> R.string.media_title_video
    }

    // Two fingers zoom the preview; one drags the picture to choose what shows, only along an axis where it is
    // larger than the cover, so the other way still scrolls the page.
    val context = LocalContext.current
    val aspect by produceState(0f, initial.fileName) { value = withContext(Dispatchers.IO) { MediaStorage.aspect(context, initial) } }
    val framable = aspect > 0f

    Scaffold(
        topBar = { EditorTopBar(title = stringResource(title), onBack = onBack, onSave = { onSave(item) }) },
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
                // Silent, like every inner-screen preview: the cover (showing this draft) is where the clip is heard,
                // and a preview that blares the moment an editor opens would only startle.
                CoverScale { CoverContent(item = item, modifier = Modifier.fillMaxSize(), playback = previewPlayback) }
                if (framable) {
                    FramingSurface(
                        aspect = aspect,
                        framing = item.framing,
                        onFramingChange = onFramingChange,
                        modifier = Modifier.matchParentSize(),
                    )
                }
            }

            if (initial.kind == MediaKind.VIDEO) {
                SectionCard {
                    if (initial.hasAudio) {
                        SwitchRow(
                            title = stringResource(R.string.media_sound),
                            description = stringResource(R.string.media_sound_desc),
                            checked = !muted,
                            onCheckedChange = { muted = !it },
                            icon = if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.VolumeOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(12.dp))
                            HintText(stringResource(R.string.media_sound_none))
                        }
                    }
                }
            }

            SectionCard {
                Text(
                    text = stringResource(R.string.media_fit),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    MediaFit.entries.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = fit == option,
                            onClick = { fit = option },
                            shape = SegmentedButtonDefaults.itemShape(index, MediaFit.entries.size),
                            icon = {},
                        ) {
                            SegmentLabel(stringResource(if (option == MediaFit.FIT) R.string.media_fit_fit else R.string.media_fit_fill))
                        }
                    }
                }
                if (framable) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HintText(stringResource(R.string.media_frame_hint), Modifier.weight(1f))
                        // Back to the picture as its fit shows it: not zoomed, centred.
                        if (item.framing.adjusted) {
                            val resetDescription = stringResource(R.string.media_frame_reset_desc)
                            TextButton(
                                onClick = { onFramingChange(MediaFraming(fit)) },
                                modifier = Modifier.semantics { contentDescription = resetDescription },
                            ) {
                                Icon(Icons.Filled.CenterFocusStrong, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.media_frame_reset))
                            }
                        }
                    }
                }
                if (initial.kind != MediaKind.IMAGE) {
                    Spacer(Modifier.height(12.dp))
                    SwitchRow(
                        title = stringResource(R.string.media_loop),
                        description = stringResource(R.string.media_loop_desc),
                        checked = loop,
                        onCheckedChange = { loop = it },
                        icon = Icons.Filled.Repeat,
                    )
                }
                if (initial.kind != MediaKind.VIDEO) {
                    Spacer(Modifier.height(4.dp))
                    SwitchRow(
                        title = stringResource(R.string.media_crisp),
                        description = stringResource(R.string.media_crisp_desc),
                        checked = crisp,
                        onCheckedChange = { crisp = it },
                        icon = Icons.Filled.GridOn,
                    )
                }
            }

            // Filled, the picture covers the whole cover and a colour would change nothing, which felt broken.
            if (fit == MediaFit.FIT) {
                SectionCard {
                    Text(
                        text = stringResource(R.string.pixel_background),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    ColorSwatchRow(colors = DefaultPalette, selected = background, onSelect = { background = it })
                    HintText(stringResource(R.string.media_background_hint))
                }
            } else {
                HintText(stringResource(R.string.media_bg_fill_hint))
            }

            if (initial.kind == MediaKind.VIDEO) {
                HintText(stringResource(R.string.media_video_note))
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
 * Lies over the preview: two fingers zoom the picture around their midpoint (and move it along), one finger drags it
 * along an axis where it is larger than the preview, a double tap resets it ([MediaFraming] of the same fit), and
 * TalkBack gets the same as actions.
 */
@Composable
private fun FramingSurface(
    aspect: Float,
    framing: MediaFraming,
    onFramingChange: (MediaFraming) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentFraming by rememberUpdatedState(framing)
    val currentOnFramingChange by rememberUpdatedState(onFramingChange)
    var surfaceSize by remember { mutableStateOf(IntSize.Zero) }
    val hint = stringResource(R.string.media_frame_hint)
    val left = stringResource(R.string.media_frame_left)
    val right = stringResource(R.string.media_frame_right)
    val top = stringResource(R.string.media_frame_top)
    val bottom = stringResource(R.string.media_frame_bottom)
    val zoomIn = stringResource(R.string.media_zoom_in)
    val zoomOut = stringResource(R.string.media_zoom_out)
    val center = stringResource(R.string.media_frame_center)
    val reset = stringResource(R.string.media_frame_reset_desc)
    val width = surfaceSize.width.toFloat()
    val height = surfaceSize.height.toFloat()
    val (movableX, movableY) = movable(aspect, width, height, framing)
    // Around the preview's centre, as the reset and the step actions keep the picture centred too.
    val zoomBy = { change: Float -> currentOnFramingChange(MediaFocus.zoomedAround(currentFraming, aspect, width, height, change, width / 2f, height / 2f)) }
    val step = { dx: Float, dy: Float ->
        val now = currentFraming
        currentOnFramingChange(now.copy(focusX = MediaFocus.clamp(now.focusX + dx), focusY = MediaFocus.clamp(now.focusY + dy)))
    }
    Box(
        modifier
            .onSizeChanged { surfaceSize = it }
            .pointerInput(aspect) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    // Undecided until the fingers moved past the touch slop: then either the picture moves (a pinch,
                    // or a drag along an axis it can move on) or the page scrolls, for the rest of the gesture.
                    var steering = false
                    var lost = false
                    var slop = Offset.Zero
                    do {
                        val event = awaitPointerEvent()
                        val fingers = event.changes.count { it.pressed }
                        val boxWidth = size.width.toFloat()
                        val boxHeight = size.height.toFloat()
                        if (fingers >= 2 && !lost) {
                            steering = true
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            if (zoom != 1f || pan != Offset.Zero) {
                                val pivot = event.calculateCentroid(useCurrent = false)
                                currentOnFramingChange(
                                    MediaFocus.zoomedAround(currentFraming, aspect, boxWidth, boxHeight, zoom, pivot.x, pivot.y, pan.x, pan.y),
                                )
                            }
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        } else if (fingers == 1 && !lost) {
                            val change = event.changes.first { it.pressed }
                            val delta = change.positionChange()
                            val now = currentFraming
                            val (canX, canY) = movable(aspect, boxWidth, boxHeight, now)
                            if (!steering) {
                                slop += delta
                                if (slop.getDistance() > viewConfiguration.touchSlop) {
                                    val sideways = abs(slop.x) >= abs(slop.y)
                                    steering = (sideways && canX) || (!sideways && canY)
                                    lost = !steering
                                    // Claimed on the very move that decided it, so the page does not start scrolling too.
                                    if (steering) change.consume()
                                }
                            } else if (delta != Offset.Zero) {
                                val (overflowX, overflowY) = MediaFocus.overflow(aspect, boxWidth, boxHeight, now.fit, now.zoom)
                                currentOnFramingChange(
                                    now.copy(
                                        focusX = if (canX) MediaFocus.dragged(now.focusX, delta.x, overflowX) else now.focusX,
                                        focusY = if (canY) MediaFocus.dragged(now.focusY, delta.y, overflowY) else now.focusY,
                                    ),
                                )
                                change.consume()
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { currentOnFramingChange(MediaFraming(currentFraming.fit)) }) }
            .semantics {
                contentDescription = hint
                customActions = buildList {
                    if (framing.zoom < MediaFocus.MAX_ZOOM) add(CustomAccessibilityAction(zoomIn) { zoomBy(MediaFocus.ZOOM_STEP); true })
                    if (framing.zoom > MediaFocus.MIN_ZOOM) add(CustomAccessibilityAction(zoomOut) { zoomBy(1f / MediaFocus.ZOOM_STEP); true })
                    if (movableX) {
                        add(CustomAccessibilityAction(left) { step(-MediaFocus.STEP, 0f); true })
                        add(CustomAccessibilityAction(right) { step(MediaFocus.STEP, 0f); true })
                    }
                    if (movableY) {
                        add(CustomAccessibilityAction(top) { step(0f, -MediaFocus.STEP); true })
                        add(CustomAccessibilityAction(bottom) { step(0f, MediaFocus.STEP); true })
                    }
                    if (framing.adjusted) {
                        add(CustomAccessibilityAction(if (framing.zoom > MediaFocus.MIN_ZOOM) reset else center) { currentOnFramingChange(MediaFraming(currentFraming.fit)); true })
                    }
                }
            },
    )
}

/**
 * Whether the picture can move sideways and up/down in a [width] x [height] preview: only where it is larger than
 * the preview, and by more than [MIN_FRAMING_CROP] (a picture of the cover's own shape loses nothing to Fill).
 */
private fun movable(aspect: Float, width: Float, height: Float, framing: MediaFraming): Pair<Boolean, Boolean> {
    val (overflowX, overflowY) = MediaFocus.overflow(aspect, width, height, framing.fit, framing.zoom)
    return (overflowX > width * MIN_FRAMING_CROP) to (overflowY > height * MIN_FRAMING_CROP)
}
