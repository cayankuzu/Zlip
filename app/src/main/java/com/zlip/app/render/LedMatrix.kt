package com.zlip.app.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.core.graphics.createBitmap
import androidx.core.graphics.get
import com.zlip.app.data.CoverSettings

/** The LED matrix look: [dots] across, every dot in its content color or, with [tint], in one color. */
data class LedStyle(val dots: Int, val tint: Int) {
    companion object {
        fun of(settings: CoverSettings): LedStyle? = if (settings.ledMatrix) LedStyle(settings.ledDensity, settings.ledTint) else null
    }
}

/**
 * Turns everything drawn inside into round LED dots, on the GPU: each dot takes the color at its centre
 * (or that brightness in the tint color), with a faint unlit dot between, like a real LED panel. Works
 * for video and animations too (a hardware video overlay is not covered, so callers use a texture view).
 */
@Composable
fun Modifier.ledMatrix(style: LedStyle?): Modifier {
    if (style == null) return this
    val shader = remember { RuntimeShader(LED_SHADER) }
    return graphicsLayer {
        if (size.width <= 0f || size.height <= 0f) return@graphicsLayer
        val pitch = size.width / style.dots.coerceAtLeast(8)
        shader.setFloatUniform("pitch", pitch)
        val tint = style.tint
        shader.setFloatUniform(
            "tint",
            AndroidColor.red(tint) / 255f,
            AndroidColor.green(tint) / 255f,
            AndroidColor.blue(tint) / 255f,
            if (tint == 0) 0f else 1f,
        )
        renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
        clip = true
    }
}

/**
 * The same look in software, for widget frames: samples the dot centres of [source] and draws the dots
 * on black. Returns a new bitmap of the same size.
 */
fun ledMatrixBitmap(source: Bitmap, style: LedStyle): Bitmap {
    val out = createBitmap(source.width, source.height)
    val canvas = Canvas(out)
    canvas.drawColor(AndroidColor.BLACK)
    val pitch = source.width.toFloat() / style.dots.coerceAtLeast(8)
    val radius = pitch * LIT_RADIUS
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    var y = pitch / 2
    while (y < source.height) {
        var x = pitch / 2
        while (x < source.width) {
            val argb = source[x.toInt().coerceIn(0, source.width - 1), y.toInt().coerceIn(0, source.height - 1)]
            val lit = if (style.tint == 0) argb else tinted(argb, style.tint)
            // Never darker than an unlit LED, like the shader, so the grid reads on black.
            paint.color = AndroidColor.rgb(maxOf(AndroidColor.red(lit), UNLIT), maxOf(AndroidColor.green(lit), UNLIT), maxOf(AndroidColor.blue(lit), UNLIT))
            canvas.drawCircle(x, y, radius, paint)
            x += pitch
        }
        y += pitch
    }
    return out
}

private fun tinted(argb: Int, tint: Int): Int {
    val lum = (0.299f * AndroidColor.red(argb) + 0.587f * AndroidColor.green(argb) + 0.114f * AndroidColor.blue(argb)) / 255f
    return AndroidColor.rgb((AndroidColor.red(tint) * lum).toInt(), (AndroidColor.green(tint) * lum).toInt(), (AndroidColor.blue(tint) * lum).toInt())
}

/** Lit dot radius as a share of the pitch. */
private const val LIT_RADIUS = 0.38f

/** An unlit LED's level (0-255), the same as the shader's 0.055. */
private const val UNLIT = 14

private const val LED_SHADER = """
uniform shader content;
uniform float pitch;
uniform half4 tint;

half4 main(float2 coord) {
    float2 centre = (floor(coord / pitch) + 0.5) * pitch;
    half4 c = content.eval(centre);
    float d = length(coord - centre) / pitch;
    float mask = 1.0 - smoothstep(0.32, 0.42, d);
    half lum = dot(c.rgb, half3(0.299, 0.587, 0.114));
    half3 lit = mix(c.rgb, tint.rgb * lum, tint.a);
    half3 unlit = half3(0.055);
    return half4(max(lit, unlit) * mask, 1.0);
}
"""
