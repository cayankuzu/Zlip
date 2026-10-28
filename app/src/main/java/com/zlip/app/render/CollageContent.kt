package com.zlip.app.render

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.zlip.app.data.CoverItem
import com.zlip.app.data.MediaKind
import kotlinx.coroutines.delay

/** Corner radius of collage cells when [CoverItem.Collage.rounded] is on. */
val COLLAGE_CORNER = 14.dp

/**
 * A collage: each cell plays its own items and moves to the next one every interval. Cells start
 * staggered, so only one changes at a time. Reports a pass ([PlaybackEvents.onCycle]) each time the
 * longest cell has shown all of its items, which lets smart auto-advance wait for the whole collage.
 * Only one video decodes at a time (the first cell showing one); other video cells show their poster.
 */
@Composable
internal fun CollageCover(item: CoverItem.Collage, modifier: Modifier, playback: Playback, events: PlaybackEvents?) {
    val currentEvents by rememberUpdatedState(events)
    val longest = item.cells.indexOfFirst { it.size == item.longestCell }
    // What each cell shows now, so the single live video slot can follow the rotation.
    val shown = remember { mutableStateMapOf<Int, Int>() }
    val liveVideoCell = item.cells.indices.firstOrNull { index ->
        val current = item.cells[index].getOrNull(shown[index] ?: 0)
        current is CoverItem.Media && current.kind == MediaKind.VIDEO
    }
    // The cells change only while the collage is seen: not while paused, and not while the cover is dark.
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val rotating = playback == Playback.LIVE && lifecycleState.isAtLeast(Lifecycle.State.STARTED)
    BoxWithConstraints(modifier.background(Color(item.background))) {
        val gap = item.gapDp.dp
        val inner = Modifier.padding(gap / 2)
        val width = maxWidth - gap
        val height = maxHeight - gap
        // Hardware video overlays cannot be clipped to a cell; cells never play sound or report single-video progress.
        CompositionLocalProvider(
            LocalVideoOverlay provides false,
            LocalAllowSound provides false,
            LocalVideoProgressSink provides null,
            LocalVideoSyncPosition provides null,
        ) {
            Box(inner.fillMaxSize()) {
                item.layout.cells.forEachIndexed { index, rect ->
                    val cell = item.cells.getOrElse(index) { emptyList() }
                    Box(
                        Modifier
                            .offset(x = width * rect[0], y = height * rect[1])
                            .size(width * (rect[2] - rect[0]), height * (rect[3] - rect[1]))
                            .padding(gap / 2)
                            .then(if (item.rounded) Modifier.clip(RoundedCornerShape(COLLAGE_CORNER)) else Modifier),
                    ) {
                        CollageCell(
                            cell = cell,
                            index = index,
                            cellCount = item.layout.cells.size,
                            intervalMs = item.intervalSeconds * 1_000L,
                            playback = playback,
                            rotating = rotating,
                            videoAllowed = index == liveVideoCell,
                            onShown = { shown[index] = it },
                            onPass = if (index == longest) ({ currentEvents?.onCycle() }) else null,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CollageCell(
    cell: List<CoverItem>,
    index: Int,
    cellCount: Int,
    intervalMs: Long,
    playback: Playback,
    rotating: Boolean,
    videoAllowed: Boolean,
    onShown: (Int) -> Unit,
    onPass: (() -> Unit)?,
) {
    var shown by remember(cell) { mutableIntStateOf(0) }
    val currentOnPass by rememberUpdatedState(onPass)
    val currentOnShown by rememberUpdatedState(onShown)
    // Also when the cell's items change (the editor): its index restarts at 0 and the parent must know.
    LaunchedEffect(cell, shown) { currentOnShown(shown) }
    LaunchedEffect(cell, intervalMs, rotating) {
        if (!rotating || cell.size < 2) return@LaunchedEffect
        // Spread the cells over one interval so the collage changes piece by piece (again after each stop).
        delay(intervalMs * index / cellCount)
        while (true) {
            delay(intervalMs)
            shown = (shown + 1) % cell.size
            if (shown == 0) currentOnPass?.invoke()
        }
    }
    if (cell.isEmpty()) return
    // STILL (thumbnails) skips the crossfade entirely: one decoder-free frame per cell.
    if (playback == Playback.STILL) {
        CoverContent(item = cell[0], modifier = Modifier.fillMaxSize(), playback = Playback.STILL)
        return
    }
    Crossfade(targetState = cell[shown.coerceIn(0, cell.lastIndex)], animationSpec = tween(CROSSFADE_MS), label = "collage-cell") { shownItem ->
        val isVideo = shownItem is CoverItem.Media && shownItem.kind == MediaKind.VIDEO
        CoverContent(
            item = shownItem,
            modifier = Modifier.fillMaxSize(),
            playback = if (isVideo && !videoAllowed) Playback.STILL else playback,
        )
    }
}

private const val CROSSFADE_MS = 600
