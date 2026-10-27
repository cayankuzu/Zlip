package com.zlip.app.data

import kotlin.random.Random

/**
 * How long things stay on the cover: which items end their own turn (a video, a marquee), and how long
 * smart auto-advance and groups wait for that at most. One source for the playlist and for the steps of
 * a [CoverItem.Group]; pure, so it is unit-tested.
 */
object GroupTiming {
    /**
     * After the minimum time, wait at most this long for a GIF, animation or marquee to finish its pass.
     * Videos have their own bound: their length ([videoPassMs]).
     */
    const val MAX_EXTRA_WAIT_MS = 90_000L

    /** After a group's last step it holds a moment before step one, so the playlist can move on without a flash. */
    const val WRAP_GRACE_MS = 400L

    /** A step that cannot play stays this long (a glimpse of the broken mark), then the group skips it. */
    const val FAILED_STEP_MS = 1_000L

    /** A video step that plays to its end stays at least this long, so a tiny clip never just flickers past. */
    const val MIN_VIDEO_STEP_MS = 1_000L

    /** Room on top of every computed bound (decoder start-up, slow frames). */
    private const val SLACK_MS = 5_000L

    /** Generous: monospace glyphs are about 0.6 em wide. */
    private const val MONO_ADVANCE_EM = 0.7f
    private const val MAX_FONT_SCALE = 2f
    private const val COVER_MAX_WIDTH_DP = 800f

    /** Mixes the pass into a group's shuffle seed, so neighbouring passes get unrelated orders. */
    private const val PASS_MIX = -0x61c8864680b583ebL

    /**
     * True when [item] finishes a pass of its own (a video, GIF, marquee, animation, collage rotation or
     * group) that is worth waiting for; everything else just stays for its time.
     */
    fun isCycleBased(item: CoverItem): Boolean = when (item) {
        // Videos play to their end, GIFs and animated WebPs through one loop.
        is CoverItem.Media -> item.kind != MediaKind.IMAGE
        is CoverItem.Text -> item.scroll
        // An animation plays its frames through once.
        is CoverItem.PixelArt -> item.animated
        // Waits until every cell has shown all of its items once.
        is CoverItem.Collage -> item.longestCell > 1
        is CoverItem.Group -> true
        is CoverItem.Countdown, is CoverItem.Qr, is CoverItem.Toy -> false
    }

    /**
     * Upper bound for one more pass of [item] once its minimum time is over. A video's comes from its length
     * ([UNKNOWN_VIDEO_MS] when that is not known yet); a marquee can take far longer than a GIF (300 characters
     * of large text crawling slowly), so its bound comes from the text itself, with room for font scaling.
     */
    fun extraWaitMs(item: CoverItem): Long = when (item) {
        is CoverItem.Media -> if (item.kind == MediaKind.VIDEO) videoPassMs(item) else MAX_EXTRA_WAIT_MS
        // Cells start staggered by up to one interval, so the last one finishes a full interval later.
        is CoverItem.Collage -> maxOf(MAX_EXTRA_WAIT_MS, (item.longestCell + 1) * item.intervalSeconds * 1_000L + SLACK_MS)
        is CoverItem.Text -> {
            val passDp = item.text.length * item.sizeSp * MONO_ADVANCE_EM * MAX_FONT_SCALE + COVER_MAX_WIDTH_DP
            maxOf(MAX_EXTRA_WAIT_MS, (passDp / item.speedDps.coerceAtLeast(1) * 1_000).toLong() + SLACK_MS)
        }
        is CoverItem.Group -> maxPassMs(item)
        else -> MAX_EXTRA_WAIT_MS
    }

    /**
     * A clip whose length could not be read is waited for at most this long. Lengths are measured at import
     * (and once for older clips), so this only bounds the wait if a clip never reports its end.
     */
    const val UNKNOWN_VIDEO_MS = 10 * 60_000L

    /** Longest one play of [video] from its start can take: its length, or [UNKNOWN_VIDEO_MS] when unknown. */
    fun videoPassMs(video: CoverItem.Media): Long =
        (if (video.durationMs > 0) video.durationMs else UNKNOWN_VIDEO_MS) + SLACK_MS

    /** The photo time of [group]: how long each still step stays. */
    fun stepMs(group: CoverItem.Group): Long = group.stepSeconds * 1_000L

    /** [step] of [group] is a video that plays once from start to end, whatever the photo time. */
    fun playsToEnd(group: CoverItem.Group, step: CoverItem): Boolean = group.videosToEnd && step.isVideo()

    /** The minimum time [step] of [group] stays: the photo time, or a moment for a video that plays to its end. */
    fun stepMinMs(group: CoverItem.Group, step: CoverItem): Long = if (playsToEnd(group, step)) MIN_VIDEO_STEP_MS else stepMs(group)

    /**
     * After its minimum, [step] of [group] waits for the end of the pass it is in: a video when the group plays
     * videos to the end (a cut video just stays the photo time), and GIFs, marquees, animations and collages.
     */
    fun stepWaitsForPass(group: CoverItem.Group, step: CoverItem): Boolean =
        if (step.isVideo()) group.videosToEnd else isCycleBased(step)

    /** Longest [step] of [group] can stay: its minimum plus, when it waits for its pass, that pass's bound. */
    fun stepMaxMs(group: CoverItem.Group, step: CoverItem): Long =
        stepMinMs(group, step) + if (stepWaitsForPass(group, step)) extraWaitMs(step) else 0L

    /** Longest a whole pass through [group] can take: every step's bound, then the hold before step one again. */
    fun maxPassMs(group: CoverItem.Group): Long = group.items.sumOf { stepMaxMs(group, it) } + WRAP_GRACE_MS + SLACK_MS

    /**
     * About how long one pass through [group] takes, for the editor. [Estimate.atLeast] when some step's own
     * length is not known (a video not measured yet, a GIF, a marquee): the total then counts only what is.
     */
    fun estimate(group: CoverItem.Group): Estimate {
        var total = 0L
        var atLeast = false
        val photoMs = stepMs(group)
        for (step in group.items) {
            total += when {
                playsToEnd(group, step) -> {
                    val length = (step as CoverItem.Media).durationMs
                    if (length <= 0) atLeast = true
                    maxOf(length, MIN_VIDEO_STEP_MS)
                }
                step.isVideo() || !isCycleBased(step) -> photoMs
                step is CoverItem.PixelArt -> wholePasses(photoMs, step.frameCount * 1_000L / step.fps.coerceAtLeast(1))
                step is CoverItem.Collage -> wholePasses(photoMs, step.longestCell * step.intervalSeconds * 1_000L)
                else -> {
                    // A GIF or a marquee: at least the photo time, then the rest of a pass of unknown length.
                    atLeast = true
                    photoMs
                }
            }
        }
        return Estimate(total, atLeast)
    }

    /** [minMs], rounded up to whole passes of [passMs]: a step waits for the pass it is in to end. */
    private fun wholePasses(minMs: Long, passMs: Long): Long =
        if (passMs <= 0) minMs else ((minMs + passMs - 1) / passMs).coerceAtLeast(1) * passMs

    /**
     * The order a [size]-step group plays its steps in during [pass]: as listed, or with [shuffle] a random
     * order per pass, none twice. Stable for the same [seed] and [pass], so recomposition never reshuffles.
     * A new pass never starts with the step the last one ended on (two steps simply alternate).
     */
    fun playOrder(size: Int, shuffle: Boolean, seed: Long, pass: Int): List<Int> {
        if (!shuffle || size <= 1) return List(size.coerceAtLeast(0)) { it }
        // Two steps can only alternate without showing one twice in a row: every pass plays the first order.
        if (size == 2) return shuffled(size, seed, 0)
        val order = shuffled(size, seed, pass).toMutableList()
        // Swapping the first two leaves the last step alone, so the previous pass's end is its raw order's.
        if (pass > 0 && order[0] == shuffled(size, seed, pass - 1).last()) {
            order[0] = order[1].also { order[1] = order[0] }
        }
        return order
    }

    private fun shuffled(size: Int, seed: Long, pass: Int): List<Int> =
        (0 until size).shuffled(Random(seed + pass * PASS_MIX))

    /** Where a group goes after step [index] of [size]: the next step, or back to the first ([wrapped], one pass). */
    fun next(index: Int, size: Int): Step = if (index + 1 < size) Step(index + 1, wrapped = false) else Step(0, wrapped = true)

    /** Every step of a [size]-step group failed during one pass, so waiting for the group is pointless. */
    fun allFailed(failed: Set<Int>, size: Int): Boolean = size > 0 && (0 until size).all { it in failed }

    data class Step(val index: Int, val wrapped: Boolean)

    /** About [ms] for one pass; with [atLeast], some lengths were unknown and it can take longer. */
    data class Estimate(val ms: Long, val atLeast: Boolean)

    private fun CoverItem.isVideo(): Boolean = this is CoverItem.Media && kind == MediaKind.VIDEO
}
