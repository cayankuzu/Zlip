package com.zlip.app.ui.components

import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zlip.app.R
import com.zlip.app.ui.theme.BrandGradient
import com.zlip.app.ui.theme.BrandMint

/** Width / height of the Z Flip5/6/7 FE cover screen (748 x 720 px). */
const val COVER_ASPECT_RATIO: Float = 748f / 720f

/** ARGB palette shared by every editor: neutrals, then a rainbow. */
val DefaultPalette: List<Int> = listOf(
    0xFF000000, 0xFFFFFFFF, 0xFF9E9E9E, 0xFF424242,
    0xFFFF2D55, 0xFFFF3B30, 0xFFFF9500, 0xFFFFCC00,
    0xFF34C759, 0xFF7DF0C3, 0xFF6FD6FF, 0xFF007AFF,
    0xFF8A9BFF, 0xFFB59CFF, 0xFFFF6FD8, 0xFFA2845E,
).map { it.toInt() }

/** Spoken names of [DefaultPalette], in the same order (TalkBack says "Mint", not a hex code). */
private val DefaultPaletteNames: List<Int> = listOf(
    R.string.color_black, R.string.color_white, R.string.color_grey, R.string.color_dark_grey,
    R.string.color_pink, R.string.color_red, R.string.color_orange, R.string.color_yellow,
    R.string.color_green, R.string.color_mint, R.string.color_sky, R.string.color_blue,
    R.string.color_periwinkle, R.string.color_lavender, R.string.color_magenta, R.string.color_brown,
)

/** A preview never takes more of the window's height than this, so the controls under it stay in reach. */
private const val PREVIEW_MAX_HEIGHT_FRACTION = 0.45f

/**
 * A frame shaped like the cover screen, for live previews. [glowing] draws the brand gradient edge. It fills
 * the width it is given but stays under [PREVIEW_MAX_HEIGHT_FRACTION] of the window's height (in landscape
 * it is narrower then, centred in that width).
 */
@Composable
fun CoverPreviewFrame(
    modifier: Modifier = Modifier,
    glowing: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    val windowHeight = LocalConfiguration.current.screenHeightDp.dp
    Box(
        modifier = modifier
            // Frees the given width from being a minimum, so the cap below can shrink it.
            .wrapContentWidth()
            .widthIn(max = windowHeight * PREVIEW_MAX_HEIGHT_FRACTION * COVER_ASPECT_RATIO)
            .aspectRatio(COVER_ASPECT_RATIO)
            .clip(shape)
            .background(Color.Black)
            .border(
                border = if (glowing) {
                    BorderStroke(2.5.dp, Brush.linearGradient(BrandGradient))
                } else {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                },
                shape = shape,
            ),
        content = content,
    )
}

/** Big pill button filled with the brand gradient. */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .heightIn(min = 52.dp)
            .clip(shape)
            .background(
                if (enabled) Brush.linearGradient(BrandGradient) else Brush.linearGradient(listOf(Color.DarkGray, Color.DarkGray)),
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color(0xFF070912))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium, color = Color(0xFF070912))
    }
}

/** Rounded, softly lit card used for every section. */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

@Composable
fun SectionTitle(@StringRes text: Int, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(text),
            style = MaterialTheme.typography.titleMedium,
            // Never split mid-word by what trails it.
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

/** Title + description on the left, a switch on the right; the whole row toggles. */
@Composable
fun SwitchRow(
    title: String,
    description: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            // toggleable (not clickable) so TalkBack announces the on/off state of the whole row.
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            IconBadge(icon)
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** Small tinted circle behind a setting's icon. */
@Composable
fun IconBadge(icon: ImageVector, size: Dp = 38.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(size * 0.55f))
    }
}

/**
 * Single-choice chips, wrapping onto more lines when they do not fit: a sideways-scrolling row hid its last
 * options with nothing to show they were there.
 */
@Composable
fun <T> ChoiceChips(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
}

/** Status dot + label, e.g. "Cover screen on". */
@Composable
fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier) {
    val animated by animateColorAsState(color, label = "status")
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(animated.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(animated),
        )
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = animated)
    }
}

/** Space between two swatches' touch targets in [ColorSwatchRow]. */
private val SWATCH_GAP = 4.dp

/** How far the row's end fades out while more colours wait past it. */
private val SWATCH_FADE = 24.dp

/**
 * Where [ColorSwatchRow] starts scrolled so the swatch at [index] is in view, one swatch of context before it:
 * [stride] is a swatch plus the gap. The scroll state clamps a value past the end.
 */
internal fun swatchScrollTarget(index: Int, stride: Int): Int = (index - 1).coerceAtLeast(0) * stride

/**
 * Horizontally scrolling row of color dots; [selected] gets a ring. It opens scrolled to the selected colour (an item
 * saved in blue showed no ring at all), and its end fades while more colours are hidden past it.
 */
@Composable
fun ColorSwatchRow(
    colors: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    swatchSize: Dp = 44.dp,
) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    LaunchedEffect(Unit) {
        val index = colors.indexOf(selected)
        if (index > 0) {
            val stride = with(density) { (maxOf(swatchSize, 48.dp) + SWATCH_GAP).roundToPx() }
            scrollState.scrollTo(swatchScrollTarget(index, stride))
        }
    }
    Row(
        modifier = modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                if (scrollState.canScrollForward) {
                    val fade = SWATCH_FADE.toPx()
                    drawRect(
                        brush = Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = size.width - fade, endX = size.width),
                        topLeft = Offset(size.width - fade, 0f),
                        size = Size(fade, size.height),
                        blendMode = BlendMode.DstOut,
                    )
                }
            }
            .horizontalScroll(scrollState),
        // The 48 dp touch targets add 2 dp around each 44 dp swatch, so the visible gap stays 8 dp.
        horizontalArrangement = Arrangement.spacedBy(SWATCH_GAP),
    ) {
        colors.forEach { argb ->
            val isSelected = argb == selected
            val name = stringResource(DefaultPaletteNames.getOrNull(DefaultPalette.indexOf(argb)) ?: R.string.color_custom)
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .size(swatchSize)
                    .clip(CircleShape)
                    .background(Color(argb))
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape,
                    )
                    .semantics {
                        this.selected = isSelected
                        contentDescription = name
                    }
                    .clickable(role = Role.RadioButton) { onSelect(argb) },
            )
        }
    }
}

/** Short centered explanation under a section. */
@Composable
fun HintText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Start,
        modifier = modifier.padding(top = 4.dp),
    )
}

@Composable
fun VerticalSpace(height: Dp) = Spacer(Modifier.height(height))

/** Formats milliseconds into mm:ss (or h:mm:ss for clips an hour or longer). */
fun formatVideoTime(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

/**
 * Sleek, custom-drawn video scrubber with mm:ss time labels.
 * Consumes touch-down and drag events immediately so outer swipe/tap detectors (e.g. on the cover screen
 * or inside a HorizontalPager) never steal horizontal scrubbing gestures.
 */
@Composable
fun VideoSeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = BrandMint,
    inactiveColor: Color = Color.White.copy(alpha = 0.25f),
    totalTimeColor: Color = Color.White.copy(alpha = 0.72f),
    compact: Boolean = false,
) {
    val safeDuration = durationMs.coerceAtLeast(1L)
    var draggingRatio by remember { mutableStateOf<Float?>(null) }
    var trackWidth by remember { mutableIntStateOf(0) }
    val currentOnSeek by rememberUpdatedState(onSeek)

    val currentRatio = (draggingRatio ?: (positionMs.toFloat() / safeDuration.toFloat())).coerceIn(0f, 1f)
    val displayPosMs = draggingRatio?.let { (it * safeDuration).toLong() } ?: positionMs.coerceIn(0L, safeDuration)

    val isDragging = draggingRatio != null
    val trackHeight by animateDpAsState(
        targetValue = if (isDragging) 7.dp else 4.5.dp,
        label = "seek-track-height",
    )
    val thumbRadius by animateDpAsState(
        targetValue = if (isDragging) 8.dp else 6.dp,
        label = "seek-thumb-radius",
    )
    val fontSize = if (compact) 11.sp else 12.sp

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp),
    ) {
        Text(
            text = formatVideoTime(displayPosMs),
            color = activeColor,
            fontSize = fontSize,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .height(if (compact) 28.dp else 32.dp)
                .onSizeChanged { trackWidth = it.width }
                .semantics {
                    progressBarRangeInfo = ProgressBarRangeInfo(currentRatio, 0f..1f)
                    setProgress { target ->
                        val clamped = target.coerceIn(0f, 1f)
                        currentOnSeek((clamped * safeDuration).toLong())
                        true
                    }
                }
                .pointerInput(safeDuration) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        val width = trackWidth.toFloat().coerceAtLeast(1f)
                        var ratio = (down.position.x / width).coerceIn(0f, 1f)
                        draggingRatio = ratio
                        var lastSeekAt = SystemClock.uptimeMillis()
                        currentOnSeek((ratio * safeDuration).toLong())

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
                            if (change == null) break
                            change.consume()
                            ratio = (change.position.x / width).coerceIn(0f, 1f)
                            draggingRatio = ratio
                            val now = SystemClock.uptimeMillis()
                            if (!change.pressed) {
                                currentOnSeek((ratio * safeDuration).toLong())
                                break
                            } else if (now - lastSeekAt >= 90L) {
                                lastSeekAt = now
                                currentOnSeek((ratio * safeDuration).toLong())
                            }
                        }
                        draggingRatio = null
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxWidth().height(18.dp)) {
                val barH = trackHeight.toPx()
                val radius = thumbRadius.toPx()
                val yCenter = size.height / 2f
                val barTop = yCenter - barH / 2f
                val corner = CornerRadius(barH / 2f, barH / 2f)

                // Inactive background track
                drawRoundRect(
                    color = inactiveColor,
                    topLeft = Offset(0f, barTop),
                    size = Size(size.width, barH),
                    cornerRadius = corner,
                )

                // Active progress track
                val activeW = (size.width * currentRatio).coerceIn(0f, size.width)
                if (activeW > 0f) {
                    drawRoundRect(
                        color = activeColor,
                        topLeft = Offset(0f, barTop),
                        size = Size(activeW, barH),
                        cornerRadius = corner,
                    )
                }

                // Thumb with subtle halo when dragging
                val thumbX = activeW.coerceIn(radius, (size.width - radius).coerceAtLeast(radius))
                if (isDragging) {
                    drawCircle(
                        color = activeColor.copy(alpha = 0.28f),
                        radius = radius * 1.75f,
                        center = Offset(thumbX, yCenter),
                    )
                }
                drawCircle(
                    color = Color.White,
                    radius = radius,
                    center = Offset(thumbX, yCenter),
                )
                drawCircle(
                    color = activeColor,
                    radius = radius * 0.68f,
                    center = Offset(thumbX, yCenter),
                )
            }
        }

        Text(
            text = formatVideoTime(safeDuration),
            color = totalTimeColor,
            fontSize = fontSize,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

/**
 * Self-contained media player control bar with Play/Pause toggle, mm:ss timestamps, and interactive seek bar.
 * Used below live video previews on the Home screen, Group Editor, and Media Editor.
 */
@Composable
fun MediaPlayerBar(
    paused: Boolean,
    onTogglePause: () -> Unit,
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(
                onClick = onTogglePause,
                modifier = Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            ) {
                Icon(
                    imageVector = if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    contentDescription = stringResource(if (paused) R.string.action_resume else R.string.action_pause),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
            VideoSeekBar(
                positionMs = positionMs,
                durationMs = durationMs,
                onSeek = onSeek,
                modifier = Modifier.weight(1f),
                activeColor = MaterialTheme.colorScheme.primary,
                inactiveColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f),
                totalTimeColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
