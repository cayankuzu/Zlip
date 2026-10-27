package com.zlip.app.ui.pixel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.round

/**
 * How far the editor's canvas is zoomed in: a point p of the whole-canvas view shows at
 * p * scale + offset. The zoomed canvas always covers its box, so there is never empty space to pan
 * into. Pure math, unit tested.
 */
@Immutable
internal data class PixelZoom(val scale: Float = 1f, val offset: Offset = Offset.Zero) {
    val zoomed: Boolean get() = scale > 1f

    /** The point of the whole-canvas view that shows at [position]. */
    fun toCanvas(position: Offset): Offset = (position - offset) / scale

    /**
     * One step of a two-finger gesture in a box of [size]: the fingers' midpoint moved by [pan] to
     * [centroid] and their spread changed by [zoom]. The spot that was under the fingers stays under them.
     */
    fun pinched(centroid: Offset, zoom: Float, pan: Offset, size: IntSize): PixelZoom {
        val newScale = (scale * zoom).coerceIn(1f, MAX_SCALE)
        val moved = centroid - (centroid - pan - offset) * (newScale / scale)
        return PixelZoom(newScale, moved).fitted(size)
    }

    /** Pulled back so the canvas covers a box of [size], after a pan past an edge or a resize. */
    fun fitted(size: IntSize): PixelZoom = copy(
        offset = Offset(
            offset.x.coerceIn(size.width * (1f - scale), 0f),
            offset.y.coerceIn(size.height * (1f - scale), 0f),
        ),
    )

    companion object {
        const val MAX_SCALE = 6f
        val FIT = PixelZoom()
    }
}

/**
 * Where [com.zlip.app.render.PixelArtView] puts a [columns] x [rows] grid in a box of [size]:
 * whole-pixel cells, centred. The editor draws and hit-tests with the same numbers, so a touch lands
 * on the cell drawn under it.
 */
internal class PixelGrid(size: Size, val columns: Int, val rows: Int) {
    val cell: Float
    val left: Float
    val top: Float

    init {
        val raw = min(size.width / columns, size.height / rows)
        cell = if (raw >= 1f) floor(raw) else raw
        left = floor((size.width - cell * columns) / 2f)
        top = floor((size.height - cell * rows) / 2f)
    }

    /** The cell under [position] on screen, seen through [zoom], or null outside the grid. */
    fun cellAt(position: Offset, zoom: PixelZoom): Pair<Int, Int>? {
        val point = zoom.toCanvas(position)
        val x = floor((point.x - left) / cell).toInt()
        val y = floor((point.y - top) / cell).toInt()
        return if (x in 0 until columns && y in 0 until rows) x to y else null
    }
}

/**
 * The editor's drawing surface: [pixels] (row-major, [columns] x [rows], 0 shows [background]) laid out
 * like [com.zlip.app.render.PixelArtView] and seen through [zoom]. The zoom is read while drawing only,
 * so a pinch redraws just this canvas, never the editor, and only the cells on screen are drawn. Cell
 * edges land on whole pixels, so neighbouring cells never show a hairline seam at any zoom.
 */
@Composable
internal fun PixelCanvas(
    pixels: List<Int>,
    columns: Int,
    rows: Int,
    background: Int,
    gridColor: Color?,
    zoom: () -> PixelZoom,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier
            .clipToBounds()
            .background(Color(background)),
    ) {
        val grid = PixelGrid(size, columns, rows)
        val view = zoom()
        fun edgeX(column: Int) = round(view.offset.x + (grid.left + column * grid.cell) * view.scale)
        fun edgeY(row: Int) = round(view.offset.y + (grid.top + row * grid.cell) * view.scale)
        val from = view.toCanvas(Offset.Zero)
        val to = view.toCanvas(Offset(size.width, size.height))
        val firstX = floor((from.x - grid.left) / grid.cell).toInt().coerceAtLeast(0)
        val lastX = floor((to.x - grid.left) / grid.cell).toInt().coerceAtMost(columns - 1)
        val firstY = floor((from.y - grid.top) / grid.cell).toInt().coerceAtLeast(0)
        val lastY = floor((to.y - grid.top) / grid.cell).toInt().coerceAtMost(rows - 1)
        for (y in firstY..lastY) {
            val top = edgeY(y)
            val bottom = edgeY(y + 1)
            for (x in firstX..lastX) {
                val argb = pixels[y * columns + x]
                if (argb != 0) {
                    val left = edgeX(x)
                    drawRect(Color(argb), topLeft = Offset(left, top), size = Size(edgeX(x + 1) - left, bottom - top))
                }
            }
        }
        if (gridColor != null && grid.cell * view.scale >= MIN_GRID_CELL) {
            val top = edgeY(firstY)
            val bottom = edgeY(lastY + 1)
            val left = edgeX(firstX)
            val right = edgeX(lastX + 1)
            for (x in firstX..lastX + 1) {
                val gx = edgeX(x)
                drawLine(gridColor, Offset(gx, top), Offset(gx, bottom), strokeWidth = 1f)
            }
            for (y in firstY..lastY + 1) {
                val gy = edgeY(y)
                drawLine(gridColor, Offset(left, gy), Offset(right, gy), strokeWidth = 1f)
            }
        }
    }
}

/** Grid lines only once cells are this many pixels wide; below that they would hide the drawing. */
private const val MIN_GRID_CELL = 4f
