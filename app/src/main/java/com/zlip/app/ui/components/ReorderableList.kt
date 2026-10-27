package com.zlip.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.zlip.app.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** How close to the visible edge a dragged row has to come before the screen scrolls. */
private val AUTO_SCROLL_EDGE = 56.dp

/** Fastest auto-scroll, per frame, with the row right at the edge. */
private val AUTO_SCROLL_STEP = 14.dp

/** What a row of a [ReorderableList] can do; its content decides where the handle and buttons go. */
interface ReorderableRowScope {
    /** The row is lifted: being dragged, or gliding into place after the drop. */
    val isDragging: Boolean
    val canMoveUp: Boolean
    val canMoveDown: Boolean

    fun moveUp()

    fun moveDown()

    /** Dragging this element moves the row right away (the rest of the row needs a long-press first). */
    fun Modifier.dragHandle(): Modifier
}

/**
 * A list the user puts in order three ways: drag a row by its handle (or long-press it anywhere), tap
 * its up/down buttons, or use TalkBack's "Move up" / "Move down" actions. A drag changes the order once,
 * on drop, through [onMove]; the other rows make room while it moves. Not lazy, so it can sit inside a
 * scrolling screen: pass that screen's [scrollState] and dragging near the visible edge scrolls it.
 * [actions] adds TalkBack actions of the caller's own (remove...) to each row.
 */
@Composable
fun <T> ReorderableList(
    items: List<T>,
    itemKey: (T) -> Any,
    onMove: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier,
    scrollState: ScrollState? = null,
    spacing: Dp = 8.dp,
    actions: (T) -> List<CustomAccessibilityAction> = { emptyList() },
    row: @Composable ReorderableRowScope.(item: T) -> Unit,
) {
    val density = LocalDensity.current
    val spacingPx = with(density) { spacing.toPx() }
    val edgePx = with(density) { AUTO_SCROLL_EDGE.toPx() }
    val stepPx = with(density) { AUTO_SCROLL_STEP.toPx() }
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val currentItems by rememberUpdatedState(items)
    val currentOnMove by rememberUpdatedState(onMove)
    val heights = remember { mutableStateMapOf<Any, Int>() }
    val drag = remember { DragState() }
    // Where the list sits in the window: unclipped, and the part the scrolling screen actually shows.
    var listTop by remember { mutableFloatStateOf(0f) }
    var listHeight by remember { mutableFloatStateOf(0f) }
    var visible by remember { mutableStateOf(Rect.Zero) }
    val moveUpLabel = stringResource(R.string.reorder_move_up)
    val moveDownLabel = stringResource(R.string.reorder_move_down)
    val liftedColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val liftedShape = MaterialTheme.shapes.medium

    fun indexOf(rowKey: Any?): Int = if (rowKey == null) -1 else currentItems.indexOfFirst { itemKey(it) == rowKey }

    // A row's height plus the gap after it: how far its neighbours move when it changes place.
    fun slot(index: Int): Float = (heights[itemKey(currentItems[index])] ?: 0) + spacingPx

    fun target(from: Int): Int {
        var to = from
        var passed = 0f
        if (drag.offset > 0) {
            while (to < currentItems.lastIndex && drag.offset > passed + slot(to + 1) / 2) passed += slot(++to)
        } else {
            while (to > 0 && -drag.offset > passed + slot(to - 1) / 2) passed += slot(--to)
        }
        return to
    }

    val from by remember { derivedStateOf { indexOf(drag.key) } }
    // Where the dragged row lands if dropped now; only a change of place recomposes, not every pixel.
    val to by remember { derivedStateOf { if (from < 0) -1 else target(from) } }

    fun start(rowKey: Any) {
        drag.settle?.cancel()
        drag.settlingKey = null
        drag.key = rowKey
        drag.offset = 0f
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun drop() {
        val rowKey = drag.key ?: return
        val start = indexOf(rowKey)
        drag.key = null
        if (start < 0) return
        val end = target(start)
        // The dragged row's new place is the rows it passed away; glide the rest of the way there.
        val passed = if (end > start) (start + 1..end).sumOf { slot(it).toDouble() } else -(end until start).sumOf { slot(it).toDouble() }
        val rest = drag.offset - passed.toFloat()
        if (end != start) currentOnMove(start, end)
        drag.settlingKey = rowKey
        drag.offset = rest
        drag.settle = scope.launch {
            animate(rest, 0f) { value, _ -> drag.offset = value }
            drag.settlingKey = null
        }
    }

    val dragging = from >= 0
    LaunchedEffect(dragging, scrollState) {
        val screen = scrollState ?: return@LaunchedEffect
        if (!dragging) return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            val at = indexOf(drag.key).takeIf { it >= 0 } ?: break
            val rowTop = listTop + (0 until at).sumOf { slot(it).toDouble() }.toFloat() + drag.offset
            val rowBottom = rowTop + (heights[drag.key] ?: 0)
            // Only towards a side where part of the list is hidden, faster the deeper into the edge.
            val step = when {
                listTop < visible.top && rowTop < visible.top + edgePx -> -stepPx * ((visible.top + edgePx - rowTop) / edgePx).coerceAtMost(1f)
                listTop + listHeight > visible.bottom && rowBottom > visible.bottom - edgePx ->
                    stepPx * ((rowBottom - visible.bottom + edgePx) / edgePx).coerceAtMost(1f)
                else -> 0f
            }
            // The content moves under the finger; the row stays with the finger.
            if (step != 0f) drag.offset += screen.scrollBy(step)
        }
    }

    Column(
        modifier = modifier.onGloballyPositioned {
            listTop = it.positionInWindow().y
            listHeight = it.size.height.toFloat()
            visible = it.boundsInWindow()
        },
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        items.forEachIndexed { index, item ->
            val rowKey = itemKey(item)
            key(rowKey) {
                val lifted = rowKey == drag.key || rowKey == drag.settlingKey
                // Rows between the dragged row and its landing place slide over by one slot.
                val shift = when {
                    !dragging || lifted -> 0f
                    index in (from + 1)..to -> -slot(from)
                    index in to until from -> slot(from)
                    else -> 0f
                }
                val shiftAnimation = remember { Animatable(0f) }
                LaunchedEffect(shift, dragging) {
                    if (dragging) shiftAnimation.animateTo(shift) else shiftAnimation.snapTo(0f)
                }
                val rowScope = RowScopeImpl(
                    isDragging = lifted,
                    canMoveUp = index > 0,
                    canMoveDown = index < items.lastIndex,
                    onMoveUp = { currentOnMove(index, index - 1) },
                    onMoveDown = { currentOnMove(index, index + 1) },
                    handle = Modifier.pointerInput(rowKey) {
                        detectDragGestures(
                            onDragStart = { start(rowKey) },
                            onDragEnd = { drop() },
                            onDragCancel = { drop() },
                            onDrag = { change, amount ->
                                change.consume()
                                drag.offset += amount.y
                            },
                        )
                    },
                )
                val rowActions = buildList {
                    if (rowScope.canMoveUp) add(CustomAccessibilityAction(moveUpLabel) { rowScope.moveUp(); true })
                    if (rowScope.canMoveDown) add(CustomAccessibilityAction(moveDownLabel) { rowScope.moveDown(); true })
                    addAll(actions(item))
                }
                Box(
                    Modifier
                        .onSizeChanged { heights[rowKey] = it.height }
                        .zIndex(if (lifted) 1f else 0f)
                        .graphicsLayer {
                            translationY = when {
                                lifted -> drag.offset
                                dragging -> shiftAnimation.value
                                else -> 0f
                            }
                            if (lifted) {
                                shadowElevation = 8.dp.toPx()
                                shape = liftedShape
                                clip = false
                            }
                        }
                        .background(if (lifted) liftedColor else Color.Transparent, liftedShape)
                        .pointerInput(rowKey) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { start(rowKey) },
                                onDragEnd = { drop() },
                                onDragCancel = { drop() },
                                onDrag = { change, amount ->
                                    change.consume()
                                    drag.offset += amount.y
                                },
                            )
                        }
                        .semantics(mergeDescendants = true) { customActions = rowActions },
                ) {
                    rowScope.row(item)
                }
            }
        }
    }
}

/** The row's drag handle. Silent for TalkBack, which moves rows with the row's actions instead. */
@Composable
fun ReorderableRowScope.DragHandle(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.Filled.DragHandle,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.dragHandle(),
    )
}

/** Up and down buttons for people who would rather tap than drag. */
@Composable
fun ReorderableRowScope.MoveButtons() {
    IconButton(onClick = { moveUp() }, enabled = canMoveUp) {
        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.reorder_move_up))
    }
    IconButton(onClick = { moveDown() }, enabled = canMoveDown) {
        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.reorder_move_down))
    }
}

/** The row being dragged (or gliding into place) and how far it is from its slot, in pixels. */
private class DragState {
    var key by mutableStateOf<Any?>(null)
    var settlingKey by mutableStateOf<Any?>(null)
    var offset by mutableFloatStateOf(0f)
    var settle: Job? = null
}

private class RowScopeImpl(
    override val isDragging: Boolean,
    override val canMoveUp: Boolean,
    override val canMoveDown: Boolean,
    private val onMoveUp: () -> Unit,
    private val onMoveDown: () -> Unit,
    private val handle: Modifier,
) : ReorderableRowScope {
    override fun moveUp() {
        if (canMoveUp) onMoveUp()
    }

    override fun moveDown() {
        if (canMoveDown) onMoveDown()
    }

    override fun Modifier.dragHandle(): Modifier = then(handle)
}
