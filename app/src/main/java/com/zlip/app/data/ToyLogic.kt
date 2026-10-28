package com.zlip.app.data

import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * The rules of the cover toys, kept free of Android so they can be unit tested: fair results, the
 * decision wheel's slice geometry and the cleaning of the user's own answers.
 *
 * Angles are degrees, clockwise, with 0 pointing up (12 o'clock). Every result is drawn uniformly
 * from [Random]; only the magic ball avoids saying the same thing twice in a row.
 */
object ToyLogic {
    /** Answers and wheel slices a user can add. */
    const val MAX_OPTIONS = 12

    /** Characters per answer or slice: still readable on the small cover screen. */
    const val MAX_OPTION_CHARS = 24

    /** A wheel needs two slices to decide anything; fewer custom ones fall back to the built-in wheel. */
    const val MIN_WHEEL_SLICES = 2

    /** Built-in wheel: Yes, No, Maybe, Ask again (drawn as symbols, named by string resources). */
    const val BUILT_IN_WHEEL_SLICES = 4

    /** Built-in magic ball answers (a string array resource of exactly this size). */
    const val BUILT_IN_BALL_ANSWERS = 20

    /** Dots around the spin-the-bottle dial. */
    const val DIAL_TICKS = 24

    /** The tally counter stops here; five LED digits still fit the cover. */
    const val MAX_COUNT = 99_999

    /** What a resting die shows (thumbnails, widget frames, before the first roll). */
    const val RESTING_FACE = 5

    /** A resting bottle leans to the upper right, which reads as "spin me" better than straight up. */
    const val RESTING_BOTTLE_ANGLE = 35f

    fun rollDie(random: Random = Random): Int = random.nextInt(1, 7)

    /** True = heads. */
    fun flipCoin(random: Random = Random): Boolean = random.nextBoolean()

    /**
     * The faces a tumbling die flashes through: [steps] quick changes, then [final]. Neighbours always
     * differ (also the last flash and [final]), so every change is visible.
     */
    fun tumble(final: Int, steps: Int, random: Random = Random): List<Int> {
        val faces = ArrayList<Int>(steps + 1)
        var previous = 0
        for (step in 0 until steps) {
            var face: Int
            do {
                face = random.nextInt(1, 7)
            } while (face == previous || (step == steps - 1 && face == final))
            faces += face
            previous = face
        }
        faces += final
        return faces
    }

    private val PIPS: Array<List<Pair<Int, Int>>> = arrayOf(
        emptyList(),
        listOf(0 to 0),
        listOf(1 to -1, -1 to 1),
        listOf(1 to -1, 0 to 0, -1 to 1),
        listOf(-1 to -1, 1 to -1, -1 to 1, 1 to 1),
        listOf(-1 to -1, 1 to -1, 0 to 0, -1 to 1, 1 to 1),
        listOf(-1 to -1, 1 to -1, -1 to 0, 1 to 0, -1 to 1, 1 to 1),
    )

    /** Pip centres of a die [face] on a 3x3 grid, as (column, row) in -1..1 (y down). */
    fun pips(face: Int): List<Pair<Int, Int>> = PIPS.getOrElse(face) { emptyList() }

    /**
     * Half turns of a coin flip from [fromHeads] that lands on [toHeads]: 6 or 8 to land on the same
     * side, one more to change it, so the flip looks equally lively either way.
     */
    fun coinHalfTurns(fromHeads: Boolean, toHeads: Boolean, random: Random = Random): Int {
        val even = 2 * random.nextInt(3, 5)
        return if (fromHeads == toHeads) even else even + 1
    }

    /** A random answer in 0 until [count], never [previous] again when there is any other choice. */
    fun pickAnswer(count: Int, previous: Int, random: Random = Random): Int {
        require(count > 0) { "No answers to pick from" }
        if (count == 1 || previous !in 0 until count) return random.nextInt(count)
        val pick = random.nextInt(count - 1)
        return if (pick >= previous) pick + 1 else pick
    }

    /** User answers as stored: one line each, trimmed, at most [MAX_OPTION_CHARS], blanks dropped, at most [MAX_OPTIONS]. */
    fun cleanOptions(options: List<String>): List<String> = options
        .map { it.replace('\n', ' ').trim().takeSafe(MAX_OPTION_CHARS).trim() }
        .filter { it.isNotEmpty() }
        .take(MAX_OPTIONS)

    /** The magic ball's answers: the user's own, or [builtIn] when there are none. */
    fun ballAnswers(options: List<String>, builtIn: List<String>): List<String> = cleanOptions(options).ifEmpty { builtIn }

    /** The user's wheel slices, or null for the built-in wheel (fewer than [MIN_WHEEL_SLICES] of their own). */
    fun customWheelSlices(options: List<String>): List<String>? = cleanOptions(options).takeIf { it.size >= MIN_WHEEL_SLICES }

    /** [degrees] in 0 until 360. */
    fun normalize(degrees: Float): Float {
        val wrapped = degrees % 360f
        return if (wrapped < 0f) wrapped + 360f else wrapped
    }

    /**
     * The slice under the pointer (at 12 o'clock) when the wheel is turned [rotation] degrees clockwise.
     * Slice 0 starts at 12 o'clock and the slices follow each other clockwise.
     */
    fun sliceAt(rotation: Float, count: Int): Int {
        val sweep = 360f / count
        return floor(normalize(-rotation) / sweep).toInt().coerceIn(0, count - 1)
    }

    /** The rotation (0 until 360) that puts [fraction] (0..1) of slice [slice] under the pointer. */
    fun rotationFor(slice: Int, fraction: Float, count: Int): Float = normalize(-(slice + fraction) * 360f / count)

    /** At rest the pointer sits in the middle of the first slice. */
    fun restingWheelRotation(count: Int): Float = rotationFor(0, 0.5f, count)

    /** A spin from [start] to [target] (degrees, target > start) taking [durationMs]. */
    data class Spin(val start: Float, val target: Float, val durationMs: Long)

    /**
     * A fair wheel spin from [current]: every slice is equally likely, it lands well inside the slice
     * (never on a line) after 4-6 full turns, in 3.2-4.5 s.
     */
    fun spinWheel(current: Float, count: Int, random: Random = Random): Spin {
        val start = normalize(current)
        val slice = random.nextInt(count)
        val fraction = 0.12f + random.nextFloat() * 0.76f
        val turns = random.nextInt(4, 7)
        val target = start + turns * 360f + normalize(rotationFor(slice, fraction, count) - start)
        return Spin(start, target, 3_200L + random.nextLong(0, 1_300))
    }

    /** Spin the bottle: a uniformly random direction after 3-5 full turns, in 2-4 s (more turns, longer spin). */
    fun spinBottle(current: Float, random: Random = Random): Spin {
        val start = normalize(current)
        val turns = random.nextInt(3, 6)
        val target = start + turns * 360f + normalize(random.nextFloat() * 360f - start)
        return Spin(start, target, 2_000L + (turns - 3) * 700L + random.nextLong(0, 601))
    }

    /** Fast start, gentle stop: how far a decelerating motion has come at [t] (0..1). */
    fun decelerate(t: Float): Float {
        val left = 1f - t.coerceIn(0f, 1f)
        return 1f - left * left * left
    }

    /**
     * How far the wheel's flapper is bent (0..1) at [rotation]: a peg at each slice line pushes it as
     * it comes near, and it snaps back once the line has passed.
     */
    fun flapperBend(rotation: Float, count: Int): Float {
        val sweep = 360f / count
        val into = normalize(-rotation) % sweep / sweep
        return if (into < FLAPPER_REACH) (FLAPPER_REACH - into) / FLAPPER_REACH else 0f
    }

    private const val FLAPPER_REACH = 0.22f

    /**
     * Which of three tints slice [index] of [count] uses: alternating, plus a third one for the last
     * slice of an odd count, so no two neighbours (the last and the first included) ever match.
     */
    fun sliceTint(index: Int, count: Int): Int = if (count % 2 == 1 && index == count - 1) 2 else index % 2

    /** The dial dot (0 until [ticks], 0 at 12 o'clock) nearest to [angle]. */
    fun nearestTick(angle: Float, ticks: Int = DIAL_TICKS): Int = (normalize(angle) / (360f / ticks)).roundToInt() % ticks

    /** The hour on a clock face that [angle] points to (12 = up), for spoken descriptions. */
    fun clockHour(angle: Float): Int {
        val hour = (normalize(angle) / 30f).roundToInt() % 12
        return if (hour == 0) 12 else hour
    }

    fun nextCount(count: Int): Int = (count + 1).coerceIn(0, MAX_COUNT)

    /** Default Pomodoro focus session length: 25 minutes. */
    const val POMODORO_DURATION_MS = 25 * 60 * 1_000L

    /** Studio light modes: 0 = Ring light, 1 = Full softbox, 2 = Strobe beacon. */
    const val FLASHLIGHT_MODES = 3

    /** Rock-Paper-Scissors moves: 0 = Rock, 1 = Paper, 2 = Scissors. */
    const val RPS_ROCK = 0
    const val RPS_PAPER = 1
    const val RPS_SCISSORS = 2

    /** Formats remaining timer [millis] (rounded up to whole seconds) as MM:SS. */
    fun formatTimerMmSs(millis: Long): String {
        val totalSeconds = ((millis.coerceAtLeast(0L) + 999L) / 1_000L).coerceAtMost(99 * 60 + 59)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(java.util.Locale.ROOT, minutes, seconds)
    }

    /** Formats elapsed stopwatch [millis] as MM:SS for the main LED matrix. */
    fun formatStopwatchMmSs(millis: Long): String {
        val totalSeconds = (millis.coerceAtLeast(0L) / 1_000L).coerceAtMost(99 * 60 + 59)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(java.util.Locale.ROOT, minutes, seconds)
    }

    /** Formats elapsed stopwatch [millis] with tenths of a second (MM:SS.d) for the result band. */
    fun formatStopwatchTenths(millis: Long): String {
        val clamped = millis.coerceAtLeast(0L)
        val totalSeconds = (clamped / 1_000L).coerceAtMost(99 * 60 + 59)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val tenths = (clamped % 1_000L) / 100L
        return "%02d:%02d.%d".format(java.util.Locale.ROOT, minutes, seconds, tenths)
    }

    /** Fair Rock-Paper-Scissors draw in 0..2. */
    fun pickRps(random: Random = Random): Int = random.nextInt(3)

    /** Quick cycling sequence of RPS moves ending on [final], with no two adjacent steps identical. */
    fun tumbleRps(final: Int, steps: Int = 6, random: Random = Random): List<Int> {
        val list = ArrayList<Int>(steps + 1)
        var prev = -1
        for (step in 0 until steps) {
            var move: Int
            do {
                move = random.nextInt(3)
            } while (move == prev || (step == steps - 1 && move == final))
            list += move
            prev = move
        }
        list += final.coerceIn(0, 2)
        return list
    }
}
