package com.zlip.app.data

/**
 * How long things stay on the cover: which items end their own turn (a video, a marquee), and how long
 * smart auto-advance and groups wait for that at most. One source for the playlist and for the steps of
 * a [CoverItem.Group]; pure, so it is unit-tested.
 */
object GroupTiming {
    /** After the minimum time, wait at most this long for a pass to end (videos are at most 60 s). */
    const val MAX_EXTRA_WAIT_MS = 90_000L

    /** After a group's last step it holds a moment before step one, so the playlist can move on without a flash. */
    const val WRAP_GRACE_MS = 400L

    /** A step that cannot play stays this long (a glimpse of the broken mark), then the group skips it. */
    const val FAILED_STEP_MS = 1_000L

    /** Room on top of every computed bound (decoder start-up, slow frames). */
    private const val SLACK_MS = 5_000L

    /** Generous: monospace glyphs are about 0.6 em wide. */
    private const val MONO_ADVANCE_EM = 0.7f
    private const val MAX_FONT_SCALE = 2f
    private const val COVER_MAX_WIDTH_DP = 800f

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
     * Upper bound for one more pass of [item] once its minimum time is over. Videos are at most 60 s; a
     * marquee can take far longer (300 characters of large text crawling slowly), so its bound comes from
     * the text itself, with room for font scaling.
     */
    fun extraWaitMs(item: CoverItem): Long = when (item) {
        // Cells start staggered by up to one interval, so the last one finishes a full interval later.
        is CoverItem.Collage -> maxOf(MAX_EXTRA_WAIT_MS, (item.longestCell + 1) * item.intervalSeconds * 1_000L + SLACK_MS)
        is CoverItem.Text -> {
            val passDp = item.text.length * item.sizeSp * MONO_ADVANCE_EM * MAX_FONT_SCALE + COVER_MAX_WIDTH_DP
            maxOf(MAX_EXTRA_WAIT_MS, (passDp / item.speedDps.coerceAtLeast(1) * 1_000).toLong() + SLACK_MS)
        }
        is CoverItem.Group -> maxPassMs(item)
        else -> MAX_EXTRA_WAIT_MS
    }

    /** The minimum time each step of [group] stays. */
    fun stepMs(group: CoverItem.Group): Long = group.stepSeconds * 1_000L

    /**
     * Longest a whole pass through [group] can take: every step's minimum plus, for steps that finish a pass
     * of their own, that pass's bound; then the hold before step one again.
     */
    fun maxPassMs(group: CoverItem.Group): Long =
        group.items.sumOf { stepMs(group) + if (isCycleBased(it)) extraWaitMs(it) else 0L } + WRAP_GRACE_MS + SLACK_MS

    /** Where a group goes after step [index] of [size]: the next step, or back to the first ([wrapped], one pass). */
    fun next(index: Int, size: Int): Step = if (index + 1 < size) Step(index + 1, wrapped = false) else Step(0, wrapped = true)

    /** Every step of a [size]-step group failed during one pass, so waiting for the group is pointless. */
    fun allFailed(failed: Set<Int>, size: Int): Boolean = size > 0 && (0 until size).all { it in failed }

    data class Step(val index: Int, val wrapped: Boolean)
}
