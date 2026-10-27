package com.zlip.app.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zlip.app.R
import com.zlip.app.data.CoverItem
import com.zlip.app.data.MediaKind
import com.zlip.app.render.CoverContent
import com.zlip.app.render.Playback
import com.zlip.app.ui.theme.BrandMint
import kotlinx.coroutines.launch

/**
 * Picks library items in order (a collage cell, a group's steps): tap order is play order, shown as
 * numbers. Items of [current] that are no longer in the library (deleted after they were added) stay
 * available. Closing the sheet any way (swipe, scrim, Back) keeps the picks, exactly like "Done"; "Clear"
 * is the one sweeping change, so it can be undone until the next pick. At most [maxCount] can be picked.
 * While Zlip is the Mini kapak the sheet is
 * not shown, but its picks wait for it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemPickerSheet(
    title: String,
    hint: String?,
    current: List<CoverItem>,
    choices: List<CoverItem>,
    onCommit: (List<CoverItem>) -> Unit,
    maxCount: Int = Int.MAX_VALUE,
) {
    val options = remember(current, choices) { current.filter { kept -> choices.none { it.id == kept.id } } + choices }
    var picked by rememberSaveable { mutableStateOf(current.map { it.id }) }
    // The picks "Clear" just took away: until the next pick its button reads "Undo" and brings them back, because any way
    // of closing the sheet commits (a 20-step group wiped by Clear and then Done was gone for good).
    var cleared by rememberSaveable { mutableStateOf<List<String>?>(null) }
    var committed by remember { mutableStateOf(false) }
    val commit = {
        // The Done button and a dismissal can both arrive while the sheet goes away; report once.
        if (!committed) {
            committed = true
            onCommit(picked.mapNotNull { id -> options.firstOrNull { it.id == id } })
        }
    }
    val full = picked.size >= maxCount
    // As the small window the sheet stays away, picks kept, and slides in again when Zlip is full size.
    if (LocalHovering.current) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = commit, sheetState = sheetState, modifier = Modifier.wakesStandby()) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                val undo = cleared
                if (undo != null) {
                    TextButton(onClick = {
                        picked = undo
                        cleared = null
                    }) { Text(stringResource(R.string.action_undo)) }
                } else {
                    TextButton(
                        onClick = {
                            cleared = picked
                            picked = emptyList()
                        },
                        enabled = picked.isNotEmpty(),
                    ) { Text(stringResource(R.string.picker_clear)) }
                }
                Spacer(Modifier.width(4.dp))
                // Slides away like a swipe would, then hands over the picks.
                Button(onClick = { scope.launch { sheetState.hide() }.invokeOnCompletion { commit() } }) {
                    Text(stringResource(R.string.action_done))
                }
            }
            if (hint != null) HintText(hint)
            if (full && maxCount != Int.MAX_VALUE) HintText(pluralStringResource(R.plurals.picker_max, maxCount, maxCount))
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 104.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                // Takes what the sheet has left, so landscape and large fonts still reach the last row.
                modifier = Modifier.weight(1f, fill = false),
            ) {
                items(options, key = { it.id }) { option ->
                    val order = picked.indexOf(option.id)
                    val selected = order >= 0
                    Column(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.medium)
                            .semantics { this.selected = selected }
                            .clickable(role = Role.Checkbox, enabled = selected || !full) {
                                picked = if (selected) picked - option.id else picked + option.id
                                cleared = null
                            },
                    ) {
                        Box {
                            CoverPreviewFrame(Modifier.fillMaxWidth(), glowing = selected) {
                                CoverScale { CoverContent(item = option, modifier = Modifier.fillMaxSize(), playback = Playback.STILL) }
                            }
                            Text(
                                text = stringResource(option.badgeLabel()),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(6.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .padding(horizontal = 6.dp, vertical = 1.dp),
                            )
                            if (selected) {
                                Text(
                                    text = "${order + 1}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color(0xFF070912),
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                        .background(BrandMint, CircleShape)
                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                        Text(
                            text = option.name,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            // Clear of the tile's rounded corners, which clip the whole column.
                            modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Short type name for badges ("Video", "Kolaj"); a group's step count is added where it is shown. */
@StringRes
fun CoverItem.badgeLabel(): Int = when (this) {
    is CoverItem.PixelArt -> R.string.badge_pixel
    is CoverItem.Text -> R.string.badge_text
    is CoverItem.Media -> when (kind) {
        MediaKind.IMAGE -> R.string.badge_photo
        MediaKind.ANIMATION -> R.string.badge_gif
        MediaKind.VIDEO -> R.string.badge_video
    }
    is CoverItem.Collage -> R.string.badge_collage
    is CoverItem.Countdown -> R.string.badge_countdown
    is CoverItem.Qr -> R.string.badge_qr
    is CoverItem.Toy -> R.string.badge_toy
    is CoverItem.Group -> R.string.badge_group
}
