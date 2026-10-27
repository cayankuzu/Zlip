package com.zlip.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The neon edge of the Zlip icon: blue, through cyan, to mint. */
val BrandBlue = Color(0xFF8A9BFF)
val BrandCyan = Color(0xFF6FD6FF)
val BrandMint = Color(0xFF8CF2C4)
val BrandViolet = Color(0xFFB59CFF)
val BrandGradient = listOf(BrandBlue, BrandCyan, BrandMint)

/**
 * Always dark with a true-black background: it matches the brand, and on the Flip's OLED panels black
 * pixels are switched off, so the app itself costs less battery.
 */
private val ZlipColors = darkColorScheme(
    primary = BrandBlue,
    onPrimary = Color(0xFF0A1035),
    primaryContainer = Color(0xFF242C5C),
    onPrimaryContainer = Color(0xFFDDE2FF),
    secondary = BrandMint,
    onSecondary = Color(0xFF00382A),
    secondaryContainer = Color(0xFF14382F),
    onSecondaryContainer = Color(0xFFB8F7DD),
    tertiary = BrandViolet,
    onTertiary = Color(0xFF241248),
    tertiaryContainer = Color(0xFF35265E),
    onTertiaryContainer = Color(0xFFE9DDFF),
    background = Color.Black,
    onBackground = Color(0xFFECEDF5),
    surface = Color.Black,
    onSurface = Color(0xFFECEDF5),
    surfaceVariant = Color(0xFF1C1D27),
    onSurfaceVariant = Color(0xFFA6A9BC),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0C0D13),
    surfaceContainer = Color(0xFF111219),
    surfaceContainerHigh = Color(0xFF171821),
    surfaceContainerHighest = Color(0xFF1E1F2A),
    surfaceBright = Color(0xFF262734),
    surfaceDim = Color.Black,
    outline = Color(0xFF3B3D50),
    outlineVariant = Color(0xFF252736),
    error = Color(0xFFFF7A86),
    onError = Color(0xFF4A0010),
    errorContainer = Color(0xFF5C1520),
    onErrorContainer = Color(0xFFFFDADC),
    inverseSurface = Color(0xFFE6E7F0),
    inverseOnSurface = Color(0xFF15161E),
    inversePrimary = Color(0xFF4253B8),
    scrim = Color.Black,
)

private val ZlipShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val ZlipTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun ZlipTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ZlipColors,
        shapes = ZlipShapes,
        typography = ZlipTypography,
        content = content,
    )
}
