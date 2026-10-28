package com.zlip.app.render

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zlip.app.R
import com.zlip.app.data.CoverItem
import com.zlip.app.data.ToyLogic
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

private const val DICE_MS = 720L
private const val DICE_TUMBLES = 7
private const val COIN_MS = 850L
private const val SETTLE_MS = 260L
private const val BUMP_MS = 170L

/** Wheel ticks closer together than this merge into one, so a fast spin purrs instead of buzzing. */
private const val TICK_GAP_MS = 40L

/** How far (degrees) the passing pegs bend the wheel's flapper. */
private const val FLAPPER_DEGREES = 24f

private val PI_F = PI.toFloat()

/** Dice: a tap tumbles the die (quick face changes, a spin and two hops) and it lands on 1-6. */
@Composable
internal fun DiceToy(item: CoverItem.Toy, still: Boolean, playable: Boolean) {
    val painter = remember { ToyPainter() }
    val haptics = LocalHapticFeedback.current
    val fps = LocalAnimationFps.current
    val animator = rememberToyAnimator()
    val landed = if (still) null else ToyMemory[item]?.toInt()
    val face = landed ?: ToyLogic.RESTING_FACE
    var rolling by remember { mutableStateOf(false) }
    // 0 while resting; otherwise the face flashing by mid-roll.
    val flash = remember { mutableIntStateOf(0) }
    val spin = remember { mutableFloatStateOf(0f) }
    val lift = remember { mutableFloatStateOf(0f) }
    val color = item.color

    fun roll() {
        if (rolling) return
        rolling = true
        val result = ToyLogic.rollDie()
        val faces = ToyLogic.tumble(result, DICE_TUMBLES)
        val startSpin = (if (Random.nextBoolean()) 1f else -1f) * (150f + Random.nextFloat() * 120f)
        animator.play {
            animateToy(DICE_MS, fps) { t ->
                val eased = ToyLogic.decelerate(t)
                // Faces change quickly at first and slow down as the die settles.
                flash.intValue = faces[(eased * faces.lastIndex).toInt().coerceAtMost(faces.lastIndex)]
                spin.floatValue = startSpin * (1f - eased)
                lift.floatValue = abs(sin(t * 2f * PI_F)) * (1f - t)
            }
            ToyMemory[item] = result.toFloat()
            flash.intValue = 0
            rolling = false
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        }
    }

    ToyStage(
        playable = playable,
        description = stringResource(R.string.toy_cd_dice, face),
        tapLabel = stringResource(R.string.toy_action_roll),
        onTap = ::roll,
        band = {
            if (playable && landed == null && !rolling) ToyHint(stringResource(R.string.toy_hint_dice), item.background)
        },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val side = ToyPainter.dieSide(size.width, size.height)
            val up = lift.floatValue
            drawIntoCanvas {
                painter.drawShadow(it.nativeCanvas, center.x, center.y + side * 0.62f, side * (0.95f - 0.3f * up), 1f - 0.6f * up)
            }
        }
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val up = lift.floatValue
                    rotationZ = spin.floatValue
                    scaleX = 1f + 0.14f * up
                    scaleY = 1f + 0.14f * up
                    translationY = -up * size.minDimension * 0.16f
                },
        ) {
            val side = ToyPainter.dieSide(size.width, size.height)
            val shown = flash.intValue.takeIf { it != 0 } ?: face
            drawIntoCanvas { painter.drawDie(it.nativeCanvas, center.x, center.y, side, shown, color) }
        }
    }
}

/** Coin flip: a tap tosses the coin, it turns over a few times in the air and lands heads or tails. */
@Composable
internal fun CoinToy(item: CoverItem.Toy, still: Boolean, playable: Boolean) {
    val painter = remember { ToyPainter() }
    val haptics = LocalHapticFeedback.current
    val fps = LocalAnimationFps.current
    val animator = rememberToyAnimator()
    val stored = if (still) null else ToyMemory[item]
    val heads = stored?.let { it >= 0.5f } ?: true
    var flipping by remember { mutableStateOf(false) }
    // The side that was up when the flip started; the turn decides which side shows now.
    var startHeads by remember { mutableStateOf(true) }
    val turn = remember { mutableFloatStateOf(0f) }
    val lift = remember { mutableFloatStateOf(0f) }
    val labelAlpha = remember { mutableFloatStateOf(1f) }
    val color = item.color
    val side = stringResource(if (heads) R.string.toy_heads else R.string.toy_tails)

    fun flip() {
        if (flipping) return
        flipping = true
        val result = ToyLogic.flipCoin()
        val halfTurns = ToyLogic.coinHalfTurns(heads, result)
        startHeads = heads
        animator.play {
            val fadeFrom = labelAlpha.floatValue
            animateToy(COIN_MS, fps) { t ->
                // Spins fast as it leaves the hand and a little slower as it comes down.
                turn.floatValue = halfTurns * PI_F * (t * (2f - t))
                lift.floatValue = sin(t * PI_F)
                labelAlpha.floatValue = fadeFrom * (1f - (t * 5f).coerceAtMost(1f))
            }
            ToyMemory[item] = if (result) 1f else 0f
            turn.floatValue = 0f
            lift.floatValue = 0f
            flipping = false
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            animateToy(SETTLE_MS, fps) { labelAlpha.floatValue = it }
        }
    }

    ToyStage(
        playable = playable,
        description = stringResource(R.string.toy_cd_coin, side),
        tapLabel = stringResource(R.string.toy_action_flip),
        onTap = ::flip,
        band = {
            when {
                stored != null -> ToyResult(side, Color(ToyPainter.accentOn(color, item.background))) { labelAlpha.floatValue }
                playable && !flipping -> ToyHint(stringResource(R.string.toy_hint_coin), item.background)
            }
        },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = ToyPainter.coinRadius(size.width, size.height)
            val up = lift.floatValue
            drawIntoCanvas {
                painter.drawShadow(it.nativeCanvas, center.x, center.y + radius * 1.14f, radius * (1.7f - 0.7f * up), 1f - 0.7f * up)
            }
        }
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val up = lift.floatValue
                    translationY = -up * size.height * 0.2f
                    scaleX = 1f + 0.16f * up
                    scaleY = 1f + 0.16f * up
                },
        ) {
            val radius = ToyPainter.coinRadius(size.width, size.height)
            val angle = turn.floatValue
            // Every half turn shows the other side.
            val flipped = ((angle / PI_F + 0.5f).toInt() % 2) == 1
            val base = if (flipping) startHeads else heads
            val showHeads = if (flipped) !base else base
            drawIntoCanvas {
                painter.drawCoin(it.nativeCanvas, center.x, center.y, radius, color, showHeads, abs(cos(angle)), sin(angle))
            }
        }
    }
}

/** Decision wheel: a tap spins it; it ticks past the pegs, slows down and lands on one choice. */
@Composable
internal fun WheelToy(item: CoverItem.Toy, still: Boolean, playable: Boolean) {
    val painter = remember { ToyPainter() }
    val haptics = LocalHapticFeedback.current
    val fps = LocalAnimationFps.current
    val animator = rememberToyAnimator()
    val custom = remember(item.options) { ToyLogic.customWheelSlices(item.options) }
    val builtIn = stringArrayResource(R.array.toy_wheel_defaults)
    val count = custom?.size ?: ToyLogic.BUILT_IN_WHEEL_SLICES
    val stored = if (still) null else ToyMemory[item]
    val resting = stored ?: ToyLogic.restingWheelRotation(count)
    val result = stored?.let { ToyLogic.sliceAt(it, count) }
    val resultText = result?.let { custom?.getOrNull(it) ?: builtIn.getOrNull(it) }
    var spinning by remember { mutableStateOf(false) }
    val rotation = remember { mutableFloatStateOf(0f) }
    val emphasis = remember { mutableFloatStateOf(1f) }
    val color = item.color

    fun spin() {
        if (spinning) return
        spinning = true
        val spin = ToyLogic.spinWheel(resting, count)
        val sweep = 360f / count
        rotation.floatValue = spin.start
        emphasis.floatValue = 0f
        animator.play {
            var lastPass = floor(spin.start / sweep).toInt()
            var lastTick = 0L
            animateToy(spin.durationMs, fps) { t ->
                val now = spin.start + (spin.target - spin.start) * ToyLogic.decelerate(t)
                rotation.floatValue = now
                val pass = floor(now / sweep).toInt()
                if (pass != lastPass) {
                    lastPass = pass
                    val time = SystemClock.uptimeMillis()
                    if (time - lastTick >= TICK_GAP_MS) {
                        lastTick = time
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                    }
                }
            }
            ToyMemory[item] = ToyLogic.normalize(spin.target)
            spinning = false
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            animateToy(SETTLE_MS, fps) { emphasis.floatValue = it }
        }
    }

    ToyStage(
        playable = playable,
        description = resultText?.let { stringResource(R.string.toy_cd_wheel, it) } ?: stringResource(R.string.toy_wheel),
        tapLabel = stringResource(R.string.toy_action_spin),
        onTap = ::spin,
        band = {
            when {
                resultText != null && !spinning -> ToyResult(resultText, Color(ToyPainter.accentOn(color, item.background))) { emphasis.floatValue }
                playable && stored == null && !spinning -> ToyHint(stringResource(R.string.toy_hint_wheel), item.background)
            }
        },
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (size.height > 0f) {
                        // Turn about the wheel's own centre, which sits a little below the middle.
                        val radius = ToyPainter.wheelRadius(size.width, size.height)
                        transformOrigin = TransformOrigin(0.5f, ToyPainter.wheelCenterY(size.height, radius) / size.height)
                    }
                    rotationZ = if (spinning) rotation.floatValue else resting
                },
        ) {
            val radius = ToyPainter.wheelRadius(size.width, size.height)
            val highlight = if (spinning) -1 else result ?: -1
            drawIntoCanvas {
                painter.drawWheel(
                    it.nativeCanvas,
                    center.x,
                    ToyPainter.wheelCenterY(size.height, radius),
                    radius,
                    custom,
                    color,
                    highlight,
                    emphasis.floatValue,
                )
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            val radius = ToyPainter.wheelRadius(size.width, size.height)
            val bend = if (spinning) ToyLogic.flapperBend(rotation.floatValue, count) else 0f
            drawIntoCanvas {
                painter.drawWheelPointer(it.nativeCanvas, center.x, ToyPainter.wheelCenterY(size.height, radius), radius, -bend * FLAPPER_DEGREES)
            }
        }
    }
}

/** Tally counter: a tap adds one (with a little bump), a long press resets it to zero. */
@Composable
internal fun CounterToy(item: CoverItem.Toy, still: Boolean, playable: Boolean) {
    val painter = remember { ToyPainter() }
    val haptics = LocalHapticFeedback.current
    val fps = LocalAnimationFps.current
    val animator = rememberToyAnimator()
    val count = if (still) 0 else ToyMemory[item]?.toInt() ?: 0
    val bump = remember { mutableFloatStateOf(0f) }
    val color = item.color

    fun pulse(direction: Float, durationMs: Long) {
        animator.play {
            val from = bump.floatValue
            animateToy(durationMs, fps) { t -> bump.floatValue = from * (1f - t) + direction * sin(t * PI_F) * (1f - abs(from)) }
        }
    }

    fun add() {
        // Read from memory, not the composed value: two taps can land before the next recomposition.
        ToyMemory[item] = ToyLogic.nextCount(ToyMemory[item]?.toInt() ?: 0).toFloat()
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        pulse(1f, BUMP_MS)
    }

    fun reset() {
        ToyMemory[item] = 0f
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        pulse(-1f, BUMP_MS * 2)
    }

    ToyStage(
        playable = playable,
        description = stringResource(R.string.toy_cd_counter, count),
        tapLabel = stringResource(R.string.toy_action_count),
        onTap = ::add,
        onLongPress = ::reset,
        longPressLabel = stringResource(R.string.toy_action_reset),
        band = {
            if (playable && count == 0) ToyHint(stringResource(R.string.toy_hint_counter), item.background)
        },
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = 1f + 0.07f * bump.floatValue
                    scaleY = 1f + 0.07f * bump.floatValue
                },
        ) {
            drawIntoCanvas {
                painter.drawCounter(it.nativeCanvas, center.x, center.y, ToyPainter.counterDiameter(size.width, size.height), count, color)
            }
        }
    }
}

/** Pomodoro focus timer (25 min): tap to start/pause, long-press to reset. */
@Composable
internal fun PomodoroToy(item: CoverItem.Toy, still: Boolean, playable: Boolean) {
    val painter = remember { ToyPainter() }
    val haptics = LocalHapticFeedback.current
    val fps = LocalAnimationFps.current
    val animator = rememberToyAnimator()
    var remainingMs by remember(item.id) {
        mutableLongStateOf(
            if (still) ToyLogic.POMODORO_DURATION_MS
            else ToyMemory[item]?.toLong()?.takeIf { it in 0L..ToyLogic.POMODORO_DURATION_MS } ?: ToyLogic.POMODORO_DURATION_MS,
        )
    }
    var running by remember(item.id) { mutableStateOf(false) }
    val bump = remember { mutableFloatStateOf(0f) }
    val color = item.color

    LaunchedEffect(running, still) {
        if (!running || still) return@LaunchedEffect
        var lastTick = SystemClock.elapsedRealtime()
        while (running) {
            delay(200L)
            val now = SystemClock.elapsedRealtime()
            val delta = (now - lastTick).coerceAtLeast(0L)
            lastTick = now
            val next = (remainingMs - delta).coerceAtLeast(0L)
            remainingMs = next
            ToyMemory[item] = next.toFloat()
            if (next <= 0L) {
                running = false
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            }
        }
    }

    fun pulse(direction: Float) {
        animator.play {
            val from = bump.floatValue
            animateToy(BUMP_MS, fps) { t -> bump.floatValue = from * (1f - t) + direction * sin(t * PI_F) * (1f - abs(from)) }
        }
    }

    fun toggle() {
        if (remainingMs <= 0L) {
            remainingMs = ToyLogic.POMODORO_DURATION_MS
            ToyMemory[item] = remainingMs.toFloat()
            running = true
        } else {
            running = !running
        }
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        pulse(1f)
    }

    fun reset() {
        running = false
        remainingMs = ToyLogic.POMODORO_DURATION_MS
        ToyMemory[item] = remainingMs.toFloat()
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        pulse(-1f)
    }

    val timeStr = ToyLogic.formatTimerMmSs(if (still) ToyLogic.POMODORO_DURATION_MS else remainingMs)
    ToyStage(
        playable = playable,
        description = stringResource(R.string.toy_cd_pomodoro, timeStr),
        tapLabel = stringResource(if (running) R.string.action_pause else R.string.action_resume),
        onTap = ::toggle,
        onLongPress = ::reset,
        longPressLabel = stringResource(R.string.toy_action_reset),
        band = {
            when {
                !still && remainingMs <= 0L -> ToyResult(stringResource(R.string.toy_pomodoro_done), Color(ToyPainter.accentOn(color, item.background))) { 1f }
                !still && running -> ToyResult(stringResource(R.string.toy_pomodoro_focus), Color(ToyPainter.accentOn(color, item.background))) { 1f }
                playable -> ToyHint(stringResource(R.string.toy_hint_pomodoro), item.background)
            }
        },
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = 1f + 0.05f * bump.floatValue
                    scaleY = 1f + 0.05f * bump.floatValue
                },
        ) {
            drawIntoCanvas {
                painter.drawPomodoro(
                    it.nativeCanvas,
                    center.x,
                    center.y,
                    ToyPainter.counterDiameter(size.width, size.height),
                    if (still) ToyLogic.POMODORO_DURATION_MS else remainingMs,
                    ToyLogic.POMODORO_DURATION_MS,
                    running && !still,
                    color,
                )
            }
        }
    }
}

/** Stopwatch: tap to start/stop, long-press to reset to 00:00. */
@Composable
internal fun StopwatchToy(item: CoverItem.Toy, still: Boolean, playable: Boolean) {
    val painter = remember { ToyPainter() }
    val haptics = LocalHapticFeedback.current
    val fps = LocalAnimationFps.current
    val animator = rememberToyAnimator()
    var elapsedMs by remember(item.id) {
        mutableLongStateOf(if (still) 0L else ToyMemory[item]?.toLong()?.coerceAtLeast(0L) ?: 0L)
    }
    var running by remember(item.id) { mutableStateOf(false) }
    val bump = remember { mutableFloatStateOf(0f) }
    val color = item.color

    LaunchedEffect(running, still) {
        if (!running || still) return@LaunchedEffect
        var lastTick = SystemClock.elapsedRealtime()
        while (running) {
            delay(80L)
            val now = SystemClock.elapsedRealtime()
            val delta = (now - lastTick).coerceAtLeast(0L)
            lastTick = now
            elapsedMs += delta
            ToyMemory[item] = elapsedMs.toFloat()
        }
    }

    fun pulse(direction: Float) {
        animator.play {
            val from = bump.floatValue
            animateToy(BUMP_MS, fps) { t -> bump.floatValue = from * (1f - t) + direction * sin(t * PI_F) * (1f - abs(from)) }
        }
    }

    fun toggle() {
        running = !running
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        pulse(1f)
    }

    fun reset() {
        running = false
        elapsedMs = 0L
        ToyMemory[item] = 0f
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        pulse(-1f)
    }

    val shownMs = if (still) 0L else elapsedMs
    ToyStage(
        playable = playable,
        description = stringResource(R.string.toy_cd_stopwatch, ToyLogic.formatStopwatchMmSs(shownMs)),
        tapLabel = stringResource(if (running) R.string.action_pause else R.string.action_resume),
        onTap = ::toggle,
        onLongPress = ::reset,
        longPressLabel = stringResource(R.string.toy_action_reset),
        band = {
            when {
                shownMs > 0L -> ToyResult(ToyLogic.formatStopwatchTenths(shownMs), Color(ToyPainter.accentOn(color, item.background))) { 1f }
                playable -> ToyHint(stringResource(R.string.toy_hint_stopwatch), item.background)
            }
        },
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = 1f + 0.05f * bump.floatValue
                    scaleY = 1f + 0.05f * bump.floatValue
                },
        ) {
            drawIntoCanvas {
                painter.drawStopwatch(
                    it.nativeCanvas,
                    center.x,
                    center.y,
                    ToyPainter.counterDiameter(size.width, size.height),
                    shownMs,
                    running && !still,
                    color,
                )
            }
        }
    }
}
