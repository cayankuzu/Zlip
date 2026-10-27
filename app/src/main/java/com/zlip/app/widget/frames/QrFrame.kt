package com.zlip.app.widget.frames

import android.graphics.Canvas
import android.graphics.RectF
import com.zlip.app.data.CoverItem
import com.zlip.app.render.QrCard

/**
 * A QR card's still frame: exactly the live cover's drawing ([QrCard]), so a code scanned off the widget
 * or a collage cell is the same code. The module bitmap is freed right away; frames are one-off renders.
 */
internal fun drawQrFrame(item: CoverItem.Qr, canvas: Canvas, box: RectF) {
    val card = QrCard(item)
    try {
        card.layout(box).draw(canvas)
    } finally {
        card.recycle()
    }
}
