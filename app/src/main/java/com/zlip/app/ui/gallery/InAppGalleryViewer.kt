package com.zlip.app.ui.gallery

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forward5
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Replay5
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zlip.app.R
import com.zlip.app.data.CoverItem
import com.zlip.app.data.MediaKind
import com.zlip.app.render.CoverContent
import com.zlip.app.render.LocalVideoProgressSink
import com.zlip.app.render.LocalVideoSyncPosition
import com.zlip.app.render.Playback
import com.zlip.app.render.VideoProgressSink
import com.zlip.app.ui.components.CoverPreviewFrame
import com.zlip.app.ui.components.CoverScale
import com.zlip.app.ui.theme.BrandMint
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

private fun formatTime(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

private fun getItemKindIconAndName(item: CoverItem): Pair<ImageVector, Int> = when (item) {
    is CoverItem.Media -> if (item.kind == MediaKind.VIDEO) {
        Icons.Filled.Videocam to R.string.gallery_type_video
    } else {
        Icons.Filled.Image to R.string.gallery_type_photo
    }
    is CoverItem.Group -> Icons.Filled.Collections to R.string.gallery_type_group
    is CoverItem.Collage -> Icons.Filled.Dashboard to R.string.gallery_type_collage
    is CoverItem.PixelArt -> Icons.Filled.Brush to R.string.gallery_type_pixel
    is CoverItem.Text -> Icons.Filled.TextFields to R.string.gallery_type_text
    is CoverItem.Countdown -> Icons.Filled.Timer to R.string.gallery_type_countdown
    is CoverItem.Qr -> Icons.Filled.QrCode to R.string.gallery_type_qr
    is CoverItem.Toy -> Icons.Filled.VideogameAsset to R.string.gallery_type_toy
}

@Composable
fun InAppGalleryViewer(
    items: List<CoverItem>,
    initialItemId: String?,
    isCoverActive: Boolean,
    coverPlayingId: String?,
    coverPositionMs: Long,
    coverDurationMs: Long,
    onCoverSeek: (Long) -> Unit,
    onToggleCoverPause: () -> Unit,
    isCoverPaused: Boolean,
    onSelectCover: (String) -> Unit,
    onEdit: (CoverItem) -> Unit,
    onDelete: (CoverItem) -> Unit,
    onDismiss: () -> Unit,
) {
    if (items.isEmpty()) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }

    val initialIndex = remember(items, initialItemId) {
        val found = items.indexOfFirst { it.id == initialItemId }
        if (found >= 0) found else 0
    }

    val pagerState = rememberPagerState(initialPage = initialIndex, pageCount = { items.size })
    val currentItem = items.getOrNull(pagerState.currentPage) ?: items.first()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Scroll thumbnail strip when page changes
    LaunchedEffect(pagerState.currentPage) {
        listState.animateScrollToItem((pagerState.currentPage - 2).coerceAtLeast(0))
    }

    val isCurrentItemOnCover = isCoverActive && currentItem.id == coverPlayingId
    val isVideo = (currentItem as? CoverItem.Media)?.kind == MediaKind.VIDEO || currentItem is CoverItem.Group

    // Local player state for items not currently playing on cover
    var localPositionMs by remember(currentItem.id) { mutableLongStateOf(0L) }
    var localDurationMs by remember(currentItem.id) { mutableLongStateOf(0L) }
    var localPaused by remember(currentItem.id) { mutableStateOf(false) }
    val localSeekFlow = remember(currentItem.id) { MutableSharedFlow<Long>(extraBufferCapacity = 2) }

    val localProgressSink = remember(currentItem.id) {
        VideoProgressSink(
            onProgress = { pos, dur ->
                localPositionMs = pos
                localDurationMs = dur
            },
            seekFlow = localSeekFlow,
        )
    }

    val displayPositionMs = if (isCurrentItemOnCover) coverPositionMs else localPositionMs
    val displayDurationMs = if (isCurrentItemOnCover) coverDurationMs else localDurationMs
    val isPlaying = if (isCurrentItemOnCover) !isCoverPaused else !localPaused

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0D0F14)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onDismiss,
                        colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }

                    val (icon, typeRes) = getItemKindIconAndName(currentItem)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                    ) {
                        Text(
                            text = currentItem.name.ifBlank { stringResource(typeRes) },
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                icon,
                                contentDescription = null,
                                tint = BrandMint,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = stringResource(typeRes) + " · ${pagerState.currentPage + 1}/${items.size}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f),
                            )
                        }
                    }

                    // Actions
                    FilledTonalButton(
                        onClick = { onSelectCover(currentItem.id) },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = BrandMint.copy(alpha = 0.2f),
                            contentColor = BrandMint,
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.padding(end = 4.dp),
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            stringResource(R.string.gallery_set_as_cover),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    IconButton(
                        onClick = {
                            onDismiss()
                            onEdit(currentItem)
                        },
                        colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = null)
                    }

                    IconButton(
                        onClick = { onDelete(currentItem) },
                        colors = IconButtonDefaults.iconButtonColors(contentColor = Color(0xFFFF5252)),
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null)
                    }
                }

                // Sync badge
                AnimatedVisibility(
                    visible = isCurrentItemOnCover,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Surface(
                        color = BrandMint.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BrandMint.copy(alpha = 0.4f)),
                        modifier = Modifier.padding(bottom = 6.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(BrandMint, CircleShape),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.gallery_sync_cover),
                                style = MaterialTheme.typography.labelSmall,
                                color = BrandMint,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                // Pager
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) { page ->
                    val item = items[page]
                    val isCoverPage = isCoverActive && item.id == coverPlayingId

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CoverPreviewFrame(
                            modifier = Modifier.fillMaxSize(0.92f),
                            glowing = isCoverPage,
                        ) {
                            val playbackMode = when {
                                isCoverPage -> if (isCoverPaused) Playback.PAUSED else Playback.LIVE
                                page == pagerState.currentPage -> if (localPaused) Playback.PAUSED else Playback.LIVE
                                else -> Playback.STILL
                            }

                            val progressSink = if (isCoverPage) null else localProgressSink
                            val syncPos = if (isCoverPage) coverPositionMs else null

                            CompositionLocalProvider(
                                LocalVideoProgressSink provides progressSink,
                                LocalVideoSyncPosition provides syncPos,
                            ) {
                                CoverScale {
                                    CoverContent(
                                        item = item,
                                        modifier = Modifier.fillMaxSize(),
                                        playback = playbackMode,
                                    )
                                }
                            }
                        }
                    }
                }

                // Media Player Controls (for videos or animated items)
                if (isVideo) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                    ) {
                        // Time & Scrubber Bar
                        if (displayDurationMs > 0) {
                            var draggingRatio by remember { mutableStateOf<Float?>(null) }
                            val currentRatio = draggingRatio ?: (displayPositionMs.toFloat() / displayDurationMs.toFloat()).coerceIn(0f, 1f)
                            val displayTimeMs = draggingRatio?.let { (it * displayDurationMs).toLong() } ?: displayPositionMs

                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = formatTime(displayTimeMs),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = BrandMint,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = formatTime(displayDurationMs),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.6f),
                                )
                            }
                            Slider(
                                value = currentRatio,
                                onValueChange = { draggingRatio = it },
                                onValueChangeFinished = {
                                    val target = ((draggingRatio ?: currentRatio) * displayDurationMs).toLong()
                                    if (isCurrentItemOnCover) {
                                        onCoverSeek(target)
                                    } else {
                                        localSeekFlow.tryEmit(target)
                                        localPositionMs = target
                                    }
                                    draggingRatio = null
                                },
                                colors = SliderDefaults.colors(
                                    thumbColor = BrandMint,
                                    activeTrackColor = BrandMint,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                                ),
                                modifier = Modifier.fillMaxWidth().height(24.dp),
                            )
                        }

                        // Playback Action Buttons Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Previous Item
                            IconButton(
                                onClick = {
                                    if (pagerState.currentPage > 0) {
                                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                                    }
                                },
                                enabled = pagerState.currentPage > 0,
                                colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
                            ) {
                                Icon(Icons.Filled.SkipPrevious, contentDescription = null, modifier = Modifier.size(28.dp))
                            }

                            // Rewind 5s
                            IconButton(
                                onClick = {
                                    val target = (displayPositionMs - 5000L).coerceAtLeast(0L)
                                    if (isCurrentItemOnCover) onCoverSeek(target) else localSeekFlow.tryEmit(target)
                                },
                                colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
                            ) {
                                Icon(Icons.Filled.Replay5, contentDescription = null)
                            }

                            // Play / Pause Button
                            FilledTonalButton(
                                onClick = {
                                    if (isCurrentItemOnCover) {
                                        onToggleCoverPause()
                                    } else {
                                        localPaused = !localPaused
                                    }
                                },
                                shape = CircleShape,
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = BrandMint,
                                    contentColor = Color.Black,
                                ),
                                modifier = Modifier.size(54.dp),
                                contentPadding = PaddingValues(0.dp),
                            ) {
                                Icon(
                                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp),
                                )
                            }

                            // Forward 5s
                            IconButton(
                                onClick = {
                                    val target = if (displayDurationMs > 0) {
                                        (displayPositionMs + 5000L).coerceAtMost(displayDurationMs)
                                    } else {
                                        displayPositionMs + 5000L
                                    }
                                    if (isCurrentItemOnCover) onCoverSeek(target) else localSeekFlow.tryEmit(target)
                                },
                                colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
                            ) {
                                Icon(Icons.Filled.Forward5, contentDescription = null)
                            }

                            // Next Item
                            IconButton(
                                onClick = {
                                    if (pagerState.currentPage < items.size - 1) {
                                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                                    }
                                },
                                enabled = pagerState.currentPage < items.size - 1,
                                colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
                            ) {
                                Icon(Icons.Filled.SkipNext, contentDescription = null, modifier = Modifier.size(28.dp))
                            }
                        }
                    }
                }

                // Thumbnail Strip
                LazyRow(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(items, key = { _, it -> it.id }) { index, item ->
                        val isSelected = index == pagerState.currentPage
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) BrandMint else Color.White.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(8.dp),
                                )
                                .clickable {
                                    scope.launch { pagerState.animateScrollToPage(index) }
                                },
                        ) {
                            CoverScale {
                                CoverContent(
                                    item = item,
                                    modifier = Modifier.fillMaxSize(),
                                    playback = Playback.STILL,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
