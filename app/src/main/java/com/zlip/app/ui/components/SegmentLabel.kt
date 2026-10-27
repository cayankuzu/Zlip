package com.zlip.app.ui.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/**
 * A segmented button's label on one line: with large fonts or long Turkish words it shrinks to fit instead
 * of breaking mid-word or being cut off. Pair it with `icon = {}` on the button, whose filled colour already
 * marks the choice, so the label gets the whole segment rather than losing a third of it to the checkmark.
 */
@Composable
fun SegmentLabel(text: String) {
    val color = LocalContentColor.current
    val style = LocalTextStyle.current
    // The button's own label size (labelLarge); a style without one in sp falls back to it.
    val full = style.fontSize.takeIf { it.isSp && it.value > MIN_LABEL_SP } ?: DEFAULT_LABEL_SP.sp
    BasicText(
        text = text,
        style = style.copy(textAlign = TextAlign.Center),
        color = { color },
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = MIN_LABEL_SP.sp, maxFontSize = full),
    )
}

/**
 * A label never shrinks below this. Low because sp grow with the phone's font size: at the largest (2.0) this
 * is still about 16 dp, above the normal 14, and "Zamanlayıcı" only fits a third of the row from there.
 */
private const val MIN_LABEL_SP = 8
private const val DEFAULT_LABEL_SP = 14
