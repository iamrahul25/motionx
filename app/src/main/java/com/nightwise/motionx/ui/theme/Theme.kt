package com.nightwise.motionx.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Scheme = darkColorScheme(
    primary = MX.Cyan,
    secondary = MX.Magenta,
    tertiary = MX.Lime,
    background = MX.Night,
    surface = MX.Surface,
    onPrimary = MX.Night,
    onBackground = MX.TextHi,
    onSurface = MX.TextHi,
)

private val Display = FontFamily.SansSerif

val MXType = Typography(
    displayLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Black, fontSize = 44.sp, lineHeight = 46.sp, letterSpacing = (-1).sp),
    displayMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Black, fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 1.2.sp),
    labelMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.4.sp),
    labelSmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 1.sp),
)

@Composable
fun MotionXTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = MXType, content = content)
}
