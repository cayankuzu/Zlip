package com.zlip.app.ui.pixel

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zlip.app.R
import com.zlip.app.data.CoverItem
import com.zlip.app.render.PixelArtView

private val THUMB_SIZE = 52.dp
private val THUMB_SHAPE = RoundedCornerShape(10.dp)

/**
 * The animation timeline under the canvas: play/stop, one thumbnail per frame (tap to draw on it) and a
 * "+" that adds a copy of the selected frame. The selected frame always scrolls into view.
 */
@Composable
internal fun FrameStrip(
    sheet: PixelFrames,
    background: Int,
    selected: Int,
    playing: Boolean,
    onSelect: (Int) -> Unit,
    onAdd: () -> Unit,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(selected, sheet.count) {
        val layout = listState.layoutInfo
        val shown = layout.visibleItemsInfo.firstOrNull { it.index == selected }
        val fullyVisible = shown != null && shown.offset >= layout.viewportStartOffset &&
            shown.offset + shown.size <= layout.viewportEndOffset
        if (!fullyVisible) listState.animateScrollToItem(selected)
    }
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        FilledTonalIconButton(
            onClick = onTogglePlay,
            enabled = sheet.count > 1,
            // Centred on the thumbnails (not on their number labels).
            modifier = Modifier.padding(top = 6.dp),
        ) {
            Icon(
                imageVector = if (playing) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                contentDescription = stringResource(if (playing) R.string.pixel_anim_stop else R.string.pixel_anim_play),
            )
        }
        Spacer(Modifier.width(4.dp))
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(sheet.count) { index ->
                FrameThumb(
                    sheet = sheet,
                    index = index,
                    background = background,
                    selected = index == selected,
                    onClick = { onSelect(index) },
                )
            }
            item(key = "add") {
                AddFrameTile(enabled = sheet.canAdd, onClick = onAdd)
            }
        }
    }
}

@Composable
private fun FrameThumb(sheet: PixelFrames, index: Int, background: Int, selected: Boolean, onClick: () -> Unit) {
    val pixels = sheet.frames[index]
    // Same instance while this frame is untouched, so drawing on one frame never redraws the others.
    val art = remember(pixels, sheet.width, sheet.height, background) {
        CoverItem.PixelArt(id = "", name = "", width = sheet.width, height = sheet.height, pixels = pixels, background = background)
    }
    val label = stringResource(R.string.pixel_anim_frame, index + 1, sheet.count)
    val accent = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(THUMB_SHAPE)
            .semantics { contentDescription = label }
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(4.dp),
    ) {
        Box(
            Modifier
                .size(THUMB_SIZE)
                .clip(THUMB_SHAPE)
                .background(Color(background))
                .border(if (selected) 2.dp else 1.dp, accent, THUMB_SHAPE)
                .padding(4.dp),
        ) {
            PixelArtView(art, Modifier.fillMaxSize())
        }
        Text(
            text = "${index + 1}",
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            // The thumbnail's description already says "Frame 3 of 8".
            modifier = Modifier
                .clearAndSetSemantics {}
                .padding(top = 2.dp),
        )
    }
}

@Composable
private fun AddFrameTile(enabled: Boolean, onClick: () -> Unit) {
    val label = stringResource(R.string.pixel_anim_add)
    val tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(4.dp)
            .size(THUMB_SIZE)
            .clip(THUMB_SHAPE)
            .border(1.dp, if (enabled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant, THUMB_SHAPE)
            .semantics { contentDescription = label }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = tint)
    }
}

/** Frame tools, shown once there is more than one frame: onion skin, reorder and delete. */
@Composable
internal fun FrameActions(
    onionSkin: Boolean,
    onOnionSkinChange: (Boolean) -> Unit,
    canMoveEarlier: Boolean,
    canMoveLater: Boolean,
    canDelete: Boolean,
    onMoveEarlier: () -> Unit,
    onMoveLater: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        FilterChip(
            selected = onionSkin,
            onClick = { onOnionSkinChange(!onionSkin) },
            label = { Text(stringResource(R.string.pixel_anim_onion)) },
            leadingIcon = { Icon(Icons.Filled.Layers, contentDescription = null) },
        )
        Spacer(Modifier.weight(1f))
        LabeledIconButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, R.string.pixel_anim_move_earlier, canMoveEarlier, onMoveEarlier)
        LabeledIconButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, R.string.pixel_anim_move_later, canMoveLater, onMoveLater)
        LabeledIconButton(Icons.Filled.Delete, R.string.pixel_anim_delete, canDelete, onDelete)
    }
}

/** Icon-only button whose label shows as a tooltip on long press (and is what TalkBack reads). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LabeledIconButton(icon: ImageVector, @StringRes label: Int, enabled: Boolean, onClick: () -> Unit) {
    val text = stringResource(label)
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(text) } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(icon, contentDescription = text)
        }
    }
}
