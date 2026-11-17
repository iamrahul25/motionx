package com.nightwise.motionx.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** MotionX palette: deep night base with a few saturated neon accents. */
object MX {
    val Night = Color(0xFF070912)
    val Night2 = Color(0xFF0D1022)
    val Surface = Color(0xFF141938)
    val SurfaceHi = Color(0xFF1C2350)
    val Glass = Color(0x1FFFFFFF)
    val GlassDark = Color(0xB30A0D1E)
    val Stroke = Color(0x33FFFFFF)
    val StrokeSoft = Color(0x1AFFFFFF)

    val Cyan = Color(0xFF22E1FF)
    val Magenta = Color(0xFFFF3EA5)
    val Violet = Color(0xFF8B5CFF)
    val Lime = Color(0xFFB6FF3B)
    val Amber = Color(0xFFFFC23D)
    val Coral = Color(0xFFFF5A4E)
    val Sky = Color(0xFF4AA8FF)

    val TextHi = Color(0xFFF5F7FF)
    val TextMid = Color(0xFFB4BAD9)
    val TextLow = Color(0xFF7A80A6)

    val Hurdle = Amber
    val Bar = Sky
    val Wall = Coral

    val PrimaryGradient = Brush.linearGradient(listOf(Color(0xFF22E1FF), Color(0xFF8B5CFF)))
    val HotGradient = Brush.linearGradient(listOf(Color(0xFFFF3EA5), Color(0xFFFF8A3D)))
    val LimeGradient = Brush.linearGradient(listOf(Color(0xFFB6FF3B), Color(0xFF22E1FF)))
}
