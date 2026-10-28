package com.zlip.app.render

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.key
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.zlip.app.data.CoverItem
import com.zlip.app.data.ToyKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min

/**
 * A cover toy: a tiny game played with a tap, right on the folded cover. While [interactive] (and not a
 * [Playback.STILL] thumbnail) taps play it; otherwise no touch handler is installed at all, so the cover
 * around it sees every touch. Animations run only in answer to a tap and stop when they land.
 */
@Composable
internal fun ToyCover(item: CoverItem.Toy, modifier: Modifier, playback: Playback, interactive: Boolean) {
    val still = playback == Playback.STILL
    val playable = interactive && !still
    ToyMemory.attach(LocalContext.current)
    Box(
        modifier
            .background(Color(item.background))
            .clipToBounds(),
    ) {
        // A different toy starts fresh; color or answer edits keep the toy's state.
        key(item.kind) {
            when (item.kind) {
                ToyKind.DICE -> DiceToy(item, still, playable)
                ToyKind.COIN -> CoinToy(item, still, playable)
                ToyKind.WHEEL -> WheelToy(item, still, playable)
                ToyKind.COUNTER -> CounterToy(item, still, playable)
                ToyKind.POMODORO -> PomodoroToy(item, still, playable)
                ToyKind.STOPWATCH -> StopwatchToy(item, still, playable)
            }
        }
    }
}

/** [kind]'s resting face filling the box, without band or input: pickers and lists. */
@Composable
internal fun ToyGlyph(kind: ToyKind, color: Int, modifier: Modifier = Modifier) {
    val painter = remember { ToyPainter() }
    Canvas(modifier) {
        drawIntoCanvas { painter.drawResting(it.nativeCanvas, kind, color, emptyList(), 0f, 0f, size.width, size.height) }
    }
}

/**
 * Where each toy last landed: a toy keeps its roll, spin or count when the user swipes away and back,
 * and the editor's preview and the cover show the same thing. Rolls and spins last as long as the app
 * runs; a tally count is the user's data, so it is also saved and survives the process being killed.
 */
internal object ToyMemory {
    private val results = mutableStateMapOf<String, Float>()

    /** Saved tally counts; read only for a counter that has no count in memory yet. */
    private var counts: SharedPreferences? = null

    /** Opens the saved counts; [ToyCover] calls it before any toy reads its result. */
    fun attach(context: Context) {
        if (counts == null) counts = context.applicationContext.getSharedPreferences(COUNTS_FILE, Context.MODE_PRIVATE)
    }

    operator fun get(item: CoverItem.Toy): Float? {
        val key = key(item)
        return results[key] ?: if (item.kind == ToyKind.COUNTER) savedCount(key) else null
    }

    operator fun set(item: CoverItem.Toy, value: Float) {
        val key = key(item)
        results[key] = value
        if (item.kind == ToyKind.COUNTER) {
            // edit {} applies in the background; a count is a few bytes, so every tap can be saved.
            counts?.edit { if (value == 0f) remove(key) else putInt(key, value.toInt()) }
        }
    }

    private fun savedCount(key: String): Float? = counts?.takeIf { it.contains(key) }?.getInt(key, 0)?.toFloat()

    private fun key(item: CoverItem.Toy) = "${item.id}/${item.kind.name}"
}

private const val COUNTS_FILE = "zlip_toy_counts"

/** One 60 Hz frame; toy animations sleep the rest of their frame budget, then wait for vsync. */
private const val TOY_VSYNC_MS = 16L

/**
 * Calls [onFrame] with the elapsed share (0..1) of [durationMs], stepping at [fps] and ending exactly on
 * 1. Waits on the frame clock, so nothing runs while the screen is not being drawn.
 */
internal suspend fun animateToy(durationMs: Long, fps: Int, onFrame: (Float) -> Unit) {
    val frameMs = 1_000L / fps.coerceIn(1, 120)
    val start = withFrameNanos { it }
    onFrame(0f)
    while (true) {
        delay((frameMs - TOY_VSYNC_MS).coerceAtLeast(0))
        val t = withFrameNanos { now -> ((now - start) / 1_000_000f / durationMs).coerceIn(0f, 1f) }
        onFrame(t)
        if (t >= 1f) return
    }
}

/** Runs one toy animation at a time; a new one replaces whatever is still fading out. */
internal class ToyAnimator(private val scope: CoroutineScope) {
    private var job: Job? = null

    fun play(block: suspend CoroutineScope.() -> Unit) {
        job?.cancel()
        job = scope.launch(block = block)
    }
}

@Composable
internal fun rememberToyAnimator(): ToyAnimator {
    val scope = rememberCoroutineScope()
    return remember(scope) { ToyAnimator(scope) }
}

/**
 * What every toy shares: the toy fills the upper [TOY_AREA_FRACTION], [band] sits below. While
 * [playable], taps (and long presses, if given) go to the toy and TalkBack gets the same actions, with
 * [description] read out whenever it changes.
 */
@Composable
internal fun ToyStage(
    playable: Boolean,
    description: String,
    tapLabel: String,
    onTap: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    longPressLabel: String? = null,
    band: @Composable BoxScope.() -> Unit = {},
    toy: @Composable BoxScope.() -> Unit,
) {
    val currentTap by rememberUpdatedState(onTap)
    val currentLongPress by rememberUpdatedState(onLongPress)
    val hasLongPress = onLongPress != null
    val input = if (playable) {
        Modifier
            .pointerInput(hasLongPress) {
                detectToyTaps(
                    onTap = { currentTap() },
                    onLongPress = if (hasLongPress) {
                        { currentLongPress?.invoke() }
                    } else {
                        null
                    },
                )
            }
            .semantics(mergeDescendants = true) {
                contentDescription = description
                liveRegion = LiveRegionMode.Polite
                onClick(label = tapLabel) {
                    currentTap()
                    true
                }
                if (hasLongPress) {
                    onLongClick(label = longPressLabel) {
                        currentLongPress?.invoke()
                        true
                    }
                }
            }
    } else {
        Modifier
    }
    Column(
        Modifier
            .fillMaxSize()
            .then(input),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .weight(TOY_AREA_FRACTION),
            content = toy,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f - TOY_AREA_FRACTION)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
            content = band,
        )
    }
}

private enum class Press { TAP, HOLD, MOVE, CANCEL }

/**
 * Taps and (with [onLongPress]) long presses for a toy. The cover around it consumes every move it
 * sees and reads swipes itself, so consumption cannot tell a tap from a swipe here; distance does. A
 * finger that travels past the touch slop is left to the cover as a swipe. A tap's release is consumed,
 * so the cover does not also treat it as its own tap (pause, controls); so is everything after a long
 * press. The down is never consumed: the cover still sees each touch start (its idle lock counts it).
 */
private suspend fun PointerInputScope.detectToyTaps(onTap: () -> Unit, onLongPress: (() -> Unit)?) {
    awaitEachGesture {
        val down = awaitFirstDown()
        val slop = viewConfiguration.touchSlop
        val press = if (onLongPress == null) {
            trackPress(down, slop)
        } else {
            withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { trackPress(down, slop) } ?: Press.HOLD
        }
        when (press) {
            Press.TAP -> onTap()
            Press.HOLD -> {
                onLongPress?.invoke()
                do {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id }
                    change?.consume()
                } while (change != null && change.pressed)
            }
            Press.MOVE, Press.CANCEL -> Unit
        }
    }
}

private suspend fun AwaitPointerEventScope.trackPress(down: PointerInputChange, slop: Float): Press {
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == down.id } ?: return Press.CANCEL
        // A second finger makes it a pinch or a two-finger swipe, not a tap.
        if (event.changes.count { it.pressed } > 1) return Press.MOVE
        if ((change.position - down.position).getDistance() > slop) return Press.MOVE
        if (!change.pressed) {
            change.consume()
            return Press.TAP
        }
    }
}

/** The small "Tap to roll" pill under a resting toy; hidden when the band is too small to read it. */
@Composable
internal fun ToyHint(text: String, background: Int) {
    val ink = Color(ToyPainter.inkOn(background))
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (maxHeight < 26.dp) return@BoxWithConstraints
        val fontSize = with(density) { min(13.sp.toPx(), (maxHeight * 0.3f).toPx()).toSp() }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(ink.copy(alpha = 0.09f))
                .padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.TouchApp,
                contentDescription = null,
                tint = ink.copy(alpha = 0.7f),
                modifier = Modifier.size(with(density) { fontSize.toDp() * 1.25f }),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = text,
                color = ink.copy(alpha = 0.8f),
                fontSize = fontSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A toy's result in the band, sized to it (so it also fits small collage cells): LED dots when every
 * character has one and they stay large enough, otherwise bold type. [alpha] is read while drawing only.
 */
@Composable
internal fun ToyResult(text: String, color: Color, alpha: () -> Float) {
    val density = LocalDensity.current
    val locale = LocalConfiguration.current.locales[0]
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha() },
        contentAlignment = Alignment.Center,
    ) {
        val upper = remember(text, locale) { text.uppercase(locale) }
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val pitch = minOf(widthPx / DotFont.width(upper).coerceAtLeast(1), heightPx * 0.6f / DotFont.HEIGHT, with(density) { 5.dp.toPx() })
        if (ToyPainter.ledSupports(upper) && pitch >= with(density) { 1.8.dp.toPx() }) {
            DotMatrixText(upper, color, maxPitch = with(density) { pitch.toDp() })
        } else {
            Text(
                text = text,
                color = color,
                fontSize = with(density) { min(28.sp.toPx(), heightPx * 0.42f).toSp() },
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.02.em,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
