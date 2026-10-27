package com.zlip.app.ui.pixel

import com.zlip.app.data.CoverItem

/**
 * The drawing being edited: one or more frames of width * height ARGB pixels (0 = transparent). Immutable,
 * so undo steps can share every frame they did not change.
 */
internal data class PixelFrames(val width: Int, val height: Int, val frames: List<List<Int>>) {
    init {
        require(width > 0 && height > 0) { "Pixel grid must not be empty" }
        require(frames.size in 1..CoverItem.PixelArt.MAX_FRAMES) { "Expected 1..${CoverItem.PixelArt.MAX_FRAMES} frames, got ${frames.size}" }
        require(frames.all { it.size == width * height }) { "Every frame must have ${width * height} pixels" }
    }

    val count: Int get() = frames.size
    val canAdd: Boolean get() = count < CoverItem.PixelArt.MAX_FRAMES

    fun replace(index: Int, pixels: List<Int>): PixelFrames = copy(frames = frames.toMutableList().apply { this[index] = pixels })

    /** A copy of frame [index] right after it: animations mostly change a little from frame to frame. */
    fun insertCopy(index: Int): PixelFrames {
        if (!canAdd) return this
        return copy(frames = frames.toMutableList().apply { add(index + 1, frames[index]) })
    }

    fun remove(index: Int): PixelFrames {
        if (count <= 1) return this
        return copy(frames = frames.toMutableList().apply { removeAt(index) })
    }

    /** Frame [from] moved to position [to]; the others keep their order. */
    fun move(from: Int, to: Int): PixelFrames {
        if (from == to || from !in frames.indices || to !in frames.indices) return this
        return copy(frames = frames.toMutableList().apply { add(to, removeAt(from)) })
    }

    /** Every frame on a [newWidth] x [newHeight] canvas, kept centred: growing adds a margin, shrinking crops the edges. */
    fun resized(newWidth: Int, newHeight: Int): PixelFrames {
        if (newWidth == width && newHeight == height) return this
        val offsetX = (newWidth - width) / 2
        val offsetY = (newHeight - height) / 2
        return PixelFrames(
            width = newWidth,
            height = newHeight,
            frames = frames.map { old ->
                List(newWidth * newHeight) { i ->
                    val x = i % newWidth - offsetX
                    val y = i / newWidth - offsetY
                    if (x in 0 until width && y in 0 until height) old[y * width + x] else 0
                }
            },
        )
    }

    /**
     * Everything in one IntArray for saved instance state (Bundles should stay small). Pixel art is mostly
     * long runs of one color, so runs are stored as (length, color) pairs whenever that is shorter.
     */
    fun pack(): IntArray {
        val frameSize = width * height
        val total = count * frameSize
        var runs = 0
        var previous = 0
        for (i in 0 until total) {
            val value = pixelAt(i, frameSize)
            if (i == 0 || value != previous) runs++
            previous = value
        }
        val header = intArrayOf(0, width, height, count)
        if (runs * 2 >= total) {
            header[0] = PACK_RAW
            return header + IntArray(total) { pixelAt(it, frameSize) }
        }
        header[0] = PACK_RUNS
        val packed = IntArray(HEADER + runs * 2)
        header.copyInto(packed)
        var at = HEADER
        var i = 0
        while (i < total) {
            val value = pixelAt(i, frameSize)
            var length = 1
            while (i + length < total && pixelAt(i + length, frameSize) == value) length++
            packed[at++] = length
            packed[at++] = value
            i += length
        }
        return packed
    }

    private fun pixelAt(index: Int, frameSize: Int): Int = frames[index / frameSize][index % frameSize]

    companion object {
        private const val HEADER = 4
        private const val PACK_RAW = 1
        private const val PACK_RUNS = 2

        fun blank(width: Int, height: Int) = PixelFrames(width, height, listOf(List(width * height) { 0 }))

        fun of(art: CoverItem.PixelArt) = PixelFrames(
            width = art.width,
            height = art.height,
            frames = (listOf(art.pixels) + art.extraFrames).take(CoverItem.PixelArt.MAX_FRAMES),
        )

        /** Reverses [pack]; null when [packed] is damaged or from an unknown format, so callers start fresh. */
        fun unpack(packed: IntArray): PixelFrames? = runCatching {
            if (packed.size < HEADER) return null
            val (format, width, height, count) = packed
            if (width !in 1..MAX_EDGE || height !in 1..MAX_EDGE || count !in 1..CoverItem.PixelArt.MAX_FRAMES) return null
            val frameSize = width * height
            val total = frameSize * count
            val pixels = when (format) {
                PACK_RAW -> if (packed.size == HEADER + total) packed.copyOfRange(HEADER, packed.size) else return null
                PACK_RUNS -> {
                    if ((packed.size - HEADER) % 2 != 0) return null
                    val out = IntArray(total)
                    var filled = 0
                    for (at in HEADER until packed.size step 2) {
                        val length = packed[at]
                        if (length <= 0 || length > total - filled) return null
                        out.fill(packed[at + 1], filled, filled + length)
                        filled += length
                    }
                    if (filled != total) return null
                    out
                }
                else -> return null
            }
            PixelFrames(width, height, List(count) { frame -> pixels.copyOfRange(frame * frameSize, (frame + 1) * frameSize).asList() })
        }.getOrNull()

        /** Far above any canvas size the editor offers; only guards against absurd allocations. */
        private const val MAX_EDGE = 256
    }
}

/** How strongly the previous frame shows through while onion skin is on. */
private const val ONION_STRENGTH = 0.32f

/**
 * Display-only onion skin: [current] with [previous] showing faintly (blended toward [background]) wherever
 * [current] is transparent. Never saved; the eyedropper and fill still see the real frame.
 */
internal fun onionSkin(current: List<Int>, previous: List<Int>, background: Int): List<Int> = List(current.size) { i ->
    val own = current[i]
    val ghost = previous[i]
    when {
        own != 0 -> own
        ghost == 0 -> 0
        else -> blend(background, ghost, ONION_STRENGTH)
    }
}

/** Opaque mix of [base] and [over]: [amount] 0 gives [base], 1 gives [over]. */
internal fun blend(base: Int, over: Int, amount: Float): Int {
    fun channel(shift: Int): Int {
        val b = (base shr shift) and 0xFF
        val o = (over shr shift) and 0xFF
        return (b + (o - b) * amount + 0.5f).toInt().coerceIn(0, 255)
    }
    return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
}
