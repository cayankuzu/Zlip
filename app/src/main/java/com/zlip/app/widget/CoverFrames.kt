package com.zlip.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.format.DateFormat
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.edit
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withClip
import com.zlip.app.data.CoverItem
import com.zlip.app.data.CoverItemJson
import com.zlip.app.data.MediaFit
import com.zlip.app.data.MediaFocus
import com.zlip.app.data.MediaKind
import com.zlip.app.data.MediaStorage
import com.zlip.app.data.leaves
import com.zlip.app.data.showsQr
import com.zlip.app.render.LedStyle
import com.zlip.app.render.ledMatrixBitmap
import com.zlip.app.widget.frames.countdownFrameKey
import com.zlip.app.widget.frames.drawCountdownFrame
import com.zlip.app.widget.frames.drawQrFrame
import com.zlip.app.widget.frames.drawToyFrame
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Turns library items into still PNG frames for places that cannot run our renderer (the folded
 * cover-screen widget is drawn by the system, from RemoteViews). Frames match the cover panel
 * (748x720) and are cached by content, so an unchanged item is never rendered twice.
 */
internal object CoverFrames {
    const val WIDTH = 748
    const val HEIGHT = 720
    private const val PARTIAL_SUFFIX = ".part"
    private const val RENDER_VERSION = 5

    /** Cover width in dp (748 px at about 2.08x); collage gaps and corners are given in dp. */
    private const val COVER_WIDTH_DP = 360f
    private const val COLLAGE_CORNER_DP = 14f

    fun dir(context: Context): File = File(context.filesDir, "frames").apply { mkdirs() }

    /** The cached frame for [item] (in the [led] look, if any), rendering it if needed; null if it cannot be drawn. */
    fun frameFor(context: Context, item: CoverItem, led: LedStyle? = null): File? {
        @Suppress("NAME_SHADOWING")
        val led = led.takeUnless { item.showsQr() }
        val file = File(dir(context), frameName(context, item, led))
        if (file.exists()) return file
        val bitmap = try {
            render(context, item)?.let { plain ->
                if (led == null) plain else ledMatrixBitmap(plain, led).also { plain.recycle() }
            }
        } catch (e: IOException) {
            null
        } catch (e: RuntimeException) {
            null
        } ?: return null
        var partial: File? = null
        return try {
            // A unique temporary name, then an atomic rename: a host never reads a half-written frame.
            val temp = File.createTempFile("frame", PARTIAL_SUFFIX, dir(context)).also { partial = it }
            val ok = temp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (ok && temp.renameTo(file)) file else null
        } catch (e: IOException) {
            // Storage full or the directory vanished: skip this frame, the widget shows the others.
            null
        } finally {
            bitmap.recycle()
            partial?.takeIf { it.exists() }?.delete()
        }
    }

    /** Deletes frames (and interrupted renders) that no current item uses. Callers serialize this with [frameFor]. */
    fun prune(context: Context, keep: Collection<File>) {
        val names = keep.mapTo(HashSet()) { it.name }
        dir(context).listFiles()?.forEach { if (it.name !in names) it.delete() }
    }

    /**
     * 128-bit name derived from what the item looks like and a per-install secret: it changes whenever
     * the pixels would (so hosts never show a stale cached image) and cannot be guessed by other apps.
     */
    private fun frameName(context: Context, item: CoverItem, led: LedStyle?): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(secret(context))
        // Bump RENDER_VERSION whenever the drawing code changes, so cached frames are redrawn after updates.
        digest.update("v$RENDER_VERSION:${WIDTH}x$HEIGHT".toByteArray(Charsets.UTF_8))
        digest.update(visualOnly(CoverItemJson.encode(item)).toString().toByteArray(Charsets.UTF_8))
        // A countdown's still shows the current day (or hour) count, so it is a new frame when that changes.
        countdownKey(item)?.let { digest.update("$it|24h=${DateFormat.is24HourFormat(context)}".toByteArray(Charsets.UTF_8)) }
        led?.let { digest.update(it.toString().toByteArray(Charsets.UTF_8)) }
        return digest.digest().take(16).joinToString("") { "%02x".format(it) } + ".png"
    }

    /**
     * [json] without what never changes a still (names, sound, timing), also inside collage cells and
     * group steps: turning sound on or renaming reuses the cached frame instead of drawing it again.
     */
    private fun visualOnly(json: JSONObject): JSONObject = json.apply {
        NON_VISUAL_KEYS.forEach { remove(it) }
        optJSONArray("cells")?.let { cells ->
            for (cell in 0 until cells.length()) {
                val entries = cells.optJSONArray(cell) ?: continue
                for (entry in 0 until entries.length()) entries.optJSONObject(entry)?.let(::visualOnly)
            }
        }
        optJSONArray("items")?.let { steps ->
            for (step in 0 until steps.length()) steps.optJSONObject(step)?.let(::visualOnly)
        }
    }

    /** JSON keys of [CoverItemJson] that do not affect the drawn still. */
    private val NON_VISUAL_KEYS = listOf("id", "name", "sound", "hasAudio", "loop", "speedDps", "scroll", "fps", "interval", "step")

    private fun countdownKey(item: CoverItem): String? {
        val now = System.currentTimeMillis()
        return item.leaves().filterIsInstance<CoverItem.Countdown>().joinToString { countdownFrameKey(it, now) }.ifEmpty { null }
    }

    private fun secret(context: Context): ByteArray {
        val prefs = context.getSharedPreferences("zlip_frames", Context.MODE_PRIVATE)
        prefs.getString("secret", null)?.let { return it.toByteArray() }
        val fresh = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        prefs.edit { putString("secret", fresh) }
        return fresh.toByteArray()
    }

    private fun render(context: Context, item: CoverItem): Bitmap? {
        val bitmap = createBitmap(WIDTH, HEIGHT)
        val drawn = try {
            draw(context, item, Canvas(bitmap), RectF(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat()), badge = true)
        } catch (e: Exception) {
            bitmap.recycle()
            throw e
        }
        if (!drawn) bitmap.recycle()
        return bitmap.takeIf { drawn }
    }

    /** Draws [item] into [box] on [canvas]. Returns false when there is nothing to draw (a video without poster). */
    private fun draw(context: Context, item: CoverItem, canvas: Canvas, box: RectF, badge: Boolean): Boolean {
        when (item) {
            is CoverItem.PixelArt -> {
                drawPixelArt(item, canvas, box)
                // Animated drawings move in the screen saver; the play mark says so on the still.
                if (badge && item.animated) drawPlayBadge(canvas)
            }
            is CoverItem.Text -> drawText(item, canvas, box)
            is CoverItem.Media -> {
                val source = (if (item.kind == MediaKind.VIDEO) item.posterFileName else item.fileName) ?: return false
                drawImage(MediaStorage.file(context, source), item, canvas, box, badge && item.kind != MediaKind.IMAGE)
            }
            is CoverItem.Collage -> drawCollage(context, item, canvas, box)
            is CoverItem.Countdown -> drawCountdownFrame(item, canvas, box, System.currentTimeMillis(), DateFormat.is24HourFormat(context))
            is CoverItem.Qr -> drawQrFrame(item, canvas, box)
            is CoverItem.Toy -> drawToyFrame(item, canvas, box)
            // The widget shows a group's steps as frames of their own; this is the stand-in for one still.
            is CoverItem.Group -> {
                val first = item.items.firstOrNull() ?: return false
                if (!draw(context, first, canvas, box, badge = false)) return false
                if (badge) drawCountBadge(canvas, item.items.size)
            }
        }
        return true
    }

    private fun drawPixelArt(art: CoverItem.PixelArt, canvas: Canvas, box: RectF) = canvas.withClip(box) {
        drawColor(art.background)
        // Whole pixels per cell (nearest neighbour), centred: crisp edges, no seams.
        val cell = min(box.width().toInt() / art.width, box.height().toInt() / art.height).coerceAtLeast(1)
        val left = box.left.toInt() + (box.width().toInt() - cell * art.width) / 2
        val top = box.top.toInt() + (box.height().toInt() - cell * art.height) / 2
        val paint = Paint()
        for (y in 0 until art.height) {
            for (x in 0 until art.width) {
                val argb = art[x, y]
                if (argb == 0) continue
                paint.color = argb
                drawRect(
                    (left + x * cell).toFloat(),
                    (top + y * cell).toFloat(),
                    (left + (x + 1) * cell).toFloat(),
                    (top + (y + 1) * cell).toFloat(),
                    paint,
                )
            }
        }
    }

    /** A still of the text: as large as fits (up to its own size), centred, in the item's colours. */
    private fun drawText(item: CoverItem.Text, canvas: Canvas, box: RectF) = canvas.withClip(box) {
        drawColor(item.background)
        val scale = box.width() / WIDTH
        val padding = (box.width() / 12).toInt()
        val width = (box.width() - 2 * padding).toInt().coerceAtLeast(1)
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = item.textColor
            typeface = Typeface.create(Typeface.MONOSPACE, if (item.bold) Typeface.BOLD else Typeface.NORMAL)
        }
        var size = (item.sizeSp * 2.2f * scale).coerceIn(12f, 220f)
        var layout: StaticLayout
        do {
            paint.textSize = size
            layout = StaticLayout.Builder.obtain(item.text, 0, item.text.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setMaxLines(6)
                .build()
            size *= 0.9f
        } while (layout.height > box.height() - 2 * padding && size > 10f)
        translate(box.left + padding, box.top + ((box.height() - layout.height) / 2f).coerceAtLeast(0f))
        layout.draw(this)
    }

    private fun drawImage(file: File, item: CoverItem.Media, canvas: Canvas, box: RectF, badge: Boolean) {
        // First frame of GIFs/animated WebP; already EXIF-rotated photos; video posters.
        val limit = (max(box.width(), box.height()) * 2).toInt().coerceAtLeast(1)
        val source = ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val edge = max(info.size.width, info.size.height)
            if (edge > limit) {
                val scale = limit.toFloat() / edge
                decoder.setTargetSize((info.size.width * scale).roundToInt().coerceAtLeast(1), (info.size.height * scale).roundToInt().coerceAtLeast(1))
            }
        }
        canvas.withClip(box) {
            drawColor(item.background)
            val scale = if (item.fit == MediaFit.FILL) {
                max(box.width() / source.width, box.height() / source.height)
            } else {
                min(box.width() / source.width, box.height() / source.height)
            }
            val w = source.width * scale
            val h = source.height * scale
            // A cropped picture shows the part the user framed, like the live cover; a fitted one is centred.
            val left = if (item.fit == MediaFit.FILL) box.left + MediaFocus.offset(w - box.width(), item.focusX) else box.centerX() - w / 2f
            val top = if (item.fit == MediaFit.FILL) box.top + MediaFocus.offset(h - box.height(), item.focusY) else box.centerY() - h / 2f
            val target = RectF(left, top, left + w, top + h)
            // Nearest-neighbour for pixel art, smooth for photos.
            val paint = Paint().apply { isFilterBitmap = !item.crisp }
            drawBitmap(source, Rect(0, 0, source.width, source.height), target, paint)
        }
        source.recycle()
        if (badge) drawPlayBadge(canvas)
    }

    /** The collage's first item in every cell, laid out like the live view (gaps, rounded corners). */
    private fun drawCollage(context: Context, item: CoverItem.Collage, canvas: Canvas, box: RectF) = canvas.withClip(box) {
        drawColor(item.background)
        // The live view lays the collage out in dp on a ~360 dp wide cover; keep the same proportions.
        val dp = box.width() / COVER_WIDTH_DP
        val gap = item.gapDp * dp
        val inner = RectF(box.left + gap / 2, box.top + gap / 2, box.right - gap / 2, box.bottom - gap / 2)
        val radius = if (item.rounded) COLLAGE_CORNER_DP * dp else 0f
        item.layout.cells.forEachIndexed { index, rect ->
            val first = item.cells.getOrNull(index)?.firstOrNull() ?: return@forEachIndexed
            val cell = RectF(
                inner.left + inner.width() * rect[0] + gap / 2,
                inner.top + inner.height() * rect[1] + gap / 2,
                inner.left + inner.width() * rect[2] - gap / 2,
                inner.top + inner.height() * rect[3] - gap / 2,
            )
            if (cell.width() < 1f || cell.height() < 1f) return@forEachIndexed
            withClip(Path().apply { addRoundRect(cell, radius, radius, Path.Direction.CW) }) {
                // A broken file in one cell must not cost the whole frame.
                runCatching { draw(context, first, this, cell, badge = false) }
            }
        }
    }

    /** Small play mark in the corner of GIF/video posters: tapping the widget plays them in the live view. */
    private fun drawPlayBadge(canvas: Canvas) {
        val r = 30f
        val cx = WIDTH - 56f
        val cy = 56f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99000000.toInt() }
        canvas.drawCircle(cx, cy, r, paint)
        paint.color = 0xFFFFFFFF.toInt()
        val path = Path().apply {
            moveTo(cx - 9f, cy - 14f)
            lineTo(cx + 15f, cy)
            lineTo(cx - 9f, cy + 14f)
            close()
        }
        canvas.drawPath(path, paint)
    }

    /** How many steps a group has, as a small pill where the play mark would be. */
    private fun drawCountBadge(canvas: Canvas, count: Int) {
        val text = "$count"
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
        }
        val width = maxOf(60f, textPaint.measureText(text) + 32f)
        val pill = RectF(WIDTH - 26f - width, 26f, WIDTH - 26f, 86f)
        canvas.drawRoundRect(pill, 30f, 30f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99000000.toInt() })
        val baseline = pill.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(text, pill.centerX() - textPaint.measureText(text) / 2, baseline, textPaint)
    }
}
