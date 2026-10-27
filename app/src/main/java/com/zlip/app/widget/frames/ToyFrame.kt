package com.zlip.app.widget.frames

import android.graphics.Canvas
import android.graphics.RectF
import com.zlip.app.data.CoverItem
import com.zlip.app.render.TOY_AREA_FRACTION
import com.zlip.app.render.ToyPainter

/**
 * A toy at rest (die on 5, coin on heads, the "8" ball, bottle, wheel, counter at 0), drawn by the same
 * painter and in the same place as the live cover, so tapping the widget into the live view does not
 * make the toy jump. Nothing here depends on the language: the built-in wheel shows symbols.
 */
internal fun drawToyFrame(item: CoverItem.Toy, canvas: Canvas, box: RectF) {
    canvas.save()
    canvas.clipRect(box)
    canvas.drawColor(item.background)
    ToyPainter().drawResting(
        canvas = canvas,
        kind = item.kind,
        color = item.color,
        options = item.options,
        left = box.left,
        top = box.top,
        width = box.width(),
        height = box.height() * TOY_AREA_FRACTION,
    )
    canvas.restore()
}
