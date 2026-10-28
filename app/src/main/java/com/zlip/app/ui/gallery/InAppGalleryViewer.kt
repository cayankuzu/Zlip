package com.zlip.app.ui.gallery

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forward5
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zlip.app.R
import com.zlip.app.cover.GroupProgress
import com.zlip.app.data.CoverItem
import com.zlip.app.data.MediaKind
import com.zlip.app.render.CoverContent
import com.zlip.app.render.LocalVideoProgressSink
import com.zlip.app.render.LocalVideoSyncPosition
import com.zlip.app.render.Playback
import com.zlip.app.render.VideoProgressSink
import com.zlip.app.ui.components.COVER_ASPECT_RATIO
import com.zlip.app.ui.components.CoverScale
import com.zlip.app.ui.components.VideoSeekBar
import com.zlip.app.ui.theme.BrandGradient
import com.zlip.app.ui.theme.BrandMint
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

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
    shownOnCover: CoverItem? = null,
    groupProgress: GroupProgress? = null,
    onPreviousStep: (() -> Unit)? = null,
    onNextStep: (() -> Unit)? = null,
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
    val haptics = LocalHapticFeedback.current

    var controlsVisible by remember { mutableStateOf(true) }
    var menuExpanded by remember { mutableStateOf(false) }

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

    // Subtle pulsing animation for cover sync badge
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )

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
                .background(Color(0xFF08090C))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { controlsVisible = !controlsVisible },
                ),
        ) {
            // Pager Viewport (Centered cleanly between top bar and bottom dock)
            val bottomClearance = when {
                !controlsVisible -> 16.dp
                isVideo -> 172.dp
                else -> 88.dp
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = if (controlsVisible) 68.dp else 16.dp,
                        bottom = bottomClearance,
                    ),
            ) { page ->
                val item = items[page]
                val isCoverPage = isCoverActive && item.id == coverPlayingId

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val frameShape = RoundedCornerShape(22.dp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .aspectRatio(COVER_ASPECT_RATIO)
                            .clip(frameShape)
                            .background(Color.Black)
                            .border(
                                border = if (isCoverPage) {
                                    BorderStroke(2.5.dp, Brush.linearGradient(BrandGradient))
                                } else {
                                    BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                                },
                                shape = frameShape,
                            ),
                    ) {
                        val playbackMode = when {
                            isCoverPage -> if (isCoverPaused) Playback.PAUSED else Playback.LIVE
                            page == pagerState.currentPage -> if (localPaused) Playback.PAUSED else Playback.LIVE
                            else -> Playback.STILL
                        }

                        val progressSink = if (isCoverPage) null else localProgressSink
                        val syncPos = if (isCoverPage) coverPositionMs else null
                        val renderItem = if (isCoverPage && shownOnCover != null) shownOnCover else item

                        CompositionLocalProvider(
                            LocalVideoProgressSink provides progressSink,
                            LocalVideoSyncPosition provides syncPos,
                        ) {
                            CoverScale {
                                CoverContent(
                                    item = renderItem,
                                    modifier = Modifier.fillMaxSize(),
                                    playback = playbackMode,
                                )
                            }
                        }
                        if (page == pagerState.currentPage && isVideo && playbackMode == Playback.PAUSED) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(56.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                    .clickable {
                                        if (isCurrentItemOnCover) onToggleCoverPause() else localPaused = false
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.PlayArrow,
                                    contentDescription = stringResource(R.string.action_resume),
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp),
                                )
                            }
                        }
                    }
                }
            }

            // Top Bar Overlay
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent),
                            ),
                        )
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                                .padding(horizontal = 6.dp),
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
                                    modifier = Modifier.size(13.dp),
                                )
                                val activeGroupProgress = groupProgress?.takeIf { isCurrentItemOnCover && it.groupId == currentItem.id }
                                val subtitleParts = listOfNotNull(
                                    stringResource(typeRes),
                                    activeGroupProgress?.let { stringResource(R.string.cover_group_step, it.step, it.count) },
                                    "${pagerState.currentPage + 1} / ${items.size}",
                                )
                                Text(
                                    text = subtitleParts.joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.75f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        if (isCurrentItemOnCover) {
                            Surface(
                                color = BrandMint.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(20.dp),
                                border = BorderStroke(1.dp, BrandMint.copy(alpha = 0.45f)),
                                modifier = Modifier.padding(end = 4.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(BrandMint.copy(alpha = pulseAlpha), CircleShape),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.home_on_cover),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = BrandMint,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        } else {
                            FilledTonalButton(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onSelectCover(currentItem.id)
                                },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = BrandMint.copy(alpha = 0.22f),
                                    contentColor = BrandMint,
                                ),
                                shape = RoundedCornerShape(20.dp),
                                border = BorderStroke(1.dp, BrandMint.copy(alpha = 0.5f)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.padding(end = 4.dp),
                            ) {
                                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    stringResource(R.string.gallery_set_as_cover),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }

                        // More options dropdown
                        Box {
                            IconButton(
                                onClick = { menuExpanded = true },
                                colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
                            ) {
                                Icon(Icons.Filled.MoreVert, contentDescription = null)
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_edit)) },
                                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onDismiss()
                                        onEdit(currentItem)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_delete)) },
                                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = Color(0xFFFF5252)) },
                                    onClick = {
                                        menuExpanded = false
                                        onDelete(currentItem)
                                    },
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Controls & Filmstrip Overlay
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.95f)),
                            ),
                        )
                        .navigationBarsPadding()
                        .padding(bottom = 8.dp),
                ) {
                    // Media Player Controls Card (for Videos & Groups)
                    if (isVideo) {
                        Surface(
                            color = Color(0xFF14171F).copy(alpha = 0.85f),
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            ) {
                                // Group step navigation row when this group is active on cover
                                val activeGroupProgress = groupProgress?.takeIf { isCurrentItemOnCover && it.groupId == currentItem.id }
                                if (activeGroupProgress != null && onPreviousStep != null && onNextStep != null) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        IconButton(
                                            onClick = {
                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                onPreviousStep()
                                            },
                                            colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
                                            modifier = Modifier.size(34.dp),
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.action_previous_step))
                                        }
                                        Text(
                                            text = stringResource(R.string.cover_group_step, activeGroupProgress.step, activeGroupProgress.count),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = BrandMint,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        IconButton(
                                            onClick = {
                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                onNextStep()
                                            },
                                            colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
                                            modifier = Modifier.size(34.dp),
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.action_next_step))
                                        }
                                    }
                                }

                                // Scrubber & Timestamps
                                if (displayDurationMs > 0) {
                                    VideoSeekBar(
                                        positionMs = displayPositionMs,
                                        durationMs = displayDurationMs,
                                        onSeek = { target ->
                                            if (isCurrentItemOnCover) {
                                                onCoverSeek(target)
                                            } else {
                                                localSeekFlow.tryEmit(target)
                                                localPositionMs = target
                                            }
                                        },
                                        activeColor = BrandMint,
                                        inactiveColor = Color.White.copy(alpha = 0.22f),
                                        totalTimeColor = Color.White.copy(alpha = 0.68f),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }

                                // Playback buttons
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 2.dp),
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
                                        Icon(Icons.Filled.SkipPrevious, contentDescription = null, modifier = Modifier.size(26.dp))
                                    }

                                    // Rewind 5s (only when a video with duration is active)
                                    if (displayDurationMs > 0) {
                                        IconButton(
                                            onClick = {
                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                val target = (displayPositionMs - 5000L).coerceAtLeast(0L)
                                                if (isCurrentItemOnCover) {
                                                    onCoverSeek(target)
                                                } else {
                                                    localSeekFlow.tryEmit(target)
                                                    localPositionMs = target
                                                }
                                            },
                                            colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
                                        ) {
                                            Icon(Icons.Filled.Replay5, contentDescription = null)
                                        }
                                    }

                                    // Center Play / Pause Button
                                    FilledTonalButton(
                                        onClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
                                        modifier = Modifier.size(48.dp),
                                        contentPadding = PaddingValues(0.dp),
                                    ) {
                                        Icon(
                                            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                            contentDescription = null,
                                            modifier = Modifier.size(26.dp),
                                        )
                                    }

                                    // Forward 5s (only when a video with duration is active)
                                    if (displayDurationMs > 0) {
                                        IconButton(
                                            onClick = {
                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                val target = (displayPositionMs + 5000L).coerceAtMost(displayDurationMs)
                                                if (isCurrentItemOnCover) {
                                                    onCoverSeek(target)
                                                } else {
                                                    localSeekFlow.tryEmit(target)
                                                    localPositionMs = target
                                                }
                                            },
                                            colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
                                        ) {
                                            Icon(Icons.Filled.Forward5, contentDescription = null)
                                        }
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
                                        Icon(Icons.Filled.SkipNext, contentDescription = null, modifier = Modifier.size(26.dp))
                                    }
                                }
                            }
                        }
                    }

                    // Filmstrip: Horizontal Mini-Thumbnails
                    LazyRow(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(items, key = { _, it -> it.id }) { index, item ->
                            val isSelected = index == pagerState.currentPage
                            val thumbItem = if (isCoverActive && item.id == coverPlayingId && shownOnCover != null) shownOnCover else item
                            Box(
                                modifier = Modifier
                                    .width(46.dp)
                                    .aspectRatio(COVER_ASPECT_RATIO)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) BrandMint else Color.White.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(8.dp),
                                    )
                                    .clickable {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        scope.launch { pagerState.animateScrollToPage(index) }
                                    },
                            ) {
                                CoverScale {
                                    CoverContent(
                                        item = thumbItem,
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
}
