package com.zlip.app.cover.mirror

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pinch
import androidx.compose.material.icons.filled.SwipeVertical
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zlip.app.R
import com.zlip.app.ui.components.label
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Thin bars along the right and bottom edges for each axis with more of the screen out of view: how much shows and
 * where. Only while the view moves and [SCROLLBAR_LINGER_MS] after, so they never sit on top of the mirror.
 */
@Composable
internal fun MirrorScrollbars(placement: State<MirrorLayout?>, modifier: Modifier = Modifier) {
    var moving by remember { mutableStateOf(false) }
    LaunchedEffect(placement) {
        snapshotFlow { placement.value?.let { it.frame to it.offset } }
            .filterNotNull()
            .distinctUntilChanged()
            .collectLatest {
                moving = true
                delay(SCROLLBAR_LINGER_MS)
                moving = false
            }
    }
    val alpha = animateFloatAsState(if (moving) 1f else 0f, tween(if (moving) 120 else 400), label = "mirrorScrollbars")
    Canvas(
        modifier
            .clearAndSetSemantics {}
            .graphicsLayer { this.alpha = alpha.value },
    ) {
        if (alpha.value == 0f) return@Canvas
        val placed = placement.value ?: return@Canvas
        if (placed.canPanY) {
            scrollbar(
                vertical = true,
                shown = size.height / placed.frame.height,
                position = -placed.offset.y / (placed.frame.height - size.height),
            )
        }
        if (placed.canPanX) {
            scrollbar(
                vertical = false,
                shown = size.width / placed.frame.width,
                position = -placed.offset.x / (placed.frame.width - size.width),
            )
        }
    }
}

/** A faint track with a bright thumb ([shown] of it long, at [position] 0..1), haloed so it reads on white too. */
private fun DrawScope.scrollbar(vertical: Boolean, shown: Float, position: Float) {
    val thickness = 3.dp.toPx()
    // Inside the unlocked frame's 3 dp border, and clear of the cover's rounded corners.
    val inset = 8.dp.toPx()
    val margin = 20.dp.toPx()
    val halo = 1.dp.toPx()
    val track = (if (vertical) size.height else size.width) - 2 * margin
    if (track <= 0f) return
    val thumb = max(MIN_THUMB_DP.dp.toPx(), track * shown.coerceIn(0f, 1f)).coerceAtMost(track)
    val start = margin + (track - thumb) * position.coerceIn(0f, 1f)
    val across = (if (vertical) size.width else size.height) - inset - thickness
    fun bar(color: Color, from: Float, length: Float, grow: Float) {
        val topLeft = if (vertical) Offset(across - grow, from - grow) else Offset(from - grow, across - grow)
        val extent = if (vertical) Size(thickness + 2 * grow, length + 2 * grow) else Size(length + 2 * grow, thickness + 2 * grow)
        drawRoundRect(color, topLeft, extent, CornerRadius(thickness / 2 + grow))
    }
    bar(Color.White.copy(alpha = 0.18f), margin, track, 0f)
    bar(Color.Black.copy(alpha = 0.4f), start, thumb, halo)
    bar(Color.White.copy(alpha = 0.92f), start, thumb, 0f)
}

/**
 * TalkBack cannot drag or pinch the cover: the mirror offers the same as actions, each only while it can do
 * something (no "scroll down" at the bottom), and says which view it is in.
 */
@Composable
internal fun MirrorAccessibility(
    controller: MirrorController,
    placement: State<MirrorLayout?>,
    cover: IntSize,
    modifier: Modifier = Modifier,
) {
    val room by remember(placement, cover) { derivedStateOf { MirrorRoom.of(placement.value, cover) } }
    val preset by controller.preset.collectAsState()
    val view = controller.view.collectAsState()
    // In steps of 10 %: a pinch must not recompose this on every frame.
    val percent by remember(view) { derivedStateOf { (view.value.zoom * 10).roundToInt() * 10 } }
    val description = stringResource(R.string.mirror_view_desc)
    val state = preset?.let { stringResource(it.label) } ?: stringResource(R.string.mirror_zoom_state, percent)
    val up = stringResource(R.string.mirror_scroll_up)
    val down = stringResource(R.string.mirror_scroll_down)
    val left = stringResource(R.string.mirror_scroll_left)
    val right = stringResource(R.string.mirror_scroll_right)
    val zoomIn = stringResource(R.string.mirror_zoom_in)
    val zoomOut = stringResource(R.string.mirror_zoom_out)
    val toggle = stringResource(R.string.mirror_toggle_view)
    val pageX = cover.width * PAGE
    val pageY = cover.height * PAGE
    Box(
        modifier.semantics {
            contentDescription = description
            stateDescription = state
            customActions = buildList {
                // "Scroll down" brings in what lies below: the screen moves up, as under a finger swiping up.
                if (room.up) add(CustomAccessibilityAction(up) { controller.scrollBy(0f, pageY); true })
                if (room.down) add(CustomAccessibilityAction(down) { controller.scrollBy(0f, -pageY); true })
                if (room.left) add(CustomAccessibilityAction(left) { controller.scrollBy(pageX, 0f); true })
                if (room.right) add(CustomAccessibilityAction(right) { controller.scrollBy(-pageX, 0f); true })
                if (room.zoomIn) add(CustomAccessibilityAction(zoomIn) { controller.zoomStep(closer = true); true })
                if (room.zoomOut) add(CustomAccessibilityAction(zoomOut) { controller.zoomStep(closer = false); true })
                add(CustomAccessibilityAction(toggle) { controller.toggleView(); true })
            }
        },
    )
}

/** Where the view can still go; compared by value, so moving within the same room does not recompose. */
private data class MirrorRoom(
    val up: Boolean = false,
    val down: Boolean = false,
    val left: Boolean = false,
    val right: Boolean = false,
    val zoomIn: Boolean = false,
    val zoomOut: Boolean = false,
) {
    companion object {
        fun of(placed: MirrorLayout?, cover: IntSize): MirrorRoom = placed?.let {
            MirrorRoom(
                up = it.canPanY && it.offset.y < 0,
                down = it.canPanY && it.offset.y + it.frame.height > cover.height,
                left = it.canPanX && it.offset.x < 0,
                right = it.canPanX && it.offset.x + it.frame.width > cover.width,
                zoomIn = it.canZoomIn,
                zoomOut = it.canZoomOut,
            )
        } ?: MirrorRoom()
    }
}

/**
 * How to steer the mirror, once per mirroring session after the first unlock: drag, pinch, tap. Not for TalkBack,
 * which gets the same as actions on the mirror.
 */
@Composable
fun MirrorGestureHint(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clearAndSetSemantics {}
            .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        HintLine(Icons.Filled.SwipeVertical, stringResource(R.string.mirror_hint_pan))
        HintLine(Icons.Filled.Pinch, stringResource(R.string.mirror_hint_zoom))
        HintLine(Icons.Filled.TouchApp, stringResource(R.string.mirror_hint_tap))
    }
}

@Composable
private fun HintLine(icon: ImageVector, text: String) {
    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = Color.White, fontSize = 13.sp)
    }
}

/** How long the scroll bars stay after the view stopped moving. */
private const val SCROLLBAR_LINGER_MS = 1_200L

private const val MIN_THUMB_DP = 24

/** One TalkBack scroll: most of a cover, so a line or two stays in view for context. */
private const val PAGE = 0.8f
