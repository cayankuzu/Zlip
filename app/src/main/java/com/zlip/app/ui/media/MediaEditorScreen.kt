package com.zlip.app.ui.media

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zlip.app.R
import com.zlip.app.data.ContentRepository
import com.zlip.app.data.CoverItem
import com.zlip.app.data.MediaFit
import com.zlip.app.data.MediaFocus
import com.zlip.app.data.MediaKind
import com.zlip.app.data.MediaStorage
import com.zlip.app.render.CoverContent
import com.zlip.app.render.Playback
import com.zlip.app.ui.components.COVER_ASPECT_RATIO
import com.zlip.app.ui.components.ColorSwatchRow
import com.zlip.app.ui.components.CoverPreviewFrame
import com.zlip.app.ui.components.CoverScale
import com.zlip.app.ui.components.DefaultPalette
import com.zlip.app.ui.components.HintText
import com.zlip.app.ui.components.SectionCard
import com.zlip.app.ui.components.SegmentLabel
import com.zlip.app.ui.components.SwitchRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/** Below this (1 %), Fill crops too little of the picture for framing it to be worth offering. */
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

    val fallbackName = stringResource(R.string.default_media_name)
    val item = initial.copy(
        name = name.ifBlank { fallbackName },
        fit = fit,
        crisp = crisp,
        loop = loop,
        background = background,
        muted = muted,
        focusX = focusX,
        focusY = focusY,
    )
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

    // Fill crops the picture; the user drags it to choose what shows. Only along the axis that is cropped,
    // so the other way still scrolls the page.
    val context = LocalContext.current
    val aspect by produceState(0f, initial.fileName) { value = withContext(Dispatchers.IO) { pictureAspect(context, initial) } }
    // A picture of the cover's own shape loses nothing to Fill: no drag, no hint saying there is one.
    val framing = fit == MediaFit.FILL && aspect > 0f && abs(aspect / COVER_ASPECT_RATIO - 1f) > MIN_FRAMING_CROP
    val horizontal = aspect > COVER_ASPECT_RATIO

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(title)) },
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
                if (framing) {
                    FramingSurface(
                        aspect = aspect,
                        horizontal = horizontal,
                        focus = { if (horizontal) focusX else focusY },
                        onFocusChange = { if (horizontal) focusX = it else focusY = it },
                        onCenter = {
                            focusX = 0f
                            focusY = 0f
                        },
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
                if (framing) HintText(stringResource(R.string.media_frame_hint))
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
 * Lies over the preview while Fill crops the picture: a drag along the cropped axis ([horizontal] or not)
 * moves the picture with the finger, a double tap centres it, and TalkBack gets the same as actions.
 */
@Composable
private fun FramingSurface(
    aspect: Float,
    horizontal: Boolean,
    focus: () -> Float,
    onFocusChange: (Float) -> Unit,
    onCenter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentFocus by rememberUpdatedState(focus)
    val currentOnFocusChange by rememberUpdatedState(onFocusChange)
    val currentOnCenter by rememberUpdatedState(onCenter)
    val hint = stringResource(R.string.media_frame_hint)
    val towardsStart = stringResource(if (horizontal) R.string.media_frame_left else R.string.media_frame_top)
    val towardsEnd = stringResource(if (horizontal) R.string.media_frame_right else R.string.media_frame_bottom)
    val center = stringResource(R.string.media_frame_center)
    Box(
        modifier
            .pointerInput(aspect, horizontal) {
                val onDrag = { change: PointerInputChange, delta: Float ->
                    change.consume()
                    val (overflowX, overflowY) = MediaFocus.overflow(aspect, size.width.toFloat(), size.height.toFloat())
                    currentOnFocusChange(MediaFocus.dragged(currentFocus(), delta, if (horizontal) overflowX else overflowY))
                }
                if (horizontal) detectHorizontalDragGestures(onHorizontalDrag = onDrag) else detectVerticalDragGestures(onVerticalDrag = onDrag)
            }
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { currentOnCenter() }) }
            .semantics {
                contentDescription = hint
                customActions = listOf(
                    CustomAccessibilityAction(towardsStart) {
                        currentOnFocusChange(MediaFocus.clamp(currentFocus() - MediaFocus.STEP))
                        true
                    },
                    CustomAccessibilityAction(towardsEnd) {
                        currentOnFocusChange(MediaFocus.clamp(currentFocus() + MediaFocus.STEP))
                        true
                    },
                    CustomAccessibilityAction(center) {
                        currentOnCenter()
                        true
                    },
                )
            },
    )
}

/** Width / height of the picture as stored (a video's from its poster frame), or 0 when it cannot be read. */
private fun pictureAspect(context: Context, media: CoverItem.Media): Float {
    val name = (if (media.kind == MediaKind.VIDEO) media.posterFileName else media.fileName) ?: return 0f
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    runCatching { BitmapFactory.decodeFile(MediaStorage.file(context, name).path, bounds) }
    return if (bounds.outWidth > 0 && bounds.outHeight > 0) bounds.outWidth.toFloat() / bounds.outHeight else 0f
}
