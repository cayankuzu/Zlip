package com.zlip.app.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withClip
import androidx.core.graphics.withTranslation
import com.zlip.app.R
import com.zlip.app.data.CoverItem
import com.zlip.app.data.QrColors
import com.zlip.app.data.QrEncoder
import com.zlip.app.data.QrKind
import com.zlip.app.data.QrMatrix
import kotlin.math.floor
import kotlin.math.min

/** Margin around the card's content, as a share of the box's shorter side. */
private const val PADDING = 0.035f

/** Caption text size as a share of the shorter side: about 19 dp on the cover, bold. */
private const val CAPTION_SIZE = 0.056f

/** Boxes smaller than this (px) show only the code: in thumbnails a caption would be unreadable anyway. */
private const val CAPTION_MIN_SIDE = 280f

/** Placeholder size (in modules) while the editor's fields cannot make a code yet. */
private const val PLACEHOLDER_MODULES = 25

/** Material icons are drawn on a 24 x 24 grid. */
private const val ICON_VIEWPORT = 24f

/**
 * A QR card on the cover: the code as large as fits with its quiet zone, caption below. Static: it never
 * animates, and the code is encoded once per payload (see [QrEncoder]).
 */
@Composable
internal fun QrCover(item: CoverItem.Qr, modifier: Modifier) {
    val card = remember(item.kind, item.payload, item.caption, item.codeColor, item.background) { QrCard(item) }
    val description = stringResource(R.string.qr_cover_description, card.caption.ifEmpty { stringResource(R.string.type_qr) })
    Box(
        modifier
            .semantics { contentDescription = description }
            .drawWithCache {
                // Laid out once per size; drawing only replays the prepared bitmap and text.
                val layout = card.layout(RectF(0f, 0f, size.width, size.height))
                onDrawBehind { drawIntoCanvas { layout.draw(it.nativeCanvas) } }
            },
    )
}

/** The icon shown next to the caption and in the editor's kind chooser. */
internal val QrKind.icon: ImageVector
    get() = when (this) {
        QrKind.LINK -> Icons.Filled.Link
        QrKind.WIFI -> Icons.Filled.Wifi
        QrKind.CONTACT -> Icons.Filled.Person
        QrKind.TEXT -> Icons.AutoMirrored.Filled.Notes
    }

/**
 * A QR card ready to draw on any [Canvas]: the live cover (via Compose) and the widget's still frames use
 * the same drawing, so they always match. Colours are made scanner-safe ([QrColors]),
 * and the code is kept as a bitmap with one pixel per module, scaled up by whole pixels so every module
 * has sharp edges.
 */
internal class QrCard(item: CoverItem.Qr) {
    val kind: QrKind = item.kind
    val caption: String = item.caption.trim()
    private val dark: Int
    private val light: Int
    private val matrix: QrMatrix? = QrEncoder.encode(item.payload)
    private val modules: Bitmap?

    init {
        val (code, background) = QrColors.scannable(item.codeColor, item.background)
        dark = code
        light = background
        modules = matrix?.let { m ->
            val size = m.size
            // Light modules stay transparent: the card's background shows through.
            val pixels = IntArray(size * size) { if (m.isDark(it % size, it / size)) dark else 0 }
            createBitmap(size, size).apply { setPixels(pixels, 0, size, 0, 0, size, size) }
        }
    }

    /** Positions everything inside [box]. Cheap enough per size change; Compose caches the result. */
    fun layout(box: RectF): CardLayout {
        val side = min(box.width(), box.height())
        val pad = side * PADDING
        var text: StaticLayout? = null
        var iconSize = 0f
        var gap = 0f
        if (caption.isNotEmpty() && side >= CAPTION_MIN_SIDE) {
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = dark
                typeface = Typeface.DEFAULT_BOLD
                textSize = side * CAPTION_SIZE
            }
            iconSize = paint.textSize * 1.2f
            gap = paint.textSize * 0.4f
            val maxWidth = (box.width() - 2 * pad - iconSize - gap).toInt()
            if (maxWidth > paint.textSize * 3) {
                text = StaticLayout.Builder.obtain(caption, 0, caption.length, paint, maxWidth)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setMaxLines(2)
                    .setEllipsize(TextUtils.TruncateAt.END)
                    .setIncludePad(false)
                    .build()
            }
        }
        val captionHeight = text?.height?.toFloat() ?: 0f
        val moduleCount = matrix?.size ?: PLACEHOLDER_MODULES
        val quietModules = 2 * QrEncoder.QUIET_ZONE
        val available = min(box.width() - 2 * pad, box.height() - 2 * pad - captionHeight)
        // Whole pixels per module whenever possible: blurry module edges are what makes cameras hesitate.
        val raw = (available / (moduleCount + quietModules)).coerceAtLeast(0f)
        val module = if (raw >= 1f) floor(raw) else raw
        val codeSize = module * moduleCount
        val quiet = module * QrEncoder.QUIET_ZONE
        // Code (with its quiet zone) and caption as one group, centred, so the caption stays close to the code.
        val groupHeight = codeSize + 2 * quiet + captionHeight
        val groupTop = box.top + pad + ((box.height() - 2 * pad - groupHeight) / 2f).coerceAtLeast(0f)
        val codeLeft = floor(box.centerX() - codeSize / 2f)
        val codeTop = floor(groupTop + quiet)
        return CardLayout(
            box = RectF(box),
            code = RectF(codeLeft, codeTop, codeLeft + codeSize, codeTop + codeSize),
            module = module,
            text = text,
            captionTop = groupTop + codeSize + 2 * quiet,
            iconSize = iconSize,
            gap = gap,
        )
    }

    /** Frees the module bitmap now instead of at the next GC; for one-off still frames only. */
    fun recycle() {
        modules?.recycle()
    }

    inner class CardLayout internal constructor(
        private val box: RectF,
        private val code: RectF,
        private val module: Float,
        private val text: StaticLayout?,
        private val captionTop: Float,
        private val iconSize: Float,
        private val gap: Float,
    ) {
        private val codePaint = Paint().apply {
            // Nearest neighbour keeps module edges hard; only sub-pixel thumbnails are smoothed.
            isFilterBitmap = module < 1f
        }
        private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = dark }

        fun draw(canvas: Canvas) {
            canvas.withClip(box) {
                drawColor(light)
                val bitmap = modules
                if (bitmap != null && !bitmap.isRecycled) {
                    drawBitmap(bitmap, null, code, codePaint)
                } else {
                    drawPlaceholder(this)
                }
                if (text != null) drawCaption(this, text)
            }
        }

        /** A dashed frame with the kind's icon where the code will appear (editor drafts only). */
        private fun drawPlaceholder(canvas: Canvas) {
            if (code.width() < 4f) return
            val stroke = (code.width() / 60f).coerceAtLeast(1f)
            val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = stroke
                color = withAlpha(dark, 0x55)
                pathEffect = DashPathEffect(floatArrayOf(stroke * 4, stroke * 3), 0f)
            }
            val corner = code.width() / 10f
            canvas.drawRoundRect(code, corner, corner, outline)
            val icon = code.width() * 0.32f
            drawIcon(canvas, code.centerX() - icon / 2f, code.centerY() - icon / 2f, icon, withAlpha(dark, 0x66))
        }

        /** Icon + caption as one centred row; the icon sits in the middle of the (up to two line) text. */
        private fun drawCaption(canvas: Canvas, text: StaticLayout) {
            var textLeft = Float.MAX_VALUE
            var textRight = 0f
            for (line in 0 until text.lineCount) {
                textLeft = min(textLeft, text.getLineLeft(line))
                textRight = maxOf(textRight, text.getLineRight(line))
            }
            val blockWidth = iconSize + gap + (textRight - textLeft)
            val blockLeft = box.centerX() - blockWidth / 2f
            drawIcon(canvas, blockLeft, captionTop + (text.height - iconSize) / 2f, iconSize, dark)
            canvas.withTranslation(blockLeft + iconSize + gap - textLeft, captionTop) { text.draw(this) }
        }

        private fun drawIcon(canvas: Canvas, left: Float, top: Float, size: Float, color: Int) {
            iconPaint.color = color
            canvas.withTranslation(left, top) {
                scale(size / ICON_VIEWPORT, size / ICON_VIEWPORT)
                for (path in iconPaths(kind)) drawPath(path, iconPaint)
            }
        }
    }
}

private fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)

private val iconPathCache = HashMap<QrKind, List<Path>>()

/**
 * The Material icon of [kind] as Android paths, for drawing on a plain canvas (widget frames have no
 * Compose). Built once per kind; callers only read them.
 */
private fun iconPaths(kind: QrKind): List<Path> = synchronized(iconPathCache) {
    iconPathCache.getOrPut(kind) {
        buildList {
            fun collect(group: VectorGroup) {
                for (node in group) {
                    when (node) {
                        is VectorPath -> add(
                            PathParser().addPathNodes(node.pathData).toPath().asAndroidPath().apply {
                                fillType = if (node.pathFillType == PathFillType.EvenOdd) Path.FillType.EVEN_ODD else Path.FillType.WINDING
                            },
                        )
                        is VectorGroup -> collect(node)
                    }
                }
            }
            collect(kind.icon.root)
        }
    }
}
